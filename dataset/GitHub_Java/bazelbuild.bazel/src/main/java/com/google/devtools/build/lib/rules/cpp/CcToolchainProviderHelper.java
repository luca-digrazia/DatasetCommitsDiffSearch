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

import static com.google.devtools.build.lib.syntax.Type.BOOLEAN;
import static com.google.devtools.build.lib.syntax.Type.STRING;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Actions;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.CompilationHelper;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.MiddlemanProvider;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.actions.CustomCommandLine;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.analysis.actions.SymlinkAction;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.CompilationMode;
import com.google.devtools.build.lib.analysis.config.InvalidConfigurationException;
import com.google.devtools.build.lib.analysis.configuredtargets.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.LabelSyntaxException;
import com.google.devtools.build.lib.cmdline.PackageIdentifier;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.packages.RuleClass.ConfiguredTargetFactory.RuleErrorException;
import com.google.devtools.build.lib.rules.cpp.CcSkyframeSupportFunction.CcSkyframeSupportException;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration.Tool;
import com.google.devtools.build.lib.rules.cpp.FdoProvider.FdoMode;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.util.FileType;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.util.StringUtil;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CToolchain;
import com.google.devtools.build.lib.view.config.crosstool.CrosstoolConfig.CrosstoolRelease;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Helper responsible for creating CcToolchainProvider */
public class CcToolchainProviderHelper {

  /**
   * This file (found under the sysroot) may be unconditionally included in every C/C++ compilation.
   */
  static final PathFragment BUILTIN_INCLUDE_FILE_SUFFIX =
      PathFragment.create("include/stdc-predef.h");

  private static final String SYSROOT_START = "%sysroot%/";
  private static final String WORKSPACE_START = "%workspace%/";
  private static final String CROSSTOOL_START = "%crosstool_top%/";
  private static final String PACKAGE_START = "%package(";
  private static final String PACKAGE_END = ")%";

  /**
   * Returns the profile name with the same file name as fdoProfile and an extension that matches
   * {@link FileType}.
   */
  private static String getLLVMProfileFileName(PathFragment fdoProfile, FileType type) {
    if (type.matches(fdoProfile)) {
      return fdoProfile.getBaseName();
    } else {
      return FileSystemUtils.removeExtension(fdoProfile.getBaseName())
          + type.getExtensions().get(0);
    }
  }

  /**
   * Resolve the given include directory.
   *
   * <p>If it starts with %sysroot%/, that part is replaced with the actual sysroot.
   *
   * <p>If it starts with %workspace%/, that part is replaced with the empty string (essentially
   * making it relative to the build directory).
   *
   * <p>If it starts with %crosstool_top%/ or is any relative path, it is interpreted relative to
   * the crosstool top. The use of assumed-crosstool-relative specifications is considered
   * deprecated, and all such uses should eventually be replaced by "%crosstool_top%/".
   *
   * <p>If it is of the form %package(@repository//my/package)%/folder, then it is interpreted as
   * the named folder in the appropriate package. All of the normal package syntax is supported. The
   * /folder part is optional.
   *
   * <p>It is illegal if it starts with a % and does not match any of the above forms to avoid
   * accidentally silently ignoring misspelled prefixes.
   *
   * <p>If it is absolute, it remains unchanged.
   */
  static PathFragment resolveIncludeDir(
      String s, PathFragment sysroot, PathFragment crosstoolTopPathFragment)
      throws InvalidConfigurationException {
    PathFragment pathPrefix;
    String pathString;
    int packageEndIndex = s.indexOf(PACKAGE_END);
    if (packageEndIndex != -1 && s.startsWith(PACKAGE_START)) {
      String packageString = s.substring(PACKAGE_START.length(), packageEndIndex);
      try {
        pathPrefix = PackageIdentifier.parse(packageString).getSourceRoot();
      } catch (LabelSyntaxException e) {
        throw new InvalidConfigurationException("The package '" + packageString + "' is not valid");
      }
      int pathStartIndex = packageEndIndex + PACKAGE_END.length();
      if (pathStartIndex + 1 < s.length()) {
        if (s.charAt(pathStartIndex) != '/') {
          throw new InvalidConfigurationException(
              "The path in the package for '" + s + "' is not valid");
        }
        pathString = s.substring(pathStartIndex + 1, s.length());
      } else {
        pathString = "";
      }
    } else if (s.startsWith(SYSROOT_START)) {
      if (sysroot == null) {
        throw new InvalidConfigurationException(
            "A %sysroot% prefix is only allowed if the " + "default_sysroot option is set");
      }
      pathPrefix = sysroot;
      pathString = s.substring(SYSROOT_START.length(), s.length());
    } else if (s.startsWith(WORKSPACE_START)) {
      pathPrefix = PathFragment.EMPTY_FRAGMENT;
      pathString = s.substring(WORKSPACE_START.length(), s.length());
    } else {
      pathPrefix = crosstoolTopPathFragment;
      if (s.startsWith(CROSSTOOL_START)) {
        pathString = s.substring(CROSSTOOL_START.length(), s.length());
      } else if (s.startsWith("%")) {
        throw new InvalidConfigurationException(
            "The include path '" + s + "' has an " + "unrecognized %prefix%");
      } else {
        pathString = s;
      }
    }

    if (!PathFragment.isNormalized(pathString)) {
      throw new InvalidConfigurationException("The include path '" + s + "' is not normalized.");
    }
    PathFragment path = PathFragment.create(pathString);
    return pathPrefix.getRelative(path);
  }

