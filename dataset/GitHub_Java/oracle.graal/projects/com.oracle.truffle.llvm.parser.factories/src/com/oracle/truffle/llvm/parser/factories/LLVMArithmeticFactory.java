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

import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMDoubleVectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI16VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI64VectorNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVM80BitFloatArithmeticNodeFactory.LLVM80BitFloatAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVM80BitFloatArithmeticNodeFactory.LLVM80BitFloatDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVM80BitFloatArithmeticNodeFactory.LLVM80BitFloatMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVM80BitFloatArithmeticNodeFactory.LLVM80BitFloatRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVM80BitFloatArithmeticNodeFactory.LLVM80BitFloatSubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMDoubleArithmeticNodeFactory.LLVMDoubleAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMDoubleArithmeticNodeFactory.LLVMDoubleDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMDoubleArithmeticNodeFactory.LLVMDoubleMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMDoubleArithmeticNodeFactory.LLVMDoubleRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMDoubleArithmeticNodeFactory.LLVMDoubleSubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMFloatArithmeticNodeFactory.LLVMFloatAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMFloatArithmeticNodeFactory.LLVMFloatDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMFloatArithmeticNodeFactory.LLVMFloatMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMFloatArithmeticNodeFactory.LLVMFloatRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMFloatArithmeticNodeFactory.LLVMFloatSubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI16ArithmeticNodeFactory.LLVMI16AddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI16ArithmeticNodeFactory.LLVMI16DivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI16ArithmeticNodeFactory.LLVMI16MulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI16ArithmeticNodeFactory.LLVMI16RemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI16ArithmeticNodeFactory.LLVMI16SubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI16ArithmeticNodeFactory.LLVMI16UDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI16ArithmeticNodeFactory.LLVMI16URemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI1ArithmeticNodeFactory.LLVMI1AddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI32ArithmeticNodeFactory.LLVMI32AddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI32ArithmeticNodeFactory.LLVMI32DivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI32ArithmeticNodeFactory.LLVMI32MulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI32ArithmeticNodeFactory.LLVMI32RemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI32ArithmeticNodeFactory.LLVMI32SubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI32ArithmeticNodeFactory.LLVMI32UDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI32ArithmeticNodeFactory.LLVMI32URemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI64ArithmeticNodeFactory.LLVMI64AddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI64ArithmeticNodeFactory.LLVMI64DivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI64ArithmeticNodeFactory.LLVMI64MulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI64ArithmeticNodeFactory.LLVMI64RemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI64ArithmeticNodeFactory.LLVMI64SubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI64ArithmeticNodeFactory.LLVMI64UDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI64ArithmeticNodeFactory.LLVMI64URemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI8ArithmeticNodeFactory.LLVMI8AddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI8ArithmeticNodeFactory.LLVMI8DivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI8ArithmeticNodeFactory.LLVMI8MulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI8ArithmeticNodeFactory.LLVMI8RemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI8ArithmeticNodeFactory.LLVMI8SubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI8ArithmeticNodeFactory.LLVMI8UDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMI8ArithmeticNodeFactory.LLVMI8URemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMIVarArithmeticNodeFactory.LLVMIVarAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMIVarArithmeticNodeFactory.LLVMIVarDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMIVarArithmeticNodeFactory.LLVMIVarMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMIVarArithmeticNodeFactory.LLVMIVarRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMIVarArithmeticNodeFactory.LLVMIVarSubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMIVarArithmeticNodeFactory.LLVMIVarUDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.integer.LLVMIVarArithmeticNodeFactory.LLVMIVarURemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMDoubleVectorArithmeticNodeFactory.LLVMDoubleVectorAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMDoubleVectorArithmeticNodeFactory.LLVMDoubleVectorDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMDoubleVectorArithmeticNodeFactory.LLVMDoubleVectorMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMDoubleVectorArithmeticNodeFactory.LLVMDoubleVectorRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMDoubleVectorArithmeticNodeFactory.LLVMDoubleVectorSubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMFloatVectorArithmeticNodeFactory.LLVMFloatVectorAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMFloatVectorArithmeticNodeFactory.LLVMFloatVectorDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMFloatVectorArithmeticNodeFactory.LLVMFloatVectorMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMFloatVectorArithmeticNodeFactory.LLVMFloatVectorRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMFloatVectorArithmeticNodeFactory.LLVMFloatVectorSubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI16VectorArithmeticNodeFactory.LLVMI16VectorAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI16VectorArithmeticNodeFactory.LLVMI16VectorDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI16VectorArithmeticNodeFactory.LLVMI16VectorMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI16VectorArithmeticNodeFactory.LLVMI16VectorRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI16VectorArithmeticNodeFactory.LLVMI16VectorSubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI16VectorArithmeticNodeFactory.LLVMI16VectorUDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI16VectorArithmeticNodeFactory.LLVMI16VectorURemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI32VectorArithmeticNodeFactory.LLVMI32VectorAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI32VectorArithmeticNodeFactory.LLVMI32VectorDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI32VectorArithmeticNodeFactory.LLVMI32VectorMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI32VectorArithmeticNodeFactory.LLVMI32VectorRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI32VectorArithmeticNodeFactory.LLVMI32VectorSubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI32VectorArithmeticNodeFactory.LLVMI32VectorUDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI32VectorArithmeticNodeFactory.LLVMI32VectorURemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI64VectorArithmeticNodeFactory.LLVMI64VectorAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI64VectorArithmeticNodeFactory.LLVMI64VectorDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI64VectorArithmeticNodeFactory.LLVMI64VectorMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI64VectorArithmeticNodeFactory.LLVMI64VectorRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI64VectorArithmeticNodeFactory.LLVMI64VectorSubNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI64VectorArithmeticNodeFactory.LLVMI64VectorUDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI64VectorArithmeticNodeFactory.LLVMI64VectorURemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI8VectorArithmeticNodeFactory.LLVMI8VectorAddNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI8VectorArithmeticNodeFactory.LLVMI8VectorDivNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI8VectorArithmeticNodeFactory.LLVMI8VectorMulNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI8VectorArithmeticNodeFactory.LLVMI8VectorRemNodeGen;
import com.oracle.truffle.llvm.nodes.op.arith.vector.LLVMI8VectorArithmeticNodeFactory.LLVMI8VectorSubNodeGen;
import com.oracle.truffle.llvm.parser.api.LLVMBaseType;
import com.oracle.truffle.llvm.parser.api.instructions.LLVMArithmeticInstructionType;

