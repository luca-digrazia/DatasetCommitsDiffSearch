/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.alloc;

import java.util.*;

import com.oracle.graal.compiler.util.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.cfg.*;

/**
 * This class performs basic optimizations on the control flow graph after LIR generation.
 */
final class ControlFlowOptimizer {

    /**
     * Performs control flow optimizations on the given LIR graph.
     * @param ir the LIR graph that should be optimized
     */
    public static void optimize(LIR ir) {
        ControlFlowOptimizer optimizer = new ControlFlowOptimizer(ir);
        List<Block> code = ir.codeEmittingOrder();
        //optimizer.reorderShortLoops(code);
        optimizer.deleteEmptyBlocks(code);
        ControlFlowOptimizer.deleteUnnecessaryJumps(code);
        //ControlFlowOptimizer.deleteJumpsToReturn(code);
    }

    private final LIR ir;

    private ControlFlowOptimizer(LIR ir) {
        this.ir = ir;
    }
/*
    private void reorderShortLoop(List<LIRBlock> code, LIRBlock headerBlock, int headerIdx) {
        int i = headerIdx + 1;
        int maxEnd = Math.min(headerIdx + GraalOptions.MaximumShortLoopSize, code.size());
        while (i < maxEnd && code.get(i).loopDepth() >= headerBlock.loopDepth()) {
            i++;
        }

        if (i == code.size() || code.get(i).loopDepth() < headerBlock.loopDepth()) {
            int endIdx = i - 1;
            LIRBlock endBlock = code.get(endIdx);

            if (endBlock.numberOfSux() == 1 && endBlock.suxAt(0) == headerBlock) {
                // short loop from headerIdx to endIdx found . reorder blocks such that
                // the headerBlock is the last block instead of the first block of the loop

                for (int j = headerIdx; j < endIdx; j++) {
                    code.set(j, code.get(j + 1));
                }
                code.set(endIdx, headerBlock);
            }
        }
    }*/
/*
    private void reorderShortLoops(List<LIRBlock> code) {
        for (int i = code.size() - 1; i >= 0; i--) {
            LIRBlock block = code.get(i);

            if (block.isLinearScanLoopHeader()) {
                reorderShortLoop(code, block, i);
            }
        }

        assert verify(code);
    }*/

    // only blocks with exactly one successor can be deleted. Such blocks
    // must always end with an unconditional branch to its successor
    private boolean canDeleteBlock(Block block) {
        if (block.numberOfSux() != 1 ||
            block == ir.cfg.getStartBlock() ||
            block.suxAt(0) == block) {
            return false;
        }

        List<LIRInstruction> instructions = block.lir;

        assert instructions.size() >= 2 : "block must have label and branch";
        assert instructions.get(0) instanceof StandardOp.LabelOp : "first instruction must always be a label";
        assert instructions.get(instructions.size() - 1) instanceof StandardOp.JumpOp : "last instruction must always be a branch";
        assert ((StandardOp.JumpOp) instructions.get(instructions.size() - 1)).destination().label() == ((StandardOp.LabelOp) block.suxAt(0).lir.get(0)).getLabel() : "branch target must be the successor";

        // block must have exactly one successor

        return instructions.size() == 2 && instructions.get(instructions.size() - 1).info == null;
    }

    private void deleteEmptyBlocks(List<Block> code) {
        int oldPos = 0;
        int newPos = 0;
        int numBlocks = code.size();

        assert verify(code);
        while (oldPos < numBlocks) {
            Block block = code.get(oldPos);

            if (canDeleteBlock(block)) {
                // adjust successor and predecessor lists
                Block other = block.suxAt(0);
                for (Block pred : block.getPredecessors()) {
                    Util.replaceAllInList(block, other, pred.getSuccessors());
                }
                for (int i = 0; i < other.getPredecessors().size(); i++) {
                    if (other.getPredecessors().get(i) == block) {
                        other.getPredecessors().remove(i);
                        other.getPredecessors().addAll(i, block.getPredecessors());
                    }
                }
                block.getSuccessors().clear();
                block.getPredecessors().clear();
                Debug.metric("BlocksDeleted").increment();
            } else {
                // adjust position of this block in the block list if blocks before
                // have been deleted
                if (newPos != oldPos) {
                    code.set(newPos, code.get(oldPos));
                }
                newPos++;
            }
            oldPos++;
        }
        assert verify(code);
        Util.truncate(code, newPos);

        assert verify(code);
    }

