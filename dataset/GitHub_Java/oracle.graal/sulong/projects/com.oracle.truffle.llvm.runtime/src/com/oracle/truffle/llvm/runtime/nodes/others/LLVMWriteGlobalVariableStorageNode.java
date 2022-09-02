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
package com.oracle.truffle.llvm.runtime.nodes.others;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMWriteGlobalVariableStorageNode extends LLVMNode {

    public abstract void execute(LLVMPointer pointer, LLVMGlobal descriptor);

    @SuppressWarnings("unused")
    @Specialization
    void doWrite(LLVMPointer pointer, LLVMGlobal descriptor,
                    @CachedContext(LLVMLanguage.class) LLVMContext context,
                    @Cached WriteDynamicObjectHelper writeHelper,
                    @Cached(value = "context.findGlobal(descriptor.getID())", dimensions = 1) LLVMPointer[] globals) {
        synchronized (globals) {
            writeHelper.execute(globals, descriptor, pointer);
        }
    }

    abstract static class WriteDynamicObjectHelper extends LLVMNode {

        public abstract void execute(LLVMPointer[] globals, LLVMGlobal descriptor, LLVMPointer value);

        @Specialization
        protected void doDirect(LLVMPointer[] globals, LLVMGlobal descriptor, LLVMPointer value) {
            CompilerAsserts.partialEvaluationConstant(descriptor);
            try {
                int index = descriptor.getIndex();
                globals[index] = value;
            } catch (Exception e) {
                CompilerDirectives.transferToInterpreter();
                throw new RuntimeException("Global write is inconsistent.");
            }
        }
    }
}
