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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedMalloc.ManagedMallocObject;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

public abstract class LLVMDirectLoadNode {

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class LLVMIVarBitDirectLoadNode extends LLVMExpressionNode {

        public abstract int getBitWidth();

        @Specialization
        public LLVMIVarBit executeI64(LLVMAddress addr) {
            return LLVMMemory.getIVarBit(addr, getBitWidth());
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVM80BitFloatDirectLoadNode extends LLVMExpressionNode {

        @Specialization
        public LLVM80BitFloat executeDouble(LLVMAddress addr) {
            return LLVMMemory.get80BitFloat(addr);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFunctionDirectLoadNode extends LLVMExpressionNode {

        @Specialization
        public LLVMFunctionHandle executeAddress(LLVMAddress addr) {
            return new LLVMFunctionHandle(LLVMHeap.getFunctionIndex(addr));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAddressDirectLoadNode extends LLVMExpressionNode {

        @Child protected ToLLVMNode toLLVM = ToLLVMNode.createNode(TruffleObject.class);

        @Specialization
        public LLVMAddress executeAddress(LLVMAddress addr) {
            return LLVMMemory.getAddress(addr);
        }

        @Specialization
        public Object executeManagedMalloc(ManagedMallocObject addr) {
            return addr.get(0);
        }

        @Specialization(guards = "objectIsManagedMalloc(addr)")
        public Object executeIndirectedManagedMalloc(LLVMTruffleObject addr) {
            return toLLVM.executeWithTarget(((ManagedMallocObject) addr.getObject()).get((int) (addr.getOffset() / LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES)));
        }

        @Specialization(guards = "!objectIsManagedMalloc(addr)")
        public Object executeIndirectedForeign(LLVMTruffleObject addr, @Cached("createForeignReadNode()") Node foreignRead) {
            try {
                return toLLVM.executeWithTarget(ForeignAccess.sendRead(foreignRead, addr.getObject(), addr.getOffset()));
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw new UnsupportedOperationException(e);
            }
        }

        @Specialization(guards = "!isManagedMalloc(addr)")
        public Object executeForeign(TruffleObject addr, @Cached("createForeignReadNode()") Node foreignRead) {
            try {
                return toLLVM.executeWithTarget(ForeignAccess.sendRead(foreignRead, addr, 0));
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw new UnsupportedOperationException(e);
            }
        }

        protected boolean objectIsManagedMalloc(LLVMTruffleObject addr) {
            return addr.getObject() instanceof ManagedMallocObject;
        }

        protected boolean isManagedMalloc(TruffleObject addr) {
            return addr instanceof ManagedMallocObject;
        }

        protected Node createForeignReadNode() {
            return Message.READ.createNode();
        }
    }

    public static final class LLVMGlobalVariableDirectLoadNode extends LLVMExpressionNode {

        protected final LLVMGlobalVariableDescriptor descriptor;

        public LLVMGlobalVariableDirectLoadNode(LLVMGlobalVariableDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return LLVMGlobalVariableDescriptor.doLoad(descriptor);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMStructDirectLoadNode extends LLVMExpressionNode {

        @Specialization
        public LLVMAddress executeAddress(LLVMAddress addr) {
            return addr; // we do not actually load the struct into a virtual register
        }
    }

}
