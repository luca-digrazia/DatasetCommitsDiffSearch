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
package com.oracle.graal.hotspot.snippets;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.snippets.*;

/**
 * Snippets for {@link sun.misc.Unsafe} methods.
 */
@ClassSubstitution(sun.misc.Unsafe.class)
public class UnsafeSnippets implements SnippetsInterface {

    public boolean compareAndSwapObject(Object o, long offset, Object expected, Object x) {
        return CompareAndSwapNode.compareAndSwap(o, 0, offset, expected, x);
    }

    public boolean compareAndSwapInt(Object o, long offset, int expected, int x) {
        return CompareAndSwapNode.compareAndSwap(o, 0, offset, expected, x);
    }

    public boolean compareAndSwapLong(Object o, long offset, long expected, long x) {
        return CompareAndSwapNode.compareAndSwap(o, 0, offset, expected, x);
    }

    public Object getObject(Object o, long offset) {
        return UnsafeLoadNode.load(o, 0, offset, Kind.Object);
    }

    public Object getObjectVolatile(Object o, long offset) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        Object result = getObject(o, offset);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_READ);
        return result;
    }

    public void putObject(Object o, long offset, Object x) {
        UnsafeStoreNode.store(o, 0, offset, x, Kind.Object);
    }

    public void putObjectVolatile(Object o, long offset, Object x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putObject(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public void putOrderedObject(Object o, long offset, Object x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putObject(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public int getInt(Object o, long offset) {
        Integer value = UnsafeLoadNode.load(o, 0, offset, Kind.Int);
        return value;
    }

    public int getIntVolatile(Object o, long offset) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        int result = getInt(o, offset);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_READ);
        return result;
    }

    public void putInt(Object o, long offset, int x) {
        UnsafeStoreNode.store(o, 0, offset, x, Kind.Int);
    }

    public void putIntVolatile(Object o, long offset, int x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putInt(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public void putOrderedInt(Object o, long offset, int x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putInt(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public boolean getBoolean(Object o, long offset) {
        @JavacBug(id = 6995200)
        Boolean result = UnsafeLoadNode.load(o, 0, offset, Kind.Boolean);
        return result;
    }

    public boolean getBooleanVolatile(Object o, long offset) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        boolean result = getBoolean(o, offset);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_READ);
        return result;
    }

    public void putBoolean(Object o, long offset, boolean x) {
        UnsafeStoreNode.store(o, 0, offset, x, Kind.Boolean);
    }

    public void putBooleanVolatile(Object o, long offset, boolean x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putBoolean(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public byte getByte(Object o, long offset) {
        @JavacBug(id = 6995200)
        Byte result = UnsafeLoadNode.load(o, 0, offset, Kind.Byte);
        return result;
    }

    public byte getByteVolatile(Object o, long offset) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        byte result = getByte(o, offset);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_READ);
        return result;
    }

    public void putByte(Object o, long offset, byte x) {
        UnsafeStoreNode.store(o, 0, offset, x, Kind.Byte);
    }

    public void putByteVolatile(Object o, long offset, byte x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putByte(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public short getShort(Object o, long offset) {
        @JavacBug(id = 6995200)
        Short result = UnsafeLoadNode.load(o, 0, offset, Kind.Short);
        return result;
    }

    public short getShortVolatile(Object o, long offset) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        short result = getShort(o, offset);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_READ);
        return result;
    }

    public void putShort(Object o, long offset, short x) {
        UnsafeStoreNode.store(o, 0, offset, x, Kind.Short);
    }

    public void putShortVolatile(Object o, long offset, short x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putShort(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public char getChar(Object o, long offset) {
        @JavacBug(id = 6995200)
        Character result = UnsafeLoadNode.load(o, 0, offset, Kind.Char);
        return result;
    }

    public char getCharVolatile(Object o, long offset) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        char result = getChar(o, offset);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_READ);
        return result;
    }

    public void putChar(Object o, long offset, char x) {
        UnsafeStoreNode.store(o, 0, offset, x, Kind.Char);
    }

    public void putCharVolatile(Object o, long offset, char x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putChar(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public long getLong(Object o, long offset) {
        @JavacBug(id = 6995200)
        Long result = UnsafeLoadNode.load(o, 0, offset, Kind.Long);
        return result;
    }

    public long getLongVolatile(Object o, long offset) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        long result = getLong(o, offset);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_READ);
        return result;
    }

    public void putLong(Object o, long offset, long x) {
        UnsafeStoreNode.store(o, 0, offset, x, Kind.Long);
    }

    public void putLongVolatile(Object o, long offset, long x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putLong(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public void putOrderedLong(Object o, long offset, long x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putLong(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public float getFloat(Object o, long offset) {
        @JavacBug(id = 6995200)
        Float result = UnsafeLoadNode.load(o, 0, offset, Kind.Float);
        return result;
    }

    public float getFloatVolatile(Object o, long offset) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        float result = getFloat(o, offset);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_READ);
        return result;
    }

    public void putFloat(Object o, long offset, float x) {
        UnsafeStoreNode.store(o, 0, offset, x, Kind.Float);
    }

    public void putFloatVolatile(Object o, long offset, float x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putFloat(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public double getDouble(Object o, long offset) {
        @JavacBug(id = 6995200)
        Double result = UnsafeLoadNode.load(o, 0, offset, Kind.Double);
        return result;
    }

    public double getDoubleVolatile(Object o, long offset) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        double result = getDouble(o, offset);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_READ);
        return result;
    }

    public void putDouble(Object o, long offset, double x) {
        UnsafeStoreNode.store(o, 0, offset, x, Kind.Double);
    }

    public void putDoubleVolatile(Object o, long offset, double x) {
        MembarNode.memoryBarrier(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        putDouble(o, offset, x);
        MembarNode.memoryBarrier(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
    }

    public void putByte(long address, byte value) {
        DirectStoreNode.store(address, value);
    }

    public void putShort(long address, short value) {
        DirectStoreNode.store(address, value);
    }

    public void putChar(long address, char value) {
        DirectStoreNode.store(address, value);
    }

    public void putInt(long address, int value) {
        DirectStoreNode.store(address, value);
    }

    public void putLong(long address, long value) {
        DirectStoreNode.store(address, value);
    }

    public void putFloat(long address, float value) {
        DirectStoreNode.store(address, value);
    }

    public void putDouble(long address, double value) {
        DirectStoreNode.store(address, value);
    }

    public byte getByte(long address) {
        return DirectReadNode.read(address, Kind.Byte);
    }

    public short getShort(long address) {
        return DirectReadNode.read(address, Kind.Short);
    }

    public char getChar(long address) {
        return DirectReadNode.read(address, Kind.Char);
    }

    public int getInt(long address) {
        return DirectReadNode.read(address, Kind.Int);
    }

    public long getLong(long address) {
        return DirectReadNode.read(address, Kind.Long);
    }

    public float getFloat(long address) {
        return DirectReadNode.read(address, Kind.Float);
    }

    public double getDouble(long address) {
        return DirectReadNode.read(address, Kind.Double);
    }
}
