/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.alloc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;

public final class TraceBuilderResult {

    public abstract static class TrivialTracePredicate {
        public abstract boolean isTrivialTrace(Trace trace);
    }

    private final ArrayList<Trace> traces;
    private final int[] blockToTrace;

    static TraceBuilderResult create(AbstractBlockBase<?>[] blocks, ArrayList<Trace> traces, int[] blockToTrace, TrivialTracePredicate pred) {
        connect(traces, blockToTrace);
        ArrayList<Trace> newTraces = reorderTraces(traces, blockToTrace, pred);
        TraceBuilderResult traceBuilderResult = new TraceBuilderResult(newTraces, blockToTrace);
        traceBuilderResult.numberTraces();
        assert verify(traceBuilderResult, blocks.length);
        return traceBuilderResult;
    }

    private TraceBuilderResult(ArrayList<Trace> traces, int[] blockToTrace) {
        this.traces = traces;
        this.blockToTrace = blockToTrace;
    }

    public Trace getTraceForBlock(AbstractBlockBase<?> block) {
        int traceNr = blockToTrace[block.getId()];
        Trace trace = traces.get(traceNr);
        assert traceNr == trace.getId() : "Trace number mismatch: " + traceNr + " vs. " + trace.getId();
        return trace;
    }

    public Trace traceForBlock(AbstractBlockBase<?> block) {
        return getTraces().get(blockToTrace[block.getId()]);
    }

    public ArrayList<Trace> getTraces() {
        return traces;
    }

    public boolean incomingEdges(Trace trace) {
        int traceNr = trace.getId();
        Iterator<AbstractBlockBase<?>> traceIt = getTraces().get(traceNr).getBlocks().iterator();
        return incomingEdges(traceNr, traceIt);
    }

    public boolean incomingSideEdges(Trace trace) {
        int traceNr = trace.getId();
        Iterator<AbstractBlockBase<?>> traceIt = getTraces().get(traceNr).getBlocks().iterator();
        if (!traceIt.hasNext()) {
            return false;
        }
        traceIt.next();
        return incomingEdges(traceNr, traceIt);
    }

    private boolean incomingEdges(int traceNr, Iterator<AbstractBlockBase<?>> trace) {
        /* TODO (je): not efficient. find better solution. */
        while (trace.hasNext()) {
            AbstractBlockBase<?> block = trace.next();
            for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                if (getTraceForBlock(pred).getId() != traceNr) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean verify(TraceBuilderResult traceBuilderResult, int expectedLength) {
        ArrayList<Trace> traces = traceBuilderResult.getTraces();
        assert verifyAllBlocksScheduled(traceBuilderResult, expectedLength) : "Not all blocks assigned to traces!";
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i);
            assert trace.getId() == i : "Trace number mismatch: " + trace.getId() + " vs. " + i;

            BitSet suxTraces = new BitSet(traces.size());
            for (Trace suxTrace : trace.getSuccessors()) {
                assert !suxTraces.get(suxTrace.getId()) : "Trace twice successors " + suxTrace;
                suxTraces.set(suxTrace.getId());
            }

            AbstractBlockBase<?> last = null;
            int blockNumber = 0;
            for (AbstractBlockBase<?> current : trace.getBlocks()) {
                AbstractBlockBase<?> block = current;
                assert traceBuilderResult.getTraceForBlock(block).getId() == i : "Trace number mismatch for block " + block + ": " + traceBuilderResult.getTraceForBlock(block) + " vs. " + i;
                assert last == null || Arrays.asList(current.getPredecessors()).contains(last) : "Last block (" + last + ") not a predecessor of " + current;
                assert current.getLinearScanNumber() == blockNumber : "Blocks not numbered correctly: " + current.getLinearScanNumber() + " vs. " + blockNumber;
                last = current;
                blockNumber++;
                for (AbstractBlockBase<?> sux : block.getSuccessors()) {
                    Trace suxTrace = traceBuilderResult.getTraceForBlock(sux);
                    assert suxTraces.get(suxTrace.getId()) : "Successor Trace " + suxTrace + " for block " + sux + " not in successor traces of " + trace;
                }
            }
        }
        return true;
    }

    private static boolean verifyAllBlocksScheduled(TraceBuilderResult traceBuilderResult, int expectedLength) {
        ArrayList<Trace> traces = traceBuilderResult.getTraces();
        BitSet handled = new BitSet(expectedLength);
        for (Trace trace : traces) {
            for (AbstractBlockBase<?> block : trace.getBlocks()) {
                assert !handled.get(block.getId()) : "Block added twice: " + block;
                handled.set(block.getId());
            }
        }
        return handled.cardinality() == expectedLength;
    }

    private void numberTraces() {
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i);
            trace.setId(i);
            for (AbstractBlockBase<?> block : trace.getBlocks()) {
                blockToTrace[block.getId()] = i;
            }
        }
    }

    private static void connect(ArrayList<Trace> traces, int[] blockToTrace) {
        int numTraces = traces.size();
        for (Trace trace : traces) {
            BitSet added = new BitSet(numTraces);
            ArrayList<Trace> successors = trace.getSuccessors();
            assert successors.size() == 0 : "Can only connect traces once!";

            for (AbstractBlockBase<?> block : trace.getBlocks()) {
                for (AbstractBlockBase<?> succ : block.getSuccessors()) {
                    int succId = blockToTrace[succ.getId()];
                    Trace succTrace = traces.get(succId);
                    if (!added.get(succId)) {
                        added.set(succId);
                        successors.add(succTrace);
                    }
                }
            }
        }
    }

    @SuppressWarnings("try")
    private static ArrayList<Trace> reorderTraces(ArrayList<Trace> traces, int[] blockToTrace, TrivialTracePredicate pred) {
        if (pred == null) {
            return traces;
        }
        try (Indent indent = Debug.logAndIndent("ReorderTrace")) {
            ArrayList<Trace> newTraces = new ArrayList<>(traces.size());
            for (Trace currentTrace : traces) {
                if (currentTrace != null) {
                    // add current trace
                    newTraces.add(currentTrace);
                    for (Trace succTrace : currentTrace.getSuccessors()) {
                        int succTraceIndex = getTraceIndex(succTrace, blockToTrace);
                        if (getTraceIndex(currentTrace, blockToTrace) < succTraceIndex && pred.isTrivialTrace(succTrace)) {
                            //
                            int oldTraceId = succTraceIndex;
                            int newTraceId = newTraces.size();
                            Debug.log("Moving trivial trace from %d to %d", oldTraceId, newTraceId);
                            //
                            succTrace.setId(newTraceId);
                            newTraces.add(succTrace);
                            traces.set(oldTraceId, null);
                        }
                    }
                }
            }
            assert newTraces.size() == traces.size() : "Lost traces?";
            return newTraces;
        }
    }

    private static int getTraceIndex(Trace trace, int[] blockToTrace) {
        return blockToTrace[trace.getBlocks().get(0).getId()];
    }

}
