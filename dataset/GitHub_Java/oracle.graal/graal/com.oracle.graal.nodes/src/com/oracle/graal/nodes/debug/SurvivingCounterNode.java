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
package com.oracle.graal.nodes.debug;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * This is a special version of the dynamic counter node that removes itself as soon as it's the
 * only usage of the associated node. This way it only increments the counter if the node is
 * actually executed.
 */
public class SurvivingCounterNode extends DynamicCounterNode implements Simplifiable, Virtualizable {

    @Input private ValueNode checkedValue;

    public SurvivingCounterNode(String name, long increment, boolean addContext, ValueNode checkedValue) {
        super(name, increment, addContext);
        this.checkedValue = checkedValue;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (checkedValue instanceof FloatingNode && checkedValue.usages().count() == 1) {
            tool.addToWorkList(checkedValue);
            ((StructuredGraph) graph()).removeFixed(this);
            // ((StructuredGraph) graph()).replaceFixedWithFixed(this, graph().add(new
            // DynamicCounterNode("non-surviving " + getName(), getIncrement(), isAddContext())));
        }
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(checkedValue);
        if (state != null && state.getState() == EscapeState.Virtual) {
            tool.delete();
        }
    }
}
