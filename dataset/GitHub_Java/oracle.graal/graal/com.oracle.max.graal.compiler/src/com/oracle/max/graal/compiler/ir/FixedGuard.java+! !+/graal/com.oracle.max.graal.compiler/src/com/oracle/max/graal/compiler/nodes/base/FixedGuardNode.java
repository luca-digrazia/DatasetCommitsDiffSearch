/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.ir.Deoptimize.DeoptAction;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public final class FixedGuard extends FixedNodeWithNext implements Canonicalizable {

    @Input private final NodeInputList<BooleanNode> conditions = new NodeInputList<BooleanNode>(this);

    public FixedGuard(BooleanNode node, Graph graph) {
        this(graph);
        addNode(node);
    }

    public FixedGuard(Graph graph) {
        super(CiKind.Illegal, graph);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitFixedGuard(this);
    }

    public void addNode(BooleanNode x) {
        conditions.add(x);
    }

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        for (BooleanNode n : conditions.snapshot()) {
            if (n instanceof Constant) {
                Constant c = (Constant) n;
                if (c.asConstant().asBoolean()) {
                    conditions.remove(n);
                } else {
                    return new Deoptimize(DeoptAction.InvalidateRecompile, graph());
                }
            }
        }

        if (conditions.isEmpty()) {
            return next();
        }
        return this;
    }
}
