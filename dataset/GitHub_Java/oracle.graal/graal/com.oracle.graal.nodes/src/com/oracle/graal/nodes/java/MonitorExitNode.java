/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.graph.*;

/**
 * The {@code MonitorEnterNode} represents a monitor release.
 */
public final class MonitorExitNode extends AccessMonitorNode implements Lowerable, LIRLowerable, Node.IterableNodeType, MonitorExit {

    /**
     * Creates a new MonitorExitNode.
     *
     * @param object the instruction produces the object value
     */
    public MonitorExitNode(ValueNode object) {
        super(object);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitMonitorExit(this);
    }

    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }
}
