/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.impl.Method;

public final class InvokeStaticNode extends QuickNode {

    protected final Method method;
    @Child private DirectCallNode directCallNode;

    public InvokeStaticNode(Method method) {
        assert method.isStatic();
        this.method = method;
    }

    @Override
    public int invoke(final VirtualFrame frame, int top) {
        // TODO(peterssen): Constant fold this check.
        if (directCallNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Obtaining call target initializes the declaring klass
            directCallNode = DirectCallNode.create(method.getCallTarget());
        }
        BytecodeNode root = (BytecodeNode) getParent();
        Object[] args = root.peekArguments(frame, top, false, method.getParsedSignature());

        Object result = directCallNode.call(args);
        int resultAt = top - Signatures.slotsForParameters(method.getParsedSignature()); // no
                                                                                         // receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, method.getReturnKind());
    }
}
