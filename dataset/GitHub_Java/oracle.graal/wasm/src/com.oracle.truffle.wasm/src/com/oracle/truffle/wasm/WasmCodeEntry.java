/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;

public class WasmCodeEntry {
    @CompilationFinal private int functionIndex;
    @CompilationFinal(dimensions = 1) private byte[] data;
    @CompilationFinal(dimensions = 1) private FrameSlot[] localSlots;
    @CompilationFinal(dimensions = 1) private FrameSlot[] stackSlots;
    @CompilationFinal(dimensions = 1) private byte[] localTypes;
    @CompilationFinal(dimensions = 1) private byte[] byteConstants;
    @CompilationFinal(dimensions = 1) private int[] intConstants;
    @CompilationFinal(dimensions = 1) private long[] longConstants;
    @CompilationFinal(dimensions = 2) private int[][] branchTables;

    public WasmCodeEntry(int functionIndex, byte[] data) {
        this.functionIndex = functionIndex;
        this.data = data;
        this.localSlots = null;
        this.stackSlots = null;
        this.localTypes = null;
        this.byteConstants = null;
        this.intConstants = null;
        this.longConstants = null;
    }

    public byte[] data() {
        return data;
    }

    public FrameSlot localSlot(int index) {
        return localSlots[index];
    }

    public FrameSlot stackSlot(int index) {
        return stackSlots[index];
    }

    public void initLocalSlots(FrameDescriptor frameDescriptor) {
        localSlots = new FrameSlot[localTypes.length];
        for (int i = 0; i != localTypes.length; ++i) {
            FrameSlot localSlot = frameDescriptor.addFrameSlot(i, frameSlotKind(localTypes[i]));
            localSlots[i] = localSlot;
        }
    }

    private static FrameSlotKind frameSlotKind(byte valueType) {
        switch (valueType) {
            case ValueTypes.I32_TYPE:
                return FrameSlotKind.Int;
            case ValueTypes.I64_TYPE:
                return FrameSlotKind.Long;
            case ValueTypes.F32_TYPE:
                return FrameSlotKind.Float;
            case ValueTypes.F64_TYPE:
                return FrameSlotKind.Double;
            default:
                Assert.fail(String.format("Unknown value type: 0x%02X", valueType));
        }
        return null;
    }

    public void initStackSlots(FrameDescriptor frameDescriptor, int maxStackSize) {
        stackSlots = new FrameSlot[maxStackSize];
        for (int i = 0; i != maxStackSize; ++i) {
            FrameSlot stackSlot = frameDescriptor.addFrameSlot(localSlots.length + i, FrameSlotKind.Long);
            stackSlots[i] = stackSlot;
        }
    }

    public void setLocalTypes(byte[] localTypes) {
        this.localTypes = localTypes;
    }

    public byte localType(int index) {
        return localTypes[index];
    }

    public byte byteConstant(int index) {
        return byteConstants[index];
    }

    public void setByteConstants(byte[] byteConstants) {
        this.byteConstants = byteConstants;
    }

    public int intConstant(int index) {
        return intConstants[index];
    }

    public void setIntConstants(int[] intConstants) {
        this.intConstants = intConstants;
    }

    public long longConstant(int index) {
        return longConstants[index];
    }

    public int longConstantAsInt(int index) {
        return (int) longConstants[index];
    }

    public float longConstantAsFloat(int index) {
        return Float.intBitsToFloat(longConstantAsInt(index));
    }

    public double longConstantAsDouble(int index) {
        return Double.longBitsToDouble(longConstants[index]);
    }

    public void setLongConstants(long[] longConstants) {
        this.longConstants = longConstants;
    }

    public int[] branchTable(int index) {
        return branchTables[index];
    }

    public void setBranchTables(int[][] branchTables) {
        this.branchTables = branchTables;
    }

    public int numLocals() {
        return localTypes.length;
    }

    public int functionIndex() {
        return functionIndex;
    }

    @Override
    public String toString() {
        return "wasm-code-entry-" + functionIndex;
    }
}
