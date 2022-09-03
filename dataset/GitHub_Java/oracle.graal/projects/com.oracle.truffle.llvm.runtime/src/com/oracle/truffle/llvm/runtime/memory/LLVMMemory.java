/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.memory;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

import sun.misc.Unsafe;

public abstract class LLVMMemory {

    static final Unsafe UNSAFE = getUnsafe();

    @SuppressWarnings("restriction")
    private static Unsafe getUnsafe() {
        CompilerAsserts.neverPartOfCompilation();
        try {
            Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
            singleoneInstanceField.setAccessible(true);
            return (Unsafe) singleoneInstanceField.get(null);
        } catch (Exception e) {
            throw new AssertionError();
        }
    }

    /** Use {@link com.oracle.truffle.llvm.runtime.memory.LLVMProfiledMemSet} instead. */
    @Deprecated
    public static void memset(LLVMAddress address, long size, byte value) {
        UNSAFE.setMemory(address.getVal(), size, value);
    }

    public static void memset(long address, long size, byte value) {
        UNSAFE.setMemory(address, size, value);
    }

    public static void free(LLVMAddress address) {
        UNSAFE.freeMemory(address.getVal());
    }

    public static LLVMAddress allocateMemory(long size) {
        return LLVMAddress.fromLong(UNSAFE.allocateMemory(size));
    }

    public static LLVMAddress reallocateMemory(LLVMAddress addr, long size) {
        // a null pointer is a valid argument
        return LLVMAddress.fromLong(UNSAFE.reallocateMemory(addr.getVal(), size));
    }

    public static boolean getI1(LLVMAddress addr) {
        return getI1(addr.getVal());
    }

    public static boolean getI1(long ptr) {
        assert ptr != 0;
        return UNSAFE.getByte(ptr) != 0;
    }

    public static byte getI8(LLVMAddress addr) {
        return getI8(addr.getVal());
    }

    public static byte getI8(long ptr) {
        assert ptr != 0;
        return UNSAFE.getByte(ptr);
    }

    public static short getI16(LLVMAddress addr) {
        return getI16(addr.getVal());
    }

    public static short getI16(long ptr) {
        assert ptr != 0;
        return UNSAFE.getShort(ptr);
    }

    public static int getI32(LLVMAddress addr) {
        return getI32(addr.getVal());
    }

    public static int getI32(long ptr) {
        assert ptr != 0;
        return UNSAFE.getInt(ptr);
    }

