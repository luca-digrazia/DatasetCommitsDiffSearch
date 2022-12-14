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

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public abstract class AbstractVectorNode extends FixedNodeWithNext {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_VECTOR = 0;
    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    public void setVector(AbstractVectorNode length) {
        inputs().set(super.inputCount() + INPUT_VECTOR, length);
    }

    public AbstractVectorNode vector() {
        return (AbstractVectorNode) inputs().get(super.inputCount() + INPUT_VECTOR);
    }

    public AbstractVectorNode(CiKind kind, int inputCount, int successorCount, AbstractVectorNode vector, Graph graph) {
        super(kind, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
        setVector(vector);
    }

    protected static AbstractVectorNode findCommonNode(AbstractVectorNode left, AbstractVectorNode right, List<AbstractVectorNode> leftList, List<AbstractVectorNode> rightList) {
        Set<AbstractVectorNode> occured = new HashSet<AbstractVectorNode>();
        AbstractVectorNode common = null;
        AbstractVectorNode cur = left;
        while (cur != null) {
            occured.add(cur);
            cur = cur.vector();
        }

        cur = right;
        while (cur != null) {
            if (occured.contains(cur)) {
                common = cur;
                break;
            }
            cur = cur.vector();
        }

        fillUntil(left, cur, leftList);
        fillUntil(right, cur, rightList);
        return common;
    }

    private static void fillUntil(AbstractVectorNode left, AbstractVectorNode until, List<AbstractVectorNode> leftList) {
        AbstractVectorNode cur = left;
        while (cur != null && cur != until) {
            leftList.add(cur);
            cur = cur.vector();
        }
    }

    public void addToLoop(LoopBegin loop, IdentityHashMap<AbstractVectorNode, Value> nodes) {
        throw new IllegalStateException("unimplemented");
    }
}
