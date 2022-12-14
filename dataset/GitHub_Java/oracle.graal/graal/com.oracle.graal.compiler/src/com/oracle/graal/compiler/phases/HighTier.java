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
package com.oracle.graal.compiler.phases;

import static com.oracle.graal.phases.GraalOptions.*;

import com.oracle.graal.loop.phases.*;
import com.oracle.graal.nodes.spi.Lowerable.LoweringType;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.virtual.phases.ea.*;

public class HighTier extends PhaseSuite<HighTierContext> {

    public HighTier() {
        if (FullUnroll.getValue()) {
            addPhase(new LoopFullUnrollPhase());
        }

        if (OptTailDuplication.getValue()) {
            addPhase(new TailDuplicationPhase());
        }

        if (PartialEscapeAnalysis.getValue()) {
            addPhase(new PartialEscapeAnalysisPhase(true, OptEarlyReadElimination.getValue()));
        }

        if (OptConvertDeoptsToGuards.getValue()) {
            addPhase(new ConvertDeoptimizeToGuardPhase());
        }

        addPhase(new LockEliminationPhase());

        if (OptLoopTransform.getValue()) {
            addPhase(new LoopTransformHighPhase());
            addPhase(new LoopTransformLowPhase());
        }
        addPhase(new RemoveValueProxyPhase());

        if (CullFrameStates.getValue()) {
            addPhase(new CullFrameStatesPhase());
        }

        if (OptCanonicalizer.getValue()) {
            addPhase(new CanonicalizerPhase());
        }

        addPhase(new LoweringPhase(LoweringType.BEFORE_GUARDS));
    }

}
