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
package com.oracle.truffle.llvm.runtime.interop.export;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignAccessNodeFactory.AttachTypeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignAccessNodeFactory.ReadNodeGen;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignAccessNodeFactory.WriteNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;

public abstract class LLVMForeignAccessNode {

    public static Read createRead() {
        return ReadNodeGen.create();
    }

    public static Write createWrite() {
        return WriteNodeGen.create();
    }

    public abstract static class Read extends LLVMNode {

        public abstract Object execute(LLVMTruffleObject ptr, LLVMInteropType type);

        @Specialization
        Object doStructured(LLVMTruffleObject ptr, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            // inline structured value, nothing to read
            return ptr;
        }

        @Specialization(guards = "type.getKind() == cachedKind")
        Object doValue(LLVMTruffleObject ptr, LLVMInteropType.Value type,
                        @Cached("type.getKind()") @SuppressWarnings("unused") LLVMInteropType.ValueKind cachedKind,
                        @Cached("createLoadNode(cachedKind)") LLVMLoadNode load,
                        @Cached("create()") LLVMDataEscapeNode dataEscape,
                        @Cached("create()") AttachTypeNode attachType) {
            Object ret = load.executeWithTarget(ptr);
            Object escaped = dataEscape.executeWithTarget(ret);
            return attachType.execute(escaped, type.getBaseType());
        }

        LLVMLoadNode createLoadNode(LLVMInteropType.ValueKind kind) {
            CompilerAsserts.neverPartOfCompilation();
            ContextReference<LLVMContext> ctxRef = LLVMLanguage.getLLVMContextReference();
            return ctxRef.get().getInteropNodeFactory().createLoadNode(kind);
        }
    }

    abstract static class AttachTypeNode extends Node {

        protected abstract Object execute(Object value, LLVMInteropType.Structured type);

        public static AttachTypeNode create() {
            return AttachTypeNodeGen.create();
        }

        @Specialization
        Object doLLVMTruffleObject(LLVMTruffleObject value, LLVMInteropType.Structured type) {
            return value.export(type);
        }

        @Fallback
        Object doOther(Object other, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return other;
        }
    }

    public abstract static class Write extends Node {

        protected abstract void execute(LLVMTruffleObject ptr, LLVMInteropType.Value type, Object value);

        @Specialization(guards = "type.getKind() == cachedKind")
        void doValue(LLVMTruffleObject ptr, LLVMInteropType.Value type, Object value,
                        @Cached("type.getKind()") @SuppressWarnings("unused") LLVMInteropType.ValueKind cachedKind,
                        @Cached("createStoreNode(cachedKind)") LLVMStoreNode store,
                        @Cached("create(type)") ForeignToLLVM toLLVM) {
            // since we only cache type.getKind(), not type, we have to use executeWithType
            Object llvmValue = toLLVM.executeWithType(value, type.getBaseType());
            store.executeWithTarget(ptr, llvmValue);
        }

        LLVMStoreNode createStoreNode(LLVMInteropType.ValueKind kind) {
            CompilerAsserts.neverPartOfCompilation();
            ContextReference<LLVMContext> ctxRef = LLVMLanguage.getLLVMContextReference();
            return ctxRef.get().getInteropNodeFactory().createStoreNode(kind);
        }
    }
}
