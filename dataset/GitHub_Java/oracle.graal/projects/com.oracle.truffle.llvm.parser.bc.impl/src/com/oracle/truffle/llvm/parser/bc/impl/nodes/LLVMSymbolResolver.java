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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.base.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.ValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.MetadataConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.StringConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.VectorConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.floatingpoint.FloatingPointConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.FloatingPointType;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;
import com.oracle.truffle.llvm.parser.base.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMLabelList;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;

public final class LLVMSymbolResolver {

    private final LLVMParserRuntime runtime;

    private final Function<GlobalValueSymbol, LLVMExpressionNode> variables;

    private final LLVMLabelList labels;

    public LLVMSymbolResolver(Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMLabelList labels, LLVMParserRuntime runtime) {
        this.variables = variables;
        this.labels = labels;
        this.runtime = runtime;
    }

    public static Integer evaluateIntegerConstant(Symbol constant) {
        if (constant instanceof IntegerConstant) {
            if (constant.getType() == IntegerType.LONG) {
                LLVMLogger.info("GEP index overflow (still parse as int");
            }
            return (int) ((IntegerConstant) constant).getValue();

        } else if (constant instanceof BigIntegerConstant) {
            LLVMLogger.info("GEP index overflow (still parse as int");
            return ((BigIntegerConstant) constant).getValue().intValueExact();

        } else if (constant instanceof NullConstant) {
            return 0;

        } else {
            return null;
        }
    }

    private LLVMExpressionNode toMetaData(MetadataConstant constant) {
        // TODO: point to Metadata
        return runtime.getNodeFactoryFacade().createLiteral(constant.getValue(), LLVMBaseType.I64);
    }

    public LLVMExpressionNode resolveElementPointer(Symbol base, List<Symbol> indices) {
        LLVMExpressionNode currentAddress = resolve(base);
        Type currentType = base.getType();

        for (final Symbol symbol : indices) {
            final Type type = symbol.getType();

            final Integer constantIndex = LLVMSymbolResolver.evaluateIntegerConstant(symbol);
            if (constantIndex == null) {
                final int indexedTypeLength = runtime.getIndexOffset(1, currentType);
                currentType = currentType.getIndexType(1);
                final LLVMExpressionNode valueref = resolve(symbol);
                currentAddress = runtime.getNodeFactoryFacade().createGetElementPtr(type.getLLVMBaseType(), currentAddress, valueref, indexedTypeLength);

            } else {
                final int indexedTypeLength = runtime.getIndexOffset(constantIndex, currentType);
                currentType = currentType.getIndexType(constantIndex);
                if (indexedTypeLength != 0) {
                    final LLVMExpressionNode constantNode;
                    switch (type.getLLVMBaseType()) {
                        case I32:
                            constantNode = runtime.getNodeFactoryFacade().createLiteral(1, LLVMBaseType.I32);
                            break;
                        case I64:
                            constantNode = runtime.getNodeFactoryFacade().createLiteral(1L, LLVMBaseType.I64);
                            break;
                        default:
                            throw new AssertionError();
                    }
                    currentAddress = runtime.getNodeFactoryFacade().createGetElementPtr(type.getLLVMBaseType(), currentAddress, constantNode, indexedTypeLength);
                }
            }
        }

        return currentAddress;
    }

    private LLVMExpressionNode toInteger(IntegerConstant constant) {
        final Type type = constant.getType();
        final LLVMBaseType baseType = type.getLLVMBaseType();
        final String stringValue = constant.toString();
        return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(stringValue, baseType, type);
    }

    private LLVMExpressionNode toBigInteger(BigIntegerConstant constant) {
        final Type type = constant.getType();
        final String stringValue = constant.toString();
        return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(stringValue, type.getLLVMBaseType(), type);
    }

