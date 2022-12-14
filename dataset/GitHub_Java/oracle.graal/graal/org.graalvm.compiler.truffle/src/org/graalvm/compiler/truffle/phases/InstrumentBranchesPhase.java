/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.phases;

import com.oracle.truffle.api.TruffleOptions;
import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;

/**
 * Instruments {@link IfNode}s in the graph, by adding execution counters to the true and the false
 * branch of each {@link IfNode}. If this phase is enabled, the runtime outputs a summary of all the
 * compiled {@link IfNode}s and the execution count of their branches, when the program exits.
 *
 * The phase is enabled with the following flag:
 *
 * <pre>
 * -Dgraal.TruffleInstrumentBranches
 * </pre>
 *
 * The phase can be configured to only instrument the {@link IfNode}s in specific methods, by
 * providing the following method filter flag:
 *
 * <pre>
 * -Dgraal.TruffleInstrumentBranchesFilter
 * </pre>
 *
 * The flag:
 *
 * <pre>
 * -Dgraal.TruffleInstrumentBranchesPerInlineSite
 * </pre>
 *
 * decides whether to treat different inlining sites separately when tracking the execution counts
 * of an {@link IfNode}.
 */
public class InstrumentBranchesPhase extends InstrumentPhase {
    public InstrumentBranchesPhase() {
    }

    @Override
    protected void instrumentGraph(StructuredGraph graph, HighTierContext context, JavaConstant tableConstant) {
        for (IfNode n : graph.getNodes().filter(IfNode.class)) {
            Instrumentation.Point p = getOrCreatePoint(methodFilter, n);
            if (p != null) {
                insertCounter(graph, context, tableConstant, n.trueSuccessor(), p.slotIndex(0));
                insertCounter(graph, context, tableConstant, n.falseSuccessor(), p.slotIndex(1));
            }
        }
    }

    @Override
    protected int instrumentationPointSlotCount() {
        return 2;
    }

    @Override
    protected String instrumentationFilter() {
        return TruffleCompilerOptions.TruffleInstrumentBranchesFilter.getValue();
    }

    @Override
    protected boolean instrumentPerInlineSite() {
        return TruffleCompilerOptions.TruffleInstrumentBranchesPerInlineSite.getValue();
    }

    @Override
    protected Instrumentation.Point createPoint(int id, int startIndex, Node n) {
        return new IfPoint(id, startIndex, n.getNodeSourcePosition(), TruffleCompilerOptions.TruffleInstrumentBranchesPretty.getValue() && TruffleCompilerOptions.TruffleInstrumentBranchesPerInlineSite.getValue());
    }

    public enum BranchState {
        NONE,
        IF,
        ELSE,
        BOTH;

        public static BranchState from(boolean ifVisited, boolean elseVisited) {
            if (ifVisited && elseVisited) {
                return BOTH;
            } else if (ifVisited && !elseVisited) {
                return IF;
            } else if (!ifVisited && elseVisited) {
                return ELSE;
            } else {
                return NONE;
            }
        }
    }

    public static class IfPoint extends InstrumentPhase.Instrumentation.Point {
        IfPoint(int id, int rawIndex, NodeSourcePosition position, boolean prettify) {
            super(id, rawIndex, position, prettify);
        }

        @Override
        public int slotCount() {
            return 2;
        }

        public long ifVisits() {
            return ACCESS_TABLE[rawIndex];
        }

        public long elseVisits() {
            return ACCESS_TABLE[rawIndex + 1];
        }

        public BranchState getBranchState() {
            return BranchState.from(ifVisits() > 0, elseVisits() > 0);
        }

        public String getCounts() {
            return "if=" + ifVisits() + "#, else=" + elseVisits() + "#";
        }

        @Override
        public long getHotness() {
            return ifVisits() + elseVisits();
        }

        @Override
        public String toString() {
            return "[" + id + "] state = " + getBranchState() + "(" + getCounts() + ")";
        }
    }
}
