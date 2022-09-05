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

import static com.google.devtools.build.lib.rules.objc.XcodeProductType.LIBRARY_STATIC;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.rules.RuleConfiguredTargetFactory;
import com.google.devtools.build.lib.rules.objc.ObjcCommon.CompilationAttributes;
import com.google.devtools.build.lib.rules.objc.ObjcCommon.ResourceAttributes;

/**
 * Implementation for {@code objc_library}.
 */
public class ObjcLibrary implements RuleConfiguredTargetFactory {

  /**
   * An {@link IterableWrapper} containing extra library {@link Artifact}s to be linked into the
   * final ObjC application bundle.
   */
  static final class ExtraImportLibraries extends IterableWrapper<Artifact> {
    ExtraImportLibraries(Artifact... extraImportLibraries) {
      super(extraImportLibraries);
    }
  }

  /**
   * Constructs an {@link ObjcCommon} instance based on the attributes of the given rule. The rule
   * should inherit from {@link ObjcLibraryRule}..
   */
  static ObjcCommon common(RuleContext ruleContext, Iterable<SdkFramework> extraSdkFrameworks,
      boolean alwayslink, ExtraImportLibraries extraImportLibraries,
      Iterable<ObjcProvider> extraDepObjcProviders) {
    CompilationArtifacts compilationArtifacts =
        CompilationSupport.compilationArtifacts(ruleContext);

    return new ObjcCommon.Builder(ruleContext)
        .setCompilationAttributes(new CompilationAttributes(ruleContext))
        .setResourceAttributes(new ResourceAttributes(ruleContext))
        .addExtraSdkFrameworks(extraSdkFrameworks)
        .addDefines(ruleContext.getTokenizedStringListAttr("defines"))
        .setCompilationArtifacts(compilationArtifacts)
        .addDepObjcProviders(ruleContext.getPrerequisites("deps", Mode.TARGET, ObjcProvider.class))
        .addDepObjcProviders(
            ruleContext.getPrerequisites("bundles", Mode.TARGET, ObjcProvider.class))
        .addNonPropagatedDepObjcProviders(ruleContext.getPrerequisites("non_propagated_deps",
            Mode.TARGET, ObjcProvider.class))
        .setIntermediateArtifacts(ObjcRuleClasses.intermediateArtifacts(ruleContext))
        .setAlwayslink(alwayslink)
        .addExtraImportLibraries(extraImportLibraries)
        .addDepObjcProviders(extraDepObjcProviders)
        .build();
  }

  @Override
  public ConfiguredTarget create(RuleContext ruleContext) throws InterruptedException {
    ObjcCommon common = common(
        ruleContext, ImmutableList.<SdkFramework>of(),
        ruleContext.attributes().get("alwayslink", Type.BOOLEAN), new ExtraImportLibraries(),
        ImmutableList.<ObjcProvider>of());
    OptionsProvider optionsProvider = optionsProvider(ruleContext);

    XcodeProvider.Builder xcodeProviderBuilder = new XcodeProvider.Builder();
    NestedSetBuilder<Artifact> filesToBuild = NestedSetBuilder.<Artifact>stableOrder()
        .addAll(common.getCompiledArchive().asSet());

    new CompilationSupport(ruleContext)
        .registerCompileAndArchiveActions(common, optionsProvider)
        .addXcodeSettings(xcodeProviderBuilder, common, optionsProvider)
        .validateAttributes();

    new ResourceSupport(ruleContext)
        .registerActions(common.getStoryboards())
        .validateAttributes()
        .addXcodeSettings(xcodeProviderBuilder);

    new XcodeSupport(ruleContext)
        .addFilesToBuild(filesToBuild)
        .addXcodeSettings(xcodeProviderBuilder, common.getObjcProvider(), LIBRARY_STATIC)
        .addDependencies(xcodeProviderBuilder)
        .registerActions(xcodeProviderBuilder.build());

    return common.configuredTarget(
        filesToBuild.build(),
        Optional.of(xcodeProviderBuilder.build()),
        Optional.of(common.getObjcProvider()),
        Optional.<XcTestAppProvider>absent(),
        Optional.of(ObjcRuleClasses.j2ObjcSrcsProvider(ruleContext)));
  }

  private OptionsProvider optionsProvider(RuleContext ruleContext) {
    return new OptionsProvider.Builder()
        .addCopts(ruleContext.getTokenizedStringListAttr("copts"))
        .addTransitive(Optional.fromNullable(
            ruleContext.getPrerequisite("options", Mode.TARGET, OptionsProvider.class)))
        .build();
  }
}
