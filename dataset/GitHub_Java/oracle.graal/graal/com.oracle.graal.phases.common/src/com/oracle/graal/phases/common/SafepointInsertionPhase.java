/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;

/**
 * Adds safepoints to loops and return points.
 */
public class SafepointInsertionPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        if (GraalOptions.GenLoopSafepoints) {
            for (LoopEndNode loopEnd : graph.getNodes(LoopEndNode.class)) {
                if (!loopEnd.canSafepoint()) {
                    continue;
                }
                SafepointNode safepoint = graph.add(new SafepointNode());
                graph.addBeforeFixed(loopEnd, safepoint);
            }
        }

        if (GraalOptions.GenSafepoints) {
            if (!GraalOptions.OptEliminateSafepoints || graph.getNodes(MethodCallTargetNode.class).isNotEmpty()) {
                for (ReturnNode loopEnd : graph.getNodes(ReturnNode.class)) {
                    SafepointNode safepoint = graph.add(new SafepointNode());
                    graph.addBeforeFixed(loopEnd, safepoint);
                }
            }
        }
    }
}
