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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import java.util.Arrays;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.llvm.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMPerformance;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

abstract class LLVMForeignCallNode extends LLVMNode {

    private final LLVMStack stack;
    @Child private ToLLVMNode slowConvertNode;
    @Child protected LLVMDataEscapeNode prepareValueForEscape;

    protected LLVMForeignCallNode(LLVMStack stack, Type returnType) {
        this.stack = stack;
        this.slowConvertNode = ToLLVMNode.createNode(null);
        this.prepareValueForEscape = LLVMDataEscapeNodeGen.create(returnType);
    }

    public abstract Object executeCall(VirtualFrame frame, LLVMFunction function, Object[] arguments);

    @SuppressWarnings("unused")
    @Specialization(guards = "function.getFunctionIndex() == functionIndex")
    public Object callDirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("function.getFunctionIndex()") int functionIndex,
                    @Cached("create(getCallTarget(function))") DirectCallNode callNode,
                    @Cached("createToLLVMNodes(function)") ToLLVMNode[] toLLVMNodes,
                    @Cached("arguments.length") int cachedLength,
                    @Cached("function.getContext()") LLVMContext context) {
        assert !(function.getType().getReturnType() instanceof StructureType);
        Object result = callNode.call(packArguments(arguments, toLLVMNodes, cachedLength));
        return prepareValueForEscape.executeWithTarget(result, context);
    }

    @Specialization
    public Object callIndirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode, @Cached("arguments.length") int cachedLength) {
        assert !(function.getType().getReturnType() instanceof StructureType);
        LLVMPerformance.warn(this);
        Object result = callNode.call(getCallTarget(function), packArguments(function, arguments, cachedLength));
        return prepareValueForEscape.executeWithTarget(result, function.getContext());
    }

    protected LLVMFunctionDescriptor lookupFunction(LLVMFunctionDescriptor function) {
        return function.getContext().lookup(function);
    }

    // no explodeLoop - length not constant
    private Object[] packArguments(LLVMFunctionDescriptor function, Object[] arguments, int cachedLength) {
        if (arguments.length != cachedLength) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException();
        }
        int actualArgumentsLength = Math.max(cachedLength, function.getType().getArgumentTypes().length);
        final Object[] packedArguments = new Object[1 + actualArgumentsLength];
        packedArguments[0] = stack.getUpperBounds();
        for (int i = 0; i < function.getType().getArgumentTypes().length; i++) {
            packedArguments[i + 1] = slowConvertNode.slowConvert(arguments[i], ToLLVMNode.convert(function.getType().getArgumentTypes()[i]));
        }
        for (int i = function.getType().getArgumentTypes().length; i < cachedLength; i++) {
            packedArguments[i + 1] = arguments[i];
        }
        return packedArguments;
    }

    protected CallTarget getCallTarget(LLVMFunctionDescriptor function) {
        return function.getCallTarget();
    }

    private static void checkArgLength(int minLength, Object[] arguments) {
        if (arguments.length < minLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throwArgLengthException(minLength, arguments);
        }
    }

    private static void throwArgLengthException(int minLength, Object[] arguments) {
        StringBuilder sb = new StringBuilder();
        sb.append("At least ").append(minLength).append(" arguments expected, but only ").append(arguments.length).append(" arguments received.");
        sb.append(" Arguments=").append(Arrays.toString(arguments));
        throw new IllegalStateException(sb.toString());
    }

    @ExplodeLoop
    private Object[] packArguments(Object[] arguments, ToLLVMNode[] toLLVMNodes, int cachedLength) {
        if (arguments.length != cachedLength) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException();
        }
        int actualArgumentsLength = Math.max(cachedLength, toLLVMNodes.length);
        final Object[] packedArguments = new Object[1 + actualArgumentsLength];
        packedArguments[0] = stack.getUpperBounds();
        checkArgLength(toLLVMNodes.length, arguments);
        for (int i = 0; i < toLLVMNodes.length; i++) {
            packedArguments[i + 1] = toLLVMNodes[i].executeWithTarget(arguments[i]);
        }
        for (int i = toLLVMNodes.length; i < cachedLength; i++) {
            packedArguments[i + 1] = arguments[i];
        }
        return packedArguments;
    }

    protected ToLLVMNode[] createToLLVMNodes(LLVMFunctionDescriptor function) {
        CompilerAsserts.neverPartOfCompilation();
        Type[] parameterTypes = function.getType().getArgumentTypes();
        ToLLVMNode[] toLLVMNodes = new ToLLVMNode[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            toLLVMNodes[i] = ToLLVMNode.createNode(ToLLVMNode.convert(parameterTypes[i]));
        }
        return toLLVMNodes;
    }

}
