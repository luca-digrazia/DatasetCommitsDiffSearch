/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.lir;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.max.graal.lir.LIRInstruction.OperandMode;
import com.oracle.max.graal.lir.LIRInstruction.ValueProcedure;

/**
 * This class represents garbage collection and deoptimization information attached to a LIR instruction.
 */
public class LIRDebugInfo {
    public final CiFrame topFrame;
    private final CiVirtualObject[] virtualObjects;
    private final List<CiStackSlot> pointerSlots;
    public final LabelRef exceptionEdge;
    private CiDebugInfo debugInfo;

    public LIRDebugInfo(CiFrame topFrame, CiVirtualObject[] virtualObjects, List<CiStackSlot> pointerSlots, LabelRef exceptionEdge) {
        this.topFrame = topFrame;
        this.virtualObjects = virtualObjects;
        this.pointerSlots = pointerSlots;
        this.exceptionEdge = exceptionEdge;
    }

    public boolean hasDebugInfo() {
        return debugInfo != null;
    }

    public CiDebugInfo debugInfo() {
        assert debugInfo != null : "debug info not allocated yet";
        return debugInfo;
    }

    /**
     * Iterates the frame state and calls the {@link ValueProcedure} for every variable.
     *
     * @param proc The procedure called for variables.
     */
    public void forEachState(ValueProcedure proc) {
        for (CiFrame cur = topFrame; cur != null; cur = cur.caller()) {
            processValues(cur.values, proc);
        }
        if (virtualObjects != null) {
            for (CiVirtualObject obj : virtualObjects) {
                processValues(obj.values(), proc);
            }
        }
    }

    /**
     * We filter out constant and illegal values ourself before calling the procedure, so {@link OperandFlag#Constant} and {@link OperandFlag#Illegal} need not be set.
     */
    private static final EnumSet<OperandFlag> STATE_FLAGS = EnumSet.of(OperandFlag.Register, OperandFlag.Stack);

    private void processValues(CiValue[] values, ValueProcedure proc) {
        for (int i = 0; i < values.length; i++) {
            CiValue value = values[i];
            if (value instanceof CiMonitorValue) {
                CiMonitorValue monitor = (CiMonitorValue) value;
                if (processed(monitor.owner)) {
                    monitor.owner = proc.doValue(monitor.owner, OperandMode.Alive, STATE_FLAGS);
                }

            } else if (processed(value)) {
                values[i] = proc.doValue(value, OperandMode.Alive, STATE_FLAGS);
            }
        }
    }

    private boolean processed(CiValue value) {
        if (isIllegal(value)) {
            // Ignore dead local variables.
            return false;
        } else if (isConstant(value)) {
            // Ignore constants, the register allocator does not need to see them.
            return false;
        } else if (isVirtualObject(value)) {
            assert Arrays.asList(virtualObjects).contains(value);
            return false;
        } else {
            return true;
        }
    }


    public void finish(CiBitMap registerRefMap, CiBitMap frameRefMap, FrameMap frameMap) {
        debugInfo = new CiDebugInfo(topFrame, registerRefMap, frameRefMap);

        // Add additional stack slots for outgoing method parameters.
        if (pointerSlots != null) {
            for (CiStackSlot v : pointerSlots) {
                frameMap.setReference(v, registerRefMap, frameRefMap);
            }
        }
    }


    @Override
    public String toString() {
        return debugInfo != null ? debugInfo.toString() : topFrame.toString();
    }
}
