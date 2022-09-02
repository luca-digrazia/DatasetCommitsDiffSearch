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
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.vm.VM;

public final class InvokeStaticNode extends QuickNode {

    @CompilationFinal protected MethodVersion method;
    private final boolean callsDoPrivileged;

    @Child private DirectCallNode directCallNode;

    final int resultAt;

    public InvokeStaticNode(Method method, int top, int curBCI) {
        super(top, curBCI);
        assert method.isStatic();
        this.method = method.getMethodVersion();
        this.callsDoPrivileged = method.getMeta().java_security_AccessController.equals(method.getDeclaringKlass()) &&
                        Name.doPrivileged.equals(method.getName());
        this.resultAt = top - Signatures.slotsForParameters(method.getParsedSignature()); // no receiver
    }

    @Override
    public int execute(VirtualFrame frame, long[] primitives, Object[] refs) {
        if (!method.getAssumption().isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            directCallNode = null;
            // update to the latest method version
            method = method.getMethod().getMethodVersion();
        }
        // TODO(peterssen): Constant fold this check.
        if (directCallNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Obtaining call target initializes the declaring klass
            // insert call node though insertion method so that
            // stack frame iteration will see this node as parent
            directCallNode = insert(DirectCallNode.create(method.getMethod().getCallTarget()));
        }

        // Support for AccessController.doPrivileged*.
        if (callsDoPrivileged) {
            EspressoRootNode rootNode = (EspressoRootNode) getRootNode();
            if (rootNode != null) {
                // Put cookie in the caller frame.
                rootNode.setFrameId(frame, VM.GlobalFrameIDs.getID());
            }
        }

        Object[] args = BytecodeNode.popArguments(primitives, refs, top, false, method.getMethod().getParsedSignature());
        Object result = directCallNode.call(args);
        return (getResultAt() - top) + BytecodeNode.putKind(primitives, refs, getResultAt(), result, method.getMethod().getReturnKind());
    }

    @Override
    public boolean producedForeignObject(long[] primitives, Object[] refs) {
        return method.getMethod().getReturnKind().isObject() && BytecodeNode.peekObject(primitives, refs, getResultAt()).isForeignObject();
    }

    private int getResultAt() {
        return resultAt;
    }

    @Override
    public boolean removedByRedefintion() {
        return method.getMethod().isRemovedByRedefition();
    }
}
