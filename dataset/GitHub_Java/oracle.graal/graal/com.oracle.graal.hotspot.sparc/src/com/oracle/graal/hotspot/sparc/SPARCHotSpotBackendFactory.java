/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.sparc.*;

@ServiceProvider(HotSpotBackendFactory.class)
public class SPARCHotSpotBackendFactory implements HotSpotBackendFactory {

    protected static Architecture createArchitecture() {
        return new SPARC();
    }

    protected TargetDescription createTarget() {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        return new TargetDescription(createArchitecture(), true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    public HotSpotBackend createBackend(HotSpotGraalRuntime runtime, HotSpotBackend host) {
        assert host == null;
        TargetDescription target = createTarget();

        HotSpotMetaAccessProvider metaAccess = new HotSpotMetaAccessProvider(runtime);
        HotSpotCodeCacheProvider codeCache = new SPARCHotSpotCodeCacheProvider(runtime, target);
        HotSpotConstantReflectionProvider constantReflection = new HotSpotConstantReflectionProvider(runtime);
        Value[] nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(runtime.getConfig(), codeCache.getRegisterConfig());
        HotSpotForeignCallsProvider foreignCalls = new SPARCHotSpotForeignCallsProvider(runtime, metaAccess, codeCache, nativeABICallerSaveRegisters);
        HotSpotHostLoweringProvider lowerer = new SPARCHotSpotLoweringProvider(runtime, metaAccess, foreignCalls);
        // Replacements cannot have speculative optimizations since they have
        // to be valid for the entire run of the VM.
        Assumptions assumptions = new Assumptions(false);
        Providers p = new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, null);
        HotSpotReplacementsImpl replacements = new HotSpotReplacementsImpl(p, runtime.getConfig(), assumptions);
        HotSpotDisassemblerProvider disassembler = new HotSpotDisassemblerProvider(runtime);
        HotSpotSuitesProvider suites = new HotSpotSuitesProvider(runtime);
        HotSpotRegisters registers = new HotSpotRegisters(Register.None, Register.None, Register.None); // FIXME
        HotSpotProviders providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, disassembler, suites, registers);

        return new SPARCHotSpotBackend(runtime, providers);
    }

    @SuppressWarnings("unused")
    private static Value[] createNativeABICallerSaveRegisters(HotSpotVMConfig config, RegisterConfig regConfig) {
        throw GraalInternalError.unimplemented();
    }

    public String getArchitecture() {
        return "SPARC";
    }

    public String getGraalRuntimeName() {
        return "basic";
    }

    @Override
    public String toString() {
        return getGraalRuntimeName() + ":" + getArchitecture();
    }
}
