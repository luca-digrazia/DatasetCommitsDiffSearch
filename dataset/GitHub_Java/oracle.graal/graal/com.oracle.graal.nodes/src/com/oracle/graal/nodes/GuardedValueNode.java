/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A node that changes the type of its input, usually narrowing it. For example, a GuardedValueNode
 * is used to keep the nodes depending on guards inside a loop during speculative guard movement.
 * 
 * A GuardedValueNode will only go away if its guard is null or {@link StructuredGraph#start()}.
 */
public class GuardedValueNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, Node.IterableNodeType, GuardingNode, Canonicalizable {

    @Input private ValueNode object;

    public ValueNode object() {
        return object;
    }

    public GuardedValueNode(ValueNode object, GuardingNode guard) {
        super(object.stamp(), guard);
        this.object = object;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        if (object.kind() != Kind.Void && object.kind() != Kind.Illegal) {
            generator.setResult(this, generator.operand(object));
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(stamp().join(object().stamp()));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual) {
            tool.replaceWithVirtual(state.getVirtualObject());
        }
    }

    public ValueNode canonical(CanonicalizerTool tool) {
        if (getGuard() == graph().start()) {
            return object();
        }
        return this;
    }
}
