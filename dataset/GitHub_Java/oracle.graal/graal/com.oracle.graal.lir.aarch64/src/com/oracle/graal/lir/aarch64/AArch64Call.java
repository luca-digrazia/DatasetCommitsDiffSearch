/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.asm.aarch64.AArch64Assembler;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public class AArch64Call {

    public abstract static class CallOp extends AArch64LIRInstruction {
        @Def({REG, ILLEGAL}) protected Value result;
        @Use({REG, STACK}) protected Value[] parameters;
        @Temp protected Value[] temps;
        @State protected LIRFrameState state;

        protected CallOp(LIRInstructionClass<? extends CallOp> c, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c);
            this.result = result;
            this.parameters = parameters;
            this.state = state;
            this.temps = temps;
            assert temps != null;
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return true;
        }
    }

    public abstract static class MethodCallOp extends CallOp {
        protected final ResolvedJavaMethod callTarget;

        protected MethodCallOp(LIRInstructionClass<? extends MethodCallOp> c, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, result, parameters, temps, state);
            this.callTarget = callTarget;
        }
    }

    @Opcode("CALL_INDIRECT")
    public static class IndirectCallOp extends MethodCallOp {
        public static final LIRInstructionClass<IndirectCallOp> TYPE = LIRInstructionClass.create(IndirectCallOp.class);

        @Use({REG}) protected Value targetAddress;

        public IndirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress, LIRFrameState state) {
            this(TYPE, callTarget, result, parameters, temps, targetAddress, state);
        }

        protected IndirectCallOp(LIRInstructionClass<? extends IndirectCallOp> c, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress,
                        LIRFrameState state) {
            super(c, callTarget, result, parameters, temps, state);
            this.targetAddress = targetAddress;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register target = asRegister(targetAddress);
            indirectCall(crb, masm, target, callTarget, state);
        }

        @Override
        public void verify() {
            super.verify();
            assert isRegister(targetAddress) : "The current register allocator cannot handle variables to be used at call sites, " + "it must be in a fixed register for now";
        }
    }

    @Opcode("CALL_DIRECT")
    public abstract static class DirectCallOp extends MethodCallOp {
        public static final LIRInstructionClass<DirectCallOp> TYPE = LIRInstructionClass.create(DirectCallOp.class);

        public DirectCallOp(ResolvedJavaMethod target, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, target, result, parameters, temps, state);
        }

        protected DirectCallOp(LIRInstructionClass<? extends DirectCallOp> c, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, callTarget, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            directCall(crb, masm, callTarget, null, state);
        }
    }

    public abstract static class ForeignCallOp extends CallOp {
        protected final ForeignCallLinkage callTarget;

        protected ForeignCallOp(LIRInstructionClass<? extends ForeignCallOp> c, ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, result, parameters, temps, state);
            this.callTarget = callTarget;
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return callTarget.destroysRegisters();
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            emitCall(crb, masm);
        }

        protected abstract void emitCall(CompilationResultBuilder crb, AArch64MacroAssembler masm);
    }

    @Opcode("NEAR_FOREIGN_CALL")
    public static class DirectNearForeignCallOp extends ForeignCallOp {
        public static final LIRInstructionClass<DirectNearForeignCallOp> TYPE = LIRInstructionClass.create(DirectNearForeignCallOp.class);

        public DirectNearForeignCallOp(ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, callTarget, result, parameters, temps, state);
        }

        @Override
        protected void emitCall(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            directCall(crb, masm, callTarget, null, state);
        }
    }

    @Opcode("FAR_FOREIGN_CALL")
    public static class DirectFarForeignCallOp extends ForeignCallOp {
        public static final LIRInstructionClass<DirectFarForeignCallOp> TYPE = LIRInstructionClass.create(DirectFarForeignCallOp.class);

        public DirectFarForeignCallOp(ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, callTarget, result, parameters, temps, state);
        }

        @Override
        protected void emitCall(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            // We can use any scratch register we want, since we know that they have been saved
            // before calling.
            directCall(crb, masm, callTarget, AArch64.r8, state);
        }
    }

    /**
     * Tests whether linkage can be called directly under all circumstances without the need for a
     * scratch register.
     *
     * Note this is a pessimistic assumption: This may return false despite a near call/jump being
     * adequate.
     *
     * @param linkage Foreign call description
     * @return true if foreign call can be called directly and does not need a scratch register to
     *         load the address into.
     */
    public static boolean isNearCall(ForeignCallLinkage linkage) {
        long maxOffset = linkage.getMaxCallTargetOffset();
        return maxOffset != -1 && AArch64MacroAssembler.isBranchImmediateOffset(maxOffset);
    }

    public static void directCall(CompilationResultBuilder crb, AArch64MacroAssembler masm, InvokeTarget callTarget, Register scratch, LIRFrameState info) {
        int before = masm.position();
        if (scratch != null) {
            /*
             * Offset might not fit into a 28-bit immediate, generate an indirect call with a 64-bit
             * immediate address which is fixed up by HotSpot.
             */
            masm.forceMov(scratch, 0L);
            masm.blr(scratch);
        } else {
            // Address is fixed up by HotSpot.
            masm.bl(0);
        }
        int after = masm.position();
        crb.recordDirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    public static void indirectCall(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register dst, InvokeTarget callTarget, LIRFrameState info) {
        int before = masm.position();
        masm.blr(dst);
        int after = masm.position();
        crb.recordIndirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    public static void directJmp(CompilationResultBuilder crb, AArch64MacroAssembler masm, InvokeTarget target) {
        int before = masm.position();
        // Address is fixed up later by c++ code.
        masm.jmp();
        int after = masm.position();
        crb.recordDirectCall(before, after, target, null);
        masm.ensureUniquePC();
    }

    public static void indirectJmp(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register dst, InvokeTarget target) {
        int before = masm.position();
        masm.jmp(dst);
        int after = masm.position();
        crb.recordIndirectCall(before, after, target, null);
        masm.ensureUniquePC();
    }

    public static void directConditionalJmp(CompilationResultBuilder crb, AArch64MacroAssembler masm, InvokeTarget target, AArch64Assembler.ConditionFlag cond) {
        int before = masm.position();
        masm.branchConditionally(cond);
        int after = masm.position();
        crb.recordDirectCall(before, after, target, null);
        masm.ensureUniquePC();
    }

}