  private static String getSkylarkValueForTool(Tool tool, CppToolchainInfo cppToolchainInfo) {
    PathFragment toolPath = cppToolchainInfo.getToolPathFragment(tool);
    return toolPath != null ? toolPath.getPathString() : "";
  }

  private static ImmutableMap<String, Object> getToolchainForSkylark(
      CppToolchainInfo cppToolchainInfo) {
    return ImmutableMap.<String, Object>builder()
        .put("objcopy_executable", getSkylarkValueForTool(Tool.OBJCOPY, cppToolchainInfo))
        .put("compiler_executable", getSkylarkValueForTool(Tool.GCC, cppToolchainInfo))
        .put("preprocessor_executable", getSkylarkValueForTool(Tool.CPP, cppToolchainInfo))
        .put("nm_executable", getSkylarkValueForTool(Tool.NM, cppToolchainInfo))
        .put("objdump_executable", getSkylarkValueForTool(Tool.OBJDUMP, cppToolchainInfo))
        .put("ar_executable", getSkylarkValueForTool(Tool.AR, cppToolchainInfo))
        .put("strip_executable", getSkylarkValueForTool(Tool.STRIP, cppToolchainInfo))
        .put("ld_executable", getSkylarkValueForTool(Tool.LD, cppToolchainInfo))
        .build();
  }

  private static PathFragment calculateSysroot(
      RuleContext ruleContext, PathFragment defaultSysroot) {
    TransitiveInfoCollection sysrootTarget =
        ruleContext.getPrerequisite(CcToolchainRule.LIBC_TOP_ATTR, Mode.TARGET);
    if (sysrootTarget == null) {
      return defaultSysroot;
    }

    return sysrootTarget.getLabel().getPackageFragment();
  }

  private static Artifact getPrefetchHintsArtifact(
      FdoInputFile prefetchHintsFile, RuleContext ruleContext) {
    if (prefetchHintsFile == null) {
      return null;
    }
    Artifact prefetchHintsArtifact = prefetchHintsFile.getArtifact();
    if (prefetchHintsArtifact != null) {
      return prefetchHintsArtifact;
    }

    prefetchHintsArtifact =
        ruleContext.getUniqueDirectoryArtifact(
            "fdo",
            prefetchHintsFile.getAbsolutePath().getBaseName(),
            ruleContext.getBinOrGenfilesDirectory());
    ruleContext.registerAction(
        new SymlinkAction(
            ruleContext.getActionOwner(),
            PathFragment.create(prefetchHintsFile.getAbsolutePath().getPathString()),
            prefetchHintsArtifact,
            "Symlinking LLVM Cache Prefetch Hints Profile "
                + prefetchHintsFile.getAbsolutePath().getPathString()));
    return prefetchHintsArtifact;
  }

