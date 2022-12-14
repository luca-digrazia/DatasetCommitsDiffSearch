// Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.config.InvalidConfigurationException;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.NativeInfo;
import com.google.devtools.build.lib.packages.NativeProvider;
import com.google.devtools.build.lib.rules.cpp.CcToolchainFeatures.ActionConfig;
import com.google.devtools.build.lib.rules.cpp.CcToolchainFeatures.ArtifactNamePattern;
import com.google.devtools.build.lib.rules.cpp.CcToolchainFeatures.Feature;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CToolchain;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CompilationModeFlags;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CrosstoolRelease;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.LinkingModeFlags;

/** Information describing C++ toolchain derived from CROSSTOOL file. */
@Immutable
public class CrosstoolInfo extends NativeInfo {
  public static final NativeProvider<CrosstoolInfo> PROVIDER =
      new NativeProvider<CrosstoolInfo>(CrosstoolInfo.class, "CrosstoolInfo") {};

  private final ImmutableList<ActionConfig> actionConfigs;
  private final ImmutableList<Feature> features;
  private final ImmutableList<ArtifactNamePattern> artifactNamePatterns;
  private final ImmutableList<String> cxxBuiltinIncludeDirectories;

  private final String toolchainIdentifier;
  private final String hostSystemName;
  private final String targetSystemName;
  private final String targetCpu;
  private final String targetLibc;
  private final String compiler;
  private final String abiVersion;
  private final String abiLibcVersion;
  private final boolean supportsGoldLinker;
  private final boolean supportsStartEndLib;
  private final boolean supportsInterfaceSharedObjects;
  private final boolean supportsEmbeddedRuntimes;
  private final String staticRuntimesFilegroup;
  private final String dynamicRuntimesFilegroup;
  private final boolean supportsFission;
  private final boolean supportsDsym;
  private final boolean needsPic;
  private final ImmutableList<Pair<String, String>> toolPaths;
  private final ImmutableList<String> compilerFlags;
  private final ImmutableList<String> cxxFlags;
  private final ImmutableList<String> unfilteredCxxFlags;
  private final ImmutableList<String> linkerFlags;
  private final ImmutableList<String> dynamicLibraryLinkerFlags;
  private final ImmutableList<String> testOnlyLinkerFlags;
  private final ImmutableList<String> objcopyEmbedFlags;
  private final ImmutableList<String> ldEmbedFlags;
  private final ImmutableList<String> optCompilationModeCompilerFlags;
  private final ImmutableList<String> optCompilationModeCxxFlags;
  private final ImmutableList<String> optCompilationModeLinkerFlags;
  private final ImmutableList<String> dbgCompilationModeCompilerFlags;
  private final ImmutableList<String> dbgCompilationModeCxxFlags;
  private final ImmutableList<String> dbgCompilationModeLinkerFlags;
  private final ImmutableList<String> fastbuildCompilationModeCompilerFlags;
  private final ImmutableList<String> fastbuildCompilationModeCxxFlags;
  private final ImmutableList<String> fastbuildCompilationModeLinkerFlags;
  private final ImmutableList<String> mostlyStaticLinkingModeFlags;
  private final ImmutableList<String> dynamicLinkingModeFlags;
  private final ImmutableList<String> fullyStaticLinkingModeFlags;
  private final ImmutableList<String> mostlyStaticLibrariesLinkingModeFlags;
  private final ImmutableList<Pair<String, String>> makeVariables;
  private final String builtinSysroot;
  private final String defaultGrteTop;
  private final String ccTargetOs;
  private final boolean hasDynamicLinkingModeFlags;

