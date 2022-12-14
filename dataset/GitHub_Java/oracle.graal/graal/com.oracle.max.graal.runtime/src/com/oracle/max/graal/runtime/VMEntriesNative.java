/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.max.graal.runtime;

import java.lang.reflect.*;

import com.oracle.max.graal.runtime.server.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Entries into the HotSpot VM from Java code.
 */
public class VMEntriesNative implements VMEntries, Remote {

    // Checkstyle: stop
    @Override
    public native byte[] RiMethod_code(long vmId);

    @Override
    public native int RiMethod_maxStackSize(long vmId);

    @Override
    public native int RiMethod_maxLocals(long vmId);

    @Override
    public native RiType RiMethod_holder(long vmId);

    @Override
    public native String RiMethod_signature(long vmId);

    @Override
    public native int RiMethod_accessFlags(long vmId);

    @Override
    public native RiType RiSignature_lookupType(String returnType, HotSpotTypeResolved accessingClass);

    @Override
    public native Object RiConstantPool_lookupConstant(long vmId, int cpi);

    @Override
    public native RiMethod RiConstantPool_lookupMethod(long vmId, int cpi, byte byteCode);

    @Override
    public native RiSignature RiConstantPool_lookupSignature(long vmId, int cpi);

    @Override
    public native RiType RiConstantPool_lookupType(long vmId, int cpi);

    @Override
    public native RiField RiConstantPool_lookupField(long vmId, int cpi, byte byteCode);

    @Override
    public native RiConstantPool RiType_constantPool(HotSpotTypeResolved klass);

    @Override
    public native void installMethod(HotSpotTargetMethod targetMethod);

    @Override
    public native long installStub(HotSpotTargetMethod targetMethod);

    @Override
    public native HotSpotVMConfig getConfiguration();

    @Override
    public native RiExceptionHandler[] RiMethod_exceptionHandlers(long vmId);

    @Override
    public native RiMethod RiType_resolveMethodImpl(HotSpotTypeResolved klass, String name, String signature);

    @Override
    public native boolean RiType_isSubtypeOf(HotSpotTypeResolved klass, RiType other);

    @Override
    public native RiType getPrimitiveArrayType(CiKind kind);

    @Override
    public native RiType RiType_arrayOf(HotSpotTypeResolved klass);

    @Override
    public native RiType RiType_componentType(HotSpotTypeResolved klass);

    @Override
    public native RiType RiType_uniqueConcreteSubtype(HotSpotTypeResolved klass);

    @Override
    public native RiType RiType_superType(HotSpotTypeResolved klass);

    @Override
    public native RiType getType(Class<?> javaClass);

    @Override
    public native boolean RiMethod_hasBalancedMonitors(long vmId);

    @Override
    public native void recordBailout(String reason);

    @Override
    public native RiMethod RiMethod_uniqueConcreteMethod(long vmId);

    @Override
    public int getArrayLength(CiConstant array) {
        return Array.getLength(array.asObject());
    }

    @Override
    public boolean compareConstantObjects(CiConstant x, CiConstant y) {
        return x.asObject() == y.asObject();
    }

    @Override
    public RiType getRiType(CiConstant constant) {
        Object o = constant.asObject();
        if (o == null) {
            return null;
        }
        return getType(o.getClass());
    }

    @Override
    public native int RiMethod_invocationCount(long vmId);

    @Override
    public native RiTypeProfile RiMethod_typeProfile(long vmId, int bci);

    // Checkstyle: resume
}
