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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMExitException;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public class LLVMGlobalRootNode extends RootNode {

    private final DirectCallNode startFunction;
    private final int mainFunctionType;
    private final String applicationPath;
    private final ContextReference<LLVMContext> ctxRef;

    public LLVMGlobalRootNode(LLVMLanguage language, FrameDescriptor descriptor, LLVMFunctionDescriptor mainFunctionDescriptor, CallTarget startFunction, String applicationPath) {
        super(language, descriptor);
        this.startFunction = Truffle.getRuntime().createDirectCallNode(startFunction);
        this.mainFunctionType = getMainFunctionType(mainFunctionDescriptor);
        this.applicationPath = applicationPath;
        this.ctxRef = language.getContextReference();
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeWithoutFrame();
    }

    @TruffleBoundary
    private Object executeWithoutFrame() {
        try (StackPointer basePointer = getContext().getThreadingStack().getStack().newFrame()) {
            try {
                TruffleObject appPath = (TruffleObject) ctxRef.get().getEnv().asGuestValue(applicationPath.getBytes());
                LLVMManagedPointer applicationPathObj = LLVMManagedPointer.create(LLVMTypedForeignObject.createUnknown(appPath));
                Object[] realArgs = new Object[]{basePointer, mainFunctionType, applicationPathObj};
                Object result = startFunction.call(realArgs);
                getContext().awaitThreadTermination();
                return (int) result;
            } catch (LLVMExitException e) {
                LLVMContext context = getContext();
                // if any variant of exit or abort was called, we know that all the necessary
                // cleanup was already done
                context.setCleanupNecessary(false);
                context.awaitThreadTermination();
                return e.getReturnCode();
            } finally {
                // if not done already, we want at least call a shutdown command
                getContext().shutdownThreads();
            }
        }
    }

    /**
     * Identify the signature of the main method so that crt0.c:_start can invoke the main method
     * with the correct signature. This is necessary because languages like Rust use non-standard C
     * main functions.
     */
    private static int getMainFunctionType(LLVMFunctionDescriptor mainFunctionDescriptor) {
        CompilerAsserts.neverPartOfCompilation();
        Type returnType = mainFunctionDescriptor.getType().getReturnType();
        Type[] argumentTypes = mainFunctionDescriptor.getType().getArgumentTypes();
        if (argumentTypes.length > 0 && argumentTypes[0] instanceof PrimitiveType) {
            if (((PrimitiveType) argumentTypes[0]).getPrimitiveKind() == PrimitiveKind.I64) {
                return 1;
            }
        }

        if (returnType instanceof VoidType) {
            return 2;
        } else if (returnType instanceof PrimitiveType) {
            switch (((PrimitiveType) returnType).getPrimitiveKind()) {
                case I8:
                    return 3;
                case I16:
                    return 4;
                case I32:
                    return 0;
                case I64:
                    return 5;
            }
        }

        throw new AssertionError("Unexpected main method signature");
    }

    public final LLVMContext getContext() {
        return getRootNode().getLanguage(LLVMLanguage.class).getContextReference().get();
    }
}