    private LLVMExpressionNode toFloat(FloatingPointConstant constant) {
        final Type type = constant.getType();
        final LLVMBaseType baseType = type.getLLVMBaseType();
        final String stringValue = constant.getStringValue();
        return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(stringValue, baseType, type);
    }

    private LLVMExpressionNode toStr(StringConstant constant) {
        final String chars = constant.getString();

        final NodeFactoryFacade factoryFacade = runtime.getNodeFactoryFacade();
        final List<LLVMExpressionNode> values = new ArrayList<>(chars.length());
        for (int i = 0; i < chars.length(); i++) {
            values.add(factoryFacade.createLiteral((byte) chars.charAt(i), LLVMBaseType.I8));
        }
        if (constant.isCString()) {
            values.add(factoryFacade.createLiteral((byte) 0, LLVMBaseType.I8));
        }

        return factoryFacade.createArrayLiteral(values, constant.getType());
    }

    private LLVMExpressionNode toStruct(StructureConstant constant) {
        final int elementCount = constant.getElementCount();
        final Type[] types = new Type[elementCount];
        final LLVMExpressionNode[] constants = new LLVMExpressionNode[elementCount];
        for (int i = 0; i < elementCount; i++) {
            types[i] = constant.getElementType(i);
            constants[i] = resolve(constant.getElement(i));
        }
        return runtime.getNodeFactoryFacade().createStructureConstantNode(constant.getType(), constant.isPacked(), types, constants);
    }

    private LLVMExpressionNode toArray(ArrayConstant array) {

        final List<LLVMExpressionNode> values = new ArrayList<>(array.getElementCount());
        for (int i = 0; i < array.getElementCount(); i++) {
            values.add(resolve(array.getElement(i)));
        }
        final Type arrayType = array.getType();
        return runtime.getNodeFactoryFacade().createArrayLiteral(values, arrayType);
    }

    private LLVMExpressionNode toVector(VectorConstant constant) {
        final List<LLVMExpressionNode> values = new ArrayList<>();
        for (int i = 0; i < constant.getLength(); i++) {
            values.add(resolve(constant.getElement(i)));
        }

        final LLVMExpressionNode target = runtime.allocateVectorResult(constant.getType());
        return runtime.getNodeFactoryFacade().createVectorLiteralNode(values, target, constant.getType().getLLVMBaseType());
    }

    private LLVMExpressionNode toFunction(FunctionType function) {
        final LLVMFunctionDescriptor.LLVMRuntimeType returnType = function.getReturnType().getRuntimeType();
        final boolean hasVarArgs = function.isVarArg();

        final LLVMFunctionDescriptor.LLVMRuntimeType[] paramTypes = new LLVMFunctionDescriptor.LLVMRuntimeType[function.getArgumentTypes().length];
        for (int i = 0; i < paramTypes.length; i++) {
            paramTypes[i] = function.getArgumentTypes()[i].getRuntimeType();
        }

        final LLVMFunction llvmFunction = runtime.getNodeFactoryFacade().createAndRegisterFunctionDescriptor(function.getName(), returnType, hasVarArgs, paramTypes);
        return runtime.getNodeFactoryFacade().createLiteral(llvmFunction, LLVMBaseType.FUNCTION_ADDRESS);
    }

