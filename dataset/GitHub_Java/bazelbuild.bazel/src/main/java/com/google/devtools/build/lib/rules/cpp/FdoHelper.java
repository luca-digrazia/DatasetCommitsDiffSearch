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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.actions.CustomCommandLine;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.analysis.actions.SymlinkAction;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.CompilationMode;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.packages.RuleClass.ConfiguredTargetFactory.RuleErrorException;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration.Tool;
import com.google.devtools.build.lib.rules.cpp.FdoContext.BranchFdoMode;
import com.google.devtools.build.lib.util.FileType;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyKey;

/** Helper responsible for creating {@link FdoContext} */
public class FdoHelper {

  public static FdoContext getFdoContext(
      RuleContext ruleContext,
      CcToolchainAttributesProvider attributes,
      BuildConfiguration configuration,
      CppConfiguration cppConfiguration,
      CppToolchainInfo toolchainInfo)
      throws InterruptedException, RuleErrorException {
    FdoInputFile fdoInputFile = null;
    FdoInputFile prefetchHints = null;
    Artifact protoProfileArtifact = null;
    Pair<FdoInputFile, Artifact> fdoInputs = null;
    if (configuration.getCompilationMode() == CompilationMode.OPT) {
      if (cppConfiguration.getFdoPrefetchHintsLabel() != null) {
        FdoPrefetchHintsProvider provider = attributes.getFdoPrefetch();
        prefetchHints = provider.getInputFile();
      }
      if (cppConfiguration.getFdoPath() != null) {
        PathFragment fdoZip = cppConfiguration.getFdoPath();
        SkyKey fdoKey = CcSkyframeFdoSupportValue.key(fdoZip);
        SkyFunction.Environment skyframeEnv = ruleContext.getAnalysisEnvironment().getSkyframeEnv();
        CcSkyframeFdoSupportValue ccSkyframeFdoSupportValue =
            (CcSkyframeFdoSupportValue) skyframeEnv.getValue(fdoKey);
        if (skyframeEnv.valuesMissing()) {
          return null;
        }
        // fdoZip should be set if the profile is a path, fdoInputFile if it is an artifact, but
        // never both
        Preconditions.checkState(fdoInputFile == null);
        fdoInputFile =
            FdoInputFile.fromAbsolutePath(ccSkyframeFdoSupportValue.getFdoZipPath().asFragment());
      } else if (cppConfiguration.getFdoOptimizeLabel() != null) {
        FdoProfileProvider fdoProfileProvider = attributes.getFdoOptimizeProvider();
        if (fdoProfileProvider != null) {
          fdoInputs = getFdoInputs(ruleContext, fdoProfileProvider);
        } else {
          fdoInputFile = fdoInputFileFromArtifacts(ruleContext, attributes);
        }
      } else if (cppConfiguration.getFdoProfileLabel() != null) {
        fdoInputs = getFdoInputs(ruleContext, attributes.getFdoProfileProvider());
      } else if (cppConfiguration.getXFdoProfileLabel() != null) {
        fdoInputs = getFdoInputs(ruleContext, attributes.getXFdoProfileProvider());
      }
    }

    if (ruleContext.hasErrors()) {
      return null;
    }

    if (fdoInputs != null) {
      fdoInputFile = fdoInputs.getFirst();
      protoProfileArtifact = fdoInputs.getSecond();
    }

    FdoContext.BranchFdoProfile branchFdoProfile = null;
    if (fdoInputFile != null) {
      BranchFdoMode branchFdoMode;
      if (CppFileTypes.GCC_AUTO_PROFILE.matches(fdoInputFile)) {
        branchFdoMode = BranchFdoMode.AUTO_FDO;
      } else if (CppFileTypes.XBINARY_PROFILE.matches(fdoInputFile)) {
        branchFdoMode = BranchFdoMode.XBINARY_FDO;
      } else if (CppFileTypes.LLVM_PROFILE.matches(fdoInputFile)) {
        branchFdoMode = BranchFdoMode.LLVM_FDO;
      } else if (CppFileTypes.LLVM_PROFILE_RAW.matches(fdoInputFile)) {
        branchFdoMode = BranchFdoMode.LLVM_FDO;
      } else if (CppFileTypes.LLVM_PROFILE_ZIP.matches(fdoInputFile)) {
        branchFdoMode = BranchFdoMode.LLVM_FDO;
      } else {
        ruleContext.ruleError("invalid extension for FDO profile file.");
        return null;
      }
      if (branchFdoMode != BranchFdoMode.XBINARY_FDO
          && cppConfiguration.getXFdoProfileLabel() != null) {
        ruleContext.throwWithRuleError(
            "--xbinary_fdo cannot accept profile input other than *.xfdo");
      }

      if (configuration.isCodeCoverageEnabled()) {
        ruleContext.throwWithRuleError("coverage mode is not compatible with FDO optimization");
      }
      // This tries to convert LLVM profiles to the indexed format if necessary.
      Artifact profileArtifact = null;
      if (branchFdoMode == BranchFdoMode.LLVM_FDO) {
        profileArtifact =
            convertLLVMRawProfileToIndexed(attributes, fdoInputFile, toolchainInfo, ruleContext);
        if (ruleContext.hasErrors()) {
          return null;
        }
      } else if (branchFdoMode == BranchFdoMode.AUTO_FDO
          || branchFdoMode == BranchFdoMode.XBINARY_FDO) {
        profileArtifact =
            ruleContext.getUniqueDirectoryArtifact(
                "fdo", fdoInputFile.getBasename(), ruleContext.getBinOrGenfilesDirectory());
        symlinkTo(
            ruleContext,
            profileArtifact,
            fdoInputFile,
            "Symlinking FDO profile " + fdoInputFile.getBasename());
      }
      branchFdoProfile =
          new FdoContext.BranchFdoProfile(branchFdoMode, profileArtifact, protoProfileArtifact);
    }
    Artifact prefetchHintsArtifact = getPrefetchHintsArtifact(prefetchHints, ruleContext);
    return new FdoContext(branchFdoProfile, prefetchHintsArtifact);
  }

