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
package com.oracle.truffle.llvm.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteValueNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(value = "rax", type = LLVMExpressionNode.class), @NodeChild(value = "rdi", type = LLVMExpressionNode.class), @NodeChild(value = "df", type = LLVMExpressionNode.class)})
public abstract class LLVMAMD64StosNode extends LLVMExpressionNode {
    @Child protected LLVMStoreNode store;
    @Child protected LLVMAMD64WriteValueNode writeRDI;

    public LLVMAMD64StosNode(LLVMAMD64WriteValueNode writeRDI) {
        this.writeRDI = writeRDI;
    }

    public abstract static class LLVMAMD64StosbNode extends LLVMAMD64StosNode {
        public LLVMAMD64StosbNode(LLVMAMD64WriteValueNode writeRDI) {
            super(writeRDI);
            store = LLVMI8StoreNodeGen.create();
        }

        @Specialization
        protected Object executeI8(VirtualFrame frame, byte al, long rdi, boolean df) {
            store.executeWithTarget(frame, LLVMAddress.fromLong(rdi), al);
            writeRDI.execute(frame, rdi + (df ? -1 : 1));
            return null;
        }

        @Specialization
        protected Object executeI8(VirtualFrame frame, byte al, LLVMAddress rdi, boolean df) {
            store.executeWithTarget(frame, rdi, al);
            writeRDI.execute(frame, rdi.increment(df ? -1 : 1));
            return null;
        }
    }
}
