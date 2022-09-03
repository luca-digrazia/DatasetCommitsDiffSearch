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
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public abstract class ForeignToLLVM extends LLVMNode {

    public abstract Object executeWithTarget(VirtualFrame frame, Object value);

    @Child protected Node isPointer = Message.IS_POINTER.createNode();
    @Child protected Node asPointer = Message.AS_POINTER.createNode();
    @Child protected Node isBoxed = Message.IS_BOXED.createNode();
    @Child protected Node unbox = Message.UNBOX.createNode();

    public Object fromForeign(TruffleObject value) {
        try {
            if (ForeignAccess.sendIsPointer(isPointer, value)) {
                return ForeignAccess.sendAsPointer(asPointer, value);
            } else if (ForeignAccess.sendIsBoxed(isBoxed, value)) {
                return ForeignAccess.sendUnbox(unbox, value);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(new Object[]{value});
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }

    protected static boolean notLLVM(TruffleObject value) {
        return LLVMExpressionNode.notLLVM(value);
    }

    protected boolean checkIsPointer(TruffleObject object) {
        return ForeignAccess.sendIsPointer(isPointer, object);
    }

    protected char getSingleStringCharacter(String value) {
        if (value.length() == 1) {
            return value.charAt(0);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }

    public enum ForeignToLLVMType {
        I1,
        I8,
        I16,
        I32,
        I64,
        FLOAT,
        DOUBLE,
        POINTER,
        VECTOR,
        ARRAY,
        STRUCT,
        ANY,
        VOID,
        VARBIT;

        public static ForeignToLLVMType getIntegerType(int bitWidth) {
            switch (bitWidth) {
                case 8:
                    return ForeignToLLVMType.I8;
                case 16:
                    return ForeignToLLVMType.I16;
                case 32:
                    return ForeignToLLVMType.I32;
                case 64:
                    return ForeignToLLVMType.I64;
                default:
                    throw new IllegalStateException("There is no integer type with " + bitWidth + " bits defined");
            }
        }
    }

    public static ForeignToLLVMType convert(Type type) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return ForeignToLLVMType.I1;
                case I8:
                    return ForeignToLLVMType.I8;
                case I16:
                    return ForeignToLLVMType.I16;
                case I32:
                    return ForeignToLLVMType.I32;
                case I64:
                    return ForeignToLLVMType.I64;
                case FLOAT:
                    return ForeignToLLVMType.FLOAT;
                case DOUBLE:
                    return ForeignToLLVMType.DOUBLE;
                default:
                    throw UnsupportedTypeException.raise(new Object[]{type});
            }
        } else if (type instanceof PointerType) {
            return ForeignToLLVMType.POINTER;
        } else if (type instanceof VoidType) {
            return ForeignToLLVMType.VOID;
        } else if (type instanceof VectorType) {
            return ForeignToLLVMType.VECTOR;
        } else if (type instanceof ArrayType) {
            return ForeignToLLVMType.ARRAY;
        } else if (type instanceof StructureType) {
            return ForeignToLLVMType.STRUCT;
        } else {
            throw UnsupportedTypeException.raise(new Object[]{type});
        }
    }

    public static SlowPathForeignToLLVM createSlowPathNode() {
        return new SlowPathForeignToLLVM();
    }

    public static ForeignToLLVM create(Type type) {
        return create(convert(type));
    }

    public static ForeignToLLVM create(ForeignToLLVMType type) {
        switch (type) {
            case VOID:
                return ToVoidLLVMNodeGen.create();
            case ANY:
                return ToAnyLLVMNodeGen.create();
            case I1:
                return ToI1NodeGen.create();
            case I8:
                return ToI8NodeGen.create();
            case I16:
                return ToI16NodeGen.create();
            case I32:
                return ToI32NodeGen.create();
            case I64:
                return ToI64NodeGen.create();
            case FLOAT:
                return ToFloatNodeGen.create();
            case DOUBLE:
                return ToDoubleNodeGen.create();
            case POINTER:
                return ToPointerNodeGen.create();
            default:
                throw new IllegalStateException(type.toString());

        }
    }

    public static Object defaultValue(ForeignToLLVMType type) {
        switch (type) {
            case I1:
                return false;
            case I8:
                return (byte) 0;
            case I16:
                return (short) 0;
            case I32:
                return 0;
            case I64:
                return 0L;
            case POINTER:
                return LLVMAddress.fromLong(0);
            case FLOAT:
                return 0f;
            case DOUBLE:
                return 0d;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
        }
    }

    public static final class SlowPathForeignToLLVM extends ForeignToLLVM {
        @TruffleBoundary
        public Object convert(Type type, LLVMMemory memory, LLVMContext context, Object value) {
            return convert(memory, ForeignToLLVM.convert(type), context, value);
        }

        @TruffleBoundary
        public Object convert(LLVMMemory memory, ForeignToLLVMType type, LLVMContext context, Object value) {
            switch (type) {
                case ANY:
                    return ToAnyLLVM.slowPathPrimitiveConvert(this, value);
                case DOUBLE:
                    return ToDouble.slowPathPrimitiveConvert(memory, this, context, value);
                case FLOAT:
                    return ToFloat.slowPathPrimitiveConvert(memory, this, context, value);
                case I1:
                    return ToI1.slowPathPrimitiveConvert(memory, this, context, value);
                case I16:
                    return ToI16.slowPathPrimitiveConvert(memory, this, context, value);
                case I32:
                    return ToI32.slowPathPrimitiveConvert(memory, this, context, value);
                case I64:
                    return ToI64.slowPathPrimitiveConvert(memory, this, context, value);
                case I8:
                    return ToI8.slowPathPrimitiveConvert(memory, this, context, value);
                case POINTER:
                    return ToPointer.slowPathPrimitiveConvert(this, value);
                default:
                    throw new IllegalStateException(type.toString());

            }
        }

        @Override
        public Object executeWithTarget(VirtualFrame frame, Object value) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Use convert method.");
        }
    }
}
