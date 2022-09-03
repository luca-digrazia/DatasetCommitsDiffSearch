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
package com.oracle.truffle.llvm.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMAMD64SetNode {
    private static final byte ZERO = 0;
    private static final byte ONE = 1;

    @NodeChild("cf")
    @NodeChild("zf")
    public abstract static class LLVMAMD64SetaNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean cf, boolean zf) {
            return !cf && !zf ? ONE : ZERO;
        }
    }

    @NodeChild("zf")
    public abstract static class LLVMAMD64SetzNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean zf) {
            return zf ? ONE : ZERO;
        }
    }

    @NodeChild("zf")
    public abstract static class LLVMAMD64SetnzNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean zf) {
            return zf ? ONE : ZERO;
        }
    }

    @NodeChild("cf")
    @NodeChild("zf")
    public abstract static class LLVMAMD64SetorNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean cf, boolean zf) {
            return cf || zf ? ONE : ZERO;
        }
    }

    @NodeChild("zf")
    @NodeChild("sf")
    @NodeChild("of")
    public abstract static class LLVMAMD64SetgNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean zf, boolean sf, boolean of) {
            return !zf && sf == of ? ONE : ZERO;
        }
    }

    @NodeChild("sf")
    @NodeChild("of")
    public abstract static class LLVMAMD64SeteqNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean sf, boolean of) {
            return sf == of ? ONE : ZERO;
        }
    }

    @NodeChild("sf")
    @NodeChild("of")
    public abstract static class LLVMAMD64SetneNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean sf, boolean of) {
            return sf != of ? ONE : ZERO;
        }
    }

    @NodeChild("zf")
    @NodeChild("sf")
    @NodeChild("of")
    public abstract static class LLVMAMD64SetleNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean zf, boolean sf, boolean of) {
            return zf || sf != of ? ONE : ZERO;
        }
    }

    @NodeChild("cf")
    @NodeChild("zf")
    public abstract static class LLVMAMD64SetandNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean cf, boolean zf) {
            return cf && zf ? ONE : ZERO;
        }
    }
}
