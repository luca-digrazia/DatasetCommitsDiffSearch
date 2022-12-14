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

package org.graalvm.compiler.lir.amd64;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

/**
 * Implements {@code jdk.internal.misc.Unsafe.writebackPreSync0(long)} and
 * {@code jdk.internal.misc.Unsafe.writebackPostSync0(long)}.
 */
public final class AMD64CacheWritebackSyncOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64CacheWritebackSyncOp> TYPE = LIRInstructionClass.create(AMD64CacheWritebackSyncOp.class);

    private final boolean isPreSync;

    public AMD64CacheWritebackSyncOp(boolean isPreSync) {
        super(TYPE);
        this.isPreSync = isPreSync;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        boolean optimized = masm.supportsCPUFeature("FLUSHOPT");
        boolean noEvict = masm.supportsCPUFeature("CLWB");

        // pick the correct implementation
        if (!isPreSync && (optimized || noEvict)) {
            // need an sfence for post flush when using clflushopt or clwb
            // otherwise no no need for any synchroniaztion
            masm.sfence();
        }
    }
}
