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
package com.oracle.max.graal.compiler.nodes.extended;

import java.util.*;

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public class VirtualObjectFieldNode extends FloatingNode {

    @Input private VirtualObjectNode object;
    @Input private FloatingNode lastState;
    @Input private ValueNode input;

    public VirtualObjectNode object() {
        return object;
    }

    public void setObject(VirtualObjectNode x) {
        updateUsages(object, x);
        object = x;
    }

    public FloatingNode lastState() {
        return lastState;
    }

    public void setLastState(FloatingNode x) {
        updateUsages(lastState, x);
        lastState = x;
    }

    public ValueNode input() {
        return input;
    }

    public void setInput(ValueNode x) {
        updateUsages(input, x);
        input = x;
    }

    private int index;

    /**
     * Constructs a new ArrayLength instruction.
     * @param array the instruction producing the array
     * @param newFrameState the state after executing this instruction
     */
    public VirtualObjectFieldNode(VirtualObjectNode object, FloatingNode lastState, ValueNode input, int index, Graph graph) {
        super(CiKind.Int, graph);
        this.index = index;
        setObject(object);
        setLastState(lastState);
        setInput(input);
    }

    public int index() {
        return index;
    }

    @Override
    public void accept(ValueVisitor v) {
        // nothing to do...
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("index", index);
        return properties;
    }

    @Override
    public String shortName() {
        return "VirtualObjectField " + object().fields()[index].name();
    }
}
