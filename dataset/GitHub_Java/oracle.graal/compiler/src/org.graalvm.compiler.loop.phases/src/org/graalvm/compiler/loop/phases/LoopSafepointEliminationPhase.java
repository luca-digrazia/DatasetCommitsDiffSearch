/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.loop.phases;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.ForeignCall;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class LoopSafepointEliminationPhase extends BasePhase<MidTierContext> {

    /**
     * To be implemented by subclasses to perform additional checks. Returns <code>true</code> if
     * the safepoint was also disabled in subclasses and we therefore don't need to continue
     * traversing.
     */
    @SuppressWarnings("unused")
    protected boolean onCallInLoop(LoopEndNode loopEnd, FixedNode currentCallNode) {
        return true;
    }

    /**
     * To be implemented by subclasses to compute additional fields.
     */
    @SuppressWarnings("unused")
    protected void onSafepointDisabledLoopBegin(LoopEx loop) {
    }

    private static boolean loopIsIn32BitRange(LoopEx loop) {
        if (loop.counted().getStamp().getBits() <= 32) {
            return true;
        }
        Stamp s = loop.counted().getLimit().stamp(NodeView.DEFAULT);
        if (s instanceof IntegerStamp) {
            IntegerStamp i = (IntegerStamp) s;
            final long lowerBound = i.lowerBound();
            final long upperBound = i.upperBound();
            if (lowerBound >= Integer.MIN_VALUE && upperBound <= Integer.MAX_VALUE) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected final void run(StructuredGraph graph, MidTierContext context) {
        LoopsData loops = context.getLoopsDataProvider().getLoopsData(graph);
        loops.detectedCountedLoops();
        for (LoopEx loop : loops.countedLoops()) {
            if (loop.loop().getChildren().isEmpty() && (loop.loopBegin().isPreLoop() || loop.loopBegin().isPostLoop() || loopIsIn32BitRange(loop))) {
                boolean hasSafepoint = false;
                for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                    hasSafepoint |= loopEnd.canSafepoint();
                }
                if (hasSafepoint) {
                    if (!loop.counted().counterNeverOverflows()) {
                        // Counter can overflow, need to create a guard.
                        if (context.getOptimisticOptimizations().useLoopLimitChecks(graph.getOptions()) && graph.getGuardsStage().allowsFloatingGuards()) {
                            loop.counted().createOverFlowGuard();
                        } else {
                            // Cannot disable this safepoint, because the loop could overflow.
                            continue;
                        }
                    }
                    loop.loopBegin().disableSafepoint();
                    onSafepointDisabledLoopBegin(loop);
                }
            }
        }
        for (LoopEx loop : loops.loops()) {
            for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                Block b = loops.getCFG().blockFor(loopEnd);
                blocks: while (b != loop.loop().getHeader()) {
                    assert b != null;
                    for (FixedNode node : b.getNodes()) {
                        boolean canDisableSafepoint = false;
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            ResolvedJavaMethod method = invoke.getTargetMethod();
                            canDisableSafepoint = context.getMetaAccessExtensionProvider().isGuaranteedSafepoint(method, invoke.getInvokeKind().isDirect());
                        } else if (node instanceof ForeignCall) {
                            canDisableSafepoint = ((ForeignCall) node).isGuaranteedSafepoint();
                        }
                        boolean disabledInSubclass = onCallInLoop(loopEnd, node);
                        if (canDisableSafepoint) {
                            loopEnd.disableSafepoint();

                            // we can only stop if subclasses also say we can stop iterating blocks
                            if (disabledInSubclass) {
                                break blocks;
                            }
                        }
                    }
                    b = b.getDominator();
                }
            }
        }
        loops.deleteUnusedNodes();
    }

}