  @AutoCodec.Instantiator
  protected CrosstoolInfo(
      ImmutableList<ActionConfig> actionConfigs,
      ImmutableList<Feature> features,
      ImmutableList<ArtifactNamePattern> artifactNamePatterns,
      ImmutableList<String> cxxBuiltinIncludeDirectories,
      String toolchainIdentifier,
      String hostSystemName,
      String targetSystemName,
      String targetCpu,
      String targetLibc,
      String compiler,
      String abiVersion,
      String abiLibcVersion,
      boolean supportsGoldLinker,
      boolean supportsStartEndLib,
      boolean supportsInterfaceSharedObjects,
      boolean supportsEmbeddedRuntimes,
      String staticRuntimesFilegroup,
      String dynamicRuntimesFilegroup,
      boolean supportsFission,
      boolean supportsDsym,
      boolean needsPic,
      ImmutableList<Pair<String, String>> toolPaths,
      ImmutableList<String> compilerFlags,
      ImmutableList<String> cxxFlags,
      ImmutableList<String> unfilteredCxxFlags,
      ImmutableList<String> linkerFlags,
      ImmutableList<String> dynamicLibraryLinkerFlags,
      ImmutableList<String> testOnlyLinkerFlags,
      ImmutableList<String> objcopyEmbedFlags,
      ImmutableList<String> ldEmbedFlags,
      ImmutableList<String> optCompilationModeCompilerFlags,
      ImmutableList<String> optCompilationModeCxxFlags,
      ImmutableList<String> optCompilationModeLinkerFlags,
      ImmutableList<String> dbgCompilationModeCompilerFlags,
      ImmutableList<String> dbgCompilationModeCxxFlags,
      ImmutableList<String> dbgCompilationModeLinkerFlags,
      ImmutableList<String> fastbuildCompilationModeCompilerFlags,
      ImmutableList<String> fastbuildCompilationModeCxxFlags,
      ImmutableList<String> fastbuildCompilationModeLinkerFlags,
      ImmutableList<String> mostlyStaticLinkingModeFlags,
      ImmutableList<String> dynamicLinkingModeFlags,
      ImmutableList<String> fullyStaticLinkingModeFlags,
      ImmutableList<String> mostlyStaticLibrariesLinkingModeFlags,
      ImmutableList<Pair<String, String>> makeVariables,
      String builtinSysroot,
      String defaultGrteTop,
      String ccTargetOs,
      boolean hasDynamicLinkingModeFlags) {
    super(PROVIDER);
    this.actionConfigs = actionConfigs;
    this.features = features;
    this.artifactNamePatterns = artifactNamePatterns;
    this.cxxBuiltinIncludeDirectories = cxxBuiltinIncludeDirectories;
    this.toolchainIdentifier = toolchainIdentifier;
    this.hostSystemName = hostSystemName;
    this.targetSystemName = targetSystemName;
    this.targetCpu = targetCpu;
    this.targetLibc = targetLibc;
    this.compiler = compiler;
    this.abiVersion = abiVersion;
    this.abiLibcVersion = abiLibcVersion;
    this.supportsGoldLinker = supportsGoldLinker;
    this.supportsStartEndLib = supportsStartEndLib;
    this.supportsInterfaceSharedObjects = supportsInterfaceSharedObjects;
    this.supportsEmbeddedRuntimes = supportsEmbeddedRuntimes;
    this.staticRuntimesFilegroup = staticRuntimesFilegroup;
    this.dynamicRuntimesFilegroup = dynamicRuntimesFilegroup;
    this.supportsFission = supportsFission;
    this.supportsDsym = supportsDsym;
    this.needsPic = needsPic;
    this.toolPaths = toolPaths;
    this.compilerFlags = compilerFlags;
    this.cxxFlags = cxxFlags;
    this.unfilteredCxxFlags = unfilteredCxxFlags;
    this.linkerFlags = linkerFlags;
    this.dynamicLibraryLinkerFlags = dynamicLibraryLinkerFlags;
    this.testOnlyLinkerFlags = testOnlyLinkerFlags;
    this.objcopyEmbedFlags = objcopyEmbedFlags;
    this.ldEmbedFlags = ldEmbedFlags;
    this.optCompilationModeCompilerFlags = optCompilationModeCompilerFlags;
    this.optCompilationModeCxxFlags = optCompilationModeCxxFlags;
    this.optCompilationModeLinkerFlags = optCompilationModeLinkerFlags;
    this.dbgCompilationModeCompilerFlags = dbgCompilationModeCompilerFlags;
    this.dbgCompilationModeCxxFlags = dbgCompilationModeCxxFlags;
    this.dbgCompilationModeLinkerFlags = dbgCompilationModeLinkerFlags;
    this.fastbuildCompilationModeCompilerFlags = fastbuildCompilationModeCompilerFlags;
    this.fastbuildCompilationModeCxxFlags = fastbuildCompilationModeCxxFlags;
    this.fastbuildCompilationModeLinkerFlags = fastbuildCompilationModeLinkerFlags;
    this.mostlyStaticLinkingModeFlags = mostlyStaticLinkingModeFlags;
    this.dynamicLinkingModeFlags = dynamicLinkingModeFlags;
    this.fullyStaticLinkingModeFlags = fullyStaticLinkingModeFlags;
    this.mostlyStaticLibrariesLinkingModeFlags = mostlyStaticLibrariesLinkingModeFlags;
    this.makeVariables = makeVariables;
    this.builtinSysroot = builtinSysroot;
    this.defaultGrteTop = defaultGrteTop;
    this.ccTargetOs = ccTargetOs;
    this.hasDynamicLinkingModeFlags = hasDynamicLinkingModeFlags;
  }

