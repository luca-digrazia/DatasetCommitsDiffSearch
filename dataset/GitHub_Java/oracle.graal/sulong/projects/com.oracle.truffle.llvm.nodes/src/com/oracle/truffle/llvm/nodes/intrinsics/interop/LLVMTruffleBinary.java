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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

public final class LLVMTruffleBinary {
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMTruffleIsBoxed extends LLVMIntrinsic {

        @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.createOptional();
        @Child private Node foreignIsBoxed = Message.IS_BOXED.createNode();

        @Specialization
        protected boolean doIntrinsic(LLVMManagedPointer value) {
            TruffleObject foreign = asForeign.execute(value);
            return foreign != null && ForeignAccess.sendIsBoxed(foreignIsBoxed, foreign);
        }

        @Fallback
        public Object fallback(@SuppressWarnings("unused") Object value) {
            return false;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMTruffleIsExecutable extends LLVMIntrinsic {

        @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.createOptional();
        @Child private Node foreignIsExecutable = Message.IS_EXECUTABLE.createNode();

        @Specialization
        protected boolean doIntrinsic(LLVMManagedPointer value) {
            TruffleObject foreign = asForeign.execute(value);
            return foreign != null && ForeignAccess.sendIsExecutable(foreignIsExecutable, foreign);
        }

        @Fallback
        public Object fallback(@SuppressWarnings("unused") Object value) {
            return false;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMTruffleIsNull extends LLVMIntrinsic {

        @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.createOptional();
        @Child private Node foreignIsNull = Message.IS_NULL.createNode();

        @Specialization
        protected boolean doIntrinsic(LLVMManagedPointer value) {
            TruffleObject foreign = asForeign.execute(value);
            return foreign != null && ForeignAccess.sendIsNull(foreignIsNull, foreign);
        }

        @Fallback
        public Object fallback(@SuppressWarnings("unused") Object value) {
            return false;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMTruffleHasSize extends LLVMIntrinsic {

        @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.createOptional();
        @Child private Node foreignHasSize = Message.HAS_SIZE.createNode();

        @Specialization
        protected boolean doIntrinsic(LLVMManagedPointer value) {
            TruffleObject foreign = asForeign.execute(value);
            return foreign != null && ForeignAccess.sendHasSize(foreignHasSize, foreign);
        }

        @Fallback
        public Object fallback(@SuppressWarnings("unused") Object value) {
            return false;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMTruffleHasKeys extends LLVMIntrinsic {

        @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.createOptional();
        @Child private Node foreignHasKeys = Message.HAS_KEYS.createNode();

        @Specialization
        protected boolean doIntrinsic(LLVMManagedPointer value) {
            TruffleObject foreign = asForeign.execute(value);
            return foreign != null && ForeignAccess.sendHasKeys(foreignHasKeys, foreign);
        }

        @Fallback
        public Object fallback(@SuppressWarnings("unused") Object value) {
            return false;
        }
    }
}
