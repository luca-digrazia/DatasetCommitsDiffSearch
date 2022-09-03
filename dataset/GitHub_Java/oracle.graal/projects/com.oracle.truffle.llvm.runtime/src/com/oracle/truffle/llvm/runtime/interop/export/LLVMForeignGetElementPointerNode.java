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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;

public abstract class LLVMForeignGetElementPointerNode extends Node {

    protected abstract LLVMTruffleObject execute(LLVMInteropType type, LLVMTruffleObject object, Object ident);

    @Specialization(guards = {"cachedMember != null", "cachedMember.getStruct() == struct", "cachedIdent.equals(ident)"})
    LLVMTruffleObject doCachedStruct(@SuppressWarnings("unused") LLVMInteropType.Struct struct, LLVMTruffleObject object, @SuppressWarnings("unused") String ident,
                    @Cached("ident") @SuppressWarnings("unused") String cachedIdent,
                    @Cached("struct.findMember(cachedIdent)") LLVMInteropType.StructMember cachedMember) {
        return object.increment(cachedMember.getStartOffset()).export(cachedMember.getType());
    }

    @Specialization(replaces = "doCachedStruct")
    LLVMTruffleObject doGenericStruct(LLVMInteropType.Struct struct, LLVMTruffleObject object, String ident) {
        LLVMInteropType.StructMember member = struct.findMember(ident);
        if (member == null) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(ident);
        }
        return object.increment(member.getStartOffset()).export(member.getType());
    }

    @Specialization(guards = "array.getElementType() == elementType")
    LLVMTruffleObject doCachedArray(LLVMInteropType.Array array, LLVMTruffleObject object, long idx,
                    @Cached("array.getElementSize()") long elementSize,
                    @Cached("array.getElementType()") LLVMInteropType elementType) {
        if (Long.compareUnsigned(idx, array.getLength()) >= 0) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(Long.toString(idx));
        }
        return object.increment(idx * elementSize).export(elementType);
    }

    @Specialization(replaces = "doCachedArray")
    LLVMTruffleObject doGenericArray(LLVMInteropType.Array array, LLVMTruffleObject object, long idx) {
        return doCachedArray(array, object, idx, array.getElementSize(), array.getElementType());
    }

    @Fallback
    @SuppressWarnings("unused")
    LLVMTruffleObject doError(LLVMInteropType type, LLVMTruffleObject object, Object ident) {
        CompilerDirectives.transferToInterpreter();
        throw UnknownIdentifierException.raise(ident.toString());
    }
}