    private static void deleteUnnecessaryJumps(List<Block> code) {
        // skip the last block because there a branch is always necessary
        for (int i = code.size() - 2; i >= 0; i--) {
            Block block = code.get(i);
            List<LIRInstruction> instructions = block.lir;

            LIRInstruction lastOp = instructions.get(instructions.size() - 1);
            if (lastOp instanceof StandardOp.JumpOp) {
                StandardOp.JumpOp lastJump = (StandardOp.JumpOp) lastOp;

                if (lastOp.info == null) {
                    if (lastJump.destination().label() == ((StandardOp.LabelOp) code.get(i + 1).lir.get(0)).getLabel()) {
                        // delete last branch instruction
                        Util.truncate(instructions, instructions.size() - 1);

                    } else {
                        LIRInstruction prevOp = instructions.get(instructions.size() - 2);
                        if (prevOp instanceof StandardOp.BranchOp) {
                            StandardOp.BranchOp prevBranch = (StandardOp.BranchOp) prevOp;

                            if (prevBranch.destination().label() == ((StandardOp.LabelOp) code.get(i + 1).lir.get(0)).getLabel() && prevOp.info == null) {
                                // eliminate a conditional branch to the immediate successor
                                prevBranch.negate(lastJump.destination());
                                Util.truncate(instructions, instructions.size() - 1);
                            }
                        }
                    }
                }
            }
        }

        assert verify(code);
    }

/*
    private static void deleteJumpsToReturn(List<LIRBlock> code) {
        for (int i = code.size() - 1; i >= 0; i--) {
            LIRBlock block = code.get(i);
            List<LIRInstruction> curInstructions = block.lir();
            LIRInstruction curLastOp = curInstructions.get(curInstructions.size() - 1);

            assert curInstructions.get(0).code == StandardOpcode.LABEL : "first instruction must always be a label";
            if (curInstructions.size() == 2 && curLastOp.code == StandardOpcode.RETURN) {
                // the block contains only a label and a return
                // if a predecessor ends with an unconditional jump to this block, then the jump
                // can be replaced with a return instruction
                //
                // Note: the original block with only a return statement cannot be deleted completely
                // because the predecessors might have other (conditional) jumps to this block.
                // this may lead to unnecesary return instructions in the final code

                assert curLastOp.info == null : "return instructions do not have debug information";
                CiValue returnOpr = curLastOp.input(0);

                for (int j = block.numberOfPreds() - 1; j >= 0; j--) {
                    LIRBlock pred = block.predAt(j);
                    List<LIRInstruction> predInstructions = pred.lir();
                    LIRInstruction predLastOp = predInstructions.get(predInstructions.size() - 1);

                    if (predLastOp instanceof LIRBranch) {
                        LIRBranch predLastBranch = (LIRBranch) predLastOp;

                        if (predLastBranch.destination().label() == block.label() && predLastBranch.code == StandardOpcode.JUMP && predLastBranch.info == null) {
                            // replace the jump to a return with a direct return
                            // Note: currently the edge between the blocks is not deleted
                            predInstructions.set(predInstructions.size() - 1, StandardOpcode.RETURN.create(returnOpr));
                        }
                    }
                }
            }
        }
    }
*/

    private static boolean verify(List<Block> code) {
        for (Block block : code) {
            for (Block sux : block.getSuccessors()) {
                assert code.contains(sux) : "missing successor from: " + block + "to: " + sux;
            }

            for (Block pred : block.getPredecessors()) {
                assert code.contains(pred) : "missing predecessor from: " + block + "to: " + pred;
            }
        }

        return true;
    }
}
