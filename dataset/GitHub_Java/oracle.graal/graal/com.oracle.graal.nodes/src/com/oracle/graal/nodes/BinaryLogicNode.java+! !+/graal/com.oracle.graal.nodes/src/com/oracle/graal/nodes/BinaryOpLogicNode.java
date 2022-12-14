/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

public abstract class BinaryLogicNode extends LogicNode implements LIRLowerable, MemoryArithmeticLIRLowerable {

    @Input private ValueNode x;
    @Input private ValueNode y;

    public ValueNode x() {
        return x;
    }

    public ValueNode y() {
        return y;
    }

    protected void setX(ValueNode x) {
        updateUsages(this.x, x);
        this.x = x;
    }

    protected void setY(ValueNode y) {
        updateUsages(this.y, y);
        this.y = y;
    }

    public BinaryLogicNode(ValueNode x, ValueNode y) {
        assert x != null && y != null && x.getKind() == y.getKind();
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean verify() {
        assertTrue(x.stamp().isCompatible(y.stamp()), "stamps not compatible: %s, %s", x.stamp(), y.stamp());
        return super.verify();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
    }

    @Override
    public boolean generate(MemoryArithmeticLIRLowerer gen, Access access) {
        return false;
    }
}