    private LLVMExpressionNode toBinaryOperation(BinaryOperationConstant operation) {
        final LLVMExpressionNode lhs = resolve(operation.getLHS());
        final LLVMExpressionNode rhs = resolve(operation.getRHS());
        final LLVMBaseType baseType = operation.getType().getLLVMBaseType();

        final LLVMExpressionNode target;
        switch (baseType) {
            case I1_VECTOR:
            case I8_VECTOR:
            case I16_VECTOR:
            case I32_VECTOR:
            case I64_VECTOR:
            case I128_VECTOR:
            case FLOAT_VECTOR:
            case DOUBLE_VECTOR:
            case ADDRESS_VECTOR:
                target = runtime.allocateVectorResult(operation.getType());
                break;
            default:
                target = null;
                break;
        }

        final LLVMArithmeticInstructionType arithmeticInstructionType = LLVMBitcodeTypeHelper.toArithmeticInstructionType(operation.getOperator());
        if (arithmeticInstructionType != null) {
            return runtime.getNodeFactoryFacade().createArithmeticOperation(lhs, rhs, arithmeticInstructionType, baseType, target);
        }

        final LLVMLogicalInstructionType logicalInstructionType = LLVMBitcodeTypeHelper.toLogicalInstructionType(operation.getOperator());
        if (logicalInstructionType != null) {
            return runtime.getNodeFactoryFacade().createLogicalOperation(lhs, rhs, logicalInstructionType, baseType, target);
        }

        throw new UnsupportedOperationException("Unsupported Binary Operator: " + operation.getOperator());
    }

    private LLVMExpressionNode toBlockAddress(BlockAddressConstant constant) {
        final int val = labels.labels(constant.getFunction().getName()).get(constant.getInstructionBlock().getName());
        return runtime.getNodeFactoryFacade().createLiteral(LLVMAddress.fromLong(val), LLVMBaseType.ADDRESS);
    }

    private LLVMExpressionNode toElementPointer(GetElementPointerConstant constant) {
        LLVMExpressionNode currentAddress = resolve(constant.getBasePointer());
        Type currentType = constant.getBasePointer().getType();
        Type parentType = null;
        int currentOffset = 0;

        for (final Symbol index : constant.getIndices()) {
            final Integer indexVal = LLVMSymbolResolver.evaluateIntegerConstant(index);
            if (indexVal == null) {
                throw new IllegalStateException("Invalid index: " + index);
            }

            currentOffset += runtime.getIndexOffset(indexVal, currentType);
            parentType = currentType;
            currentType = currentType.getIndexType(indexVal);
        }

        if (currentType != null && !((parentType instanceof StructureType) && (((StructureType) parentType).isPacked()))) {
            currentOffset += runtime.getBytePadding(currentOffset, currentType);
        }

        if (currentOffset != 0) {
            final LLVMExpressionNode oneValueNode = runtime.getNodeFactoryFacade().createLiteral(1, LLVMBaseType.I32);
            currentAddress = runtime.getNodeFactoryFacade().createGetElementPtr(LLVMBaseType.I32, currentAddress, oneValueNode, currentOffset);
        }

        return currentAddress;
    }

    private LLVMExpressionNode toComparison(CompareConstant compare) {
        final LLVMExpressionNode lhs = resolve(compare.getLHS());
        final LLVMExpressionNode rhs = resolve(compare.getRHS());
        return runtime.getNodeFactoryFacade().createComparison(compare.getOperator(), compare.getLHS().getType(), lhs, rhs);
    }

    private LLVMExpressionNode toCast(CastConstant constant) {
        final LLVMConversionType type = LLVMBitcodeTypeHelper.toConversionType(constant.getOperator());
        final LLVMExpressionNode fromNode = resolve(constant.getValue());
        return runtime.getNodeFactoryFacade().createCast(fromNode, constant.getType(), constant.getValue().getType(), type);
    }

