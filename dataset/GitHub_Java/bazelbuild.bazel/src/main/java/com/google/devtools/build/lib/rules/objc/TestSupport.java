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

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.PrerequisiteArtifacts;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.Runfiles.Builder;
import com.google.devtools.build.lib.analysis.RunfilesProvider;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction.Substitution;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.rules.objc.ObjcProvider.Key;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesProvider;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesProviderImpl;
import com.google.devtools.build.lib.util.FileType;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Support for running XcTests.
 */
public class TestSupport {
  private final RuleContext ruleContext;

  public TestSupport(RuleContext ruleContext) {
    this.ruleContext = ruleContext;
  }

  /**
   * Registers actions to create all files needed in order to actually run the test.
   */
  public TestSupport registerTestRunnerActions() {
    registerTestScriptSubstitutionAction();
    return this;
  }

  /**
   * Returns the script which should be run in order to actually run the tests.
   */
  public Artifact generatedTestScript() {
    return ObjcRuleClasses.artifactByAppendingToBaseName(ruleContext, "_test_script");
  }

  private void registerTestScriptSubstitutionAction() {
    // testIpa is the app actually containing the tests
    Artifact testIpa = testIpa();

    // The substitutions below are common for simulator and lab device.
    ImmutableList.Builder<Substitution> substitutions = new ImmutableList.Builder<Substitution>()
        .add(Substitution.of("%(test_app_ipa)s", testIpa.getRootRelativePathString()))
        .add(Substitution.of("%(test_app_name)s", baseNameWithoutIpa(testIpa)))
        .add(Substitution.of("%(plugin_jars)s", Artifact.joinRootRelativePaths(":", plugins())));

    // xctestIpa is the app bundle being tested
    Optional<Artifact> xctestIpa = xctestIpa();
    if (xctestIpa.isPresent()) {
      substitutions
          .add(Substitution.of("%(xctest_app_ipa)s", xctestIpa.get().getRootRelativePathString()))
          .add(Substitution.of("%(xctest_app_name)s", baseNameWithoutIpa(xctestIpa.get())));
    } else {
      substitutions
          .add(Substitution.of("%(xctest_app_ipa)s", ""))
          .add(Substitution.of("%(xctest_app_name)s", ""));
    }

    Artifact template;
    if (!runWithLabDevice()) {
      substitutions.addAll(substitutionsForSimulator());
      template = ruleContext.getPrerequisiteArtifact("$test_template", Mode.TARGET);
    } else {
      substitutions.addAll(substitutionsForLabDevice());
      template = testTemplateForLabDevice();
    }

    ruleContext.registerAction(new TemplateExpansionAction(ruleContext.getActionOwner(),
        template, generatedTestScript(), substitutions.build(), /*executable=*/true));
  }

  private boolean runWithLabDevice() {
    return iosLabDeviceSubstitutions() != null;
  }

  /**
   * Gets the substitutions for simulator.
   */
  private ImmutableList<Substitution> substitutionsForSimulator() {
    ImmutableList.Builder<Substitution> substitutions = new ImmutableList.Builder<Substitution>()
        .add(Substitution.of("%(iossim_path)s", iossim().getRootRelativePath().getPathString()))
        .add(Substitution.of("%(std_redirect_dylib_path)s",
            stdRedirectDylib().getRootRelativePath().getPathString()))
        .addAll(deviceSubstitutions().getSubstitutionsForTestRunnerScript());

    Optional<Artifact> testRunner = testRunner();
    if (testRunner.isPresent()) {
      substitutions.add(
          Substitution.of("%(testrunner_binary)s", testRunner.get().getRootRelativePathString()));
    }
    return substitutions.build();
  }

  private IosTestSubstitutionProvider deviceSubstitutions() {
    return ruleContext.getPrerequisite(
        "target_device", Mode.TARGET, IosTestSubstitutionProvider.class);
  }

  private Artifact testIpa() {
    return ruleContext.getImplicitOutputArtifact(ReleaseBundlingSupport.IPA);
  }

  private Optional<Artifact> xctestIpa() {
    FileProvider fileProvider =
        ruleContext.getPrerequisite("xctest_app", Mode.TARGET, FileProvider.class);
    if (fileProvider == null) {
      return Optional.absent();
    }
    List<Artifact> files =
        Artifact.filterFiles(fileProvider.getFilesToBuild(), FileType.of(".ipa"));
    if (files.size() == 0) {
      return Optional.absent();
    } else if (files.size() == 1) {
      return Optional.of(Iterables.getOnlyElement(files));
    } else {
      throw new IllegalStateException("Expected 0 or 1 files in xctest_app, got: " + files);
    }
  }

  private Artifact iossim() {
    return ruleContext.getPrerequisiteArtifact("$iossim", Mode.HOST);
  }

  private Artifact stdRedirectDylib() {
    return ruleContext.getPrerequisiteArtifact("$std_redirect_dylib", Mode.HOST);
  }

  /**
   * Gets the binary of the testrunner attribute, if there is one.
   */
  private Optional<Artifact> testRunner() {
    return Optional.fromNullable(ruleContext.getPrerequisiteArtifact("$test_runner", Mode.TARGET));
  }

  /**
   * Gets the substitutions for lab device.
   */
  private ImmutableList<Substitution> substitutionsForLabDevice() {
    return new ImmutableList.Builder<Substitution>()
        .addAll(iosLabDeviceSubstitutions().getSubstitutionsForTestRunnerScript())
        .add(Substitution.of("%(ios_device_arg)s", Joiner.on(" ").join(iosDeviceArgs()))).build();
  }

