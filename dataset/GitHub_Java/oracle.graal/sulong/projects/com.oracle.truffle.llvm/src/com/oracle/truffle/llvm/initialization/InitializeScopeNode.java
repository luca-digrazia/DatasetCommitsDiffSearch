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
package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLocalScope;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

import java.util.ArrayList;

/**
 * Initialization node for the global scope and the local scope of the module. The scopes are
 * allocated from the symbols in the file scope of the module.
 *
 * @see InitializeSymbolsNode
 * @see InitializeGlobalNode
 * @see InitializeModuleNode
 * @see InitializeOverwriteNode
 * @see InitializeExternalNode
 */
public final class InitializeScopeNode extends LLVMNode {
    @Children final AllocScopeNode[] allocScopes;
    private final LLVMScope fileScope;

    public InitializeScopeNode(LLVMParserResult result) {
        this.fileScope = result.getRuntime().getFileScope();
        ArrayList<AllocScopeNode> allocScopesList = new ArrayList<>();
        for (LLVMSymbol symbol : fileScope.values()) {
            if (symbol.isExported()) {
                allocScopesList.add(new AllocScopeNode(symbol));
            }
        }
        this.allocScopes = allocScopesList.toArray(AllocScopeNode.EMPTY);
    }

    public void execute(LLVMContext context, LLVMLocalScope localScope) {
        synchronized (context) {
            localScope.addMissingLinkageName(fileScope);
            for (int i = 0; i < allocScopes.length; i++) {
                AllocScopeNode allocScope = allocScopes[i];
                allocScope.allocateScope(context, localScope);
            }
        }
    }

    /**
     * Allocating a symbol to the global and local scope of a module.
     */
    private static final class AllocScopeNode extends LLVMNode {

        static final AllocScopeNode[] EMPTY = {};
        final LLVMSymbol symbol;

        AllocScopeNode(LLVMSymbol symbol) {
            this.symbol = symbol;
        }

        void allocateScope(LLVMContext context, LLVMLocalScope localScope) {
            LLVMScope globalScope = context.getGlobalScope();
            LLVMSymbol exportedSymbol = globalScope.get(symbol.getName());
            if (exportedSymbol == null) {
                globalScope.register(symbol);
            }
            LLVMSymbol exportedSymbolFromLocal = localScope.get(symbol.getName());
            if (exportedSymbolFromLocal == null) {
                localScope.register(symbol);
            }
        }
    }
}
