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
package com.oracle.truffle.llvm.nodes.memory.load;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.LongValueProfile;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.types.IntegerType;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMI64LoadNode extends LLVMExpressionNode {

    @Child protected Node foreignRead = Message.READ.createNode();
    @Child protected ToLLVMNode toLLVM = new ToLLVMNode();

    protected long doForeignAccess(VirtualFrame frame, LLVMTruffleObject addr) {
        try {
            int index = (int) addr.getOffset() / LLVMExpressionNode.I64_SIZE_IN_BYTES;
            Object value = ForeignAccess.sendRead(foreignRead, frame, addr.getObject(), index);
            return toLLVM.convert(frame, value, long.class);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    public abstract static class LLVMI64DirectLoadNode extends LLVMI64LoadNode {

        @Specialization
        public long executeI64(LLVMAddress addr) {
            return LLVMMemory.getI64(addr);
        }

        @Specialization
        public long executeI64(VirtualFrame frame, LLVMTruffleObject addr) {
            return doForeignAccess(frame, addr);
        }

        @Specialization
        public long executeI64(VirtualFrame frame, TruffleObject addr) {
            return executeI64(frame, new LLVMTruffleObject(addr, IntegerType.LONG));
        }

    }

    public abstract static class LLVMI64ProfilingLoadNode extends LLVMI64LoadNode {

        private final LongValueProfile profile = LongValueProfile.createIdentityProfile();

        @Specialization
        public long executeI64(LLVMAddress addr) {
            long val = LLVMMemory.getI64(addr);
            return profile.profile(val);
        }

        @Specialization
        public long executeI64(VirtualFrame frame, LLVMTruffleObject addr) {
            return doForeignAccess(frame, addr);
        }

        @Specialization
        public long executeI64(VirtualFrame frame, TruffleObject addr) {
            return executeI64(frame, new LLVMTruffleObject(addr, IntegerType.LONG));
        }

    }

}
