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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.STACK;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.UNINITIALIZED;
import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.aarch64.AArch64Address;
import com.oracle.graal.asm.aarch64.AArch64Assembler;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.StandardOp;
import com.oracle.graal.lir.StandardOp.NullCheck;
import com.oracle.graal.lir.StandardOp.ValueMoveOp;
import com.oracle.graal.lir.VirtualStackSlot;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public class AArch64Move {

    @Opcode("MOVE")
    public static class MoveToRegOp extends AArch64LIRInstruction implements ValueMoveOp {
        public static final LIRInstructionClass<MoveToRegOp> TYPE = LIRInstructionClass.create(MoveToRegOp.class);

        @Def protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue input;

        public MoveToRegOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            move(crb, masm, getResult(), getInput());
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    /**
     * If the destination is a StackSlot we cannot have a StackSlot or Constant as the source, hence
     * we have to special case this particular combination. Note: We allow a register as the
     * destination too just to give the register allocator more freedom.
     */
    @Opcode("MOVE")
    public static class MoveToStackOp extends AArch64LIRInstruction implements StandardOp.ValueMoveOp {
        public static final LIRInstructionClass<MoveToStackOp> TYPE = LIRInstructionClass.create(MoveToStackOp.class);

        @Def({STACK, REG}) protected AllocatableValue result;
        @Use protected AllocatableValue input;

        public MoveToStackOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            move(crb, masm, getResult(), getInput());
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static class MoveFromConstOp extends AArch64LIRInstruction implements StandardOp.LoadConstantOp {
        public static final LIRInstructionClass<MoveFromConstOp> TYPE = LIRInstructionClass.create(MoveFromConstOp.class);

        @Def protected AllocatableValue result;
        private final JavaConstant input;

        public MoveFromConstOp(AllocatableValue result, JavaConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            const2reg(crb, masm, result, input);
        }

        @Override
        public Constant getConstant() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    public static class LoadAddressOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<LoadAddressOp> TYPE = LIRInstructionClass.create(LoadAddressOp.class);

        @Def protected AllocatableValue result;
        @Use(COMPOSITE) protected AArch64AddressValue address;

        public LoadAddressOp(AllocatableValue result, AArch64AddressValue address) {
            super(TYPE);
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            AArch64Address adr = address.toAddress();
            masm.loadAddress(dst, adr, address.getPlatformKind().getSizeInBytes());
        }
    }

    public static class LoadDataOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<LoadDataOp> TYPE = LIRInstructionClass.create(LoadDataOp.class);

        @Def protected AllocatableValue result;
        private final byte[] data;

        public LoadDataOp(AllocatableValue result, byte[] data) {
            super(TYPE);
            this.result = result;
            this.data = data;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            int alignment = 16;
            masm.loadAddress(dst, (AArch64Address) crb.recordDataReferenceInCode(data, alignment), alignment);
        }
    }

    public static class StackLoadAddressOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<StackLoadAddressOp> TYPE = LIRInstructionClass.create(StackLoadAddressOp.class);

        @Def protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected AllocatableValue slot;

        public StackLoadAddressOp(AllocatableValue result, AllocatableValue slot) {
            super(TYPE);
            assert slot instanceof VirtualStackSlot || slot instanceof StackSlot;
            this.result = result;
            this.slot = slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Address address = (AArch64Address) crb.asAddress(slot);
            PlatformKind kind = AArch64Kind.QWORD;
            masm.loadAddress(asRegister(result, kind), address, kind.getSizeInBytes());
        }
    }

    public static class MembarOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<MembarOp> TYPE = LIRInstructionClass.create(MembarOp.class);

        @SuppressWarnings("unused") private final int barriers;

        public MembarOp(int barriers) {
            super(TYPE);
            this.barriers = barriers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            // As I understand it load acquire/store release have the same semantics as on IA64
            // and allow us to handle LoadStore, LoadLoad and StoreStore without an explicit
            // barrier.
            // But Graal support to figure out if a load/store is volatile is non-existant so for
            // now
            // just use
            // memory barriers everywhere.
            // if ((barrier & MemoryBarriers.STORE_LOAD) != 0) {
            masm.dmb(AArch64MacroAssembler.BarrierKind.ANY_ANY);
            // }
        }
    }

    abstract static class MemOp extends AArch64LIRInstruction implements StandardOp.ImplicitNullCheck {

        protected final AArch64Kind kind;
        @Use({COMPOSITE}) protected AArch64AddressValue addressValue;
        @State protected LIRFrameState state;

        public MemOp(LIRInstructionClass<? extends MemOp> c, AArch64Kind kind, AArch64AddressValue address, LIRFrameState state) {
            super(c);
            this.kind = kind;
            this.addressValue = address;
            this.state = state;
        }

        protected abstract void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm);

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            emitMemAccess(crb, masm);
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            int immediate = addressValue.getImmediate();
            if (state == null && value.equals(addressValue.getBase()) && addressValue.getOffset().equals(Value.ILLEGAL) && immediate >= 0 && immediate < implicitNullCheckLimit) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public static final class LoadOp extends MemOp {
        public static final LIRInstructionClass<LoadOp> TYPE = LIRInstructionClass.create(LoadOp.class);

        @Def protected AllocatableValue result;

        public LoadOp(AArch64Kind kind, AllocatableValue result, AArch64AddressValue address, LIRFrameState state) {
            super(TYPE, kind, address, state);
            this.result = result;
        }

        @Override
        protected void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Address address = addressValue.toAddress();
            Register dst = asRegister(result);

            int destSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int srcSize = kind.getSizeInBytes() * Byte.SIZE;
            if (kind.isInteger()) {
                // TODO How to load unsigned chars without the necessary information?
                masm.ldrs(destSize, srcSize, dst, address);
            } else {
                assert srcSize == destSize;
                masm.fldr(srcSize, dst, address);
            }
        }
    }

    public static class StoreOp extends MemOp {
        public static final LIRInstructionClass<StoreOp> TYPE = LIRInstructionClass.create(StoreOp.class);
        @Use protected AllocatableValue input;

        public StoreOp(AArch64Kind kind, AArch64AddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, kind, address, state);
            this.input = input;
        }

        @Override
        protected void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            emitStore(crb, masm, kind, addressValue.toAddress(), asRegister(input));
        }
    }

    public static final class StoreConstantOp extends MemOp {
        public static final LIRInstructionClass<StoreConstantOp> TYPE = LIRInstructionClass.create(StoreConstantOp.class);

        protected final JavaConstant input;

        public StoreConstantOp(AArch64Kind kind, AArch64AddressValue address, JavaConstant input, LIRFrameState state) {
            super(TYPE, kind, address, state);
            this.input = input;
            if (!input.isDefaultForKind()) {
                throw JVMCIError.shouldNotReachHere("Can only store null constants to memory");
            }
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            emitStore(crb, masm, kind, addressValue.toAddress(), zr);
        }
    }

    public static final class NullCheckOp extends AArch64LIRInstruction implements NullCheck {
        public static final LIRInstructionClass<NullCheckOp> TYPE = LIRInstructionClass.create(NullCheckOp.class);

        @Use(COMPOSITE) protected AArch64AddressValue address;
        @State protected LIRFrameState state;

        public NullCheckOp(AArch64AddressValue address, LIRFrameState state) {
            super(TYPE);
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            crb.recordImplicitException(masm.position(), state);
            masm.ldr(64, zr, address.toAddress());
        }

        public Value getCheckedValue() {
            return address.base;
        }

        public LIRFrameState getState() {
            return state;
        }
    }

    /**
     * Compare and swap instruction. Does the following atomically: <code>
     *  CAS(newVal, expected, address):
     *    oldVal = *address
     *    if oldVal == expected:
     *        *address = newVal
     *    return oldVal
     * </code>
     */
    @Opcode("CAS")
    public static class CompareAndSwap extends AArch64LIRInstruction {
        public static final LIRInstructionClass<CompareAndSwap> TYPE = LIRInstructionClass.create(CompareAndSwap.class);

        @Def protected AllocatableValue resultValue;
        @Alive protected Value expectedValue;
        @Alive protected AllocatableValue newValue;
        @Alive(COMPOSITE) protected AArch64AddressValue addressValue;
        @Temp protected AllocatableValue scratchValue;

        public CompareAndSwap(AllocatableValue result, Value expectedValue, AllocatableValue newValue, AArch64AddressValue addressValue, AllocatableValue scratch) {
            super(TYPE);
            this.resultValue = result;
            this.expectedValue = expectedValue;
            this.newValue = newValue;
            this.addressValue = addressValue;
            this.scratchValue = scratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Kind kind = (AArch64Kind) expectedValue.getPlatformKind();
            assert kind.isInteger();
            int size = kind.getSizeInBytes() * Byte.SIZE;

            AArch64Address address = addressValue.toAddress();
            Register result = asRegister(resultValue);
            Register newVal = asRegister(newValue);
            Register scratch = asRegister(scratchValue);
            // We could avoid using a scratch register here, by reusing resultValue for the stlxr
            // success flag
            // and issue a mov resultValue, expectedValue in case of success before returning.
            Label retry = new Label();
            Label fail = new Label();
            masm.bind(retry);
            masm.ldaxr(size, result, address);
            AArch64Compare.gpCompare(masm, resultValue, expectedValue);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, fail);
            masm.stlxr(size, scratch, newVal, address);
            // if scratch == 0 then write successful, else retry.
            masm.cbnz(32, scratch, retry);
            masm.bind(fail);
        }
    }

    public static void emitStore(@SuppressWarnings("unused") CompilationResultBuilder crb, AArch64MacroAssembler masm, AArch64Kind kind, AArch64Address dst, Register src) {
        int destSize = kind.getSizeInBytes() * Byte.SIZE;
        if (kind.isInteger()) {
            masm.str(destSize, src, dst);
        } else {
            masm.fstr(destSize, src, dst);
        }
    }

    public static void move(CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, Value input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(crb, masm, result, asAllocatableValue(input));
            } else if (isStackSlot(result)) {
                reg2stack(crb, masm, result, asAllocatableValue(input));
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                stack2reg(crb, masm, result, asAllocatableValue(input));
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        } else if (isJavaConstant(input)) {
            if (isRegister(result)) {
                const2reg(crb, masm, result, asJavaConstant(input));
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
    }

    private static void reg2reg(@SuppressWarnings("unused") CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, AllocatableValue input) {
        Register dst = asRegister(result);
        Register src = asRegister(input);
        AArch64Kind kind = (AArch64Kind) input.getPlatformKind();
        int size = kind.getSizeInBytes() * Byte.SIZE;
        if (kind.isInteger()) {
            masm.mov(size, dst, src);
        } else {
            masm.fmov(size, dst, src);
        }
    }

    private static void reg2stack(CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, AllocatableValue input) {
        AArch64Address dest = loadStackSlotAddress(crb, masm, asStackSlot(result), Value.ILLEGAL);
        Register src = asRegister(input);
        AArch64Kind kind = (AArch64Kind) input.getPlatformKind();
        int size = kind.getSizeInBytes() * Byte.SIZE;
        if (kind.isInteger()) {
            masm.str(size, src, dest);
        } else {
            masm.fstr(size, src, dest);
        }
    }

    private static void stack2reg(CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, AllocatableValue input) {
        AArch64Address src = loadStackSlotAddress(crb, masm, asStackSlot(input), result);
        Register dest = asRegister(result);
        AArch64Kind kind = (AArch64Kind) input.getPlatformKind();
        int size = kind.getSizeInBytes() * Byte.SIZE;
        if (kind.isInteger()) {
            masm.ldr(size, dest, src);
        } else {
            masm.fldr(size, dest, src);
        }
    }

    private static void const2reg(CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, JavaConstant input) {
        Register dst = asRegister(result);
        switch (input.getJavaKind().getStackKind()) {
            case Int:
                masm.mov(dst, input.asInt());
                break;
            case Long:
                masm.mov(dst, input.asLong());
                break;
            case Float:
                if (AArch64MacroAssembler.isFloatImmediate(input.asFloat())) {
                    masm.fmov(32, dst, input.asFloat());
                } else {
                    masm.fldr(32, dst, (AArch64Address) crb.asFloatConstRef(input));
                }
                break;
            case Double:
                if (AArch64MacroAssembler.isDoubleImmediate(input.asDouble())) {
                    masm.fmov(64, dst, input.asDouble());
                } else {
                    masm.fldr(64, dst, (AArch64Address) crb.asDoubleConstRef(input));
                }
                break;
            case Object:
                if (input.isNull()) {
                    masm.mov(dst, 0);
                } else if (crb.target.inlineObjects) {
                    crb.recordInlineDataInCode(input);
                    masm.forceMov(dst, 0xDEADDEADDEADDEADL);
                } else {
                    masm.ldr(64, dst, (AArch64Address) crb.recordDataReferenceInCode(input, 8));
                }
                break;
            default:
                throw JVMCIError.shouldNotReachHere("kind=" + input.getJavaKind().getStackKind());
        }
    }

    /**
     * Returns AArch64Address of given StackSlot. We cannot use CompilationResultBuilder.asAddress
     * since this calls AArch64MacroAssembler.makeAddress with displacements that may be larger than
     * 9-bit signed, which cannot be handled by that method.
     *
     * Instead we create an address ourselves. We use scaled unsigned addressing since we know the
     * transfersize, which gives us a 15-bit address range (for longs/doubles) respectively a 14-bit
     * range (for everything else).
     *
     * @param scratch Scratch register that can be used to load address. If Value.ILLEGAL this
     *            instruction fails if we try to access a StackSlot that is too large to be loaded
     *            directly.
     * @return AArch64Address of given StackSlot. Uses scratch register if necessary to do so.
     */
    private static AArch64Address loadStackSlotAddress(CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot slot, AllocatableValue scratch) {
        AArch64Kind kind = (AArch64Kind) scratch.getPlatformKind();
        assert kind.isInteger();
        int displacement = crb.frameMap.offsetForStackSlot(slot);
        int transferSize = slot.getPlatformKind().getSizeInBytes();
        Register scratchReg = Value.ILLEGAL.equals(scratch) ? AArch64.zr : asRegister(scratch);
        return masm.makeAddress(AArch64.sp, displacement, scratchReg, transferSize, /* allowOverwrite */false);
    }
}
