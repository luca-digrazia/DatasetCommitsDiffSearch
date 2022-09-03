/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.lang.annotation.Annotation;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.c.MutableBoxedPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Call stub for invoking {@link CEntryPoint} methods via a Java-to-native call to the method's
 * {@linkplain CEntryPointCallStubMethod native-to-Java stub}.
 */
public class CEntryPointJavaCallStubMethod extends CCallStubMethod {
    private final String name;
    private final ResolvedJavaType declaringClass;
    private final CodePointer target;

    CEntryPointJavaCallStubMethod(ResolvedJavaMethod original, String name, ResolvedJavaType declaringClass, CodePointer target) {
        super(original, true);
        this.name = name;
        this.declaringClass = declaringClass;
        this.target = target;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    protected String getCorrespondingAnnotationName() {
        return CEntryPoint.class.getSimpleName();
    }

    @Override
    protected ValueNode createTargetAddressNode(HostedGraphKit kit, HostedProviders providers) {
        try {
            /*
             * We currently cannot handle {@link MethodPointer} as a constant in the code, so we
             * force an indirection with a field load from an object of MutableBoxedPointer. Due to
             * the mutable nature of the class, the field load is not constant-folded.
             */
            MutableBoxedPointer box = new MutableBoxedPointer(target);
            ConstantNode boxNode = kit.createObject(box);
            ResolvedJavaField field = providers.getMetaAccess().lookupJavaField(MutableBoxedPointer.class.getDeclaredField("pointer"));
            return kit.createLoadField(boxNode, field);
        } catch (NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    public Annotation[] getAnnotations() {
        return getDeclaredAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }
}
