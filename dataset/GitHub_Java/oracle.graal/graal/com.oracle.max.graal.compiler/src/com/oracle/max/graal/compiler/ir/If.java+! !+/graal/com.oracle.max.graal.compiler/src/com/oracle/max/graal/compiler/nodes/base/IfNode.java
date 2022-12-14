/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code If} instruction represents a branch that can go one of two directions depending on the outcome of a
 * comparison.
 */
public final class If extends ControlSplit implements Canonicalizable {

    @Input private BooleanNode compare;

    public BooleanNode compare() {
        return compare;
    }

    public void setCompare(BooleanNode x) {
        updateUsages(compare, x);
        compare = x;
    }

    public If(BooleanNode condition, double probability, Graph graph) {
        super(CiKind.Illegal, 2, new double[] {probability, 1 - probability}, graph);
        setCompare(condition);
    }

    /**
     * Gets the block corresponding to the true successor.
     *
     * @return the true successor
     */
    public FixedNode trueSuccessor() {
        return blockSuccessor(0);
    }

    /**
     * Gets the block corresponding to the false successor.
     *
     * @return the false successor
     */
    public FixedNode falseSuccessor() {
        return blockSuccessor(1);
    }

    public void setTrueSuccessor(FixedNode node) {
        setBlockSuccessor(0, node);
    }

    public void setFalseSuccessor(FixedNode node) {
        setBlockSuccessor(1, node);
    }

    /**
     * Gets the block corresponding to the specified outcome of the branch.
     *
     * @param istrue {@code true} if the true successor is requested, {@code false} otherwise
     * @return the corresponding successor
     */
    public FixedNode successor(boolean istrue) {
        return blockSuccessor(istrue ? 0 : 1);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitIf(this);
    }

    @Override
    public boolean verify() {
        assertTrue(compare() != null);
        assertTrue(trueSuccessor() != null);
        assertTrue(falseSuccessor() != null);
        return true;
    }

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (compare() instanceof Constant) {
            Constant c = (Constant) compare();
            if (c.asConstant().asBoolean()) {
                return trueSuccessor();
            } else {
                return falseSuccessor();
            }
        }
        if (trueSuccessor() instanceof EndNode && falseSuccessor() instanceof EndNode) {
            EndNode trueEnd = (EndNode) trueSuccessor();
            EndNode falseEnd = (EndNode) falseSuccessor();
            Merge merge = trueEnd.merge();
            if (merge == falseEnd.merge() && merge.phis().size() == 0 && merge.endCount() == 2) {
                FixedNode next = merge.next();
                merge.setNext(null); // disconnect to avoid next from having 2 preds
                return next;
            }
        }
        return this;
    }
}
