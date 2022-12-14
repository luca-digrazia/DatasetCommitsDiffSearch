/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.loop;

import java.util.*;

import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.VirtualState.NodeClosure;
import com.oracle.graal.nodes.util.*;


public class LoopFragmentInside extends LoopFragment {
    /** mergedInitializers.
     * When an inside fragment's (loop)ends are merged to create a unique exit point,
     * some phis must be created : they phis together all the back-values of the loop-phis
     * These can then be used to update the loop-phis' forward edge value ('initializer') in the peeling case.
     * In the unrolling case they will be used as the value that replace the loop-phis of the duplicated inside fragment
     */
    private Map<PhiNode, ValueNode> mergedInitializers;
    private final DuplicationReplacement dataFixBefore = new DuplicationReplacement() {
        @Override
        public Node replacement(Node oriInput) {
            if (!(oriInput instanceof ValueNode)) {
                return oriInput;
            }
            return prim((ValueNode) oriInput);
        }
    };

    public LoopFragmentInside(LoopEx loop) {
        super(loop);
    }

    public LoopFragmentInside(LoopFragmentInside original) {
        super(null, original);
    }

    @Override
    public LoopFragmentInside duplicate() {
        assert !isDuplicate();
        return new LoopFragmentInside(this);
    }

    @Override
    public LoopFragmentInside original() {
        return (LoopFragmentInside) super.original();
    }

    @SuppressWarnings("unused")
    public void appendInside(LoopEx loop) {
        // TODO (gd)
    }

    @Override
    public LoopEx loop() {
        assert !this.isDuplicate();
        return super.loop();
    }

    @Override
    public void insertBefore(LoopEx loop) {
        assert this.isDuplicate() && this.original().loop() == loop;

        patchNodes(dataFixBefore);

        BeginNode end = mergeEnds();

        original().patchPeeling(this);

        mergeEarlyExits();

        BeginNode entry = getDuplicatedNode(loop.loopBegin());
        FrameState state = entry.stateAfter();
        if (state != null) {
            entry.setStateAfter(null);
            GraphUtil.killWithUnusedFloatingInputs(state);
        }
        loop.entryPoint().replaceAtPredecessor(entry);
        end.setProbability(loop.entryPoint().probability());
        end.setNext(loop.entryPoint());
    }

    @Override
    public NodeIterable<Node> nodes() {
        if (nodes == null) {
            LoopFragmentWhole whole = loop().whole();
            whole.nodes(); // init nodes bitmap in whole
            nodes = whole.nodes.copy();
            // remove the phis
            for (PhiNode phi : loop().loopBegin().phis()) {
                nodes.clear(phi);
            }
        }
        return nodes;
    }

    @Override
    protected DuplicationReplacement getDuplicationReplacement() {
        final LoopBeginNode loopBegin = loop().loopBegin();
        final StructuredGraph graph = graph();
        return new DuplicationReplacement() {
            @Override
            public Node replacement(Node original) {
                if (original == loopBegin) {
                    return graph.add(new BeginNode());
                }
                if (original instanceof LoopExitNode && ((LoopExitNode) original).loopBegin() == loopBegin) {
                    return graph.add(new BeginNode());
                }
                if (original instanceof LoopEndNode && ((LoopEndNode) original).loopBegin() == loopBegin) {
                    return graph.add(new EndNode());
                }
                return original;
            }
        };
    }

    @Override
    protected void finishDuplication() {
        // TODO (gd) ?
    }

