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
package com.oracle.truffle.llvm.nodes.op.arith.vector;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.vector.LLVMI32Vector;

@NodeChildren({@NodeChild(value = "addressNode", type = LLVMAddressNode.class), @NodeChild("leftNode"), @NodeChild("rightNode")})
public abstract class LLVMI32VectorArithmeticNode extends LLVMI32VectorNode {

    public abstract static class LLVMI32VectorAddNode extends LLVMI32VectorArithmeticNode {
        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMAddress target, LLVMI32Vector left, LLVMI32Vector right) {
            return left.add(target, right);
        }
    }

    public abstract static class LLVMI32VectorMulNode extends LLVMI32VectorArithmeticNode {

        @Specialization
        public LLVMI32Vector executeI32Vector(LLVMAddress target, LLVMI32Vector left, LLVMI32Vector right) {
            return left.mul(target, right);
        }

    }

    public abstract static class LLVMI32VectorSubNode extends LLVMI32VectorArithmeticNode {

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMAddress target, LLVMI32Vector left, LLVMI32Vector right) {
            return left.sub(target, right);
        }
    }

    public abstract static class LLVMI32VectorDivNode extends LLVMI32VectorArithmeticNode {

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMAddress target, LLVMI32Vector left, LLVMI32Vector right) {
            return left.div(target, right);
        }
    }

    public abstract static class LLVMI32VectorUDivNode extends LLVMI32VectorArithmeticNode {

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMAddress target, LLVMI32Vector left, LLVMI32Vector right) {
            return left.divUnsigned(target, right);

        }
    }

    public abstract static class LLVMI32VectorRemNode extends LLVMI32VectorArithmeticNode {

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMAddress target, LLVMI32Vector left, LLVMI32Vector right) {
            return left.rem(target, right);
        }
    }

    public abstract static class LLVMI32VectorURemNode extends LLVMI32VectorArithmeticNode {

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMAddress target, LLVMI32Vector left, LLVMI32Vector right) {
            return left.remUnsigned(target, right);
        }
    }

}
