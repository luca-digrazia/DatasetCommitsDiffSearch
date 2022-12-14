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
package com.oracle.truffle.llvm.runtime.types;

import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public class PointerType implements Type {

    /* This must be mutable to handle circular references */
    private Type type;

    public PointerType(Type type) {
        this.type = type;
    }

    @Override
    public void accept(TypeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public LLVMBaseType getLLVMBaseType() {
        // if the pointeetype is also a pointer it will not resolve to LLVMBaseType.ADDRESS but to
        // its own pointeetype's LLVMBaseType, so we cannot just use type.getLLVMBaseType() but
        // instead have to use instanceof
        if (type instanceof FunctionType) {
            return LLVMBaseType.FUNCTION_ADDRESS;
        } else {
            return LLVMBaseType.ADDRESS;
        }
    }

    public Type getPointeeType() {
        return type;
    }

    public void setPointeeType(Type type) {
        this.type = type;
    }

    @Override
    public int getBits() {
        return Long.BYTES * Byte.SIZE;
    }

    @Override
    public int getAlignment(DataSpecConverter targetDataLayout) {
        if (targetDataLayout != null) {
            return targetDataLayout.getBitAlignment(getLLVMBaseType()) / Byte.SIZE;
        } else {
            return Long.BYTES;
        }
    }

    @Override
    public int getSize(DataSpecConverter targetDataLayout) {
        if (type instanceof FunctionType) {
            return LLVMHeap.FUNCTION_PTR_SIZE_BYTE;
        } else {
            return LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE;
        }
    }

    @Override
    public int getIndexOffset(int index, DataSpecConverter targetDataLayout) {
        return type.getSize(targetDataLayout) * index;
    }

    @Override
    public Type getIndexType(int index) {
        return type;
    }

    @Override
    public LLVMFunctionDescriptor.LLVMRuntimeType getRuntimeType() {
        switch (type.getRuntimeType()) {
            case FUNCTION_ADDRESS:
                return LLVMFunctionDescriptor.LLVMRuntimeType.FUNCTION_ADDRESS;
            case I1:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I1_POINTER;
            case I8:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I8_POINTER;
            case I16:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I16_POINTER;
            case I32:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I32_POINTER;
            case I64:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I64_POINTER;
            case HALF:
                return LLVMFunctionDescriptor.LLVMRuntimeType.HALF_POINTER;
            case FLOAT:
                return LLVMFunctionDescriptor.LLVMRuntimeType.FLOAT_POINTER;
            case DOUBLE:
                return LLVMFunctionDescriptor.LLVMRuntimeType.DOUBLE_POINTER;
            default:
                return LLVMFunctionDescriptor.LLVMRuntimeType.ADDRESS;
        }
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof PointerType && type.equals(((PointerType) obj).type));
    }

    @Override
    public String toString() {
        return String.format("%s*", type);
    }
}
