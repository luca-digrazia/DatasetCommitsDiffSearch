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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMPerformance;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMTruffleExecute extends LLVMIntrinsic {

    @Children private final LLVMExpressionNode[] args;
    @Children private final LLVMDataEscapeNode[] prepareValuesForEscape;
    @Child private Node foreignExecute;
    @Child private ToLLVMNode toLLVM;

    public LLVMTruffleExecute(ToLLVMNode toLLVM, LLVMExpressionNode[] args, Type[] argTypes) {
        this.toLLVM = toLLVM;
        this.args = args;
        this.prepareValuesForEscape = new LLVMDataEscapeNode[args.length];
        for (int i = 0; i < prepareValuesForEscape.length; i++) {
            prepareValuesForEscape[i] = LLVMDataEscapeNodeGen.create(argTypes[i]);
        }
        this.foreignExecute = Message.createExecute(args.length).createNode();
    }

    private static void checkLLVMTruffleObject(LLVMTruffleObject value) {
        if (value.getOffset() != 0 || value.getName() != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalAccessError("Pointee must be unmodified");
        }
    }

    @ExplodeLoop
    private Object doExecute(VirtualFrame frame, TruffleObject value, LLVMContext context) {
        Object[] evaluatedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            evaluatedArgs[i] = prepareValuesForEscape[i].executeWithTarget(args[i].executeGeneric(frame), context);
        }
        try {
            Object rawValue = ForeignAccess.sendExecute(foreignExecute, value, evaluatedArgs);
            return toLLVM.executeWithTarget(rawValue);
        } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "value == cachedValue", limit = "2")
    public Object doIntrinsicCachedTruffleObject(VirtualFrame frame, TruffleObject value, @Cached("value") TruffleObject cachedValue, @Cached("getContext()") LLVMContext context) {
        return doExecute(frame, cachedValue, context);
    }

    @Specialization(replaces = "doIntrinsicCachedTruffleObject")
    public Object doIntrinsicTruffleObject(VirtualFrame frame, TruffleObject value, @Cached("getContext()") LLVMContext context) {
        LLVMPerformance.warn(this);
        return doExecute(frame, value, context);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "value == cachedValue")
    public Object doIntrinsicCachedLLVMTruffleObject(VirtualFrame frame, LLVMTruffleObject value, @Cached("value") LLVMTruffleObject cachedValue, @Cached("getContext()") LLVMContext context) {
        checkLLVMTruffleObject(cachedValue);
        return doExecute(frame, cachedValue.getObject(), context);
    }

    @Specialization(replaces = "doIntrinsicCachedLLVMTruffleObject")
    public Object doIntrinsicLLVMTruffleObject(VirtualFrame frame, LLVMTruffleObject value, @Cached("getContext()") LLVMContext context) {
        LLVMPerformance.warn(this);
        checkLLVMTruffleObject(value);
        return doExecute(frame, value.getObject(), context);
    }
}
