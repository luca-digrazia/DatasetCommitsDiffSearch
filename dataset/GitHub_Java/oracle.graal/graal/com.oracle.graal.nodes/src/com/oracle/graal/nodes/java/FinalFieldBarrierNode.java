/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_20;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_2;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.MembarNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;

@NodeInfo(cycles = CYCLES_20, size = SIZE_2)
public class FinalFieldBarrierNode extends FixedWithNextNode implements Virtualizable, Lowerable {
    public static final NodeClass<FinalFieldBarrierNode> TYPE = NodeClass.create(FinalFieldBarrierNode.class);

    @Input private ValueNode value;

    public FinalFieldBarrierNode(ValueNode value) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
    }

    public ValueNode getValue() {
        return value;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (tool.getAlias(value) instanceof VirtualObjectNode) {
            tool.delete();
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        graph().replaceFixedWithFixed(this, graph().add(new MembarNode(LOAD_STORE | STORE_STORE)));
    }
}
