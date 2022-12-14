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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import com.oracle.truffle.llvm.parser.metadata.Flags;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDBasicType;
import com.oracle.truffle.llvm.parser.metadata.MDCompositeType;
import com.oracle.truffle.llvm.parser.metadata.MDDerivedType;
import com.oracle.truffle.llvm.parser.metadata.MDEnumerator;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDOldNode;
import com.oracle.truffle.llvm.parser.metadata.MDReference;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MDSubrange;
import com.oracle.truffle.llvm.parser.metadata.MDTypedValue;
import com.oracle.truffle.llvm.parser.metadata.MetadataList;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceDecoratorType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceEnumLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceMemberType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceStructLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.oracle.truffle.llvm.runtime.debug.LLVMSourceType.UNKNOWN_TYPE;

final class MDTypeExtractor implements MetadataVisitor {

    private static final String COUNT_NAME = "<count>";

    LLVMSourceType parseType(MDBaseNode mdType) {
        if (mdType == null) {
            return null;
        }
        mdType.accept(this);
        return parsedTypes.getOrDefault(mdType, UNKNOWN_TYPE);
    }

    private final Map<MDBaseNode, LLVMSourceType> parsedTypes = new HashMap<>();

    private final Map<String, MDCompositeType> identifiedTypes = new HashMap<>();

    private MetadataList scopeMetadata;

    MDTypeExtractor() {
    }

    void setScopeMetadata(MetadataList currentMetadata) {
        this.scopeMetadata = currentMetadata;
    }

    @Override
    public void ifVisitNotOverwritten(MDBaseNode md) {
        parsedTypes.put(md, UNKNOWN_TYPE);
    }

    @Override
    public void visit(MDBasicType mdType) {
        if (!parsedTypes.containsKey(mdType)) {

            String name = MDNameExtractor.getName(mdType.getName());
            long size = mdType.getSize();
            long align = mdType.getAlign();
            long offset = mdType.getOffset();

            LLVMSourceBasicType.Kind kind;
            switch (mdType.getEncoding()) {
                case DW_ATE_ADDRESS:
                    kind = LLVMSourceBasicType.Kind.ADDRESS;
                    break;
                case DW_ATE_BOOLEAN:
                    kind = LLVMSourceBasicType.Kind.BOOLEAN;
                    break;
                case DW_ATE_FLOAT:
                    kind = LLVMSourceBasicType.Kind.FLOATING;
                    break;
                case DW_ATE_SIGNED:
                    kind = LLVMSourceBasicType.Kind.SIGNED;
                    break;
                case DW_ATE_SIGNED_CHAR:
                    kind = LLVMSourceBasicType.Kind.SIGNED_CHAR;
                    break;
                case DW_ATE_UNSIGNED:
                    kind = LLVMSourceBasicType.Kind.UNSIGNED;
                    break;
                case DW_ATE_UNSIGNED_CHAR:
                    kind = LLVMSourceBasicType.Kind.UNSIGNED_CHAR;
                    break;
                default:
                    kind = LLVMSourceBasicType.Kind.UNKNOWN;
                    break;
            }

            final LLVMSourceType type = new LLVMSourceBasicType(name, size, align, offset, kind);
            parsedTypes.put(mdType, type);
        }
    }

