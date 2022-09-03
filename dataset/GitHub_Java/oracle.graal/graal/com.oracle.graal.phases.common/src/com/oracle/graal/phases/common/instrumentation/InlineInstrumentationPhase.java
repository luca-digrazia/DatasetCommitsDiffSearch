/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.instrumentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.DeoptimizingNode;
import com.oracle.graal.nodes.EndNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.MergeNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.StartNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.debug.instrumentation.InstrumentationInliningCallback;
import com.oracle.graal.nodes.debug.instrumentation.InstrumentationNode;
import com.oracle.graal.nodes.memory.MemoryAnchorNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.virtual.EscapeObjectState;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.FloatingReadPhase;
import com.oracle.graal.phases.common.FrameStateAssignmentPhase;
import com.oracle.graal.phases.common.GuardLoweringPhase;
import com.oracle.graal.phases.common.LoweringPhase;
import com.oracle.graal.phases.tiers.LowTierContext;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * The {code InlineInstrumentationPhase} inlines the instrumentation graph back into the graph to
 * take place of the InstrumentationNode. Before inlining, the instrumentation graph will be passed
 * to GuardLoweringPhase, FrameStateAssignmentPhase, LoweringPhase, and FloatingReadPhase.
 */
public class InlineInstrumentationPhase extends BasePhase<LowTierContext> {

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        Set<StructuredGraph> visited = new HashSet<>();
        for (InstrumentationNode instrumentationNode : graph.getNodes().filter(InstrumentationNode.class)) {
            // if the target of the instrumentationNode is null while the offset is non-zero, the
            // instrumentation is invalid.
            if (instrumentationNode.target() == null && instrumentationNode.offset() != 0) {
                graph.removeFixed(instrumentationNode);
                continue;
            }
            // Clone the instrumentation in case it is shared amongst multiple InstrumentationNodes.
            StructuredGraph instrumentationGraph = instrumentationNode.instrumentationGraph();
            if (visited.contains(instrumentationGraph)) {
                instrumentationGraph = (StructuredGraph) instrumentationGraph.copy();
                instrumentationNode.setInstrumentationGraph(instrumentationGraph);
            }
            visited.add(instrumentationGraph);
        }
        // at this point, instrumentation graphs are not shared and can apply changes according to
        // the context of the InstrumentationNode
        for (InstrumentationNode instrumentationNode : graph.getNodes().filter(InstrumentationNode.class)) {
            StructuredGraph instrumentationGraph = instrumentationNode.instrumentationGraph();
            // notify instrumentation nodes of the preInlineInstrumentation event
            for (Node node : instrumentationGraph.getNodes()) {
                if (node instanceof InstrumentationInliningCallback) {
                    ((InstrumentationInliningCallback) node).preInlineInstrumentation(instrumentationNode);
                }
            }
            // pre-process the instrumentation graph
            new GuardLoweringPhase().apply(instrumentationGraph, null);
            new FrameStateAssignmentPhase().apply(instrumentationGraph, false);
            new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.LOW_TIER).apply(instrumentationGraph, context);
            new FloatingReadPhase(true, true).apply(instrumentationGraph, false);

            final StartNode entryPointNode = instrumentationGraph.start();
            MemoryAnchorNode anchor = instrumentationGraph.add(new MemoryAnchorNode());
            instrumentationGraph.start().replaceAtUsages(InputType.Memory, anchor);
            if (anchor.hasNoUsages()) {
                anchor.safeDelete();
            } else {
                instrumentationGraph.addAfterFixed(entryPointNode, anchor);
            }

            ArrayList<Node> nodes = new ArrayList<>(instrumentationGraph.getNodes().count());
            FixedNode firstCFGNode = entryPointNode.next();
            // locate return nodes
            ArrayList<ReturnNode> returnNodes = new ArrayList<>(4);
            for (Node node : instrumentationGraph.getNodes()) {
                if (node == entryPointNode || node == entryPointNode.stateAfter() || node instanceof ParameterNode) {
                    // Do nothing.
                } else {
                    nodes.add(node);
                    if (node instanceof ReturnNode) {
                        returnNodes.add((ReturnNode) node);
                    }
                }
            }

