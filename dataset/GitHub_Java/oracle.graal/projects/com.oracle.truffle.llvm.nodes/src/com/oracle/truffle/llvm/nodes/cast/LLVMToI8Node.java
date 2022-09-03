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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64Node.LLVMToI64BitNode;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToI8Node extends LLVMExpressionNode {

    @Specialization
    protected byte doI8(LLVMFunctionDescriptor from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        return (byte) toNative.executeWithTarget(from).asNative();
    }

    @Specialization
    protected byte doGlobal(LLVMGlobal from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode access) {
        return (byte) access.executeWithTarget(from).asNative();
    }

    @Child private ForeignToLLVM convert = ForeignToLLVM.create(ForeignToLLVMType.I8);

    @Specialization
    protected byte doManaged(LLVMManagedPointer from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        return (byte) toNative.executeWithTarget(from).asNative();
    }

    @Specialization
    protected byte doLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        return (byte) convert.executeWithTarget(from.getValue());
    }

    public abstract static class LLVMToI8NoZeroExtNode extends LLVMToI8Node {

        @Specialization
        protected byte doI8(boolean from) {
            return from ? (byte) -1 : 0;
        }

        @Specialization
        protected byte doI8(short from) {
            return (byte) from;
        }

        @Specialization
        protected byte doI8(int from) {
            return (byte) from;
        }

        @Specialization
        protected byte doI8(long from) {
            return (byte) from;
        }

        @Specialization
        protected byte doI8(LLVMIVarBit from) {
            return from.getByteValue();
        }

        @Specialization
        protected byte doI8(float from) {
            return (byte) from;
        }

        @Specialization
        protected byte doI8(double from) {
            return (byte) from;
        }

        @Specialization
        protected byte doI8(LLVM80BitFloat from) {
            return from.getByteValue();
        }

        @Specialization
        protected byte doI8(LLVMNativePointer from) {
            return (byte) from.asNative();
        }

        @Specialization
        protected byte doI8(byte from) {
            return from;
        }
    }

    public abstract static class LLVMToI8ZeroExtNode extends LLVMToI8Node {

        @Specialization
        protected byte doI8(boolean from) {
            return (byte) (from ? 1 : 0);
        }

        @Specialization
        protected byte doI8(byte from) {
            return from;
        }

        @Specialization
        protected byte doI8(LLVMIVarBit from) {
            return from.getZeroExtendedByteValue();
        }
    }

    public abstract static class LLVMToI8BitNode extends LLVMToI8Node {

        @Specialization
        protected byte doI8(byte from) {
            return from;
        }

        @Specialization
        protected byte doI1Vector(LLVMI1Vector from) {
            return (byte) LLVMToI64BitNode.castI1Vector(from, Byte.SIZE);
        }

        @Specialization
        protected byte doI8Vector(LLVMI8Vector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return from.getValue(0);
        }
    }
}