    @Override
    public void visit(MDCompositeType mdType) {
        if (!parsedTypes.containsKey(mdType)) {
            final long size = mdType.getSize();
            final long align = mdType.getAlign();
            final long offset = mdType.getOffset();

            switch (mdType.getTag()) {

                case DW_TAG_VECTOR_TYPE:
                case DW_TAG_ARRAY_TYPE: {
                    final LLVMSourceArrayLikeType type = new LLVMSourceArrayLikeType(size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getDerivedFrom();
                    mdBaseType.accept(this);
                    LLVMSourceType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = UNKNOWN_TYPE;
                    }

                    final List<LLVMSourceType> members = new ArrayList<>(1);
                    getElements(mdType.getMemberDescriptors(), members);

                    for (int i = members.size() - 1; i > 0; i--) {
                        final LLVMSourceType count = members.get(i);
                        final long tmpSize = count.getSize() * baseType.getSize(); // TODO alignment
                        final LLVMSourceArrayLikeType tmp = new LLVMSourceArrayLikeType(tmpSize, align, 0L);

                        if (COUNT_NAME.equals(count.getName())) {
                            tmp.setLength(count.getSize());
                            final LLVMSourceType finalBaseType = baseType;
                            tmp.setBaseType(() -> finalBaseType);
                            if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_VECTOR_TYPE) {
                                tmp.setName(() -> String.format("%s<%d>", finalBaseType.getName(), tmp.getLength()));
                            } else {
                                tmp.setName(() -> String.format("%s[%d]", finalBaseType.getName(), tmp.getLength()));
                            }
                        } else {
                            tmp.setLength(0);
                        }
                        baseType = tmp;
                    }

                    final LLVMSourceType count = members.get(0);
                    if (COUNT_NAME.equals(count.getName())) {
                        type.setLength(count.getSize());
                        final LLVMSourceType finalBaseType = baseType;
                        type.setBaseType(() -> finalBaseType);
                        if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_VECTOR_TYPE) {
                            type.setName(() -> String.format("%s<%d>", finalBaseType.getName(), type.getLength()));
                        } else {
                            type.setName(() -> String.format("%s[%d]", finalBaseType.getName(), type.getLength()));
                        }

                    } else {
                        type.setLength(0);
                    }

                    break;
                }

                case DW_TAG_CLASS_TYPE:
                case DW_TAG_UNION_TYPE:
                case DW_TAG_STRUCTURE_TYPE: {
                    final LLVMSourceStructLikeType type = new LLVMSourceStructLikeType(size, align, offset);
                    final String parsedName = MDNameExtractor.getName(mdType.getName());

                    if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_CLASS_TYPE) {
                        type.setName(() -> parsedName);
                    } else if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_STRUCTURE_TYPE) {
                        type.setName(() -> String.format("struct %s", parsedName));
                    } else {
                        type.setName(() -> String.format("union %s", parsedName));
                    }

                    parsedTypes.put(mdType, type);

