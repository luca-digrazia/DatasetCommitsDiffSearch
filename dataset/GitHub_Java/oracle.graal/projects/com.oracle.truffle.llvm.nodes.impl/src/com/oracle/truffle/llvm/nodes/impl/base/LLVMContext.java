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
package com.oracle.truffle.llvm.nodes.impl.base;

import java.util.Map;

import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.ExecutionContext;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.llvm.nativeint.NativeLookup;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.parser.NodeFactoryFacade;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

public class LLVMContext extends ExecutionContext {

    private final LLVMFunctionRegistry registry;

    private LLVMNode[] staticInits;

    private LLVMAddress[] deallocations;

    private final NativeLookup nativeLookup;

    private final LLVMStack stack = new LLVMStack();

    public LLVMContext(NodeFactoryFacade facade, LLVMOptimizationConfiguration optimizationConfig) {
        nativeLookup = new NativeLookup(facade);
        this.registry = new LLVMFunctionRegistry(optimizationConfig);

    }

    public RootCallTarget getFunction(LLVMFunction function) {
        return LLVMFunctionRegistry.lookup(function);
    }

    public LLVMNode[] getStaticInits() {
        return staticInits;
    }

    public LLVMAddress[] getAllocatedGlobalAddresses() {
        return deallocations;
    }

    public LLVMFunctionRegistry getFunctionRegistry() {
        CompilerAsserts.neverPartOfCompilation();
        return registry;
    }

    public void setStaticInits(LLVMNode[] staticInits, LLVMAddress[] deallocations) {
        this.staticInits = staticInits;
        this.deallocations = deallocations;
    }

    public NativeFunctionHandle getNativeHandle(LLVMFunction function, LLVMExpressionNode[] args) {
        return nativeLookup.getNativeHandle(function, args);
    }

    public long getNativeHandle(String functionName) {
        return nativeLookup.getNativeHandle(functionName);
    }

    public Map<LLVMFunction, Integer> getNativeFunctionLookupStats() {
        return nativeLookup.getNativeFunctionLookupStats();
    }

    public LLVMStack getStack() {
        return stack;
    }

}