public final class LLVMArithmeticFactory {

    private LLVMArithmeticFactory() {
    }

    public static LLVMExpressionNode createArithmeticOperation(LLVMExpressionNode left, LLVMExpressionNode right, LLVMArithmeticInstructionType type, LLVMBaseType llvmType, LLVMAddressNode target) {
        if (left == null || right == null) {
            throw new AssertionError();
        }
        return createNode(left, right, llvmType, type, target);
    }

    private static LLVMExpressionNode createNode(LLVMExpressionNode left, LLVMExpressionNode right, LLVMBaseType llvmType, LLVMArithmeticInstructionType type, LLVMAddressNode target)
                    throws AssertionError {
        switch (llvmType) {
            case I1:
                return visitBinaryI1Instruction(type, (LLVMI1Node) left, (LLVMI1Node) right);
            case I8:
                return visitBinaryI8Instruction(type, (LLVMI8Node) left, (LLVMI8Node) right);
            case I16:
                return visitBinaryI16Instruction(type, (LLVMI16Node) left, (LLVMI16Node) right);
            case I32:
                return visitBinaryI32Instruction(type, (LLVMI32Node) left, (LLVMI32Node) right);
            case I64:
                return visitBinaryI64Instruction(type, (LLVMI64Node) left, (LLVMI64Node) right);
            case I_VAR_BITWIDTH:
                return visitBinaryIVarInstruction(type, (LLVMIVarBitNode) left, (LLVMIVarBitNode) right);
            case FLOAT:
                return visitBinaryFloatInstruction(type, (LLVMFloatNode) left, (LLVMFloatNode) right);
            case DOUBLE:
                return visitBinaryDoubleInstruction(type, (LLVMDoubleNode) left, (LLVMDoubleNode) right);
            case X86_FP80:
                return visitBinary80BitFloatInstruction(type, (LLVM80BitFloatNode) left, (LLVM80BitFloatNode) right);
            case I8_VECTOR:
                return visitBinaryI8VectorInstruction(type, (LLVMI8VectorNode) left, (LLVMI8VectorNode) right, target);
            case I16_VECTOR:
                return visitBinaryI16VectorInstruction(type, (LLVMI16VectorNode) left, (LLVMI16VectorNode) right, target);
            case I32_VECTOR:
                return visitBinaryI32VectorInstruction(type, (LLVMI32VectorNode) left, (LLVMI32VectorNode) right, target);
            case I64_VECTOR:
                return visitBinaryI64VectorInstruction(type, (LLVMI64VectorNode) left, (LLVMI64VectorNode) right, target);
            case FLOAT_VECTOR:
                return visitBinaryFloatVectorInstruction(type, (LLVMFloatVectorNode) left, (LLVMFloatVectorNode) right, target);
            case DOUBLE_VECTOR:
                return visitBinaryDoubleVectorInstruction(type, (LLVMDoubleVectorNode) left, (LLVMDoubleVectorNode) right, target);
            default:
                throw new AssertionError(llvmType);
        }
    }

