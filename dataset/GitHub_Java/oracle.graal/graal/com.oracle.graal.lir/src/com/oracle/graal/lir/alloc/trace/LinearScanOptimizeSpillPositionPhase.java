/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace;

import static com.oracle.graal.compiler.common.cfg.AbstractControlFlowGraph.*;
import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import com.oracle.graal.debug.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.alloc.trace.Interval.SpillState;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;

public final class LinearScanOptimizeSpillPositionPhase extends AllocationPhase {

    private static final DebugMetric betterSpillPos = Debug.metric("BetterSpillPosition");
    private static final DebugMetric betterSpillPosWithLowerProbability = Debug.metric("BetterSpillPositionWithLowerProbability");

    private final LinearScan allocator;

    LinearScanOptimizeSpillPositionPhase(LinearScan allocator) {
        this.allocator = allocator;
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        optimizeSpillPosition();
        allocator.printIntervals("After optimize spill position");
    }

    private void optimizeSpillPosition() {
        try (Indent indent0 = Debug.logAndIndent("OptimizeSpillPositions")) {
            LIRInsertionBuffer[] insertionBuffers = new LIRInsertionBuffer[allocator.getLIR().linearScanOrder().size()];
            for (Interval interval : allocator.intervals()) {
                optimizeInterval(insertionBuffers, interval);
            }
            for (LIRInsertionBuffer insertionBuffer : insertionBuffers) {
                if (insertionBuffer != null) {
                    assert insertionBuffer.initialized() : "Insertion buffer is nonnull but not initialized!";
                    insertionBuffer.finish();
                }
            }
        }
    }

    private void optimizeInterval(LIRInsertionBuffer[] insertionBuffers, Interval interval) {
        if (interval == null || !interval.isSplitParent() || interval.spillState() != SpillState.SpillInDominator) {
            return;
        }
        AbstractBlockBase<?> defBlock = allocator.blockForId(interval.spillDefinitionPos());
        AbstractBlockBase<?> spillBlock = null;
        Interval firstSpillChild = null;
        try (Indent indent = Debug.logAndIndent("interval %s (%s)", interval, defBlock)) {
            for (Interval splitChild : interval.getSplitChildren()) {
                if (isStackSlotValue(splitChild.location())) {
                    if (firstSpillChild == null || splitChild.from() < firstSpillChild.from()) {
                        firstSpillChild = splitChild;
                    } else {
                        assert firstSpillChild.from() < splitChild.from();
                    }
                    // iterate all blocks where the interval has use positions
                    for (AbstractBlockBase<?> splitBlock : blocksForInterval(splitChild)) {
                        if (dominates(defBlock, splitBlock)) {
                            Debug.log("Split interval %s, block %s", splitChild, splitBlock);
                            if (spillBlock == null) {
                                spillBlock = splitBlock;
                            } else {
                                spillBlock = commonDominator(spillBlock, splitBlock);
                                assert spillBlock != null;
                            }
                        }
                    }
                }
            }
            if (spillBlock == null) {
                Debug.log("not spill interval found");
                // no spill interval
                interval.setSpillState(SpillState.StoreAtDefinition);
                return;
            }
            Debug.log(3, "Spill block candidate (initial): %s", spillBlock);
            // move out of loops
            if (defBlock.getLoopDepth() < spillBlock.getLoopDepth()) {
                spillBlock = moveSpillOutOfLoop(defBlock, spillBlock);
            }
            Debug.log(3, "Spill block candidate (after loop optimizaton): %s", spillBlock);

            /*
             * The spill block is the begin of the first split child (aka the value is on the
             * stack).
             *
             * The problem is that if spill block has more than one predecessor, the values at the
             * end of the predecessors might differ. Therefore, we would need a spill move in all
             * predecessors. To avoid this we spill in the dominator.
             */
            assert firstSpillChild != null;
            if (!defBlock.equals(spillBlock) && spillBlock.equals(allocator.blockForId(firstSpillChild.from()))) {
                AbstractBlockBase<?> dom = spillBlock.getDominator();
                if (Debug.isLogEnabled()) {
                    Debug.log("Spill block (%s) is the beginning of a spill child -> use dominator (%s)", spillBlock, dom);
                }
                spillBlock = dom;
            }
            if (defBlock.equals(spillBlock)) {
                Debug.log(3, "Definition is the best choice: %s", defBlock);
                // definition is the best choice
                interval.setSpillState(SpillState.StoreAtDefinition);
                return;
            }
            assert dominates(defBlock, spillBlock);
            betterSpillPos.increment();
            if (Debug.isLogEnabled()) {
                Debug.log("Better spill position found (Block %s)", spillBlock);
            }

            if (defBlock.probability() <= spillBlock.probability()) {
                Debug.log(3, "Definition has lower probability %s (%f) is lower than spill block %s (%f)", defBlock, defBlock.probability(), spillBlock, spillBlock.probability());
                // better spill block has the same probability -> do nothing
                interval.setSpillState(SpillState.StoreAtDefinition);
                return;
            }

            LIRInsertionBuffer insertionBuffer = insertionBuffers[spillBlock.getId()];
            if (insertionBuffer == null) {
                insertionBuffer = new LIRInsertionBuffer();
                insertionBuffers[spillBlock.getId()] = insertionBuffer;
                insertionBuffer.init(allocator.getLIR().getLIRforBlock(spillBlock));
            }
            int spillOpId = allocator.getFirstLirInstructionId(spillBlock);
            // insert spill move
            AllocatableValue fromLocation = interval.getSplitChildAtOpId(spillOpId, OperandMode.DEF, allocator).location();
            AllocatableValue toLocation = LinearScan.canonicalSpillOpr(interval);
            LIRInstruction move = allocator.getSpillMoveFactory().createMove(toLocation, fromLocation);
            Debug.log(3, "Insert spill move %s", move);
            move.setId(LinearScan.DOMINATOR_SPILL_MOVE_ID);
            /*
             * We can use the insertion buffer directly because we always insert at position 1.
             */
            insertionBuffer.append(1, move);

            betterSpillPosWithLowerProbability.increment();
            interval.setSpillDefinitionPos(spillOpId);
        }
    }

