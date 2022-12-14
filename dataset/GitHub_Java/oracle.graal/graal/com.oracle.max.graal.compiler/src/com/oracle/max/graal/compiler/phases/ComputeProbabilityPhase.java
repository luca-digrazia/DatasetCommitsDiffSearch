/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.phases.EscapeAnalysisPhase.MergeableState;
import com.oracle.max.graal.compiler.phases.EscapeAnalysisPhase.PostOrderNodeIterator;
import com.oracle.max.graal.graph.*;

public class ComputeProbabilityPhase extends Phase {
    private static final double EPSILON = 1d / Integer.MAX_VALUE;

    /*
     * The computation of absolute probabilities works in three steps:
     *
     * - The first step, "PropagateProbability", traverses the graph in post order (merges after their ends, ...) and keeps track of the "probability state".
     *   Whenever it encounters a ControlSplit it uses the splits probability information to divide the probability upon the successors.
     *   Whenever it encounters an Invoke it assumes that the exception edge is unlikely and propagates the whole probability to the normal successor.
     *   Whenever it encounters a Merge it sums up the probability of all predecessors.
     *   It also maintains a set of active loops (whose LoopBegin has been visited) and builds def/use information for the second step.
     *
     * - The third step propagates the loop frequencies and multiplies each FixedNode's probability with its loop frequency.
     *
     *   TODO: add exception probability information to Invokes
     */

