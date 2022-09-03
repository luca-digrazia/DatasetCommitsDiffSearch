/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.schedule;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;


public class IdentifyBlocksPhase extends Phase {
    private final List<Block> blocks = new ArrayList<Block>();
    private NodeMap<Block> nodeToBlock;
    private Graph graph;
    private boolean scheduleAllNodes;
    private int loopCount;

    public IdentifyBlocksPhase(boolean scheduleAllNodes) {
        super(scheduleAllNodes ? "FullSchedule" : "PartSchedule", false);
        this.scheduleAllNodes = scheduleAllNodes;
    }


    @Override
    protected void run(Graph graph) {
        this.graph = graph;
        nodeToBlock = graph.createNodeMap();
        identifyBlocks();
    }

    public List<Block> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public NodeMap<Block> getNodeToBlock() {
        return nodeToBlock;
    }

    private Block createBlock() {
        Block b = new Block(blocks.size());
        blocks.add(b);
        return b;
    }

    public int loopCount() {
        return loopCount;
    }

    private Block assignBlockNew(Node n, Block b) {
        if (b == null) {
            b = createBlock();
        }

        assert nodeToBlock.get(n) == null;
        nodeToBlock.set(n, b);

        if (n instanceof Merge) {
            for (Node usage : n.usages()) {

                if (usage instanceof Phi) {
                    nodeToBlock.set(usage, b);
                }

                if (usage instanceof LoopCounter) {
                    nodeToBlock.set(usage, b);
                }

            }
        }
        if (b.lastNode() == null) {
            b.setFirstNode(n);
            b.setLastNode(n);
            b.getInstructions().add(n);
        } else {
            b.getInstructions().add(0, n);
            b.setFirstNode(n);
        }

        return b;
    }

    public static boolean isFixed(Node n) {
        return n != null && ((n instanceof FixedNode) || n == n.graph().start());
    }

    public static boolean isBlockEnd(Node n) {
        return trueSuccessorCount(n) > 1 || n instanceof Return || n instanceof Unwind || n instanceof Deoptimize;
    }

    private void print() {
        Block dominatorRoot = nodeToBlock.get(graph.start());
        System.out.println("Root = " + dominatorRoot);
        System.out.println("nodeToBlock :");
        System.out.println(nodeToBlock);
        System.out.println("Blocks :");
        for (Block b : blocks) {
            System.out.println(b + " [S:" + b.getSuccessors() + ", P:" + b.getPredecessors() + ", D:" + b.getDominators());
            System.out.println("  f " + b.firstNode());
            for (Node n : b.getInstructions()) {
                System.out.println("  - " + n);
            }
            System.out.println("  l " + b.lastNode());
        }
    }

    private void identifyBlocks() {

        // Identify blocks.
        for (Node n : graph.getNodes()) {
            if (n instanceof EndNode || n instanceof Return || n instanceof Unwind || n instanceof LoopEnd || n instanceof Deoptimize) {
                Block block = null;
                Node currentNode = n;
                while (nodeToBlock.get(currentNode) == null) {
                    if (block != null && (currentNode instanceof ControlSplit || trueSuccessorCount(currentNode) > 1)) {
                        // We are at a split node => start a new block.
                        block = null;
                    }
                    block = assignBlockNew(currentNode, block);
                    if (currentNode.predecessor() == null) {
                        // Either dead code or at a merge node => stop iteration.
                        break;
                    }
                    Node prev = currentNode;
                    currentNode = currentNode.predecessor();
                    assert !currentNode.isDeleted() : prev + " " + currentNode;
                }
            }
        }

        // Connect blocks.
        for (Block block : blocks) {
            Node n = block.firstNode();
            if (n instanceof Merge) {
                Merge m = (Merge) n;
                for (Node pred : m.cfgPredecessors()) {
                    Block predBlock = nodeToBlock.get(pred);
                    predBlock.addSuccessor(block);
                }
            } else {
                if (n.predecessor() != null) {
                    if (isFixed(n.predecessor())) {
                        Block predBlock = nodeToBlock.get(n.predecessor());
                        predBlock.addSuccessor(block);
                    }
                }
            }
        }

        computeDominators();


        if (scheduleAllNodes) {
            computeLoopInformation(); // Will make the graph cyclic.
            assignLatestPossibleBlockToNodes();
            sortNodesWithinBlocks();
        } else {
            computeJavaBlocks();
        }
    }

