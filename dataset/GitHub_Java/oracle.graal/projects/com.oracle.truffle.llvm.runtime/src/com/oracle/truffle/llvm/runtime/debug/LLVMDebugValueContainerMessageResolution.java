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
package com.oracle.truffle.llvm.runtime.debug;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = LLVMDebugValueContainer.class)
public class LLVMDebugValueContainerMessageResolution {

    @Resolve(message = "KEYS")
    public abstract static class LLVMDebugObjectPropertiesNode extends Node {

        @TruffleBoundary
        private static Object obtainKeys(LLVMDebugValueContainer receiver) {
            final Object[] keys = receiver.getKeys();
            return JavaInterop.asTruffleObject(keys);
        }

        public Object access(LLVMDebugValueContainer receiver) {
            return obtainKeys(receiver);
        }
    }

    @Resolve(message = "KEY_INFO")
    public abstract static class LLVMDebugObjectPropertiesInfoNode extends Node {

        public int access(LLVMDebugValueContainer receiver, Object key) {
            if (receiver.getElement(key) != null) {
                return 0b11;
            } else {
                return 0;
            }
        }
    }

    @Resolve(message = "READ")
    public abstract static class LLVMDebugObjectForeignReadNode extends Node {

        public Object access(LLVMDebugValueContainer receiver, Object key) {
            return receiver.getElement(key);
        }
    }
}
