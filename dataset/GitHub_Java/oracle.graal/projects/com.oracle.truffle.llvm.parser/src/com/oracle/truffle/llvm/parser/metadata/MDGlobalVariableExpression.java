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
package com.oracle.truffle.llvm.parser.metadata;

public final class MDGlobalVariableExpression implements MDBaseNode {

    private static final int ARGINDEX_VARIABLE = 1;
    private static final int ARGINDEX_EXPRESSION = 2;

    private final MDReference globalVariable;
    private final MDReference expression;

    public MDGlobalVariableExpression(MDReference globalVariable, MDReference expression) {
        this.globalVariable = globalVariable;
        this.expression = expression;
    }

    public static MDGlobalVariableExpression create(long[] args, MetadataList md) {
        final MDReference varRef = md.getMDRefOrNullRef(args[ARGINDEX_VARIABLE]);
        final MDReference exprRef = md.getMDRefOrNullRef(args[ARGINDEX_EXPRESSION]);
        return new MDGlobalVariableExpression(varRef, exprRef);
    }

    public MDReference getGlobalVariable() {
        return globalVariable;
    }

    public MDReference getExpression() {
        return expression;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }
}
