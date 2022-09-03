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
package com.oracle.truffle.llvm.nodes.memory.literal;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMForeignWriteNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMForeignWriteNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

@NodeChild(value = "address", type = LLVMExpressionNode.class)
public abstract class LLVMI1ArrayLiteralNode extends LLVMExpressionNode {

    @Children private final LLVMExpressionNode[] values;
    private final int stride;

    public LLVMI1ArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
        this.values = values;
        this.stride = stride;
    }

    @Specialization
    protected LLVMAddress write(VirtualFrame frame, LLVMGlobal global,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        return writeI8(frame, globalAccess.executeWithTarget(global), memory);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMAddress writeI8(VirtualFrame frame, LLVMAddress addr,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        long currentPtr = addr.getVal();
        for (int i = 0; i < values.length; i++) {
            boolean currentValue = LLVMTypesGen.asBoolean(values[i].executeGeneric(frame));
            memory.putI1(currentPtr, currentValue);
            currentPtr += stride;
        }
        return addr;
    }

    protected LLVMForeignWriteNode createForeignWrite() {
        return LLVMForeignWriteNodeGen.create(PrimitiveType.I1);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMTruffleObject foreignWriteI1(VirtualFrame frame, LLVMTruffleObject addr,
                    @Cached("createForeignWrite()") LLVMForeignWriteNode foreignWrite) {
        LLVMTruffleObject currentPtr = addr;
        for (int i = 0; i < values.length; i++) {
            boolean currentValue = LLVMTypesGen.asBoolean(values[i].executeGeneric(frame));
            foreignWrite.execute(currentPtr, currentValue);
            currentPtr = currentPtr.increment(stride);
        }
        return addr;
    }
}
