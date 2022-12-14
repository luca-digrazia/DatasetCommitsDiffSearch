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
package com.oracle.truffle.llvm.parser.listeners;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.scanner.Block;
import com.oracle.truffle.llvm.parser.util.SymbolNameMangling;

public final class BCFileRoot implements ParserListener {

    private final Source source;
    private final ModelModule module;
    private final StringTable stringTable;
    private final IRScope scope;

    public BCFileRoot(Source source, ModelModule module) {
        this.source = source;
        this.module = module;
        this.stringTable = new StringTable();
        this.scope = new IRScope();
    }

    @Override
    public ParserListener enter(Block block) {
        switch (block) {
            case MODULE:
                return new Module(module, stringTable, scope);

            case STRTAB:
                return stringTable;

            default:
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void exit() {
        SymbolNameMangling.demangleGlobals(module);
        module.exitModule(scope, source);
    }

    @Override
    public void record(long id, long[] args) {
    }
}
