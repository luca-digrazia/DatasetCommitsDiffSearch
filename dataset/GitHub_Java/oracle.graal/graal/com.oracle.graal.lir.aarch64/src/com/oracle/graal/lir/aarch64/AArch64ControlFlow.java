/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.function.Function;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CompilationResult.JumpTable;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.asm.aarch64.AArch64Address;
import com.oracle.graal.asm.aarch64.AArch64Assembler;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler.PatchLabelKind;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.StandardOp;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public class AArch64ControlFlow {

    /**
     * Compares integer register to 0 and branches if condition is true. Condition may only be equal
     * or non-equal.
     */
    // TODO (das) where do we need this?
    // public static class CompareAndBranchOp extends AArch64LIRInstruction implements
    // StandardOp.BranchOp {
    // private final ConditionFlag condition;
    // private final LabelRef destination;
    // @Use({REG}) private Value x;
    //
    // public CompareAndBranchOp(Condition condition, LabelRef destination, Value x) {
    // assert condition == Condition.EQ || condition == Condition.NE;
    // assert ARMv8.isGpKind(x.getKind());
    // this.condition = condition == Condition.EQ ? ConditionFlag.EQ : ConditionFlag.NE;
    // this.destination = destination;
    // this.x = x;
    // }
    //
    // @Override
    // public void emitCode(CompilationResultBuilder crb, ARMv8MacroAssembler masm) {
    // int size = ARMv8.bitsize(x.getKind());
    // if (condition == ConditionFlag.EQ) {
    // masm.cbz(size, asRegister(x), destination.label());
    // } else {
    // masm.cbnz(size, asRegister(x), destination.label());
    // }
    // }
    // }

    public static class BranchOp extends AArch64BlockEndOp implements StandardOp.BranchOp {
        public static final LIRInstructionClass<BranchOp> TYPE = LIRInstructionClass.create(BranchOp.class);

        private final AArch64Assembler.ConditionFlag condition;
        private final LabelRef trueDestination;
        private final LabelRef falseDestination;

        private final double trueDestinationProbability;

        public BranchOp(AArch64Assembler.ConditionFlag condition, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(TYPE);
            this.condition = condition;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.trueDestinationProbability = trueDestinationProbability;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            /*
             * Explanation: Depending on what the successor edge is, we can use the fall-through to
             * optimize the generated code. If neither is a successor edge, use the branch
             * probability to try to take the conditional jump as often as possible to avoid
             * executing two instructions instead of one.
             */
            if (crb.isSuccessorEdge(trueDestination)) {
                masm.branchConditionally(condition.negate(), falseDestination.label());
            } else if (crb.isSuccessorEdge(falseDestination)) {
                masm.branchConditionally(condition, trueDestination.label());
            } else if (trueDestinationProbability < 0.5) {
                masm.branchConditionally(condition.negate(), falseDestination.label());
                masm.jmp(trueDestination.label());
            } else {
                masm.branchConditionally(condition, trueDestination.label());
                masm.jmp(falseDestination.label());
            }
        }

    }

    @Opcode("CMOVE")
    public static class CondMoveOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<CondMoveOp> TYPE = LIRInstructionClass.create(CondMoveOp.class);

        @Def protected Value result;
        @Use protected Value trueValue;
        @Use protected Value falseValue;
        private final AArch64Assembler.ConditionFlag condition;

        public CondMoveOp(Variable result, AArch64Assembler.ConditionFlag condition, Value trueValue, Value falseValue) {
            super(TYPE);
            assert trueValue.getPlatformKind() == falseValue.getPlatformKind() && trueValue.getPlatformKind() == result.getPlatformKind();
            this.result = result;
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Kind kind = (AArch64Kind) trueValue.getPlatformKind();
            int size = kind.getSizeInBytes() * Byte.SIZE;
            if (kind.isInteger()) {
                masm.cmov(size, asRegister(result), asRegister(trueValue), asRegister(falseValue), condition);
            } else {
                masm.fcmov(size, asRegister(result), asRegister(trueValue), asRegister(falseValue), condition);
            }
        }
    }

    public static class StrategySwitchOp extends AArch64BlockEndOp implements StandardOp.BlockEndOp {
        public static final LIRInstructionClass<StrategySwitchOp> TYPE = LIRInstructionClass.create(StrategySwitchOp.class);

        private final Constant[] keyConstants;
        private final SwitchStrategy strategy;
        private final Function<Condition, AArch64Assembler.ConditionFlag> converter;
        private final LabelRef[] keyTargets;
        private final LabelRef defaultTarget;
        @Alive protected Value key;
        // TODO (das) This could be optimized: We only need the scratch register in case of a
        // datapatch, or too large
        // immediates.
        @Temp protected Value scratch;

        public StrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch, Function<Condition, AArch64Assembler.ConditionFlag> converter) {
            super(TYPE);
            this.strategy = strategy;
            this.converter = converter;
            this.keyConstants = strategy.getKeyConstants();
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            this.scratch = scratch;
            assert keyConstants.length == keyTargets.length;
            assert keyConstants.length == strategy.keyProbabilities.length;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            strategy.run(new SwitchClosure(crb, masm));
        }

        private class SwitchClosure extends SwitchStrategy.BaseSwitchClosure {
            private final AArch64MacroAssembler masm;
            private final CompilationResultBuilder crb;

            public SwitchClosure(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
                super(crb, masm, keyTargets, defaultTarget);
                this.masm = masm;
                this.crb = crb;
            }

            @Override
            protected void conditionalJump(int index, Condition condition, Label target) {
                emitComparison(keyConstants[index]);
                masm.branchConditionally(converter.apply(condition), target);
            }

            private void emitComparison(Constant c) {
                JavaConstant jc = (JavaConstant) c;
                ConstantValue constVal = new ConstantValue(LIRKind.value(key.getPlatformKind()), c);
                switch (jc.getJavaKind()) {
                    case Int:
                        long lc = jc.asLong();
                        assert NumUtil.isInt(lc);
                        emitCompare(crb, masm, key, scratch, constVal);
                        break;
                    case Long:
                        emitCompare(crb, masm, key, scratch, constVal);
                        break;
                    case Object:
                        emitCompare(crb, masm, key, scratch, constVal);
                        break;
                    default:
                        throw new JVMCIError("switch only supported for int, long and object");
                }
            }
        }
    }

    public static class TableSwitchOp extends AArch64BlockEndOp implements StandardOp.BlockEndOp {
        public static final LIRInstructionClass<TableSwitchOp> TYPE = LIRInstructionClass.create(TableSwitchOp.class);

        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @Alive protected Variable keyValue;
        @Temp protected Variable scratchValue;

        public TableSwitchOp(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Variable key, Variable scratch) {
            super(TYPE);
            this.lowKey = lowKey;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
            this.keyValue = key;
            this.scratchValue = scratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register key = asRegister(keyValue);
            Register scratch = asRegister(scratchValue);
            if (lowKey != 0) {
                if (AArch64MacroAssembler.isArithmeticImmediate(lowKey)) {
                    masm.sub(32, key, key, lowKey);
                } else {
                    ConstantValue constVal = new ConstantValue(LIRKind.value(AArch64Kind.WORD), JavaConstant.forInt(lowKey));
                    AArch64Move.move(crb, masm, scratchValue, constVal);
                    masm.sub(32, key, key, scratch);
                }
            }
            if (defaultTarget != null) {
                // if key is not in table range, jump to default target if it exists.
                ConstantValue constVal = new ConstantValue(LIRKind.value(AArch64Kind.WORD), JavaConstant.forInt(targets.length));
                emitCompare(crb, masm, keyValue, scratchValue, constVal);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.HS, defaultTarget.label());
            }

            // Load the start address of the jump table - which starts 3 instructions after the adr
            // - into scratch.
            masm.adr(scratch, 4 * 3);
            masm.ldr(32, scratch, AArch64Address.createRegisterOffsetAddress(scratch, key, /* scaled */true));
            masm.jmp(scratch);
            int jumpTablePos = masm.position();
            // emit jump table entries
            for (LabelRef target : targets) {
                Label label = target.label();
                if (label.isBound()) {
                    masm.emitInt(target.label().position());
                } else {
                    label.addPatchAt(masm.position());
                    masm.emitInt(PatchLabelKind.JUMP_ADDRESS.encoding);
                }
            }
            JumpTable jt = new JumpTable(jumpTablePos, lowKey, lowKey + targets.length - 1, 4);
            crb.compilationResult.addAnnotation(jt);
        }
    }

    private static void emitCompare(CompilationResultBuilder crb, AArch64MacroAssembler masm, Value key, Value scratchValue, ConstantValue c) {
        long imm = c.getJavaConstant().asLong();
        int size = key.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        if (AArch64MacroAssembler.isComparisonImmediate(imm)) {
            masm.cmp(size, asRegister(key), (int) imm);
        } else {
            AArch64Move.move(crb, masm, asAllocatableValue(scratchValue), c);
            masm.cmp(size, asRegister(key), asRegister(scratchValue));
        }
    }

}