  /**
   * Returns the profile name with the same file name as fdoProfile and an extension that matches
   * {@link FileType}.
   */
  private static String getLLVMProfileFileName(FdoInputFile fdoProfile, FileType type) {
    if (type.matches(fdoProfile)) {
      return fdoProfile.getBasename();
    } else {
      return FileSystemUtils.removeExtension(fdoProfile.getBasename())
          + type.getExtensions().get(0);
    }
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
        SymlinkAction.toAbsolutePath(
            ruleContext.getActionOwner(),
            PathFragment.create(prefetchHintsFile.getAbsolutePath().getPathString()),
            prefetchHintsArtifact,
            "Symlinking LLVM Cache Prefetch Hints Profile "
                + prefetchHintsFile.getAbsolutePath().getPathString()));
    return prefetchHintsArtifact;
  }

  private static void symlinkTo(
      RuleContext ruleContext,
      Artifact symlink,
      FdoInputFile fdoInputFile,
      String progressMessage) {
    if (fdoInputFile.getArtifact() != null) {
      ruleContext.registerAction(
          SymlinkAction.toArtifact(
              ruleContext.getActionOwner(), fdoInputFile.getArtifact(), symlink, progressMessage));
    } else {
      ruleContext.registerAction(
          SymlinkAction.toAbsolutePath(
              ruleContext.getActionOwner(),
              fdoInputFile.getAbsolutePath(),
              symlink,
              progressMessage));
    }
  }

  /*
   * This function checks the format of the input profile data and converts it to
   * the indexed format (.profdata) if necessary.
   */
  private static Artifact convertLLVMRawProfileToIndexed(
      CcToolchainAttributesProvider attributes,
      FdoInputFile fdoProfile,
      CppToolchainInfo toolchainInfo,
      RuleContext ruleContext) {

    Artifact profileArtifact =
        ruleContext.getUniqueDirectoryArtifact(
            "fdo",
            getLLVMProfileFileName(fdoProfile, CppFileTypes.LLVM_PROFILE),
            ruleContext.getBinOrGenfilesDirectory());

    // If the profile file is already in the desired format, symlink to it and return.
    if (CppFileTypes.LLVM_PROFILE.matches(fdoProfile)) {
      symlinkTo(
          ruleContext,
          profileArtifact,
          fdoProfile,
          "Symlinking LLVM Profile " + fdoProfile.getBasename());
      return profileArtifact;
    }

    Artifact rawProfileArtifact;

    if (CppFileTypes.LLVM_PROFILE_ZIP.matches(fdoProfile)) {
      // Get the zipper binary for unzipping the profile.
      Artifact zipperBinaryArtifact = attributes.getZipper();
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
              "fdo", fdoProfile.getBasename(), ruleContext.getBinOrGenfilesDirectory());
      symlinkTo(
          ruleContext,
          zipProfileArtifact,
          fdoProfile,
          "Symlinking LLVM ZIP Profile " + fdoProfile.getBasename());

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
      symlinkTo(
          ruleContext,
          rawProfileArtifact,
          fdoProfile,
          "Symlinking LLVM Raw Profile " + fdoProfile.getBasename());
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
            .addTransitiveInputs(attributes.getAllFilesMiddleman())
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

  static Pair<FdoInputFile, Artifact> getFdoInputs(
      RuleContext ruleContext, FdoProfileProvider fdoProfileProvider) {
    if (fdoProfileProvider == null) {
      ruleContext.ruleError("--fdo_profile/--xbinary_fdo input needs to be an fdo_profile rule");
      return null;
    }
    return Pair.of(fdoProfileProvider.getInputFile(), fdoProfileProvider.getProtoProfileArtifact());
  }

  private static FdoInputFile fdoInputFileFromArtifacts(
      RuleContext ruleContext, CcToolchainAttributesProvider attributes) {
    ImmutableList<Artifact> fdoArtifacts = attributes.getFdoOptimizeArtifacts();
    if (fdoArtifacts.size() != 1) {
      ruleContext.ruleError("--fdo_optimize does not point to a single target");
      return null;
    }

    Artifact fdoArtifact = fdoArtifacts.get(0);
    if (!fdoArtifact.isSourceArtifact()) {
      ruleContext.ruleError("--fdo_optimize points to a target that is not an input file");
      return null;
    }

    Label fdoLabel = attributes.getFdoOptimize().getLabel();
    if (!fdoLabel
        .getPackageIdentifier()
        .getPathUnderExecRoot()
        .getRelative(fdoLabel.getName())
        .equals(fdoArtifact.getExecPath())) {
      ruleContext.ruleError("--fdo_optimize points to a target that is not an input file");
      return null;
    }

    return FdoInputFile.fromArtifact(fdoArtifact);
  }
}