    private void patchPeeling(LoopFragmentInside peel) {
        LoopBeginNode loopBegin = loop().loopBegin();
        StructuredGraph graph = (StructuredGraph) loopBegin.graph();
        List<PhiNode> newPhis = new LinkedList<>();
        for (PhiNode phi : loopBegin.phis().snapshot()) {
            ValueNode first;
            if (loopBegin.loopEnds().count() == 1) {
                ValueNode b = phi.valueAt(loopBegin.loopEnds().first()); // back edge value
                first = peel.prim(b); // corresponding value in the peel
            } else {
                first = peel.mergedInitializers.get(phi);
            }
            // create a new phi (we don't patch the old one since some usages of the old one may still be valid)
            PhiNode newPhi = graph.add(phi.type() == PhiType.Value ? new PhiNode(phi.kind(), loopBegin) : new PhiNode(phi.type(), loopBegin));
            newPhi.addInput(first);
            for (LoopEndNode end : loopBegin.orderedLoopEnds()) {
                newPhi.addInput(phi.valueAt(end));
            }
            peel.putDuplicatedNode(phi, newPhi);
            newPhis.add(newPhi);
            for (Node usage : phi.usages().snapshot()) {
                if (peel.getDuplicatedNode(usage) != null) { // patch only usages that should use the new phi ie usages that were peeled
                    usage.replaceFirstInput(phi, newPhi);
                }
            }
        }
        // check new phis to see if they have as input some old phis, replace those inputs with the new corresponding phis
        for (PhiNode phi : newPhis) {
            for (int i = 0; i < phi.valueCount(); i++) {
                ValueNode v = phi.valueAt(i);
                if (loopBegin.isPhiAtMerge(v)) {
                    PhiNode newV = peel.getDuplicatedNode((PhiNode) v);
                    if (newV != null) {
                        phi.setValueAt(i, newV);
                    }
                }
            }
        }
    }

    /**
     * Gets the corresponding value in this fragment.
     *
     * @param b original value
     * @return corresponding value in the peel
     */
    private ValueNode prim(ValueNode b) {
        assert isDuplicate();
        LoopBeginNode loopBegin = original().loop().loopBegin();
        if (loopBegin.isPhiAtMerge(b)) {
            PhiNode phi = (PhiNode) b;
            return phi.valueAt(loopBegin.forwardEnd());
        } else if (nodesReady) {
            ValueNode v = getDuplicatedNode(b);
            if (v == null) {
                return b;
            }
            return v;
        } else {
            return b;
        }
    }

    private BeginNode mergeEnds() {
        assert isDuplicate();
        List<EndNode> endsToMerge = new LinkedList<>();
        Map<EndNode, LoopEndNode> reverseEnds = new HashMap<>(); // map peel's exit to the corresponding loop exits
        LoopBeginNode loopBegin = original().loop().loopBegin();
        for (LoopEndNode le : loopBegin.loopEnds()) {
            EndNode duplicate = getDuplicatedNode(le);
            if (duplicate != null) {
                endsToMerge.add(duplicate);
                reverseEnds.put(duplicate, le);
            }
        }
        mergedInitializers = new IdentityHashMap<>();
        BeginNode newExit;
        StructuredGraph graph = graph();
        if (endsToMerge.size() == 1) {
            EndNode end = endsToMerge.get(0);
            assert end.usages().count() == 0;
            newExit = graph.add(new BeginNode());
            end.replaceAtPredecessor(newExit);
            end.safeDelete();
        } else {
            assert endsToMerge.size() > 1;
            MergeNode newExitMerge = graph.add(new MergeNode());
            newExit = newExitMerge;
            FrameState state = loopBegin.stateAfter();
            FrameState duplicateState = null;
            if (state != null) {
                duplicateState = state.duplicateWithVirtualState();
                newExitMerge.setStateAfter(duplicateState);
            }
            for (EndNode end : endsToMerge) {
                newExitMerge.addForwardEnd(end);
            }

            for (final PhiNode phi : loopBegin.phis().snapshot()) {
                final PhiNode firstPhi = graph.add(phi.type() == PhiType.Value ? new PhiNode(phi.kind(), newExitMerge) : new PhiNode(phi.type(), newExitMerge));
                for (EndNode end : newExitMerge.forwardEnds()) {
                    LoopEndNode loopEnd = reverseEnds.get(end);
                    ValueNode prim = prim(phi.valueAt(loopEnd));
                    assert prim != null;
                    firstPhi.addInput(prim);
                }
                ValueNode initializer = firstPhi;
                if (duplicateState != null) {
                    // fix the merge's state after
                    duplicateState.applyToNonVirtual(new NodeClosure<ValueNode>() {
                        @Override
                        public void apply(Node from, ValueNode node) {
                            if (node == phi) {
                                from.replaceFirstInput(phi, firstPhi);
                            }
                        }
                    });
                }
                mergedInitializers.put(phi, initializer);
            }
        }
        return newExit;
    }
}
