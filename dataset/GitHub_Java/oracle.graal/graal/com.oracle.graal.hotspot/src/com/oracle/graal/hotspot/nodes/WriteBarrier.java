/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_100;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_100;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;

@NodeInfo(cycles = CYCLES_100, size = SIZE_100)
public abstract class WriteBarrier extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<WriteBarrier> TYPE = NodeClass.create(WriteBarrier.class);

    protected WriteBarrier(NodeClass<? extends WriteBarrier> c) {
        super(c, StampFactory.forVoid());
    }

    @Override
    public void lower(LoweringTool tool) {
        assert graph().getGuardsStage().areFrameStatesAtDeopts();
        tool.getLowerer().lower(this, tool);
    }
}
