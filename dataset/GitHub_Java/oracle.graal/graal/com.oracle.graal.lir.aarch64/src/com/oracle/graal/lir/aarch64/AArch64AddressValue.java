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

import java.util.EnumSet;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.asm.aarch64.AArch64Address;
import com.oracle.graal.asm.aarch64.AArch64Address.AddressingMode;
import com.oracle.graal.asm.aarch64.AArch64Assembler;
import com.oracle.graal.asm.aarch64.AArch64Assembler.ExtendType;
import com.oracle.graal.lir.CompositeValue;
import com.oracle.graal.lir.InstructionValueConsumer;
import com.oracle.graal.lir.InstructionValueProcedure;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;

public final class AArch64AddressValue extends CompositeValue {
    private static final EnumSet<OperandFlag> flags = EnumSet.of(OperandFlag.REG, OperandFlag.ILLEGAL);

    @Component({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue base;
    @Component({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue offset;
    private final int immediate;

    /**
     * Whether register offset should be scaled or not.
     */
    private final boolean scaled;
    private final AddressingMode addressingMode;

    public AArch64AddressValue(LIRKind kind, AllocatableValue base, AllocatableValue offset, int immediate, boolean scaled, AddressingMode addressingMode) {
        super(kind);
        this.base = base;
        this.offset = offset;
        this.immediate = immediate;
        this.scaled = scaled;
        this.addressingMode = addressingMode;
    }

    private static Register toRegister(AllocatableValue value) {
        if (value.equals(Value.ILLEGAL)) {
            return AArch64.zr;
        } else {
            return ((RegisterValue) value).getRegister();
        }
    }

    public AllocatableValue getBase() {
        return base;
    }

    public AllocatableValue getOffset() {
        return offset;
    }

    public int getImmediate() {
        return immediate;
    }

    public boolean isScaled() {
        return scaled;
    }

    public AddressingMode getAddressingMode() {
        return addressingMode;
    }

    public AArch64Address toAddress() {
        Register baseReg = toRegister(base);
        Register offsetReg = toRegister(offset);
        AArch64Assembler.ExtendType extendType = addressingMode == AddressingMode.EXTENDED_REGISTER_OFFSET ? ExtendType.SXTW : null;
        return AArch64Address.createAddress(addressingMode, baseReg, offsetReg, immediate, scaled, extendType);
    }

    @Override
    public CompositeValue forEachComponent(LIRInstruction inst, LIRInstruction.OperandMode mode, InstructionValueProcedure proc) {
        AllocatableValue newBase = (AllocatableValue) proc.doValue(inst, base, mode, flags);
        AllocatableValue newOffset = (AllocatableValue) proc.doValue(inst, offset, mode, flags);
        if (!base.identityEquals(newBase) || !offset.identityEquals(newOffset)) {
            return new AArch64AddressValue(getLIRKind(), newBase, newOffset, immediate, scaled, addressingMode);
        }
        return this;
    }

    @Override
    protected void visitEachComponent(LIRInstruction inst, LIRInstruction.OperandMode mode, InstructionValueConsumer proc) {
        proc.visitValue(inst, base, mode, flags);
        proc.visitValue(inst, offset, mode, flags);
    }
}
