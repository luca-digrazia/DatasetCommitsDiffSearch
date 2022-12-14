/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.phases.GraalOptions.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

/**
 * Emits a safepoint poll.
 */
@Opcode("SAFEPOINT")
public class AMD64SafepointOp extends AMD64LIRInstruction {

    @State protected LIRFrameState state;

    private final HotSpotVMConfig config;

    public AMD64SafepointOp(LIRFrameState state, HotSpotVMConfig config) {
        this.state = state;
        this.config = config;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler asm) {
        Register scratch = tasm.frameMap.registerConfig.getScratchRegister();
        int pos = asm.codeBuffer.position();
        int offset = SafepointPollOffset % tasm.target.pageSize;
        if (config.isPollingPageFar) {
            asm.movq(scratch, config.safepointPollingAddress + offset);
            tasm.recordMark(Marks.MARK_POLL_FAR);
            tasm.recordSafepoint(pos, state);
            asm.movq(scratch, new Address(tasm.target.wordKind, scratch.asValue()));
        } else {
            tasm.recordMark(Marks.MARK_POLL_NEAR);
            tasm.recordSafepoint(pos, state);
            // The C++ code transforms the polling page offset into an RIP displacement
            // to the real address at that offset in the polling page.
            asm.movq(scratch, new Address(tasm.target.wordKind, rip.asValue(), offset));
        }
    }
}