  /*
   * This function checks the format of the input profile data and converts it to
   * the indexed format (.profdata) if necessary.
   */
  private static Artifact convertLLVMRawProfileToIndexed(
      PathFragment fdoProfile, CppToolchainInfo toolchainInfo, RuleContext ruleContext) {

    Artifact profileArtifact =
        ruleContext.getUniqueDirectoryArtifact(
            "fdo",
            getLLVMProfileFileName(fdoProfile, CppFileTypes.LLVM_PROFILE),
            ruleContext.getBinOrGenfilesDirectory());

    // If the profile file is already in the desired format, symlink to it and return.
    if (CppFileTypes.LLVM_PROFILE.matches(fdoProfile)) {
      ruleContext.registerAction(
          new SymlinkAction(
              ruleContext.getActionOwner(),
              fdoProfile,
              profileArtifact,
              "Symlinking LLVM Profile " + fdoProfile.getPathString()));
      return profileArtifact;
    }

    Artifact rawProfileArtifact;

    if (CppFileTypes.LLVM_PROFILE_ZIP.matches(fdoProfile)) {
      // Get the zipper binary for unzipping the profile.
      Artifact zipperBinaryArtifact = ruleContext.getPrerequisiteArtifact(":zipper", Mode.HOST);
      if (zipperBinaryArtifact == null) {
        ruleContext.ruleError("Cannot find zipper binary to unzip the profile");
        return null;
      }

      // TODO(zhayu): find a way to avoid hard-coding cpu architecture here (b/65582760)
      String rawProfileFileName = "fdocontrolz_profile.profraw";
      String cpu = toolchainInfo.getTargetCpu();
      if (!"k8".equals(cpu)) {
        rawProfileFileName = "fdocontrolz_profile-" + cpu + ".profraw";
      }
      rawProfileArtifact =
          ruleContext.getUniqueDirectoryArtifact(
              "fdo", rawProfileFileName, ruleContext.getBinOrGenfilesDirectory());

      // Symlink to the zipped profile file to extract the contents.
      Artifact zipProfileArtifact =
          ruleContext.getUniqueDirectoryArtifact(
              "fdo", fdoProfile.getBaseName(), ruleContext.getBinOrGenfilesDirectory());
      ruleContext.registerAction(
          new SymlinkAction(
              ruleContext.getActionOwner(),
              PathFragment.create(fdoProfile.getPathString()),
              zipProfileArtifact,
              "Symlinking LLVM ZIP Profile " + fdoProfile.getPathString()));

      // Unzip the profile.
      ruleContext.registerAction(
          new SpawnAction.Builder()
              .addInput(zipProfileArtifact)
              .addInput(zipperBinaryArtifact)
              .addOutput(rawProfileArtifact)
              .useDefaultShellEnvironment()
              .setExecutable(zipperBinaryArtifact)
              .setProgressMessage(
                  "LLVMUnzipProfileAction: Generating %s", rawProfileArtifact.prettyPrint())
              .setMnemonic("LLVMUnzipProfileAction")
              .addCommandLine(
                  CustomCommandLine.builder()
                      .addExecPath("xf", zipProfileArtifact)
                      .add(
                          "-d",
                          rawProfileArtifact.getExecPath().getParentDirectory().getSafePathString())
                      .build())
              .build(ruleContext));
    } else {
      rawProfileArtifact =
          ruleContext.getUniqueDirectoryArtifact(
              "fdo",
              getLLVMProfileFileName(fdoProfile, CppFileTypes.LLVM_PROFILE_RAW),
              ruleContext.getBinOrGenfilesDirectory());
      ruleContext.registerAction(
          new SymlinkAction(
              ruleContext.getActionOwner(),
              PathFragment.create(fdoProfile.getPathString()),
              rawProfileArtifact,
              "Symlinking LLVM Raw Profile " + fdoProfile.getPathString()));
    }

    if (toolchainInfo.getToolPathFragment(Tool.LLVM_PROFDATA) == null) {
      ruleContext.ruleError(
          "llvm-profdata not available with this crosstool, needed for profile conversion");
      return null;
    }

    // Convert LLVM raw profile to indexed format.
    ruleContext.registerAction(
        new SpawnAction.Builder()
            .addInput(rawProfileArtifact)
            .addTransitiveInputs(getMiddlemanOrFiles(ruleContext, "all_files"))
            .addOutput(profileArtifact)
            .useDefaultShellEnvironment()
            .setExecutable(toolchainInfo.getToolPathFragment(Tool.LLVM_PROFDATA))
            .setProgressMessage("LLVMProfDataAction: Generating %s", profileArtifact.prettyPrint())
            .setMnemonic("LLVMProfDataAction")
            .addCommandLine(
                CustomCommandLine.builder()
                    .add("merge")
                    .add("-o")
                    .addExecPath(profileArtifact)
                    .addExecPath(rawProfileArtifact)
                    .build())
            .build(ruleContext));

    return profileArtifact;
  }

