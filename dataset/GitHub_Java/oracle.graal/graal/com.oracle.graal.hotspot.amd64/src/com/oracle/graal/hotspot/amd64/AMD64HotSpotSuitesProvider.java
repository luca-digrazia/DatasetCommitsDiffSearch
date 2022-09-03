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
package com.oracle.graal.hotspot.amd64;

import com.oracle.graal.compiler.amd64.AMD64SuitesProvider;
import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.hotspot.lir.HotSpotZapRegistersPhase;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.options.OptionValues;
import com.oracle.graal.phases.tiers.CompilerConfiguration;

public class AMD64HotSpotSuitesProvider extends AMD64SuitesProvider {

    public AMD64HotSpotSuitesProvider(CompilerConfiguration compilerConfiguration, Plugins plugins) {
        super(compilerConfiguration, plugins);
    }

    @Override
    public LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites lirSuites = super.createLIRSuites(options);
        if (GraalOptions.DetailedAsserts.getValue(options)) {
            lirSuites.getPostAllocationOptimizationStage().appendPhase(new HotSpotZapRegistersPhase());
        }
        return lirSuites;
    }
}
