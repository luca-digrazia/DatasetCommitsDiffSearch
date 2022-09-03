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
package com.oracle.truffle.llvm.types;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

public final class LLVMFunctionDescriptor implements TruffleObject, Comparable<LLVMFunctionDescriptor> {

    public enum LLVMRuntimeType {
        I1,
        I8,
        I16,
        I32,
        I64,
        I_VAR_BITWIDTH,
        HALF,
        FLOAT,
        DOUBLE,
        X86_FP80,
        ADDRESS,
        STRUCT,
        ARRAY,
        FUNCTION_ADDRESS,
        I1_VECTOR,
        I8_VECTOR,
        I16_VECTOR,
        I32_VECTOR,
        I64_VECTOR,
        FLOAT_VECTOR,
        DOUBLE_VECTOR,
        VOID,
        ILLEGAL;
    }

    private final String functionName;
    private final LLVMRuntimeType returnType;
    private final LLVMRuntimeType[] parameterTypes;
    private final boolean hasVarArgs;
    private final int functionId;

    private LLVMFunctionDescriptor(String name, LLVMRuntimeType llvmReturnType, LLVMRuntimeType[] llvmParamTypes, boolean varArgs, int functionId) {
        this.functionName = name;
        this.returnType = llvmReturnType;
        this.parameterTypes = llvmParamTypes;
        this.hasVarArgs = varArgs;
        this.functionId = functionId;
    }

    public static LLVMFunctionDescriptor create(String name, LLVMRuntimeType llvmReturnType, LLVMRuntimeType[] llvmParamTypes, boolean varArgs, int functionId) {
        CompilerAsserts.neverPartOfCompilation();
        LLVMFunctionDescriptor func = new LLVMFunctionDescriptor(name, llvmReturnType, llvmParamTypes, varArgs, functionId);
        return func;
    }

    public static LLVMFunctionDescriptor createFromName(String name) {
        return create(name, LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false, -1);
    }

    public String getName() {
        return functionName;
    }

    public LLVMRuntimeType getReturnType() {
        return returnType;
    }

    public LLVMRuntimeType[] getParameterTypes() {
        return parameterTypes;
    }

    public boolean isVarArgs() {
        return hasVarArgs;
    }

    /**
     * Gets an unique index for a function descriptor.
     *
     * @return the function's index
     */
    public int getFunctionIndex() {
        return functionId;
    }

    @Override
    public String toString() {
        return getName() + " " + getFunctionIndex();
    }

    @Override
    public ForeignAccess getForeignAccess() {
        throw new AssertionError();
    }

    @Override
    public int compareTo(LLVMFunctionDescriptor o) {
        return getName().compareTo(o.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode() + 11 * functionId;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVMFunctionDescriptor)) {
            return false;
        } else {
            LLVMFunctionDescriptor other = (LLVMFunctionDescriptor) obj;
            return getFunctionIndex() == other.getFunctionIndex();
        }
    }

}