  static CcToolchainProvider getCcToolchainProvider(
      RuleContext ruleContext,
      boolean isAppleToolchain,
      CcToolchainVariables additionalBuildVariables)
      throws RuleErrorException, InterruptedException {
    BuildConfiguration configuration = Preconditions.checkNotNull(ruleContext.getConfiguration());
    CppConfiguration cppConfiguration =
        Preconditions.checkNotNull(configuration.getFragment(CppConfiguration.class));

    PathFragment fdoZip = null;
    FdoInputFile prefetchHints = null;
    Artifact protoProfileArtifact = null;
    boolean allowInference = true;
    if (configuration.getCompilationMode() == CompilationMode.OPT) {
      if (cppConfiguration.getFdoPrefetchHintsLabel() != null) {
        FdoPrefetchHintsProvider provider =
            ruleContext.getPrerequisite(
                ":fdo_prefetch_hints", Mode.TARGET, FdoPrefetchHintsProvider.PROVIDER);
        prefetchHints = provider.getInputFile();
      }
      if (cppConfiguration.getFdoPath() != null) {
        fdoZip = cppConfiguration.getFdoPath();
      } else if (cppConfiguration.getFdoOptimizeLabel() != null) {
        // If fdo_profile rule is used, do not allow inferring proto.profile from AFDO profile.
        allowInference = false;

        FdoProfileProvider fdoProfileProvider =
            ruleContext.getPrerequisite(
                CcToolchainRule.FDO_OPTIMIZE_ATTR, Mode.TARGET, FdoProfileProvider.PROVIDER);
        if (fdoProfileProvider != null) {
          fdoZip = fdoProfileProvider.getProfilePathFragment();
          protoProfileArtifact = fdoProfileProvider.getProtoProfileArtifact();
        } else {
          ImmutableList<Artifact> fdoArtifacts =
              ruleContext
                  .getPrerequisiteArtifacts(CcToolchainRule.FDO_OPTIMIZE_ATTR, Mode.TARGET)
                  .list();
          if (fdoArtifacts.size() != 1) {
            ruleContext.ruleError("--fdo_optimize does not point to a single target");
            return null;
          }

          Artifact fdoArtifact = fdoArtifacts.get(0);
          if (!fdoArtifact.isSourceArtifact()) {
            ruleContext.ruleError("--fdo_optimize points to a target that is not an input file");
            return null;
          }
          Label fdoLabel =
              ruleContext
                  .getPrerequisite(CcToolchainRule.FDO_OPTIMIZE_ATTR, Mode.TARGET)
                  .getLabel();
          if (!fdoLabel
              .getPackageIdentifier()
              .getPathUnderExecRoot()
              .getRelative(fdoLabel.getName())
              .equals(fdoArtifact.getExecPath())) {
            ruleContext.ruleError("--fdo_optimize points to a target that is not an input file");
            return null;
          }
          fdoZip = fdoArtifact.getPath().asFragment();
        }
      } else if (cppConfiguration.getFdoProfileLabel() != null) {
        FdoProfileProvider fdoProvider =
            ruleContext.getPrerequisite(
                CcToolchainRule.FDO_PROFILE_ATTR, Mode.TARGET, FdoProfileProvider.PROVIDER);
        fdoZip = fdoProvider.getProfilePathFragment();
        protoProfileArtifact = fdoProvider.getProtoProfileArtifact();
      }
    }

    FdoMode fdoMode;
    if (fdoZip == null) {
      fdoMode = FdoMode.OFF;
    } else if (CppFileTypes.GCC_AUTO_PROFILE.matches(fdoZip)) {
      fdoMode = FdoMode.AUTO_FDO;
    } else if (CppFileTypes.XBINARY_PROFILE.matches(fdoZip)) {
      fdoMode = FdoMode.XBINARY_FDO;
    } else if (CppFileTypes.LLVM_PROFILE.matches(fdoZip)) {
      fdoMode = FdoMode.LLVM_FDO;
    } else if (CppFileTypes.LLVM_PROFILE_RAW.matches(fdoZip)) {
      fdoMode = FdoMode.LLVM_FDO;
    } else if (CppFileTypes.LLVM_PROFILE_ZIP.matches(fdoZip)) {
      fdoMode = FdoMode.LLVM_FDO;
    } else {
      ruleContext.ruleError("invalid extension for FDO profile file.");
      return null;
    }

    // Is there a toolchain proto available on the target directly?
    CToolchain toolchain = parseToolchainFromAttributes(ruleContext);
    Label ccToolchainSuiteLabelIfNeeded = null;
    if (toolchain == null && cppConfiguration.getCrosstoolFromCcToolchainProtoAttribute() == null) {
      ccToolchainSuiteLabelIfNeeded = cppConfiguration.getCrosstoolTop();
    }

    SkyKey ccSupportKey = CcSkyframeSupportValue.key(fdoZip, ccToolchainSuiteLabelIfNeeded);

    SkyFunction.Environment skyframeEnv = ruleContext.getAnalysisEnvironment().getSkyframeEnv();
    CcSkyframeSupportValue ccSkyframeSupportValue;
    try {
      ccSkyframeSupportValue =
          (CcSkyframeSupportValue)
              skyframeEnv.getValueOrThrow(ccSupportKey, CcSkyframeSupportException.class);
    } catch (CcSkyframeSupportException e) {
      ruleContext.throwWithRuleError(e.getMessage());
      throw new IllegalStateException("Should not be reached");
    }
    if (skyframeEnv.valuesMissing()) {
      return null;
    }

    CppToolchainInfo toolchainInfo =
        getCppToolchainInfo(ruleContext, cppConfiguration, ccSkyframeSupportValue, toolchain);

    final Label label = ruleContext.getLabel();
    final NestedSet<Artifact> crosstool =
        ruleContext
            .getPrerequisite("all_files", Mode.HOST)
            .getProvider(FileProvider.class)
            .getFilesToBuild();
    final NestedSet<Artifact> crosstoolMiddleman = getMiddlemanOrFiles(ruleContext, "all_files");
    final NestedSet<Artifact> compile = getMiddlemanOrFiles(ruleContext, "compiler_files");
    final NestedSet<Artifact> compileWithoutIncludes =
        getOptionalMiddlemanOrFiles(ruleContext, "compiler_files_without_includes");
    final NestedSet<Artifact> strip = getMiddlemanOrFiles(ruleContext, "strip_files");
    final NestedSet<Artifact> objcopy = getMiddlemanOrFiles(ruleContext, "objcopy_files");
    final NestedSet<Artifact> as = getOptionalMiddlemanOrFiles(ruleContext, "as_files");
    final NestedSet<Artifact> ar = getOptionalMiddlemanOrFiles(ruleContext, "ar_files");
    final NestedSet<Artifact> link = getMiddlemanOrFiles(ruleContext, "linker_files");
    final NestedSet<Artifact> dwp = getMiddlemanOrFiles(ruleContext, "dwp_files");
    final NestedSet<Artifact> libcMiddleman =
        getOptionalMiddlemanOrFiles(ruleContext, CcToolchainRule.LIBC_TOP_ATTR, Mode.TARGET);
    final NestedSet<Artifact> libc =
        getOptionalFiles(ruleContext, CcToolchainRule.LIBC_TOP_ATTR, Mode.TARGET);
    String purposePrefix = Actions.escapeLabel(label) + "_";
    String runtimeSolibDirBase = "_solib_" + "_" + Actions.escapeLabel(label);
    final PathFragment runtimeSolibDir =
        configuration.getBinFragment().getRelative(runtimeSolibDirBase);

    // Static runtime inputs.
    TransitiveInfoCollection staticRuntimeLibDep =
        selectDep(ruleContext, "static_runtime_libs", toolchainInfo.getStaticRuntimeLibsLabel());
    final NestedSet<Artifact> staticRuntimeLinkInputs;
    final Artifact staticRuntimeLinkMiddleman;
    if (toolchainInfo.supportsEmbeddedRuntimes()) {
      staticRuntimeLinkInputs =
          staticRuntimeLibDep.getProvider(FileProvider.class).getFilesToBuild();
    } else {
      staticRuntimeLinkInputs = NestedSetBuilder.emptySet(Order.STABLE_ORDER);
    }

    if (!staticRuntimeLinkInputs.isEmpty()) {
      NestedSet<Artifact> staticRuntimeLinkMiddlemanSet =
          CompilationHelper.getAggregatingMiddleman(
              ruleContext, purposePrefix + "static_runtime_link", staticRuntimeLibDep);
      staticRuntimeLinkMiddleman =
          staticRuntimeLinkMiddlemanSet.isEmpty()
              ? null
              : Iterables.getOnlyElement(staticRuntimeLinkMiddlemanSet);
    } else {
      staticRuntimeLinkMiddleman = null;
    }

    Preconditions.checkState(
        (staticRuntimeLinkMiddleman == null) == staticRuntimeLinkInputs.isEmpty());

    // Dynamic runtime inputs.
    TransitiveInfoCollection dynamicRuntimeLibDep =
        selectDep(ruleContext, "dynamic_runtime_libs", toolchainInfo.getDynamicRuntimeLibsLabel());
    NestedSet<Artifact> dynamicRuntimeLinkSymlinks;
    List<Artifact> dynamicRuntimeLinkInputs = new ArrayList<>();
    Artifact dynamicRuntimeLinkMiddleman;
    if (toolchainInfo.supportsEmbeddedRuntimes()) {
      NestedSetBuilder<Artifact> dynamicRuntimeLinkSymlinksBuilder = NestedSetBuilder.stableOrder();
      for (Artifact artifact :
          dynamicRuntimeLibDep.getProvider(FileProvider.class).getFilesToBuild()) {
        if (CppHelper.SHARED_LIBRARY_FILETYPES.matches(artifact.getFilename())) {
          dynamicRuntimeLinkInputs.add(artifact);
          dynamicRuntimeLinkSymlinksBuilder.add(
              SolibSymlinkAction.getCppRuntimeSymlink(
                  ruleContext,
                  artifact,
                  toolchainInfo.getSolibDirectory(),
                  runtimeSolibDirBase,
                  configuration));
        }
      }
      dynamicRuntimeLinkSymlinks = dynamicRuntimeLinkSymlinksBuilder.build();
    } else {
      dynamicRuntimeLinkSymlinks = NestedSetBuilder.emptySet(Order.STABLE_ORDER);
    }

    if (!dynamicRuntimeLinkInputs.isEmpty()) {
      List<Artifact> dynamicRuntimeLinkMiddlemanSet =
          CppHelper.getAggregatingMiddlemanForCppRuntimes(
              ruleContext,
              purposePrefix + "dynamic_runtime_link",
              dynamicRuntimeLinkInputs,
              toolchainInfo.getSolibDirectory(),
              runtimeSolibDirBase,
              configuration);
      dynamicRuntimeLinkMiddleman =
          dynamicRuntimeLinkMiddlemanSet.isEmpty()
              ? null
              : Iterables.getOnlyElement(dynamicRuntimeLinkMiddlemanSet);
    } else {
      dynamicRuntimeLinkMiddleman = null;
    }

    Preconditions.checkState(
        (dynamicRuntimeLinkMiddleman == null) == dynamicRuntimeLinkSymlinks.isEmpty());

    CcCompilationContext.Builder ccCompilationContextBuilder =
        new CcCompilationContext.Builder(ruleContext);
    CppModuleMap moduleMap = createCrosstoolModuleMap(ruleContext);
    if (moduleMap != null) {
      ccCompilationContextBuilder.setCppModuleMap(moduleMap);
    }
    final CcCompilationContext ccCompilationContext = ccCompilationContextBuilder.build();
    boolean supportsParamFiles = ruleContext.attributes().get("supports_param_files", BOOLEAN);
    boolean supportsHeaderParsing =
        ruleContext.attributes().get("supports_header_parsing", BOOLEAN);

    NestedSetBuilder<Pair<String, String>> coverageEnvironment = NestedSetBuilder.compileOrder();

    NestedSet<Artifact> coverage = getOptionalMiddlemanOrFiles(ruleContext, "coverage_files");
    if (coverage.isEmpty()) {
      coverage = crosstool;
    }

    PathFragment sysroot = calculateSysroot(ruleContext, toolchainInfo.getDefaultSysroot());

    ImmutableList.Builder<PathFragment> builtInIncludeDirectoriesBuilder = ImmutableList.builder();
    for (String s : toolchainInfo.getRawBuiltInIncludeDirectories()) {
      try {
        builtInIncludeDirectoriesBuilder.add(
            resolveIncludeDir(s, sysroot, toolchainInfo.getCrosstoolTopPathFragment()));
      } catch (InvalidConfigurationException e) {
        ruleContext.ruleError(e.getMessage());
      }
    }
    ImmutableList<PathFragment> builtInIncludeDirectories =
        builtInIncludeDirectoriesBuilder.build();

    coverageEnvironment.add(
        Pair.of(
            "COVERAGE_GCOV_PATH", toolchainInfo.getToolPathFragment(Tool.GCOV).getPathString()));
    if (cppConfiguration.getFdoInstrument() != null) {
      coverageEnvironment.add(Pair.of("FDO_DIR", cppConfiguration.getFdoInstrument()));
    }

    // This tries to convert LLVM profiles to the indexed format if necessary.
    Artifact profileArtifact = null;
    if (fdoMode == FdoMode.LLVM_FDO) {
      profileArtifact =
          convertLLVMRawProfileToIndexed(
              ccSkyframeSupportValue.getFdoZipPath().asFragment(), toolchainInfo, ruleContext);
      if (ruleContext.hasErrors()) {
        return null;
      }
    } else if (fdoMode == FdoMode.AUTO_FDO || fdoMode == FdoMode.XBINARY_FDO) {
      Path fdoProfile = ccSkyframeSupportValue.getFdoZipPath();
      profileArtifact =
          ruleContext.getUniqueDirectoryArtifact(
              "fdo", fdoProfile.getBaseName(), ruleContext.getBinOrGenfilesDirectory());
      ruleContext.registerAction(
          new SymlinkAction(
              ruleContext.getActionOwner(),
              fdoProfile.asFragment(),
              profileArtifact,
              "Symlinking FDO profile " + fdoProfile.getPathString()));
    }

    Artifact prefetchHintsArtifact = getPrefetchHintsArtifact(prefetchHints, ruleContext);

    reportInvalidOptions(ruleContext, toolchainInfo);
    return new CcToolchainProvider(
        getToolchainForSkylark(toolchainInfo),
        cppConfiguration,
        toolchainInfo,
        cppConfiguration.getCrosstoolTopPathFragment(),
        crosstool,
        /* crosstoolMiddleman= */ NestedSetBuilder.<Artifact>stableOrder()
            .addTransitive(crosstoolMiddleman)
            .addTransitive(libcMiddleman)
            .build(),
        compile,
        compileWithoutIncludes,
        strip,
        objcopy,
        as,
        ar,
        fullInputsForLink(ruleContext, link, libcMiddleman, isAppleToolchain),
        ruleContext.getPrerequisiteArtifact("$interface_library_builder", Mode.HOST),
        dwp,
        coverage,
        libc,
        staticRuntimeLinkInputs,
        staticRuntimeLinkMiddleman,
        dynamicRuntimeLinkSymlinks,
        dynamicRuntimeLinkMiddleman,
        runtimeSolibDir,
        ccCompilationContext,
        supportsParamFiles,
        supportsHeaderParsing,
        getBuildVariables(ruleContext, toolchainInfo.getDefaultSysroot(), additionalBuildVariables),
        getBuiltinIncludes(libc),
        coverageEnvironment.build(),
        toolchainInfo.supportsInterfaceSharedObjects()
            ? ruleContext.getPrerequisiteArtifact("$link_dynamic_library_tool", Mode.HOST)
            : null,
        builtInIncludeDirectories,
        sysroot,
        fdoMode,
        new FdoProvider(
            ccSkyframeSupportValue.getFdoZipPath(),
            fdoMode,
            cppConfiguration.getFdoInstrument(),
            profileArtifact,
            prefetchHintsArtifact,
            protoProfileArtifact,
            allowInference),
        cppConfiguration.useLLVMCoverageMapFormat(),
        configuration.isCodeCoverageEnabled(),
        configuration.isHostConfiguration());
  }

