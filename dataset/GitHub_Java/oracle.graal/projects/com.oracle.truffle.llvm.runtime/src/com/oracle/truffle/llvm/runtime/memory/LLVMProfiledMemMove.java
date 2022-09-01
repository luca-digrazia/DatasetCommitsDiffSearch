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
package com.oracle.truffle.llvm.runtime.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.LLVMAddress;

public class LLVMProfiledMemMove {
    protected static final long MAX_JAVA_LEN = 256;

    @CompilationFinal private boolean inJava = true;

    public void memmove(LLVMAddress target, LLVMAddress source, long length) {
        if (inJava) {
            if (length <= MAX_JAVA_LEN) {
                long targetPointer = target.getVal();
                long sourcePointer = source.getVal();

                assert targetPointer >= 0 && sourcePointer >= 0;
                if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, targetPointer == sourcePointer)) {
                    // nothing todo
                } else if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, Long.compareUnsigned(targetPointer - sourcePointer, length) >= 0)) {
                    copyForward(targetPointer, sourcePointer, length);
                } else {
                    copyBackward(targetPointer, sourcePointer, length);
                }
                return;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                inJava = false;
            }
        }

        nativeMemCopy(target, source, length);
    }

    private static void copyForward(long target, long source, long length) {
        long targetPointer = target;
        long sourcePointer = source;
        long i64ValuesToWrite = length >> 3;
        for (int i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i64ValuesToWrite); i++) {
            long v64 = LLVMMemory.getI64(sourcePointer);
            LLVMMemory.putI64(targetPointer, v64);
            targetPointer += 8;
            sourcePointer += 8;
        }

        long i8ValuesToWrite = length & 0x07;
        for (int i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i8ValuesToWrite); i++) {
            byte value = LLVMMemory.getI8(sourcePointer);
            LLVMMemory.putI8(targetPointer, value);
            targetPointer++;
            sourcePointer++;
        }
    }

    private static void copyBackward(long target, long source, long length) {
        long targetPointer = target + length;
        long sourcePointer = source + length;
        long i64ValuesToWrite = length >> 3;
        for (int i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i64ValuesToWrite); i++) {
            targetPointer -= 8;
            sourcePointer -= 8;
            long v64 = LLVMMemory.getI64(sourcePointer);
            LLVMMemory.putI64(targetPointer, v64);
        }

        long i8ValuesToWrite = length & 0x07;
        for (int i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i8ValuesToWrite); i++) {
            targetPointer--;
            sourcePointer--;
            byte value = LLVMMemory.getI8(sourcePointer);
            LLVMMemory.putI8(targetPointer, value);
        }
    }

    @SuppressWarnings("deprecation")
    private static void nativeMemCopy(LLVMAddress target, LLVMAddress source, long length) {
        LLVMMemory.copyMemory(source.getVal(), target.getVal(), length);
    }
}
