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
package com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.function;

import java.util.List;

import com.oracle.truffle.llvm.parser.base.model.blocks.InstructionGenerator;
import com.oracle.truffle.llvm.parser.base.model.generators.FunctionGenerator;
import com.oracle.truffle.llvm.parser.base.model.types.AggregateType;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerConstantType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerType;
import com.oracle.truffle.llvm.parser.base.model.types.MetaType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;
import com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.ModuleVersion;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.blocks.Block;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.records.Records;
import com.oracle.truffle.llvm.parser.bc.impl.parser.ir.module.records.FunctionRecord;
import com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.ParserListener;
import com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.Types;
import com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.ValueSymbolTable;
import com.oracle.truffle.llvm.runtime.LLVMLogger;

public class Function implements ParserListener {

    private static final int INSERT_VALUE_MAX_ARGS = 3;

    private final ModuleVersion version;

    protected final FunctionGenerator generator;

    protected final Types types;

    protected final List<Type> symbols;

    protected final int mode;

    protected InstructionGenerator code;

    public Function(ModuleVersion version, Types types, List<Type> symbols, FunctionGenerator generator, int mode) {
        this.version = version;
        this.types = types;
        this.symbols = symbols;
        this.generator = generator;
        this.mode = mode;
    }

    @Override
    public ParserListener enter(Block block) {
        switch (block) {
            case CONSTANTS:
                return version.createConstants(types, symbols, generator);

            case VALUE_SYMTAB:
                return new ValueSymbolTable(generator);

            case METADATA:
                return version.createMetadata(types, symbols, generator); // TODO

            case METADATA_ATTACHMENT:
                return version.createMetadata(types, symbols, generator); // TODO

            default:
                LLVMLogger.info("Entering Unknown Block inside Function: " + block);
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void exit() {
        generator.exitFunction();
    }

    @Override
    public void record(long id, long[] args) {
        FunctionRecord record = FunctionRecord.decode(id);

        if (record == FunctionRecord.DECLAREBLOCKS) {
            generator.allocateBlocks((int) args[0]);
            return;
        }

        /*
         * FUNC_CODE_DEBUG_LOC as well as FUNC_CODE_DEBUG_LOC_AGAIN also occur after the RET
         * Instruction, where the InstructionGenerator would already been deleted. This has to be
         * improved in the future, but for now we simply parse those instructions before checking
         * for an existing InstructionGenerator. Otherwise we would cause an RuntimeException.
         */
        if (record == FunctionRecord.FUNC_CODE_DEBUG_LOC) {
            /*
             * TODO: implement intial debugging support
             *
             * http://llvm.org/releases/3.2/docs/SourceLevelDebugging.html#format_common_lifetime
             * http://llvm.org/releases/3.4/docs/SourceLevelDebugging.html#object-lifetimes-and-scoping
             *
             * @formatter:off
             *
             * metadata !{
             *  i32 4,          ;; line number
             *  i32 0,          ;; column number
             *  metadata !12,   ;; scope
             *  null            ;; original scope
             * }
             *
             * @formatter:on
             */
            return;
        }

        if (record == FunctionRecord.FUNC_CODE_DEBUG_LOC_AGAIN) {
            return;
        }

        if (code == null) {
            code = generator.generateBlock();
        }

        switch (record) {
            case BINOP:
                createBinaryOperation(args);
                break;

            case CAST:
                createCast(args);
                break;

            case GEP_OLD:
                createGetElementPointerOld(args, false);
                break;

            case EXTRACTELT:
                createExtractElement(args);
                break;

            case INSERTELT:
                createInsertElement(args);
                break;

            case SHUFFLEVEC:
                createShuffleVector(args);
                break;

            case RET:
                createReturn(args);
                break;

            case BR:
                createBranch(args);
                break;

            case SWITCH:
                createSwitch(args);
                break;

            case UNREACHABLE:
                createUnreachable(args);
                break;

            case PHI:
                createPhi(args);
                break;

            case ALLOCA:
                createAllocation(args);
                break;

            case LOAD:
                createLoad(args);
                break;

            case STORE_OLD:
                createStoreOld(args);
                break;

            case EXTRACTVAL:
                createExtractValue(args);
                break;

            case INSERTVAL:
                createInsertValue(args);
                break;

            case CMP2:
                createCompare2(args);
                break;

            case VSELECT:
                createSelect(args);
                break;

            case INBOUNDS_GEP_OLD:
                createGetElementPointerOld(args, true);
                break;

            case INDIRECTBR:
                createIndirectBranch(args);
                break;

            case CALL:
                createCall(args);
                break;

            case GEP:
                createGetElementPointer(args);
                break;

            case STORE:
                createStore(args);
                break;

            case LOADATOMIC:
                createAtomicLoad(args);
                break;

            case STOREATOMIC:
                createAtomicStore(args);
                break;

            default:
                throw new UnsupportedOperationException("Unsupported Record: " + record);
        }
    }

    protected void createAtomicLoad(long[] args) {
        int i = 0;
        final int source = getIndex(args[i++]);
        final Type type = types.get(args[i++]);
        final int align = getAlign(args[i++]);
        final boolean isVolatile = args[i++] != 0;
        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        code.createAtomicLoad(type, source, align, isVolatile, atomicOrdering, synchronizationScope);

        symbols.add(type);
    }

    protected void createAtomicStore(long[] args) {
        int i = 0;

        final int destination = getIndex(args[i++]);
        if (destination > symbols.size()) {
            i++;
        }

        final int source = getIndex(args[i++]);
        if (source > symbols.size()) {
            i++;
        }

        final int align = getAlign(args[i++]);
        final boolean isVolatile = args[i++] != 0;
        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        code.createAtomicStore(destination, source, align, isVolatile, atomicOrdering, synchronizationScope);
    }

    protected void createAllocation(long[] args) {
        int i = 0;
        PointerType type = new PointerType(types.get(args[i++]));
        i++; // Unused parameter
        int count = getIndexV0(args[i++]);
        int align = getAlign(args[i++]);

        code.createAllocation(type, count, align);

        symbols.add(type);
    }

    protected void createBinaryOperation(long[] args) {
        int i = 0;
        Type type;
        int lhs = getIndex(args[i++]);
        if (lhs < symbols.size()) {
            type = symbols.get(lhs).getType();
        } else {
            type = types.get(args[i++]);
        }
        int rhs = getIndex(args[i++]);
        int opcode = (int) args[i++];
        int flags = i < args.length ? (int) args[i] : 0;

        code.createBinaryOperation(type, opcode, flags, lhs, rhs);

        symbols.add(type);
    }

    protected void createBranch(long[] args) {
        if (args.length == 1) {
            code.createBranch((int) args[0]);
        } else {
            code.createBranch(getIndex(args[2]), (int) args[0], (int) args[1]);
        }

        code.exitBlock();
        code = null;
    }

    protected void createCall(long[] args) {
        int i = 0;
        final long linkage = args[i++];
        final long visibility = args[i++];
        final FunctionType function = (FunctionType) types.get(args[i++]);
        final int target = getIndex(args[i++]);

        final int[] arguments = new int[args.length - i];
        for (int j = 0; i < args.length; i++, j++) {
            arguments[j] = getIndex(args[i]);
        }

        final Type returnType = function.getReturnType();
        code.createCall(returnType, target, arguments, visibility, linkage);

        if (returnType != MetaType.VOID) {
            symbols.add(returnType);
        }
    }

    protected void createCast(long[] args) {
        int i = 0;
        int value = getIndex(args[i++]);
        if (value >= symbols.size()) {
            i++;
        }
        Type type = types.get(args[i++]);
        int opcode = (int) args[i++];

        code.createCast(type, opcode, value);

        symbols.add(type);
    }

    protected void createCompare2(long[] args) {
        int i = 0;
        Type operandType;
        int lhs = getIndex(args[i++]);
        if (lhs < symbols.size()) {
            operandType = symbols.get(lhs).getType();
        } else {
            operandType = types.get(args[i++]);
        }
        int rhs = getIndex(args[i++]);
        int opcode = (int) args[i++];

        Type type = operandType instanceof VectorType
                        ? new VectorType(IntegerType.BOOLEAN, ((VectorType) operandType).getLength())
                        : IntegerType.BOOLEAN;

        code.createCompare(type, opcode, lhs, rhs);

        symbols.add(type);
    }

    protected void createExtractElement(long[] args) {
        int vector = getIndex(args[0]);
        int index = getIndex(args[1]);

        Type type = ((VectorType) symbols.get(vector).getType()).getElementType();

        code.createExtractElement(type, vector, index);

        symbols.add(type);
    }

    protected void createExtractValue(long[] args) {
        int aggregate = getIndex(args[0]);
        int index = (int) args[1];

        if (args.length != 2) {
            // This is supported in neither parser.
            throw new UnsupportedOperationException("Multiple indices are not yet supported!");
        }

        Type type = ((AggregateType) symbols.get(aggregate).getType()).getElementType(index);

        code.createExtractValue(type, aggregate, index);

        symbols.add(type);
    }

    protected void createGetElementPointer(long[] args) {
        int i = 0;
        boolean isInbounds = args[i++] != 0;
        i++; // Unused parameter
        int pointer = getIndex(args[i++]);
        int[] indices = getIndices(args, i++);

        Type type = new PointerType(getElementPointerType(symbols.get(pointer).getType(), indices));

        code.createGetElementPointer(
                        type,
                        pointer,
                        indices,
                        isInbounds);

        symbols.add(type);
    }

    protected void createGetElementPointerOld(long[] args, boolean isInbounds) {
        int i = 0;
        int pointer = getIndex(args[i++]);
        Type base;
        if (pointer < symbols.size()) {
            base = symbols.get(pointer).getType();
        } else {
            base = types.get(args[i++]);
        }
        int[] indices = getIndices(args, i);

        Type type = new PointerType(getElementPointerType(base, indices));

        code.createGetElementPointer(
                        type,
                        pointer,
                        indices,
                        isInbounds);

        symbols.add(type);
    }

    protected void createIndirectBranch(long[] args) {
        int address = getIndex(args[1]);
        int[] successors = new int[args.length - 2];
        for (int i = 0; i < successors.length; i++) {
            successors[i] = (int) args[i + 2];
        }

        code.createIndirectBranch(address, successors);

        code.exitBlock();
        code = null;
    }

    protected void createInsertElement(long[] args) {
        int vector = getIndex(args[0]);
        int index = getIndex(args[2]);
        int value = getIndex(args[1]);

        Type symbol = symbols.get(vector);

        code.createInsertElement(symbol.getType(), vector, index, value);

        symbols.add(symbol);
    }

    protected void createInsertValue(long[] args) {
        int aggregate = getIndex(args[0]);
        int index = (int) args[2];
        int value = getIndex(args[1]);

        if (args.length != INSERT_VALUE_MAX_ARGS) {
            // This is supported in neither parser.
            throw new UnsupportedOperationException("Multiple indices are not yet supported!");
        }

        Type symbol = symbols.get(aggregate);

        code.createInsertValue(symbol.getType(), aggregate, index, value);

        symbols.add(symbol);
    }

    protected void createLoad(long[] args) {
        int i = 0;
        int source = getIndex(args[i++]);
        Type type = types.get(args[i++]);
        int align = getAlign(args[i++]);
        boolean isVolatile = args[i++] != 0;

        code.createLoad(type, source, align, isVolatile);

        symbols.add(type);
    }

    protected void createPhi(long[] args) {
        Type type = types.get(args[0]);
        int count = (args.length) - 1 >> 1;
        int[] values = new int[count];
        int[] blocks = new int[count];
        for (int i = 0, j = 1; i < count; i++) {
            values[i] = getIndex(Records.toSignedValue(args[j++]));
            blocks[i] = (int) args[j++];
        }

        code.createPhi(type, values, blocks);

        symbols.add(type);
    }

    protected void createReturn(long[] args) {
        if (args.length == 0 || args[0] == 0) {
            code.createReturn();
        } else {
            code.createReturn(getIndex(args[0]));
        }

        code.exitBlock();
        code = null;
    }

    protected void createSelect(long[] args) {
        int i = 0;
        Type type;
        int trueValue = getIndex(args[i++]);
        if (trueValue < symbols.size()) {
            type = symbols.get(trueValue).getType();
        } else {
            type = types.get(args[i++]);
        }
        int falseValue = getIndex(args[i++]);
        int condition = getIndex(args[i++]);

        code.createSelect(type, condition, trueValue, falseValue);

        symbols.add(type);
    }

    protected void createShuffleVector(long[] args) {
        int vector1 = getIndex(args[0]);
        int vector2 = getIndex(args[1]);
        int mask = getIndex(args[2]);

        Type subtype = ((VectorType) symbols.get(vector1).getType()).getElementType();
        int length = ((VectorType) symbols.get(mask).getType()).getLength();
        Type type = new VectorType(subtype, length);

        code.createShuffleVector(type, vector1, vector2, mask);

        symbols.add(type);
    }

    protected void createStore(long[] args) {
        int i = 0;

        int destination = getIndex(args[i++]);
        if (destination > symbols.size()) {
            i++;
        }

        int source = getIndex(args[i++]);
        if (source > symbols.size()) {
            i++;
        }

        int align = getAlign(args[i++]);
        boolean isVolatile = args[i++] != 0;

        code.createStore(destination, source, align, isVolatile);
    }

    protected void createStoreOld(long[] args) {
        int i = 0;

        int destination = getIndex(args[i++]);
        if (destination > symbols.size()) {
            i++;
        }

        int source = getIndex(args[i++]);
        int align = getAlign(args[i++]);
        boolean isVolatile = args[i++] != 0;

        code.createStore(destination, source, align, isVolatile);
    }

    protected void createSwitch(long[] args) {
        int i = 1;
        int condition = getIndex(args[i++]);
        int defaultBlock = (int) args[i++];
        int count = (args.length - i) >> 1;
        int[] caseValues = new int[count];
        int[] caseBlocks = new int[count];
        for (int j = 0; j < count; j++) {
            caseValues[j] = getIndexV0(args[i++]);
            caseBlocks[j] = (int) args[i++];
        }

        code.createSwitch(condition, defaultBlock, caseValues, caseBlocks);

        code.exitBlock();
        code = null;
    }

    protected void createUnreachable(@SuppressWarnings("unused") long[] args) {
        code.createUnreachable();

        code.exitBlock();
        code = null;
    }

    protected int getAlign(long argument) {
        return (int) argument & (Long.SIZE - 1);
    }

    protected Type getElementPointerType(Type type, int[] indices) {
        Type elementType = type;
        for (int i = 0; i < indices.length; i++) {
            if (elementType instanceof PointerType) {
                elementType = ((PointerType) elementType).getPointeeType();
            } else if (elementType instanceof ArrayType) {
                elementType = ((ArrayType) elementType).getElementType();
            } else {
                StructureType structure = (StructureType) elementType;
                Type idx = symbols.get(indices[i]);
                if (!(idx instanceof IntegerConstantType)) {
                    throw new IllegalStateException("Cannot infer structure element from " + idx);
                }
                elementType = structure.getElementType((int) ((IntegerConstantType) idx).getValue());
            }
        }
        return elementType;
    }

    protected int getIndex(long index) {
        if (mode == 0) {
            return getIndexV0(index);
        } else {
            return getIndexV1(index);
        }
    }

    protected int[] getIndices(long[] arguments) {
        return getIndices(arguments, 0, arguments.length);
    }

    protected int[] getIndices(long[] arguments, int from) {
        return getIndices(arguments, from, arguments.length);
    }

    protected int[] getIndices(long[] arguments, int from, int to) {
        int[] indices = new int[to - from];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = getIndex(arguments[from + i]);
        }
        return indices;
    }

    protected int getIndexV0(long index) {
        return (int) index;
    }

    protected int getIndexV1(long index) {
        return symbols.size() - (int) index;
    }
}
