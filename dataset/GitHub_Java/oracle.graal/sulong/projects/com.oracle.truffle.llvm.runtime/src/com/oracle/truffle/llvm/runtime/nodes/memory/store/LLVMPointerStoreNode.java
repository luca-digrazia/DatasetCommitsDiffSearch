/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory.store;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToPointerNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMPointerStoreNode extends LLVMStoreNodeCommon {

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected void doAddress(long addr, Object value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        getLLVMMemoryCached().putPointer(addr, toNative.executeWithTarget(value));
    }

    @Specialization(guards = "isAutoDerefHandle(addr)")
    protected void doDerefAddress(long addr, Object value,
                    @Cached LLVMToPointerNode toPointer,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        doTruffleObject(getDerefHandleGetReceiverNode().execute(addr), value, toPointer, nativeWrite);
    }

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected void doAddress(LLVMNativePointer addr, Object value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        getLLVMMemoryCached().putPointer(addr, toNative.executeWithTarget(value));
    }

    @Specialization(guards = "isAutoDerefHandle(addr)")
    protected void doOpDerefHandle(LLVMNativePointer addr, Object value,
                    @Cached LLVMToPointerNode toPointer,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        doTruffleObject(getDerefHandleGetReceiverNode().execute(addr), value, toPointer, nativeWrite);
    }

    @Specialization
    protected void doAddress(LLVMVirtualAllocationAddress address, Object value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                    @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess memory) {
        address.writeI64(memory, toNative.executeWithTarget(value).asNative());
    }

    @Specialization(limit = "3")
    protected void doTruffleObject(LLVMManagedPointer address, Object value,
                    @Cached LLVMToPointerNode toPointer,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        nativeWrite.writePointer(address.getObject(), address.getOffset(), toPointer.executeWithTarget(value));
    }
}
