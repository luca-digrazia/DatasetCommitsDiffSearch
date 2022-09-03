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
package com.oracle.graal.nodes.extended;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_2;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_1;

import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * Load of a value at a location specified as an absolute address.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public class UnsafeMemoryLoadNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<UnsafeMemoryLoadNode> TYPE = NodeClass.create(UnsafeMemoryLoadNode.class);

    @Input protected ValueNode address;
    protected final JavaKind kind;
    protected final LocationIdentity locationIdentity;

    public UnsafeMemoryLoadNode(ValueNode address, JavaKind kind, LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(kind.getStackKind()));
        this.address = address;
        this.kind = kind;
        this.locationIdentity = locationIdentity;
    }

    public ValueNode getAddress() {
        return address;
    }

    public JavaKind getKind() {
        return kind;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }
}
