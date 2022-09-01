/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.Value;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_30;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * A special purpose store node that differs from {@link LogicCompareAndSwapNode} in that it returns
 * either the expected value or the compared against value instead of a boolean.
 */
@NodeInfo(allowedUsageTypes = {Value, Memory}, cycles = CYCLES_30, size = SIZE_8)
public final class ValueCompareAndSwapNode extends AbstractCompareAndSwapNode {
    public static final NodeClass<ValueCompareAndSwapNode> TYPE = NodeClass.create(ValueCompareAndSwapNode.class);

    public ValueCompareAndSwapNode(ValueNode address, ValueNode expectedValue, ValueNode newValue, LocationIdentity location) {
        this((AddressNode) address, expectedValue, newValue, location, BarrierType.NONE);
    }

    public ValueCompareAndSwapNode(AddressNode address, ValueNode expectedValue, ValueNode newValue, LocationIdentity location, BarrierType barrierType) {
        super(TYPE, address, location, expectedValue, newValue, barrierType, expectedValue.stamp().meet(newValue.stamp()));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert getNewValue().stamp().isCompatible(getExpectedValue().stamp());
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        gen.setResult(this, tool.emitValueCompareAndSwap(gen.operand(getAddress()), gen.operand(getExpectedValue()), gen.operand(getNewValue())));
    }
}
