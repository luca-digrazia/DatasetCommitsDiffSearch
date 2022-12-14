/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class LeafAssumptionGetterNode extends InlinedGetterNode {

    protected final int opCode;
    protected final int curBCI;

    protected LeafAssumptionGetterNode(Method inlinedMethod, int top, int opCode, int curBCI) {
        super(inlinedMethod, top, curBCI);
        this.opCode = opCode;
        this.curBCI = curBCI;
    }

    @Override
    public int execute(VirtualFrame frame) {
        BytecodeNode root = getBytecodesNode();
        if (inlinedMethod.leafAssumption()) {
            StaticObject receiver = field.isStatic()
                            ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                            : nullCheck(root.peekAndReleaseObject(frame, top - 1));
            int resultAt = inlinedMethod.isStatic() ? top : (top - 1);
            return (resultAt - top) + getFieldNode.getField(frame, root, receiver, resultAt);
        } else {
            return root.reQuickenInvoke(frame, top, curBCI, opCode, inlinedMethod);
        }
    }

}
