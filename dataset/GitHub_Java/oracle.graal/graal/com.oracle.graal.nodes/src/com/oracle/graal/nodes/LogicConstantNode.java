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
package com.oracle.graal.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code ConstantNode} represents a constant such as an integer value, long, float, object
 * reference, address, etc.
 */
@NodeInfo(nameTemplate = "{p#value}")
public class LogicConstantNode extends LogicNode implements LIRLowerable {

    public final boolean value;

    protected LogicConstantNode(boolean value) {
        super();
        this.value = value;
    }

    /**
     * Returns a node for a boolean constant.
     * 
     * @param v the boolean value for which to create the instruction
     * @param graph
     * @return a node representing the boolean
     */
    public static LogicConstantNode forBoolean(boolean v, Graph graph) {
        return graph.unique(new LogicConstantNode(v));
    }

    public static LogicConstantNode tautology(Graph graph) {
        return forBoolean(true, graph);
    }

    public static LogicConstantNode contradiction(Graph graph) {
        return forBoolean(false, graph);
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public LogicNode canonical(CanonicalizerTool tool) {
        throw new GraalInternalError("shouldn't call canonical on LogicConstantNode");
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        // nothing to do
    }
}