                    final List<LLVMSourceType> members = new ArrayList<>();
                    getElements(mdType.getMemberDescriptors(), members);
                    for (final LLVMSourceType member : members) {
                        if (member instanceof LLVMSourceMemberType) {
                            type.addMember((LLVMSourceMemberType) member);

                        } else {
                            // we should never get here because the offsets will be wrong, but this
                            // is still better than crashing outright and for testing it at least
                            // does not fail silently
                            final LLVMSourceMemberType namedMember = new LLVMSourceMemberType("<unknown>", member.getSize(), member.getAlign(), member.getOffset());
                            namedMember.setElementType(member);
                            type.addMember(namedMember);
                        }
                    }
                    break;
                }

                case DW_TAG_ENUMERATION_TYPE: {
                    final String parsedName = MDNameExtractor.getName(mdType.getName());
                    final LLVMSourceEnumLikeType type = new LLVMSourceEnumLikeType(() -> String.format("enum %s", parsedName), size, align, offset);
                    parsedTypes.put(mdType, type);

                    final List<LLVMSourceType> members = new ArrayList<>();
                    getElements(mdType.getMemberDescriptors(), members);
                    for (final LLVMSourceType member : members) {
                        type.addValue((int) member.getOffset(), member.getName());
                    }
                    break;
                }

                default:
                    // TODO parse other kinds and remove this
                    parsedTypes.put(mdType, UNKNOWN_TYPE);
            }
        }
    }

    @Override
    public void visit(MDDerivedType mdType) {
        if (!parsedTypes.containsKey(mdType)) {
            long size = mdType.getSize();
            long align = mdType.getAlign();
            long offset = mdType.getOffset();

            switch (mdType.getTag()) {

                case DW_TAG_MEMBER: {
                    final String name = MDNameExtractor.getName(mdType.getName());
                    final LLVMSourceMemberType type = new LLVMSourceMemberType(name, size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    LLVMSourceType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = UNKNOWN_TYPE;
                    }
                    if (Flags.BITFIELD.isSetIn(mdType.getFlags())) {
                        LLVMSourceDecoratorType decorator = new LLVMSourceDecoratorType(size, align, offset, Function.identity(), l -> size);
                        final LLVMSourceType finalBaseType = baseType;
                        decorator.setBaseType(() -> finalBaseType);
                        baseType = decorator;
                    }
                    type.setElementType(baseType);
                    break;
                }

                case DW_TAG_POINTER_TYPE: {
                    final boolean isSafeToDereference = Flags.OBJECT_POINTER.isSetIn(mdType.getFlags());
                    final LLVMSourcePointerType type = new LLVMSourcePointerType(size, align, offset, isSafeToDereference);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    LLVMSourceType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = UNKNOWN_TYPE;
                    }
                    final LLVMSourceType finalBaseType = baseType; // to be used in lambdas
                    type.setBaseType(() -> finalBaseType);
                    type.setName(() -> String.format("*%s", finalBaseType.getName()));
                    break;
                }

                case DW_TAG_TYPEDEF:
                case DW_TAG_VOLATILE_TYPE:
                case DW_TAG_CONST_TYPE: {
                    final Function<String, String> decorator;
                    switch (mdType.getTag()) {
                        case DW_TAG_VOLATILE_TYPE:
                            decorator = s -> String.format("volatile %s", s);
                            break;
                        case DW_TAG_CONST_TYPE:
                            decorator = s -> String.format("const %s", s);
                            break;
                        case DW_TAG_TYPEDEF: {
                            final String name = MDNameExtractor.getName(mdType.getName());
                            decorator = s -> name;
                            break;
                        }
                        default:
                            decorator = Function.identity();
                    }
                    final LLVMSourceDecoratorType type = new LLVMSourceDecoratorType(size, align, offset, decorator, Function.identity());
                    parsedTypes.put(mdType, type);
                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    LLVMSourceType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = UNKNOWN_TYPE;
                    }
                    final LLVMSourceType finalBaseType = baseType; // to be used in lambdas
                    type.setBaseType(() -> finalBaseType);
                    break;
                }

                case DW_TAG_INHERITANCE: {
                    final LLVMSourceMemberType type = new LLVMSourceMemberType("super" + mdType.toString(), size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    final LLVMSourceType baseType = parsedTypes.getOrDefault(mdBaseType, UNKNOWN_TYPE);
                    type.setElementType(baseType);
                    type.setName(() -> String.format("super (%s)", baseType.getName()));

                    break;
                }

                default:
                    // TODO parse other kinds and remove this
                    parsedTypes.put(mdType, UNKNOWN_TYPE);
            }
        }
    }

    @Override
    public void visit(MDReference mdRef) {
        if (mdRef != MDReference.VOID) {
            if (!parsedTypes.containsKey(mdRef)) {
                final MDBaseNode target = mdRef.get();
                target.accept(this);
                final LLVMSourceType parsedType = parsedTypes.get(target);
                if (parsedType != null) {
                    parsedTypes.put(mdRef, parsedType);
                }
            }
        }
    }

    @Override
    public void visit(MDSubrange mdRange) {
        // for array types the member descriptors contain this as the only element
        parsedTypes.put(mdRange, new IntermediaryType(() -> COUNT_NAME, mdRange.getSize(), 0L, 0L));
    }

    @Override
    public void visit(MDEnumerator mdEnumElement) {
        final String representation = MDNameExtractor.getName(mdEnumElement.getName());
        final long id = mdEnumElement.getValue();
        parsedTypes.put(mdEnumElement, new IntermediaryType(() -> representation, 0, 0, id));
    }

    @Override
    public void visit(MDNode mdTypeList) {
        for (MDBaseNode member : mdTypeList) {
            member.accept(this);
        }
    }

    @Override
    public void visit(MDString mdString) {
        if (parsedTypes.containsKey(mdString)) {
            return;

        } else if (!identifiedTypes.containsKey(mdString.getString())) {
            scopeMetadata.accept(new MDFollowRefVisitor() {

                @Override
                public void visit(MDCompositeType mdCompositeType) {
                    final String identifier = getIdentifier(mdCompositeType);
                    identifiedTypes.put(identifier, mdCompositeType);
                }

            });
        }

        final MDCompositeType referencedType = identifiedTypes.get(mdString.getString());
        if (referencedType != null) {
            referencedType.accept(this);
            parsedTypes.put(mdString, parsedTypes.getOrDefault(referencedType, UNKNOWN_TYPE));
        }
    }

    @Override
    public void visit(MDGlobalVariable mdGlobal) {
        if (!parsedTypes.containsKey(mdGlobal)) {
            final MDReference typeRef = mdGlobal.getType();
            typeRef.accept(this);
            final LLVMSourceType type = parsedTypes.get(typeRef);
            if (type != null) {
                parsedTypes.put(mdGlobal, type);
            }
        }
    }

    @Override
    public void visit(MDLocalVariable mdLocal) {
        if (!parsedTypes.containsKey(mdLocal)) {
            final MDReference typeRef = mdLocal.getType();
            typeRef.accept(this);
            LLVMSourceType type = parsedTypes.get(typeRef);
            if (type == null) {
                return;

            } else if (Flags.OBJECT_POINTER.isSetIn(mdLocal.getFlags()) && type instanceof LLVMSourcePointerType) {
                // llvm does not set the objectpointer flag on this pointer type even though it sets
                // it on the pointer type that is used in the function type descriptor
                final LLVMSourcePointerType oldPointer = (LLVMSourcePointerType) type;
                final LLVMSourcePointerType newPointer = new LLVMSourcePointerType(oldPointer.getSize(), oldPointer.getAlign(), oldPointer.getOffset(), true);
                newPointer.setBaseType(oldPointer::getBaseType);
                newPointer.setName(oldPointer::getName);
                type = newPointer;
            }
            parsedTypes.put(mdLocal, type);
        }
    }

    private void getElements(MDReference elemRef, List<LLVMSourceType> elemTypes) {
        if (elemRef == MDReference.VOID) {
            return;
        }

        MDBaseNode elemList = elemRef.get();
        if (elemList instanceof MDNode) {
            MDNode elemListNode = (MDNode) elemList;
            for (MDBaseNode elemNode : elemListNode) {
                if (elemNode != MDReference.VOID && elemNode instanceof MDReference) {
                    elemNode.accept(this);
                    final LLVMSourceType elemType = parsedTypes.get(((MDReference) elemNode).get());
                    if (elemType != UNKNOWN_TYPE) {
                        elemTypes.add(elemType);
                    }
                }
            }
        } else if (elemList instanceof MDOldNode) {
            MDOldNode elemListNode = (MDOldNode) elemList;
            for (MDTypedValue elemNode : elemListNode) {
                if (elemNode != MDReference.VOID && elemNode instanceof MDReference) {
                    MDReference elementReference = (MDReference) elemNode;
                    elementReference.accept(this);
                    final LLVMSourceType elemType = parsedTypes.get(elementReference.get());
                    if (elemType != UNKNOWN_TYPE) {
                        elemTypes.add(elemType);
                    }
                }
            }
        }
    }

    private static String getIdentifier(MDCompositeType type) {
        MDBaseNode id = type.getIdentifier();
        if (id != MDReference.VOID) {
            id = ((MDReference) id).get();
            if (id instanceof MDString) {
                return ((MDString) id).getString();
            }
        }
        return null;
    }

    private static final class IntermediaryType extends LLVMSourceType {

        IntermediaryType(Supplier<String> nameSupplier, long size, long align, long offset) {
            super(nameSupplier, size, align, offset);
        }

        @Override
        public LLVMSourceType getOffset(long newOffset) {
            return this;
        }
    }
}
