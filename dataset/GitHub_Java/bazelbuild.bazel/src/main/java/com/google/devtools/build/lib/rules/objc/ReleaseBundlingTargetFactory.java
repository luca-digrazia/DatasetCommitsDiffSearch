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

package com.google.devtools.build.lib.rules.objc;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetBuilder;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.rules.RuleConfiguredTargetFactory;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;
import com.google.devtools.build.lib.rules.apple.ApplePlatform.PlatformType;
import com.google.devtools.build.lib.rules.apple.DottedVersion;
import com.google.devtools.build.lib.rules.apple.XcodeConfig;
import com.google.devtools.build.lib.rules.objc.ReleaseBundlingSupport.LinkedBinary;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesCollector;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesProvider;
import javax.annotation.Nullable;

/**
 * Base class for rules that bundle releases.
 *
 * @deprecated The native bundling rules have been deprecated. This class will be removed in the
 *     future.
 */
@Deprecated
public abstract class ReleaseBundlingTargetFactory implements RuleConfiguredTargetFactory {

  private final String bundleDirFormat;
  private final ImmutableSet<Attribute> dependencyAttributes;

  /**
   * @param bundleDirFormat format string representing the bundle's directory with a single
   *     placeholder for the target name (e.g. {@code "Payload/%s.app"})
   * @param dependencyAttributes all attributes that contain dependencies of this rule. Any
   *     dependency so listed must expose {@link ObjcProvider}.
   */
  public ReleaseBundlingTargetFactory(
      String bundleDirFormat, ImmutableSet<Attribute> dependencyAttributes) {
    this.bundleDirFormat = bundleDirFormat;
    this.dependencyAttributes = dependencyAttributes;
  }

  @Override
  public ConfiguredTarget create(RuleContext ruleContext)
      throws InterruptedException, RuleErrorException {
    ruleContext.ruleWarning(
        "This rule is deprecated. Please use the new Apple build rules "
            + "(https://github.com/bazelbuild/rules_apple) to build Apple targets.");

    validateAttributes(ruleContext);
    ObjcCommon common = common(ruleContext);

    NestedSetBuilder<Artifact> filesToBuild = NestedSetBuilder.stableOrder();

    AppleConfiguration appleConfiguration = ruleContext.getFragment(AppleConfiguration.class);
    ReleaseBundlingSupport releaseBundlingSupport = new ReleaseBundlingSupport(
        ruleContext, common.getObjcProvider(), LinkedBinary.DEPENDENCIES_ONLY, bundleDirFormat,
        bundleName(ruleContext), bundleMinimumOsVersion(ruleContext),
        appleConfiguration.getMultiArchPlatform(PlatformType.IOS));
    releaseBundlingSupport
        .registerActions(DsymOutputType.APP)
        .addFilesToBuild(filesToBuild, Optional.of(DsymOutputType.APP))
        .validateResources()
        .validateAttributes();

    RuleConfiguredTargetBuilder targetBuilder =
        ObjcRuleClasses.ruleConfiguredTarget(ruleContext, filesToBuild.build())
            .addProvider(XcTestAppProvider.class, releaseBundlingSupport.xcTestAppProvider())
            .addProvider(
                InstrumentedFilesProvider.class,
                InstrumentedFilesCollector.forward(ruleContext, "binary"));

    ObjcProvider exposedObjcProvider = exposedObjcProvider(ruleContext, releaseBundlingSupport);
    if (exposedObjcProvider != null) {
      targetBuilder
          .addProvider(ObjcProvider.class, exposedObjcProvider)
          .addNativeDeclaredProvider(exposedObjcProvider);
    }

    configureTarget(targetBuilder, ruleContext, releaseBundlingSupport);
    return targetBuilder.build();
  }

  /**
   * Validates application-related attributes set on this rule and registers any errors with the
   * rule context. Default implemenation does nothing; subclasses may override it.
   */
  protected void validateAttributes(RuleContext ruleContext) {}

  /**
   * Returns the minimum OS version this bundle's plist and resources should be generated for
   * (<b>not</b> the minimum OS version its binary is compiled with, that needs to be set in the
   * configuration).
   */
  protected DottedVersion bundleMinimumOsVersion(RuleContext ruleContext) {
    return XcodeConfig.getMinimumOsForPlatformType(ruleContext, PlatformType.IOS);
  }

  /**
   * Performs additional configuration of the target. The default implementation does nothing, but
   * subclasses may override it to add logic.
   * @throws InterruptedException 
   */
  protected void configureTarget(RuleConfiguredTargetBuilder target, RuleContext ruleContext,
      ReleaseBundlingSupport releaseBundlingSupport) throws InterruptedException {}

  /**
   * Returns the name of this target's bundle.
   */
  protected String bundleName(RuleContext ruleContext) {
    return ruleContext.getLabel().getName();
  }

  /**
   * Returns an exposed {@code ObjcProvider} object.
   * @throws InterruptedException
   */
  @Nullable
  protected ObjcProvider exposedObjcProvider(
      RuleContext ruleContext, ReleaseBundlingSupport releaseBundlingSupport)
      throws InterruptedException {
    return null;
  }

  private ObjcCommon common(RuleContext ruleContext) {
    ObjcCommon.Builder builder = new ObjcCommon.Builder(ruleContext)
        .setIntermediateArtifacts(ObjcRuleClasses.intermediateArtifacts(ruleContext));
    for (Attribute attribute : dependencyAttributes) {
      builder.addDepObjcProviders(
          ruleContext.getPrerequisites(
              attribute.getName(), attribute.getAccessMode(), ObjcProvider.class));
    }
    return builder.build();
  }
}
