/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.interpreter;

import java.lang.reflect.*;

import sun.misc.*;

import com.oracle.graal.api.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;


/**
 * This class encapsulates all vm specific methods for the {@link BytecodeInterpreter}.
 */
public class VMAdapter {

    private static final Unsafe unsafe = loadUnsafe();

    protected VMAdapter() {
    }

    public MetaAccessProvider getRuntime() {
        return Graal.getRuntime().getCapability(MetaAccessProvider.class);
    }

    public void monitorEnter(Object value) {
        nullCheck(value);
        unsafe.monitorEnter(value);
    }

    public void monitorExit(Object value) {
        nullCheck(value);
        unsafe.monitorExit(value);
    }

    public Object newObject(ResolvedJavaType type) throws InstantiationException {
        return unsafe.allocateInstance(type.toJava());
    }

    public Object getField(Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            return unsafe.getObjectVolatile(resolveBase(base, field), offset);
        } else {
            return unsafe.getObject(resolveBase(base, field), offset);
        }
    }

    public int getFieldInt(Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            return unsafe.getIntVolatile(resolveBase(base, field), offset);
        } else {
            return unsafe.getInt(resolveBase(base, field), offset);
        }
    }

    public long getFieldLong(Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            return unsafe.getLongVolatile(resolveBase(base, field), offset);
        } else {
            return unsafe.getLong(resolveBase(base, field), offset);
        }
    }

    public double getFieldDouble(Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            return unsafe.getDoubleVolatile(resolveBase(base, field), offset);
        } else {
            return unsafe.getDouble(resolveBase(base, field), offset);
        }
    }

    public float getFieldFloat(Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            return unsafe.getFloatVolatile(resolveBase(base, field), offset);
        } else {
            return unsafe.getFloat(resolveBase(base, field), offset);
        }
    }

    public void setField(Object value, Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            unsafe.putObjectVolatile(resolveBase(base, field), offset, value);
        } else {
            unsafe.putObject(resolveBase(base, field), offset, value);
        }
    }

    public void setFieldInt(int value, Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            unsafe.putIntVolatile(resolveBase(base, field), offset, value);
        } else {
            unsafe.putInt(resolveBase(base, field), offset, value);
        }
    }


    public void setFieldFloat(float value, Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            unsafe.putFloatVolatile(resolveBase(base, field), offset, value);
        } else {
            unsafe.putFloat(resolveBase(base, field), offset, value);
        }
    }

    public void setFieldDouble(double value, Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            unsafe.putDoubleVolatile(resolveBase(base, field), offset, value);
        } else {
            unsafe.putDouble(resolveBase(base, field), offset, value);
        }
    }

    public void setFieldLong(long value, Object base, ResolvedJavaField field) {
        long offset = resolveOffset(field);
        if (isVolatile(field)) {
            unsafe.putDoubleVolatile(resolveBase(base, field), offset, value);
        } else {
            unsafe.putDouble(resolveBase(base, field), offset, value);
        }
    }

    public byte getArrayByte(long index, Object array) {
        checkArray(array, index);
        return unsafe.getByte(array, Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * index);
    }

    public char getArrayChar(long index, Object array) {
        checkArray(array, index);
        return unsafe.getChar(array, Unsafe.ARRAY_CHAR_BASE_OFFSET + Unsafe.ARRAY_CHAR_INDEX_SCALE * index);
    }

    public short getArrayShort(long index, Object array) {
        checkArray(array, index);
        return unsafe.getShort(array, Unsafe.ARRAY_SHORT_BASE_OFFSET + Unsafe.ARRAY_SHORT_INDEX_SCALE * index);
    }

    public int getArrayInt(long index, Object array) {
        checkArray(array, index);
        return unsafe.getInt(array, Unsafe.ARRAY_INT_BASE_OFFSET + Unsafe.ARRAY_INT_INDEX_SCALE * index);
    }

    public long getArrayLong(long index, Object array) {
        checkArray(array, index);
        return unsafe.getLong(array, Unsafe.ARRAY_LONG_BASE_OFFSET + Unsafe.ARRAY_LONG_INDEX_SCALE * index);
    }

    public double getArrayDouble(long index, Object array) {
        checkArray(array, index);
        return unsafe.getDouble(array, Unsafe.ARRAY_DOUBLE_BASE_OFFSET + Unsafe.ARRAY_DOUBLE_INDEX_SCALE * index);
    }

    public float getArrayFloat(long index, Object array) {
        checkArray(array, index);
        return unsafe.getFloat(array, Unsafe.ARRAY_FLOAT_BASE_OFFSET + Unsafe.ARRAY_FLOAT_INDEX_SCALE * index);
    }

    public Object getArrayObject(long index, Object array) {
        checkArray(array, index);
        return unsafe.getObject(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index);
    }

    public void setArrayByte(byte value, long index, Object array) {
        checkArray(array, index);
        if (array instanceof boolean[]) {
            checkArrayType(array, boolean.class);
        } else {
            checkArrayType(array, byte.class);
        }
        unsafe.putByte(array, Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * index, value);
    }

    public void setArrayChar(char value, long index, Object array) {
        checkArray(array, index);
        checkArrayType(array, char.class);
        unsafe.putChar(array, Unsafe.ARRAY_CHAR_BASE_OFFSET + Unsafe.ARRAY_CHAR_INDEX_SCALE * index, value);
    }

    public void setArrayShort(short value, long index, Object array) {
        checkArray(array, index);
        checkArrayType(array, short.class);
        unsafe.putShort(array, Unsafe.ARRAY_SHORT_BASE_OFFSET + Unsafe.ARRAY_SHORT_INDEX_SCALE * index, value);
    }

    public void setArrayInt(int value, long index, Object array) {
        checkArray(array, index);
        checkArrayType(array, int.class);
        unsafe.putInt(array, Unsafe.ARRAY_INT_BASE_OFFSET + Unsafe.ARRAY_INT_INDEX_SCALE * index, value);
    }

    public void setArrayLong(long value, long index, Object array) {
        checkArray(array, index);
        checkArrayType(array, long.class);
        unsafe.putLong(array, Unsafe.ARRAY_LONG_BASE_OFFSET + Unsafe.ARRAY_LONG_INDEX_SCALE * index, value);
    }

    public void setArrayFloat(float value, long index, Object array) {
        checkArray(array, index);
        checkArrayType(array, float.class);
        unsafe.putFloat(array, Unsafe.ARRAY_FLOAT_BASE_OFFSET + Unsafe.ARRAY_FLOAT_INDEX_SCALE * index, value);
    }

    public void setArrayDouble(double value, long index, Object array) {
        checkArray(array, index);
        checkArrayType(array, double.class);
        unsafe.putDouble(array, Unsafe.ARRAY_DOUBLE_BASE_OFFSET + Unsafe.ARRAY_DOUBLE_INDEX_SCALE * index, value);
    }

    public void setArrayObject(Object value, long index, Object array) {
        checkArray(array, index);
        checkArrayType(array, value != null ? value.getClass() : null);
        unsafe.putObject(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index, value);
    }

    private static void nullCheck(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
    }

    private void checkArrayType(Object array, Class<?> arrayType) {
        if (arrayType == null) {
            return;
        }
        ResolvedJavaType type = getRuntime().getResolvedJavaType(array.getClass()).componentType();
        if (!type.toJava().isAssignableFrom(arrayType)) {
            throw new ArrayStoreException(arrayType.getName());
        }
    }

    private void checkArray(Object array, long index) {
        nullCheck(array);
        ResolvedJavaType type = getRuntime().getResolvedJavaType(array.getClass());
        if (!type.isArrayClass()) {
            throw new ArrayStoreException(array.getClass().getName());
        }
        if (index < 0 || index >= arrayLength(array)) {
            throw new ArrayIndexOutOfBoundsException((int) index);
        }
    }

    private static int arrayLength(Object array) {
        assert array != null;
        return Array.getLength(array);
    }

    private static boolean isVolatile(ResolvedJavaField field) {
        return Modifier.isVolatile(field.accessFlags());
    }

    private static long resolveOffset(ResolvedJavaField field) {
        return ((HotSpotResolvedJavaField) field).offset();
    }

    private static Object resolveBase(Object base, ResolvedJavaField field) {
        Object accessorBase = base;
        if (accessorBase == null) {
            accessorBase = field.holder().toJava();
        }
        return accessorBase;
    }

    private static Unsafe loadUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    private static final VMAdapter instance = new VMAdapter();
    public static VMAdapter getInstance() {
        return instance;
    }


}
