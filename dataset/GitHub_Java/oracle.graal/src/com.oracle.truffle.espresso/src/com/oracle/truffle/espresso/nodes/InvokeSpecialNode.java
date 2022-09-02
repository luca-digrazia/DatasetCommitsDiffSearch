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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;

public final class InvokeSpecialNode extends QuickNode {
    protected final Method method;
    @Child private DirectCallNode directCallNode;

    public InvokeSpecialNode(Method method) {
        this.method = method;
        this.directCallNode = DirectCallNode.create(method.getCallTarget());
    }

    @Override
    public int invoke(final VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        // TODO(peterssen): IsNull Node?
        nullCheck(root.peekReceiver(frame, top, method));
        Object[] args = root.peekArguments(frame, top, true, method.getParsedSignature());
        Object result = callSpecial(args);
        int resultAt = top - Signatures.slotsForParameters(method.getParsedSignature()) - 1; // -receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, method.getReturnKind());
    }

    private final Object callSpecial(Object[] args) {
        if (method.isSynchronized()) {
            synchronized (/* this */ args[0]) {
                return directCallNode.call(args);
            }
        } else {
            return directCallNode.call(args);
        }
    }
}
