/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.analysis.liveness;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.analysis.DepthFirstBlockIterator;
import com.oracle.truffle.espresso.analysis.GraphBuilder;
import com.oracle.truffle.espresso.analysis.Util;
import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.analysis.liveness.actions.MultiAction;
import com.oracle.truffle.espresso.analysis.liveness.actions.NullOutAction;
import com.oracle.truffle.espresso.analysis.liveness.actions.SelectEdgeAction;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.perf.TimerCollection;

public class LivenessAnalysis {

    public static final DebugTimer LIVENESS_TIMER = DebugTimer.create("liveness");
    public static final DebugTimer BUILDER_TIMER = DebugTimer.create("builder");
    public static final DebugTimer LOADSTORE_TIMER = DebugTimer.create("loadStore");
    public static final DebugTimer STATE_TIMER = DebugTimer.create("state");
    public static final DebugTimer PROPAGATE_TIMER = DebugTimer.create("propagation");
    public static final DebugTimer ACTION_TIMER = DebugTimer.create("action");

    public static final LivenessAnalysis NO_ANALYSIS = new LivenessAnalysis() {
        @Override
        public void performPostBCI(VirtualFrame frame, int bci, BytecodeNode node) {
        }

        @Override
        public void performOnEdge(VirtualFrame frame, int bci, int nextBci, BytecodeNode node) {
        }

        @Override
        public void onStart(VirtualFrame frame, BytecodeNode node) {
        }
    };

    /**
     * Contains 2 entries per BCI: the action to perform on entering the BCI (for nulling out locals
     * when jumping into a block), and one for the action to perform after executing the bytecode
     * (/ex: Nulling out a local once it has been loaded and no other load requires it).
     */
    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final LocalVariableAction[] result;
    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final EdgeAction[] edge;
    private final LocalVariableAction onStart;

    private final boolean compiledCodeOnly;

    private boolean compiledCodeCheck() {
        return !compiledCodeOnly || CompilerDirectives.inCompiledCode();
    }

    public void performOnEdge(VirtualFrame frame, int bci, int nextBci, BytecodeNode node) {
        if (compiledCodeCheck()) {
            if (edge != null && edge[nextBci] != null) {
                edge[nextBci].onEdge(frame, bci, node);
            }
        }
    }

    public void onStart(VirtualFrame frame, BytecodeNode node) {
        if (compiledCodeCheck()) {
            if (onStart != null) {
                onStart.execute(frame, node);
            }
        }
    }

    public void performPostBCI(VirtualFrame frame, int bci, BytecodeNode node) {
        if (compiledCodeCheck()) {
            if (result != null && result[bci] != null) {
                result[bci].execute(frame, node);
            }
        }
    }

    @SuppressWarnings("try")
    public static LivenessAnalysis analyze(Method method) {
        if (method.getContext().livenessAnalysisMode == EspressoOptions.LivenessAnalysisMode.DISABLED) {
            return NO_ANALYSIS;
        }
        TimerCollection scope = method.getContext().getTimers();
        try (DebugCloseable liveness = LIVENESS_TIMER.scope(scope)) {
            Graph<? extends LinkedBlock> graph;
            try (DebugCloseable builder = BUILDER_TIMER.scope(scope)) {
                graph = GraphBuilder.build(method);
            }

            // Transform the graph into a more manageable graph consisting of only the history of
            // load/stores.
            LoadStoreFinder loadStoreClosure;
            try (DebugCloseable loadStore = LOADSTORE_TIMER.scope(scope)) {
                loadStoreClosure = new LoadStoreFinder(graph, method);
                loadStoreClosure.analyze();
            }

            // Computes the entry/end live sets for each variable for each block.
            BlockBoundaryFinder blockBoundaryFinder;
            try (DebugCloseable boundary = STATE_TIMER.scope(scope)) {
                blockBoundaryFinder = new BlockBoundaryFinder(method, loadStoreClosure.result());
                DepthFirstBlockIterator.analyze(method, graph, blockBoundaryFinder);
            }

            try (DebugCloseable propagation = PROPAGATE_TIMER.scope(scope)) {
                // Forces loop ends to inherit the loop entry state, and propagates the changes.
                LoopPropagatorClosure loopPropagation = new LoopPropagatorClosure(graph, blockBoundaryFinder.result());
                while (loopPropagation.process(graph)) {
                    /*
                     * This loop should iterate at MOST exactly the maximum number of nested loops
                     * in the method.
                     *
                     * The reasoning is the following:
                     *
                     * - The only reason a new iteration is required is when a loop entry's state
                     * gets modified by the previous iteration.
                     *
                     * - This can happen only if a new live variable gets propagated from an outer
                     * loop.
                     *
                     * - Which means that we do not need to re-propagate the state of the outermost
                     * loop.
                     */
                }
            }

            // Using the live sets and history, build a set of action for each bci, such that it
            // frees as early as possible each dead local.
            try (DebugCloseable actionFinder = ACTION_TIMER.scope(scope)) {
                Builder builder = new Builder(graph, method, blockBoundaryFinder.result());
                builder.build();
                boolean compiledCodeOnly = method.getContext().livenessAnalysisMode == EspressoOptions.LivenessAnalysisMode.COMPILED;
                return new LivenessAnalysis(builder.actions, builder.edge, builder.onStart, compiledCodeOnly);
            }
        }
    }

    public LivenessAnalysis(LocalVariableAction[] result, EdgeAction[] edge, LocalVariableAction onStart, boolean compiledCodeOnly) {
        this.result = result;
        this.edge = edge;
        this.onStart = onStart;
        this.compiledCodeOnly = compiledCodeOnly;
    }

