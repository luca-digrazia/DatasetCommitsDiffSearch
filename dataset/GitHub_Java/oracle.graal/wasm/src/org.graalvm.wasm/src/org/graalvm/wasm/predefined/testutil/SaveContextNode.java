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
package org.graalvm.wasm.predefined.testutil;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import org.graalvm.wasm.Assert;
import org.graalvm.wasm.Globals;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.WasmPredefinedRootNode;

/**
 * Records the context state (memory and global variables) into a custom object.
 */
public class SaveContextNode extends WasmPredefinedRootNode {
    public SaveContextNode(WasmLanguage language, WasmCodeEntry codeEntry, WasmMemory memory) {
        super(language, codeEntry, memory);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return saveModuleState();
    }

    @Override
    public String predefinedNodeName() {
        return TestutilModule.Names.RESET_CONTEXT;
    }

    @CompilerDirectives.TruffleBoundary
    private ContextState saveModuleState() {
        final WasmContext context = contextReference().get();
        Assert.assertIntLessOrEqual(context.memories().count(), 1, "Currently, only 0 or 1 memories can be saved.");
        final WasmMemory currentMemory = context.memories().count() == 1 ? context.memories().memory(0).duplicate() : null;
        final Globals globals = context.globals().duplicate();
        final ContextState state = new ContextState(currentMemory, globals);

        return state;
    }

    static final class ContextState implements TruffleObject {
        private final WasmMemory memory;
        private final Globals globals;

        private ContextState(WasmMemory memory, Globals globals) {
            this.memory = memory;
            this.globals = globals;
        }

        public WasmMemory memory() {
            return memory;
        }

        public Globals globals() {
            return globals;
        }
    }
}
