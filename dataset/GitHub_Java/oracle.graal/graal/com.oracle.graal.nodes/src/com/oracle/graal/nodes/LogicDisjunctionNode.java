/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.nodes.spi.*;

/**
 * This node is true if {@link #getX() x} <b>or</b> {@link #getY() y} are true.
 * 
 */
public class LogicDisjunctionNode extends LogicBinaryNode implements Canonicalizable {

    public LogicDisjunctionNode(LogicNode x, LogicNode y) {
        this(x, false, y, false);
    }

    public LogicDisjunctionNode(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated) {
        super(x, xNegated, y, yNegated);
    }

    @Override
    public LogicNode canonical(CanonicalizerTool tool) {
        LogicNode x = getX();
        LogicNode y = getY();
        if (x == y) {
            return x;
        }
        if (x instanceof LogicConstantNode) {
            if (((LogicConstantNode) x).getValue() ^ isXNegated()) {
                return LogicConstantNode.tautology(graph());
            } else {
                if (isYNegated()) {
                    negateUsages();
                }
                return y;
            }
        }
        if (y instanceof LogicConstantNode) {
            if (((LogicConstantNode) y).getValue() ^ isYNegated()) {
                return LogicConstantNode.tautology(graph());
            } else {
                if (isXNegated()) {
                    negateUsages();
                }
                return x;
            }
        }
        return this;
    }

}
