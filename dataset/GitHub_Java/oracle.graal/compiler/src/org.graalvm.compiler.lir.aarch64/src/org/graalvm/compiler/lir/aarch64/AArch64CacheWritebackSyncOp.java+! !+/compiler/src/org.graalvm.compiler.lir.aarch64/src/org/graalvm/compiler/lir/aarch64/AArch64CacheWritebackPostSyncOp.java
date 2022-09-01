/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package org.graalvm.compiler.lir.aarch64;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

/**
 * Implements {@code jdk.internal.misc.Unsafe.writebackPreSync0(long)} and
 * {@code jdk.internal.misc.Unsafe.writebackPostSync0(long)}.
 */
public final class AArch64CacheWritebackSyncOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64CacheWritebackSyncOp> TYPE = LIRInstructionClass.create(AArch64CacheWritebackSyncOp.class);

    private final boolean isPreSync;

    public AArch64CacheWritebackSyncOp(boolean isPreSync) {
        super(TYPE);
        this.isPreSync = isPreSync;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        // only need a barrier post sync
        if (!isPreSync) {
            masm.dmb(AArch64MacroAssembler.BarrierKind.ANY_ANY);
        }
    }
}
