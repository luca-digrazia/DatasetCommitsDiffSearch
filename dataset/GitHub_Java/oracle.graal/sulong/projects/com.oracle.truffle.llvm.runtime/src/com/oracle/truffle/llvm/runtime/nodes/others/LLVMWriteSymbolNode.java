/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.except.LLVMIllegalSymbolIndexException;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMWriteSymbolNode extends LLVMNode {

    protected final LLVMSymbol symbol;

    public static final LLVMWriteSymbolNode[] EMPTY = {};

    public LLVMWriteSymbolNode(LLVMSymbol symbol) {
        if (symbol.isAlias()) {
            throw new IllegalStateException("Writing an alias into symbol table is forbidden.");
        }
        this.symbol = symbol;
    }

    public abstract void execute(LLVMPointer pointer);

    @Specialization
    void doWrite(LLVMPointer pointer,
                    @CachedContext(LLVMLanguage.class) LLVMContext context) {
        AssumedValue<LLVMPointer>[] symbols = context.findSymbolTable(symbol.getBitcodeID(false));
        synchronized (symbols) {
            CompilerAsserts.partialEvaluationConstant(symbol);
            try {
                int index = symbol.getSymbolIndex(false);
                symbols[index] = newAssumedValue(symbol, pointer);
            } catch (LLVMIllegalSymbolIndexException e) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMLinkerException(this, "Writing symbol into symbol table is inconsistent.");
            }
        }
    }

    @TruffleBoundary
    private static AssumedValue<LLVMPointer> newAssumedValue(LLVMSymbol symbol, LLVMPointer pointer) {
        return new AssumedValue<>(symbol.getKind() + "." + symbol.getName(), pointer);
    }
}