    /**
     * Iterate over all {@link AbstractBlockBase blocks} of an interval.
     */
    private class IntervalBlockIterator implements Iterator<AbstractBlockBase<?>> {

        Range range;
        AbstractBlockBase<?> block;

        public IntervalBlockIterator(Interval interval) {
            range = interval.first();
            block = allocator.blockForId(range.from);
        }

        public AbstractBlockBase<?> next() {
            AbstractBlockBase<?> currentBlock = block;
            int nextBlockIndex = block.getLinearScanNumber() + 1;
            if (nextBlockIndex < allocator.sortedBlocks().size()) {
                block = allocator.sortedBlocks().get(nextBlockIndex);
                if (range.to <= allocator.getFirstLirInstructionId(block)) {
                    range = range.next;
                    if (range == Range.EndMarker) {
                        block = null;
                    } else {
                        block = allocator.blockForId(range.from);
                    }
                }
            } else {
                block = null;
            }
            return currentBlock;
        }

        public boolean hasNext() {
            return block != null;
        }
    }

    private Iterable<AbstractBlockBase<?>> blocksForInterval(Interval interval) {
        return new Iterable<AbstractBlockBase<?>>() {
            public Iterator<AbstractBlockBase<?>> iterator() {
                return new IntervalBlockIterator(interval);
            }
        };
    }

    private static AbstractBlockBase<?> moveSpillOutOfLoop(AbstractBlockBase<?> defBlock, AbstractBlockBase<?> spillBlock) {
        int defLoopDepth = defBlock.getLoopDepth();
        for (AbstractBlockBase<?> block = spillBlock.getDominator(); !defBlock.equals(block); block = block.getDominator()) {
            assert block != null : "spill block not dominated by definition block?";
            if (block.getLoopDepth() <= defLoopDepth) {
                assert block.getLoopDepth() == defLoopDepth : "Cannot spill an interval outside of the loop where it is defined!";
                return block;
            }
        }
        return defBlock;
    }
}