    private void computeLoopInformation() {

        // Add successors of loop end nodes. Makes the graph cyclic.
        for (Block block : blocks) {
            Node n = block.lastNode();
            if (n instanceof LoopEnd) {
                LoopEnd loopEnd = (LoopEnd) n;
                assert loopEnd.loopBegin() != null;
                Block loopBeginBlock = nodeToBlock.get(loopEnd.loopBegin());
                block.addSuccessor(loopBeginBlock);
                BitMap map = new BitMap(blocks.size());
                markBlocks(block, loopBeginBlock, map, loopCount++, block.loopDepth());
                assert loopBeginBlock.loopDepth() == block.loopDepth() && loopBeginBlock.loopIndex() == block.loopIndex();
            }
        }

//        for (Block block : blocks) {
//            TTY.println("Block B" + block.blockID() + " loopIndex=" + block.loopIndex() + ", loopDepth=" + block.loopDepth());
//        }
    }

    private void markBlocks(Block block, Block endBlock, BitMap map, int loopIndex, int initialDepth) {
        if (map.get(block.blockID())) {
            return;
        }

        map.set(block.blockID());
        if (block.loopDepth() <= initialDepth) {
            assert block.loopDepth() == initialDepth;
            block.setLoopIndex(loopIndex);
        }
        block.setLoopDepth(block.loopDepth() + 1);

        if (block == endBlock) {
            return;
        }

        for (Block pred : block.getPredecessors()) {
            markBlocks(pred, endBlock, map, loopIndex, initialDepth);
        }

        if (block.isLoopHeader()) {
            markBlocks(nodeToBlock.get(((LoopBegin) block.firstNode()).loopEnd()), endBlock, map, loopIndex, initialDepth);
        }
    }

    private void computeJavaBlocks() {

        for (Block b : blocks) {
            computeJavaBlock(b);
        }
    }

    private Block computeJavaBlock(Block b) {
        if (b.javaBlock() == null) {
            if (b.getPredecessors().size() == 0) {
                b.setJavaBlock(b);
            } else if (b.getPredecessors().size() == 1) {
                Block pred = b.getPredecessors().get(0);
                if (pred.getSuccessors().size() > 1) {
                    b.setJavaBlock(b);
                } else {
                    b.setJavaBlock(computeJavaBlock(pred));
                }
            } else {
                Block dominatorBlock = b.getPredecessors().get(0);
                for (int i = 1; i < b.getPredecessors().size(); ++i) {
                    dominatorBlock = getCommonDominator(dominatorBlock, b.getPredecessors().get(i));
                }
                BitMap blockMap = new BitMap(blocks.size());
                markPredecessors(b, dominatorBlock, blockMap);

                Block result = dominatorBlock;
                L1: for (Block curBlock : blocks) {
                    if (curBlock != b && blockMap.get(curBlock.blockID())) {
                        for (Block succ : curBlock.getSuccessors()) {
                            if (!blockMap.get(succ.blockID())) {
                                result = b;
                                break L1;
                            }
                        }
                    }
                }
                b.setJavaBlock(result);
            }
        }
        return b.javaBlock();
    }

    private void markPredecessors(Block b, Block stopBlock, BitMap blockMap) {
        if (blockMap.get(b.blockID())) {
            return;
        }
        blockMap.set(b.blockID());
        if (b != stopBlock) {
            for (Block pred : b.getPredecessors()) {
                markPredecessors(pred, stopBlock, blockMap);
            }
        }
    }

    private void assignLatestPossibleBlockToNodes() {
        for (Node n : graph.getNodes()) {
            assignLatestPossibleBlockToNode(n);
        }
    }

