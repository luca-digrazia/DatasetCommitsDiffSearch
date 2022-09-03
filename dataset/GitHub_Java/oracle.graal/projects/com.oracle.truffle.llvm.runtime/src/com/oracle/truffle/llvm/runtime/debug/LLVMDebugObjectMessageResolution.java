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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = LLVMDebugObject.class)
public class LLVMDebugObjectMessageResolution {

    @MessageResolution(receiverType = Keys.class)
    static final class Keys implements TruffleObject {

        private final Object[] keys;

        private Keys(Object[] keys) {
            this.keys = keys;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof Keys;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return KeysForeign.ACCESS;
        }

        @Resolve(message = "GET_SIZE")
        abstract static class GetSize extends Node {

            int access(Keys receiver) {
                if (receiver.keys == null) {
                    return 0;
                } else {
                    return receiver.keys.length;
                }
            }
        }

        @Resolve(message = "READ")
        abstract static class Read extends Node {

            Object access(Keys receiver, int index) {
                if (receiver.keys == null || index < 0 || index >= receiver.keys.length) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(Integer.toString(index));
                }
                return receiver.keys[index];
            }
        }
    }

    @Resolve(message = "KEYS")
    public abstract static class LLVMDebugObjectPropertiesNode extends Node {

        @TruffleBoundary
        private static Object obtainKeys(LLVMDebugObject receiver) {
            final Object[] keys = receiver.getKeys();
            return new Keys(keys);
        }

        public Object access(LLVMDebugObject receiver) {
            return obtainKeys(receiver);
        }
    }

    @Resolve(message = "KEY_INFO")
    public abstract static class LLVMDebugObjectPropertiesInfoNode extends Node {

        public int access(LLVMDebugObject receiver, Object key) {
            if (receiver.getMember(key) != null) {
                return 0b11;
            } else {
                return 0;
            }
        }
    }

    @Resolve(message = "READ")
    public abstract static class LLVMDebugObjectForeignReadNode extends Node {

        public Object access(LLVMDebugObject receiver, Object name) {
            return receiver.getMember(name);
        }
    }
}
