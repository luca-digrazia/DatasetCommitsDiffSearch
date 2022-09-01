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
package com.oracle.truffle.llvm.nodes.cast;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.types.LLVMIVarBit;

public abstract class LLVMToVarINode extends LLVMIVarBitNode {

    @NodeChild(value = "fromNode", type = LLVMI8Node.class)
    @NodeField(type = int.class, name = "bits")
    public abstract static class LLVMI8ToIVarNode extends LLVMToVarINode {

        public abstract int getBits();

        @Specialization
        public LLVMIVarBit executeI8(byte from) {
            return LLVMIVarBit.fromByte(getBits(), from);
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI16Node.class)
    @NodeField(type = int.class, name = "bits")
    public abstract static class LLVMI16ToIVarNode extends LLVMToVarINode {

        public abstract int getBits();

        @Specialization
        public LLVMIVarBit executeI16(short from) {
            return LLVMIVarBit.fromShort(getBits(), from);
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI32Node.class)
    @NodeField(type = int.class, name = "bits")
    public abstract static class LLVMI32ToIVarNode extends LLVMToVarINode {

        public abstract int getBits();

        @Specialization
        public LLVMIVarBit executeI32(int from) {
            return LLVMIVarBit.fromInt(getBits(), from);
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI32Node.class)
    @NodeField(type = int.class, name = "bits")
    public abstract static class LLVMI32ToIVarZeroExtNode extends LLVMToVarINode {

        public abstract int getBits();

        @Specialization
        public LLVMIVarBit executeI32(int from) {
            return LLVMIVarBit.createZeroExt(getBits(), from);
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI64Node.class)
    @NodeField(type = int.class, name = "bits")
    public abstract static class LLVMI64ToIVarNode extends LLVMToVarINode {

        public abstract int getBits();

        @Specialization
        public LLVMIVarBit executeI32(long from) {
            return LLVMIVarBit.fromLong(getBits(), from);
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI64Node.class)
    @NodeField(type = int.class, name = "bits")
    public abstract static class LLVMI64ToIVarZeroExtNode extends LLVMToVarINode {

        public abstract int getBits();

        @Specialization
        public LLVMIVarBit executeI32(long from) {
            return LLVMIVarBit.createZeroExt(getBits(), from);
        }

    }

    @NodeChild(value = "fromNode", type = LLVMIVarBitNode.class)
    @NodeField(type = int.class, name = "bits")
    public abstract static class LLVMIVarToIVarNode extends LLVMToVarINode {

        public abstract int getBits();

        @Specialization
        public LLVMIVarBit executeI8(LLVMIVarBit from) {
            return LLVMIVarBit.create(getBits(), from.getSignExtendedBytes());
        }
    }
}