  /** Finds an appropriate {@link CppToolchainInfo} for this target. */
  private static CppToolchainInfo getCppToolchainInfo(
      RuleContext ruleContext,
      CppConfiguration cppConfiguration,
      CcSkyframeSupportValue ccSkyframeSupportValue,
      CToolchain toolchainFromCcToolchainAttribute)
      throws RuleErrorException {

    if (cppConfiguration.enableCcToolchainConfigInfoFromSkylark()) {
      // Attempt to obtain CppToolchainInfo from the 'toolchain_config' attribute of cc_toolchain.
      CcToolchainConfigInfo configInfo =
          ruleContext.getPrerequisite(
              CcToolchainRule.TOOLCHAIN_CONFIG_ATTR, Mode.TARGET, CcToolchainConfigInfo.PROVIDER);
      if (configInfo != null) {
        try {
          return CppToolchainInfo.create(
              ruleContext.getRepository().getPathUnderExecRoot(),
              ruleContext.getLabel(),
              configInfo,
              cppConfiguration.disableLegacyCrosstoolFields(),
              cppConfiguration.disableCompilationModeFlags(),
              cppConfiguration.disableLinkingModeFlags());
        } catch (InvalidConfigurationException e) {
          throw ruleContext.throwWithRuleError(e.getMessage());
        }
      }
    }

    // Attempt to find a toolchain based on the target attributes, not the configuration.
    CToolchain toolchain = toolchainFromCcToolchainAttribute;
    if (toolchain == null) {
      toolchain = getToolchainFromAttributes(ruleContext, cppConfiguration, ccSkyframeSupportValue);
    }

    // If we found a toolchain, use it.
    try {
      toolchain =
          CppToolchainInfo.addLegacyFeatures(
              toolchain, cppConfiguration.getCrosstoolTopPathFragment());
      CcToolchainConfigInfo ccToolchainConfigInfo = CcToolchainConfigInfo.fromToolchain(toolchain);
      return CppToolchainInfo.create(
          cppConfiguration.getCrosstoolTopPathFragment(),
          cppConfiguration.getCcToolchainRuleLabel(),
          ccToolchainConfigInfo,
          cppConfiguration.disableLegacyCrosstoolFields(),
          cppConfiguration.disableCompilationModeFlags(),
          cppConfiguration.disableLinkingModeFlags());
    } catch (InvalidConfigurationException e) {
      throw ruleContext.throwWithRuleError(e.getMessage());
    }
  }