    private Block assignLatestPossibleBlockToNode(Node n) {
        if (n == null) {
            return null;
        }

        assert !n.isDeleted();

        Block prevBlock = nodeToBlock.get(n);
        if (prevBlock != null) {
            return prevBlock;
        }

        Block block = null;
        for (Node succ : n.successors()) {
            block = getCommonDominator(block, assignLatestPossibleBlockToNode(succ));
        }
        for (Node usage : n.usages()) {
            if (usage instanceof Phi) {
                Phi phi = (Phi) usage;
                Merge merge = phi.merge();
                Block mergeBlock = nodeToBlock.get(merge);
                assert mergeBlock != null : "no block for merge " + merge.id();
                for (int i = 0; i < phi.valueCount(); ++i) {
                    if (phi.valueAt(i) == n) {
                        if (mergeBlock.getPredecessors().size() <= i) {
                            TTY.println(merge.toString());
                            TTY.println(phi.toString());
                            TTY.println(merge.phiPredecessors().toString());
                            TTY.println(phi.inputs().toString());
                            TTY.println("value count: " + phi.valueCount());
                        }
                        block = getCommonDominator(block, mergeBlock.getPredecessors().get(i));
                    }
                }
            } else if (usage instanceof FrameState && ((FrameState) usage).block() != null) {
                Merge merge = ((FrameState) usage).block();
                for (Node pred : merge.cfgPredecessors()) {
                    block = getCommonDominator(block, nodeToBlock.get(pred));
                }
            } else if (usage instanceof LoopCounter) {
                LoopCounter counter = (LoopCounter) usage;
                if (n == counter.init() || n == counter.stride()) {
                    LoopBegin loopBegin = counter.loopBegin();
                    Block mergeBlock = nodeToBlock.get(loopBegin);
                    block = getCommonDominator(block, mergeBlock.dominator());
                }
            } else {
                block = getCommonDominator(block, assignLatestPossibleBlockToNode(usage));
            }
        }


        if (block != null) {
            if (GraalOptions.OptOptimisticSchedule) {
                block = scheduleOutOfLoops(n, block);
            }
            nodeToBlock.set(n, block);
            block.getInstructions().add(n);
        }
        return block;
    }

    private Block scheduleOutOfLoops(Node n, Block latestBlock) {
        Block cur = latestBlock;
        while (cur.loopDepth() != 0) {
            if (cur.isLoopHeader()) {
                assert cur.getPredecessors().size() == 2 : cur.getPredecessors().size();
                if (canSchedule(n, cur.getPredecessors().get(0))) {
                   // TTY.println("can schedule out of loop!" + n);
                    return scheduleOutOfLoops(n, cur.getPredecessors().get(0));
                } else {
                    break;
                }
            }
            Block prev = cur;
            cur = cur.dominator();

            // This must be a loop exit.
            if (cur.loopDepth() > prev.loopDepth()) {
//                TTY.println("break out because of different loop depth");
                break;
            }
        }
        return latestBlock;
    }

    private boolean canSchedule(Node n, Block block) {
        Set<Block> allowedBlocks = new HashSet<Block>();
        Block cur = block;
        while (cur != null) {
            allowedBlocks.add(cur);
            cur = cur.dominator();
        }
        // Now we know the allowed blocks for inputs and predecessors.
        return checkNodesAbove(allowedBlocks, n);
    }

    private boolean checkNodesAbove(Set<Block> allowedBlocks, Node n) {
        if (n == null) {
            return true;
        }

        if (nodeToBlock.get(n) != null) {
            return allowedBlocks.contains(nodeToBlock.get(n));
        } else {
            assert !(n instanceof Phi) : ((Phi) n).merge();
            for (Node input : n.inputs()) {
                if (!checkNodesAbove(allowedBlocks, input)) {
                    return false;
                }
            }
            if (n.predecessor() != null) {
                if (!checkNodesAbove(allowedBlocks, n.predecessor())) {
                    return false;
                }
            }
            return true;
        }
    }


    private Block getCommonDominator(Block a, Block b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return commonDominator(a, b);
    }

    private void sortNodesWithinBlocks() {
        NodeBitMap map = graph.createNodeBitMap();
        for (Block b : blocks) {
            sortNodesWithinBlocks(b, map);
        }
    }

    private void sortNodesWithinBlocks(Block b, NodeBitMap map) {
        List<Node> instructions = b.getInstructions();
        List<Node> sortedInstructions = new ArrayList<Node>(instructions.size() + 2);

        assert !map.isMarked(b.firstNode()) && nodeToBlock.get(b.firstNode()) == b;
        assert !map.isMarked(b.lastNode()) && nodeToBlock.get(b.lastNode()) == b;

        for (Node i : instructions) {
            addToSorting(b, i, sortedInstructions, map);
        }

        // Make sure that last node gets really last (i.e. when a frame state successor hangs off it).
        Node lastSorted = sortedInstructions.get(sortedInstructions.size() - 1);
        if (lastSorted != b.lastNode()) {
            int idx = sortedInstructions.indexOf(b.lastNode());
            boolean canNotMove = false;
            for (int i = idx + 1; i < sortedInstructions.size(); i++) {
                if (sortedInstructions.get(i).inputContains(b.lastNode())) {
                    canNotMove = true;
                    break;
                }
            }
            if (canNotMove) {
                assert !(b.lastNode() instanceof ControlSplit);
                //b.setLastNode(lastSorted);
            } else {
                sortedInstructions.remove(b.lastNode());
                sortedInstructions.add(b.lastNode());
            }
        }
        b.setInstructions(sortedInstructions);
//        TTY.println();
//        TTY.println("B" + b.blockID());
//        for (Node n : sortedInstructions) {
//            TTY.println("n=" + n);
//        }
    }

