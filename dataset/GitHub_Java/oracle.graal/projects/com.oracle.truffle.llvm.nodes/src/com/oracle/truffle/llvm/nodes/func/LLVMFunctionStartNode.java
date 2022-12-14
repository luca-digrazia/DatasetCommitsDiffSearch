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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.context.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.api.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.parser.api.model.functions.FunctionDefinition;

public class LLVMFunctionStartNode extends RootNode {

    @Child private LLVMExpressionNode node;
    @Children private final LLVMExpressionNode[] beforeFunction;
    @Children private final LLVMExpressionNode[] afterFunction;
    @CompilationFinal(dimensions = 1) private final LLVMStackFrameNuller[] nullers;
    private final FunctionDefinition functionHeader;

    public LLVMFunctionStartNode(LLVMExpressionNode node, LLVMExpressionNode[] beforeFunction, LLVMExpressionNode[] afterFunction, SourceSection sourceSection, FrameDescriptor frameDescriptor,
                    FunctionDefinition functionHeader, LLVMStackFrameNuller[] initNullers) {
        super(LLVMLanguage.class, sourceSection, frameDescriptor);
        this.node = node;
        this.beforeFunction = beforeFunction;
        this.afterFunction = afterFunction;
        this.functionHeader = functionHeader;
        this.nullers = initNullers;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        for (LLVMStackFrameNuller nuller : nullers) {
            nuller.nullifySlot(frame);
        }
        CompilerAsserts.compilationConstant(beforeFunction);
        for (LLVMExpressionNode before : beforeFunction) {
            before.executeGeneric(frame);
        }
        Object result = node.executeGeneric(frame);
        CompilerAsserts.compilationConstant(afterFunction);
        for (LLVMExpressionNode after : afterFunction) {
            after.executeGeneric(frame);
        }
        return result;
    }

    @Override
    public String toString() {
        return getFunctionName();
    }

    public String getFunctionName() {
        return functionHeader == null ? "null" : functionHeader.getName();
    }

    public FunctionDefinition getFunctionHeader() {
        return functionHeader;
    }

    @Override
    public String getName() {
        return getFunctionName();
    }

}
