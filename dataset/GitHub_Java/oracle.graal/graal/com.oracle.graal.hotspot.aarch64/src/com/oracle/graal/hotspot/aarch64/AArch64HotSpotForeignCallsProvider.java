/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.aarch64;

import static com.oracle.graal.compiler.common.LocationIdentity.any;
import static com.oracle.graal.hotspot.HotSpotBackend.Options.PreferGraalStubs;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.JUMP_ADDRESS;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect.PRESERVES_REGISTERS;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition.LEAF;
import static com.oracle.graal.hotspot.replacements.CRC32Substitutions.UPDATE_BYTES_CRC32;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static jdk.vm.ci.meta.Value.ILLEGAL;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkageImpl;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotHostForeignCallsProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.word.WordTypes;

public class AArch64HotSpotForeignCallsProvider extends HotSpotHostForeignCallsProvider {

    private final Value[] nativeABICallerSaveRegisters;

    public AArch64HotSpotForeignCallsProvider(HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
                    WordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        super(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes);
        this.nativeABICallerSaveRegisters = nativeABICallerSaveRegisters;
    }

    @Override
    public void initialize(HotSpotProviders providers) {
        HotSpotVMConfig config = jvmciRuntime.getConfig();
        TargetDescription target = providers.getCodeCache().getTarget();
        PlatformKind word = target.arch.getWordKind();

        // The calling convention for the exception handler stub is (only?) defined in
        // TemplateInterpreterGenerator::generate_throw_exception()
        RegisterValue exception = r0.asValue(LIRKind.reference(word));
        RegisterValue exceptionPc = r3.asValue(LIRKind.value(word));
        CallingConvention exceptionCc = new CallingConvention(0, ILLEGAL, exception, exceptionPc);
        register(new HotSpotForeignCallLinkageImpl(HotSpotBackend.EXCEPTION_HANDLER, 0L, PRESERVES_REGISTERS, LEAF, null, exceptionCc, NOT_REEXECUTABLE, any()));
        register(new HotSpotForeignCallLinkageImpl(HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER, JUMP_ADDRESS, PRESERVES_REGISTERS, LEAF, exceptionCc, null, NOT_REEXECUTABLE, any()));

        if (PreferGraalStubs.getValue()) {
            throw JVMCIError.unimplemented("PreferGraalStubs");
        }

        // These stubs do callee saving
        if (config.useCRC32Intrinsics) {
            registerForeignCall(UPDATE_BYTES_CRC32, config.updateBytesCRC32Stub, NativeCall, PRESERVES_REGISTERS, LEAF, NOT_REEXECUTABLE, any());
        }

        super.initialize(providers);
    }

    @Override
    public Value[] getNativeABICallerSaveRegisters() {
        return nativeABICallerSaveRegisters;
    }

}