    private void addToSorting(Block b, Node i, List<Node> sortedInstructions, NodeBitMap map) {
        if (i == null || map.isMarked(i) || nodeToBlock.get(i) != b || i instanceof Phi || i instanceof Local || i instanceof LoopCounter) {
            return;
        }

        if (i instanceof WriteNode) {
            // TODO(tw): Make sure every ReadNode that is connected to the same memory state is executed before every write node.
            WriteNode wn = (WriteNode) i;
            // TODO: Iterate over variablePart.
//            wn.variableInputs();
        }

        FrameState state = null;
        WriteNode writeNode = null;
        for (Node input : i.inputs()) {
            if (input instanceof WriteNode && !map.isMarked(input) && nodeToBlock.get(input) == b) {
                writeNode = (WriteNode) input;
            } else if (input instanceof FrameState) {
                state = (FrameState) input;
            } else {
                addToSorting(b, input, sortedInstructions, map);
            }
        }

        if (i.predecessor() != null) {
            addToSorting(b, i.predecessor(), sortedInstructions, map);
        }

        map.mark(i);

        addToSorting(b, state, sortedInstructions, map);
        assert writeNode == null || !map.isMarked(writeNode);
        addToSorting(b, writeNode, sortedInstructions, map);

        // Now predecessors and inputs are scheduled => we can add this node.
        sortedInstructions.add(i);
    }

    private void computeDominators() {
        Block dominatorRoot = nodeToBlock.get(graph.start());
        assert dominatorRoot.getPredecessors().size() == 0;
        BitMap visited = new BitMap(blocks.size());
        visited.set(dominatorRoot.blockID());
        LinkedList<Block> workList = new LinkedList<Block>();
        for (Block block : blocks) {
            if (block.getPredecessors().size() == 0) {
                workList.add(block);
            }
        }

        int cnt = 0;
        while (!workList.isEmpty()) {
            if (cnt++ > blocks.size() * 20) {
                throw new RuntimeException("(ls) endless loop in computeDominators?");
            }
            Block b = workList.remove();

            List<Block> predecessors = b.getPredecessors();
            if (predecessors.size() == 1) {
                b.setDominator(predecessors.get(0));
            } else if (predecessors.size() > 0) {
                boolean delay = false;
                for (Block pred : predecessors) {
                    if (pred != dominatorRoot && pred.dominator() == null) {
                        delay = true;
                        break;
                    }
                }

                if (delay) {
                    workList.add(b);
                    continue;
                }

                Block dominator = null;
                for (Block pred : predecessors) {
                    if (dominator == null) {
                        dominator = pred;
                    } else {
                        dominator = commonDominator(dominator, pred);
                    }
                }
                b.setDominator(dominator);
            }

            for (Block succ : b.getSuccessors()) {
                if (!visited.get(succ.blockID())) {
                    visited.set(succ.blockID());
                    workList.add(succ);
                }
            }
        }
    }

    public Block commonDominator(Block a, Block b) {
        BitMap bitMap = new BitMap(blocks.size());
        Block cur = a;
        while (cur != null) {
            bitMap.set(cur.blockID());
            cur = cur.dominator();
        }

        cur = b;
        while (cur != null) {
            if (bitMap.get(cur.blockID())) {
                return cur;
            }
            cur = cur.dominator();
        }

        throw new IllegalStateException("no common dominator between " + a + " and " + b);
    }

    public static int trueSuccessorCount(Node n) {
        if (n == null) {
            return 0;
        }
        int i = 0;
        for (Node s : n.successors()) {
            if (isFixed(s)) {
                i++;
            }
        }
        return i;
    }
}
