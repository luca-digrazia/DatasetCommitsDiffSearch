/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemReadI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemReadI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemReadI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemReadI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemReadNativeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemWriteI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemWriteI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemWriteI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemWriteI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ManagedMemMoveHelperNodeFactory.MemWriteNativeNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Helper class for memmove involving managed (foreign) objects.
 */
final class ManagedMemMoveHelperNode extends LLVMNode {

    @Child MemReadHelperNode read;
    @Child MemWriteHelperNode write;

    public static ManagedMemMoveHelperNode create(LLVMPointer target, LLVMPointer source) {
        MemReadHelperNode read = MemReadHelperNode.create(source);
        MemWriteHelperNode write = MemWriteHelperNode.create(target);
        return new ManagedMemMoveHelperNode(read, write);
    }

    public boolean supportsUnitSize(int unitSize) {
        if (!read.supportsUnitSize(unitSize)) {
            return false;
        }
        return write.supportsUnitSize(unitSize);
    }

    public boolean guard(LLVMPointer target, LLVMPointer source) {
        if (!read.guard(source)) {
            return false;
        }
        return write.guard(target);
    }

    public void moveUnit(LLVMPointer target, LLVMPointer source, int unitSize) {
        CompilerAsserts.partialEvaluationConstant(unitSize);
        long value = read.execute(source, unitSize);
        write.execute(target, value, unitSize);
    }

    abstract static class UnitSizeNode extends LLVMNode {

        abstract int execute(ManagedMemMoveHelperNode helper, long length);

        boolean isDivisible(long length, int unitSize) {
            return length % unitSize == 0;
        }

        @Specialization(guards = {"helper.supportsUnitSize(8)", "isDivisible(length, 8)"})
        @SuppressWarnings("unused")
        int do8(ManagedMemMoveHelperNode helper, long length) {
            return 8;
        }

        @Specialization(guards = {"helper.supportsUnitSize(4)", "isDivisible(length, 4)"}, replaces = "do8")
        @SuppressWarnings("unused")
        int do4(ManagedMemMoveHelperNode helper, long length) {
            return 4;
        }

        @Specialization(guards = {"helper.supportsUnitSize(2)", "isDivisible(length, 2)"}, replaces = "do4")
        @SuppressWarnings("unused")
        int do2(ManagedMemMoveHelperNode helper, long length) {
            return 2;
        }

        @Specialization(guards = {"helper.supportsUnitSize(1)", "isDivisible(length, 1)"}, replaces = "do2")
        @SuppressWarnings("unused")
        int do1(ManagedMemMoveHelperNode helper, long length) {
            return 1;
        }

