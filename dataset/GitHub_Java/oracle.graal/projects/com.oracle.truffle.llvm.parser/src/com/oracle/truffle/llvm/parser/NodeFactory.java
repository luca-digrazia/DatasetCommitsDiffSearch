/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser;

import java.util.List;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.model.enums.Flag;
import com.oracle.truffle.llvm.parser.model.enums.ReadModifyWriteOperator;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMFrameValueAccess;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.interop.export.InteropNodeFactory;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateStringNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.memory.VarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

/**
 * This interface decouples the parser and the concrete implementation of the nodes by only making
 * {@link LLVMExpressionNode} and {@link LLVMExpressionNode} visible. The parser should not directly
 * instantiate a node, but instead use the factory facade.
 */
public interface NodeFactory extends InteropNodeFactory {

    LLVMExpressionNode createInsertElement(Type resultType, LLVMExpressionNode vector, LLVMExpressionNode element, LLVMExpressionNode index);

    LLVMExpressionNode createExtractElement(Type resultType, LLVMExpressionNode vector, LLVMExpressionNode index);

    LLVMExpressionNode createShuffleVector(Type llvmType, LLVMExpressionNode vector1, LLVMExpressionNode vector2,
                    LLVMExpressionNode mask);

    LLVMExpressionNode createLoad(Type resolvedResultType, LLVMExpressionNode loadTarget);

    LLVMStatementNode createStore(LLVMContext context, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type, LLVMSourceLocation source);

    LLVMExpressionNode createReadModifyWrite(ReadModifyWriteOperator operator, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type);

    LLVMStatementNode createFence();

    LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionKind opCode, Type llvmType, Flag[] flags);

    LLVMExpressionNode createLiteral(Object value, Type type);

    LLVMExpressionNode createSimpleConstantNoArray(LLVMContext context, Object constant, Type instructionType);

    LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, Type type);

    LLVMStatementNode createFrameNuller(FrameSlot slot);

    LLVMControlFlowNode createRetVoid(LLVMSourceLocation source);

    LLVMControlFlowNode createNonVoidRet(LLVMContext context, LLVMExpressionNode retValue, Type resolvedType, LLVMSourceLocation source);

    LLVMExpressionNode createFunctionArgNode(int argIndex, Type paramType);

    LLVMExpressionNode createFunctionArgNode(int argIndex);

    LLVMExpressionNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type, LLVMSourceLocation sourceSection);

    LLVMControlFlowNode createFunctionInvoke(FrameSlot resultLocation, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type, int normalIndex,
                    int unwindIndex, LLVMStatementNode normalPhiWriteNodes,
                    LLVMStatementNode unwindPhiWriteNodes, LLVMSourceLocation sourceSection);

    LLVMExpressionNode createFrameRead(Type llvmType, FrameSlot frameSlot);

    LLVMStatementNode createFrameWrite(Type llvmType, LLVMExpressionNode result, FrameSlot slot, LLVMSourceLocation sourceSection);

    LLVMExpressionNode createComparison(CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs);

    LLVMExpressionNode createCast(LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType type);

    LLVMExpressionNode createArithmeticOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, Type llvmType, Flag[] flags);

    LLVMExpressionNode createExtractValue(Type type, LLVMExpressionNode targetAddress);

    LLVMExpressionNode createTypedElementPointer(LLVMExpressionNode aggregateAddress, LLVMExpressionNode index, long indexedTypeLength,
                    Type targetType);

    LLVMExpressionNode createSelect(Type type, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue);

    LLVMExpressionNode createZeroVectorInitializer(int nrElements, VectorType llvmType);

    LLVMControlFlowNode createUnreachableNode();

    LLVMControlFlowNode createIndirectBranch(LLVMExpressionNode value, int[] labelTargets, LLVMStatementNode[] phiWrites, LLVMSourceLocation source);

    LLVMControlFlowNode createSwitch(LLVMExpressionNode cond, int[] labels, LLVMExpressionNode[] cases,
                    Type llvmType, LLVMStatementNode[] phiWriteNodes, LLVMSourceLocation source);

    LLVMControlFlowNode createConditionalBranch(int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMStatementNode truePhiWriteNodes,
                    LLVMStatementNode falsePhiWriteNodes, LLVMSourceLocation sourceSection);

    LLVMControlFlowNode createUnconditionalBranch(int unconditionalIndex, LLVMStatementNode phi, LLVMSourceLocation source);

    LLVMExpressionNode createArrayLiteral(LLVMContext context, List<LLVMExpressionNode> arrayValues, ArrayType arrayType);

    /*
     * Stack allocations with type (LLVM's alloca instruction)
     */
    LLVMExpressionNode createAlloca(LLVMContext context, Type type);

    LLVMExpressionNode createAlloca(LLVMContext context, Type type, int alignment);

    LLVMExpressionNode createAllocaArray(LLVMContext context, Type elementType, LLVMExpressionNode numElements, int alignment);

    /*
     * Stack allocation without a type
     */
    VarargsAreaStackAllocationNode createVarargsAreaStackAllocation(LLVMContext context);

    LLVMExpressionNode createInsertValue(LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, long offset, LLVMExpressionNode valueToInsert,
                    Type llvmType);

    LLVMExpressionNode createZeroNode(LLVMExpressionNode addressNode, int size);

    LLVMExpressionNode createStructureConstantNode(LLVMContext context, Type structureType, boolean packed, Type[] types, LLVMExpressionNode[] constants);

    LLVMStatementNode createBasicBlockNode(LLVMStatementNode[] statementNodes, LLVMControlFlowNode terminatorNode, int blockId, String blockName);

    LLVMExpressionNode createFunctionBlockNode(FrameSlot exceptionValueSlot, List<? extends LLVMStatementNode> basicBlockNodes, FrameSlot[][] beforeBlockNuller,
                    FrameSlot[][] afterBlockNuller, LLVMSourceLocation sourceSection, LLVMStatementNode[] copyArgumentsToFrame);

    RootNode createFunctionStartNode(LLVMContext context, LLVMExpressionNode functionBodyNode, SourceSection sourceSection, FrameDescriptor frameDescriptor, FunctionDefinition functionHeader,
                    Source bcSource, LLVMSourceLocation location);

    LLVMExpressionNode createInlineAssemblerExpression(LLVMContext context, ExternalLibrary library, String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type[] argTypes, Type retType,
                    LLVMSourceLocation sourceSection);

    LLVMExpressionNode createLandingPad(LLVMExpressionNode allocateLandingPadValue, FrameSlot exceptionSlot, boolean cleanup, long[] clauseKinds,
                    LLVMExpressionNode[] entries, LLVMExpressionNode getStack);

    LLVMControlFlowNode createResumeInstruction(FrameSlot exceptionSlot, LLVMSourceLocation sourceSection);

    LLVMExpressionNode createCompareExchangeInstruction(LLVMContext context, Type returnType, Type elementType, LLVMExpressionNode ptrNode, LLVMExpressionNode cmpNode,
                    LLVMExpressionNode newNode);

    LLVMExpressionNode createLLVMBuiltin(LLVMContext context, Symbol target, LLVMExpressionNode[] args, int callerArgumentCount, LLVMSourceLocation sourceSection);

    LLVMStatementNode createPhi(LLVMExpressionNode[] from, FrameSlot[] to, Type[] types);

    LLVMExpressionNode createCopyStructByValue(LLVMContext context, Type type, LLVMExpressionNode parameterNode);

    LLVMExpressionNode createVarArgCompoundValue(int length, int alignment, LLVMExpressionNode parameterNode);

    LLVMStatementNode createDebugValueUpdate(boolean isDeclaration, LLVMExpressionNode valueRead, FrameSlot targetSlot, LLVMExpressionNode aggregateRead, int partIndex, int[] clearParts);

    LLVMStatementNode createDebugValueInit(FrameSlot targetSlot, int[] offsets, int[] lengths);

    LLVMDebugObjectBuilder createDebugStaticValue(LLVMExpressionNode valueNode);

    LLVMDebugObjectBuilder createDebugDynamicValue(LLVMExpressionNode valueNode);

    LLVMFrameValueAccess createDebugFrameValue(FrameSlot slot, boolean isDeclaration);

    LLVMStatementNode registerSourceType(FrameSlot valueSlot, LLVMSourceType type);

    LLVMMemMoveNode createMemMove();

    LLVMMemSetNode createMemSet();

    LLVMAllocateStringNode createAllocateString();
}
