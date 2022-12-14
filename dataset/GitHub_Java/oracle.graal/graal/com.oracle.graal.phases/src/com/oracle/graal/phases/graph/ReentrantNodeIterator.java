/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.graph;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.nodes.*;

public final class ReentrantNodeIterator {

    public static class LoopInfo<StateT> {

        public final Map<LoopEndNode, StateT> endStates = new IdentityHashMap<>(4);
        public final Map<LoopExitNode, StateT> exitStates = new IdentityHashMap<>(2);
    }

    public abstract static class NodeIteratorClosure<StateT> {

        protected abstract void processNode(FixedNode node, StateT currentState);

        protected abstract StateT merge(MergeNode merge, List<StateT> states);

        protected abstract StateT afterSplit(BeginNode node, StateT oldState);

        protected abstract Map<LoopExitNode, StateT> processLoop(LoopBeginNode loop, StateT initialState);
    }

    private ReentrantNodeIterator() {
        // no instances allowed
    }

    public static <StateT> LoopInfo<StateT> processLoop(NodeIteratorClosure<StateT> closure, LoopBeginNode loop, StateT initialState) {
        HashSet<FixedNode> boundary = new HashSet<>();
        for (LoopExitNode exit : loop.loopExits()) {
            boundary.add(exit);
        }
        Map<FixedNode, StateT> blockEndStates = apply(closure, loop, initialState, boundary);

        LoopInfo<StateT> info = new LoopInfo<>();
        for (LoopEndNode end : loop.loopEnds()) {
            assert blockEndStates.containsKey(end) : "no end state for " + end;
            info.endStates.put(end, blockEndStates.get(end));
        }
        for (LoopExitNode exit : loop.loopExits()) {
            assert blockEndStates.containsKey(exit) : "no exit state for " + exit;
            info.exitStates.put(exit, blockEndStates.get(exit));
        }
        return info;
    }

    public static <StateT> Map<FixedNode, StateT> apply(NodeIteratorClosure<StateT> closure, FixedNode start, StateT initialState, Set<FixedNode> boundary) {
        Deque<BeginNode> nodeQueue = new ArrayDeque<>();
        IdentityHashMap<FixedNode, StateT> blockEndStates = new IdentityHashMap<>();

        StateT state = initialState;
        FixedNode current = start;
        do {
            while (current instanceof FixedWithNextNode) {
                if (boundary != null && boundary.contains(current)) {
                    blockEndStates.put(current, state);
                    current = null;
                } else {
                    FixedNode next = ((FixedWithNextNode) current).next();
                    closure.processNode(current, state);
                    current = next;
                }
            }

            if (current != null) {
                closure.processNode(current, state);

                NodeClassIterator successors = current.successors().iterator();
                if (!successors.hasNext()) {
                    if (current instanceof LoopEndNode) {
                        blockEndStates.put(current, state);
                    } else if (current instanceof EndNode) {
                        // add the end node and see if the merge is ready for processing
                        MergeNode merge = ((EndNode) current).merge();
                        if (merge instanceof LoopBeginNode) {
                            Map<LoopExitNode, StateT> loopExitState = closure.processLoop((LoopBeginNode) merge, state);
                            for (Map.Entry<LoopExitNode, StateT> entry : loopExitState.entrySet()) {
                                blockEndStates.put(entry.getKey(), entry.getValue());
                                nodeQueue.add(entry.getKey());
                            }
                        } else {
                            assert !blockEndStates.containsKey(current);
                            blockEndStates.put(current, state);
                            boolean endsVisited = true;
                            for (EndNode forwardEnd : merge.forwardEnds()) {
                                if (!blockEndStates.containsKey(forwardEnd)) {
                                    endsVisited = false;
                                    break;
                                }
                            }
                            if (endsVisited) {
                                ArrayList<StateT> states = new ArrayList<>(merge.forwardEndCount());
                                for (int i = 0; i < merge.forwardEndCount(); i++) {
                                    EndNode forwardEnd = merge.forwardEndAt(i);
                                    assert blockEndStates.containsKey(forwardEnd);
                                    StateT other = blockEndStates.get(forwardEnd);
                                    states.add(other);
                                }
                                state = closure.merge(merge, states);
                                current = merge;
                                continue;
                            }
                        }
                    }
                } else {
                    FixedNode firstSuccessor = (FixedNode) successors.next();
                    if (!successors.hasNext()) {
                        current = firstSuccessor;
                        continue;
                    } else {
                        while (successors.hasNext()) {
                            BeginNode successor = (BeginNode) successors.next();
                            blockEndStates.put(successor, closure.afterSplit(successor, state));
                            nodeQueue.add(successor);
                        }
                        state = closure.afterSplit((BeginNode) firstSuccessor, state);
                        current = firstSuccessor;
                        continue;
                    }
                }
            }

            // get next queued block
            if (nodeQueue.isEmpty()) {
                return blockEndStates;
            } else {
                current = nodeQueue.removeFirst();
                state = blockEndStates.get(current);
                assert !(current instanceof MergeNode) && current instanceof BeginNode;
            }
        } while (true);
    }
}
