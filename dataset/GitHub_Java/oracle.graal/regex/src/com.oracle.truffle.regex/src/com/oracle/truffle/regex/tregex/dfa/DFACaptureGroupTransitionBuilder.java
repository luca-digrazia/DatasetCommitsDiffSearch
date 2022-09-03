/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupLazyTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupPartialTransitionNode;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Arrays;

public class DFACaptureGroupTransitionBuilder implements JsonConvertible {

    private final Counter idCounter;
    private final NFA nfa;
    private final DFAStateTransitionBuilder transition;
    private final boolean isInitialTransition;
    private NFAStateSet requiredStates = null;
    private DFACaptureGroupLazyTransitionNode lazyTransition = null;

    public DFACaptureGroupTransitionBuilder(Counter idCounter, NFA nfa, DFAStateTransitionBuilder transition, boolean isInitialTransition) {
        this.idCounter = idCounter;
        this.nfa = nfa;
        this.transition = transition;
        this.isInitialTransition = isInitialTransition;
    }

    public void setLazyTransition(DFACaptureGroupLazyTransitionNode lazyTransition) {
        this.lazyTransition = lazyTransition;
    }

    private NFAStateSet getRequiredStates() {
        if (requiredStates == null) {
            requiredStates = new NFAStateSet(nfa);
            for (NFAStateTransition nfaTransition : transition.getTransitionSet()) {
                requiredStates.add(nfaTransition.getSource());
            }
        }
        return requiredStates;
    }

    private DFACaptureGroupPartialTransitionNode createPartialTransition(NFAStateSet targetStates, CompilationBuffer compilationBuffer) {
        int[] newOrder = new int[Math.max(getRequiredStates().size(), targetStates.size())];
        Arrays.fill(newOrder, -1);
        boolean[] used = new boolean[newOrder.length];
        int[] copySource = new int[getRequiredStates().size()];
        ObjectArrayBuffer indexUpdates = compilationBuffer.getObjectBuffer1();
        ObjectArrayBuffer indexClears = compilationBuffer.getObjectBuffer2();
        ByteArrayBuffer arrayCopies = compilationBuffer.getByteArrayBuffer();
        for (NFAStateTransition nfaTransition : transition.getTransitionSet()) {
            if (targetStates.contains(nfaTransition.getTarget())) {
                int sourceIndex = getRequiredStates().getStateIndex(nfaTransition.getSource());
                int targetIndex = targetStates.getStateIndex(nfaTransition.getTarget());
                assert !(nfaTransition.getTarget().isForwardFinalState()) || targetIndex == DFACaptureGroupPartialTransitionNode.FINAL_STATE_RESULT_INDEX;
                if (!used[sourceIndex]) {
                    used[sourceIndex] = true;
                    newOrder[targetIndex] = sourceIndex;
                    copySource[sourceIndex] = targetIndex;
                } else {
                    arrayCopies.add((byte) copySource[sourceIndex]);
                    arrayCopies.add((byte) targetIndex);
                }
                if (nfaTransition.getGroupBoundaries().hasIndexUpdates()) {
                    indexUpdates.add(nfaTransition.getGroupBoundaries().updatesToPartialTransitionArray(targetIndex));
                }
                if (nfaTransition.getGroupBoundaries().hasIndexClears()) {
                    indexClears.add(nfaTransition.getGroupBoundaries().clearsToPartialTransitionArray(targetIndex));
                }
            }
        }
        int order = 0;
        for (int i = 0; i < newOrder.length; i++) {
            if (newOrder[i] == -1) {
                while (used[order]) {
                    order++;
                }
                newOrder[i] = order++;
            }
        }
        byte preReorderFinalStateResultIndex = (byte) newOrder[DFACaptureGroupPartialTransitionNode.FINAL_STATE_RESULT_INDEX];
        // important: don't change the order, because newOrderToSequenceOfSwaps() reuses
        // CompilationBuffer#getByteArrayBuffer()
        byte[] byteArrayCopies = arrayCopies.size() == 0 ? DFACaptureGroupPartialTransitionNode.EMPTY_ARRAY_COPIES : arrayCopies.toArray();
        byte[] reorderSwaps = isInitialTransition ? DFACaptureGroupPartialTransitionNode.EMPTY_REORDER_SWAPS : newOrderToSequenceOfSwaps(newOrder, compilationBuffer);
        return DFACaptureGroupPartialTransitionNode.create(reorderSwaps, byteArrayCopies,
                        indexUpdates.toArray(DFACaptureGroupPartialTransitionNode.EMPTY_INDEX_UPDATES),
                        indexClears.toArray(DFACaptureGroupPartialTransitionNode.EMPTY_INDEX_CLEARS),
                        preReorderFinalStateResultIndex);
    }

    /**
     * Converts the ordering given by <code>newOrder</code> to a sequence of swap operations as
     * needed by {@link DFACaptureGroupPartialTransitionNode}. The number of swap operations is
     * guaranteed to be smaller than <code>newOrder.length</code>. Caution: this method uses
     * {@link CompilationBuffer#getByteArrayBuffer()}.
     */
    private static byte[] newOrderToSequenceOfSwaps(int[] newOrder, CompilationBuffer compilationBuffer) {
        ByteArrayBuffer swaps = compilationBuffer.getByteArrayBuffer();
        for (int i = 0; i < newOrder.length; i++) {
            int swapSource = newOrder[i];
            int swapTarget = swapSource;
            if (swapSource == i) {
                continue;
            }
            do {
                swapSource = swapTarget;
                swapTarget = newOrder[swapTarget];
                swaps.add((byte) swapSource);
                swaps.add((byte) swapTarget);
                newOrder[swapSource] = swapSource;
            } while (swapTarget != i);
        }
        assert swaps.size() / 2 < newOrder.length;
        return swaps.size() == 0 ? DFACaptureGroupPartialTransitionNode.EMPTY_REORDER_SWAPS : swaps.toArray();
    }

    public DFACaptureGroupLazyTransitionNode toLazyTransition(CompilationBuffer compilationBuffer) {
        if (lazyTransition == null) {
            DFAStateNodeBuilder successor = transition.getTarget();
            DFACaptureGroupPartialTransitionNode[] partialTransitions = new DFACaptureGroupPartialTransitionNode[successor.getTransitions().length];
            for (int i = 0; i < successor.getTransitions().length; i++) {
                DFACaptureGroupTransitionBuilder successorTransition = successor.getTransitions()[i].getCaptureGroupTransition();
                partialTransitions[i] = createPartialTransition(successorTransition.getRequiredStates(), compilationBuffer);
            }
            DFACaptureGroupPartialTransitionNode transitionToFinalState = null;
            DFACaptureGroupPartialTransitionNode transitionToAnchoredFinalState = null;
            if (successor.isFinalState()) {
                transitionToFinalState = createPartialTransition(
                                new NFAStateSet(nfa, successor.getUnAnchoredFinalStateTransition().getSource()), compilationBuffer);
            }
            if (successor.isAnchoredFinalState()) {
                transitionToAnchoredFinalState = createPartialTransition(
                                new NFAStateSet(nfa, successor.getAnchoredFinalStateTransition().getSource()), compilationBuffer);
            }
            lazyTransition = new DFACaptureGroupLazyTransitionNode((short) idCounter.inc(), partialTransitions, transitionToFinalState, transitionToAnchoredFinalState);
        }
        return lazyTransition;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.array(transition.getTransitionSet().stream().map(x -> Json.val(x.getId())));
    }
}
