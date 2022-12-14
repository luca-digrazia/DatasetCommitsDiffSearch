/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.access;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceMemberType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceStructLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import java.util.IdentityHashMap;

/**
 * Describes how foreign interop should interpret values.
 */
public abstract class LLVMInteropType {

    public static final LLVMInteropType.Value UNKNOWN = Value.primitive(null);

    public enum ValueKind {
        I1,
        I8,
        I16,
        I32,
        I64,
        FLOAT,
        DOUBLE,
        POINTER;

        public final LLVMInteropType.Value type = Value.primitive(this);
    }

    public static final class Value extends LLVMInteropType {

        final ValueKind kind;
        final Structured baseType;

        private static Value primitive(ValueKind kind) {
            return new Value(kind, null);
        }

        static Value pointer(Structured baseType) {
            return new Value(ValueKind.POINTER, baseType);
        }

        private Value(ValueKind kind, Structured baseType) {
            this.kind = kind;
            this.baseType = baseType;
        }

        public ValueKind getKind() {
            return kind;
        }

        public Structured getBaseType() {
            return baseType;
        }
    }

    public abstract static class Structured extends LLVMInteropType {
    }

    public static final class Array extends Structured {

        final LLVMInteropType elementType;
        final long elementSize;
        final long length;

        Array(InteropTypeFactory.Register elementType, long elementSize, long length) {
            this.elementType = elementType.get(this);
            this.elementSize = elementSize;
            this.length = length;
        }

        private Array(LLVMInteropType elementType, long elementSize, long length) {
            this.elementType = elementType;
            this.elementSize = elementSize;
            this.length = length;
        }

        public LLVMInteropType getElementType() {
            return elementType;
        }

        public long getElementSize() {
            return elementSize;
        }

        public long getLength() {
            return length;
        }

        public LLVMInteropType.Array resize(long newLength) {
            return new LLVMInteropType.Array(elementType, elementSize, newLength);
        }
    }

    public static final class Struct extends Structured {

        @CompilationFinal(dimensions = 1) final StructMember[] members;

        Struct(StructMember[] members) {
            this.members = members;
        }

        public StructMember getMember(int i) {
            return members[i];
        }

        @TruffleBoundary
        public StructMember findMember(String name) {
            for (StructMember member : members) {
                if (member.getName().equals(name)) {
                    return member;
                }
            }
            return null;
        }

        public int getMemberCount() {
            return members.length;
        }
    }

    public static final class StructMember {

        final Struct struct;

        final String name;
        final long startOffset;
        final long endOffset;
        final LLVMInteropType type;

        StructMember(Struct struct, String name, long startOffset, long endOffset, LLVMInteropType type) {
            this.struct = struct;
            this.name = name;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.type = type;
        }

        boolean contains(long offset) {
            return startOffset <= offset && offset < endOffset;
        }

        public String getName() {
            return name;
        }

        public LLVMInteropType getType() {
            return type;
        }

        public long getStartOffset() {
            return startOffset;
        }

    }

    public static LLVMInteropType fromSourceType(LLVMSourceType type) {
        return new InteropTypeFactory().getStructured(type);
    }

    private static class InteropTypeFactory {

        private final IdentityHashMap<LLVMSourceType, LLVMInteropType> typeCache = new IdentityHashMap<>();

        private final class Register {

            private final LLVMSourceType source;
            private final LLVMSourceType target;

            private Register(LLVMSourceType source, LLVMSourceType target) {
                this.source = source;
                this.target = target;
            }

            LLVMInteropType get(LLVMInteropType self) {
                typeCache.put(source, self);
                return InteropTypeFactory.this.get(target);
            }
        }

        LLVMInteropType get(LLVMSourceType type) {
            LLVMSourceType actual = type.getActualType();
            if (typeCache.containsKey(actual)) {
                return typeCache.get(actual);
            } else {
                LLVMInteropType ret = convert(actual);
                typeCache.put(actual, ret);
                return ret;
            }
        }

        private LLVMInteropType convert(LLVMSourceType type) {
            if (type instanceof LLVMSourcePointerType) {
                return convertPointer((LLVMSourcePointerType) type);
            } else if (type instanceof LLVMSourceBasicType) {
                return convertBasic((LLVMSourceBasicType) type);
            } else {
                return convertStructured(type);
            }
        }

        Structured getStructured(LLVMSourceType type) {
            LLVMSourceType actual = type.getActualType();
            if (typeCache.containsKey(actual)) {
                LLVMInteropType ret = typeCache.get(actual);
                if (ret instanceof Structured) {
                    return (Structured) ret;
                } else {
                    return null;
                }
            } else {
                /*
                 * No need to put, structured types put themselves in the map to break cycles. Also,
                 * we don't want to put the null value in the map in case this type is not
                 * structured.
                 */
                return convertStructured(actual);
            }
        }

        Structured convertStructured(LLVMSourceType type) {
            if (type instanceof LLVMSourceArrayLikeType) {
                return convertArray((LLVMSourceArrayLikeType) type);
            } else if (type instanceof LLVMSourceStructLikeType) {
                return convertStruct((LLVMSourceStructLikeType) type);
            } else {
                return null;
            }
        }

        private Array convertArray(LLVMSourceArrayLikeType type) {
            LLVMSourceType base = type.getBaseType();
            return new Array(new Register(type, base), base.getSize() / 8, type.getLength());
        }

        private Struct convertStruct(LLVMSourceStructLikeType type) {
            Struct ret = new Struct(new StructMember[type.getDynamicElementCount()]);
            typeCache.put(type, ret);
            for (int i = 0; i < ret.members.length; i++) {
                LLVMSourceMemberType member = type.getDynamicElement(i);
                LLVMSourceType memberType = member.getElementType();
                long startOffset = member.getOffset() / 8;
                long endOffset = startOffset + memberType.getSize() / 8;
                ret.members[i] = new StructMember(ret, member.getName(), startOffset, endOffset, get(memberType));
            }
            return ret;
        }

        private static Value convertBasic(LLVMSourceBasicType type) {
            switch (type.getKind()) {
                case ADDRESS:
                    return ValueKind.POINTER.type;
                case BOOLEAN:
                    return ValueKind.I1.type;
                case FLOATING:
                    switch ((int) type.getSize()) {
                        case 32:
                            return ValueKind.FLOAT.type;
                        case 64:
                            return ValueKind.DOUBLE.type;
                    }
                    break;
                case SIGNED:
                case SIGNED_CHAR:
                case UNSIGNED:
                case UNSIGNED_CHAR:
                    switch ((int) type.getSize()) {
                        case 1:
                            return ValueKind.I1.type;
                        case 8:
                            return ValueKind.I8.type;
                        case 16:
                            return ValueKind.I16.type;
                        case 32:
                            return ValueKind.I32.type;
                        case 64:
                            return ValueKind.I64.type;
                    }
                    break;
            }
            return UNKNOWN;
        }

        private Value convertPointer(LLVMSourcePointerType type) {
            return Value.pointer(getStructured(type.getBaseType()));
        }
    }
}
