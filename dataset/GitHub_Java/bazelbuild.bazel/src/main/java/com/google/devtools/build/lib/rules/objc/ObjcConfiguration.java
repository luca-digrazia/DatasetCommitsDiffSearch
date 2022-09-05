// Copyright 2014 Google Inc. All rights reserved.
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

package com.google.devtools.build.lib.rules.objc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.BuildOptions;
import com.google.devtools.build.lib.analysis.config.CompilationMode;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.xcode.common.Platform;

import java.util.List;

import javax.annotation.Nullable;

/**
 * A compiler configuration containing flags required for Objective-C compilation.
 */
public class ObjcConfiguration extends BuildConfiguration.Fragment {
  @VisibleForTesting
  static final ImmutableList<String> DBG_COPTS = ImmutableList.of("-O0", "-DDEBUG=1",
      "-fstack-protector", "-fstack-protector-all", "-D_GLIBCXX_DEBUG_PEDANTIC", "-D_GLIBCXX_DEBUG",
      "-D_GLIBCPP_CONCEPT_CHECKS");

  // TODO(bazel-team): Add "-DDEBUG=1" to FASTBUILD_COPTS.
  @VisibleForTesting
  static final ImmutableList<String> FASTBUILD_COPTS = ImmutableList.of("-O0");

  @VisibleForTesting
  static final ImmutableList<String> OPT_COPTS =
      ImmutableList.of("-Os", "-DNDEBUG=1", "-Wno-unused-variable", "-Winit-self", "-Wno-extra");

  private final String iosSdkVersion;
  private final String iosMinimumOs;
  private final String iosSimulatorVersion;
  private final String iosSimulatorDevice;
  private final String iosCpu;
  private final String xcodeOptions;
  private final boolean generateDebugSymbols;
  private final boolean runMemleaks;
  private final List<String> copts;
  private final CompilationMode compilationMode;
  private final List<String> iosMultiCpus;
  private final String iosSplitCpu;

  // We only load these labels if the mode which uses them is enabled. That is know as part of the
  // BuildConfiguration. This label needs to be part of a configuration because only configurations
  // can conditionally cause loading.
  // They are referenced from late bound attributes, and if loading wasn't forced in a
  // configuration, the late bound attribute will fail to be initialized because it hasn't been
  // loaded.
  @Nullable private final Label gcovLabel;
  @Nullable private final Label dumpSymsLabel;
  @Nullable private final Label defaultProvisioningProfileLabel;

  ObjcConfiguration(
      ObjcCommandLineOptions objcOptions,
      BuildConfiguration.Options options,
      @Nullable Label gcovLabel,
      @Nullable Label dumpSymsLabel,
      @Nullable Label defaultProvisioningProfileLabel) {
    this.iosSdkVersion = Preconditions.checkNotNull(objcOptions.iosSdkVersion, "iosSdkVersion");
    this.iosMinimumOs = Preconditions.checkNotNull(objcOptions.iosMinimumOs, "iosMinimumOs");
    this.iosSimulatorDevice =
        Preconditions.checkNotNull(objcOptions.iosSimulatorDevice, "iosSimulatorDevice");
    this.iosSimulatorVersion =
        Preconditions.checkNotNull(objcOptions.iosSimulatorVersion, "iosSimulatorVersion");
    this.iosCpu = Preconditions.checkNotNull(objcOptions.iosCpu, "iosCpu");
    this.xcodeOptions = Preconditions.checkNotNull(objcOptions.xcodeOptions, "xcodeOptions");
    this.generateDebugSymbols = objcOptions.generateDebugSymbols;
    this.runMemleaks = objcOptions.runMemleaks;
    this.copts = ImmutableList.copyOf(objcOptions.copts);
    this.compilationMode = Preconditions.checkNotNull(options.compilationMode, "compilationMode");
    this.gcovLabel = gcovLabel;
    this.dumpSymsLabel = dumpSymsLabel;
    this.defaultProvisioningProfileLabel = defaultProvisioningProfileLabel;
    this.iosMultiCpus = Preconditions.checkNotNull(objcOptions.iosMultiCpus, "iosMultiCpus");
    this.iosSplitCpu = Preconditions.checkNotNull(objcOptions.iosSplitCpu, "iosSplitCpu");
  }

  public String getIosSdkVersion() {
    return iosSdkVersion;
  }

  /**
   * Returns the minimum iOS version supported by binaries and libraries. Any dependencies on newer
   * iOS version features or libraries will become weak dependencies which are only loaded if the
   * runtime OS supports them.
   */
  public String getMinimumOs() {
    return iosMinimumOs;
  }

  /**
   * Returns the type of device (e.g. 'iPhone 6') to simulate when running on the simulator.
   */
  public String getIosSimulatorDevice() {
    return iosSimulatorDevice;
  }

  public String getIosSimulatorVersion() {
    return iosSimulatorVersion;
  }

  public String getIosCpu() {
    return iosCpu;
  }

  public Platform getPlatform() {
    return Platform.forArch(getIosCpu());
  }

  public String getXcodeOptions() {
    return xcodeOptions;
  }

  public boolean generateDebugSymbols() {
    return generateDebugSymbols;
  }

  public boolean runMemleaks() {
    return runMemleaks;
  }

  /**
   * Returns the current compilation mode.
   */
  public CompilationMode getCompilationMode() {
    return compilationMode;
  }

  /**
   * Returns the default set of clang options for the current compilation mode.
   */
  public List<String> getCoptsForCompilationMode() {
    switch (compilationMode) {
      case DBG:
        return DBG_COPTS;
      case FASTBUILD:
        return FASTBUILD_COPTS;
      case OPT:
        return OPT_COPTS;
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns options passed to (Apple) clang when compiling Objective C. These options should be
   * applied after any default options but before options specified in the attributes of the rule.
   */
  public List<String> getCopts() {
    return copts;
  }

  /**
   * Returns the label of the gcov binary, used to get test coverage data. Null iff not in coverage
   * mode.
   */
  @Nullable public Label getGcovLabel() {
    return gcovLabel;
  }

  /**
   * Returns the label of the dump_syms binary, used to get debug symbols from a binary. Null iff
   * !{@link #generateDebugSymbols}.
   */
  @Nullable public Label getDumpSymsLabel() {
    return dumpSymsLabel;
  }

  /**
   * Returns the label of the default provisioning profile to use when bundling/signing the
   * application. Null iff iOS CPU indicates a simulator is being targeted.
   */
  @Nullable public Label getDefaultProvisioningProfileLabel() {
    return defaultProvisioningProfileLabel;
  }

  /**
   * List of all CPUs that this invocation is being built for. Different from {@link #getIosCpu()}
   * which is the specific CPU <b>this target</b> is being built for.
   */
  public List<String> getIosMultiCpus() {
    return iosMultiCpus;
  }

  @Override
  public String getName() {
    return "Objective-C";
  }

  @Override
  public String cacheKey() {
    return iosSdkVersion;
  }

  @Nullable
  @Override
  public String getOutputDirectoryName() {
    return !iosSplitCpu.isEmpty() ? "ios-" + iosSplitCpu : null;
  }

  @Override
  public void reportInvalidOptions(EventHandler reporter, BuildOptions buildOptions) {
    if (generateDebugSymbols && !iosMultiCpus.isEmpty()) {
      reporter.handle(Event.error(
          "--objc_generate_debug_symbols is not supported when --ios_multi_cpus is set"));
    }
  }
}
