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
package com.oracle.truffle.llvm.parser.factories;

import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.BitwiseBinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.Type;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStatementNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMVectorNode;
import com.oracle.truffle.llvm.nodes.control.LLVMIndirectBranchNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;

public class NodeFactoryFacadeImpl implements NodeFactoryFacade {

    protected final LLVMParserRuntime runtime;

    public NodeFactoryFacadeImpl(LLVMParserRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public LLVMVectorNode createInsertElement(LLVMBaseType resultType, LLVMExpressionNode vector, Type vectorType, LLVMExpressionNode element, LLVMExpressionNode index) {
        return LLVMVectorFactory.createInsertElement(runtime, resultType, vector, vectorType, element, (LLVMI32Node) index);
    }

    @Override
    public LLVMExpressionNode createExtractElement(LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index) {
        return LLVMVectorFactory.createExtractElement(resultType, vector, index);
    }

    @Override
    public LLVMVectorNode createShuffleVector(LLVMBaseType llvmType, LLVMExpressionNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2, LLVMExpressionNode mask) {
        return LLVMVectorFactory.createShuffleVector(llvmType, (LLVMAddressNode) target, vector1, vector2, (LLVMI32VectorNode) mask);
    }

    @Override
    public LLVMExpressionNode createLoad(ResolvedType resolvedResultType, LLVMExpressionNode loadTarget) {
        return LLVMMemoryReadWriteFactory.createLoad(resolvedResultType, (LLVMAddressNode) loadTarget, runtime);
    }

    @Override
    public LLVMNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, ResolvedType type) {
        return LLVMMemoryReadWriteFactory.createStore((LLVMAddressNode) pointerNode, valueNode, type);
    }

