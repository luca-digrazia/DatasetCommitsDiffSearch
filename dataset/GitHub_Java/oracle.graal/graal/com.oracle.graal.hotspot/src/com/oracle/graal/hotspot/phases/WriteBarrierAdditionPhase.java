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
package com.oracle.graal.hotspot.phases;

import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.Node;
import com.oracle.graal.hotspot.GraalHotSpotVMConfig;
import com.oracle.graal.hotspot.nodes.G1ArrayRangePostWriteBarrier;
import com.oracle.graal.hotspot.nodes.G1ArrayRangePreWriteBarrier;
import com.oracle.graal.hotspot.nodes.G1PostWriteBarrier;
import com.oracle.graal.hotspot.nodes.G1PreWriteBarrier;
import com.oracle.graal.hotspot.nodes.G1ReferentFieldReadBarrier;
import com.oracle.graal.hotspot.nodes.SerialArrayRangeWriteBarrier;
import com.oracle.graal.hotspot.nodes.SerialWriteBarrier;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.ArrayRangeWriteNode;
import com.oracle.graal.nodes.java.LoweredAtomicReadAndWriteNode;
import com.oracle.graal.nodes.java.LoweredCompareAndSwapNode;
import com.oracle.graal.nodes.memory.FixedAccessNode;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.memory.ReadNode;
import com.oracle.graal.nodes.memory.WriteNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.phases.Phase;

public class WriteBarrierAdditionPhase extends Phase {

    private GraalHotSpotVMConfig config;

    public WriteBarrierAdditionPhase(GraalHotSpotVMConfig config) {
        this.config = config;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof ReadNode) {
                addReadNodeBarriers((ReadNode) n, graph);
            } else if (n instanceof WriteNode) {
                addWriteNodeBarriers((WriteNode) n, graph);
            } else if (n instanceof LoweredAtomicReadAndWriteNode) {
                LoweredAtomicReadAndWriteNode loweredAtomicReadAndWriteNode = (LoweredAtomicReadAndWriteNode) n;
                addAtomicReadWriteNodeBarriers(loweredAtomicReadAndWriteNode, graph);
            } else if (n instanceof LoweredCompareAndSwapNode) {
                addCASBarriers((LoweredCompareAndSwapNode) n, graph);
            } else if (n instanceof ArrayRangeWriteNode) {
                ArrayRangeWriteNode node = (ArrayRangeWriteNode) n;
                if (node.isObjectArray()) {
                    addArrayRangeBarriers(node, graph);
                }
            }
        }
    }

    private void addReadNodeBarriers(ReadNode node, StructuredGraph graph) {
        if (node.getBarrierType() == BarrierType.PRECISE) {
            assert config.useG1GC;
            G1ReferentFieldReadBarrier barrier = graph.add(new G1ReferentFieldReadBarrier(node.getAddress(), node, false));
            graph.addAfterFixed(node, barrier);
        } else {
            assert node.getBarrierType() == BarrierType.NONE : "Non precise read barrier has been attached to read node.";
        }
    }

    protected static void addG1PreWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean doLoad, boolean nullCheck, StructuredGraph graph) {
        G1PreWriteBarrier preBarrier = graph.add(new G1PreWriteBarrier(address, value, doLoad, nullCheck));
        preBarrier.setStateBefore(node.stateBefore());
        node.setNullCheck(false);
        node.setStateBefore(null);
        graph.addBeforeFixed(node, preBarrier);
    }

    protected void addG1PostWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean precise, StructuredGraph graph) {
        final boolean alwaysNull = StampTool.isPointerAlwaysNull(value);
        graph.addAfterFixed(node, graph.add(new G1PostWriteBarrier(address, value, precise, alwaysNull)));
    }

    protected void addSerialPostWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean precise, StructuredGraph graph) {
        final boolean alwaysNull = StampTool.isPointerAlwaysNull(value);
        if (alwaysNull) {
            // Serial barrier isn't needed for null value
            return;
        }
        graph.addAfterFixed(node, graph.add(new SerialWriteBarrier(address, precise)));
    }

    private void addWriteNodeBarriers(WriteNode node, StructuredGraph graph) {
        BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case IMPRECISE:
            case PRECISE:
                boolean precise = barrierType == BarrierType.PRECISE;
                if (config.useG1GC) {
                    if (!node.isInitialization()) {
                        addG1PreWriteBarrier(node, node.getAddress(), null, true, node.getNullCheck(), graph);
                    }
                    addG1PostWriteBarrier(node, node.getAddress(), node.value(), precise, graph);
                } else {
                    addSerialPostWriteBarrier(node, node.getAddress(), node.value(), precise, graph);
                }
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    private void addAtomicReadWriteNodeBarriers(LoweredAtomicReadAndWriteNode node, StructuredGraph graph) {
        BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case IMPRECISE:
            case PRECISE:
                boolean precise = barrierType == BarrierType.PRECISE;
                if (config.useG1GC) {
                    addG1PreWriteBarrier(node, node.getAddress(), null, true, node.getNullCheck(), graph);
                    addG1PostWriteBarrier(node, node.getAddress(), node.getNewValue(), precise, graph);
                } else {
                    addSerialPostWriteBarrier(node, node.getAddress(), node.getNewValue(), precise, graph);
                }
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    private void addCASBarriers(LoweredCompareAndSwapNode node, StructuredGraph graph) {
        BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case IMPRECISE:
            case PRECISE:
                boolean precise = barrierType == BarrierType.PRECISE;
                if (config.useG1GC) {
                    addG1PreWriteBarrier(node, node.getAddress(), node.getExpectedValue(), false, false, graph);
                    addG1PostWriteBarrier(node, node.getAddress(), node.getNewValue(), precise, graph);
                } else {
                    addSerialPostWriteBarrier(node, node.getAddress(), node.getNewValue(), precise, graph);
                }
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    private void addArrayRangeBarriers(ArrayRangeWriteNode node, StructuredGraph graph) {
        if (config.useG1GC) {
            if (!node.isInitialization()) {
                G1ArrayRangePreWriteBarrier g1ArrayRangePreWriteBarrier = graph.add(new G1ArrayRangePreWriteBarrier(node.getArray(), node.getIndex(), node.getLength()));
                graph.addBeforeFixed(node, g1ArrayRangePreWriteBarrier);
            }
            G1ArrayRangePostWriteBarrier g1ArrayRangePostWriteBarrier = graph.add(new G1ArrayRangePostWriteBarrier(node.getArray(), node.getIndex(), node.getLength()));
            graph.addAfterFixed(node, g1ArrayRangePostWriteBarrier);
        } else {
            SerialArrayRangeWriteBarrier serialArrayRangeWriteBarrier = graph.add(new SerialArrayRangeWriteBarrier(node.getArray(), node.getIndex(), node.getLength()));
            graph.addAfterFixed(node, serialArrayRangeWriteBarrier);
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 25.0f;
    }
}