    public static LLVMIVarBit getIVarBit(LLVMAddress addr, int bitWidth) {
        if (bitWidth % Byte.SIZE != 0) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError();
        }
        int bytes = bitWidth / Byte.SIZE;
        byte[] loadedBytes = new byte[bytes];
        long currentAddressPtr = addr.getVal();
        for (int i = loadedBytes.length - 1; i >= 0; i--) {
            loadedBytes[i] = getI8(currentAddressPtr);
            currentAddressPtr += Byte.BYTES;
        }
        return LLVMIVarBit.create(bitWidth, loadedBytes);
    }

    public static long getI64(LLVMAddress addr) {
        return getI64(addr.getVal());
    }

    public static long getI64(long ptr) {
        assert ptr != 0;
        return UNSAFE.getLong(ptr);
    }

    public static float getFloat(LLVMAddress addr) {
        return getFloat(addr.getVal());
    }

    public static float getFloat(long ptr) {
        assert ptr != 0;
        return UNSAFE.getFloat(ptr);
    }

    public static double getDouble(LLVMAddress addr) {
        return getDouble(addr.getVal());
    }

    public static double getDouble(long ptr) {
        assert ptr != 0;
        return UNSAFE.getDouble(ptr);
    }

    public static LLVM80BitFloat get80BitFloat(LLVMAddress addr) {
        byte[] bytes = new byte[LLVM80BitFloat.BYTE_WIDTH];
        long currentPtr = addr.getVal();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = getI8(currentPtr);
            currentPtr += Byte.BYTES;
        }
        return LLVM80BitFloat.fromBytes(bytes);
    }

    static long extractAddr(LLVMAddress addr) {
        assert addr.getVal() != 0;
        return addr.getVal();
    }

    public static LLVMAddress getAddress(LLVMAddress addr) {
        return getAddress(addr.getVal());
    }

    public static LLVMAddress getAddress(long ptr) {
        assert ptr != 0;
        return LLVMAddress.fromLong(UNSAFE.getAddress(ptr));
    }

    public static void putI1(LLVMAddress addr, boolean value) {
        putI1(addr.getVal(), value);
    }

    public static void putI1(long ptr, boolean value) {
        assert ptr != 0;
        UNSAFE.putByte(ptr, (byte) (value ? 1 : 0));
    }

    public static void putI8(LLVMAddress addr, byte value) {
        putI8(addr.getVal(), value);
    }

    public static void putI8(long ptr, byte value) {
        assert ptr != 0;
        UNSAFE.putByte(ptr, value);
    }

    public static void putI16(LLVMAddress addr, short value) {
        putI16(addr.getVal(), value);
    }

    public static void putI16(long ptr, short value) {
        assert ptr != 0;
        UNSAFE.putShort(ptr, value);
    }

    public static void putI32(LLVMAddress addr, int value) {
        putI32(addr.getVal(), value);
    }

    public static void putI32(long ptr, int value) {
        assert ptr != 0;
        UNSAFE.putInt(ptr, value);
    }

    public static void putI64(LLVMAddress addr, long value) {
        putI64(addr.getVal(), value);
    }

    public static void putI64(long ptr, long value) {
        assert ptr != 0;
        UNSAFE.putLong(ptr, value);
    }

    public static void putIVarBit(LLVMAddress addr, LLVMIVarBit value) {
        byte[] bytes = value.getBytes();
        long currentptr = addr.getVal();
        for (int i = bytes.length - 1; i >= 0; i--) {
            putI8(currentptr, bytes[i]);
            currentptr += Byte.BYTES;
        }
    }

    private static void putByteArray(LLVMAddress addr, byte[] bytes) {
        putByteArray(addr.getVal(), bytes);
    }

    private static void putByteArray(long ptr, byte[] bytes) {
        long currentptr = ptr;
        for (int i = 0; i < bytes.length; i++) {
            putI8(currentptr, bytes[i]);
            currentptr += Byte.BYTES;
        }
    }

    public static void putFloat(LLVMAddress addr, float value) {
        putFloat(addr.getVal(), value);
    }

    public static void putFloat(long ptr, float value) {
        assert ptr != 0;
        UNSAFE.putFloat(ptr, value);
    }

    public static void putDouble(LLVMAddress addr, double value) {
        putDouble(addr.getVal(), value);
    }

    public static void putDouble(long ptr, double value) {
        assert ptr != 0;
        UNSAFE.putDouble(ptr, value);
    }

    public static void put80BitFloat(LLVMAddress addr, LLVM80BitFloat value) {
        putByteArray(addr, value.getBytes());
    }

    public static void put80BitFloat(long ptr, LLVM80BitFloat value) {
        putByteArray(ptr, value.getBytes());
    }

    public static void putAddress(LLVMAddress addr, LLVMAddress value) {
        putAddress(addr.getVal(), value);
    }

    public static void putAddress(LLVMAddress addr, long ptrValue) {
        putAddress(addr.getVal(), ptrValue);
    }

    public static void putAddress(long ptr, LLVMAddress value) {
        putAddress(ptr, value.getVal());
    }

    public static void putAddress(long ptr, long ptrValue) {
        assert ptr != 0;
        UNSAFE.putAddress(ptr, ptrValue);
    }

    public static LLVMI32Vector getI32Vector(LLVMAddress addr, int size) {
        return LLVMI32Vector.readVectorFromMemory(addr, size);
    }

    public static LLVMI8Vector getI8Vector(LLVMAddress addr, int size) {
        return LLVMI8Vector.readVectorFromMemory(addr, size);
    }

    public static LLVMI1Vector getI1Vector(LLVMAddress addr, int size) {
        return LLVMI1Vector.readVectorFromMemory(addr, size);
    }

    public static LLVMI16Vector getI16Vector(LLVMAddress addr, int size) {
        return LLVMI16Vector.readVectorFromMemory(addr, size);
    }

    public static LLVMI64Vector getI64Vector(LLVMAddress addr, int size) {
        return LLVMI64Vector.readVectorFromMemory(addr, size);
    }

    public static LLVMFloatVector getFloatVector(LLVMAddress addr, int size) {
        return LLVMFloatVector.readVectorFromMemory(addr, size);
    }

    public static LLVMDoubleVector getDoubleVector(LLVMAddress addr, int size) {
        return LLVMDoubleVector.readVectorFromMemory(addr, size);
    }

    // watch out for casts such as I32* to I32Vector* when changing the way how vectors are
    // implemented
    public static void putVector(LLVMAddress addr, LLVMDoubleVector vector) {
        LLVMDoubleVector.writeVectorToMemory(addr, vector);
    }

    public static void putVector(LLVMAddress addr, LLVMFloatVector vector) {
        LLVMFloatVector.writeVectorToMemory(addr, vector);
    }

    public static void putVector(LLVMAddress addr, LLVMI16Vector vector) {
        LLVMI16Vector.writeVectorToMemory(addr, vector);
    }

    public static void putVector(LLVMAddress addr, LLVMI1Vector vector) {
        LLVMI1Vector.writeVectorToMemory(addr, vector);
    }

    public static void putVector(LLVMAddress addr, LLVMI32Vector vector) {
        LLVMI32Vector.writeVectorToMemory(addr, vector);
    }

    public static void putVector(LLVMAddress addr, LLVMI64Vector vector) {
        LLVMI64Vector.writeVectorToMemory(addr, vector);
    }

    public static void putVector(LLVMAddress addr, LLVMI8Vector vector) {
        LLVMI8Vector.writeVectorToMemory(addr, vector);
    }

}
