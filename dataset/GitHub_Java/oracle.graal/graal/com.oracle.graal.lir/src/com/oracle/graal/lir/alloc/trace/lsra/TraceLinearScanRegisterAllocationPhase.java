/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace.lsra;

import java.util.List;

import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.TargetDescription;

final class TraceLinearScanRegisterAllocationPhase extends TraceLinearScanAllocationPhase {

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, Trace trace, TraceLinearScanAllocationContext context) {
        TraceLinearScan allocator = context.allocator;
        allocateRegisters(allocator);
    }

    @SuppressWarnings("try")
    private static void allocateRegisters(TraceLinearScan allocator) {
        try (Indent indent = Debug.logAndIndent("allocate registers")) {
            FixedInterval precoloredIntervals = allocator.createFixedUnhandledList();
            TraceInterval notPrecoloredIntervals = allocator.createUnhandledListByFrom(TraceLinearScan.IS_VARIABLE_INTERVAL);

            // allocate cpu registers
            TraceLinearScanWalker lsw = new TraceLinearScanWalker(allocator, precoloredIntervals, notPrecoloredIntervals);
            lsw.walk();
            lsw.finishAllocation();
        }
    }

}