  @Nullable
  private static CToolchain parseToolchainFromAttributes(RuleContext ruleContext)
      throws RuleErrorException {
    String protoAttribute = StringUtil.emptyToNull(ruleContext.attributes().get("proto", STRING));
    if (protoAttribute == null) {
      return null;
    }

    CToolchain.Builder builder = CToolchain.newBuilder();
    try {
      TextFormat.merge(protoAttribute, builder);
      return builder.build();
    } catch (ParseException e) {
      throw ruleContext.throwWithAttributeError("proto", "Could not parse CToolchain data");
    }
  }

  private static void reportInvalidOptions(RuleContext ruleContext, CppToolchainInfo toolchain) {
    CppOptions options = ruleContext.getConfiguration().getOptions().get(CppOptions.class);
    CppConfiguration config = ruleContext.getFragment(CppConfiguration.class);
    if (options.fissionModes.contains(config.getCompilationMode())
        && !toolchain.supportsFission()) {
      ruleContext.ruleWarning(
          "Fission is not supported by this crosstool.  Please use a "
              + "supporting crosstool to enable fission");
    }
    if (options.buildTestDwp
        && !(toolchain.supportsFission() && config.fissionIsActiveForCurrentCompilationMode())) {
      ruleContext.ruleWarning(
          "Test dwp file requested, but Fission is not enabled.  To generate a "
              + "dwp for the test executable, use '--fission=yes' with a toolchain that supports "
              + "Fission to build statically.");
    }
  }