    private static LLVMExpressionNode visitBinaryIVarInstruction(LLVMArithmeticInstructionType type, LLVMIVarBitNode left, LLVMIVarBitNode right) {
        switch (type) {
            case ADDITION:
                return LLVMIVarAddNodeGen.create(left, right);
            case SUBTRACTION:
                return LLVMIVarSubNodeGen.create(left, right);
            case MULTIPLICATION:
                return LLVMIVarMulNodeGen.create(left, right);
            case UNSIGNED_DIVISION:
                return LLVMIVarUDivNodeGen.create(left, right);
            case DIVISION:
                return LLVMIVarDivNodeGen.create(left, right);
            case UNSIGNED_REMAINDER:
                return LLVMIVarURemNodeGen.create(left, right);
            case REMAINDER:
                return LLVMIVarRemNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitBinaryI8VectorInstruction(LLVMArithmeticInstructionType type, LLVMI8VectorNode left, LLVMI8VectorNode right, LLVMAddressNode target) {
        switch (type) {
            case ADDITION:
                return LLVMI8VectorAddNodeGen.create(target, left, right);
            case SUBTRACTION:
                return LLVMI8VectorSubNodeGen.create(target, left, right);
            case MULTIPLICATION:
                return LLVMI8VectorMulNodeGen.create(target, left, right);
            case DIVISION:
                return LLVMI8VectorDivNodeGen.create(target, left, right);
            case REMAINDER:
                return LLVMI8VectorRemNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitBinaryI16VectorInstruction(LLVMArithmeticInstructionType type, LLVMI16VectorNode left, LLVMI16VectorNode right, LLVMAddressNode target) {
        switch (type) {
            case ADDITION:
                return LLVMI16VectorAddNodeGen.create(target, left, right);
            case SUBTRACTION:
                return LLVMI16VectorSubNodeGen.create(target, left, right);
            case MULTIPLICATION:
                return LLVMI16VectorMulNodeGen.create(target, left, right);
            case DIVISION:
                return LLVMI16VectorDivNodeGen.create(target, left, right);
            case REMAINDER:
                return LLVMI16VectorRemNodeGen.create(target, left, right);
            case UNSIGNED_DIVISION:
                return LLVMI16VectorUDivNodeGen.create(target, left, right);
            case UNSIGNED_REMAINDER:
                return LLVMI16VectorURemNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitBinaryDoubleVectorInstruction(LLVMArithmeticInstructionType type, LLVMDoubleVectorNode left, LLVMDoubleVectorNode right, LLVMAddressNode target) {
        switch (type) {
            case ADDITION:
                return LLVMDoubleVectorAddNodeGen.create(target, left, right);
            case SUBTRACTION:
                return LLVMDoubleVectorSubNodeGen.create(target, left, right);
            case MULTIPLICATION:
                return LLVMDoubleVectorMulNodeGen.create(target, left, right);
            case DIVISION:
                return LLVMDoubleVectorDivNodeGen.create(target, left, right);
            case REMAINDER:
                return LLVMDoubleVectorRemNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitBinaryFloatVectorInstruction(LLVMArithmeticInstructionType type, LLVMFloatVectorNode left, LLVMFloatVectorNode right, LLVMAddressNode target) {
        switch (type) {
            case ADDITION:
                return LLVMFloatVectorAddNodeGen.create(target, left, right);
            case SUBTRACTION:
                return LLVMFloatVectorSubNodeGen.create(target, left, right);
            case MULTIPLICATION:
                return LLVMFloatVectorMulNodeGen.create(target, left, right);
            case DIVISION:
                return LLVMFloatVectorDivNodeGen.create(target, left, right);
            case REMAINDER:
                return LLVMFloatVectorRemNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitBinaryI64VectorInstruction(LLVMArithmeticInstructionType type, LLVMI64VectorNode left, LLVMI64VectorNode right, LLVMAddressNode target) {
        switch (type) {
            case ADDITION:
                return LLVMI64VectorAddNodeGen.create(target, left, right);
            case SUBTRACTION:
                return LLVMI64VectorSubNodeGen.create(target, left, right);
            case MULTIPLICATION:
                return LLVMI64VectorMulNodeGen.create(target, left, right);
            case UNSIGNED_DIVISION:
                return LLVMI64VectorUDivNodeGen.create(target, left, right);
            case DIVISION:
                return LLVMI64VectorDivNodeGen.create(target, left, right);
            case UNSIGNED_REMAINDER:
                return LLVMI64VectorURemNodeGen.create(target, left, right);
            case REMAINDER:
                return LLVMI64VectorRemNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitBinaryI32VectorInstruction(LLVMArithmeticInstructionType type, LLVMI32VectorNode left, LLVMI32VectorNode right, LLVMAddressNode target) {
        switch (type) {
            case ADDITION:
                return LLVMI32VectorAddNodeGen.create(target, left, right);
            case SUBTRACTION:
                return LLVMI32VectorSubNodeGen.create(target, left, right);
            case MULTIPLICATION:
                return LLVMI32VectorMulNodeGen.create(target, left, right);
            case UNSIGNED_DIVISION:
                return LLVMI32VectorUDivNodeGen.create(target, left, right);
            case DIVISION:
                return LLVMI32VectorDivNodeGen.create(target, left, right);
            case UNSIGNED_REMAINDER:
                return LLVMI32VectorURemNodeGen.create(target, left, right);
            case REMAINDER:
                return LLVMI32VectorRemNodeGen.create(target, left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitBinaryI1Instruction(LLVMArithmeticInstructionType type, LLVMI1Node left, LLVMI1Node right) {
        switch (type) {
            case ADDITION:
                return LLVMI1AddNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMI8Node visitBinaryI8Instruction(LLVMArithmeticInstructionType type, LLVMI8Node left, LLVMI8Node right) {
        switch (type) {
            case ADDITION:
                return LLVMI8AddNodeGen.create(left, right);
            case SUBTRACTION:
                return LLVMI8SubNodeGen.create(left, right);
            case MULTIPLICATION:
                return LLVMI8MulNodeGen.create(left, right);
            case DIVISION:
                return LLVMI8DivNodeGen.create(left, right);
            case REMAINDER:
                return LLVMI8RemNodeGen.create(left, right);
            case UNSIGNED_REMAINDER:
                return LLVMI8URemNodeGen.create(left, right);
            case UNSIGNED_DIVISION:
                return LLVMI8UDivNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMI16Node visitBinaryI16Instruction(LLVMArithmeticInstructionType type, LLVMI16Node left, LLVMI16Node right) {
        switch (type) {
            case ADDITION:
                return LLVMI16AddNodeGen.create(left, right);
            case SUBTRACTION:
                return LLVMI16SubNodeGen.create(left, right);
            case MULTIPLICATION:
                return LLVMI16MulNodeGen.create(left, right);
            case DIVISION:
                return LLVMI16DivNodeGen.create(left, right);
            case REMAINDER:
                return LLVMI16RemNodeGen.create(left, right);
            case UNSIGNED_REMAINDER:
                return LLVMI16URemNodeGen.create(left, right);
            case UNSIGNED_DIVISION:
                return LLVMI16UDivNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMI32Node visitBinaryI32Instruction(LLVMArithmeticInstructionType type, LLVMI32Node leftNode, LLVMI32Node rightNode) {
        switch (type) {
            case ADDITION:
                return LLVMI32AddNodeGen.create(leftNode, rightNode);
            case SUBTRACTION:
                return LLVMI32SubNodeGen.create(leftNode, rightNode);
            case MULTIPLICATION:
                return LLVMI32MulNodeGen.create(leftNode, rightNode);
            case UNSIGNED_DIVISION:
                return LLVMI32UDivNodeGen.create(leftNode, rightNode);
            case DIVISION:
                return LLVMI32DivNodeGen.create(leftNode, rightNode);
            case UNSIGNED_REMAINDER:
                return LLVMI32URemNodeGen.create(leftNode, rightNode);
            case REMAINDER:
                return LLVMI32RemNodeGen.create(leftNode, rightNode);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMI64Node visitBinaryI64Instruction(LLVMArithmeticInstructionType type, LLVMI64Node leftNode, LLVMI64Node rightNode) {
        switch (type) {
            case ADDITION:
                return LLVMI64AddNodeGen.create(leftNode, rightNode);
            case SUBTRACTION:
                return LLVMI64SubNodeGen.create(leftNode, rightNode);
            case MULTIPLICATION:
                return LLVMI64MulNodeGen.create(leftNode, rightNode);
            case UNSIGNED_DIVISION:
                return LLVMI64UDivNodeGen.create(leftNode, rightNode);
            case DIVISION:
                return LLVMI64DivNodeGen.create(leftNode, rightNode);
            case UNSIGNED_REMAINDER:
                return LLVMI64URemNodeGen.create(leftNode, rightNode);
            case REMAINDER:
                return LLVMI64RemNodeGen.create(leftNode, rightNode);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMFloatNode visitBinaryFloatInstruction(LLVMArithmeticInstructionType type, LLVMFloatNode left, LLVMFloatNode right) {
        switch (type) {
            case ADDITION:
                return LLVMFloatAddNodeGen.create(left, right);
            case SUBTRACTION:
                return LLVMFloatSubNodeGen.create(left, right);
            case MULTIPLICATION:
                return LLVMFloatMulNodeGen.create(left, right);
            case DIVISION:
                return LLVMFloatDivNodeGen.create(left, right);
            case REMAINDER:
                return LLVMFloatRemNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMDoubleNode visitBinaryDoubleInstruction(LLVMArithmeticInstructionType type, LLVMDoubleNode left, LLVMDoubleNode right) {
        switch (type) {
            case ADDITION:
                return LLVMDoubleAddNodeGen.create(left, right);
            case SUBTRACTION:
                return LLVMDoubleSubNodeGen.create(left, right);
            case MULTIPLICATION:
                return LLVMDoubleMulNodeGen.create(left, right);
            case DIVISION:
                return LLVMDoubleDivNodeGen.create(left, right);
            case REMAINDER:
                return LLVMDoubleRemNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

    private static LLVMExpressionNode visitBinary80BitFloatInstruction(LLVMArithmeticInstructionType type, LLVM80BitFloatNode left, LLVM80BitFloatNode right) {
        switch (type) {
            case ADDITION:
                return LLVM80BitFloatAddNodeGen.create(left, right);
            case SUBTRACTION:
                return LLVM80BitFloatSubNodeGen.create(left, right);
            case MULTIPLICATION:
                return LLVM80BitFloatMulNodeGen.create(left, right);
            case DIVISION:
                return LLVM80BitFloatDivNodeGen.create(left, right);
            case REMAINDER:
                return LLVM80BitFloatRemNodeGen.create(left, right);
            default:
                throw new AssertionError(type);
        }
    }

}