    @Override
    protected void run(Graph graph) {
        new PropagateProbability((FixedNode) graph.start().next()).apply();
        GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved() && GraalOptions.TraceProbability) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After PropagateProbability", graph, true, false));
        }
        computeLoopFactors();
        if (compilation.compiler.isObserved() && GraalOptions.TraceProbability) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After computeLoopFactors", graph, true, false));
        }
        new PropagateLoopFrequency((FixedNode) graph.start().next()).apply();
    }

    private void computeLoopFactors() {
        if (GraalOptions.TraceProbability) {
            for (LoopInfo info : loopInfos) {
                TTY.println("\nLoop " + info.loopBegin);
                TTY.print("  requires: ");
                for (LoopInfo r : info.requires) {
                    TTY.print(r.loopBegin + " ");
                }
                TTY.println();
            }
        }
        for (LoopInfo info : loopInfos) {
            double frequency = info.loopFrequency();
            assert frequency != -1;
        }
    }

    private static boolean isRelativeProbability(double prob) {
        // 1.01 to allow for some rounding errors
        return prob >= 0 && prob <= 1.01;
    }

    public static class LoopInfo {
        public final LoopBegin loopBegin;

        public final Set<LoopInfo> requires = new HashSet<LoopInfo>(4);

        private double loopFrequency = -1;
        public boolean ended = false;

        public LoopInfo(LoopBegin loopBegin) {
            this.loopBegin = loopBegin;
        }

        public double loopFrequency() {
            if (loopFrequency == -1 && ended) {
                double factor = 1;
                for (LoopInfo required : requires) {
                    double t = required.loopFrequency();
                    if (t == -1) {
                        return -1;
                    }
                    factor *= t;
                }
                double d = loopBegin.loopEnd().probability() * factor;
                if (d < EPSILON) {
                    d = EPSILON;
                } else if (d > loopBegin.probability() - EPSILON) {
                    d = loopBegin.probability() - EPSILON;
                }
                loopFrequency = loopBegin.probability() / (loopBegin.probability() - d);
                loopBegin.setLoopFrequency(loopFrequency);
//                TTY.println("computed loop Frequency %f for %s", loopFrequency, loopBegin);
            }
            return loopFrequency;
        }
    }

    public Set<LoopInfo> loopInfos = new HashSet<LoopInfo>();
    public Map<Merge, Set<LoopInfo>> mergeLoops = new HashMap<Merge, Set<LoopInfo>>();

    private class Probability implements MergeableState<Probability> {
        public double probability;
        public HashSet<LoopInfo> loops;
        public LoopInfo loopInfo;

        public Probability(double probability, HashSet<LoopInfo> loops) {
            this.probability = probability;
            this.loops = new HashSet<LoopInfo>(4);
            if (loops != null) {
                this.loops.addAll(loops);
            }
        }

        @Override
        public Probability clone() {
            return new Probability(probability, loops);
        }

        @Override
        public boolean merge(Merge merge, Collection<Probability> withStates) {
            if (merge.endCount() > 1) {
                HashSet<LoopInfo> intersection = new HashSet<LoopInfo>(loops);
                for (Probability other : withStates) {
                    intersection.retainAll(other.loops);
                }
                for (LoopInfo info : loops) {
                    if (!intersection.contains(info)) {
    //                    TTY.println("probability for %s at %s", info.loopBegin, merge);
                        double loopFrequency = info.loopFrequency();
                        if (loopFrequency == -1) {
    //                        TTY.println("re-queued " + merge);
                            return false;
                        }
                        probability *= loopFrequency;
                    }
                }
                for (Probability other : withStates) {
                    double prob = other.probability;
                    for (LoopInfo info : other.loops) {
                        if (!intersection.contains(info)) {
    //                        TTY.println("probability for %s at %s", info.loopBegin, merge);
                            double loopFrequency = info.loopFrequency();
                            if (loopFrequency == -1) {
    //                            TTY.println("re-queued " + merge);
                                return false;
                            }
                            prob *= loopFrequency;
                        }
                    }
                    probability += prob;
                }
                loops = intersection;
    //            TTY.println("merged " + merge);
                mergeLoops.put(merge, new HashSet<LoopInfo>(intersection));
                assert isRelativeProbability(probability) : probability;
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBegin loopBegin) {
            loopInfo = new LoopInfo(loopBegin);
            loopInfos.add(loopInfo);
            loops.add(loopInfo);
        }

        @Override
        public void loopEnd(LoopEnd loopEnd, Probability loopEndState) {
            assert loopInfo != null;
            for (LoopInfo innerLoop : loopEndState.loops) {
                if (innerLoop != loopInfo && !loops.contains(innerLoop)) {
                    loopInfo.requires.add(innerLoop);
                }
            }
            loopInfo.ended = true;
        }

        @Override
        public void afterSplit(FixedNode node) {
            assert node.predecessors().size() == 1;
            Node pred = node.predecessors().get(0);
            if (pred instanceof Invoke) {
                Invoke x = (Invoke) pred;
                if (x.next() != node) {
                    probability = 0;
                }
            } else {
                assert pred instanceof ControlSplit;
                ControlSplit x = (ControlSplit) pred;
                double sum = 0;
                for (int i = 0; i < x.blockSuccessorCount(); i++) {
                    if (x.blockSuccessor(i) == node) {
                        sum += x.probability(i);
                    }
                }
                probability *= sum;
            }
        }
    }

    private class PropagateProbability extends PostOrderNodeIterator<Probability> {

        public PropagateProbability(FixedNode start) {
            super(start, new Probability(1d, null));
        }

        @Override
        protected void node(FixedNode node) {
//            TTY.println(" -- %7.5f %s", state.probability, node);
            node.setProbability(state.probability);
        }
    }

    private class LoopCount implements MergeableState<LoopCount> {
        public double count;

        public LoopCount(double count) {
            this.count = count;
        }

        @Override
        public LoopCount clone() {
            return new LoopCount(count);
        }

        @Override
        public boolean merge(Merge merge, Collection<LoopCount> withStates) {
            assert merge.endCount() == withStates.size() + 1;
            if (merge.endCount() > 1) {
                Set<LoopInfo> loops = mergeLoops.get(merge);
//                TTY.println("merging count for %s: %d ends, %d loops", merge, merge.endCount(), loops.size());
                assert loops != null;
                double countProd = 1;
                for (LoopInfo loop : loops) {
                    countProd *= loop.loopFrequency();
                }
                count = countProd;
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBegin loopBegin) {
            count *= loopBegin.loopFrequency();
        }

        @Override
        public void loopEnd(LoopEnd loopEnd, LoopCount loopEndState) {
            // nothing to do...
        }

        @Override
        public void afterSplit(FixedNode node) {
            // nothing to do...
        }
    }

    private class PropagateLoopFrequency extends PostOrderNodeIterator<LoopCount> {

        public PropagateLoopFrequency(FixedNode start) {
            super(start, new LoopCount(1d));
        }

        @Override
        protected void node(FixedNode node) {
            node.setProbability(node.probability() * state.count);
        }
    }
}
