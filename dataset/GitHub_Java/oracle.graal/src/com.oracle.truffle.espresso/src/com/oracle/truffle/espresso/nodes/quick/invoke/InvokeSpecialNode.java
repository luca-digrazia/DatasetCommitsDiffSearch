/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.OperandStack;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class InvokeSpecialNode extends QuickNode {
    @CompilationFinal protected MethodVersion method;
    @Child private DirectCallNode directCallNode;

    final int resultAt;

    public InvokeSpecialNode(Method method, int top, int callerBCI) {
        super(top, callerBCI);
        this.method = method.getMethodVersion();
        this.directCallNode = DirectCallNode.create(method.getCallTarget());
        this.resultAt = top - Signatures.slotsForParameters(method.getParsedSignature()) - 1; // -receiver
    }

    @Override
    public int execute(VirtualFrame frame, final OperandStack stack) {
        if (!method.getAssumption().isValid()) {
            // update to the latest method version and grab a new direct call target
            CompilerDirectives.transferToInterpreterAndInvalidate();
            method = method.getMethod().getMethodVersion();
            directCallNode = DirectCallNode.create(method.getCallTarget());
            adoptChildren();
        }
        // TODO(peterssen): IsNull Node?
        Object[] args = BytecodeNode.popArguments(stack, top, true, method.getMethod().getParsedSignature());
        nullCheck((StaticObject) args[0]); // nullcheck receiver
        Object result = directCallNode.call(args);
        return (getResultAt() - top) + BytecodeNode.putKind(stack, getResultAt(), result, method.getMethod().getReturnKind());
    }

    @Override
    public boolean producedForeignObject(OperandStack stack) {
        return method.getMethod().getReturnKind().isObject() && BytecodeNode.peekObject(stack, getResultAt()).isForeignObject();
    }

    private int getResultAt() {
        return resultAt;
    }

    @Override
    public boolean removedByRedefintion() {
        return method.getMethod().isRemovedByRedefition();
    }
}