  /**
   * Gets the test template for lab devices.
   */
  private Artifact testTemplateForLabDevice() {
    return ruleContext
        .getPrerequisite("ios_test_target_device", Mode.TARGET, LabDeviceTemplateProvider.class)
        .getLabDeviceTemplate();
  }

  @Nullable
  private IosTestSubstitutionProvider iosLabDeviceSubstitutions() {
    return ruleContext.getPrerequisite(
        "ios_test_target_device", Mode.TARGET, IosTestSubstitutionProvider.class);
  }

  private List<String> iosDeviceArgs() {
    return ruleContext.attributes().get("ios_device_arg", Type.STRING_LIST);
  }

  /**
   * Adds all files needed to run this test to the passed Runfiles builder.
   *
   * @param objcProvider common information about this rule's attributes and its dependencies
   */
  public TestSupport addRunfiles(Builder runfilesBuilder, ObjcProvider objcProvider) {
    runfilesBuilder
        .addArtifact(testIpa())
        .addArtifacts(xctestIpa().asSet())
        .addArtifact(generatedTestScript())
        .addTransitiveArtifacts(plugins());
    if (!runWithLabDevice()) {
      runfilesBuilder
          .addArtifact(iossim())
          .addArtifact(stdRedirectDylib())
          .addTransitiveArtifacts(deviceRunfiles())
          .addArtifacts(testRunner().asSet());
    } else {
      runfilesBuilder.addTransitiveArtifacts(labDeviceRunfiles());
    }

    if (ruleContext.getConfiguration().isCodeCoverageEnabled()) {
      runfilesBuilder
          .addTransitiveArtifacts(objcProvider.get(ObjcProvider.SOURCE))
          .addTransitiveArtifacts(gcnoFiles(objcProvider));
    }
    return this;
  }

  /**
   * Returns any additional providers that need to be exported to the rule context to the passed
   * builder.
   *
   * @param objcProvider common information about this rule's attributes and its dependencies
   */
  public Map<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider>
      getExtraProviders(ObjcProvider objcProvider) {
    return ImmutableMap.<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider>of(
        InstrumentedFilesProvider.class,
        new InstrumentedFilesProviderImpl(
            instrumentedFiles(objcProvider), gcnoFiles(objcProvider), gcovEnv()));
  }

  /**
   * Returns a map of extra environment variable names to their values used to point to gcov binary,
   * which should be added to the test action environment, if coverage is enabled.
   */
  private Map<String, String> gcovEnv() {
    if (ruleContext.getConfiguration().isCodeCoverageEnabled()) {
      return ImmutableMap.of("COVERAGE_GCOV_PATH",
          ruleContext.getHostPrerequisiteArtifact(":gcov").getExecPathString());
    }
    return ImmutableMap.of();
  }

  /**
   * Returns all GCC coverage notes files available for computing coverage.
   */
  private NestedSet<Artifact> gcnoFiles(ObjcProvider objcProvider) {
    return filesWithXcTestApp(ObjcProvider.GCNO, objcProvider);
  }

  /**
   * Returns all source files from the test (and if present xctest app) which have been
   * instrumented for code coverage.
   */
  private NestedSet<Artifact> instrumentedFiles(ObjcProvider objcProvider) {
    return filesWithXcTestApp(ObjcProvider.INSTRUMENTED_SOURCE, objcProvider);
  }

  private NestedSet<Artifact> filesWithXcTestApp(Key<Artifact> key, ObjcProvider objcProvider) {
    NestedSet<Artifact> underlying = objcProvider.get(key);
    XcTestAppProvider provider = ruleContext.getPrerequisite(
        IosTest.XCTEST_APP, Mode.TARGET, XcTestAppProvider.class);
    if (provider == null) {
      return underlying;
    }

    return NestedSetBuilder.<Artifact>stableOrder()
        .addTransitive(underlying)
        .addTransitive(provider.getObjcProvider().get(key))
        .build();
  }

  /**
   * Jar files for plugins to the test runner. May be empty.
   */
  private NestedSet<Artifact> plugins() {
    return PrerequisiteArtifacts.nestedSet(ruleContext, "plugins", Mode.TARGET);
  }

  /**
   * Runfiles required in order to use the specified target device.
   */
  private NestedSet<Artifact> deviceRunfiles() {
    return ruleContext.getPrerequisite("target_device", Mode.TARGET, RunfilesProvider.class)
        .getDefaultRunfiles().getAllArtifacts();
  }

  /**
   * Runfiles required in order to use the specified target device.
   */
  private NestedSet<Artifact> labDeviceRunfiles() {
    return ruleContext
        .getPrerequisite("ios_test_target_device", Mode.TARGET, RunfilesProvider.class)
        .getDefaultRunfiles().getAllArtifacts();
  }

  /**
   * Adds files which must be built in order to run this test to builder.
   */
  public TestSupport addFilesToBuild(NestedSetBuilder<Artifact> builder) {
    builder.add(testIpa()).addAll(xctestIpa().asSet());
    return this;
  }

  /**
   * Returns the base name of the artifact, with the .ipa stuffix stripped.
   */
  private static String baseNameWithoutIpa(Artifact artifact) {
    String baseName = artifact.getExecPath().getBaseName();
    Preconditions.checkState(baseName.endsWith(".ipa"),
        "%s should end in .ipa but doesn't", baseName);
    return baseName.substring(0, baseName.length() - 4);
  }
}
