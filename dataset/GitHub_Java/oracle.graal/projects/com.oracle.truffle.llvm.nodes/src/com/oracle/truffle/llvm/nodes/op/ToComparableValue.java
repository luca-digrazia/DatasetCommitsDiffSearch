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
package com.oracle.truffle.llvm.nodes.op;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.nodes.op.ToComparableValueNodeGen.ForeignToComparableValueNodeGen;
import com.oracle.truffle.llvm.nodes.op.ToComparableValueNodeGen.ManagedToComparableValueNodeGen;
import com.oracle.truffle.llvm.nodes.op.ToComparableValueNodeGen.NativeToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

public abstract class ToComparableValue extends LLVMNode {
    protected abstract long execute(Object obj);

    @Specialization(guards = "lib.guard(obj)")
    protected long doNativeCached(Object obj,
                    @Cached("createCached(obj)") LLVMObjectNativeLibrary lib,
                    @Cached("createToComparable()") NativeToComparableValue toComparable) {
        return doNative(obj, lib, toComparable);
    }

    @Specialization(replaces = "doNativeCached", guards = "lib.guard(obj)")
    protected long doNative(Object obj,
                    @Cached("createGeneric()") LLVMObjectNativeLibrary lib,
                    @Cached("createToComparable()") NativeToComparableValue toComparable) {
        return toComparable.execute(obj, lib);
    }

    protected static NativeToComparableValue createToComparable() {
        return NativeToComparableValueNodeGen.create();
    }

    @TruffleBoundary
    private static int getHashCode(Object address) {
        return address.hashCode();
    }

    protected abstract static class ForeignToComparableValue extends LLVMNode {

        abstract long execute(TruffleObject obj);

        public static ForeignToComparableValue create() {
            return ForeignToComparableValueNodeGen.create();
        }

        @Specialization
        protected long doForeign(LLVMTypedForeignObject obj) {
            return getHashCode(obj.getForeign());
        }

        @Fallback
        protected long doOther(TruffleObject obj) {
            return getHashCode(obj);
        }
    }

    @ImportStatic(ForeignToLLVMType.class)
    protected abstract static class ManagedToComparableValue extends LLVMNode {

        abstract long execute(Object obj);

        @Specialization
        protected long doAddress(long address) {
            return address;
        }

        @Specialization
        protected long doManagedMalloc(LLVMVirtualAllocationAddress address) {
            if (address.isNull()) {
                return address.getOffset();
            } else {
                return getHashCode(address.getObject()) + address.getOffset();
            }
        }

        @Specialization
        protected long doManaged(LLVMManagedPointer address,
                        @Cached("create()") ForeignToComparableValue toComparable) {
            return toComparable.execute(address.getObject()) + address.getOffset();
        }

        @Specialization
        protected long doLLVMBoxedPrimitive(LLVMBoxedPrimitive address,
                        @Cached("create(I64)") ForeignToLLVM toLLVM) {
            return (long) toLLVM.executeWithTarget(address.getValue());
        }

        public static ManagedToComparableValue create() {
            return ManagedToComparableValueNodeGen.create();
        }
    }

    protected abstract static class NativeToComparableValue extends LLVMNode {

        protected abstract long execute(Object obj, LLVMObjectNativeLibrary lib);

        @Specialization(guards = "lib.isPointer(obj)")
        protected long doPointer(Object obj, LLVMObjectNativeLibrary lib) {
            try {
                return lib.asPointer(obj);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }

        @Specialization(guards = "!lib.isPointer(obj)")
        @SuppressWarnings("unused")
        protected long doManaged(Object obj, LLVMObjectNativeLibrary lib,
                        @Cached("create()") ManagedToComparableValue toComparable) {
            return toComparable.execute(obj);
        }
    }
}
