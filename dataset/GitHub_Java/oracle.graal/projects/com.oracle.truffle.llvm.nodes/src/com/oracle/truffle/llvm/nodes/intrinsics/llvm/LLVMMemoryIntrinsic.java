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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMMemoryIntrinsic extends LLVMExpressionNode {

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMMalloc extends LLVMMemoryIntrinsic {

        @Specialization
        public LLVMAddress executeVoid(int size) {
            try {
                return LLVMMemory.allocateMemory(size);
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreter();
                return LLVMAddress.nullPointer();
            }
        }

        @Specialization
        public LLVMAddress executeVoid(long size) {
            try {
                return LLVMMemory.allocateMemory(size);
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreter();
                return LLVMAddress.nullPointer();
            }
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMCalloc extends LLVMMemoryIntrinsic {

        @Specialization
        public LLVMAddress executeVoid(int n, int size) {
            try {
                long length = Math.multiplyExact(n, size);
                LLVMAddress address = LLVMMemory.allocateMemory(length);
                LLVMMemory.memset(address, n * size, (byte) 0);
                return address;
            } catch (OutOfMemoryError | ArithmeticException e) {
                CompilerDirectives.transferToInterpreter();
                return LLVMAddress.nullPointer();
            }
        }

        @Specialization
        public LLVMAddress executeVoid(long n, long size) {
            try {
                long length = Math.multiplyExact(n, size);
                LLVMAddress address = LLVMMemory.allocateMemory(length);
                LLVMMemory.memset(address, n * size, (byte) 0);
                return address;
            } catch (OutOfMemoryError | ArithmeticException e) {
                CompilerDirectives.transferToInterpreter();
                return LLVMAddress.nullPointer();
            }
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMRealloc extends LLVMMemoryIntrinsic {

        @Specialization
        public LLVMAddress executeVoid(LLVMAddress addr, int size) {
            try {
                return LLVMMemory.reallocateMemory(addr, size);
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreter();
                return LLVMAddress.nullPointer();
            }
        }

        @Specialization
        public LLVMAddress executeVoid(LLVMAddress addr, long size) {
            try {
                return LLVMMemory.reallocateMemory(addr, size);
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreter();
                return LLVMAddress.nullPointer();
            }
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMFree extends LLVMMemoryIntrinsic {

        @Specialization
        public Object executeVoid(LLVMAddress address) {
            LLVMMemory.free(address);
            return null;
        }
    }
}
