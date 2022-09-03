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
package com.oracle.truffle.llvm.nodes.func;

import java.util.LinkedList;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMException;
import com.oracle.truffle.llvm.runtime.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public final class LLVMRethrowNode extends LLVMExpressionNode {

    public static final int RETHROWN_MARKER = Integer.MAX_VALUE;

    @CompilationFinal private LinkedList<LLVMAddress> caughtExceptionStack;
    @Child private LLVMNativeFunctions.SulongSetHandlerCountNode setHandlerCount;
    @Child private LLVMNativeFunctions.SulongGetExceptionPointerNode getExceptionPointer;

    public LinkedList<LLVMAddress> getCaughtExceptionStack() {
        if (caughtExceptionStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            caughtExceptionStack = getContext().getCaughtExceptionStack();
        }
        return caughtExceptionStack;
    }

    public LLVMNativeFunctions.SulongSetHandlerCountNode getSetHandlerCount() {
        if (setHandlerCount == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMContext context = getContext();
            NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
            setHandlerCount = insert(nfiContextExtension.getNativeSulongFunctions().createSetHandlerCount(context));
        }
        return setHandlerCount;
    }

    public LLVMNativeFunctions.SulongGetExceptionPointerNode getGetExceptionPointer() {
        if (getExceptionPointer == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMContext context = getContext();
            NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
            getExceptionPointer = insert(nfiContextExtension.getNativeSulongFunctions().createGetExceptionPointer(context));
        }
        return getExceptionPointer;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        LLVMAddress ptr = peekExceptionToStack();
        LLVMAddress exceptionPointer = getGetExceptionPointer().getExceptionPointer(ptr);
        /*
         * this is not a hack, this is as suggested in the documentation
         */
        getSetHandlerCount().set(ptr, RETHROWN_MARKER);
        throw new LLVMException(exceptionPointer);
    }

    @TruffleBoundary
    private LLVMAddress peekExceptionToStack() {
        return getCaughtExceptionStack().peek();
    }
}
