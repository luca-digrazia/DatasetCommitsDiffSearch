// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.r13;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.NdkPaths;
import com.google.devtools.build.lib.bazel.rules.android.ndkcrosstools.StlImpl;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CToolchain;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CompilationMode;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CompilationModeFlags;

/**
 * Crosstool definitions for x86. These values are based on the setup.mk files in the Android NDK
 * toolchain directories.
 */
final class X86Crosstools {
  private final NdkPaths ndkPaths;
  private final StlImpl stlImpl;

  X86Crosstools(NdkPaths ndkPaths, StlImpl stlImpl) {
    this.ndkPaths = ndkPaths;
    this.stlImpl = stlImpl;
  }

  ImmutableList<CToolchain.Builder> createCrosstools() {
    /** x86 */
    // clang
    CToolchain.Builder x86Clang =
        createBaseX86ClangToolchain("x86", "i686")
            // Workaround for https://code.google.com/p/android/issues/detail?id=220159.
            .addCompilerFlag("-mstackrealign")
            .setToolchainIdentifier("x86-clang3.8")
            .setTargetCpu("x86")
            .addAllToolPath(ndkPaths.createClangToolpaths("x86-4.9", "i686-linux-android", null))
            .setBuiltinSysroot(ndkPaths.createBuiltinSysroot("x86"));

    stlImpl.addStlImpl(x86Clang, "4.9");

    /** x86_64 */
    CToolchain.Builder x8664Clang =
        createBaseX86ClangToolchain("x86_64", "x86_64")
            .setToolchainIdentifier("x86_64-clang3.8")
            .setTargetCpu("x86_64")
            .addAllToolPath(
                ndkPaths.createClangToolpaths("x86_64-4.9", "x86_64-linux-android", null))
            .setBuiltinSysroot(ndkPaths.createBuiltinSysroot("x86_64"));

    stlImpl.addStlImpl(x8664Clang, "4.9");

    return ImmutableList.of(x86Clang, x8664Clang);
  }

  private CToolchain.Builder createBaseX86ClangToolchain(String x86Arch, String llvmArch) {
    String gccToolchain = ndkPaths.createGccToolchainPath(x86Arch + "-4.9");
    String llvmTriple = llvmArch + "-none-linux-android";

    return CToolchain.newBuilder()
        .setCompiler("clang3.8")

        .addCxxBuiltinIncludeDirectory(
            ndkPaths.createClangToolchainBuiltinIncludeDirectory(
                AndroidNdkCrosstoolsR13.CLANG_VERSION))

        // Compiler flags
        .addCompilerFlag("-gcc-toolchain")
        .addCompilerFlag(gccToolchain)
        .addCompilerFlag("-target")
        .addCompilerFlag(llvmTriple)
        .addCompilerFlag("-ffunction-sections")
        .addCompilerFlag("-funwind-tables")
        .addCompilerFlag("-fstack-protector-strong")
        .addCompilerFlag("-fPIC")
        .addCompilerFlag("-Wno-invalid-command-line-argument")
        .addCompilerFlag("-Wno-unused-command-line-argument")
        .addCompilerFlag("-no-canonical-prefixes")

        // Linker flags
        .addLinkerFlag("-gcc-toolchain")
        .addLinkerFlag(gccToolchain)
        .addLinkerFlag("-target")
        .addLinkerFlag(llvmTriple)
        .addLinkerFlag("-no-canonical-prefixes")

        // Additional release flags
        .addCompilationModeFlags(
            CompilationModeFlags.newBuilder()
                .setMode(CompilationMode.OPT)
                .addCompilerFlag("-O2")
                .addCompilerFlag("-g")
                .addCompilerFlag("-DNDEBUG"))

        // Additional debug flags
        .addCompilationModeFlags(
            CompilationModeFlags.newBuilder()
                .setMode(CompilationMode.DBG)
                .addCompilerFlag("-O0")
                .addCompilerFlag("-g"))

        .setTargetSystemName("x86-linux-android");
  }
}
