/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.graphio.GraphStructure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.getOptions;

class PolymorphicSpecializeDump {

    public static void dumpPolymorphicSpecialize(List<Node> toDump, List<OptimizedDirectCallNode> knownCallNodes) {
        assert toDump.size() > 0;
        assert knownCallNodes.size() > 0;
        final DebugContext debugContext = DebugContext.create(getOptions(), Collections.emptyList());
        try {
            Collections.reverse(toDump);
            PolymorphicSpecializeDump.PolymorphicSpecializeGraph graph = new PolymorphicSpecializeDump.PolymorphicSpecializeGraph(knownCallNodes, toDump);
            final GraphOutput<PolymorphicSpecializeGraph, ?> output = debugContext.buildOutput(GraphOutput.newBuilder(new PolymorphicSpecializeDump.PolymorphicSpecializeGraphStructure()));
            output.beginGroup(graph, "Polymorphic Specialize [" + knownCallNodes.get(0).getCurrentCallTarget() + "]" , "Polymorphic Specialize", null, 0, null);
            output.print(graph, null, 0, toDump.get(toDump.size() - 1).toString());
            output.endGroup();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class PolymorphicSpecializeGraph {
        int idCounter = 0;
        final List<DumpNode> nodes = new ArrayList<>();

        class DumpNode {

            DumpNode(Node node) {
                this.node = node;
            }

            final Node node;
            final int id = idCounter++;
            DumpEdge edge;
        }

        class DumpEdge {
            DumpEdge(DumpNode node) {
                this.node = node;
            }

            final DumpNode node;
        }

        enum DumpEdgeEnum {
            CHILD
        }

        DumpNode makeNode(Node node) {
            DumpNode n = new DumpNode(node);
            nodes.add(n);
            return n;
        }

        PolymorphicSpecializeGraph(List<OptimizedDirectCallNode> needsSplitCallNodes, List<Node> nodeChain) {
            DumpNode last = null;
            for (int i = 0; i < nodeChain.size(); i++) {
                if (i == 0) {
                    for (OptimizedDirectCallNode callNode : needsSplitCallNodes) {
                        makeNode(callNode);
                    }
                    last = makeNode(nodeChain.get(i));
                    for (DumpNode dumpNode : nodes) {
                        dumpNode.edge = new DumpEdge(last);
                    }
                } else {
                    DumpNode n = makeNode(nodeChain.get(i));
                    last.edge = new DumpEdge(n);
                    last = n;
                }
            }
        }
    }

    static class PolymorphicSpecializeGraphStructure implements GraphStructure<PolymorphicSpecializeGraph, PolymorphicSpecializeGraph.DumpNode, PolymorphicSpecializeGraph.DumpNode, PolymorphicSpecializeGraph.DumpEdge> {

        @Override
        public PolymorphicSpecializeGraph graph(PolymorphicSpecializeGraph currentGraph, Object obj) {
            return (obj instanceof PolymorphicSpecializeGraph) ? (PolymorphicSpecializeGraph) obj : null;
        }

        @Override
        public Iterable<? extends PolymorphicSpecializeGraph.DumpNode> nodes(PolymorphicSpecializeGraph graph) {
            return graph.nodes;
        }

        @Override
        public int nodesCount(PolymorphicSpecializeGraph graph) {
            return graph.nodes.size();
        }

        @Override
        public int nodeId(PolymorphicSpecializeGraph.DumpNode node) {
            return node.id;
        }

        @Override
        public boolean nodeHasPredecessor(PolymorphicSpecializeGraph.DumpNode node) {
            return false;
        }

        @Override
        public void nodeProperties(PolymorphicSpecializeGraph graph, PolymorphicSpecializeGraph.DumpNode node, Map<String, ? super Object> properties) {
            properties.put("label", node.node.toString());
            properties.put("ROOT?", node.node instanceof RootNode);
            properties.put("LEAF?", node.edge == null);
            properties.put("RootNode", node.node.getRootNode());
            properties.putAll(node.node.getDebugProperties());
            properties.put("SourceSection", node.node.getSourceSection());
            if (Introspection.isIntrospectable(node.node)) {
                final List<Introspection.SpecializationInfo> specializations = Introspection.getSpecializations(node.node);
                for (Introspection.SpecializationInfo specialization : specializations) {
                    properties.put(specialization.getMethodName() + ".isActive", specialization.isActive());
                    properties.put(specialization.getMethodName() + ".isExcluded", specialization.isExcluded());
                    properties.put(specialization.getMethodName() + ".instances", specialization.getInstances());
                }
            }
        }

        @Override
        public PolymorphicSpecializeGraph.DumpNode node(Object obj) {
            return (obj instanceof PolymorphicSpecializeGraph.DumpNode) ? (PolymorphicSpecializeGraph.DumpNode) obj : null;
        }

        @Override
        public PolymorphicSpecializeGraph.DumpNode nodeClass(Object obj) {
            return (obj instanceof PolymorphicSpecializeGraph.DumpNode) ? (PolymorphicSpecializeGraph.DumpNode) obj : null;

        }

        @Override
        public PolymorphicSpecializeGraph.DumpNode classForNode(PolymorphicSpecializeGraph.DumpNode node) {
            return node;
        }

        @Override
        public String nameTemplate(PolymorphicSpecializeGraph.DumpNode nodeClass) {
            return "{p#label}";
        }

        @Override
        public Object nodeClassType(PolymorphicSpecializeGraph.DumpNode nodeClass) {
            return nodeClass.getClass();
        }

        @Override
        public PolymorphicSpecializeGraph.DumpEdge portInputs(PolymorphicSpecializeGraph.DumpNode nodeClass) {
            return null;
        }

        @Override
        public PolymorphicSpecializeGraph.DumpEdge portOutputs(PolymorphicSpecializeGraph.DumpNode nodeClass) {
            return nodeClass.edge;
        }

        @Override
        public int portSize(PolymorphicSpecializeGraph.DumpEdge port) {
            return port == null ? 0 : 1;
        }

        @Override
        public boolean edgeDirect(PolymorphicSpecializeGraph.DumpEdge port, int index) {
            return port != null;
        }

        @Override
        public String edgeName(PolymorphicSpecializeGraph.DumpEdge port, int index) {
            return "";
        }

        @Override
        public Object edgeType(PolymorphicSpecializeGraph.DumpEdge port, int index) {
            return PolymorphicSpecializeGraph.DumpEdgeEnum.CHILD;
        }

        @Override
        public Collection<? extends PolymorphicSpecializeGraph.DumpNode> edgeNodes(PolymorphicSpecializeGraph graph, PolymorphicSpecializeGraph.DumpNode node, PolymorphicSpecializeGraph.DumpEdge port, int index) {
            return Collections.singleton(node.edge.node);
        }
    }
}
