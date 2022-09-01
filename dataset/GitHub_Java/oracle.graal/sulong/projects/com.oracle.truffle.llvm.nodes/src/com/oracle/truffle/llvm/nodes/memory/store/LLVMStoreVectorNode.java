/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory.store;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

public abstract class LLVMStoreVectorNode extends LLVMStoreNodeCommon {

    private final int vectorLength;

    public LLVMStoreVectorNode(int vectorLength) {
        super(null);
        this.vectorLength = vectorLength;
    }

    public LLVMStoreVectorNode(LLVMSourceLocation sourceLocation, VectorType type) {
        super(sourceLocation);
        this.vectorLength = type.getNumberOfElements();
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMDoubleVector vector) {
        assert vector.getLength() == vectorLength;
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            memory.putDouble(currentPtr, vector.getValue(i));
            currentPtr += DOUBLE_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMDoubleVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMFloatVector vector) {
        assert vector.getLength() == vectorLength;
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            memory.putFloat(currentPtr, vector.getValue(i));
            currentPtr += FLOAT_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMFloatVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI16Vector vector) {
        assert vector.getLength() == vectorLength;
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            memory.putI16(currentPtr, vector.getValue(i));
            currentPtr += I16_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI16Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI1Vector vector) {
        assert vector.getLength() == vectorLength;
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            memory.putI1(currentPtr, vector.getValue(i));
            currentPtr += I1_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI1Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI32Vector vector) {
        assert vector.getLength() == vectorLength;
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            memory.putI32(currentPtr, vector.getValue(i));
            currentPtr += I32_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI32Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI64Vector vector) {
        assert vector.getLength() == vectorLength;
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            memory.putI64(currentPtr, vector.getValue(i));
            currentPtr += I64_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI64Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI8Vector vector) {
        assert vector.getLength() == vectorLength;
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            memory.putI8(currentPtr, vector.getValue(i));
            currentPtr += I8_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI8Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMPointerVector value,
                    @Cached("createPointerStore()") LLVMPointerStoreNode write) {
        assert value.getLength() == vectorLength;
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            write.executeWithTarget(currentPtr, value.getValue(i));
            currentPtr += ADDRESS_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMPointerVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI1Vector value) {
        assert value.getLength() == vectorLength;
        LLVMManagedPointer currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode(ForeignToLLVMType.I1).execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I1_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI8Vector value) {
        assert value.getLength() == vectorLength;
        LLVMManagedPointer currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode(ForeignToLLVMType.I8).execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI16Vector value) {
        assert value.getLength() == vectorLength;
        LLVMManagedPointer currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode(ForeignToLLVMType.I16).execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I16_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI32Vector value) {
        assert value.getLength() == vectorLength;
        LLVMManagedPointer currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode(ForeignToLLVMType.I32).execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I32_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMFloatVector value) {
        assert value.getLength() == vectorLength;
        LLVMManagedPointer currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode(ForeignToLLVMType.FLOAT).execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(FLOAT_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMDoubleVector value) {
        assert value.getLength() == vectorLength;
        LLVMManagedPointer currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode(ForeignToLLVMType.DOUBLE).execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(DOUBLE_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI64Vector value) {
        assert value.getLength() == vectorLength;
        LLVMManagedPointer currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode(ForeignToLLVMType.I64).execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I64_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMPointerVector value) {
        assert value.getLength() == vectorLength;
        LLVMManagedPointer currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode(ForeignToLLVMType.POINTER).execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(ADDRESS_SIZE_IN_BYTES);
        }
    }

    protected static LLVMPointerStoreNode createPointerStore() {
        return LLVMPointerStoreNodeGen.create(null, null);
    }
}
