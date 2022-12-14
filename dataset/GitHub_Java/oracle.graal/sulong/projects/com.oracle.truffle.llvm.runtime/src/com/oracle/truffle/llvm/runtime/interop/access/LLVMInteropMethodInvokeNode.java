/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.access;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Method;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMInteropMethodInvokeNode extends LLVMNode {
    abstract Object execute(LLVMPointer receiver, String methodName, LLVMInteropType.Clazz type, Method method, long virtualIndex, Object[] argumentsWithSelf)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException;

    public static LLVMInteropMethodInvokeNode create() {
        return LLVMInteropMethodInvokeNodeGen.create();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "virtualIndex>=0")
    Object doVirtualCall(LLVMPointer receiver, String methodName, LLVMInteropType.Clazz type, Method method, long virtualIndex, Object[] arguments)
                    throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        LLVMMemory memory = LLVMLanguage.getLanguage().getLLVMMemory();

        final long vtableAddress = memory.getI64(null, LLVMNativePointer.cast(receiver).asNative());
        final long methodOffset = virtualIndex * 8;
        // 8 -> anywhere to get the size automatically instead of hardcoded?
        final long methodAddress = memory.getI64(null, vtableAddress + methodOffset);
        LLVMPointer methodPointer = LLVMNativePointer.create(methodAddress);
        return InteropLibrary.getUncached(methodPointer).execute(methodPointer, arguments);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "virtualIndex<0")
    Object doNonvirtualCall(LLVMPointer receiver, String methodName, LLVMInteropType.Clazz type, Method method, long virtualIndex, Object[] arguments, @Cached LLVMInteropNonvirtualCallNode call)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        return call.execute(receiver, type, methodName, method, arguments);
    }

}