        @Fallback
        @SuppressWarnings("unused")
        int doError(ManagedMemMoveHelperNode helper, long length) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(this, "Memmove length is not divisible by managed array element size.");
        }
    }

    private ManagedMemMoveHelperNode(MemReadHelperNode read, MemWriteHelperNode write) {
        this.read = read;
        this.write = write;
    }

    abstract static class MemAccessHelperNode extends LLVMNode {

        abstract boolean supportsUnitSize(int unitSize);

        abstract boolean guard(LLVMPointer ptr);
    }

    abstract static class MemReadHelperNode extends MemAccessHelperNode {

        static MemReadHelperNode create(LLVMPointer target) {
            if (LLVMNativePointer.isInstance(target)) {
                return MemReadNativeNodeGen.create();
            } else {
                assert LLVMManagedPointer.isInstance(target);
                LLVMManagedPointer managed = LLVMManagedPointer.cast(target);
                NativeTypeLibrary nativeTypes = NativeTypeLibrary.getFactory().create(managed.getObject());
                LLVMManagedReadLibrary managedRead = LLVMManagedReadLibrary.getFactory().create(managed.getObject());

                // we need to use uncached here, since we're not yet adopted
                Object type = NativeTypeLibrary.getFactory().getUncached().getNativeType(managed.getObject());
                if (type instanceof LLVMInteropType.Array) {
                    long elementSize = ((LLVMInteropType.Array) type).getElementSize();
                    if (elementSize == 2) {
                        return MemReadI16NodeGen.create(nativeTypes, managedRead);
                    } else if (elementSize == 4) {
                        return MemReadI32NodeGen.create(nativeTypes, managedRead);
                    } else if (elementSize == 8) {
                        return MemReadI64NodeGen.create(nativeTypes, managedRead);
                    }
                }
                // elementSize == 1 or unknown
                return MemReadI8NodeGen.create(nativeTypes, managedRead);
            }
        }

        abstract long execute(LLVMPointer source, int unitSize);
    }

    abstract static class MemReadNative extends MemReadHelperNode {

        @Override
        boolean supportsUnitSize(int unitSize) {
            return true;
        }

        @Override
        boolean guard(LLVMPointer ptr) {
            return LLVMNativePointer.isInstance(ptr);
        }

        @Specialization(guards = "unitSize == 1")
        long doNativeI8(LLVMNativePointer source, @SuppressWarnings("unused") int unitSize,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(LLVMMemory.class).getI8(source);
        }

        @Specialization(guards = "unitSize == 2")
        long doNativeI16(LLVMNativePointer source, @SuppressWarnings("unused") int unitSize,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(LLVMMemory.class).getI16(source);
        }

        @Specialization(guards = "unitSize == 4")
        long doNativeI32(LLVMNativePointer source, @SuppressWarnings("unused") int unitSize,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(LLVMMemory.class).getI32(source);
        }

        @Specialization(guards = "unitSize == 8")
        long doNativeI64(LLVMNativePointer source, @SuppressWarnings("unused") int unitSize,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(LLVMMemory.class).getI64(source);
        }
    }

    abstract static class MemReadManaged extends MemReadHelperNode {

        @Child NativeTypeLibrary nativeTypes;
        @Child LLVMManagedReadLibrary managedRead;

        abstract int getAccessSize();

        @Override
        boolean supportsUnitSize(int unitSize) {
            return unitSize % getAccessSize() == 0;
        }

        MemReadManaged(NativeTypeLibrary nativeTypes, LLVMManagedReadLibrary managedRead) {
            this.nativeTypes = nativeTypes;
            this.managedRead = managedRead;
        }

        @Override
        boolean guard(LLVMPointer ptr) {
            if (LLVMManagedPointer.isInstance(ptr)) {
                LLVMManagedPointer managed = LLVMManagedPointer.cast(ptr);
                if (nativeTypes.accepts(managed.getObject()) && managedRead.accepts(managed.getObject()) && managedRead.isReadable(managed.getObject())) {
                    Object type = nativeTypes.getNativeType(managed.getObject());
                    if (type instanceof LLVMInteropType.Array) {
                        return ((LLVMInteropType.Array) type).getElementSize() == getAccessSize();
                    } else {
                        return getAccessSize() == 1;
                    }
                }
            }
            return false;
        }
    }

    abstract static class MemReadI8 extends MemReadManaged {

        MemReadI8(NativeTypeLibrary nativeTypes, LLVMManagedReadLibrary managedRead) {
            super(nativeTypes, managedRead);
        }

        @Override
        int getAccessSize() {
            return Byte.BYTES;
        }

        @Specialization
        @ExplodeLoop
        long execute(LLVMManagedPointer source, int unitSize) {
            int shift = 0;
            long ret = 0;
            for (int i = 0; i < unitSize; i++) {
                ret |= (managedRead.readI8(source.getObject(), source.getOffset() + i) & 0xFFL) << shift;
                shift += Byte.SIZE;
            }
            return ret;
        }
    }

    abstract static class MemReadI16 extends MemReadManaged {

        MemReadI16(NativeTypeLibrary nativeTypes, LLVMManagedReadLibrary managedRead) {
            super(nativeTypes, managedRead);
        }

        @Override
        int getAccessSize() {
            return Short.BYTES;
        }

        @Specialization
        @ExplodeLoop
        long doManaged(LLVMManagedPointer source, int unitSize) {
            int shift = 0;
            long ret = 0;
            for (int i = 0; i < unitSize; i += Short.BYTES) {
                ret |= (managedRead.readI16(source.getObject(), source.getOffset() + i) & 0xFFFFL) << shift;
                shift += Short.SIZE;
            }
            return ret;
        }
    }

    abstract static class MemReadI32 extends MemReadManaged {

        MemReadI32(NativeTypeLibrary nativeTypes, LLVMManagedReadLibrary managedRead) {
            super(nativeTypes, managedRead);
        }

        @Override
        int getAccessSize() {
            return Integer.BYTES;
        }

        @Specialization
        @ExplodeLoop
        long doManaged(LLVMManagedPointer source, int unitSize) {
            int shift = 0;
            long ret = 0;
            for (int i = 0; i < unitSize; i += Integer.BYTES) {
                ret |= (managedRead.readI32(source.getObject(), source.getOffset() + i) & 0xFFFF_FFFFL) << shift;
                shift += Integer.SIZE;
            }
            return ret;
        }
    }

    abstract static class MemReadI64 extends MemReadManaged {

        MemReadI64(NativeTypeLibrary nativeTypes, LLVMManagedReadLibrary managedRead) {
            super(nativeTypes, managedRead);
        }

        @Override
        int getAccessSize() {
            return Long.BYTES;
        }

        @Specialization
        long doManaged(LLVMManagedPointer source, int unitSize) {
            try {
                assert unitSize == Long.BYTES;
                return managedRead.readI64(source.getObject(), source.getOffset());
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Can not memmove from an object containing foreign pointers.");
            }
        }
    }

    abstract static class MemWriteHelperNode extends MemAccessHelperNode {

        static MemWriteHelperNode create(LLVMPointer target) {
            if (LLVMNativePointer.isInstance(target)) {
                return MemWriteNativeNodeGen.create();
            } else {
                assert LLVMManagedPointer.isInstance(target);
                LLVMManagedPointer managed = LLVMManagedPointer.cast(target);
                NativeTypeLibrary nativeTypes = NativeTypeLibrary.getFactory().create(managed.getObject());
                LLVMManagedWriteLibrary managedWrite = LLVMManagedWriteLibrary.getFactory().create(managed.getObject());

                // we need to use uncached here, since we're not yet adopted
                Object type = NativeTypeLibrary.getFactory().getUncached().getNativeType(managed.getObject());
                if (type instanceof LLVMInteropType.Array) {
                    long elementSize = ((LLVMInteropType.Array) type).getElementSize();
                    if (elementSize == 2) {
                        return MemWriteI16NodeGen.create(nativeTypes, managedWrite);
                    } else if (elementSize == 4) {
                        return MemWriteI32NodeGen.create(nativeTypes, managedWrite);
                    } else if (elementSize == 8) {
                        return MemWriteI64NodeGen.create(nativeTypes, managedWrite);
                    }
                }
                // elementSize == 1 or unknown
                return MemWriteI8NodeGen.create(nativeTypes, managedWrite);
            }
        }

        abstract void execute(LLVMPointer target, long value, int unitSize);
    }

    abstract static class MemWriteNative extends MemWriteHelperNode {

        @Override
        boolean supportsUnitSize(int unitSize) {
            return true;
        }

        @Override
        boolean guard(LLVMPointer ptr) {
            return LLVMNativePointer.isInstance(ptr);
        }

        @Specialization(guards = "unitSize == 1")
        void doNativeI8(LLVMNativePointer target, long value, @SuppressWarnings("unused") int unitSize,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(LLVMMemory.class).putI8(target, (byte) value);
        }

        @Specialization(guards = "unitSize == 2")
        void doNativeI16(LLVMNativePointer target, long value, @SuppressWarnings("unused") int unitSize,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(LLVMMemory.class).putI16(target, (short) value);
        }

        @Specialization(guards = "unitSize == 4")
        void doNativeI32(LLVMNativePointer target, long value, @SuppressWarnings("unused") int unitSize,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(LLVMMemory.class).putI32(target, (int) value);
        }

        @Specialization(guards = "unitSize == 8")
        void doNativeI64(LLVMNativePointer target, long value, @SuppressWarnings("unused") int unitSize,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(LLVMMemory.class).putI64(target, value);
        }
    }

    abstract static class MemWriteManaged extends MemWriteHelperNode {

        @Child NativeTypeLibrary nativeTypes;
        @Child LLVMManagedWriteLibrary managedWrite;

        abstract int getAccessSize();

        @Override
        boolean supportsUnitSize(int unitSize) {
            return unitSize % getAccessSize() == 0;
        }

        MemWriteManaged(NativeTypeLibrary nativeTypes, LLVMManagedWriteLibrary managedWrite) {
            this.nativeTypes = nativeTypes;
            this.managedWrite = managedWrite;
        }

        @Override
        boolean guard(LLVMPointer ptr) {
            if (LLVMManagedPointer.isInstance(ptr)) {
                LLVMManagedPointer managed = LLVMManagedPointer.cast(ptr);
                if (nativeTypes.accepts(managed.getObject()) && managedWrite.accepts(managed.getObject()) && managedWrite.isWritable(managed.getObject())) {
                    Object type = nativeTypes.getNativeType(managed.getObject());
                    if (type instanceof LLVMInteropType.Array) {
                        return ((LLVMInteropType.Array) type).getElementSize() == getAccessSize();
                    } else {
                        return getAccessSize() == 1;
                    }
                }
            }
            return false;
        }
    }

    abstract static class MemWriteI8 extends MemWriteManaged {

        MemWriteI8(NativeTypeLibrary nativeTypes, LLVMManagedWriteLibrary managedWrite) {
            super(nativeTypes, managedWrite);
        }

        @Override
        int getAccessSize() {
            return Byte.BYTES;
        }

        @Specialization
        @ExplodeLoop
        void doManaged(LLVMManagedPointer target, long value, int unitSize) {
            long v = value;
            for (int i = 0; i < unitSize; i += Byte.BYTES) {
                managedWrite.writeI8(target.getObject(), target.getOffset() + i, (byte) v);
                v >>= Byte.SIZE;
            }
        }
    }

    abstract static class MemWriteI16 extends MemWriteManaged {

        MemWriteI16(NativeTypeLibrary nativeTypes, LLVMManagedWriteLibrary managedWrite) {
            super(nativeTypes, managedWrite);
        }

        @Override
        int getAccessSize() {
            return Short.BYTES;
        }

        @Specialization
        @ExplodeLoop
        void doManaged(LLVMManagedPointer target, long value, int unitSize) {
            long v = value;
            for (int i = 0; i < unitSize; i += Short.BYTES) {
                managedWrite.writeI16(target.getObject(), target.getOffset() + i, (short) v);
                v >>= Short.SIZE;
            }
        }
    }

    abstract static class MemWriteI32 extends MemWriteManaged {

        MemWriteI32(NativeTypeLibrary nativeTypes, LLVMManagedWriteLibrary managedWrite) {
            super(nativeTypes, managedWrite);
        }

        @Override
        int getAccessSize() {
            return Integer.BYTES;
        }

        @Specialization
        @ExplodeLoop
        void doManaged(LLVMManagedPointer target, long value, int unitSize) {
            long v = value;
            for (int i = 0; i < unitSize; i += Integer.BYTES) {
                managedWrite.writeI32(target.getObject(), target.getOffset() + i, (int) v);
                v >>= Integer.SIZE;
            }
        }
    }

    abstract static class MemWriteI64 extends MemWriteManaged {

        MemWriteI64(NativeTypeLibrary nativeTypes, LLVMManagedWriteLibrary managedWrite) {
            super(nativeTypes, managedWrite);
        }

        @Override
        int getAccessSize() {
            return Long.BYTES;
        }

        @Specialization
        void doManaged(LLVMManagedPointer target, long value, int unitSize) {
            assert unitSize == Long.BYTES;
            managedWrite.writeI64(target.getObject(), target.getOffset(), value);
        }
    }
}