            final AbstractBeginNode prevBegin = AbstractBeginNode.prevBegin(instrumentationNode);
            DuplicationReplacement localReplacement = new DuplicationReplacement() {
                @Override
                public Node replacement(Node replacement) {
                    if (replacement instanceof ParameterNode) {
                        // rewire weak dependencies. In case of invalid input, we replace it with a
                        // constant null
                        ValueNode value = instrumentationNode.getWeakDependency(((ParameterNode) replacement).index());
                        if (value == null || value.isDeleted() || value instanceof VirtualObjectNode || value.stamp().getStackKind() != JavaKind.Object) {
                            return graph.unique(new ConstantNode(JavaConstant.NULL_POINTER, ((ParameterNode) replacement).stamp()));
                        } else {
                            return value;
                        }
                    } else if (replacement == entryPointNode) {
                        return prevBegin;
                    }
                    return replacement;
                }
            };
            // clone instrumentation nodes into the graph and replace the InstrumentationNode
            Map<Node, Node> duplicates = graph.addDuplicates(nodes, instrumentationGraph, instrumentationGraph.getNodeCount(), localReplacement);
            FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
            instrumentationNode.replaceAtPredecessor(firstCFGNodeDuplicate);

            if (!returnNodes.isEmpty()) {
                if (returnNodes.size() == 1) {
                    ReturnNode returnNode = (ReturnNode) duplicates.get(returnNodes.get(0));
                    returnNode.replaceAndDelete(instrumentationNode);
                } else {
                    ArrayList<ReturnNode> returnDuplicates = new ArrayList<>(returnNodes.size());
                    for (ReturnNode returnNode : returnNodes) {
                        returnDuplicates.add((ReturnNode) duplicates.get(returnNode));
                    }
                    AbstractMergeNode merge = graph.add(new MergeNode());

                    for (ReturnNode returnNode : returnDuplicates) {
                        EndNode endNode = graph.add(new EndNode());
                        merge.addForwardEnd(endNode);
                        returnNode.replaceAndDelete(endNode);
                    }

                    merge.setNext(instrumentationNode);
                }
            }

            // invalidate FrameStates in the instrumentation
            for (Node replacee : duplicates.values()) {
                if (replacee instanceof FrameState) {
                    FrameState oldState = (FrameState) replacee;
                    FrameState newState = new FrameState(null, oldState.method(), oldState.bci, 0, 0, 0, oldState.rethrowException(), oldState.duringCall(), null,
                                    Collections.<EscapeObjectState> emptyList());
                    graph.addWithoutUnique(newState);
                    oldState.replaceAtUsages(newState);
                }
            }
            // update FrameStates of the DeoptimizingNodes in the instrumentation
            FrameState state = instrumentationNode.stateBefore();
            for (Node replacee : duplicates.values()) {
                if (replacee instanceof DeoptimizingNode && !(replacee instanceof Invoke)) {
                    DeoptimizingNode deoptDup = (DeoptimizingNode) replacee;
                    if (deoptDup.canDeoptimize()) {
                        if (deoptDup instanceof DeoptimizingNode.DeoptBefore) {
                            ((DeoptimizingNode.DeoptBefore) deoptDup).setStateBefore(state);
                        }
                        if (deoptDup instanceof DeoptimizingNode.DeoptDuring) {
                            DeoptimizingNode.DeoptDuring deoptDupDuring = (DeoptimizingNode.DeoptDuring) deoptDup;
                            assert !deoptDupDuring.hasSideEffect() : "can't use stateBefore as stateDuring for state split " + deoptDupDuring;
                            deoptDupDuring.setStateDuring(state);
                        }
                        if (deoptDup instanceof DeoptimizingNode.DeoptAfter) {
                            DeoptimizingNode.DeoptAfter deoptDupAfter = (DeoptimizingNode.DeoptAfter) deoptDup;
                            assert !deoptDupAfter.hasSideEffect() : "can't use stateBefore as stateAfter for state split " + deoptDupAfter;
                            deoptDupAfter.setStateAfter(state);
                        }
                    }
                }
            }
            // notify instrumentation nodes of the postInlineInstrumentation event
            for (Node replacee : duplicates.values()) {
                if (replacee instanceof InstrumentationInliningCallback) {
                    ((InstrumentationInliningCallback) replacee).postInlineInstrumentation(instrumentationNode);
                }
            }

            graph.removeFixed(instrumentationNode);
        }

        new CanonicalizerPhase().apply(graph, context);
    }

}
