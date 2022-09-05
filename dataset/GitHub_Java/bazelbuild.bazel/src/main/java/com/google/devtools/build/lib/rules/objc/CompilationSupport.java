// Copyright 2015 Google Inc. All rights reserved.
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

import static com.google.devtools.build.lib.rules.objc.ObjcRuleClasses.NON_ARC_SRCS_TYPE;
import static com.google.devtools.build.lib.rules.objc.ObjcRuleClasses.SRCS_TYPE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.actions.CustomCommandLine;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.rules.objc.ObjcActionsBuilder.ExtraLinkArgs;
import com.google.devtools.build.lib.rules.objc.ObjcActionsBuilder.ExtraLinkInputs;
import com.google.devtools.build.lib.rules.objc.ObjcCommon.CompilationAttributes;
import com.google.devtools.build.lib.rules.objc.XcodeProvider.Builder;
import com.google.devtools.build.lib.shell.ShellUtils;
import com.google.devtools.build.lib.vfs.PathFragment;

/**
 * Support for rules that compile sources. Provides ways to determine files that should be output,
 * registering Xcode settings and generating the various actions that might be needed for
 * compilation.
 *
 * <p>Methods on this class can be called in any order without impacting the result.
 */
final class CompilationSupport {

  @VisibleForTesting
  static final String ABSOLUTE_INCLUDES_PATH_FORMAT =
      "The path '%s' is absolute, but only relative paths are allowed.";

  @VisibleForTesting
  static final ImmutableList<String> LINKER_COVERAGE_FLAGS = ImmutableList.<String>of(
      "-ftest-coverage", "-fprofile-arcs");

  /**
   * Returns information about the given rule's compilation artifacts.
   */
  // TODO(bazel-team): Remove this information from ObjcCommon and move it internal to this class.
  static CompilationArtifacts compilationArtifacts(RuleContext ruleContext) {
    return new CompilationArtifacts.Builder()
        .addSrcs(ruleContext.getPrerequisiteArtifacts("srcs", Mode.TARGET)
            .errorsForNonMatching(SRCS_TYPE)
            .list())
        .addNonArcSrcs(ruleContext.getPrerequisiteArtifacts("non_arc_srcs", Mode.TARGET)
            .errorsForNonMatching(NON_ARC_SRCS_TYPE)
            .list())
        .setIntermediateArtifacts(ObjcRuleClasses.intermediateArtifacts(ruleContext))
        .setPchFile(Optional.fromNullable(ruleContext.getPrerequisiteArtifact("pch", Mode.TARGET)))
        .build();
  }

  private final RuleContext ruleContext;
  private final CompilationAttributes attributes;

  /**
   * Creates a new compilation support for the given rule.
   */
  CompilationSupport(RuleContext ruleContext) {
    this.ruleContext = ruleContext;
    this.attributes = new CompilationAttributes(ruleContext);
  }

  /**
   * Registers all actions necessary to compile this rule's sources and archive them.
   *
   * @param common common information about this rule and its dependencies
   * @param optionsProvider option and plist information about this rule and its dependencies
   *
   * @return this compilation support
   */
  CompilationSupport registerCompileAndArchiveActions(
      ObjcCommon common, OptionsProvider optionsProvider) {
    if (common.getCompilationArtifacts().isPresent()) {
      ObjcRuleClasses.actionsBuilder(ruleContext).registerCompileAndArchiveActions(
          common.getCompilationArtifacts().get(), common.getObjcProvider(), optionsProvider,
          ruleContext.getConfiguration().isCodeCoverageEnabled());
    }
    return this;
  }

  /**
   * Registers any actions necessary to link this rule and its dependencies. Debug symbols are
   * generated if {@link ObjcConfiguration#generateDebugSymbols()} is set.
   *
   * @param objcProvider common information about this rule's attributes and its dependencies
   * @param extraLinkArgs any additional arguments to pass to the linker
   * @param extraLinkInputs any additional input artifacts to pass to the link action
   *
   * @return this compilation support
   */
  CompilationSupport registerLinkActions(ObjcProvider objcProvider, ExtraLinkArgs extraLinkArgs,
      ExtraLinkInputs extraLinkInputs) {
    IntermediateArtifacts intermediateArtifacts =
        ObjcRuleClasses.intermediateArtifacts(ruleContext);
    Optional<Artifact> dsymBundle;
    if (ObjcRuleClasses.objcConfiguration(ruleContext).generateDebugSymbols()) {
      registerDsymActions();
      dsymBundle = Optional.of(intermediateArtifacts.dsymBundle());
    } else {
      dsymBundle = Optional.absent();
    }

    ExtraLinkArgs coverageLinkArgs = new ExtraLinkArgs();
    if (ruleContext.getConfiguration().isCodeCoverageEnabled()) {
      coverageLinkArgs = new ExtraLinkArgs(LINKER_COVERAGE_FLAGS);
    }

    ObjcRuleClasses.actionsBuilder(ruleContext).registerLinkAction(
        intermediateArtifacts.singleArchitectureBinary(), objcProvider,
        extraLinkArgs.appendedWith(coverageLinkArgs), extraLinkInputs,
        dsymBundle);
    return this;
  }

