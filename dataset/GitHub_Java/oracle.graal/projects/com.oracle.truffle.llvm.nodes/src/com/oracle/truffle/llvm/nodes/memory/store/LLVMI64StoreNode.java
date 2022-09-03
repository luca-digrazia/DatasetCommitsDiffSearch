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
package com.oracle.truffle.llvm.nodes.memory.store;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteI64Node;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

public abstract class LLVMI64StoreNode extends LLVMStoreNodeCommon {

    public LLVMI64StoreNode() {
        this(null);
    }

    public LLVMI64StoreNode(LLVMSourceLocation sourceLocation) {
        super(sourceLocation, PrimitiveType.I64);
    }

    @Specialization
    protected Object doOp(LLVMGlobal address, long value,
                    @Cached("create()") WriteI64Node globalAccess) {
        globalAccess.execute(address, value);
        return null;
    }

    @Specialization
    protected Object doOp(LLVMAddress address, long value,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        memory.putI64(address, value);
        return null;
    }

    @Specialization
    protected Object doOp(LLVMAddress address, LLVMTruffleObject value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toAddress,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        memory.putI64(address, toAddress.executeWithTarget(value).getVal());
        return null;
    }

    @Specialization
    protected Object doOp(LLVMAddress address, LLVMAddress value,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        memory.putI64(address, value.getVal());
        return null;
    }

    @Specialization
    protected Object doOp(LLVMAddress address, LLVMGlobal value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        memory.putI64(address, globalAccess.executeWithTarget(value).getVal());
        return null;
    }

    @Specialization
    protected Object doOp(LLVMVirtualAllocationAddress address, long value,
                    @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess memory) {
        address.writeI64(memory, value);
        return null;
    }

    @Specialization(guards = "address.isNative()")
    protected Object doOp(LLVMTruffleObject address, Object value,
                    @Cached("createRecursive()") LLVMI64StoreNode recursive) {
        return recursive.executeWithTarget(address.asNative(), value);
    }

    protected static LLVMI64StoreNode createRecursive() {
        return LLVMI64StoreNodeGen.create(null, null);
    }

    @Specialization(guards = "address.isManaged()")
    protected Object doOp(LLVMTruffleObject address, Object value,
                    @Cached("createForeignWrite()") LLVMForeignWriteNode foreignWrite) {
        foreignWrite.execute(address, value);
        return null;
    }

    @Specialization
    protected Object doOp(LLVMBoxedPrimitive address, long value,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        if (address.getValue() instanceof Long) {
            memory.putI64((long) address.getValue(), value);
            return null;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError("Cannot access address: " + address.getValue());
        }
    }
}
