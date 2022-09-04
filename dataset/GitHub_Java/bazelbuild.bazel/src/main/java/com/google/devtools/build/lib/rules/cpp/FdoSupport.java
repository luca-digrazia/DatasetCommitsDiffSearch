// Copyright 2014 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.rules.cpp;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.lib.rules.cpp.CcToolchainFeatures.FeatureConfiguration;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec.VisibleForSerialization;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.skyframe.SkyFunction;

/**
 * Support class for FDO (feedback directed optimization).
 *
 * <p>{@link FdoSupport} is created from {@link FdoSupportFunction} (a {@link SkyFunction}),
 * which is requested from Skyframe by the {@code cc_toolchain} rule.
 *
 * <p>For each C++ compile action in the target configuration, {@link #configureCompilation} is
 * called, which adds command line options and input files required for the build.
 */
@Immutable
@AutoCodec
public class FdoSupport {
  /**
   * The FDO mode we are operating in.
   */
  @VisibleForSerialization
  enum FdoMode {
    /** FDO is turned off. */
    OFF,

    /** Profiling-based FDO using an explicitly recorded profile. */
    VANILLA,

    /** FDO based on automatically collected data. */
    AUTO_FDO,

    /** FDO based on cross binary collected data. */
    XBINARY_FDO,

    /** Instrumentation-based FDO implemented on LLVM. */
    LLVM_FDO,
  }

  /**
   * Coverage information output directory passed to {@code --fdo_instrument},
   * or {@code null} if FDO instrumentation is disabled.
   */
  private final String fdoInstrument;

  /**
   * Path of the profile file passed to {@code --fdo_optimize}, or
   * {@code null} if FDO optimization is disabled.  The profile file
   * can be a coverage ZIP or an AutoFDO feedback file.
   */
  // TODO(lberki): this should be a PathFragment
  private final Path fdoProfile;

  /**
   * FDO mode.
   */
  private final FdoMode fdoMode;

  /**
   * Creates an FDO support object.
   *
   * @param fdoInstrument value of the --fdo_instrument option
   * @param fdoProfile path to the profile file passed to --fdo_optimize option
   */
  @VisibleForSerialization
  @AutoCodec.Instantiator
  FdoSupport(FdoMode fdoMode, String fdoInstrument, Path fdoProfile) {
    this.fdoInstrument = fdoInstrument;
    this.fdoProfile = fdoProfile;
    this.fdoMode = fdoMode;
  }

  public Path getFdoProfile() {
    return fdoProfile;
  }

  /**
   * Configures a compile action builder by setting up command line options and auxiliary inputs
   * according to the FDO configuration. This method does nothing If FDO is disabled.
   */
  @ThreadSafe
  public ImmutableMap<String, String> configureCompilation(
      CppCompileActionBuilder builder,
      FeatureConfiguration featureConfiguration,
      FdoSupportProvider fdoSupportProvider) {

    ImmutableMap.Builder<String, String> variablesBuilder = ImmutableMap.builder();

    if (fdoSupportProvider != null && fdoSupportProvider.getPrefetchHintsArtifact() != null) {
      variablesBuilder.put(
          CompileBuildVariables.FDO_PREFETCH_HINTS_PATH.getVariableName(),
          fdoSupportProvider.getPrefetchHintsArtifact().getExecPathString());
    }

    // FDO is disabled -> do nothing.
    if (fdoInstrument == null && fdoProfile == null) {
      return ImmutableMap.of();
    }

    if (featureConfiguration.isEnabled(CppRuleClasses.FDO_INSTRUMENT)) {
      variablesBuilder.put(
          CompileBuildVariables.FDO_INSTRUMENT_PATH.getVariableName(), fdoInstrument);
    }

    // Optimization phase
    if (fdoProfile != null) {
      Iterable<Artifact> auxiliaryInputs = getAuxiliaryInputs(fdoSupportProvider);
      builder.addMandatoryInputs(auxiliaryInputs);
      if (!Iterables.isEmpty(auxiliaryInputs)) {
        if (featureConfiguration.isEnabled(CppRuleClasses.AUTOFDO)
            || featureConfiguration.isEnabled(CppRuleClasses.XBINARYFDO)) {
          variablesBuilder.put(
              CompileBuildVariables.FDO_PROFILE_PATH.getVariableName(),
              fdoSupportProvider.getProfileArtifact().getExecPathString());
        }
        if (featureConfiguration.isEnabled(CppRuleClasses.FDO_OPTIMIZE)) {
          if (fdoMode == FdoMode.LLVM_FDO) {
            variablesBuilder.put(
                CompileBuildVariables.FDO_PROFILE_PATH.getVariableName(),
                fdoSupportProvider.getProfileArtifact().getExecPathString());
          }
        }
      }
    }
    return variablesBuilder.build();
  }

