/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropReadNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropWriteNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess;
import com.oracle.truffle.llvm.runtime.types.PointerType;

@ValueType
public final class LLVMTypedForeignObject implements LLVMObjectAccess, LLVMInternalTruffleObject {

    private final TruffleObject foreign;
    private final LLVMInteropType.Structured type;

    public static LLVMTypedForeignObject create(TruffleObject foreign, LLVMInteropType.Structured type) {
        return new LLVMTypedForeignObject(foreign, type);
    }

    public static LLVMTypedForeignObject createUnknown(TruffleObject foreign) {
        return new LLVMTypedForeignObject(foreign, null);
    }

    private LLVMTypedForeignObject(TruffleObject foreign, LLVMInteropType.Structured type) {
        this.foreign = foreign;
        this.type = type;
    }

    public TruffleObject getForeign() {
        return foreign;
    }

    public LLVMInteropType.Structured getType() {
        return type;
    }

    @Override
    public LLVMObjectReadNode createReadNode(ForeignToLLVMType toLLVMType) {
        return new ForeignReadNode(toLLVMType);
    }

    @Override
    public LLVMObjectWriteNode createWriteNode() {
        return new ForeignWriteNode();
    }

    static class ForeignReadNode extends LLVMObjectReadNode {

        @Child LLVMInteropReadNode read;

        protected ForeignReadNode(ForeignToLLVMType type) {
            this.read = LLVMInteropReadNode.create(type);
        }

        @Override
        public Object executeRead(Object obj, long offset) throws InteropException {
            LLVMTypedForeignObject object = (LLVMTypedForeignObject) obj;
            return read.execute(object.getType(), object.getForeign(), offset);
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMTypedForeignObject;
        }
    }

    static class ForeignWriteNode extends LLVMObjectWriteNode {

        @Child LLVMInteropWriteNode write = LLVMInteropWriteNode.create();
        @Child LLVMDataEscapeNode dataEscape = LLVMDataEscapeNodeGen.create(PointerType.VOID);

        @Override
        public void executeWrite(Object obj, long offset, Object value) throws InteropException {
            LLVMTypedForeignObject object = (LLVMTypedForeignObject) obj;
            Object escapedValue = dataEscape.executeWithTarget(value);
            write.execute(object.getType(), object.getForeign(), offset, escapedValue);
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMTypedForeignObject;
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMTypedForeignObjectMessageResolutionForeign.ACCESS;
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof LLVMTypedForeignObject;
    }
}
