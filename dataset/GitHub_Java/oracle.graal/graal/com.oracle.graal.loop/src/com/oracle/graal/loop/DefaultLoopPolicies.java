/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.loop;

import static com.oracle.graal.compiler.common.GraalOptions.LoopMaxUnswitch;
import static com.oracle.graal.compiler.common.GraalOptions.MaximumDesiredSize;
import static com.oracle.graal.compiler.common.GraalOptions.MinimumPeelProbability;

import java.util.List;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugCounter;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeBitMap;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.ControlSplitNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.LoopBeginNode;
import com.oracle.graal.nodes.MergeNode;
import com.oracle.graal.nodes.VirtualState;
import com.oracle.graal.nodes.VirtualState.VirtualClosure;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;
import com.oracle.graal.nodes.debug.ControlFlowAnchorNode;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.MetaAccessProvider;

public class DefaultLoopPolicies implements LoopPolicies {
    @Option(help = "", type = OptionType.Expert) public static final OptionValue<Integer> LoopUnswitchMaxIncrease = new OptionValue<>(500);
    @Option(help = "", type = OptionType.Expert) public static final OptionValue<Integer> LoopUnswitchTrivial = new OptionValue<>(10);
    @Option(help = "", type = OptionType.Expert) public static final OptionValue<Double> LoopUnswitchFrequencyBoost = new OptionValue<>(10.0);

    @Option(help = "", type = OptionType.Expert) public static final OptionValue<Integer> FullUnrollMaxNodes = new OptionValue<>(300);
    @Option(help = "", type = OptionType.Expert) public static final OptionValue<Integer> FullUnrollMaxIterations = new OptionValue<>(600);
    @Option(help = "", type = OptionType.Expert) public static final OptionValue<Integer> ExactFullUnrollMaxNodes = new OptionValue<>(1200);

    @Override
    public boolean shouldPeel(LoopEx loop, ControlFlowGraph cfg, MetaAccessProvider metaAccess) {
        LoopBeginNode loopBegin = loop.loopBegin();
        double entryProbability = cfg.blockFor(loopBegin.forwardEnd()).probability();
        if (entryProbability > MinimumPeelProbability.getValue() && loop.size() + loopBegin.graph().getNodeCount() < MaximumDesiredSize.getValue()) {
            // check whether we're allowed to peel this loop
            for (Node node : loop.inside().nodes()) {
                if (node instanceof ControlFlowAnchorNode) {
                    return false;
                }
                if (node instanceof FrameState) {
                    FrameState frameState = (FrameState) node;
                    if (frameState.bci == BytecodeFrame.AFTER_EXCEPTION_BCI || frameState.bci == BytecodeFrame.UNWIND_BCI) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean shouldFullUnroll(LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount()) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        long maxTrips = counted.constantMaxTripCount();
        int maxNodes = (counted.isExactTripCount() && counted.isConstantExactTripCount()) ? ExactFullUnrollMaxNodes.getValue() : FullUnrollMaxNodes.getValue();
        maxNodes = Math.min(maxNodes, Math.max(0, MaximumDesiredSize.getValue() - loop.loopBegin().graph().getNodeCount()));
        int size = Math.max(1, loop.size() - 1 - loop.loopBegin().phis().count());
        if (maxTrips <= FullUnrollMaxIterations.getValue() && size * (maxTrips - 1) <= maxNodes) {
            // check whether we're allowed to unroll this loop
            for (Node node : loop.inside().nodes()) {
                if (node instanceof ControlFlowAnchorNode) {
                    return false;
                }
                if (node instanceof FrameState) {
                    FrameState frameState = (FrameState) node;
                    if (frameState.bci == BytecodeFrame.AFTER_EXCEPTION_BCI || frameState.bci == BytecodeFrame.UNWIND_BCI) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean shouldTryUnswitch(LoopEx loop) {
        LoopBeginNode loopBegin = loop.loopBegin();
        double loopFrequency = loopBegin.loopFrequency();
        if (loopFrequency <= 1.0) {
            return false;
        }
        return loopBegin.unswitches() <= LoopMaxUnswitch.getValue();
    }

    private static final class CountingClosure implements VirtualClosure {
        int count;

        @Override
        public void apply(VirtualState node) {
            count++;
        }
    }

    private static class IsolatedInitialization {
        static final DebugCounter UNSWITCH_SPLIT_WITH_PHIS = Debug.counter("UnswitchSplitWithPhis");
    }

    @Override
    public boolean shouldUnswitch(LoopEx loop, List<ControlSplitNode> controlSplits) {
        int phis = 0;
        NodeBitMap branchNodes = loop.loopBegin().graph().createNodeBitMap();
        for (ControlSplitNode controlSplit : controlSplits) {
            for (Node successor : controlSplit.successors()) {
                AbstractBeginNode branch = (AbstractBeginNode) successor;
                // this may count twice because of fall-through in switches
                loop.nodesInLoopBranch(branchNodes, branch);
            }
            Block postDomBlock = loop.loopsData().getCFG().blockFor(controlSplit).getPostdominator();
            if (postDomBlock != null) {
                IsolatedInitialization.UNSWITCH_SPLIT_WITH_PHIS.increment();
                phis += ((MergeNode) postDomBlock.getBeginNode()).phis().count();
            }
        }
        int inBranchTotal = branchNodes.count();

        CountingClosure stateNodesCount = new CountingClosure();
        double loopFrequency = loop.loopBegin().loopFrequency();
        int maxDiff = LoopUnswitchTrivial.getValue() + (int) (LoopUnswitchFrequencyBoost.getValue() * (loopFrequency - 1.0 + phis));

        maxDiff = Math.min(maxDiff, LoopUnswitchMaxIncrease.getValue());
        int remainingGraphSpace = MaximumDesiredSize.getValue() - loop.loopBegin().graph().getNodeCount();
        maxDiff = Math.min(maxDiff, remainingGraphSpace);

        loop.loopBegin().stateAfter().applyToVirtual(stateNodesCount);
        int loopTotal = loop.size() - loop.loopBegin().phis().count() - stateNodesCount.count - 1;
        int actualDiff = loopTotal - inBranchTotal;

        Debug.log("shouldUnswitch(%s, %s) : delta=%d (%.2f%% inside of branches), max=%d, f=%.2f, phis=%d -> %b", loop, controlSplits, actualDiff, (double) (inBranchTotal) / loopTotal * 100, maxDiff,
                        loopFrequency, phis, actualDiff <= maxDiff);
        return actualDiff <= maxDiff;
    }

}
