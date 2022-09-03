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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMGetStackNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.SlowPathForeignToLLVM;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

abstract class LLVMForeignCallNode extends LLVMNode {

    @Child protected LLVMDataEscapeNode prepareValueForEscape;

    protected LLVMForeignCallNode(Type returnType) {
        this.prepareValueForEscape = LLVMDataEscapeNodeGen.create(returnType);
    }

    public static class PackForeignArgumentsNode extends Node {
        @Children private final ForeignToLLVM[] toLLVM;

        PackForeignArgumentsNode(Type[] parameterTypes, int argumentsLength) {
            this.toLLVM = new ForeignToLLVM[argumentsLength];
            for (int i = 0; i < parameterTypes.length; i++) {
                toLLVM[i] = ForeignToLLVM.create(parameterTypes[i]);
            }
            for (int i = parameterTypes.length; i < argumentsLength; i++) {
                toLLVM[i] = ForeignToLLVM.create(ForeignToLLVMType.ANY);
            }
        }

        @ExplodeLoop
        Object[] pack(VirtualFrame frame, Object[] arguments, long stackPointer) {
            assert arguments.length == toLLVM.length;
            final Object[] packedArguments = new Object[1 + toLLVM.length];
            packedArguments[0] = stackPointer;
            for (int i = 0; i < toLLVM.length; i++) {
                packedArguments[i + 1] = toLLVM[i].executeWithTarget(frame, arguments[i]);
            }
            return packedArguments;
        }
    }

    public static PackForeignArgumentsNode createFastPackArguments(LLVMFunctionDescriptor descriptor, int length) {
        checkArgLength(descriptor.getType().getArgumentTypes().length, length);
        return new PackForeignArgumentsNode(descriptor.getType().getArgumentTypes(), length);
    }

    protected static class SlowPackForeignArgumentsNode extends Node {
        @Child private SlowPathForeignToLLVM slowConvert = ForeignToLLVM.createSlowPathNode();

        Object[] pack(LLVMFunctionDescriptor function, LLVMContext context, Object[] arguments, long stackPointer) {
            int actualArgumentsLength = Math.max(arguments.length, function.getType().getArgumentTypes().length);
            final Object[] packedArguments = new Object[1 + actualArgumentsLength];
            packedArguments[0] = stackPointer;
            for (int i = 0; i < function.getType().getArgumentTypes().length; i++) {
                packedArguments[i + 1] = slowConvert.convert(function.getType().getArgumentTypes()[i], context, arguments[i]);
            }
            for (int i = function.getType().getArgumentTypes().length; i < arguments.length; i++) {
                packedArguments[i + 1] = slowConvert.convert(ForeignToLLVMType.ANY, context, arguments[i]);
            }
            return packedArguments;
        }
    }

    public static SlowPackForeignArgumentsNode createSlowPackArguments() {
        return new SlowPackForeignArgumentsNode();
    }

    public abstract Object executeCall(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments);

    @CompilationFinal private LLVMThreadingStack threadingStack = null;

    private LLVMThreadingStack getThreadingStack(LLVMContext context) {
        if (threadingStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            threadingStack = context.getThreadingStack();
        }
        return threadingStack;
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "3", guards = {"function == cachedFunction", "cachedLength == arguments.length"})
    protected Object callDirectCached(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("function") LLVMFunctionDescriptor cachedFunction,
                    @Cached("create(getCallTarget(cachedFunction))") DirectCallNode callNode,
                    @Cached("createFastPackArguments(cachedFunction, arguments.length)") PackForeignArgumentsNode packNode,
                    @Cached("arguments.length") int cachedLength,
                    @Cached("cachedFunction.getContext()") LLVMContext context,
                    @Cached("cachedFunction.needsStackPointer()") boolean needsStackPointer,
                    @Cached("create()") LLVMGetStackNode getStack) {
        assert !(cachedFunction.getType().getReturnType() instanceof StructureType);
        return directCall(frame, arguments, callNode, packNode, getStack, context, needsStackPointer);
    }

    private Object directCall(VirtualFrame frame, Object[] arguments, DirectCallNode callNode, PackForeignArgumentsNode packNode, LLVMGetStackNode getStack, LLVMContext context,
                    boolean needsStackPointer) {
        Object result;
        if (needsStackPointer) {
            LLVMStack stack = getStack.executeWithTarget(getThreadingStack(context), Thread.currentThread());
            try (StackPointer stackPointer = stack.takeStackPointer()) {
                result = callNode.call(packNode.pack(frame, arguments, stackPointer.get()));
            }
        } else {
            result = callNode.call(packNode.pack(frame, arguments, 0));
        }
        return prepareValueForEscape.executeWithTarget(result, context);
    }

    @Specialization(replaces = "callDirectCached")
    protected Object callIndirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode,
                    @Cached("createSlowPackArguments()") SlowPackForeignArgumentsNode slowPack,
                    @Cached("create()") LLVMGetStackNode getStack) {
        assert !(function.getType().getReturnType() instanceof StructureType);
        LLVMStack stack = getStack.executeWithTarget(function.getContext().getThreadingStack(), Thread.currentThread());
        Object result;
        try (StackPointer stackPointer = stack.takeStackPointer()) {
            result = callNode.call(getCallTarget(function), slowPack.pack(function, function.getContext(), arguments, stackPointer.get()));
        }
        return prepareValueForEscape.executeWithTarget(result, function.getContext());
    }

    protected CallTarget getCallTarget(LLVMFunctionDescriptor function) {
        return function.getLLVMIRFunction();
    }

    private static void checkArgLength(int minLength, int actualLength) {
        if (actualLength < minLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throwArgLengthException(minLength, actualLength);
        }
    }

    private static void throwArgLengthException(int minLength, int actualLength) {
        throw ArityException.raise(minLength, actualLength);
    }
}
