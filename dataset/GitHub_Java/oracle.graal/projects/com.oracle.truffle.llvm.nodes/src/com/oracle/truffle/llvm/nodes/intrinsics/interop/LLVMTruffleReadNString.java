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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.ToLLVMNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMTruffleReadNString extends LLVMIntrinsic {

    @Specialization
    public Object executeIntrinsic(LLVMAddress value, int n) {
        return getString(value, n);
    }

    @TruffleBoundary
    private static Object getString(LLVMAddress value, int n) {
        long ptr = value.getVal();
        int count = n < 0 ? 0 : n;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append((char) Byte.toUnsignedInt(LLVMMemory.getI8(ptr)));
            ptr += Byte.BYTES;
        }
        return sb.toString();
    }

    @Specialization
    public Object interop(LLVMTruffleObject objectWithOffset, int n,
                    @Cached("createForeignReadNode()") Node foreignRead,
                    @Cached("createToByteNode()") ToLLVMNode toLLVM) {
        long offset = objectWithOffset.getOffset();
        TruffleObject object = objectWithOffset.getObject();
        char[] chars = new char[n];
        for (int i = 0; i < n; i++) {
            Object rawValue;
            try {
                rawValue = ForeignAccess.sendRead(foreignRead, object, offset + n);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
            byte byteValue = (byte) toLLVM.executeWithTarget(rawValue);
            chars[i] = (char) Byte.toUnsignedInt(byteValue);
        }
        return new String(chars);
    }

    protected ToLLVMNode createToByteNode() {
        return ToLLVMNode.createNode(byte.class);
    }

    protected Node createForeignReadNode() {
        return Message.READ.createNode();
    }

}