    @Override
    public LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType type, LLVMBaseType llvmType, LLVMExpressionNode target) {
        return LLVMLogicalFactory.createLogicalOperation(left, right, type, llvmType, (LLVMAddressNode) target);
    }

    @Override
    public LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, BitwiseBinaryInstruction type, LLVMBaseType llvmType, LLVMExpressionNode target) {
        return LLVMLogicalFactory.createLogicalOperation(left, right, type, llvmType, (LLVMAddressNode) target);
    }

    @Override
    public LLVMExpressionNode createUndefinedValue(EObject t) {
        return LLVMLiteralFactory.createUndefinedValue(runtime, t);
    }

    @Override
    public LLVMExpressionNode createSimpleConstantNoArray(String stringValue, LLVMBaseType instructionType, ResolvedType type) {
        return LLVMLiteralFactory.createSimpleConstantNoArray(stringValue, instructionType, type);
    }

    @Override
    public LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, LLVMExpressionNode target, ResolvedVectorType type) {
        return LLVMLiteralFactory.createVectorLiteralNode(listValues, (LLVMAddressNode) target, type);
    }

    @Override
    public LLVMNode createLLVMIntrinsic(String functionName, Object[] argNodes, FunctionDef def) {
        return LLVMIntrinsicFactory.create(functionName, argNodes, def, runtime);
    }

    @Override
    public LLVMStatementNode createRetVoid() {
        return LLVMFunctionFactory.createRetVoid(runtime);
    }

    @Override
    public LLVMStatementNode createNonVoidRet(LLVMExpressionNode retValue, ResolvedType resolvedType) {
        return LLVMFunctionFactory.createNonVoidRet(runtime, retValue, resolvedType);
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType) {
        return LLVMFunctionFactory.createFunctionArgNode(argIndex, paramType);
    }

    @Override
    public LLVMNode createFunctionCall(LLVMFunctionNode functionNode, LLVMExpressionNode[] argNodes, LLVMBaseType llvmType) {
        return LLVMFunctionFactory.createFunctionCall(runtime, functionNode, argNodes, llvmType);
    }

    @Override
    public LLVMExpressionNode createFrameRead(LLVMBaseType llvmType, FrameSlot frameSlot) {
        return LLVMFrameReadWriteFactory.createFrameRead(llvmType, frameSlot);
    }

    @Override
    public LLVMNode createFrameWrite(LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot) {
        return LLVMFrameReadWriteFactory.createFrameWrite(llvmType, result, slot);
    }

    @Override
    public FrameSlotKind getFrameSlotKind(ResolvedType type) {
        return LLVMFrameReadWriteFactory.getFrameSlotKind(type);
    }

    @Override
    public FrameSlotKind getFrameSlotKind(LLVMBaseType llvmType) {
        return LLVMFrameReadWriteFactory.getFrameSlotKind(llvmType);
    }

    @Override
    public LLVMI1Node createIntegerComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMIntegerComparisonType type) {
        return LLVMComparisonFactory.createIntegerComparison(left, right, llvmType, type);
    }

    @Override
    public LLVMI1Node createFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMFloatComparisonType conditionString) {
        return LLVMComparisonFactory.createFloatComparison(left, right, llvmType, conditionString);
    }

    @Override
    public LLVMExpressionNode createCast(LLVMExpressionNode fromNode, ResolvedType targetType, ResolvedType fromType, LLVMConversionType type) {
        return LLVMCastsFactory.cast(fromNode, targetType, fromType, type);
    }

    @Override
    public LLVMExpressionNode createArithmeticOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType type, LLVMBaseType llvmType, LLVMExpressionNode target) {
        return LLVMArithmeticFactory.createArithmeticOperation(left, right, type, llvmType, (LLVMAddressNode) target);
    }

    @Override
    public LLVMExpressionNode createExtractValue(LLVMBaseType type, LLVMExpressionNode targetAddress) {
        return LLVMAggregateFactory.createExtractValue(type, (LLVMAddressNode) targetAddress);
    }

    public LLVMAddressNode createGetElementPtr(LLVMBaseType llvmBaseType, LLVMExpressionNode currentAddress, LLVMExpressionNode valueRef, int indexedTypeLength) {
        return LLVMGetElementPtrFactory.create(llvmBaseType, (LLVMAddressNode) currentAddress, valueRef, indexedTypeLength);
    }

    public Class<?> getJavaClass(LLVMExpressionNode node) {
        return LLVMNativeFactory.getJavaClass(node);
    }

    public LLVMExpressionNode createSelect(LLVMBaseType llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        return LLVMSelectFactory.createSelect(llvmType, (LLVMI1Node) condition, trueValue, falseValue, runtime);
    }

    public LLVMExpressionNode createZeroVectorInitializer(int nrElements, LLVMExpressionNode target, LLVMBaseType llvmType) {
        return LLVMLiteralFactory.createZeroVectorInitializer(nrElements, (LLVMAddressNode) target, llvmType);
    }

    public LLVMExpressionNode createLiteral(Object value, LLVMBaseType type) {
        return LLVMLiteralFactory.createLiteral(value, type);
    }

    public Node createStructWriteNode(LLVMExpressionNode node, ResolvedType type) {
        return LLVMAggregateFactory.createStructWriteNode(node, type);
    }

    public LLVMExpressionNode createStructLiteralNode(int[] offsets, Node[] nodes, LLVMExpressionNode alloc) {
        LLVMStructWriteNode[] structWriteNodes = new LLVMStructWriteNode[nodes.length];
        System.arraycopy(nodes, 0, structWriteNodes, 0, nodes.length);
        return LLVMAggregateFactory.createStructLiteralNode(offsets, structWriteNodes, (LLVMAddressNode) alloc);
    }

    public LLVMNode createUnreachableNode() {
        return new LLVMUnreachableNode();
    }

    public LLVMNode createIndirectBranch(LLVMExpressionNode value, int[] labelTargets) {
        return new LLVMIndirectBranchNode((LLVMAddressNode) value, labelTargets);
    }

}
