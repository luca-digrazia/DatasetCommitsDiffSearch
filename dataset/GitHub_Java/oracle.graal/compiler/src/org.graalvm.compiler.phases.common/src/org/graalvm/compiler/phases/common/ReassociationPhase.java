/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;

/**
 * Rearrange {@link BinaryArithmeticNode#isAssociative() associative binary operations} for loop
 * invariants and constants.
 */
public class ReassociationPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        reassociateInvariants(graph);
        reassociateConstants(graph);
    }

    //@formatter:off
    /**
     * Re-associate loop invariant so that invariant parts of the expression can move outside of the
     * loop.
     *
     * For example:
     *     for (int i = 0; i < LENGTH; i++) {         for (int i = 0; i < LENGTH; i++) {
     *         arr[i] = (i * inv1) * inv2;       =>       arr[i] = i * (inv1 * inv2);
     *     }                                          }
     */
    //@formatter:on
    @SuppressWarnings("try")
    private static void reassociateInvariants(StructuredGraph graph) {
        DebugContext debug = graph.getDebug();
        LoopsData loopsData = new LoopsData(graph);
        int iterations = 0;
        try (DebugContext.Scope s = debug.scope("ReassociateInvariants")) {
            boolean changed = true;
            // Terminate the loop if there is no change or if the iteration is reached to the upper
            // bound.
            while (changed && iterations < 32) {
                changed = false;
                for (LoopEx loop : loopsData.loops()) {
                    changed |= loop.reassociateInvariants();
                }
                loopsData.deleteUnusedNodes();
                iterations++;
                debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Reassociation: after iteration %d", iterations);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    /**
     * Push constant down the expression tree like "(a + 1) + b" => "(a + b) + 1". It creates more
     * opportunities for optimizations like "(a + 1) + 2" => "(a + 3)", which has been implemented
     * in the {@linkplain CanonicalizerPhase CanonicalizerPhase}. To avoid some unexpected
     * regressions for loop invariants like "i + (inv + 1)" => "(i + inv) + 1", this re-association
     * is applied after {@linkplain ReassociationPhase#reassociateInvariants reassociateInvariants}
     * and only applied to expressions outside a loop.
     */
    @SuppressWarnings("try")
    private static void reassociateConstants(StructuredGraph graph) {
        LoopsData loopsData = new LoopsData(graph);
        NodeBitMap loopNodes = graph.createNodeBitMap();
        for (LoopEx loop : loopsData.loops()) {
            loopNodes.union(loop.whole().nodes());
        }

        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("ReassociateConstants")) {
            for (BinaryArithmeticNode<?> binary : graph.getNodes().filter(BinaryArithmeticNode.class)) {
                // Skip re-associations to loop variant expressions.
                if (!binary.isAssociative() || (!loopNodes.isNew(binary) && loopNodes.contains(binary))) {
                    continue;
                }
                ValueNode result = BinaryArithmeticNode.reassociateUnmatchedValues(binary, ValueNode.isConstantPredicate(), NodeView.DEFAULT);
                if (result != binary) {
                    if (!result.isAlive()) {
                        assert !result.isDeleted();
                        result = graph.addOrUniqueWithInputs(result);
                    }
                    if (debug.isLogEnabled()) {
                        debug.log("%s : Re-associated %s into %s", graph.method().format("%H::%n"), binary, result);
                    }
                    binary.replaceAtUsages(result);
                    GraphUtil.killWithUnusedFloatingInputs(binary);
                }
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }

    }
}
