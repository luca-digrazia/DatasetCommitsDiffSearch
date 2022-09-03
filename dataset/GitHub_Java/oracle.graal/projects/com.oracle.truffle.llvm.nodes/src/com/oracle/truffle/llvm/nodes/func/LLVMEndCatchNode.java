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
import com.oracle.truffle.llvm.runtime.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public final class LLVMEndCatchNode extends LLVMExpressionNode {

    @Child private LLVMExpressionNode stackPointer;
    @Child private LLVMLookupDispatchNode dispatch;
    @Child private LLVMNativeFunctions.SulongDecrementHandlerCountNode decHandlerCount;
    @Child private LLVMNativeFunctions.SulongGetHandlerCountNode getHandlerCount;
    @Child private LLVMNativeFunctions.SulongGetThrownObjectNode getThrownObject;
    @Child private LLVMNativeFunctions.SulongGetDestructorNode getDestructor;
    @Child private LLVMNativeFunctions.SulongSetHandlerCountNode setHandlerCount;
    @CompilationFinal private LinkedList<LLVMAddress> caughtExceptionStack;
    @CompilationFinal private LLVMContext cachedContext;

    public LLVMContext getCachedContext() {
        if (cachedContext == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.cachedContext = getContextReference().get();
        }
        return cachedContext;
    }

    public LLVMEndCatchNode(LLVMExpressionNode stackPointer) {
        this.stackPointer = stackPointer;
        this.dispatch = LLVMLookupDispatchNodeGen.create(new FunctionType(VoidType.INSTANCE, new Type[]{new PointerType(null)}, false));
    }

    public LinkedList<LLVMAddress> getCaughtExceptionStack() {
        if (caughtExceptionStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.caughtExceptionStack = getContextReference().get().getCaughtExceptionStack();
        }
        return caughtExceptionStack;
    }

    public LLVMNativeFunctions.SulongDecrementHandlerCountNode getDecHandlerCount() {
        if (decHandlerCount == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMContext context = getContextReference().get();
            NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
            this.decHandlerCount = insert(nfiContextExtension.getNativeSulongFunctions().createDecrementHandlerCount(context));
        }
        return decHandlerCount;
    }

    public LLVMNativeFunctions.SulongGetHandlerCountNode getGetHandlerCount() {
        if (getHandlerCount == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMContext context = getContextReference().get();
            NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
            this.getHandlerCount = insert(nfiContextExtension.getNativeSulongFunctions().createGetHandlerCount(context));
        }
        return getHandlerCount;
    }

    public LLVMNativeFunctions.SulongGetDestructorNode getGetDestructor() {
        if (getDestructor == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMContext context = getContextReference().get();
            NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
            this.getDestructor = insert(nfiContextExtension.getNativeSulongFunctions().createGetDestructor(context));
        }
        return getDestructor;
    }

    public LLVMNativeFunctions.SulongGetThrownObjectNode getGetThrownObject() {
        if (getThrownObject == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMContext context = getContextReference().get();
            NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
            this.getThrownObject = insert(nfiContextExtension.getNativeSulongFunctions().createGetThrownObject(context));
        }
        return getThrownObject;
    }

    public LLVMNativeFunctions.SulongSetHandlerCountNode getSetHandlerCount() {
        if (setHandlerCount == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMContext context = getContextReference().get();
            NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
            this.setHandlerCount = insert(nfiContextExtension.getNativeSulongFunctions().createSetHandlerCount(context));
        }
        return setHandlerCount;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        try {
            LLVMAddress ptr = popExceptionToStack();
            int handlerCount = getGetHandlerCount().get(ptr);
            if (handlerCount == LLVMRethrowNode.RETHROWN_MARKER) {
                // exception was re-thrown, do nothing but reset marker
                getSetHandlerCount().set(ptr, 0);
                return 0;
            }
            getDecHandlerCount().dec(ptr);
            LLVMAddress destructorAddress = getGetDestructor().get(ptr);
            if (getGetHandlerCount().get(ptr) <= 0 && destructorAddress.getVal() != 0) {
                dispatch.executeDispatch(frame, destructorAddress, new Object[]{stackPointer.executeGeneric(frame), getGetThrownObject().getThrownObject(ptr)});
            }
            return null;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @TruffleBoundary
    private LLVMAddress popExceptionToStack() {
        return getCaughtExceptionStack().pop();
    }
}
