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
package com.oracle.truffle.llvm.runtime.memory;

import static com.oracle.truffle.llvm.runtime.memory.LLVMMemory.getUnsafe;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Implements a stack that grows from the top to the bottom.
 */
public final class LLVMStack {

    /**
     * Nodes that access (e.g. alloca) or need (e.g. calls) the stack must be annotated
     * with @StackNode.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface NeedsStack {

    }

    public static final String FRAME_ID = "<stackpointer>";

    @CompilationFinal private long lowerBounds;
    @CompilationFinal private long upperBounds;
    private boolean isFreed = true;

    private long stackPointer;

    public LLVMStack(int stackSize) {
        allocate(stackSize);
    }

    @TruffleBoundary
    private void allocate(final int stackSize) {
        if (!isFreed) {
            throw new AssertionError("previously not deallocated");
        }
        final long stackAllocation = getUnsafe().allocateMemory(stackSize * 1024);
        lowerBounds = stackAllocation;
        upperBounds = stackAllocation + stackSize * 1024;
        isFreed = false;
        stackPointer = upperBounds;
    }

    public boolean isFreed() {
        return isFreed;
    }

    public final class StackPointer implements AutoCloseable {

        private final long pointer;

        private StackPointer() {
            this.pointer = getStackPointer();
        }

        public long get() {
            return pointer;
        }

        @Override
        public void close() {
            setStackPointer(pointer);
        }
    }

    private long getStackPointer() {
        long sp = this.stackPointer;
        assert assertStackPointer();
        return sp;
    }

    public StackPointer takeStackPointer() {
        return new StackPointer();
    }

    private boolean assertStackPointer() {
        boolean azzert = stackPointer != 0;
        stackPointer = 0;
        return azzert;
    }

    public void setStackPointer(long pointer) {
        this.stackPointer = pointer;
    }

    @TruffleBoundary
    public void free() {
        if (isFreed) {
            throw new AssertionError("already freed");
        }
        getUnsafe().freeMemory(lowerBounds);
        lowerBounds = 0;
        upperBounds = 0;
        stackPointer = 0;
        isFreed = true;
    }

    public static final int NO_ALIGNMENT_REQUIREMENTS = 1;

    public static long allocateStackMemory(VirtualFrame frame, FrameSlot stackPointerSlot, final long size, final int alignment) {
        assert size >= 0;
        assert alignment != 0 && powerOfTwo(alignment);
        long stackPointer = FrameUtil.getLongSafe(frame, stackPointerSlot);
        assert stackPointer != 0;
        final long alignedAllocation = (stackPointer - size) & -alignment;
        assert alignedAllocation <= stackPointer;
        frame.setLong(stackPointerSlot, alignedAllocation);
        return alignedAllocation;
    }

    private static boolean powerOfTwo(int value) {
        return (value & -value) == value;
    }

    /*
     * TODO: How frequent is this? having a finalizer will prevent EA and add significant overhead.
     * maybe this can be done using a reference queue?
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (!isFreed) {
            throw new AssertionError("Did not free stack memory!");
        }
    }
}
