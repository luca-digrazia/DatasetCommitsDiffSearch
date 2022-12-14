/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.amd64.AMD64DeoptimizeOp.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

/**
 * Removes the current frame and tail calls the uncommon trap routine.
 */
@Opcode("DEOPT_CALLER")
final class AMD64HotSpotDeoptimizeCallerOp extends AMD64HotSpotEpilogueOp {

    private final DeoptimizationAction action;
    private final DeoptimizationReason reason;

    AMD64HotSpotDeoptimizeCallerOp(DeoptimizationAction action, DeoptimizationReason reason) {
        this.action = action;
        this.reason = reason;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        if (isStackSlot(savedRbp)) {
            // Restoring RBP from the stack must be done before the frame is removed
            masm.movq(rbp, (AMD64Address) tasm.asAddress(savedRbp));
        } else {
            Register framePointer = asRegister(savedRbp);
            if (framePointer != rbp) {
                masm.movq(rbp, framePointer);
            }
        }
        if (tasm.frameContext != null) {
            tasm.frameContext.leave(tasm);
        }
        HotSpotGraalRuntime runtime = graalRuntime();
        Register thread = runtime.getRuntime().threadRegister();
        masm.movl(new AMD64Address(thread, runtime.getConfig().pendingDeoptimizationOffset), tasm.runtime.encodeDeoptActionAndReason(action, reason));
        AMD64Call.directJmp(tasm, masm, tasm.runtime.lookupRuntimeCall(DEOPTIMIZE));
    }
}
