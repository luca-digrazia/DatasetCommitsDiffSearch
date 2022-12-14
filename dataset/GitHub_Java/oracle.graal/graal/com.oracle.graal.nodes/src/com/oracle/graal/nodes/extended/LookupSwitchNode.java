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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code LookupSwitchNode} represents a lookup switch bytecode, which has a sorted
 * array of key values.
 */
public final class LookupSwitchNode extends SwitchNode implements LIRLowerable, Simplifiable {

    private final int[] keys;

    /**
     * Constructs a new LookupSwitch instruction.
     * @param value the instruction producing the value being switched on
     * @param successors the list of successors
     * @param keys the list of keys, sorted
     */
    public LookupSwitchNode(ValueNode value, BeginNode[] successors, int[] keys, double[] probability) {
        super(value, successors, probability);
        assert successors.length == keys.length + 1;
        this.keys = keys;
    }

    public LookupSwitchNode(ValueNode value, int[] keys, double[] switchProbability) {
        this(value, new BeginNode[switchProbability.length], keys, switchProbability);
    }

    /**
     * Gets the key at the specified index.
     * @param i the index
     * @return the key at that index
     */
    public int keyAt(int i) {
        return keys[i];
    }

    public int keysLength() {
        return keys.length;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitLookupSwitch(this);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (value() instanceof ConstantNode) {
            ConstantNode constant = (ConstantNode) value();
            int value = constant.value.asInt();

            BeginNode remainingSux = (BeginNode) defaultSuccessor();
            int remainingSuxIndex = blockSuccessorCount() - 1;
            for (int i = 0; i < keys.length; i++) {
                if (value == keys[i]) {
                    remainingSux = blockSuccessor(i);
                    remainingSuxIndex = i;
                    break;
                }
            }

            for (int i = 0; i < blockSuccessorCount(); i++) {
                BeginNode sux = blockSuccessor(i);
                if (sux != remainingSux) {
                    tool.deleteBranch(sux);
                }
            }

            tool.addToWorkList(remainingSux);
            ((StructuredGraph) graph()).removeSplit(this, remainingSuxIndex);
        }
    }
}