  /**
   * Registers actions that compile and archive j2Objc dependencies of this rule.
   *
   * @param optionsProvider option and plist information about this rule and its dependencies
   * @param objcProvider common information about this rule's attributes and its dependencies
   *
   * @return this compilation support
   */
  CompilationSupport registerJ2ObjcCompileAndArchiveActions(
      OptionsProvider optionsProvider, ObjcProvider objcProvider) {
    for (J2ObjcSource j2ObjcSource : ObjcRuleClasses.j2ObjcSrcsProvider(ruleContext).getSrcs()) {
      IntermediateArtifacts intermediateArtifacts =
          ObjcRuleClasses.j2objcIntermediateArtifacts(ruleContext, j2ObjcSource);
      CompilationArtifacts compilationArtifact = new CompilationArtifacts.Builder()
          .addNonArcSrcs(j2ObjcSource.getObjcSrcs())
          .setIntermediateArtifacts(intermediateArtifacts)
          .setPchFile(Optional.<Artifact>absent())
          .build();
      ObjcActionsBuilder actionBuilder = new ObjcActionsBuilder(
          ruleContext,
          intermediateArtifacts,
          ObjcRuleClasses.objcConfiguration(ruleContext),
          ruleContext.getConfiguration(),
          ruleContext);
      actionBuilder
          .registerCompileAndArchiveActions(compilationArtifact, objcProvider, optionsProvider,
               ruleContext.getConfiguration().isCodeCoverageEnabled());
    }

    return this;
  }

  /**
   * Sets compilation-related Xcode project information on the given provider builder.
   *
   * @param common common information about this rule's attributes and its dependencies
   * @param optionsProvider option and plist information about this rule and its dependencies
   * @return this compilation support
   */
  CompilationSupport addXcodeSettings(Builder xcodeProviderBuilder,
      ObjcCommon common, OptionsProvider optionsProvider) {
    ObjcConfiguration objcConfiguration = ObjcRuleClasses.objcConfiguration(ruleContext);
    for (CompilationArtifacts artifacts : common.getCompilationArtifacts().asSet()) {
      xcodeProviderBuilder.setCompilationArtifacts(artifacts);
    }
    xcodeProviderBuilder
        .addHeaders(attributes.hdrs())
        .addUserHeaderSearchPaths(ObjcCommon.userHeaderSearchPaths(ruleContext.getConfiguration()))
        .addHeaderSearchPaths("$(WORKSPACE_ROOT)", attributes.headerSearchPaths())
        .addHeaderSearchPaths("$(SDKROOT)/usr/include", attributes.sdkIncludes())
        .addCompilationModeCopts(objcConfiguration.getCoptsForCompilationMode())
        .addCopts(objcConfiguration.getCopts())
        .addCopts(optionsProvider.getCopts());
    return this;
  }

  /**
   * Validates compilation-related attributes on this rule.
   *
   * @return this compilation support
   */
  CompilationSupport validateAttributes() {
    for (PathFragment absoluteInclude :
        Iterables.filter(attributes.includes(), PathFragment.IS_ABSOLUTE)) {
      ruleContext.attributeError(
          "includes", String.format(ABSOLUTE_INCLUDES_PATH_FORMAT, absoluteInclude));
    }

    return this;
  }

  private CompilationSupport registerDsymActions() {
    IntermediateArtifacts intermediateArtifacts =
        ObjcRuleClasses.intermediateArtifacts(ruleContext);
    Artifact dsymBundle = intermediateArtifacts.dsymBundle();
    Artifact debugSymbolFile = intermediateArtifacts.dsymSymbol();
    ruleContext.registerAction(new SpawnAction.Builder()
        .setMnemonic("UnzipDsym")
        .setProgressMessage("Unzipping dSYM file: " + ruleContext.getLabel())
        .setExecutable(new PathFragment("/usr/bin/unzip"))
        .addInput(dsymBundle)
        .setCommandLine(CustomCommandLine.builder()
            .add(dsymBundle.getExecPathString())
            .add("-d")
            .add(stripSuffix(dsymBundle.getExecPathString(),
                IntermediateArtifacts.TMP_DSYM_BUNDLE_SUFFIX) + ".app.dSYM")
            .build())
        .addOutput(intermediateArtifacts.dsymPlist())
        .addOutput(debugSymbolFile)
        .build(ruleContext));

    Artifact dumpsyms = ruleContext.getPrerequisiteArtifact("$dumpsyms", Mode.HOST);
    Artifact breakpadFile = intermediateArtifacts.breakpadSym();
    ruleContext.registerAction(new SpawnAction.Builder()
        .setMnemonic("GenBreakpad")
        .setProgressMessage("Generating breakpad file: " + ruleContext.getLabel())
        .setShellCommand(ImmutableList.of("/bin/bash", "-c"))
        .setExecutionInfo(ImmutableMap.of(ExecutionRequirements.REQUIRES_DARWIN, ""))
        .addInput(dumpsyms)
        .addInput(debugSymbolFile)
        .addArgument(String.format("%s %s > %s",
            ShellUtils.shellEscape(dumpsyms.getExecPathString()),
            ShellUtils.shellEscape(debugSymbolFile.getExecPathString()),
            ShellUtils.shellEscape(breakpadFile.getExecPathString())))
        .addOutput(breakpadFile)
        .build(ruleContext));
    return this;
  }

  private String stripSuffix(String str, String suffix) {
    // TODO(bazel-team): Throw instead of returning null?
    return str.endsWith(suffix) ? str.substring(0, str.length() - suffix.length()) : null;
  }
}
