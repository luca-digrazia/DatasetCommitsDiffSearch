/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import java.util.*;
import java.util.stream.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.graph.*;

public class ComputeLoopFrequenciesClosure extends ReentrantNodeIterator.NodeIteratorClosure<Double> {

    private static final double EPSILON = Double.MIN_NORMAL;
    private static final ComputeLoopFrequenciesClosure INSTANCE = new ComputeLoopFrequenciesClosure();

    private ComputeLoopFrequenciesClosure() {
        // nothing to do
    }

    @Override
    protected Double processNode(FixedNode node, Double currentState) {
        // normal nodes never change the probability of a path
        return currentState;
    }

    @Override
    protected Double merge(MergeNode merge, List<Double> states) {
        // a merge has the sum of all predecessor probabilities
        return states.stream().collect(Collectors.summingDouble(d -> d));
    }

    @Override
    protected Double afterSplit(BeginNode node, Double oldState) {
        // a control split splits up the probability
        ControlSplitNode split = (ControlSplitNode) node.predecessor();
        return oldState * split.probability(node);
    }

    @Override
    protected Map<LoopExitNode, Double> processLoop(LoopBeginNode loop, Double initialState) {
        Map<LoopExitNode, Double> exitStates = ReentrantNodeIterator.processLoop(this, loop, 1D).exitStates;

        double exitProbability = exitStates.values().stream().mapToDouble(d -> d).sum();
        assert exitProbability <= 1D && exitProbability >= 0D;
        if (exitProbability < EPSILON) {
            exitProbability = EPSILON;
        }
        double loopFrequency = 1D / exitProbability;
        loop.setLoopFrequency(loopFrequency);

        double adjustmentFactor = initialState * loopFrequency;
        exitStates.replaceAll((exitNode, probability) -> multiplySaturate(probability, adjustmentFactor));

        return exitStates;
    }

    /**
     * Multiplies a and b and saturates the result to 1/{@link Double#MIN_NORMAL}.
     *
     * @return a times b saturated to 1/{@link Double#MIN_NORMAL}
     */
    public static double multiplySaturate(double a, double b) {
        double r = a * b;
        if (r > 1 / Double.MIN_NORMAL) {
            return 1 / Double.MIN_NORMAL;
        }
        return r;
    }

    /**
     * Computes the frequencies of all loops in the given graph. This is done by performing a
     * reverse postorder iteration and computing the probability of all fixed nodes. The combined
     * probability of all exits of a loop can be used to compute the loop's expected frequency.
     */
    public static void compute(StructuredGraph graph) {
        if (graph.hasLoops()) {
            ReentrantNodeIterator.apply(INSTANCE, graph.start(), 1D);
        }
    }

}
