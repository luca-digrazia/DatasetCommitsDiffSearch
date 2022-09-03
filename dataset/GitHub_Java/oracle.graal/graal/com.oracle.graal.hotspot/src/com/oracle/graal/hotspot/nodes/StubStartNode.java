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

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_0;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_0;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.hotspot.stubs.Stub;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.StartNode;

/**
 * Start node for a {@link Stub}'s graph.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class StubStartNode extends StartNode {

    public static final NodeClass<StubStartNode> TYPE = NodeClass.create(StubStartNode.class);
    protected final Stub stub;

    public StubStartNode(Stub stub) {
        super(TYPE);
        this.stub = stub;
    }

    public Stub getStub() {
        return stub;
    }
}
