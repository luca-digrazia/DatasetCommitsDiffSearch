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
package com.oracle.truffle.llvm.runtime.memory;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Implements a stack that grows from the top to the bottom. The stack is allocated lazily when it
 * is accessed for the first time.
 */
public final class LLVMStack {

    public static final String FRAME_ID = "<stackpointer>";

    private final int stackSize;

    private long lowerBounds;
    private long upperBounds;
    private boolean isAllocated;

    private long stackPointer;
    private long uniquesRegionPointer;

    public LLVMStack(int stackSize) {
        this.stackSize = stackSize;

        lowerBounds = 0;
        upperBounds = 0;
        stackPointer = 0;
        isAllocated = false;
    }

    public final class StackPointer implements AutoCloseable {
        private long basePointer;
        private long uniquesRegionBasePointer;

        private StackPointer(long basePointer, long uniquesRegionBasePointer) {
            this.basePointer = basePointer;
            this.uniquesRegionBasePointer = uniquesRegionBasePointer;
        }

        public long get(LLVMMemory memory) {
            if (basePointer == 0) {
                basePointer = getStackPointer(memory);
                stackPointer = basePointer;
            }
            return stackPointer;
        }

        public void set(long sp) {
            stackPointer = sp;
        }

        public long getUniquesRegionPointer() {
            return uniquesRegionPointer;
        }

        public void setUniquesRegionPointer(long urp) {
            uniquesRegionPointer = urp;
        }

        @Override
        public void close() {
            if (basePointer != 0) {
                stackPointer = basePointer;
                uniquesRegionPointer = uniquesRegionBasePointer;
            }
        }

        public StackPointer newFrame() {
            return new StackPointer(stackPointer, uniquesRegionPointer);
        }
    }

    public static final class UniquesRegion {
        private long currentSlotPointer = 0;
        private int alignment = 1;

        public UniqueSlot addSlot(int slotSize, int slotAlignment) {
            CompilerAsserts.neverPartOfCompilation();
            currentSlotPointer = getAlignedAllocation(currentSlotPointer, slotSize, slotAlignment);
            // maximum of current alignment, slot alignment and the alignment masking slot size
            alignment = setMSB(alignment | slotAlignment | setMSB(slotSize) << 1);
            return new UniqueSlot(currentSlotPointer);
        }

        void allocate(VirtualFrame frame, LLVMMemory memory, FrameSlot stackPointerSlot) {
            StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, stackPointerSlot);
            long stackPointer = basePointer.get(memory);
            assert stackPointer != 0;
            long uniquesRegionBasePointer = getAlignedBasePointer(stackPointer);
            long uniquesRegionSize = stackPointer - uniquesRegionBasePointer - currentSlotPointer;
            basePointer.setUniquesRegionPointer(uniquesRegionBasePointer);
            allocateStackMemory(memory, basePointer, uniquesRegionSize, NO_ALIGNMENT_REQUIREMENTS);
        }

        long getAlignedBasePointer(long address) {
            assert alignment != 0 && powerOfTwo(alignment);
            return address & -alignment;
        }

        public final class UniqueSlot {
            private final long address;

            private UniqueSlot(long address) {
                this.address = address;
            }

            public long toPointer(VirtualFrame frame, FrameSlot stackPointerSlot) {
                StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, stackPointerSlot);
                long uniquesRegionPointer = basePointer.getUniquesRegionPointer();
                assert uniquesRegionPointer != 0;
                return uniquesRegionPointer + address;
            }
        }

    }

    @TruffleBoundary
    private void allocate(LLVMMemory memory) {
        long size = stackSize * 1024L;
        long stackAllocation = memory.allocateMemory(size).asNative();
        lowerBounds = stackAllocation;
        upperBounds = stackAllocation + size;
        isAllocated = true;
        stackPointer = upperBounds;
    }

    private long getStackPointer(LLVMMemory memory) {
        if (!isAllocated) {
            allocate(memory);
        }
        return this.stackPointer;
    }

    public StackPointer newFrame() {
        return new StackPointer(stackPointer, uniquesRegionPointer);
    }

    @TruffleBoundary
    public void free(LLVMMemory memory) {
        if (isAllocated) {
            /*
             * It can be that the stack was never allocated.
             */
            memory.free(lowerBounds);
            lowerBounds = 0;
            upperBounds = 0;
            stackPointer = 0;
            isAllocated = false;
        }
    }

    public static final int NO_ALIGNMENT_REQUIREMENTS = 1;

    public static long allocateStackMemory(VirtualFrame frame, LLVMMemory memory, FrameSlot stackPointerSlot, final long size, final int alignment) {
        StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, stackPointerSlot);
        return allocateStackMemory(memory, basePointer, size, alignment);
    }

    private static long allocateStackMemory(LLVMMemory memory, StackPointer basePointer, final long size, final int alignment) {
        long stackPointer = basePointer.get(memory);
        assert stackPointer != 0;
        long alignedAllocation = getAlignedAllocation(stackPointer, size, alignment);
        basePointer.set(alignedAllocation);
        return alignedAllocation;
    }

    private static long getAlignedAllocation(long address, long size, int alignment) {
        assert size >= 0;
        assert alignment != 0 && powerOfTwo(alignment);
        long alignedAllocation = (address - size) & -alignment;
        assert alignedAllocation <= address;
        return alignedAllocation;
    }

    private static boolean powerOfTwo(int value) {
        return (value & -value) == value;
    }

    // https://www.geeksforgeeks.org/find-significant-set-bit-number/
    private static int setMSB(int value) {
        int n = value;

        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;

        n = n + 1;
        return (n >>> 1);
    }
}
