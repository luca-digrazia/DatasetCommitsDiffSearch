/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Store of a value at a location specified as an offset relative to an object. No null check is
 * performed before the store.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class RawStoreNode extends UnsafeAccessNode implements StateSplit, Lowerable, Virtualizable, MemoryCheckpoint.Single {

    public static final NodeClass<RawStoreNode> TYPE = NodeClass.create(RawStoreNode.class);
    @Input ValueNode value;
    @OptionalInput(State) FrameState stateAfter;
    private final boolean needsBarrier;

    public RawStoreNode(ValueNode object, ValueNode offset, ValueNode value, JavaKind accessKind, LocationIdentity locationIdentity) {
        this(object, offset, value, accessKind, locationIdentity, true);
    }

    public RawStoreNode(ValueNode object, ValueNode offset, ValueNode value, JavaKind accessKind, LocationIdentity locationIdentity, boolean needsBarrier) {
        this(object, offset, value, accessKind, locationIdentity, needsBarrier, null, false);
    }

    public RawStoreNode(ValueNode object, ValueNode offset, ValueNode value, JavaKind accessKind, LocationIdentity locationIdentity, boolean needsBarrier, FrameState stateAfter,
                    boolean forceAnyLocation) {
        super(TYPE, StampFactory.forVoid(), object, offset, accessKind, locationIdentity, forceAnyLocation);
        this.value = value;
        this.needsBarrier = needsBarrier;
        this.stateAfter = stateAfter;
        assert accessKind != JavaKind.Void && accessKind != JavaKind.Illegal;
    }

    @NodeIntrinsic
    public static native Object storeObject(Object object, long offset, Object value, @ConstantNodeParameter JavaKind kind, @ConstantNodeParameter LocationIdentity locationIdentity,
                    @ConstantNodeParameter boolean needsBarrier);

    @NodeIntrinsic
    public static native Object storeChar(Object object, long offset, char value, @ConstantNodeParameter JavaKind kind, @ConstantNodeParameter LocationIdentity locationIdentity);

    public boolean needsBarrier() {
        return needsBarrier;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            ValueNode indexValue = tool.getAlias(offset());
            if (indexValue.isConstant()) {
                long off = indexValue.asJavaConstant().asLong();
                int entryIndex = virtual.entryIndexForOffset(off, accessKind());
                if (entryIndex != -1) {
                    JavaKind entryKind = virtual.entryKind(entryIndex);
                    boolean canVirtualize = entryKind == accessKind() || entryKind == accessKind().getStackKind();
                    if (!canVirtualize) {
                        /*
                         * Special case: If the entryKind is long, allow arbitrary kinds as long as
                         * a value of the same kind is already there. This can only happen if some
                         * other node initialized the entry with a value of a different kind. One
                         * example where this happens is the Truffle NewFrameNode.
                         */
                        ValueNode entry = tool.getEntry(virtual, entryIndex);
                        if (entryKind == JavaKind.Long && entry.getStackKind() == value.getStackKind()) {
                            canVirtualize = true;
                        }
                    }
                    if (canVirtualize) {
                        tool.setVirtualEntry(virtual, entryIndex, value(), true);
                        tool.delete();
                    } else {
                        /*
                         * Special case: Allow storing a single long or double value into two
                         * consecutive int slots.
                         */
                        if ((accessKind() == JavaKind.Long || accessKind() == JavaKind.Double) && entryKind == JavaKind.Int) {
                            int nextIndex = virtual.entryIndexForOffset(off + 4, entryKind);
                            if (nextIndex != -1) {
                                JavaKind nextKind = virtual.entryKind(nextIndex);
                                if (nextKind == JavaKind.Int) {
                                    tool.setVirtualEntry(virtual, entryIndex, value(), true);
                                    tool.setVirtualEntry(virtual, nextIndex, ConstantNode.forConstant(JavaConstant.forIllegal(), tool.getMetaAccessProvider(), graph()), true);
                                    tool.delete();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected ValueNode cloneAsFieldAccess(Assumptions assumptions, ResolvedJavaField field) {
        return new StoreFieldNode(object(), field, value(), stateAfter());
    }

    @Override
    protected ValueNode cloneAsArrayAccess(ValueNode location, LocationIdentity identity) {
        return new RawStoreNode(object(), location, value, accessKind(), identity, needsBarrier, stateAfter(), isAnyLocationForced());
    }

    public FrameState getState() {
        return stateAfter;
    }
}