  @Nullable
  private static CToolchain getToolchainFromAttributes(
      RuleContext ruleContext,
      CppConfiguration cppConfiguration,
      CcSkyframeSupportValue ccSkyframeSupportValue)
      throws RuleErrorException {

    String toolchainIdentifier = ruleContext.attributes().get("toolchain_identifier", Type.STRING);
    String cpu = ruleContext.attributes().get("cpu", Type.STRING);
    String compiler = ruleContext.attributes().get("compiler", Type.STRING);
    try {
      CrosstoolRelease crosstoolRelease;
      if (cppConfiguration.getCrosstoolFromCcToolchainProtoAttribute() != null) {
        // We have cc_toolchain_suite.proto attribute set, let's use it
        crosstoolRelease = cppConfiguration.getCrosstoolFromCcToolchainProtoAttribute();
      } else {
        // We use the proto from the CROSSTOOL file
        crosstoolRelease = ccSkyframeSupportValue.getCrosstoolRelease();
      }

      return CToolchainSelectionUtils.selectCToolchain(
          toolchainIdentifier,
          cpu,
          compiler,
          cppConfiguration.getTransformedCpuFromOptions(),
          cppConfiguration.getCompilerFromOptions(),
          crosstoolRelease);
    } catch (InvalidConfigurationException e) {
      ruleContext.throwWithRuleError(
          String.format("Error while selecting cc_toolchain: %s", e.getMessage()));
      return null;
    }
  }

