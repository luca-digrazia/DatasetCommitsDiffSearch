/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory.store;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

@NodeField(name = "vectorLength", type = int.class)
public abstract class LLVMStoreVectorNode extends LLVMStoreNodeCommon {

    public abstract int getVectorLength();

    protected abstract void executeManaged(LLVMManagedPointer address, Object vector);

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMDoubleVector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putDouble(currentPtr, vector.getValue(i));
            currentPtr += DOUBLE_SIZE_IN_BYTES;
        }
    }

    LLVMStoreVectorNode createRecursive() {
        return LLVMStoreVectorNodeGen.create(null, null, getVectorLength());
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, Object value,
                    @Cached("createRecursive()") LLVMStoreVectorNode store) {
        store.executeManaged(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMFloatVector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putFloat(currentPtr, vector.getValue(i));
            currentPtr += FLOAT_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI16Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI16(currentPtr, vector.getValue(i));
            currentPtr += I16_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI1Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI1(currentPtr, vector.getValue(i));
            currentPtr += I1_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI32Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI32(currentPtr, vector.getValue(i));
            currentPtr += I32_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI64Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI64(currentPtr, vector.getValue(i));
            currentPtr += I64_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI8Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI8(currentPtr, vector.getValue(i));
            currentPtr += I8_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMPointerVector value,
                    @Cached("createPointerStore()") LLVMPointerStoreNode write) {
        assert value.getLength() == getVectorLength();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            write.executeWithTarget(currentPtr, value.getValue(i));
            currentPtr += ADDRESS_SIZE_IN_BYTES;
        }
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI1Vector value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        assert value.getLength() == getVectorLength();
        long curOffset = address.getOffset();
        for (int i = 0; i < getVectorLength(); i++) {
            nativeWrite.writeI8(address.getObject(), curOffset, value.getValue(i) ? (byte) 1 : (byte) 0);
            curOffset += I1_SIZE_IN_BYTES;
        }
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI8Vector value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        assert value.getLength() == getVectorLength();
        long curOffset = address.getOffset();
        for (int i = 0; i < getVectorLength(); i++) {
            nativeWrite.writeI8(address.getObject(), curOffset, value.getValue(i));
            curOffset += I8_SIZE_IN_BYTES;
        }
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI16Vector value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        assert value.getLength() == getVectorLength();
        long curOffset = address.getOffset();
        for (int i = 0; i < getVectorLength(); i++) {
            nativeWrite.writeI16(address.getObject(), curOffset, value.getValue(i));
            curOffset += I16_SIZE_IN_BYTES;
        }
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI32Vector value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        assert value.getLength() == getVectorLength();
        long curOffset = address.getOffset();
        for (int i = 0; i < getVectorLength(); i++) {
            nativeWrite.writeI32(address.getObject(), curOffset, value.getValue(i));
            curOffset += I32_SIZE_IN_BYTES;
        }
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMFloatVector value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        assert value.getLength() == getVectorLength();
        long curOffset = address.getOffset();
        for (int i = 0; i < getVectorLength(); i++) {
            nativeWrite.writeFloat(address.getObject(), curOffset, value.getValue(i));
            curOffset += FLOAT_SIZE_IN_BYTES;
        }
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMDoubleVector value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        assert value.getLength() == getVectorLength();
        long curOffset = address.getOffset();
        for (int i = 0; i < getVectorLength(); i++) {
            nativeWrite.writeDouble(address.getObject(), curOffset, value.getValue(i));
            curOffset += DOUBLE_SIZE_IN_BYTES;
        }
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI64Vector value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        assert value.getLength() == getVectorLength();
        long curOffset = address.getOffset();
        for (int i = 0; i < getVectorLength(); i++) {
            nativeWrite.writeI64(address.getObject(), curOffset, value.getValue(i));
            curOffset += I64_SIZE_IN_BYTES;
        }
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMPointerVector value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        assert value.getLength() == getVectorLength();
        long curOffset = address.getOffset();
        for (int i = 0; i < getVectorLength(); i++) {
            nativeWrite.writePointer(address.getObject(), curOffset, value.getValue(i));
            curOffset += ADDRESS_SIZE_IN_BYTES;
        }
    }

    protected static LLVMPointerStoreNode createPointerStore() {
        return LLVMPointerStoreNodeGen.create(null, null);
    }
}
