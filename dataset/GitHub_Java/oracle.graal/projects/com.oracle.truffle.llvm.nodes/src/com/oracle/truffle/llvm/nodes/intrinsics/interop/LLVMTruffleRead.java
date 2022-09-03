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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMPerformance;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;

public abstract class LLVMTruffleRead extends LLVMIntrinsic {

    private static void checkLLVMTruffleObject(LLVMTruffleObject value) {
        if (value.getOffset() != 0 || value.getName() != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalAccessError("Pointee must be unmodified");
        }
    }

    private static Object doRead(VirtualFrame frame, TruffleObject value, LLVMAddress id, Node foreignRead, ToLLVMNode toLLVM) {
        String name = LLVMTruffleIntrinsicUtil.readString(id);
        return doRead(frame, value, name, foreignRead, toLLVM);
    }

    private static Object doRead(VirtualFrame frame, TruffleObject value, String name, Node foreignRead, ToLLVMNode toLLVM) {
        try {
            Object rawValue = ForeignAccess.sendRead(foreignRead, frame, value, name);
            return toLLVM.executeWithTarget(frame, rawValue);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object doReadIdx(VirtualFrame frame, TruffleObject value, int id, Node foreignRead, ToLLVMNode toLLVM) {
        try {
            Object rawValue = ForeignAccess.sendRead(foreignRead, frame, value, id);
            return toLLVM.executeWithTarget(frame, rawValue);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleReadFromName extends LLVMTruffleRead {

        @Child protected Node foreignRead = Message.READ.createNode();
        @Child protected ToLLVMNode toLLVM;

        public LLVMTruffleReadFromName(ToLLVMNode toLLVM) {
            this.toLLVM = toLLVM;
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "2", guards = "constantPointer(id, cachedPtr)")
        public Object executeIntrinsicCached(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id, @Cached("pointerOf(id)") long cachedPtr,
                        @Cached("readString(id)") String cachedId) {
            checkLLVMTruffleObject(value);
            return doRead(frame, value.getObject(), cachedId, foreignRead, toLLVM);
        }

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id) {
            checkLLVMTruffleObject(value);
            LLVMPerformance.warn(this);
            return doRead(frame, value.getObject(), id, foreignRead, toLLVM);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "2", guards = "constantPointer(id, cachedPtr)")
        public Object executeIntrinsicCached(VirtualFrame frame, TruffleObject value, LLVMAddress id, @Cached("pointerOf(id)") long cachedPtr,
                        @Cached("readString(id)") String cachedId) {
            return doRead(frame, value, cachedId, foreignRead, toLLVM);
        }

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
            LLVMPerformance.warn(this);
            return doRead(frame, value, id, foreignRead, toLLVM);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleReadFromIndex extends LLVMTruffleRead {

        @Child protected Node foreignRead = Message.READ.createNode();
        @Child protected ToLLVMNode toLLVM;

        public LLVMTruffleReadFromIndex(ToLLVMNode toLLVM) {
            this.toLLVM = toLLVM;
        }

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, LLVMTruffleObject value, int id) {
            checkLLVMTruffleObject(value);
            return doReadIdx(frame, value.getObject(), id, foreignRead, toLLVM);
        }

        @Specialization
        public Object executeIntrinsic(VirtualFrame frame, TruffleObject value, int id) {
            return doReadIdx(frame, value, id, foreignRead, toLLVM);
        }
    }

}
