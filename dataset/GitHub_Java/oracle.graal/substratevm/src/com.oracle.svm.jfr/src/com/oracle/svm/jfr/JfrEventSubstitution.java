/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.jfr;

//Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.util.ReflectionUtil;

import jdk.jfr.Event;
import jdk.jfr.internal.EventWriter;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.SecuritySupport;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

/**
 * I think that most of the code in this class is unnecessary and can be implemented in a different
 * way (i.e., without a SubstitutionProcessor).
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class JfrEventSubstitution extends SubstitutionProcessor {

    private final ResolvedJavaType jdkJfrEvent;
    private final ConcurrentHashMap<ResolvedJavaType, Boolean> typeSubstitution;

    JfrEventSubstitution(MetaAccessProvider metaAccess) {
        jdkJfrEvent = metaAccess.lookupJavaType(Event.class);
        ResolvedJavaType jdkJfrEventWriter = metaAccess.lookupJavaType(EventWriter.class);
        changeWriterResetMethod(jdkJfrEventWriter);
        typeSubstitution = new ConcurrentHashMap<>();
    }

    @Override
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        if (!jdkJfrEvent.equals(type) && jdkJfrEvent.isAssignableFrom(type) && !type.isAbstract()) {
            typeSubstitution.computeIfAbsent(type, JfrEventSubstitution::initEventClass);
        }
        return type;
    }

    private static Boolean initEventClass(ResolvedJavaType eventType) throws RuntimeException {
        try {
            Class<? extends Event> newEventClass = OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), eventType).asSubclass(Event.class);
            eventType.initialize();
            SecuritySupport.registerEvent(newEventClass);
            JfrJavaEvents.registerEventClass(newEventClass);
            JVM.getJVM().retransformClasses(new Class<?>[]{newEventClass});
            Field field = ReflectionUtil.lookupField(newEventClass, "eventHandler");   // EventInstrumentation.FIELD_EVENT_HANDLER
            RuntimeReflection.register(field);
            return Boolean.TRUE;
        } catch (Throwable ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static void changeWriterResetMethod(ResolvedJavaType eventWriterType) {
        for (ResolvedJavaMethod m : eventWriterType.getDeclaredMethods()) {
            if (m.getName().equals("reset")) {
                setPublicModifier(m);
            }
        }
    }

    private static void setPublicModifier(ResolvedJavaMethod m) {
        try {
            Class<?> method = m.getClass();
            Method metaspaceMethodM = method.getDeclaredMethod("getMetaspaceMethod");
            metaspaceMethodM.setAccessible(true);
            long metaspaceMethod = (Long) metaspaceMethodM.invoke(m);
            // Checkstyle: stop
            Class<?> hotSpotVMConfigC = Class.forName("jdk.vm.ci.hotspot.HotSpotVMConfig");
            // Checkstyle: resume
            Method configM = hotSpotVMConfigC.getDeclaredMethod("config");
            configM.setAccessible(true);
            Field methodAccessFlagsOffsetF = hotSpotVMConfigC.getDeclaredField("methodAccessFlagsOffset");
            methodAccessFlagsOffsetF.setAccessible(true);
            Object hotSpotVMConfig = configM.invoke(null);
            int methodAccessFlagsOffset = methodAccessFlagsOffsetF.getInt(hotSpotVMConfig);
            Unsafe unsafe = GraalUnsafeAccess.getUnsafe();
            int modifiers = unsafe.getInt(metaspaceMethod + methodAccessFlagsOffset);
            int newModifiers = modifiers & ~Modifier.PRIVATE | Modifier.PUBLIC;
            unsafe.putInt(metaspaceMethod + methodAccessFlagsOffset, newModifiers);
        } catch (Exception ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
