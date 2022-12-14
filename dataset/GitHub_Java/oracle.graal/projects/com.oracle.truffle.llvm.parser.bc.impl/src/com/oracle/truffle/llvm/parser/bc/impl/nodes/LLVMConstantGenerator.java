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

package com.oracle.truffle.llvm.parser.bc.impl.nodes;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAddressZeroNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstruction;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMLabelList;
import com.oracle.truffle.llvm.parser.bc.impl.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.factories.LLVMCastsFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMGetElementPtrFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLiteralFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import uk.ac.man.cs.llvm.ir.model.FunctionDeclaration;
import uk.ac.man.cs.llvm.ir.model.FunctionDefinition;
import uk.ac.man.cs.llvm.ir.model.GlobalValueSymbol;
import uk.ac.man.cs.llvm.ir.model.Symbol;
import uk.ac.man.cs.llvm.ir.model.ValueSymbol;
import uk.ac.man.cs.llvm.ir.model.constants.ArrayConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BinaryOperationConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BlockAddressConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CastConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CompareConstant;
import uk.ac.man.cs.llvm.ir.model.constants.FloatingPointConstant;
import uk.ac.man.cs.llvm.ir.model.constants.GetElementPointerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.IntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.NullConstant;
import uk.ac.man.cs.llvm.ir.model.constants.StringConstant;
import uk.ac.man.cs.llvm.ir.model.constants.StructureConstant;
import uk.ac.man.cs.llvm.ir.model.constants.UndefinedConstant;
import uk.ac.man.cs.llvm.ir.types.ArrayType;
import uk.ac.man.cs.llvm.ir.types.FloatingPointType;
import uk.ac.man.cs.llvm.ir.types.FunctionType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.StructureType;
import uk.ac.man.cs.llvm.ir.types.Type;
import uk.ac.man.cs.llvm.ir.types.VectorType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class LLVMConstantGenerator {

    private LLVMConstantGenerator() {
    }

    public static LLVMExpressionNode toConstantNode(Symbol value, int align, Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMContext context, FrameSlot stackSlot, LLVMLabelList labels,
                    LLVMBitcodeTypeHelper typeHelper) {
        if (value instanceof GlobalValueSymbol) {
            return variables.apply((GlobalValueSymbol) value);

        } else if (value instanceof FunctionDefinition || value instanceof FunctionDeclaration) {
            final FunctionType type = (FunctionType) value;
            final LLVMFunctionDescriptor.LLVMRuntimeType returnType = LLVMBitcodeTypeHelper.toRuntimeType(type.getReturnType());
            final LLVMFunctionDescriptor.LLVMRuntimeType[] argTypes = LLVMBitcodeTypeHelper.toRuntimeTypes(type.getArgumentTypes());

            final String name = ((ValueSymbol) value).getName();
            return LLVMFunctionLiteralNodeGen.create(context.getFunctionRegistry().createFunctionDescriptor(name, returnType, argTypes, type.isVarArg()));

        } else if (value instanceof StringConstant) {
            final StringConstant constant = (StringConstant) value;
            final String chars = constant.getString();

            final List<LLVMI8Node> values = new ArrayList<>(chars.length());
            for (int i = 0; i < chars.length(); i++) {
                values.add(new LLVMSimpleLiteralNode.LLVMI8LiteralNode((byte) chars.charAt(i)));
            }
            if (constant.isCString()) {
                values.add(new LLVMSimpleLiteralNode.LLVMI8LiteralNode((byte) 0));
            }

            final int size = typeHelper.getByteSize(new IntegerType(Byte.SIZE)) * values.size();
            final LLVMAllocInstruction.LLVMAllocaInstruction target = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(size, 1, context, stackSlot);
            return LLVMStoreNodeFactory.LLVMI8ArrayLiteralNodeGen.create(values.toArray(new LLVMI8Node[values.size()]), typeHelper.getByteSize(new IntegerType(Byte.SIZE)), target);

        } else if (value instanceof ArrayConstant) {
            return toArrayConstant((ArrayConstant) value, align, variables, context, stackSlot, labels, typeHelper);

        } else if (value instanceof StructureConstant) {
            return toStructureConstant((StructureConstant) value, align, variables, context, stackSlot, labels, typeHelper);

        } else if (value instanceof NullConstant) {
            return LLVMConstantGenerator.toConstantZeroNode(value.getType(), context, stackSlot, typeHelper);

        } else if (value instanceof UndefinedConstant) {
            return LLVMConstantGenerator.toConstantZeroNode(value.getType(), context, stackSlot, typeHelper);

        } else if (value instanceof BinaryOperationConstant) {
            final BinaryOperationConstant operation = (BinaryOperationConstant) value;
            final LLVMExpressionNode lhs = toConstantNode(operation.getLHS(), align, variables, context, stackSlot, labels, typeHelper);
            final LLVMExpressionNode rhs = toConstantNode(operation.getRHS(), align, variables, context, stackSlot, labels, typeHelper);
            final LLVMBaseType type = LLVMBitcodeTypeHelper.getLLVMBaseType(operation.getType());
            return LLVMNodeGenerator.generateBinaryOperatorNode(operation.getOperator(), type, lhs, rhs);

        } else if (value instanceof CastConstant) {
            final CastConstant cast = (CastConstant) value;
            final LLVMConversionType type = LLVMBitcodeTypeHelper.toConversionType(cast.getOperator());
            final LLVMExpressionNode fromNode = toConstantNode(cast.getValue(), align, variables, context, stackSlot, labels, typeHelper);
            final LLVMBaseType from = LLVMBitcodeTypeHelper.getLLVMBaseType(cast.getValue().getType());
            final LLVMBaseType to = LLVMBitcodeTypeHelper.getLLVMBaseType(cast.getType());
            return LLVMCastsFactory.cast(fromNode, to, from, type);

        } else if (value instanceof CompareConstant) {
            final CompareConstant compare = (CompareConstant) value;
            final LLVMExpressionNode lhs = toConstantNode(compare.getLHS(), align, variables, context, stackSlot, labels, typeHelper);
            final LLVMExpressionNode rhs = toConstantNode(compare.getRHS(), align, variables, context, stackSlot, labels, typeHelper);
            return LLVMNodeGenerator.toCompareNode(compare.getOperator(), compare.getLHS().getType(), lhs, rhs);

        } else if (value instanceof GetElementPointerConstant) {
            return toGetElementPointerConstant((GetElementPointerConstant) value, align, variables, context, stackSlot, labels, typeHelper);

        } else if (value instanceof IntegerConstant) {
            final IntegerConstant constant = (IntegerConstant) value;
            final int bits = ((IntegerType) (constant).getType()).getBitCount();
            switch (bits) {
                case 1:
                    return new LLVMSimpleLiteralNode.LLVMI1LiteralNode(constant.getValue() != 0);
                case Byte.SIZE:
                    return new LLVMSimpleLiteralNode.LLVMI8LiteralNode((byte) constant.getValue());
                case Short.SIZE:
                    return new LLVMSimpleLiteralNode.LLVMI16LiteralNode((short) constant.getValue());
                case Integer.SIZE:
                    return new LLVMSimpleLiteralNode.LLVMI32LiteralNode((int) constant.getValue());
                case Long.SIZE:
                    return new LLVMSimpleLiteralNode.LLVMI64LiteralNode(constant.getValue());
                default:
                    return new LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode(LLVMIVarBit.fromLong(bits, constant.getValue()));
            }

        } else if (value instanceof FloatingPointConstant) {
            final FloatingPointConstant constant = (FloatingPointConstant) value;
            switch (((FloatingPointType) constant.getType())) {
                case FLOAT:
                    return new LLVMSimpleLiteralNode.LLVMFloatLiteralNode(constant.toFloat());
                case DOUBLE:
                    return new LLVMSimpleLiteralNode.LLVMDoubleLiteralNode(constant.toDouble());
                default:
                    throw new UnsupportedOperationException("Unsupported Floating Point Type: " + constant.getType());
            }

        } else if (value instanceof BlockAddressConstant) {
            return toBlockAddressConstant((BlockAddressConstant) value, labels);

        } else {
            throw new UnsupportedOperationException("Unsupported Constant: " + value);
        }
    }

    private static LLVMExpressionNode toArrayConstant(ArrayConstant array, int align, Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMContext context, FrameSlot stackSlot,
                    LLVMLabelList labels, LLVMBitcodeTypeHelper typeHelper) {
        final Type elementType = array.getType().getElementType();
        final LLVMBaseType llvmElementType = LLVMBitcodeTypeHelper.getLLVMBaseType(elementType);
        final int stride = typeHelper.getByteSize(elementType);
        final LLVMAllocInstruction.LLVMAllocaInstruction allocation = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(typeHelper.getByteSize(array.getType()),
                        typeHelper.getAlignment(array.getType()), context, stackSlot);
        switch (llvmElementType) {
            case I8: {
                final LLVMI8Node[] elements = new LLVMI8Node[array.getElementCount()];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = (LLVMI8Node) toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
                }
                return LLVMStoreNodeFactory.LLVMI8ArrayLiteralNodeGen.create(elements, stride, allocation);
            }
            case I16: {
                final LLVMI16Node[] elements = new LLVMI16Node[array.getElementCount()];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = (LLVMI16Node) toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
                }
                return LLVMStoreNodeFactory.LLVMI16ArrayLiteralNodeGen.create(elements, stride, allocation);
            }
            case I32: {
                final LLVMI32Node[] elements = new LLVMI32Node[array.getElementCount()];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = (LLVMI32Node) toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
                }
                return LLVMStoreNodeFactory.LLVMI32ArrayLiteralNodeGen.create(elements, stride, allocation);
            }
            case I64: {
                final LLVMI64Node[] elements = new LLVMI64Node[array.getElementCount()];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = (LLVMI64Node) toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
                }
                return LLVMStoreNodeFactory.LLVMI64ArrayLiteralNodeGen.create(elements, stride, allocation);
            }
            case FLOAT: {
                final LLVMFloatNode[] elements = new LLVMFloatNode[array.getElementCount()];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = (LLVMFloatNode) toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
                }
                return LLVMStoreNodeFactory.LLVMFloatArrayLiteralNodeGen.create(elements, stride, allocation);
            }
            case DOUBLE: {
                final LLVMDoubleNode[] elements = new LLVMDoubleNode[array.getElementCount()];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = (LLVMDoubleNode) toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
                }
                return LLVMStoreNodeFactory.LLVMDoubleArrayLiteralNodeGen.create(elements, stride, allocation);
            }
            case ARRAY:
            case STRUCT: {
                final LLVMAddressNode[] elements = new LLVMAddressNode[array.getElementCount()];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = (LLVMAddressNode) toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
                }
                return LLVMStoreNodeFactory.LLVMAddressArrayCopyNodeGen.create(elements, stride, allocation);
            }
            case ADDRESS: {
                final LLVMAddressNode[] elements = new LLVMAddressNode[array.getElementCount()];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = (LLVMAddressNode) toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
                }
                return LLVMStoreNodeFactory.LLVMAddressArrayLiteralNodeGen.create(elements, stride, allocation);
            }
            case FUNCTION_ADDRESS: {
                final LLVMFunctionNode[] elements = new LLVMFunctionNode[array.getElementCount()];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = (LLVMFunctionNode) toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
                }
                return LLVMStoreNodeFactory.LLVMFunctionArrayLiteralNodeGen.create(elements, stride, allocation);
            }
            default:
                throw new AssertionError(llvmElementType);
        }
    }

    private static LLVMExpressionNode toBlockAddressConstant(BlockAddressConstant blockAddressConstant, LLVMLabelList labels) {
        final FunctionDefinition function = blockAddressConstant.getFunction();
        final Map<String, Integer> innerLabels = labels.labels(function.getName());
        for (Map.Entry<String, Integer> labelEntry : innerLabels.entrySet()) {
            if (labelEntry.getValue().equals(blockAddressConstant.getBlock())) {
                String blockName = labelEntry.getKey();
                for (int i = 0; i < function.getBlockCount(); i++) {
                    if (function.getBlock(i).getName().equals(blockName)) {
                        return new LLVMSimpleLiteralNode.LLVMAddressLiteralNode(LLVMAddress.fromLong(i));
                    }
                }
            }
        }

        throw new AssertionError("Could not find Block: " + blockAddressConstant.getBlock());
    }

    private static LLVMExpressionNode toGetElementPointerConstant(GetElementPointerConstant constant, int align, Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMContext context,
                    FrameSlot stackSlot, LLVMLabelList labels, LLVMBitcodeTypeHelper typeHelper) {
        LLVMAddressNode currentAddress = (LLVMAddressNode) toConstantNode(constant.getBasePointer(), align, variables, context, stackSlot, labels, typeHelper);
        Type currentType = constant.getBasePointer().getType();
        Type parentType = null;
        int currentOffset = 0;

        for (final Symbol index : constant.getIndices()) {
            final Integer indexVal = LLVMNodeGenerator.evaluateIntegerConstant(index);
            if (indexVal == null) {
                throw new IllegalStateException("Invalid index: " + index);
            }

            currentOffset += typeHelper.goIntoTypeGetLength(currentType, indexVal);
            parentType = currentType;
            currentType = LLVMBitcodeTypeHelper.goIntoType(currentType, indexVal);
        }

        if (currentType != null && !((parentType instanceof StructureType) && (((StructureType) parentType).isPacked()))) {
            currentOffset += typeHelper.getPadding(currentOffset, currentType);
        }

        if (currentOffset != 0) {
            currentAddress = LLVMGetElementPtrFactory.create(LLVMBaseType.I32, currentAddress, new LLVMSimpleLiteralNode.LLVMI32LiteralNode(1), currentOffset);
        }

        return currentAddress;
    }

    private static LLVMStructWriteNode createStructWriteNode(LLVMExpressionNode parsedConstant, LLVMBaseType baseType, int byteSize) {
        switch (baseType) {
            case I1:
                return new StructLiteralNode.LLVMI1StructWriteNode((LLVMI1Node) parsedConstant);
            case I8:
                return new StructLiteralNode.LLVMI8StructWriteNode((LLVMI8Node) parsedConstant);
            case I16:
                return new StructLiteralNode.LLVMI16StructWriteNode((LLVMI16Node) parsedConstant);
            case I32:
                return new StructLiteralNode.LLVMI32StructWriteNode((LLVMI32Node) parsedConstant);
            case I64:
                return new StructLiteralNode.LLVMI64StructWriteNode((LLVMI64Node) parsedConstant);
            case FLOAT:
                return new StructLiteralNode.LLVMFloatStructWriteNode((LLVMFloatNode) parsedConstant);
            case DOUBLE:
                return new StructLiteralNode.LLVMDoubleStructWriteNode((LLVMDoubleNode) parsedConstant);
            case X86_FP80:
                return new StructLiteralNode.LLVM80BitFloatStructWriteNode((LLVM80BitFloatNode) parsedConstant);
            case ARRAY:
            case STRUCT:
                if (byteSize == 0) {
                    return new StructLiteralNode.LLVMEmptyStructWriteNode();
                } else {
                    return new StructLiteralNode.LLVMCompoundStructWriteNode((LLVMAddressNode) parsedConstant, byteSize);
                }
            case ADDRESS:
                return new StructLiteralNode.LLVMAddressStructWriteNode((LLVMAddressNode) parsedConstant);
            case FUNCTION_ADDRESS:
                return new StructLiteralNode.LLVMFunctionStructWriteNode((LLVMFunctionNode) parsedConstant);
            default:
                throw new AssertionError("Invalid BaseType for StructWriteNode: " + baseType);
        }
    }

    private static LLVMExpressionNode toStructureConstant(StructureConstant constant, int align, Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMContext context, FrameSlot stackSlot,
                    LLVMLabelList labels, LLVMBitcodeTypeHelper typeHelper) {

        final int elementCount = constant.getElementCount();

        final boolean packed = constant.isPacked();
        final int[] offsets = new int[elementCount];
        final LLVMStructWriteNode[] nodes = new LLVMStructWriteNode[elementCount];

        final StructureType structureType = (StructureType) constant.getType();
        final int structSize = typeHelper.getByteSize(structureType);
        final int structAlignment = typeHelper.getAlignment(structureType);
        final LLVMAddressNode allocation = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(structSize, structAlignment, context, stackSlot);

        int currentOffset = 0;
        for (int i = 0; i < elementCount; i++) {
            final Type elementType = constant.getElementType(i);
            if (!packed) {
                currentOffset += typeHelper.getPadding(currentOffset, elementType);
            }
            offsets[i] = currentOffset;
            final int byteSize = typeHelper.getByteSize(elementType);
            final LLVMExpressionNode resolvedConstant = toConstantNode(constant.getElement(i), align, variables, context, stackSlot, labels, typeHelper);
            nodes[i] = createStructWriteNode(resolvedConstant, LLVMBitcodeTypeHelper.getLLVMBaseType(elementType), byteSize);
            currentOffset += byteSize;
        }

        return new StructLiteralNode(offsets, nodes, allocation);
    }

    private static LLVMExpressionNode toStructZeroNode(StructureType structureType, LLVMContext context, FrameSlot stackSlot, LLVMBitcodeTypeHelper typeHelper) {
        final int size = typeHelper.getByteSize(structureType);
        if (size == 0) {
            final LLVMAddress minusOneNode = LLVMAddress.fromLong(-1);
            return new LLVMSimpleLiteralNode.LLVMAddressLiteralNode(minusOneNode);
        } else {
            final int alignment = typeHelper.getAlignment(structureType);
            final LLVMAddressNode addressNode = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(size, alignment, context, stackSlot);
            return new LLVMAddressZeroNode(addressNode, size);
        }
    }

    private static LLVMExpressionNode toArrayZeroNode(ArrayType type, LLVMContext context, FrameSlot stack, LLVMBitcodeTypeHelper typeHelper) {
        // return
        // LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(typeHelper.getByteSize(type),
        // typeHelper.getAlignment(type), context, stack);
        final int size = typeHelper.getByteSize(type);
        if (size == 0) {
            throw new AssertionError("Zero-Size Arrays are not allowed per C/C++ standards!");
        } else {
            final int alignment = typeHelper.getAlignment(type);
            final LLVMAddressNode allocation = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(size, alignment, context, stack);
            return new LLVMAddressZeroNode(allocation, size);
        }
    }

    public static LLVMExpressionNode toConstantZeroNode(Type type, LLVMContext context, FrameSlot stack, LLVMBitcodeTypeHelper typeHelper) {
        if (type instanceof IntegerType) {
            final int vbr = ((IntegerType) type).getBitCount();
            switch (vbr) {
                case 1:
                    return new LLVMSimpleLiteralNode.LLVMI1LiteralNode(false);
                case Byte.SIZE:
                    return new LLVMSimpleLiteralNode.LLVMI8LiteralNode((byte) 0);
                case Short.SIZE:
                    return new LLVMSimpleLiteralNode.LLVMI16LiteralNode((short) 0);
                case Integer.SIZE:
                    return new LLVMSimpleLiteralNode.LLVMI32LiteralNode(0);
                case Long.SIZE:
                    return new LLVMSimpleLiteralNode.LLVMI64LiteralNode(0L);
                default:
                    return new LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode(LLVMIVarBit.fromLong(vbr, 0L));
            }

        } else if (type instanceof FloatingPointType) {
            FloatingPointType floatingPointType = (FloatingPointType) type;
            switch (floatingPointType) {
                case FLOAT:
                    return new LLVMSimpleLiteralNode.LLVMFloatLiteralNode(0.0f);
                case DOUBLE:
                    return new LLVMSimpleLiteralNode.LLVMDoubleLiteralNode(0.0);
                default:
                    throw new UnsupportedOperationException("Unsupported Floating Point Type: " + floatingPointType);
            }

        } else if (type instanceof PointerType) {
            if (((PointerType) type).getPointeeType() instanceof FunctionType) {
                final LLVMFunctionDescriptor functionDescriptor = context.getFunctionRegistry().createZeroFunctionDescriptor();
                return LLVMFunctionLiteralNodeGen.create(functionDescriptor);
            } else {
                return new LLVMSimpleLiteralNode.LLVMAddressLiteralNode(LLVMAddress.fromLong(0));
            }

        } else if (type instanceof ArrayType) {
            return toArrayZeroNode((ArrayType) type, context, stack, typeHelper);

        } else if (type instanceof VectorType) {
            final VectorType vectorType = (VectorType) type.getType();
            final LLVMAddressNode target = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(typeHelper.getByteSize(type.getType()), typeHelper.getAlignment(type.getType()), context,
                            stack);
            final LLVMExpressionNode[] zeroes = new LLVMExpressionNode[vectorType.getElementCount()];
            Arrays.fill(zeroes, toConstantZeroNode(vectorType.getElementType(), context, stack, typeHelper));
            return LLVMLiteralFactory.createVectorLiteralNode(Arrays.asList(zeroes), target, LLVMBitcodeTypeHelper.getLLVMBaseType(vectorType));

        } else if (type instanceof FunctionType) {
            final LLVMFunctionDescriptor functionDescriptor = context.getFunctionRegistry().createFunctionDescriptor("<zero function>", LLVMFunctionDescriptor.LLVMRuntimeType.ILLEGAL,
                            new LLVMFunctionDescriptor.LLVMRuntimeType[0], false);
            return LLVMFunctionLiteralNodeGen.create(functionDescriptor);

        } else if (type instanceof StructureType) {
            final StructureType structureType = (StructureType) type;
            return toStructZeroNode(structureType, context, stack, typeHelper);

        } else {
            throw new AssertionError("Unsupported Type for Zero Constant: " + type);
        }
    }
}
