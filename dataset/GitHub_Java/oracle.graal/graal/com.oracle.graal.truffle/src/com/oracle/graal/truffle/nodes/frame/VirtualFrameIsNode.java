/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.frame;

import jdk.vm.ci.meta.JavaKind;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.ConditionalNode;
import com.oracle.graal.nodes.calc.IntegerEqualsNode;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;
import com.oracle.truffle.api.frame.FrameSlot;

@NodeInfo
public final class VirtualFrameIsNode extends VirtualFrameAccessorNode implements Virtualizable {
    public static final NodeClass<VirtualFrameIsNode> TYPE = NodeClass.create(VirtualFrameIsNode.class);

    public VirtualFrameIsNode(NewFrameNode frame, FrameSlot frameSlot, int accessTag) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean), frame, frameSlot, accessTag);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode tagAlias = tool.getAlias(frame.virtualFrameTagArray);

        if (tagAlias instanceof VirtualObjectNode) {
            VirtualObjectNode tagVirtual = (VirtualObjectNode) tagAlias;

            ValueNode actualTag = tool.getEntry(tagVirtual, getFrameSlotIndex());
            if (actualTag.isConstant()) {
                tool.replaceWith(getConstant(actualTag.asJavaConstant().asInt() == accessTag ? 1 : 0));

            } else {
                LogicNode comparison = new IntegerEqualsNode(actualTag, getConstant(accessTag));
                tool.addNode(comparison);
                ConditionalNode result = new ConditionalNode(comparison, getConstant(1), getConstant(0));
                tool.addNode(result);
                tool.replaceWith(result);
            }
            return;
        }

        /*
         * We could "virtualize" to a UnsafeLoadNode here that remains a memory access. But it is
         * simpler, and consistent with the get and set intrinsification, to deoptimize.
         */
        insertDeoptimization(tool);
    }
}
