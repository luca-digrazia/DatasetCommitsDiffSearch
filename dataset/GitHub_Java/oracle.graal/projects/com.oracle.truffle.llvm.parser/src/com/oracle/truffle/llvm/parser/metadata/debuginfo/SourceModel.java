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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MetadataList;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.MetadataConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitorAdapter;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SourceModel {

    public static final int LLVM_DBG_INTRINSICS_VALUE_ARGINDEX = 0;

    public static final String LLVM_DBG_DECLARE_NAME = "@llvm.dbg.declare";
    public static final int LLVM_DBG_DECLARE_LOCALREF_ARGINDEX = 1;

    public static final String LLVM_DBG_VALUE_NAME = "@llvm.dbg.value";
    public static final int LLVM_DBG_VALUE_LOCALREF_ARGINDEX = 2;

    public static SourceModel generate(ModelModule irModel, Source bitcodeSource) {
        final MetadataList moduleMetadata = irModel.getMetadata();
        final Parser parser = new Parser(moduleMetadata, bitcodeSource);
        UpgradeMDToFunctionMappingVisitor.upgrade(moduleMetadata);
        irModel.accept(parser);
        return parser.sourceModel;
    }

    public static final class Function {

        private final FunctionDefinition definition;

        private final List<Variable> locals;

        private final List<Variable> globals;

        private final Map<Instruction, SourceSection> instructions = new HashMap<>();

        private SourceSection lexicalScope;

        private Function(FunctionDefinition definition, List<Variable> globals) {
            this.definition = definition;
            this.globals = globals;
            this.locals = new ArrayList<>();
        }

        public List<Variable> getGlobals() {
            return globals;
        }

        public List<Variable> getLocals() {
            return locals;
        }

        public SourceSection getSourceSection() {
            return lexicalScope;
        }

        public SourceSection getSourceSection(Instruction instruction) {
            return instructions.get(instruction);
        }
    }

    public static final class Variable implements Symbol {

        private final String name;

        private final Symbol symbol;

        private final LLVMSourceType type;

        private Variable(String name, Symbol symbol, LLVMSourceType type) {
            this.name = name;
            this.symbol = symbol;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Symbol getSymbol() {
            return symbol;
        }

        public LLVMSourceType getSourceType() {
            return type;
        }

        @Override
        public Type getType() {
            return MetaType.DEBUG;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final List<Variable> globals = new ArrayList<>();

    private SourceModel() {
    }

    private static final class Parser implements ModelVisitor, FunctionVisitor, InstructionVisitorAdapter {

        private final LexicalScopeExtractor lexicalScopeExtractor;
        private final MDTypeExtractor typeExtractor;
        private final SourceModel sourceModel;

        private final MetadataList moduleMetadata;
        private final Source bitcodeSource;

        private Function currentFunction = null;

        private SourceSection currentScopeStart = null;
        private SourceSection currentScopeEnd = null;

        private Parser(MetadataList moduleMetadata, Source bitcodeSource) {
            this.moduleMetadata = moduleMetadata;
            this.bitcodeSource = bitcodeSource;
            typeExtractor = new MDTypeExtractor();
            this.typeExtractor.setScopeMetadata(moduleMetadata);
            lexicalScopeExtractor = new LexicalScopeExtractor();
            sourceModel = new SourceModel();
        }

        @Override
        public void visit(FunctionDefinition function) {
            currentFunction = new Function(function, sourceModel.globals);
            currentScopeStart = lexicalScopeExtractor.get(function);
            currentScopeEnd = currentScopeStart;
            typeExtractor.setScopeMetadata(function.getMetadata());

            function.accept(this);
            function.setSourceFunction(currentFunction);

            SourceSection lexicalScope;
            if (currentScopeStart != null && currentScopeEnd != null) {
                if (currentScopeStart != currentScopeEnd) {
                    final int startIndex = currentScopeStart.getCharIndex();
                    final int length = currentScopeEnd.getCharEndIndex() - startIndex;
                    final Source source = currentScopeStart.getSource();
                    // Truffle breakpoints are only hit if the text referenced by the SourceSection
                    // of the corresponding node is fully contained in its rootnode's
                    // sourcesection's text
                    try {
                        lexicalScope = source.createSection(startIndex, length);
                    } catch (Throwable ignored) {
                        // this might fail in case the source file was modified after compilation
                        lexicalScope = null;
                    }

                } else {
                    lexicalScope = currentScopeStart;
                }

            } else {
                // debug information is not available or the current function is not included in it
                final String sourceText = String.format("%s:%s", bitcodeSource.getName(), function.getName());
                final Source irSource = Source.newBuilder(sourceText).mimeType(LexicalScopeExtractor.MIMETYPE_PLAINTEXT).name(sourceText).build();
                lexicalScope = irSource.createSection(1);
            }
            currentFunction.lexicalScope = lexicalScope;

            typeExtractor.setScopeMetadata(moduleMetadata);
            currentScopeEnd = null;
            currentScopeStart = null;
            currentFunction = null;
        }

        private void visitGlobal(GlobalValueSymbol global) {
            if (!global.hasAttachedMetadata()) {
                return;
            }

            final MDBaseNode md = global.getMetadataAttachment(MDKind.DBG_NAME);
            if (md == null) {
                return;
            }

            final String name = MDNameExtractor.getName(md);
            final LLVMSourceType type = typeExtractor.parseType(md);
            Variable globalVar = new Variable(name, global, type);
            sourceModel.globals.add(globalVar);
        }

        @Override
        public void visit(GlobalAlias alias) {
            visitGlobal(alias);
        }

        @Override
        public void visit(GlobalConstant constant) {
            visitGlobal(constant);
        }

        @Override
        public void visit(GlobalVariable variable) {
            visitGlobal(variable);
        }

        @Override
        public void visit(InstructionBlock block) {
            block.accept(this);
        }

        @Override
        public void defaultAction(Instruction instruction) {
            final SourceSection instructionScope = lexicalScopeExtractor.get(instruction);
            if (instructionScope != null) {
                currentFunction.instructions.put(instruction, instructionScope);
            } else {
                return;
            }

            if (currentScopeStart == null || currentScopeEnd == null) {
                currentScopeStart = instructionScope;
                currentScopeEnd = instructionScope;
                return;
            }

            if (!currentScopeStart.getSource().equals(instructionScope.getSource())) {
                // inlined functions should not extend the scope
                return;
            }

            if (currentScopeEnd.getCharEndIndex() < instructionScope.getCharEndIndex()) {
                currentScopeEnd = instructionScope;
            }

            if (currentScopeStart.getCharIndex() > instructionScope.getCharIndex()) {
                currentScopeStart = instructionScope;
            }
        }

        @Override
        public void visit(VoidCallInstruction call) {
            final Symbol callTarget = call.getCallTarget();
            if (callTarget instanceof FunctionDeclaration) {
                int mdlocalArgumentIndex = -1;
                switch (((FunctionDeclaration) callTarget).getName()) {
                    case LLVM_DBG_DECLARE_NAME:
                        if (call.getArgumentCount() >= LLVM_DBG_DECLARE_LOCALREF_ARGINDEX) {
                            mdlocalArgumentIndex = LLVM_DBG_DECLARE_LOCALREF_ARGINDEX;
                        }
                        break;

                    case LLVM_DBG_VALUE_NAME:
                        if (call.getArgumentCount() >= LLVM_DBG_VALUE_LOCALREF_ARGINDEX) {
                            mdlocalArgumentIndex = LLVM_DBG_VALUE_LOCALREF_ARGINDEX;
                        }
                        break;
                }

                if (mdlocalArgumentIndex >= 0) {
                    handleDebugIntrinsic(call, mdlocalArgumentIndex);
                }
            }

            defaultAction(call);
        }

        private static final int LLVM_DBG_INTRINSICS_VALUEINDEX = 0;

        private void handleDebugIntrinsic(VoidCallInstruction call, int mdlocalArgumentIndex) {
            Symbol value = call.getArgument(LLVM_DBG_INTRINSICS_VALUEINDEX);
            if (value instanceof MetadataConstant) {
                // the first argument should reference the allocation site of the variable
                final long mdIndex = ((MetadataConstant) value).getValue();
                value = MDSymbolExtractor.getSymbol(currentFunction.definition.getMetadata().getMDRef(mdIndex));
            }

            if (value instanceof ValueInstruction) {
                ((ValueInstruction) value).setSourceVariable(true);
            } else if (value instanceof FunctionParameter) {
                ((FunctionParameter) value).setSourceVariable(true);
            } else {
                return;
            }

            final Symbol mdLocalMDRef = call.getArgument(mdlocalArgumentIndex);
            if (mdLocalMDRef instanceof MetadataConstant) {
                final long mdIndex = ((MetadataConstant) mdLocalMDRef).getValue();
                final MDBaseNode mdLocal = currentFunction.definition.getMetadata().getMDRef(mdIndex);
                final LLVMSourceType type = typeExtractor.parseType(mdLocal);
                final String varName = MDNameExtractor.getName(mdLocal);
                final Variable var = new Variable(varName, value, type);
                currentFunction.locals.add(var);

                // ensure that lifetime analysis does not kill the variable before it is used in
                // the call
                call.replace(call.getArgument(LLVM_DBG_INTRINSICS_VALUEINDEX), value);
                call.replace(mdLocalMDRef, var);
            }
        }
    }
}
