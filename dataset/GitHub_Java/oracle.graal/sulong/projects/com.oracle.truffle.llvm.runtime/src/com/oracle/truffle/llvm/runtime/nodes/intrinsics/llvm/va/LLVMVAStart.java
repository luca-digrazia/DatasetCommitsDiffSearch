/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

/**
 * The node handling the <code>va_start</code> instruction. It basically just delegates to
 * {@link LLVMVaListLibrary}.
 */
@NodeChild
public abstract class LLVMVAStart extends LLVMExpressionNode {

    private final int numberOfExplicitArguments;

    public LLVMVAStart(int numberOfExplicitArguments) {
        this.numberOfExplicitArguments = numberOfExplicitArguments;
    }

    private static Object[] getArgumentsArray(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        Object[] newArguments = new Object[arguments.length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        System.arraycopy(arguments, LLVMCallNode.USER_ARGUMENT_OFFSET, newArguments, 0, newArguments.length);

        return newArguments;
    }

    @Specialization(limit = "1")
    protected Object vaStart(VirtualFrame frame, LLVMManagedPointer targetAddress, @CachedLibrary("targetAddress.getObject()") LLVMVaListLibrary vaListLibrary) {
        vaListLibrary.initialize(targetAddress.getObject(), getArgumentsArray(frame), numberOfExplicitArguments);
        return null;
    }

    static Object createNativeVAListWrapper(LLVMNativePointer targetAddress, LLVMLanguage lang) {
        return lang.getCapability(PlatformCapability.class).createNativeVAListWrapper(targetAddress);
    }

    @Specialization
    protected Object vaStart(VirtualFrame frame, LLVMNativePointer targetAddress,
                    @CachedLanguage LLVMLanguage lang,
                    @Cached NativeLLVMVaListHelper nativeLLVMVaListHelper) {
        return nativeLLVMVaListHelper.execute(frame, createNativeVAListWrapper(targetAddress, lang), numberOfExplicitArguments);
    }

    abstract static class NativeLLVMVaListHelper extends LLVMNode {

        public abstract Object execute(VirtualFrame frame, Object nativeVaListWrapper, int numberOfExplicitArguments);

        @Specialization(limit = "1")
        protected Object vaStart(VirtualFrame frame, Object nativeVaListWrapper, int numberOfExplicitArguments,
                        @CachedLibrary("nativeVaListWrapper") LLVMVaListLibrary vaListLibrary) {
            vaListLibrary.initialize(nativeVaListWrapper, getArgumentsArray(frame), numberOfExplicitArguments);
            return null;
        }

    }

}
