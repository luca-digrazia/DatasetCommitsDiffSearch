/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.nodes.cfg.*;

public final class LIRVerifier {
    private final LIR lir;
    private final FrameMap frameMap;

    private final boolean beforeRegisterAllocation;

    private final BitSet[] blockLiveOut;
    private final Object[] variableDefinitions;

    private BitSet liveOutFor(Block block) {
        return blockLiveOut[block.getId()];
    }
    private void setLiveOutFor(Block block, BitSet liveOut) {
        blockLiveOut[block.getId()] = liveOut;
    }

    private int maxRegisterNum() {
        return frameMap.target.arch.getRegisters().length;
    }

    private boolean isAllocatableRegister(Value value) {
        return isRegister(value) && frameMap.registerConfig.getAttributesMap()[asRegister(value).number].isAllocatable();
    }

    public static boolean verify(final LIRInstruction op) {
        ValueProcedure allowedProc = new ValueProcedure() {
            @Override public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                return allowed(op, value, mode, flags);
            }
        };

        op.forEachInput(allowedProc);
        op.forEachAlive(allowedProc);
        op.forEachState(allowedProc);
        op.forEachTemp(allowedProc);
        op.forEachOutput(allowedProc);

        op.verify();
        return true;
    }

    public static boolean verify(boolean beforeRegisterAllocation, LIR lir, FrameMap frameMap) {
        LIRVerifier verifier = new LIRVerifier(beforeRegisterAllocation, lir, frameMap);
        verifier.verify();
        return true;
    }


    private LIRVerifier(boolean beforeRegisterAllocation, LIR lir, FrameMap frameMap) {
        this.beforeRegisterAllocation = beforeRegisterAllocation;
        this.lir = lir;
        this.frameMap = frameMap;
        this.blockLiveOut = new BitSet[lir.linearScanOrder().size()];
        this.variableDefinitions = new Object[lir.numVariables()];
    }

    private BitSet curVariablesLive;
    private Value[] curRegistersLive;

    private Block curBlock;
    private Object curInstruction;
    private BitSet curRegistersDefined;

    private void verify() {
        ValueProcedure useProc = new ValueProcedure() { @Override public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) { return use(value, mode, flags); } };
        ValueProcedure defProc = new ValueProcedure() { @Override public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) { return def(value, mode, flags); } };

        int maxRegisterNum = maxRegisterNum();
        curRegistersDefined = new BitSet();
        for (Block block : lir.linearScanOrder()) {
            curBlock = block;
            curVariablesLive = new BitSet();
            curRegistersLive = new Value[maxRegisterNum];

            if (block.getDominator() != null) {
                curVariablesLive.or(liveOutFor(block.getDominator()));
            }

            assert lir.lir(block).get(0) instanceof StandardOp.LabelOp : "block must start with label";

            if (block.getSuccessorCount() > 0) {
                LIRInstruction last = lir.lir(block).get(lir.lir(block).size() - 1);
                assert last instanceof StandardOp.JumpOp : "block with successor must end with unconditional jump";
            }

            for (LIRInstruction op : lir.lir(block)) {
                curInstruction = op;

                op.forEachInput(useProc);
                if (op.hasCall()) {
                    for (Register register : frameMap.registerConfig.getCallerSaveRegisters()) {
                        curRegistersLive[register.number] = null;
                    }
                }
                curRegistersDefined.clear();
                op.forEachAlive(useProc);
                op.forEachState(useProc);
                op.forEachTemp(defProc);
                op.forEachOutput(defProc);

                curInstruction = null;
            }

            setLiveOutFor(block, curVariablesLive);
        }
    }

    private Value use(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        allowed(curInstruction, value, mode, flags);

        if (isVariable(value)) {
            assert beforeRegisterAllocation;

            int variableIdx = asVariable(value).index;
            if (!curVariablesLive.get(variableIdx)) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live variables: %s", curVariablesLive);
                if (variableDefinitions[variableIdx] != null) {
                    TTY.println("definition of %s: %s", value, variableDefinitions[variableIdx]);
                }
                TTY.println("ERROR: Use of variable %s that is not defined in dominator", value);
                throw GraalInternalError.shouldNotReachHere();
            }

        } else if (isAllocatableRegister(value)) {
            int regNum = asRegister(value).number;
            if (mode == OperandMode.ALIVE) {
                curRegistersDefined.set(regNum);
            }

            if (beforeRegisterAllocation && curRegistersLive[regNum] != value) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live registers: %s", Arrays.toString(curRegistersLive));
                TTY.println("ERROR: Use of fixed register %s that is not defined in this block", value);
                throw GraalInternalError.shouldNotReachHere();
            }
        }
        return value;
    }

    private Value def(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        allowed(curInstruction, value, mode, flags);

        if (isVariable(value)) {
            assert beforeRegisterAllocation;

            int variableIdx = asVariable(value).index;
            if (variableDefinitions[variableIdx] != null) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live variables: %s", curVariablesLive);
                TTY.println("definition of %s: %s", value, variableDefinitions[variableIdx]);
                TTY.println("ERROR: Variable %s defined multiple times", value);
                throw GraalInternalError.shouldNotReachHere();
            }
            assert curInstruction != null;
            variableDefinitions[variableIdx] = curInstruction;
            assert !curVariablesLive.get(variableIdx);
            if (mode == OperandMode.DEF) {
                curVariablesLive.set(variableIdx);
            }

        } else if (isAllocatableRegister(value)) {
            int regNum = asRegister(value).number;
            if (curRegistersDefined.get(regNum)) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("ERROR: Same register defined twice in the same instruction: %s", value);
                throw GraalInternalError.shouldNotReachHere();
            }
            curRegistersDefined.set(regNum);

            if (beforeRegisterAllocation) {
                if (mode == OperandMode.DEF) {
                    curRegistersLive[regNum] = value;
                } else {
                    curRegistersLive[regNum] = null;
                }
            }
        }
        return value;
    }

    private static Value allowed(Object op, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        if ((isVariable(value)  && flags.contains(OperandFlag.REG)) ||
            (isRegister(value)  && flags.contains(OperandFlag.REG)) ||
            (isStackSlot(value) && flags.contains(OperandFlag.STACK)) ||
            (isConstant(value)  && flags.contains(OperandFlag.CONST) && mode != OperandMode.DEF) ||
            (isIllegal(value)   && flags.contains(OperandFlag.ILLEGAL))) {
            return value;
        }
        TTY.println("instruction %s", op);
        TTY.println("mode: %s  flags: %s", mode, flags);
        TTY.println("Unexpected value: %s %s", value.getClass().getSimpleName(), value);
        throw GraalInternalError.shouldNotReachHere();
    }
}
