/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.asm.*;

/**
 * Restores registers from stack slots.
 */
@Opcode("RESTORE_REGISTER")
public final class AMD64RestoreRegistersOp extends AMD64LIRInstruction {

    /**
     * The slots from which the registers are restored.
     */
    @Use(STACK) protected final StackSlot[] slots;

    /**
     * The operation that saved the registers restored by this operation.
     */
    private final AMD64SaveRegistersOp save;

    public AMD64RestoreRegistersOp(StackSlot[] source, AMD64SaveRegistersOp save) {
        this.slots = source;
        this.save = save;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        for (AMD64LIRInstruction restoringMove : save.restoringMoves) {
            if (restoringMove != null) {
                restoringMove.emitCode(tasm, masm);
            }
        }
    }
}
