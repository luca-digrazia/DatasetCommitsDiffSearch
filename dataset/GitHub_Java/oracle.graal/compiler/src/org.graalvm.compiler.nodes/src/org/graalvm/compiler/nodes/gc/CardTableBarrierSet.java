/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.nodes.gc;

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ArrayRangeWrite;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.HeapAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

public class CardTableBarrierSet implements BarrierSet {
    private final boolean useDeferredInitBarriers;

    public CardTableBarrierSet(boolean useDeferredInitBarriers) {
        this.useDeferredInitBarriers = useDeferredInitBarriers;
    }

    @Override
    public void addBarriers(FixedAccessNode n) {
        if (n instanceof ReadNode) {
            // nothing to do
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            addWriteBarrier(write, write.value());
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            addWriteBarrier(atomic, atomic.getNewValue());
        } else if (n instanceof AbstractCompareAndSwapNode) {
            AbstractCompareAndSwapNode cmpSwap = (AbstractCompareAndSwapNode) n;
            addWriteBarrier(cmpSwap, cmpSwap.getNewValue());
        } else if (n instanceof ArrayRangeWrite) {
            addArrayRangeBarriers((ArrayRangeWrite) n);
        } else {
            GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
        }
    }

    public boolean needsBarrier(FixedAccessNode n) {
        if (n instanceof ReadNode) {
            return false;
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            return needsWriteBarrier(write, write.value());
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            return needsWriteBarrier(atomic, atomic.getNewValue());
        } else if (n instanceof AbstractCompareAndSwapNode) {
            AbstractCompareAndSwapNode cmpSwap = (AbstractCompareAndSwapNode) n;
            return needsWriteBarrier(cmpSwap, cmpSwap.getNewValue());
        } else if (n instanceof ArrayRangeWrite) {
            return needsWriteBarrier((ArrayRangeWrite) n);
        } else {
            GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
            return false;
        }
    }

    public boolean hasBarrier(FixedAccessNode n) {
        if (n instanceof ReadNode) {
            return false;
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            return hasWriteBarrier(write);
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            return hasWriteBarrier(atomic);
        } else if (n instanceof AbstractCompareAndSwapNode) {
            AbstractCompareAndSwapNode cmpSwap = (AbstractCompareAndSwapNode) n;
            return hasWriteBarrier(cmpSwap);
        } else if (n instanceof ArrayRangeWrite) {
            return hasWriteBarrier((ArrayRangeWrite) n);
        } else {
            GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
            return false;
        }
    }

    public boolean isMatchingBarrier(FixedAccessNode n, WriteBarrier barrier) {
        if (n instanceof ReadNode) {
            return false;
        } else if (n instanceof WriteNode || n instanceof LoweredAtomicReadAndWriteNode || n instanceof AbstractCompareAndSwapNode || n instanceof ArrayRangeWrite) {
            return barrier instanceof SerialWriteBarrier && matches(n, (SerialWriteBarrier) barrier);
        } else {
            throw GraalError.shouldNotReachHere("Unexpected node: " + n.getClass());
        }
    }

    public void addArrayRangeBarriers(ArrayRangeWrite write) {
        if (needsWriteBarrier(write)) {
            StructuredGraph graph = write.asNode().graph();
            SerialArrayRangeWriteBarrier serialArrayRangeWriteBarrier = graph.add(new SerialArrayRangeWriteBarrier(write.getAddress(), write.getLength(), write.getElementStride()));
            graph.addAfterFixed(write.asNode(), serialArrayRangeWriteBarrier);
        }
    }

    private void addWriteBarrier(FixedAccessNode node, ValueNode writtenValue) {
        if (needsWriteBarrier(node, writtenValue)) {
            addSerialPostWriteBarrier(node, node.getAddress(), node.graph());
        }
    }

    public boolean needsWriteBarrier(FixedAccessNode node, ValueNode writtenValue) {
        assert !(node instanceof ArrayRangeWrite);
        HeapAccess.BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                return false;
            case FIELD:
            case ARRAY:
            case UNKNOWN:
                return isNonNullObjectValue(writtenValue) && !isDeferredInit(node);
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    public static boolean needsWriteBarrier(ArrayRangeWrite write) {
        return write.writesObjectArray();
    }

    private static boolean hasWriteBarrier(FixedAccessNode node) {
        return node.next() instanceof SerialWriteBarrier && matches(node, (SerialWriteBarrier) node.next());
    }

    private static boolean hasWriteBarrier(ArrayRangeWrite write) {
        FixedAccessNode node = write.asNode();
        return node.next() instanceof SerialArrayRangeWriteBarrier && matches(write, (SerialArrayRangeWriteBarrier) node.next());
    }

    private static void addSerialPostWriteBarrier(FixedAccessNode node, AddressNode address, StructuredGraph graph) {
        boolean precise = node.getBarrierType() != HeapAccess.BarrierType.FIELD;
        graph.addAfterFixed(node, graph.add(new SerialWriteBarrier(address, precise)));
    }

    private static boolean isNonNullObjectValue(ValueNode value) {
        return value.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp && !StampTool.isPointerAlwaysNull(value);
    }

    private boolean isDeferredInit(FixedAccessNode node) {
        return node.getLocationIdentity().isInit() && useDeferredInitBarriers;
    }

    private static boolean matches(FixedAccessNode node, SerialWriteBarrier barrier) {
        if (!barrier.usePrecise()) {
            if (barrier.getAddress() instanceof OffsetAddressNode && node.getAddress() instanceof OffsetAddressNode) {
                return GraphUtil.unproxify(((OffsetAddressNode) barrier.getAddress()).getBase()) == GraphUtil.unproxify(((OffsetAddressNode) node.getAddress()).getBase());
            }
        }
        return barrier.getAddress() == node.getAddress();
    }

    private static boolean matches(ArrayRangeWrite node, SerialArrayRangeWriteBarrier barrier) {
        return barrier.getAddress() == node.getAddress() && node.getLength() == barrier.getLength() && node.getElementStride() == barrier.getElementStride();
    }
}