    private LivenessAnalysis() {
        this(null, null, null, false);
    }

    private static final class Builder {
        private final LocalVariableAction[] actions;
        private final EdgeAction[] edge;
        private LocalVariableAction onStart;

        private final Graph<? extends LinkedBlock> graph;
        private final Method method;
        private final BlockBoundaryResult helper;

        private Builder(Graph<? extends LinkedBlock> graph, Method method, BlockBoundaryResult helper) {
            this.actions = new LocalVariableAction[method.getCode().length];
            this.edge = new EdgeAction[method.getCode().length];
            this.graph = graph;
            this.method = method;
            this.helper = helper;
        }

        private void build() {
            for (int id = 0; id < graph.totalBlocks(); id++) {
                processBlock(id);
            }
        }

        private void processBlock(int blockID) {
            LinkedBlock current = graph.get(blockID);

            if (current == graph.entryBlock()) {
                // Clear all non-argument locals (and non-used args)
                processEntryBlock(blockID);
            } else {
                // merge the state from all predecessors
                BitSet mergedEntryState = mergePredecessors(current);

                // Locals inherited from merging predecessors, but are not needed down the line can
                // be killed on block entry.
                killLocalsOnBlockEntry(blockID, current, mergedEntryState);
            }

            // Replay history in reverse to seek the last load for each variable.
            replayHistory(blockID);
        }

        private void processEntryBlock(int blockID) {
            BitSet entryState = helper.entryFor(blockID);
            ArrayList<Integer> kills = new ArrayList<>();
            for (int i = 0; i < method.getMaxLocals(); i++) {
                if (!entryState.get(i)) {
                    kills.add(i);
                }
            }
            if (!kills.isEmpty()) {
                onStart = toLocalAction(kills);
            }
        }

        private BitSet mergePredecessors(LinkedBlock current) {
            BitSet mergedEntryState = new BitSet(method.getMaxLocals());
            for (int pred : current.predecessorsID()) {
                mergedEntryState.or(helper.endFor(pred));
            }
            return mergedEntryState;
        }

        @SuppressWarnings("unchecked")
        private void killLocalsOnBlockEntry(int blockID, LinkedBlock current, BitSet mergedEntryState) {
            BitSet entryState = helper.entryFor(blockID);
            mergedEntryState.andNot(entryState);

            int nbPredKills = 0;
            int[] predecessors = current.predecessorsID();
            ArrayList<Integer>[] kills = new ArrayList[predecessors.length];

            for (int local : Util.bitSetIterator(mergedEntryState)) {
                for (int j = 0; j < predecessors.length; j++) {
                    int pred = predecessors[j];
                    BitSet predEnd = helper.endFor(pred);
                    if (predEnd.get(local)) {
                        ArrayList<Integer> kill = kills[j];
                        if (kill == null) {
                            kills[j] = kill = new ArrayList<>();
                            nbPredKills++;
                        }
                        kill.add(local);
                    }
                }
            }

            if (nbPredKills > 0) {
                int pos = 0;
                int[] predBCIs = new int[nbPredKills];
                LocalVariableAction[] edgeActions = new LocalVariableAction[nbPredKills];
                for (int p = 0; p < predecessors.length; p++) {
                    ArrayList<Integer> clears = kills[p];
                    if (clears != null) {
                        predBCIs[pos] = graph.get(predecessors[p]).lastBCI();
                        edgeActions[pos] = toLocalAction(clears);
                        pos++;
                    }
                }
                assert pos == nbPredKills;
                edge[current.start()] = new SelectEdgeAction(predBCIs, edgeActions);
            }

        }

        private static LocalVariableAction toLocalAction(ArrayList<Integer> actions) {
            assert !actions.isEmpty();
            if (actions.size() == 1) {
                return NullOutAction.get(actions.get(0));
            } else {
                return new MultiAction(Util.toIntArray(actions));
            }
        }

        private void replayHistory(int blockID) {
            BitSet endState = helper.endFor(blockID);
            if (endState == null) {
                // unreachable
                return;
            }
            endState = (BitSet) endState.clone();
            for (Record r : helper.historyFor(blockID).reverse()) {
                switch (r.type) {
                    case LOAD: // Fallthrough
                    case IINC:
                        if (!endState.get(r.local)) {
                            // last load for this value
                            recordAction(r.bci, NullOutAction.get(r.local));
                            endState.set(r.local);
                        }
                        break;
                    case STORE:
                        if (!endState.get(r.local)) {
                            // Store is not used: can be killed immediately.
                            recordAction(r.bci, NullOutAction.get(r.local));
                        } else {
                            // Store for this variable kills the local between here and the previous
                            // usage
                            endState.clear(r.local);
                        }
                        break;
                }
            }
        }

        private void recordAction(int bci, LocalVariableAction action) {
            LocalVariableAction toInsert = action;
            if (actions[bci] != null) {
                // 2 actions for a single BCI: access to a 2 slot local (long/double).
                toInsert = actions[bci].merge(toInsert);
            }
            actions[bci] = toInsert;
        }

    }

    @SuppressWarnings("unused") // For debug purposes.
    private void log(PrintStream ps) {
        ps.println("on start: " + onStart);
        for (int i = 0; i < result.length; i++) {
            LocalVariableAction post = result[i];
            if (post != null) {
                ps.println(i + "- post: " + post);
            }
            EdgeAction edgeAction = edge[i];
            if (edgeAction != null) {
                ps.println("at " + i);
                ps.println(edgeAction.toString());
            }
        }
    }

}