  public static CrosstoolInfo fromToolchain(
      CrosstoolRelease file, CToolchain toolchain, PathFragment crosstoolTop)
      throws InvalidConfigurationException {

    ImmutableList.Builder<ActionConfig> actionConfigBuilder = ImmutableList.builder();
    for (CToolchain.ActionConfig actionConfig : toolchain.getActionConfigList()) {
      actionConfigBuilder.add(new ActionConfig(actionConfig, crosstoolTop));
    }

    ImmutableList.Builder<Feature> featureBuilder = ImmutableList.builder();
    for (CToolchain.Feature feature : toolchain.getFeatureList()) {
      featureBuilder.add(new Feature(feature));
    }

    ImmutableList.Builder<ArtifactNamePattern> artifactNamePatternBuilder = ImmutableList.builder();
    for (CToolchain.ArtifactNamePattern artifactNamePattern :
        toolchain.getArtifactNamePatternList()) {
      artifactNamePatternBuilder.add(new ArtifactNamePattern(artifactNamePattern));
    }

    ImmutableList.Builder<String> optCompilationModeCompilerFlags = ImmutableList.builder();
    ImmutableList.Builder<String> optCompilationModeCxxFlags = ImmutableList.builder();
    ImmutableList.Builder<String> optCompilationModeLinkerFlags = ImmutableList.builder();
    ImmutableList.Builder<String> dbgCompilationModeCompilerFlags = ImmutableList.builder();
    ImmutableList.Builder<String> dbgCompilationModeCxxFlags = ImmutableList.builder();
    ImmutableList.Builder<String> dbgCompilationModeLinkerFlags = ImmutableList.builder();
    ImmutableList.Builder<String> fastbuildCompilationModeCompilerFlags = ImmutableList.builder();
    ImmutableList.Builder<String> fastbuildCompilationModeCxxFlags = ImmutableList.builder();
    ImmutableList.Builder<String> fastbuildCompilationModeLinkerFlags = ImmutableList.builder();

    ImmutableList.Builder<String> fullyStaticLinkerFlags = ImmutableList.builder();
    ImmutableList.Builder<String> mostlyStaticLinkerFlags = ImmutableList.builder();
    ImmutableList.Builder<String> dynamicLinkerFlags = ImmutableList.builder();
    ImmutableList.Builder<String> mostlyStaticLibrariesLinkerFlags = ImmutableList.builder();

    for (CompilationModeFlags flag : toolchain.getCompilationModeFlagsList()) {
      switch (flag.getMode()) {
        case OPT:
          optCompilationModeCompilerFlags.addAll(flag.getCompilerFlagList());
          optCompilationModeCxxFlags.addAll(flag.getCxxFlagList());
          optCompilationModeLinkerFlags.addAll(flag.getLinkerFlagList());
          break;
        case DBG:
          dbgCompilationModeCompilerFlags.addAll(flag.getCompilerFlagList());
          dbgCompilationModeCxxFlags.addAll(flag.getCxxFlagList());
          dbgCompilationModeLinkerFlags.addAll(flag.getLinkerFlagList());
          break;
        case FASTBUILD:
          fastbuildCompilationModeCompilerFlags.addAll(flag.getCompilerFlagList());
          fastbuildCompilationModeCxxFlags.addAll(flag.getCxxFlagList());
          fastbuildCompilationModeLinkerFlags.addAll(flag.getLinkerFlagList());
          break;
        default:
          // CompilationMode.COVERAGE is ignored
      }
    }

    boolean hasDynamicLinkingModeFlags = false;
    for (LinkingModeFlags flag : toolchain.getLinkingModeFlagsList()) {
      switch (flag.getMode()) {
        case FULLY_STATIC:
          fullyStaticLinkerFlags.addAll(flag.getLinkerFlagList());
          break;
        case MOSTLY_STATIC:
          mostlyStaticLinkerFlags.addAll(flag.getLinkerFlagList());
          break;
        case DYNAMIC:
          hasDynamicLinkingModeFlags = true;
          dynamicLinkerFlags.addAll(flag.getLinkerFlagList());
          break;
        case MOSTLY_STATIC_LIBRARIES:
          mostlyStaticLibrariesLinkerFlags.addAll(flag.getLinkerFlagList());
          break;
      }
    }

    return new CrosstoolInfo(
        actionConfigBuilder.build(),
        featureBuilder.build(),
        artifactNamePatternBuilder.build(),
        ImmutableList.copyOf(toolchain.getCxxBuiltinIncludeDirectoryList()),
        toolchain.getToolchainIdentifier(),
        toolchain.getHostSystemName(),
        toolchain.getTargetSystemName(),
        toolchain.getTargetCpu(),
        toolchain.getTargetLibc(),
        toolchain.getCompiler(),
        toolchain.getAbiVersion(),
        toolchain.getAbiLibcVersion(),
        toolchain.getSupportsGoldLinker(),
        toolchain.getSupportsStartEndLib(),
        toolchain.getSupportsInterfaceSharedObjects(),
        toolchain.getSupportsEmbeddedRuntimes(),
        toolchain.getStaticRuntimesFilegroup(),
        toolchain.getDynamicRuntimesFilegroup(),
        toolchain.getSupportsFission(),
        toolchain.getSupportsDsym(),
        toolchain.getNeedsPic(),
        toolchain
            .getToolPathList()
            .stream()
            .map(a -> Pair.of(a.getName(), a.getPath()))
            .collect(ImmutableList.toImmutableList()),
        ImmutableList.copyOf(toolchain.getCompilerFlagList()),
        ImmutableList.copyOf(toolchain.getCxxFlagList()),
        ImmutableList.copyOf(toolchain.getUnfilteredCxxFlagList()),
        ImmutableList.copyOf(toolchain.getLinkerFlagList()),
        ImmutableList.copyOf(toolchain.getDynamicLibraryLinkerFlagList()),
        ImmutableList.copyOf(toolchain.getTestOnlyLinkerFlagList()),
        ImmutableList.copyOf(toolchain.getObjcopyEmbedFlagList()),
        ImmutableList.copyOf(toolchain.getLdEmbedFlagList()),
        optCompilationModeCompilerFlags.build(),
        optCompilationModeCxxFlags.build(),
        optCompilationModeLinkerFlags.build(),
        dbgCompilationModeCompilerFlags.build(),
        dbgCompilationModeCxxFlags.build(),
        dbgCompilationModeLinkerFlags.build(),
        fastbuildCompilationModeCompilerFlags.build(),
        fastbuildCompilationModeCxxFlags.build(),
        fastbuildCompilationModeLinkerFlags.build(),
        mostlyStaticLinkerFlags.build(),
        dynamicLinkerFlags.build(),
        fullyStaticLinkerFlags.build(),
        mostlyStaticLibrariesLinkerFlags.build(),
        toolchain
            .getMakeVariableList()
            .stream()
            .map(makeVariable -> Pair.of(makeVariable.getName(), makeVariable.getValue()))
            .collect(ImmutableList.toImmutableList()),
        toolchain.getBuiltinSysroot(),
        toolchain.getDefaultGrteTop(),
        toolchain.getCcTargetOs(),
        hasDynamicLinkingModeFlags);
  }

