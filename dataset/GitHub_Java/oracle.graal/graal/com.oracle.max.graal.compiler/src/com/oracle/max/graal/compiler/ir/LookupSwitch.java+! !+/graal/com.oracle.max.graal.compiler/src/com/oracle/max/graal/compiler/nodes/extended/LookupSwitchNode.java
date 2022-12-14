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

import java.util.*;

import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.cfg.*;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;

/**
 * The {@code LookupSwitch} instruction represents a lookup switch bytecode, which has a sorted
 * array of key values.
 */
public final class LookupSwitch extends SwitchNode {

    private static final int INPUT_COUNT = 0;
    private static final int SUCCESSOR_COUNT = 0;

    final int[] keys;

    /**
     * Constructs a new LookupSwitch instruction.
     * @param value the instruction producing the value being switched on
     * @param successors the list of successors
     * @param keys the list of keys, sorted
     * @param stateAfter the state after the switch
     * @param graph
     */
    public LookupSwitch(ValueNode value, List<? extends FixedNode> successors, int[] keys, double[] probability, Graph graph) {
        super(value, successors, probability, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.keys = keys;
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
    public void accept(ValueVisitor v) {
        v.visitLookupSwitch(this);
    }
}