  /** Returns the auxiliary files that need to be added to the {@link CppCompileAction}. */
  private Iterable<Artifact> getAuxiliaryInputs(FdoSupportProvider fdoSupportProvider) {
    ImmutableSet.Builder<Artifact> auxiliaryInputs = ImmutableSet.builder();

    if (fdoSupportProvider.getPrefetchHintsArtifact() != null) {
      auxiliaryInputs.add(fdoSupportProvider.getPrefetchHintsArtifact());
    }
    // If --fdo_optimize was not specified, we don't have any additional inputs.
    if (fdoProfile == null) {
      return auxiliaryInputs.build();
    } else if (fdoMode == FdoMode.LLVM_FDO
        || fdoMode == FdoMode.AUTO_FDO
        || fdoMode == FdoMode.XBINARY_FDO) {
      auxiliaryInputs.add(fdoSupportProvider.getProfileArtifact());
      return auxiliaryInputs.build();
    } else {
      return auxiliaryInputs.build();
    }
  }

  /**
   * Returns whether AutoFDO is enabled.
   */
  @ThreadSafe
  public boolean isAutoFdoEnabled() {
    return fdoMode == FdoMode.AUTO_FDO;
  }

  /** Returns whether crossbinary FDO is enabled. */
  @ThreadSafe
  public boolean isXBinaryFdoEnabled() {
    return fdoMode == FdoMode.XBINARY_FDO;
  }

  /**
   * Adds the FDO profile output path to the variable builder. If FDO is disabled, no build variable
   * is added.
   */
  @ThreadSafe
  public void getLinkOptions(
      FeatureConfiguration featureConfiguration, CcToolchainVariables.Builder buildVariables) {
    if (featureConfiguration.isEnabled(CppRuleClasses.FDO_INSTRUMENT)) {
      buildVariables.addStringVariable("fdo_instrument_path", fdoInstrument);
    }
  }

  /**
   * Adds the AutoFDO profile path to the variable builder and returns the profile artifact. If
   * AutoFDO is disabled, no build variable is added and returns null.
   */
  @ThreadSafe
  public ProfileArtifacts buildProfileForLtoBackend(
      FdoSupportProvider fdoSupportProvider,
      FeatureConfiguration featureConfiguration,
      CcToolchainVariables.Builder buildVariables) {
    Artifact prefetch = fdoSupportProvider.getPrefetchHintsArtifact();
    if (prefetch != null) {
      buildVariables.addStringVariable("fdo_prefetch_hints_path", prefetch.getExecPathString());
    }
    if (!featureConfiguration.isEnabled(CppRuleClasses.AUTOFDO)
        && !featureConfiguration.isEnabled(CppRuleClasses.XBINARYFDO)) {
      return new ProfileArtifacts(null, prefetch);
    }

    Artifact profile = fdoSupportProvider.getProfileArtifact();
    buildVariables.addStringVariable("fdo_profile_path", profile.getExecPathString());
    return new ProfileArtifacts(profile, prefetch);
  }
}
