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

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code Phi} instruction represents the merging of dataflow
 * in the instruction graph. It refers to a join block and a variable.
 */
public final class Phi extends FixedNode {

    private static final int DEFAULT_MAX_VALUES = 2;

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_MERGE = 0;

    private final int maxValues;

    private static final int SUCCESSOR_COUNT = 0;

    private int usedInputCount;
    private boolean isDead;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT + maxValues;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The merge node for this phi.
     */
    public Merge merge() {
        return (Merge) inputs().get(super.inputCount() + INPUT_MERGE);
    }

    public Value setMerge(Value n) {
        return (Merge) inputs().set(super.inputCount() + INPUT_MERGE, n);
    }

    /**
     * Create a new Phi for the specified join block and local variable (or operand stack) slot.
     * @param kind the type of the variable
     * @param merge the join point
     * @param graph
     */
    public Phi(CiKind kind, Merge merge, Graph graph) {
        this(kind, merge, DEFAULT_MAX_VALUES, graph);
    }

    public Phi(CiKind kind, Merge merge, int maxValues, Graph graph) {
        super(kind, INPUT_COUNT + maxValues, SUCCESSOR_COUNT, graph);
        this.maxValues = maxValues;
        usedInputCount = 0;
        setMerge(merge);
    }

    /**
     * Get the instruction that produces the value associated with the i'th predecessor
     * of the join block.
     * @param i the index of the predecessor
     * @return the instruction that produced the value in the i'th predecessor
     */
    public Value valueAt(int i) {
        return (Value) inputs().get(INPUT_COUNT + i);
    }

    public Node setValueAt(int i, Node x) {
        return inputs().set(INPUT_COUNT + i, x);
    }

    /**
     * Get the number of inputs to this phi (i.e. the number of predecessors to the join block).
     * @return the number of inputs in this phi
     */
    public int valueCount() {
        return usedInputCount;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitPhi(this);
    }

    /**
     * Make this phi illegal if types were not merged correctly.
     */
    public void makeDead() {
        isDead = true;
    }

    public boolean isDead() {
        return isDead;
    }

    @Override
    public void print(LogStream out) {
        out.print("phi function (");
        for (int i = 0; i < valueCount(); ++i) {
            if (i != 0) {
                out.print(' ');
            }
            out.print(valueAt(i));
        }
        out.print(')');
    }

    @Override
    public String shortName() {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < valueCount(); ++i) {
            if (i != 0) {
                str.append(' ');
            }
            str.append(valueAt(i) == null ? "-" : valueAt(i).id());
        }
        return "Phi: (" + str + ")";
    }

    public Phi addInput(Node y) {
        assert !this.isDeleted() && !y.isDeleted();
        Phi phi = this;
        if (usedInputCount == maxValues) {
            phi = new Phi(kind, merge(), maxValues * 2, graph());
            for (int i = 0; i < valueCount(); ++i) {
                phi.addInput(valueAt(i));
            }
            phi.addInput(y);
            this.replace(phi);
        } else {
            setValueAt(usedInputCount++, y);
        }
        return phi;
    }

    public void removeInput(int index) {
        assert index < valueCount() : "index: " + index + ", valueCount: " + valueCount() + "@phi " + id();
        setValueAt(index, Node.Null);
        for (int i = index + 1; i < valueCount(); ++i) {
            setValueAt(i - 1, valueAt(i));
        }
        setValueAt(valueCount() - 1, Node.Null);
        usedInputCount--;
    }

    @Override
    public Node copy(Graph into) {
        Phi x = new Phi(kind, null, maxValues, into);
        x.usedInputCount = usedInputCount;
        x.isDead = isDead;
        return x;
    }
}
