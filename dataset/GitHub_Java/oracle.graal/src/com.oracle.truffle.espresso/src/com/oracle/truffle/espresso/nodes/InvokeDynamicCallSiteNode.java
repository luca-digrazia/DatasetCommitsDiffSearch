/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

// Non-constant call site. His target can change, but hopefully, the signature never changes.
public final class InvokeDynamicCallSiteNode extends QuickNode {

    private final StaticObject appendix;
    private final boolean hasAppendix;
    private final Symbol<Type> returnType;
    private final JavaKind returnKind;
    @Child private DirectCallNode callNode;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private Symbol<Symbol.Type>[] parsedSignature;

    InvokeDynamicCallSiteNode(StaticObjectImpl memberName, StaticObject appendix, Symbol<Type>[] parsedSignature, Meta meta) {
        Method target = (Method) memberName.getHiddenField(meta.HIDDEN_VMTARGET);
        this.appendix = appendix;
        this.parsedSignature = parsedSignature;
        this.returnType = Signatures.returnType(parsedSignature);
        this.returnKind = Signatures.returnKind(parsedSignature);
        this.hasAppendix = appendix != StaticObject.NULL;
        target.getDeclaringKlass().initialize();
        this.callNode = DirectCallNode.create(target.getCallTarget());

    }

    @Override
    public int invoke(final VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        int argCount = Signatures.parameterCount(parsedSignature, false);
        Object[] args = root.peekBasicArgumentsWithArray(frame, top, parsedSignature, new Object[argCount + (hasAppendix ? 1 : 0)], argCount);
        if (hasAppendix) {
            args[args.length - 1] = appendix;
        }
        Object result = callNode.call(args);
        int resultAt = top - Signatures.slotsForParameters(parsedSignature); // no receiver
        return (resultAt - top) + root.putKind(frame, resultAt, unbasic(result, returnType), returnKind);
    }

    // Transforms ints to sub-words
    public static Object unbasic(Object arg, Symbol<Type> t) {
        if (t == Type._boolean) {
            return ((int) arg != 0);
        } else if (t == Type._short) { // Unbox to cast.
            int value = (int) arg;
            return (short) value;
        } else if (t == Type._byte) {
            int value = (int) arg;
            return (byte) value;
        } else if (t == Type._char) {
            int value = (int) arg;
            return (char) value;
        } else {
            return arg;
        }
    }
}
