/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.stackslotalloc;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;

/**
 * A {@link StackSlotAllocator} is responsible for translating {@link VirtualStackSlot virtual}
 * stack slots into {@link StackSlot real} stack slots. This includes changing all occurrences of
 * {@link VirtualStackSlot} in the {@link LIRGenerationResult#getLIR() LIR} to {@link StackSlot}.
 */
public interface StackSlotAllocator {
    /**
     * The number of allocated stack slots.
     */
    DebugMetric allocatedSlots = Debug.metric("StackSlotAllocator[allocatedSlots]");
    /**
     * The number of reused stack slots.
     */
    DebugMetric reusedSlots = Debug.metric("StackSlotAllocator[reusedSlots]");
    /**
     * The size (in bytes) required for all allocated stack slots. Note that this number corresponds
     * to the actual frame size and might include alignment.
     */
    DebugMetric allocatedFramesize = Debug.metric("StackSlotAllocator[AllocatedFramesize]");
    /** The size (in bytes) required for all virtual stack slots. */
    DebugMetric virtualFramesize = Debug.metric("StackSlotAllocator[VirtualFramesize]");

    void allocateStackSlots(FrameMapBuilderTool builder, LIRGenerationResult res);
}
