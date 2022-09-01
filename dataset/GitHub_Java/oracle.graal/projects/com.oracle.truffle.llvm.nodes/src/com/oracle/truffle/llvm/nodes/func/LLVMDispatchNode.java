/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

@SuppressWarnings("unused")
public abstract class LLVMDispatchNode extends LLVMNode {

    protected static final int INLINE_CACHE_SIZE = 5;

    private final FunctionType type;
    @CompilationFinal private String signature;

    protected LLVMDispatchNode(FunctionType type) {
        this.type = type;
    }

    private String getSignature() {
        if (signature == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.signature = LLVMContext.getNativeSignature(type, LLVMCallNode.ARG_START_INDEX);
        }
        return signature;
    }

    public abstract Object executeDispatch(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments);

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"function.getFunctionIndex() == cachedFunction.getFunctionIndex()", "cachedFunction.getCallTarget() != null"})
    protected static Object doDirect(LLVMFunctionDescriptor function, Object[] arguments,
                    @Cached("function") LLVMFunctionDescriptor cachedFunction,
                    @Cached("create(cachedFunction.getCallTarget())") DirectCallNode callNode) {
        return callNode.call(arguments);
    }

    @Specialization(replaces = "doDirect", guards = "descriptor.getCallTarget() != null")
    protected static Object doIndirect(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode) {
        return callNode.call(descriptor.getCallTarget(), arguments);
    }

    @Specialization(limit = "10", guards = {"descriptor.getFunctionIndex() == cachedDescriptor.getFunctionIndex()", "descriptor.getCallTarget() == null"})
    protected Object doCachedNative(VirtualFrame frame, LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("descriptor") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @Cached("createNativeCallNode()") Node nativeCall,
                    @Cached("bindSymbol(frame, cachedDescriptor)") TruffleObject cachedBoundFunction) {

        Object[] nativeArgs = prepareNativeArguments(frame, arguments, toNative);
        Object returnValue = LLVMNativeCallUtils.callNativeFunction(getContext(), nativeCall, cachedBoundFunction, nativeArgs, cachedDescriptor);
        return fromNative.executeConvert(frame, returnValue);
    }

    protected TruffleObject bindSymbol(VirtualFrame frame, LLVMFunctionDescriptor descriptor) {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMNativeCallUtils.bindNativeSymbol(LLVMNativeCallUtils.getBindNode(), getContext().resolveAsNativeFunction(descriptor), getSignature());
    }

    @Specialization(replaces = "doCachedNative", guards = "descriptor.getCallTarget() == null")
    protected Object doNative(VirtualFrame frame, LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @Cached("createNativeCallNode()") Node nativeCall,
                    @Cached("getBindNode()") Node bindNode) {

        Object[] nativeArgs = prepareNativeArguments(frame, arguments, toNative);
        TruffleObject boundSymbol = LLVMNativeCallUtils.bindNativeSymbol(bindNode, getContext().resolveAsNativeFunction(descriptor), getSignature());
        Object returnValue = LLVMNativeCallUtils.callNativeFunction(getContext(), nativeCall, boundSymbol, nativeArgs, descriptor);
        return fromNative.executeConvert(frame, returnValue);
    }

    @ExplodeLoop
    private static Object[] prepareNativeArguments(VirtualFrame frame, Object[] arguments, LLVMNativeConvertNode[] toNative) {
        Object[] nativeArgs = new Object[arguments.length - 1];
        for (int i = 1; i < arguments.length; i++) {
            nativeArgs[i - 1] = toNative[i - 1].executeConvert(frame, arguments[i]);
        }
        return nativeArgs;
    }

    protected Node getBindNode() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMNativeCallUtils.getBindNode();
    }

    protected Node createNativeCallNode() {
        CompilerAsserts.neverPartOfCompilation();
        int argCount = type.getArgumentTypes().length - 1;
        return Message.createExecute(argCount).createNode();
    }

    @ExplodeLoop
    protected LLVMNativeConvertNode[] createToNativeNodes() {
        LLVMNativeConvertNode[] ret = new LLVMNativeConvertNode[type.getArgumentTypes().length - 1];
        for (int i = 1; i < type.getArgumentTypes().length; i++) {
            ret[i - 1] = LLVMNativeConvertNode.createToNative(type.getArgumentTypes()[i]);
        }
        return ret;
    }

    protected LLVMNativeConvertNode createFromNativeNode() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMNativeConvertNode.createFromNative(type.getReturnType());
    }
}
