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
package com.oracle.graal.hotspot.nodes.aot;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_3;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_3;

import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.iterators.NodeIterable;
import com.oracle.graal.hotspot.nodes.type.MethodCountersPointerStamp;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(cycles = CYCLES_3, size = SIZE_3)
public class LoadMethodCountersNode extends FloatingNode implements LIRLowerable {
    public static final NodeClass<LoadMethodCountersNode> TYPE = NodeClass.create(LoadMethodCountersNode.class);

    ResolvedJavaMethod method;

    public LoadMethodCountersNode(ResolvedJavaMethod method) {
        super(TYPE, MethodCountersPointerStamp.methodCountersNonNull());
        this.method = method;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public static NodeIterable<LoadMethodCountersNode> getLoadMethodCountersNodes(StructuredGraph graph) {
        return graph.getNodes().filter(LoadMethodCountersNode.class);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // TODO: With AOT we don't need this, as this node will be replaced.
        // Implement later when profiling is needed in the JIT mode.
        throw GraalError.unimplemented();
    }
}