    private LLVMExpressionNode toNullValue(Type type) {
        if (type instanceof IntegerType) {
            if (type.getBits() == 1) {
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray("false", LLVMBaseType.I1, type);
            } else {
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray("0", type.getLLVMBaseType(), type);
            }

        } else if (type instanceof FloatingPointType) {
            final FloatingPointType floatingPointType = (FloatingPointType) type;
            if (floatingPointType == FloatingPointType.X86_FP80) {
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray("0xK00000000000000000000", LLVMBaseType.X86_FP80, type);
            } else {
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray("0.0", floatingPointType.getLLVMBaseType(), floatingPointType);
            }

        } else if (type instanceof PointerType || type instanceof FunctionType) {
            return runtime.getNodeFactoryFacade().createSimpleConstantNoArray("null", type.getLLVMBaseType(), type);

        } else if (type instanceof ArrayType) {
            final int size = runtime.getByteSize(type);
            if (size == 0) {
                return null;
            } else {
                final LLVMExpressionNode target = runtime.allocateFunctionLifetime(type, runtime.getByteSize(type), runtime.getByteAlignment(type));
                return runtime.getNodeFactoryFacade().createZeroNode(target, size);
            }

        } else if (type instanceof VectorType) {
            final VectorType vectorType = (VectorType) type.getType();
            final int nrElements = vectorType.getLength();
            final LLVMExpressionNode target = runtime.allocateVectorResult(vectorType);
            final LLVMBaseType baseType = vectorType.getLLVMBaseType();
            return runtime.getNodeFactoryFacade().createZeroVectorInitializer(nrElements, target, baseType);

        } else if (type instanceof StructureType) {
            final StructureType structureType = (StructureType) type;
            final int size = runtime.getByteSize(structureType);
            if (size == 0) {
                final LLVMAddress minusOneNode = LLVMAddress.fromLong(-1);
                return runtime.getNodeFactoryFacade().createLiteral(minusOneNode, LLVMBaseType.ADDRESS);
            } else {
                final int alignment = runtime.getByteAlignment(structureType);
                final LLVMExpressionNode addressnode = runtime.allocateFunctionLifetime(structureType, size, alignment);
                return runtime.getNodeFactoryFacade().createZeroNode(addressnode, size);
            }

        } else {
            throw new AssertionError("Unsupported Type for Zero Constant: " + type);
        }
    }

    public LLVMExpressionNode resolve(Symbol symbol) {
        if (symbol instanceof ValueInstruction || symbol instanceof FunctionParameter) {
            final FrameSlot slot = runtime.getMethodFrameDescriptor().findFrameSlot(((ValueSymbol) symbol).getName());
            return runtime.getNodeFactoryFacade().createFrameRead(symbol.getType().getLLVMBaseType(), slot);

        } else if (symbol instanceof GlobalValueSymbol) {
            return variables.apply((GlobalValueSymbol) symbol);

        } else if (symbol instanceof NullConstant || symbol instanceof UndefinedConstant) {
            return toNullValue(symbol.getType());

        } else if (symbol instanceof IntegerConstant) {
            return toInteger((IntegerConstant) symbol);

        } else if (symbol instanceof BigIntegerConstant) {
            return toBigInteger((BigIntegerConstant) symbol);

        } else if (symbol instanceof FloatingPointConstant) {
            return toFloat((FloatingPointConstant) symbol);

        } else if (symbol instanceof StringConstant) {
            return toStr((StringConstant) symbol);

        } else if (symbol instanceof StructureConstant) {
            return toStruct((StructureConstant) symbol);

        } else if (symbol instanceof ArrayConstant) {
            return toArray((ArrayConstant) symbol);

        } else if (symbol instanceof VectorConstant) {
            return toVector((VectorConstant) symbol);

        } else if (symbol instanceof FunctionType) {
            return toFunction((FunctionType) symbol);

        } else if (symbol instanceof BinaryOperationConstant) {
            return toBinaryOperation((BinaryOperationConstant) symbol);

        } else if (symbol instanceof GetElementPointerConstant) {
            return toElementPointer((GetElementPointerConstant) symbol);

        } else if (symbol instanceof BlockAddressConstant) {
            return toBlockAddress((BlockAddressConstant) symbol);

        } else if (symbol instanceof CompareConstant) {
            return toComparison((CompareConstant) symbol);

        } else if (symbol instanceof CastConstant) {
            return toCast((CastConstant) symbol);

        } else if (symbol instanceof MetadataConstant) {
            return toMetaData((MetadataConstant) symbol);

        } else {
            throw new AssertionError("Cannot resolve symbol: " + symbol);
        }
    }
}
