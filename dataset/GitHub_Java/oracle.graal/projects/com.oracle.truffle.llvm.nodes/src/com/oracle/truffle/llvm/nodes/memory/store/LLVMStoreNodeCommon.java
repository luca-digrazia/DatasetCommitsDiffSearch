/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory.store;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;

public abstract class LLVMStoreNodeCommon extends LLVMStoreNode {

    private final LLVMSourceLocation source;
    @CompilationFinal private LLVMMemory llvmMemory;
    @Child private LLVMForeignWriteNode foreignWriteNode;

    @Child private LLVMDerefHandleGetReceiverNode derefHandleGetReceiverNode;

    public LLVMStoreNodeCommon(LLVMSourceLocation source) {
        this.source = source;
    }

    protected LLVMForeignWriteNode createForeignWrite() {
        return LLVMForeignWriteNodeGen.create();
    }

    @Override
    public LLVMSourceLocation getSourceLocation() {
        return source;
    }

    private Assumption getNoDerefHandleAssumption() {
        return getLLVMMemoryCached().getNoDerefHandleAssumption();
    }

    protected LLVMDerefHandleGetReceiverNode getDerefHandleGetReceiverNode() {
        if (derefHandleGetReceiverNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            derefHandleGetReceiverNode = insert(LLVMDerefHandleGetReceiverNode.create(LLVMMemory.getObjectMask()));
        }
        return derefHandleGetReceiverNode;
    }

    protected LLVMForeignWriteNode getForeignReadNode() {
        if (foreignWriteNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            foreignWriteNode = insert(createForeignWrite());
        }
        return foreignWriteNode;
    }

    protected boolean isAutoDerefHandle(LLVMAddress addr) {
        return !getNoDerefHandleAssumption().isValid() && LLVMMemory.isDerefMemory(addr);
    }

    protected LLVMMemory getLLVMMemoryCached() {
        if (llvmMemory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            llvmMemory = getLLVMMemory();
        }
        return llvmMemory;
    }
}
