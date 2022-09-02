/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeInfo(shortName = ".")
public class DebugExprObjectMemberNode extends LLVMExpressionNode {

    @Child private LLVMExpressionNode baseNode;
    private final String fieldName;

    public DebugExprObjectMemberNode(LLVMExpressionNode baseNode, String fieldName) {
        this.fieldName = fieldName;
        this.baseNode = baseNode;
    }

    public DebugExprType getType(Object baseMember) {
        return findMemberAndType(baseMember).getRight();
    }

    public Object getMember(Object baseMember) {
        return findMemberAndType(baseMember).getLeft();
    }

    public String getFieldName() {
        return fieldName;
    }

    private Pair<Object, DebugExprType> findMemberAndType(Object baseMember) {
        InteropLibrary library = InteropLibrary.getFactory().getUncached();

        if (library.isMemberExisting(baseMember, fieldName)) {
            try {
                Object member = library.readMember(baseMember, fieldName);
                LLVMDebuggerValue ldv = (LLVMDebuggerValue) member;
                Object metaObj = ldv.getMetaObject();
                DebugExprType type = DebugExprType.getTypeFromSymbolTableMetaObject(metaObj);
                return Pair.create(member, type);
            } catch (UnsupportedMessageException e1) {
                throw DebugExprException.symbolNotFound(this, fieldName, baseMember);
            } catch (UnknownIdentifierException e1) {
                throw DebugExprException.symbolNotFound(this, e1.getUnknownIdentifier(), baseMember);
            } catch (ClassCastException e1) {
                throw DebugExprException.symbolNotFound(this, fieldName, baseMember);
            }
        }

        throw DebugExprException.symbolNotFound(this, fieldName, baseMember);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object baseMember = baseNode.executeGeneric(frame);
        Pair<Object, DebugExprType> pair = findMemberAndType(baseMember);
        Object member = pair.getLeft();
        if (member != null)
            return pair.getRight().parse(member);
        throw DebugExprException.symbolNotFound(this, fieldName, baseMember);
    }
}
