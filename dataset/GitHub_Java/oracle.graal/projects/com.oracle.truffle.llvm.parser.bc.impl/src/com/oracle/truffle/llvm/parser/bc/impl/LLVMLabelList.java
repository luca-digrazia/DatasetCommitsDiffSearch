/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.bc.impl;

import java.util.HashMap;
import java.util.Map;

import uk.ac.man.cs.llvm.ir.model.*;
import uk.ac.man.cs.llvm.ir.types.Type;

public final class LLVMLabelList {

    public static LLVMLabelList generate(Model model) {
        LLVMLabelListVisitor visitor = new LLVMLabelListVisitor();

        model.accept(visitor);

        return new LLVMLabelList(visitor.labels());
    }

    private final Map<String, Map<String, Integer>> labels;

    private LLVMLabelList(Map<String, Map<String, Integer>> labels) {
        this.labels = labels;
    }

    public Map<String, Integer> labels(String method) {
        return labels.get(method);
    }

    private static class LLVMLabelListVisitor implements ModelVisitor {

        private final Map<String, Map<String, Integer>> labels = new HashMap<>();

        public LLVMLabelListVisitor() {
        }

        private Map<String, Map<String, Integer>> labels() {
            return labels;
        }

        @Override
        public void visit(GlobalConstant constant) {
        }

        @Override
        public void visit(GlobalVariable variable) {
        }

        @Override
        public void visit(FunctionDeclaration method) {
        }

        @Override
        public void visit(FunctionDefinition method) {
            String name = method.getName();

            LLVMLabelListFunctionVisitor visitor = new LLVMLabelListFunctionVisitor();

            method.accept(visitor);

            labels.put(name, visitor.labels());
        }

        @Override
        public void visit(Type type) {
        }
    }

    private static class LLVMLabelListFunctionVisitor implements FunctionVisitor {

        private final Map<String, Integer> labels = new HashMap<>();

        private int index = 0;

        public LLVMLabelListFunctionVisitor() {
        }

        private Map<String, Integer> labels() {
            return labels;
        }

        @Override
        public void visit(Block block) {
            String name = block.getName();
            if (name.isEmpty() || "entry".equals(name)) {
                name = "%0";
            }
            labels.put(name, index++);
        }
    }
}
