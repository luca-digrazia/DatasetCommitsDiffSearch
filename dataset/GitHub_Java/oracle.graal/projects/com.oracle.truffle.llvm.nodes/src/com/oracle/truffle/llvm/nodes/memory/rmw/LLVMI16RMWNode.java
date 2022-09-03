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
package com.oracle.truffle.llvm.nodes.memory.rmw;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren(value = {@NodeChild(type = LLVMExpressionNode.class, value = "pointerNode"), @NodeChild(type = LLVMExpressionNode.class, value = "valueNode")})
public abstract class LLVMI16RMWNode extends LLVMExpressionNode {

    public abstract static class LLVMI16RMWXchgNode extends LLVMI16RMWNode {
        @Specialization
        public short execute(LLVMGlobalVariable address, short value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMAddress adr = globalAccess.getNativeLocation(address);
            return LLVMMemory.getAndOpI16(adr, value, (a, b) -> b);
        }

        @Specialization
        public short execute(LLVMAddress address, short value) {
            return LLVMMemory.getAndOpI16(address, value, (a, b) -> b);
        }
    }

    public abstract static class LLVMI16RMWAddNode extends LLVMI16RMWNode {
        @Specialization
        public short execute(LLVMGlobalVariable address, short value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMAddress adr = globalAccess.getNativeLocation(address);
            return LLVMMemory.getAndOpI16(adr, value, (a, b) -> ((short) (a + b)));
        }

        @Specialization
        public short execute(LLVMAddress address, short value) {
            return LLVMMemory.getAndOpI16(address, value, (a, b) -> ((short) (a + b)));
        }
    }

    public abstract static class LLVMI16RMWSubNode extends LLVMI16RMWNode {
        @Specialization
        public short execute(LLVMGlobalVariable address, short value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMAddress adr = globalAccess.getNativeLocation(address);
            return LLVMMemory.getAndOpI16(adr, value, (a, b) -> ((short) (a - b)));
        }

        @Specialization
        public short execute(LLVMAddress address, short value) {
            return LLVMMemory.getAndOpI16(address, value, (a, b) -> ((short) (a - b)));
        }
    }

    public abstract static class LLVMI16RMWAndNode extends LLVMI16RMWNode {
        @Specialization
        public short execute(LLVMGlobalVariable address, short value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMAddress adr = globalAccess.getNativeLocation(address);
            return LLVMMemory.getAndOpI16(adr, value, (a, b) -> ((short) (a & b)));
        }

        @Specialization
        public short execute(LLVMAddress address, short value) {
            return LLVMMemory.getAndOpI16(address, value, (a, b) -> ((short) (a & b)));
        }
    }

    public abstract static class LLVMI16RMWNandNode extends LLVMI16RMWNode {
        @Specialization
        public short execute(LLVMGlobalVariable address, short value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMAddress adr = globalAccess.getNativeLocation(address);
            return LLVMMemory.getAndOpI16(adr, value, (a, b) -> ((short) ~(a & b)));
        }

        @Specialization
        public short execute(LLVMAddress address, short value) {
            return LLVMMemory.getAndOpI16(address, value, (a, b) -> ((short) ~(a & b)));
        }
    }

    public abstract static class LLVMI16RMWOrNode extends LLVMI16RMWNode {
        @Specialization
        public short execute(LLVMGlobalVariable address, short value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMAddress adr = globalAccess.getNativeLocation(address);
            return LLVMMemory.getAndOpI16(adr, value, (a, b) -> ((short) (a | b)));
        }

        @Specialization
        public short execute(LLVMAddress address, short value) {
            return LLVMMemory.getAndOpI16(address, value, (a, b) -> ((short) (a | b)));
        }
    }

    public abstract static class LLVMI16RMWXorNode extends LLVMI16RMWNode {
        @Specialization
        public short execute(LLVMGlobalVariable address, short value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMAddress adr = globalAccess.getNativeLocation(address);
            return LLVMMemory.getAndOpI16(adr, value, (a, b) -> ((short) (a ^ b)));
        }

        @Specialization
        public short execute(LLVMAddress address, short value) {
            return LLVMMemory.getAndOpI16(address, value, (a, b) -> ((short) (a ^ b)));
        }
    }

}
