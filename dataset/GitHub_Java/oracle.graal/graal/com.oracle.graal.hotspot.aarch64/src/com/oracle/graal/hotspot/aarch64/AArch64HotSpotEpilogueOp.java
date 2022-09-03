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
package com.oracle.graal.hotspot.aarch64;

import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import com.oracle.graal.hotspot.GraalHotSpotVMConfig;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.aarch64.AArch64BlockEndOp;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;

/**
 * Superclass for operations that leave a method's frame.
 */
abstract class AArch64HotSpotEpilogueOp extends AArch64BlockEndOp {

    private final GraalHotSpotVMConfig config;

    protected AArch64HotSpotEpilogueOp(LIRInstructionClass<? extends AArch64HotSpotEpilogueOp> c, GraalHotSpotVMConfig config) {
        super(c);
        this.config = config;
    }

    protected void leaveFrame(CompilationResultBuilder crb, AArch64MacroAssembler masm, boolean emitSafepoint) {
        assert crb.frameContext != null : "We never elide frames in aarch64";
        crb.frameContext.leave(crb);
        if (emitSafepoint) {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                AArch64HotSpotSafepointOp.emitCode(crb, masm, config, true, scratch, null);
            }
        }
    }
}
