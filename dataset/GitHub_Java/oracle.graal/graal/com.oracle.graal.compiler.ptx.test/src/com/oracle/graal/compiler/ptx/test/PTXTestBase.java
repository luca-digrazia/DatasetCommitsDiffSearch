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
package com.oracle.graal.compiler.ptx.test;

import com.oracle.graal.api.code.CodeCacheProvider;
import com.oracle.graal.api.code.CompilationResult;
import com.oracle.graal.api.code.SpeculationLog;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.runtime.Graal;
import com.oracle.graal.compiler.GraalCompiler;
import com.oracle.graal.compiler.ptx.PTXBackend;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.java.GraphBuilderConfiguration;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.hotspot.HotSpotGraalRuntime;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhasePlan;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.ptx.PTX;

public abstract class PTXTestBase extends GraalCompilerTest {

    protected CompilationResult compile(String test) {
        StructuredGraph graph = parse(test);
        Debug.dump(graph, "Graph");
        TargetDescription target = new TargetDescription(new PTX(), true, 1, 0, true);
        PTXBackend ptxBackend = new PTXBackend(Graal.getRequiredCapability(CodeCacheProvider.class), target);
        PhasePlan phasePlan = new PhasePlan();
        GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDefault(),
                                                                    OptimisticOptimizations.NONE);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, new PTXPhase());
        new PTXPhase().apply(graph);
        CompilationResult result = GraalCompiler.compileMethod(runtime, HotSpotGraalRuntime.getInstance().getReplacements(),
                                                               ptxBackend, target, graph.method(), graph, null, phasePlan,
                                                               OptimisticOptimizations.NONE, new SpeculationLog());
        return result;
    }

}
