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
package com.oracle.max.graal.compiler.util;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.GraphUtil.ColorSplitingLambda;
import com.oracle.max.graal.compiler.util.GraphUtil.ColoringLambda;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public class LoopUtil {

    public static class Loop {
        private final LoopBegin loopBegin;
        private NodeBitMap nodes;
        private Loop parent;
        private NodeBitMap exits;
        public Loop(LoopBegin loopBegin, NodeBitMap nodes, NodeBitMap exits) {
            this.loopBegin = loopBegin;
            this.nodes = nodes;
            this.exits = exits;
        }

        public LoopBegin loopBegin() {
            return loopBegin;
        }

        public NodeBitMap nodes() {
            return nodes;
        }

        public Loop parent() {
            return parent;
        }

        public NodeBitMap exits() {
            return exits;
        }

        public void setParent(Loop parent) {
            this.parent = parent;
        }

        public boolean isChild(Loop loop) {
            return loop.parent != null && (loop.parent == this || loop.parent.isChild(this));
        }
    }

    private static class PeelingResult {
        public final FixedNode begin;
        public final FixedNode end;
        public final NodeMap<StateSplit> exits;
        public final NodeBitMap unaffectedExits;
        public final NodeMap<Placeholder> phis;
        public final NodeMap<Node> phiInits;
        public final NodeMap<Node> dataOut;
        public final NodeBitMap exitFrameStates;
        public PeelingResult(FixedNode begin, FixedNode end, NodeMap<StateSplit> exits, NodeMap<Placeholder> phis, NodeMap<Node> phiInits, NodeMap<Node> dataOut, NodeBitMap unaffectedExits, NodeBitMap exitFrameStates) {
            this.begin = begin;
            this.end = end;
            this.exits = exits;
            this.phis = phis;
            this.phiInits = phiInits;
            this.dataOut = dataOut;
            this.unaffectedExits = unaffectedExits;
            this.exitFrameStates = exitFrameStates;
        }
    }

    public static List<Loop> computeLoops(Graph graph) {
        List<Loop> loops = new LinkedList<LoopUtil.Loop>();
        for (LoopBegin loopBegin : graph.getNodes(LoopBegin.class)) {
            NodeBitMap nodes = computeLoopNodes(loopBegin);
            NodeBitMap exits = computeLoopExits(loopBegin, nodes);
            loops.add(new Loop(loopBegin, nodes, exits));
        }
        for (Loop loop : loops) {
            for (Loop other : loops) {
                if (other != loop && other.nodes().isMarked(loop.loopBegin())) {
                    if (loop.parent() == null || loop.parent().nodes().isMarked(other.loopBegin())) {
                        loop.setParent(other);
                    }
                }
            }
        }
        return loops;
    }

    public static NodeBitMap computeLoopExits(LoopBegin loopBegin, NodeBitMap nodes) {
        Graph graph = loopBegin.graph();
        NodeBitMap exits = graph.createNodeBitMap();
        for (Node n : markUpCFG(loopBegin, loopBegin.loopEnd())) {
            if (IdentifyBlocksPhase.trueSuccessorCount(n) > 1) {
                for (Node sux : n.cfgSuccessors()) {
                    if (!nodes.isMarked(sux) && sux instanceof FixedNode) {
                        exits.mark(sux);
                    }
                }
            }
        }
        return exits;
    }

    public static NodeBitMap computeLoopNodes(LoopBegin loopBegin) {
        return computeLoopNodesFrom(loopBegin, loopBegin.loopEnd());
    }
    private static boolean recurse = false;
    public static NodeBitMap computeLoopNodesFrom(LoopBegin loopBegin, FixedNode from) {
        NodeFlood workData1 = loopBegin.graph().createNodeFlood();
        NodeFlood workData2 = loopBegin.graph().createNodeFlood();
        NodeBitMap loopNodes = markUpCFG(loopBegin, from);
        loopNodes.mark(loopBegin);
        for (Node n : loopNodes) {
            workData1.add(n);
            workData2.add(n);
        }
        NodeBitMap inOrAfter = loopBegin.graph().createNodeBitMap();
        for (Node n : workData1) {
            markWithState(n, inOrAfter);
            for (Node usage : n.dataUsages()) {
                if (usage instanceof Phi) { // filter out data graph cycles
                    Phi phi = (Phi) usage;
                    if (!phi.isDead()) {
                        Merge merge = phi.merge();
                        if (merge instanceof LoopBegin) {
                            LoopBegin phiLoop = (LoopBegin) merge;
                            int backIndex = phiLoop.phiPredecessorIndex(phiLoop.loopEnd());
                            if (phi.valueAt(backIndex) == n) {
                                continue;
                            }
                        }
                    }
                }
                workData1.add(usage);
            }
        }
        NodeBitMap inOrBefore = loopBegin.graph().createNodeBitMap();
        for (Node n : workData2) {
            //markWithState(n, inOrBefore);
            inOrBefore.mark(n);
            if (n instanceof Phi) { // filter out data graph cycles
                Phi phi = (Phi) n;
                if (!phi.isDead()) {
                    int backIndex = -1;
                    Merge merge = phi.merge();
                    if (!loopNodes.isMarked(merge) && merge instanceof LoopBegin) {
                        LoopBegin phiLoop = (LoopBegin) merge;
                        backIndex = phiLoop.phiPredecessorIndex(phiLoop.loopEnd());
                    }
                    for (int i = 0; i < phi.valueCount(); i++) {
                        if (i != backIndex) {
                            workData2.add(phi.valueAt(i));
                        }
                    }
                }
            } else {
                for (Node input : n.dataInputs()) {
                    workData2.add(input);
                }
            }
            if (n instanceof Merge) { //add phis & counters
                for (Node usage : n.dataUsages()) {
                    workData2.add(usage);
                }
            }
            if (n instanceof StateSplit) {
                FrameState stateAfter = ((StateSplit) n).stateAfter();
                if (stateAfter != null) {
                    workData2.add(stateAfter);
                }
            }
        }
        /*if (!recurse) {
            recurse = true;
            GraalCompilation compilation = GraalCompilation.compilation();
            if (compilation.compiler.isObserved()) {
                Map<String, Object> debug = new HashMap<String, Object>();
                debug.put("loopNodes", loopNodes);
                debug.put("inOrAfter", inOrAfter);
                debug.put("inOrBefore", inOrBefore);
                compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "Compute loop nodes loop#" + loopBegin.id(), loopBegin.graph(), true, false, debug));
            }
            recurse = false;
        }*/
        inOrAfter.setIntersect(inOrBefore);
        loopNodes.setUnion(inOrAfter);
        return loopNodes;
    }

    private static NodeBitMap markUpCFG(LoopBegin loopBegin, FixedNode from) {
        NodeFlood workCFG = loopBegin.graph().createNodeFlood();
        workCFG.add(from);
        NodeBitMap loopNodes = loopBegin.graph().createNodeBitMap();
        for (Node n : workCFG) {
            if (n == loopBegin) {
                continue;
            }
            loopNodes.mark(n);
            if (n instanceof LoopBegin) {
                workCFG.add(((LoopBegin) n).loopEnd());
            }
            for (Node pred : n.cfgPredecessors()) {
                workCFG.add(pred);
            }
        }
        return loopNodes;
    }

    public static void inverseLoop(Loop loop, If split) {
        assert loop.nodes().isMarked(split);
        FixedNode noExit = split.trueSuccessor();
        FixedNode exit = split.falseSuccessor();
        if (loop.nodes().isMarked(exit) && !loop.nodes().isMarked(noExit)) {
            FixedNode tmp = noExit;
            noExit = exit;
            exit = tmp;
        }
        assert !loop.nodes().isMarked(exit);
        assert loop.nodes().isMarked(noExit);

        PeelingResult peeling = preparePeeling(loop, split);
        rewirePeeling(peeling, loop, split);
        // TODO (gd) move peeled part to the end, rewire dataOut
    }

    public static void peelLoop(Loop loop) {
        LoopEnd loopEnd = loop.loopBegin().loopEnd();
        PeelingResult peeling = preparePeeling(loop, loopEnd);
        GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After peeling preparation", loopEnd.graph(), true, false));
        }
        /*System.out.println("Peeling : ");
        System.out.println(" begin = " + peeling.begin);
        System.out.println(" end = " + peeling.end);
        System.out.println(" Phis :");
        for (Entry<Node, Placeholder> entry : peeling.phis.entries()) {
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println(" Exits :");
        for (Entry<Node, StateSplit> entry : peeling.exits.entries()) {
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println(" PhiInits :");
        for (Entry<Node, Node> entry : peeling.phiInits.entries()) {
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println(" DataOut :");
        for (Entry<Node, Node> entry : peeling.dataOut.entries()) {
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        }*/
        rewirePeeling(peeling, loop, loopEnd);
        /*if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After rewirePeeling", loopEnd.graph(), true, false));
        }*/
        // update parents
        Loop parent = loop.parent();
        while (parent != null) {
            parent.nodes = computeLoopNodes(parent.loopBegin);
            parent.exits = computeLoopExits(parent.loopBegin, parent.nodes);
            parent = parent.parent;
        }
        GraalMetrics.LoopsPeeled++;
    }

    private static void rewirePeeling(PeelingResult peeling, Loop loop, FixedNode from) {
        LoopBegin loopBegin = loop.loopBegin();
        Graph graph = loopBegin.graph();
        Node loopPred = loopBegin.singlePredecessor();
        loopPred.successors().replace(loopBegin.forwardEdge(), peeling.begin);
        NodeBitMap loopNodes = loop.nodes();
        Node originalLast = from;
        if (originalLast == loopBegin.loopEnd()) {
            originalLast = loopBegin.loopEnd().singlePredecessor();
        }
        int size = originalLast.successors().size();
        boolean found = false;
        for (int i = 0; i < size; i++) {
            Node sux = originalLast.successors().get(i);
            if (sux == null) {
                continue;
            }
            if (loopNodes.isMarked(sux)) {
                assert !found;
                peeling.end.successors().set(i, loopBegin.forwardEdge());
                found = true;
            }
        }
        assert found;
        int phiInitIndex = loopBegin.phiPredecessorIndex(loopBegin.forwardEdge());
        for (Entry<Node, Placeholder> entry : peeling.phis.entries()) {
            Phi phi = (Phi) entry.getKey();
            Placeholder p = entry.getValue();
            Value init = phi.valueAt(phiInitIndex);
            p.replaceAndDelete(init);
            for (Entry<Node, Node> dataEntry : peeling.dataOut.entries()) {
                if (dataEntry.getValue() == p) {
                    dataEntry.setValue(init);
                    //System.out.println("Patch dataOut : " + dataEntry.getKey() + " -> " + dataEntry.getValue());
                }
            }
        }
        for (Entry<Node, Node> entry : peeling.phiInits.entries()) {
            Phi phi = (Phi) entry.getKey();
            Node newInit = entry.getValue();
            phi.setValueAt(phiInitIndex, (Value) newInit);
        }

        if (from == loopBegin.loopEnd()) {
            for (LoopCounter counter : loopBegin.counters()) {
                counter.setInit(new IntegerAdd(counter.kind, counter.init(), counter.stride(), graph));
            }
        }
        NodeMap<NodeMap<Value>> newExitValues = graph.createNodeMap();
        List<Node> exitPoints = new LinkedList<Node>();
        for (Node exit : peeling.unaffectedExits) {
            exitPoints.add(exit);
        }
        for (Entry<Node, StateSplit> entry : peeling.exits.entries()) {
            StateSplit original = (StateSplit) entry.getKey();
            StateSplit newExit = entry.getValue();
            EndNode oEnd = new EndNode(graph);
            EndNode nEnd = new EndNode(graph);
            Merge merge = new Merge(graph);
            FrameState originalState = original.stateAfter();
            merge.addEnd(nEnd);
            merge.addEnd(oEnd);
            merge.setStateAfter(originalState.duplicate(originalState.bci, true));
            merge.setNext(original.next());
            original.setNext(oEnd);
            newExit.setNext(nEnd);
            exitPoints.add(original);
            exitPoints.add(newExit);
        }

        for (Entry<Node, StateSplit> entry : peeling.exits.entries()) {
            StateSplit original = (StateSplit) entry.getKey();
            EndNode oEnd = (EndNode) original.next();
            Merge merge = oEnd.merge();
            EndNode nEnd = merge.endAt(1 - merge.phiPredecessorIndex(oEnd));
            Node newExit = nEnd.singlePredecessor();
            for (Entry<Node, Node> dataEntry : peeling.dataOut.entries()) {
                Node originalValue = dataEntry.getKey();
                Node newValue = dataEntry.getValue();
                NodeMap<Value> phiMap = newExitValues.get(originalValue);
                if (phiMap == null) {
                    phiMap = graph.createNodeMap();
                    newExitValues.set(originalValue, phiMap);
                }
                phiMap.set(original, (Value) originalValue);
                phiMap.set(newExit, (Value) newValue);
            }
        }

        for (Entry<Node, NodeMap<Value>> entry : newExitValues.entries()) {
            Value original = (Value) entry.getKey();
            NodeMap<Value> pointToValue = entry.getValue();
            for (Node exit : exitPoints) {
                Node valueAtExit = pointToValue.get(exit);
                if (valueAtExit == null) {
                    pointToValue.set(exit, original);
                }
            }
        }

        replaceValuesAtLoopExits(newExitValues, loop, exitPoints, peeling.exitFrameStates);
    }

    private static void replaceValuesAtLoopExits(final NodeMap<NodeMap<Value>> newExitValues, Loop loop, List<Node> exitPoints, final NodeBitMap exitFrameStates) {
        Graph graph = loop.loopBegin().graph();
        final NodeMap<Node> colors = graph.createNodeMap();

        // prepare inital colors
        for (Node exitPoint : exitPoints) {
            colors.set(exitPoint, exitPoint);
        }

        /*System.out.println("newExitValues");
        for (Entry<Node, NodeMap<Value>> entry : newExitValues.entries()) {
            System.out.println(" - " + entry.getKey() + " :");
            for (Entry<Node, Value> entry2 : entry.getValue().entries()) {
                System.out.println("    + " + entry2.getKey() + " -> " + entry2.getValue());
            }
        }*/

        // color
        GraphUtil.colorCFGDown(colors, new ColoringLambda<Node>() {
            @Override
            public Node color(Iterable<Node> incomming, Merge merge) {
                Node color = null;
                for (Node c : incomming) {
                    if (c == null) {
                        return null;
                    }
                    if (color == null) {
                        color = c;
                    } else if (color != c) {
                        return merge;
                    }
                }
                return color;
            }
            @Override
            public Node danglingColor(Iterable<Node> incomming, Merge merge) {
                Node color = null;
                for (Node c : incomming) {
                    if (color == null) {
                        color = c;
                    } else if (color != c) {
                        return merge;
                    }
                }
                assert color != null;
                return color;
            }
        });

        final NodeBitMap inOrBefore = inOrBefore(loop);

        GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved()) {
            Map<String, Object> debug = new HashMap<String, Object>();
            debug.put("loopExits", colors);
            debug.put("inOrBefore", inOrBefore);
            debug.put("exitFrameStates", exitFrameStates);
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After coloring", graph, true, false, debug));
        }

        GraphUtil.splitFromColoring(colors, new ColorSplitingLambda<Node>(){
            @Override
            public void fixSplit(Node oldNode, Node newNode, Node color) {
                assert color != null;
                this.fixNode(newNode, color);
            }
            private Value getValueAt(Node point, NodeMap<Value> valueMap, CiKind kind) {
                Value value = valueMap.get(point);
                if (value != null) {
                    //System.out.println("getValueAt(" + point + ", valueMap, kind) = (cached) " + value);
                    return value;
                }
                Merge merge = (Merge) point;
                ArrayList<Value> values = new ArrayList<Value>(merge.phiPredecessorCount());
                Value v = null;
                boolean createPhi = false;
                for (EndNode end : merge.cfgPredecessors()) {
                    Value valueAt = getValueAt(colors.get(end), valueMap, kind);
                    if (v == null) {
                        v = valueAt;
                    } else if (v != valueAt) {
                        createPhi = true;
                    }
                    values.add(valueAt);
                }
                if (createPhi) {
                    Phi phi = new Phi(kind, merge, merge.graph());
                    valueMap.set(point, phi);
                    for (EndNode end : merge.cfgPredecessors()) {
                        phi.addInput(getValueAt(colors.get(end), valueMap, kind));
                    }
                    //System.out.println("getValueAt(" + point + ", valueMap, kind) = (new-phi) " + phi);
                    return phi;
                } else {
                    assert v != null;
                    valueMap.set(point, v);
                    //System.out.println("getValueAt(" + point + ", valueMap, kind) = (unique) " + v);
                    return v;
                }
            }
            @Override
            public boolean explore(Node n) {
                return (!exitFrameStates.isNew(n) && exitFrameStates.isMarked(n))
                || (!inOrBefore.isNew(n) && !inOrBefore.isMarked(n) && n.inputs().size() > 0 && !danglingMergeFrameState(n)); //TODO (gd) hum
            }
            public boolean danglingMergeFrameState(Node n) {
                if (!(n instanceof FrameState)) {
                    return false;
                }
                Merge block = ((FrameState) n).block();
                return block != null && colors.get(block.next()) == null;
            }
            @Override
            public void fixNode(Node node, Node color) {
                //System.out.println("fixNode(" + node + ", " + color + ")");
                if (color == null) {
                    // 'white' it out : make non-explorable
                    exitFrameStates.clear(node);
                    inOrBefore.mark(node);
                } else {
                    for (int i = 0; i < node.inputs().size(); i++) {
                        Node input = node.inputs().get(i);
                        if (input == null || newExitValues.isNew(input)) {
                            continue;
                        }
                        NodeMap<Value> valueMap = newExitValues.get(input);
                        if (valueMap != null) {
                            Value replacement = getValueAt(color, valueMap, ((Value) input).kind);
                            node.inputs().set(i, replacement);
                        }
                    }
                }
            }
            @Override
            public Value fixPhiInput(Value input, Node color) {
                if (newExitValues.isNew(input)) {
                    return input;
                }
                NodeMap<Value> valueMap = newExitValues.get(input);
                if (valueMap != null) {
                    return getValueAt(color, valueMap, input.kind);
                }
                return input;
            }
            @Override
            public List<Node> parentColors(Node color) {
                if (!(color instanceof Merge)) {
                    return Collections.emptyList();
                }
                Merge merge = (Merge) color;
                List<Node> parentColors = new ArrayList<Node>(merge.phiPredecessorCount());
                for (Node pred : merge.phiPredecessors()) {
                    parentColors.add(colors.get(pred));
                }
                return parentColors;
            }
            @Override
            public Merge merge(Node color) {
                return (Merge) color;
            }
        });

        /*if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After split from colors", graph, true, false));
        }*/
    }

    private static PeelingResult preparePeeling(Loop loop, FixedNode from) {
        LoopBegin loopBegin = loop.loopBegin();
        Graph graph = loopBegin.graph();
        NodeBitMap marked = computeLoopNodesFrom(loopBegin, from);
        GraalCompilation compilation = GraalCompilation.compilation();
        /*if (compilation.compiler.isObserved()) {
            Map<String, Object> debug = new HashMap<String, Object>();
            debug.put("marked", marked);
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After computeLoopNodesFrom", loopBegin.graph(), true, false, debug));
        }*/
        if (from == loopBegin.loopEnd()) {
            clearWithState(from, marked);
        }
        clearWithState(loopBegin, marked);
        Map<Node, Node> replacements = new HashMap<Node, Node>();
        NodeMap<Placeholder> phis = graph.createNodeMap();
        NodeMap<StateSplit> exits = graph.createNodeMap();
        NodeBitMap unaffectedExits = graph.createNodeBitMap();
        NodeBitMap clonedExits = graph.createNodeBitMap();
        NodeBitMap exitFrameStates = graph.createNodeBitMap();
        for (Node exit : loop.exits()) {
            if (marked.isMarked(exit.singlePredecessor())) {
                StateSplit pExit = findNearestMergableExitPoint(exit, marked);
                markWithState(pExit, marked);
                clonedExits.mark(pExit);
                FrameState stateAfter = pExit.stateAfter();
                while (stateAfter != null) {
                    exitFrameStates.mark(stateAfter);
                    stateAfter = stateAfter.outerFrameState();
                }
            } else {
                unaffectedExits.mark(exit);
            }
        }

        NodeBitMap dataOut = graph.createNodeBitMap();
        for (Node n : marked) {
            if (!(n instanceof FrameState)) {
                for (Node usage : n.dataUsages()) {
                    if ((!marked.isMarked(usage) && !((usage instanceof Phi) && ((Phi) usage).merge() != loopBegin))
                                    || (marked.isMarked(usage) && exitFrameStates.isMarked(usage))) {
                        dataOut.mark(n);
                        break;
                    }
                }
            }
        }

        for (Node n : marked) {
            if (n instanceof Phi && ((Phi) n).merge() == loopBegin) {
                Placeholder p = new Placeholder(graph);
                replacements.put(n, p);
                phis.set(n, p);
                marked.clear(n);
            }
            for (Node input : n.dataInputs()) {
                if (!marked.isMarked(input) && (!(input instanceof Phi) || ((Phi) input).merge() != loopBegin) && replacements.get(input) == null) {
                    replacements.put(input, input);
                }
            }
        }

        //GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved()) {
            Map<String, Object> debug = new HashMap<String, Object>();
            debug.put("marked", marked);
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "Before addDuplicate loop#" + loopBegin.id(), loopBegin.graph(), true, false, debug));
        }

        Map<Node, Node> duplicates = graph.addDuplicate(marked, replacements);

        /*System.out.println("Dup mapping :");
        for (Entry<Node, Node> entry : duplicates.entrySet()) {
            System.out.println(" - " + entry.getKey().id() + " -> " + entry.getValue().id());
        }*/

        NodeMap<Node> dataOutMapping = graph.createNodeMap();
        for (Node n : dataOut) {
            Node newOut = duplicates.get(n);
            if (newOut == null) {
                newOut = replacements.get(n);
            }
            assert newOut != null;
            dataOutMapping.set(n, newOut);
        }

        for (Node n : clonedExits) {
            exits.set(n, (StateSplit) duplicates.get(n));
        }

        NodeMap<Node> phiInits = graph.createNodeMap();
        if (from == loopBegin.loopEnd()) {
            int backIndex = loopBegin.phiPredecessorIndex(loopBegin.loopEnd());
            int fowardIndex = loopBegin.phiPredecessorIndex(loopBegin.forwardEdge());
            for (Phi phi : loopBegin.phis()) {
                Value backValue = phi.valueAt(backIndex);
                if (marked.isMarked(backValue)) {
                    phiInits.set(phi, duplicates.get(backValue));
                } else if (backValue instanceof Phi && ((Phi) backValue).merge() == loopBegin) {
                    Phi backPhi = (Phi) backValue;
                    phiInits.set(phi, backPhi.valueAt(fowardIndex));
                } else {
                    phiInits.set(phi, backValue);
                }
            }
        }

        FixedNode newBegin = (FixedNode) duplicates.get(loopBegin.next());
        FixedNode newFrom = (FixedNode) duplicates.get(from == loopBegin.loopEnd() ? from.singlePredecessor() : from);
        return new PeelingResult(newBegin, newFrom, exits, phis, phiInits, dataOutMapping, unaffectedExits, exitFrameStates);
    }

    private static StateSplit findNearestMergableExitPoint(Node exit, NodeBitMap marked) {
        // TODO (gd) find appropriate point : will be useful if a loop exit goes "up" as a result of making a branch dead in the loop body
        return (StateSplit) exit;
    }

    private static NodeBitMap inOrBefore(Loop loop) {
        Graph graph = loop.loopBegin().graph();
        NodeBitMap inOrBefore = graph.createNodeBitMap();
        NodeFlood work = graph.createNodeFlood();
        NodeBitMap loopNodes = loop.nodes();
        work.addAll(loopNodes);
        for (Node n : work) {
            inOrBefore.mark(n);
            for (Node pred : n.predecessors()) {
                work.add(pred);
            }
            if (n instanceof Phi) { // filter out data graph cycles
                Phi phi = (Phi) n;
                if (!phi.isDead()) {
                    int backIndex = -1;
                    Merge merge = phi.merge();
                    if (!loopNodes.isNew(merge) && !loopNodes.isMarked(merge) && merge instanceof LoopBegin) {
                        LoopBegin phiLoop = (LoopBegin) merge;
                        backIndex = phiLoop.phiPredecessorIndex(phiLoop.loopEnd());
                    }
                    for (int i = 0; i < phi.valueCount(); i++) {
                        if (i != backIndex) {
                            work.add(phi.valueAt(i));
                        }
                    }
                }
            } else {
                for (Node in : n.inputs()) {
                    if (in != null) {
                        work.add(in);
                    }
                }
                if (n instanceof LoopBegin) {
                    Loop p = loop.parent;
                    boolean isParent = false;
                    while (p != null) {
                        if (p.loopBegin() == n) {
                            isParent = true;
                            break;
                        }
                        p = p.parent;
                    }
                    if (!isParent) {
                        work.add(((LoopBegin) n).loopEnd());
                    }
                }
            }
        }
        return inOrBefore;
    }

    private static void markWithState(Node n, NodeBitMap map) {
        map.mark(n);
        if (n instanceof StateSplit) {
            FrameState stateAfter = ((StateSplit) n).stateAfter();
            while (stateAfter != null) {
                map.mark(stateAfter);
                stateAfter = stateAfter.outerFrameState();
            }
        }
    }

    private static void clearWithState(Node n, NodeBitMap map) {
        map.clear(n);
        if (n instanceof StateSplit) {
            FrameState stateAfter = ((StateSplit) n).stateAfter();
            while (stateAfter != null) {
                map.clear(stateAfter);
                stateAfter = stateAfter.outerFrameState();
            }
        }
    }
}
