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
package com.oracle.truffle.llvm.parser;

import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.BitwiseBinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.Type;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction.LLVMRuntimeType;

/**
 * This interface decouples the parser and the concrete implementation of the nodes by only making
 * {@link LLVMExpressionNode} and {@link LLVMNode} visible. The parser should not directly
 * instantiate a node, but instead use the factory facade.
 */
public interface NodeFactoryFacade {

    LLVMExpressionNode createInsertElement(LLVMBaseType resultType, LLVMExpressionNode vector, Type vectorType, LLVMExpressionNode element, LLVMExpressionNode index);

    LLVMExpressionNode createExtractElement(LLVMBaseType resultType, LLVMExpressionNode vector, LLVMExpressionNode index);

    LLVMExpressionNode createShuffleVector(LLVMBaseType llvmType, LLVMExpressionNode target, LLVMExpressionNode vector1, LLVMExpressionNode vector2, LLVMExpressionNode mask);

    LLVMExpressionNode createLoad(ResolvedType resolvedResultType, LLVMExpressionNode loadTarget);

    LLVMNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, ResolvedType type);

    LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMLogicalInstructionType opCode, LLVMBaseType llvmType, LLVMExpressionNode target);

    LLVMExpressionNode createLogicalOperation(LLVMExpressionNode left, LLVMExpressionNode right, BitwiseBinaryInstruction type, LLVMBaseType llvmType, LLVMExpressionNode target);

    LLVMExpressionNode createUndefinedValue(EObject t);

    LLVMExpressionNode createLiteral(Object value, LLVMBaseType type);

    LLVMExpressionNode createSimpleConstantNoArray(String stringValue, LLVMBaseType instructionType, ResolvedType type);

    LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, LLVMExpressionNode target, ResolvedVectorType type);

    /**
     * Creates an intrinsic for a <code>@llvm.*</code> function.
     *
     * @param functionName the name of the intrinsic function starting with <code>@llvm.</code>
     * @param argNodes the arguments to the intrinsic function
     * @param functionDef the function definition of the function from which the intrinsic is called
     * @return the created intrinsic
     */
    LLVMNode createLLVMIntrinsic(String functionName, Object[] argNodes, FunctionDef functionDef);

    LLVMNode createRetVoid();

    LLVMNode createNonVoidRet(LLVMExpressionNode retValue, ResolvedType resolvedType);

    LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType);

    LLVMNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, LLVMBaseType llvmType);

    LLVMExpressionNode createFrameRead(LLVMBaseType llvmType, FrameSlot frameSlot);

    LLVMNode createFrameWrite(LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot);

    FrameSlotKind getFrameSlotKind(ResolvedType type);

    LLVMExpressionNode createIntegerComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMIntegerComparisonType type);

    LLVMExpressionNode createFloatComparison(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMFloatComparisonType type);

    LLVMExpressionNode createCast(LLVMExpressionNode fromNode, ResolvedType targetType, ResolvedType fromType, LLVMConversionType type);

    LLVMExpressionNode createArithmeticOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType instr, LLVMBaseType llvmType, LLVMExpressionNode target);

    LLVMExpressionNode createExtractValue(LLVMBaseType type, LLVMExpressionNode targetAddress);

    LLVMExpressionNode createGetElementPtr(LLVMBaseType llvmBaseType, LLVMExpressionNode currentAddress, LLVMExpressionNode valueRef, int indexedTypeLength);

    Class<?> getJavaClass(LLVMExpressionNode llvmExpressionNode);

    LLVMExpressionNode createSelect(LLVMBaseType llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue);

    /**
     * Creates a zero vector initializer.
     *
     * @param nrElements the number of elements of the vector
     * @param target the allocated result storage
     * @param llvmType the type of the vector
     *
     * @return the zero vector initializer
     */
    LLVMExpressionNode createZeroVectorInitializer(int nrElements, LLVMExpressionNode target, LLVMBaseType llvmType);

    /**
     * Creates a node representing an <code>unreachable</code> instruction.
     *
     * @return an unreachable node
     * @see <a href="http://llvm.org/docs/LangRef.html#unreachable-instruction">Unreachable in the
     *      LLVM Language Reference Manual</a>
     */
    LLVMNode createUnreachableNode();

    LLVMNode createIndirectBranch(LLVMExpressionNode value, int[] labelTargets, LLVMNode[] phiWrites);

    LLVMNode createSwitch(LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases,
                    LLVMBaseType llvmType, LLVMNode[] phiWriteNodes);

    LLVMNode createConditionalBranch(int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMNode[] truePhiWriteNodes, LLVMNode[] falsePhiWriteNodes);

    LLVMNode createUnconditionalBranch(int unconditionalIndex, LLVMNode[] phiWrites);

    LLVMExpressionNode createArrayLiteral(List<LLVMExpressionNode> arrayValues, ResolvedType arrayType);

    /**
     * Creates an <code>alloca</code> node with a certain number of elements.
     *
     * @param numElementsType the type of <code>numElements</code>
     * @param byteSize the size of an element
     * @param alignment the alignment requirement
     * @param numElements how many elements to allocate, may be <code>null</code> if only one
     *            element should be allocated
     * @param type the type of an element, may be <code>null</code> if only one element should be
     *            allocated
     * @return a node that allocates the specified number of elements
     */
    LLVMExpressionNode createAlloc(ResolvedType type, int byteSize, int alignment, LLVMBaseType numElementsType, LLVMExpressionNode numElements);

    LLVMExpressionNode createInsertValue(LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset, LLVMExpressionNode valueToInsert, LLVMBaseType llvmType);

    LLVMExpressionNode createZeroNode(LLVMExpressionNode addressNode, int size);

    LLVMExpressionNode createEmptyStructLiteralNode(LLVMExpressionNode alloca, int byteSize);

    LLVMExpressionNode createGetElementPtr(LLVMExpressionNode currentAddress, LLVMExpressionNode oneValueNode, int currentOffset);

    /**
     * Creates the global root (e.g., the main function in C).
     *
     * @param staticInits
     * @param mainCallTarget
     * @param allocatedGlobalAddresses
     * @param args
     * @return the global root
     */
    RootNode createGlobalRootNode(LLVMNode[] staticInits, RootCallTarget mainCallTarget, LLVMAddress[] allocatedGlobalAddresses, Object... args);

    /**
     * Wraps the global root (e.g., the main function in C) to convert its result.
     *
     * @param mainCallTarget
     * @param returnType
     * @return the wrapped global root
     */
    RootNode createGlobalRootNodeWrapping(RootCallTarget mainCallTarget, LLVMRuntimeType returnType);

    /**
     * Creates a structure literal node.
     *
     * @param structureType type of the structure
     * @param packed whether the struct is packed (alignment of the struct is one byte and there is
     *            no padding between the elements)
     * @param types the types of the structure members
     * @param constants the structure members
     * @return the constructed structure literal
     */
    LLVMExpressionNode createStructureConstantNode(ResolvedType structureType, boolean packed, ResolvedType[] types, LLVMExpressionNode[] constants);

    LLVMNode createMemCopyNode(LLVMExpressionNode globalVarAddress, LLVMExpressionNode constant, LLVMExpressionNode lengthNode, LLVMExpressionNode alignNode, LLVMExpressionNode isVolatileNode);

    /**
     * Creates a basic block node.
     *
     * @param statementNodes the statement nodes that do not change control flow
     * @param terminatorNode the terminator instruction node that changes control flow
     * @return the basic block node
     */
    LLVMNode createBasicBlockNode(LLVMNode[] statementNodes, LLVMNode terminatorNode);

    /**
     * Creates a node that groups together several basic blocks in a function and returns the
     * function's result.
     *
     * @param returnSlot the frame slot for the return value
     * @param basicBlockNodes the basic blocks
     * @param indexToSlotNuller nuller node for nulling dead variables
     * @return the function block node
     */
    LLVMExpressionNode createFunctionBlockNode(FrameSlot returnSlot, List<LLVMNode> basicBlockNodes, LLVMStackFrameNuller[][] indexToSlotNuller);

    /**
     * Creates the entry point for a function.
     *
     * @param functionBodyNode the body of a function that returns the functions result
     * @param beforeFunction function prologue nodes
     * @param afterFunction function epilogue nodes
     * @param frameDescriptor
     * @param functionName
     * @return a function root node
     */
    RootNode createFunctionStartNode(LLVMExpressionNode functionBodyNode, LLVMNode[] beforeFunction, LLVMNode[] afterFunction, FrameDescriptor frameDescriptor, String functionName);

    /**
     * Returns the index of the first argument of the formal parameter list.
     *
     * @return the index
     */
    int getArgStartIndex();

    /**
     * Creates an inline assembler instruction.
     *
     * @param asmExpression
     * @param asmFlags
     * @param args
     * @param retType the type the inline assembler instruction produces
     * @return an inline assembler node
     */
    LLVMNode createInlineAssemblerExpression(String asmExpression, String asmFlags, LLVMExpressionNode[] args, LLVMBaseType retType);

}
