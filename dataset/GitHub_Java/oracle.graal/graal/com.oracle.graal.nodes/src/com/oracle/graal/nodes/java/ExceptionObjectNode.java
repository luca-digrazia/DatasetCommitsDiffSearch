/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.nodeinfo.InputType.Memory;
import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_10;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_8;

import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.compiler.common.type.TypeReference;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.BeginStateSplitNode;
import com.oracle.graal.nodes.InvokeWithExceptionNode;
import com.oracle.graal.nodes.KillingBeginNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.memory.MemoryCheckpoint;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * The entry to an exception handler with the exception coming from a call (as opposed to a local
 * throw instruction or implicit exception).
 */
@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_10, size = SIZE_8)
public final class ExceptionObjectNode extends BeginStateSplitNode implements Lowerable, MemoryCheckpoint.Single {
    public static final NodeClass<ExceptionObjectNode> TYPE = NodeClass.create(ExceptionObjectNode.class);

    public ExceptionObjectNode(MetaAccessProvider metaAccess) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(metaAccess.lookupJavaType(Throwable.class))));
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
            /*
             * Now the lowering to BeginNode+LoadExceptionNode can be performed, since no more
             * deopts can float in between the begin node and the load exception node.
             */
            LocationIdentity locationsKilledByInvoke = ((InvokeWithExceptionNode) predecessor()).getLocationIdentity();
            AbstractBeginNode entry = graph().add(new KillingBeginNode(locationsKilledByInvoke));
            LoadExceptionObjectNode loadException = graph().add(new LoadExceptionObjectNode(stamp()));

            loadException.setStateAfter(stateAfter());
            replaceAtUsages(InputType.Value, loadException);
            graph().replaceFixedWithFixed(this, entry);
            entry.graph().addAfterFixed(entry, loadException);

            loadException.lower(tool);
        }
    }

    @Override
    public boolean verify() {
        assertTrue(stateAfter() != null, "an exception handler needs a frame state");
        return super.verify();
    }
}