  private static ImmutableList<Artifact> getBuiltinIncludes(NestedSet<Artifact> libc) {
    ImmutableList.Builder<Artifact> result = ImmutableList.builder();
    for (Artifact artifact : libc) {
      if (artifact.getExecPath().endsWith(BUILTIN_INCLUDE_FILE_SUFFIX)) {
        result.add(artifact);
      }
    }

    return result.build();
  }

  private static CppModuleMap createCrosstoolModuleMap(RuleContext ruleContext) {
    if (ruleContext.getPrerequisite("module_map", Mode.HOST) == null) {
      return null;
    }
    Artifact moduleMapArtifact = ruleContext.getPrerequisiteArtifact("module_map", Mode.HOST);
    if (moduleMapArtifact == null) {
      return null;
    }
    return new CppModuleMap(moduleMapArtifact, "crosstool");
  }

  static TransitiveInfoCollection selectDep(
      RuleContext ruleContext, String attribute, Label label) {
    for (TransitiveInfoCollection dep : ruleContext.getPrerequisites(attribute, Mode.TARGET)) {
      if (dep.getLabel().equals(label)) {
        return dep;
      }
    }

    return ruleContext.getPrerequisites(attribute, Mode.TARGET).get(0);
  }

  private static NestedSet<Artifact> getMiddlemanOrFiles(RuleContext context, String attribute) {
    return getMiddlemanOrFiles(context, attribute, Mode.HOST);
  }

  private static NestedSet<Artifact> getMiddlemanOrFiles(
      RuleContext context, String attribute, Mode mode) {
    TransitiveInfoCollection dep = context.getPrerequisite(attribute, mode);
    MiddlemanProvider middlemanProvider = dep.getProvider(MiddlemanProvider.class);
    // We use the middleman if we can (if the dep is a filegroup), otherwise, just the regular
    // filesToBuild (e.g. if it is a simple input file)
    return middlemanProvider != null
        ? middlemanProvider.getMiddlemanArtifact()
        : dep.getProvider(FileProvider.class).getFilesToBuild();
  }

  private static NestedSet<Artifact> getOptionalMiddlemanOrFiles(
      RuleContext context, String attribute) {
    return getOptionalMiddlemanOrFiles(context, attribute, Mode.HOST);
  }

  private static NestedSet<Artifact> getOptionalMiddlemanOrFiles(
      RuleContext context, String attribute, Mode mode) {
    TransitiveInfoCollection dep = context.getPrerequisite(attribute, mode);
    return dep != null
        ? getMiddlemanOrFiles(context, attribute, mode)
        : NestedSetBuilder.emptySet(Order.STABLE_ORDER);
  }

  private static NestedSet<Artifact> getOptionalFiles(
      RuleContext ruleContext, String attribute, Mode mode) {
    TransitiveInfoCollection dep = ruleContext.getPrerequisite(attribute, mode);
    return dep != null
        ? dep.getProvider(FileProvider.class).getFilesToBuild()
        : NestedSetBuilder.emptySet(Order.STABLE_ORDER);
  }

  /**
   * Returns {@link CcToolchainVariables} instance with build variables that only depend on the
   * toolchain.
   *
   * @param ruleContext the rule context
   * @param defaultSysroot the default sysroot
   * @param additionalBuildVariables
   * @throws RuleErrorException if there are configuration errors making it impossible to resolve
   *     certain build variables of this toolchain
   */
  private static final CcToolchainVariables getBuildVariables(
      RuleContext ruleContext,
      PathFragment defaultSysroot,
      CcToolchainVariables additionalBuildVariables) {
    CcToolchainVariables.Builder variables = new CcToolchainVariables.Builder();

    CppConfiguration cppConfiguration =
        Preconditions.checkNotNull(ruleContext.getFragment(CppConfiguration.class));
    String minOsVersion = cppConfiguration.getMinimumOsVersion();
    if (minOsVersion != null) {
      variables.addStringVariable(CcCommon.MINIMUM_OS_VERSION_VARIABLE_NAME, minOsVersion);
    }

    PathFragment sysroot = calculateSysroot(ruleContext, defaultSysroot);
    if (sysroot != null) {
      variables.addStringVariable(CcCommon.SYSROOT_VARIABLE_NAME, sysroot.getPathString());
    }

    variables.addAllNonTransitive(additionalBuildVariables);

    return variables.build();
  }

  /**
   * Returns the crosstool-derived link action inputs for a given rule. Adds the given set of
   * artifacts as extra inputs.
   */
  private static NestedSet<Artifact> fullInputsForLink(
      RuleContext ruleContext,
      NestedSet<Artifact> link,
      NestedSet<Artifact> libcLink,
      boolean isAppleToolchain) {
    NestedSetBuilder<Artifact> builder =
        NestedSetBuilder.<Artifact>stableOrder().addTransitive(link).addTransitive(libcLink);
    if (!isAppleToolchain) {
      builder
          .add(ruleContext.getPrerequisiteArtifact("$interface_library_builder", Mode.HOST))
          .add(ruleContext.getPrerequisiteArtifact("$link_dynamic_library_tool", Mode.HOST));
    }
    return builder.build();
  }
}
