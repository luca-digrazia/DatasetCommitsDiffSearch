/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory.load;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ByteValueProfile;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNode.ReadI8Node;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;

public abstract class LLVMI8LoadNode extends LLVMAbstractLoadNode {

    private final ByteValueProfile profile = ByteValueProfile.createIdentityProfile();

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected byte doI8(LLVMAddress addr) {
        return profile.profile(getLLVMMemoryCached().getI8(addr));
    }

    @Specialization(guards = "isAutoDerefHandle(addr)")
    protected byte doI8DerefHandle(LLVMAddress addr) {
        return doI8Native(getDerefHandleGetReceiverNode().execute(addr));
    }

    @Specialization
    protected byte doI8(LLVMVirtualAllocationAddress address,
                    @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess memory) {
        return address.getI8(memory);
    }

    @Specialization
    protected byte doI8(LLVMGlobal addr,
                    @Cached("create()") ReadI8Node globalAccess) {
        return profile.profile(globalAccess.execute(addr));
    }

    @Override
    LLVMForeignReadNode createForeignRead() {
        return new LLVMForeignReadNode(ForeignToLLVMType.I8);
    }

    @Specialization(guards = "addr.isNative()")
    protected byte doI8Native(LLVMTruffleObject addr) {
        return doI8(addr.asNative());
    }

    @Specialization(guards = "addr.isManaged()")
    protected byte doI8Managed(LLVMTruffleObject addr) {
        return (byte) getForeignReadNode().execute(addr);
    }

    @Specialization
    protected byte doLLVMBoxedPrimitive(LLVMBoxedPrimitive addr) {
        if (addr.getValue() instanceof Long) {
            return getLLVMMemoryCached().getI8((long) addr.getValue());
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError("Cannot access address: " + addr.getValue());
        }
    }
}