  public ImmutableList<ActionConfig> getActionConfigs() {
    return actionConfigs;
  }

  public ImmutableList<Feature> getFeatures() {
    return features;
  }

  public ImmutableList<ArtifactNamePattern> getArtifactNamePatterns() {
    return artifactNamePatterns;
  }

  public ImmutableList<String> getCxxBuiltinIncludeDirectories() {
    return cxxBuiltinIncludeDirectories;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getToolchainIdentifier() {
    return toolchainIdentifier;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getHostSystemName() {
    return hostSystemName;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getTargetSystemName() {
    return targetSystemName;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getTargetCpu() {
    return targetCpu;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getTargetLibc() {
    return targetLibc;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getCompiler() {
    return compiler;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getAbiVersion() {
    return abiVersion;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getAbiLibcVersion() {
    return abiLibcVersion;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public boolean supportsGoldLinker() {
    return supportsGoldLinker;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public boolean supportsStartEndLib() {
    return supportsStartEndLib;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public boolean supportsInterfaceSharedObjects() {
    return supportsInterfaceSharedObjects;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public boolean supportsEmbeddedRuntimes() {
    return supportsEmbeddedRuntimes;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getStaticRuntimesFilegroup() {
    return staticRuntimesFilegroup;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getDynamicRuntimesFilegroup() {
    return dynamicRuntimesFilegroup;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public boolean supportsFission() {
    return supportsFission;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public boolean supportsDsym() {
    return supportsDsym;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public boolean needsPic() {
    return needsPic;
  }

  /** Returns a list of paths of the tools in the form Pair<toolName, path>. */
  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<Pair<String, String>> getToolPaths() {
    return toolPaths;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getCompilerFlags() {
    return compilerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getCxxFlags() {
    return cxxFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getUnfilteredCxxFlags() {
    return unfilteredCxxFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getLinkerFlags() {
    return linkerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getDynamicLibraryLinkerFlags() {
    return dynamicLibraryLinkerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getTestOnlyLinkerFlags() {
    return testOnlyLinkerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getObjcopyEmbedFlags() {
    return objcopyEmbedFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getLdEmbedFlags() {
    return ldEmbedFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getMostlyStaticLinkingModeFlags() {
    return mostlyStaticLinkingModeFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getDynamicLinkingModeFlags() {
    return dynamicLinkingModeFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getFullyStaticLinkingModeFlags() {
    return fullyStaticLinkingModeFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getMostlyStaticLibrariesLinkingModeFlags() {
    return mostlyStaticLibrariesLinkingModeFlags;
  }

  /** Returns a list of make variables that have the form Pair<name, value>. */
  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<Pair<String, String>> getMakeVariables() {
    return makeVariables;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getBuiltinSysroot() {
    return builtinSysroot;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getDefaultGrteTop() {
    return defaultGrteTop;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getOptCompilationModeCompilerFlags() {
    return optCompilationModeCompilerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getOptCompilationModeCxxFlags() {
    return optCompilationModeCxxFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getOptCompilationModeLinkerFlags() {
    return optCompilationModeLinkerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getDbgCompilationModeCompilerFlags() {
    return dbgCompilationModeCompilerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getDbgCompilationModeCxxFlags() {
    return dbgCompilationModeCxxFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getDbgCompilationModeLinkerFlags() {
    return dbgCompilationModeLinkerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getFastbuildCompilationModeCompilerFlags() {
    return fastbuildCompilationModeCompilerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getFastbuildCompilationModeCxxFlags() {
    return fastbuildCompilationModeCxxFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public ImmutableList<String> getFastbuildCompilationModeLinkerFlags() {
    return fastbuildCompilationModeLinkerFlags;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public String getCcTargetOs() {
    return ccTargetOs;
  }

  // TODO(b/65151735): Remove once this field is migrated to features.
  @Deprecated
  public boolean hasDynamicLinkingModeFlags() {
    return hasDynamicLinkingModeFlags;
  }
}
