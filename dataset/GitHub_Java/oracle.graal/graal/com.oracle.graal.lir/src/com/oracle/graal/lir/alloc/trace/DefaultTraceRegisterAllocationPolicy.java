/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace;

import java.util.ArrayList;

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.lir.alloc.trace.TraceAllocationPhase.TraceAllocationContext;
import com.oracle.graal.lir.alloc.trace.TraceRegisterAllocationPhase.Options;
import com.oracle.graal.lir.alloc.trace.TraceRegisterAllocationPolicy.AllocationStrategy;
import com.oracle.graal.lir.alloc.trace.lsra.TraceLinearScanPhase;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Manages the selection of allocation strategies.
 */
public final class DefaultTraceRegisterAllocationPolicy {

    public static final class TrivialTraceStrategy extends AllocationStrategy {

        public TrivialTraceStrategy(TraceRegisterAllocationPolicy plan) {
            plan.super();
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            return TraceUtil.isTrivialTrace(getLIR(), trace);
        }

        @Override
        protected TraceAllocationPhase<TraceAllocationContext> initAllocator(TargetDescription target, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory,
                        RegisterAllocationConfig registerAllocationConfig, AllocatableValue[] cachedStackSlots, TraceBuilderResult resultTraces, boolean neverSpillConstant,
                        ArrayList<AllocationStrategy> strategies) {
            return new TrivialTraceAllocator();
        }
    }

    public static final class TraceLinearScanStrategy extends AllocationStrategy {

        public TraceLinearScanStrategy(TraceRegisterAllocationPolicy plan) {
            plan.super();
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            return true;
        }

        @Override
        protected TraceAllocationPhase<TraceAllocationContext> initAllocator(TargetDescription target, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory,
                        RegisterAllocationConfig registerAllocationConfig, AllocatableValue[] cachedStackSlots, TraceBuilderResult resultTraces, boolean neverSpillConstant,
                        ArrayList<AllocationStrategy> strategies) {
            return new TraceLinearScanPhase(target, lirGenRes, spillMoveFactory, registerAllocationConfig, resultTraces, neverSpillConstant, cachedStackSlots);
        }
    }

    public static TraceRegisterAllocationPolicy defaultAllocationPolicy(TargetDescription target, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig, AllocatableValue[] cachedStackSlots, TraceBuilderResult resultTraces, boolean neverSpillConstant) {

        TraceRegisterAllocationPolicy plan = new TraceRegisterAllocationPolicy(target, lirGenRes, spillMoveFactory, registerAllocationConfig, cachedStackSlots, resultTraces, neverSpillConstant);
        if (Options.TraceRAtrivialBlockAllocator.getValue()) {
            plan.appendStrategy(new TrivialTraceStrategy(plan));
        }
        plan.appendStrategy(new TraceLinearScanStrategy(plan));
        return plan;
    }
}
