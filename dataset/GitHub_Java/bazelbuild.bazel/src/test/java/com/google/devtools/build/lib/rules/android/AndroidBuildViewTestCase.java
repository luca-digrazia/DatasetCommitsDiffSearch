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
package com.google.devtools.build.lib.rules.android;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.devtools.build.lib.actions.util.ActionsTestUtil.getFirstArtifactEndingWith;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.CommandLineExpansionException;
import com.google.devtools.build.lib.actions.util.ActionsTestUtil;
import com.google.devtools.build.lib.analysis.ConfiguredRuleClassProvider;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.analysis.configuredtargets.OutputFileConfiguredTarget;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.devtools.build.lib.rules.java.JavaCompileAction;
import com.google.devtools.build.lib.rules.java.JavaRuleOutputJarsProvider;
import com.google.devtools.build.lib.testutil.TestRuleClassProvider;
import com.google.devtools.build.lib.util.Preconditions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Common methods shared between Android related {@link BuildViewTestCase}s. */
public abstract class AndroidBuildViewTestCase extends BuildViewTestCase {

  @Override
  protected ConfiguredRuleClassProvider getRuleClassProvider() {
    ConfiguredRuleClassProvider.Builder builder = new ConfiguredRuleClassProvider.Builder();
    TestRuleClassProvider.addStandardRules(builder);
    return builder
        // TODO(b/35097211): Remove this once the new testing rules are released.
        .addRuleDefinition(new AndroidDeviceScriptFixtureRule())
        .addRuleDefinition(new AndroidHostServiceFixtureRule())
        .addRuleDefinition(new AndroidInstrumentationRule())
        .addRuleDefinition(new AndroidInstrumentationTestRule())
        .build();
  }

  protected Iterable<Artifact> getNativeLibrariesInApk(ConfiguredTarget target) {
    return Iterables.filter(
        getGeneratingAction(getCompressedUnsignedApk(target)).getInputs(),
        a -> a.getFilename().endsWith(".so"));
  }

  protected Label getGeneratingLabelForArtifact(Artifact artifact) {
    Action generatingAction = getGeneratingAction(artifact);
    return generatingAction != null
        ? getGeneratingAction(artifact).getOwner().getLabel()
        : null;
  }

  protected void assertNativeLibrariesCopiedNotLinked(
      ConfiguredTarget target, String... expectedLibNames) {
    Iterable<Artifact> copiedLibs = getNativeLibrariesInApk(target);
    for (Artifact copiedLib : copiedLibs) {
      assertWithMessage("Native libraries were linked to produce " + copiedLib)
          .that(getGeneratingLabelForArtifact(copiedLib))
          .isNotEqualTo(target.getLabel());
    }
    assertThat(artifactsToStrings(copiedLibs))
        .containsAllIn(ImmutableSet.copyOf(Arrays.asList(expectedLibNames)));
  }

  protected String flagValue(String flag, List<String> args) {
    assertThat(args).contains(flag);
    return args.get(args.indexOf(flag) + 1);
  }

  /**
   * The unsigned APK is created in two actions. The first action adds everything that needs to be
   * unconditionally compressed in the APK. The second action adds everything else, preserving their
   * compression.
   */
  protected Artifact getCompressedUnsignedApk(ConfiguredTarget target) {
    return artifactByPath(
        actionsTestUtil().artifactClosureOf(getFinalUnsignedApk(target)),
        "_unsigned.apk",
        "_unsigned.apk");
  }

  protected Artifact getFinalUnsignedApk(ConfiguredTarget target) {
    return getFirstArtifactEndingWith(
        target.getProvider(FileProvider.class).getFilesToBuild(), "_unsigned.apk");
  }

  protected Artifact getResourceApk(ConfiguredTarget target) {
    Artifact resourceApk =
        getFirstArtifactEndingWith(
            getGeneratingAction(getFinalUnsignedApk(target)).getInputs(), ".ap_");
    assertThat(resourceApk).isNotNull();
    return resourceApk;
  }

  protected void assertProguardUsed(ConfiguredTarget binary) {
    assertWithMessage("proguard.jar is not in the rule output")
        .that(
            actionsTestUtil()
                .getActionForArtifactEndingWith(getFilesToBuild(binary), "_proguard.jar"))
        .isNotNull();
  }

  protected List<String> resourceArguments(ResourceContainer resource)
      throws CommandLineExpansionException {
    return getGeneratingSpawnActionArgs(resource.getApk());
  }

  protected SpawnAction resourceGeneratingAction(ResourceContainer resource) {
    return getGeneratingSpawnAction(resource.getApk());
  }

  protected static ResourceContainer getResourceContainer(ConfiguredTarget target) {
    return getResourceContainer(target, /* transitive= */ false);
  }

  protected static ResourceContainer getResourceContainer(
      ConfiguredTarget target, boolean transitive) {

    Preconditions.checkNotNull(target);
    final AndroidResourcesProvider provider = target.getProvider(AndroidResourcesProvider.class);
    assertThat(provider).named("No android resources exported from the target.").isNotNull();
    return getOnlyElement(
        transitive
            ? provider.getTransitiveAndroidResources()
            : provider.getDirectAndroidResources());
  }

  protected Artifact getResourceClassJar(final ConfiguredTarget target) {
    JavaRuleOutputJarsProvider jarProvider = target.getProvider(JavaRuleOutputJarsProvider.class);
    assertThat(jarProvider).isNotNull();
    return Iterables.find(
            jarProvider.getOutputJars(),
            outputJar -> {
              assertThat(outputJar).isNotNull();
              assertThat(outputJar.getClassJar()).isNotNull();
              return outputJar
                  .getClassJar()
                  .getFilename()
                  .equals(target.getTarget().getName() + "_resources.jar");
            })
        .getClassJar();
  }

  // android resources related tests
  protected void assertPrimaryResourceDirs(List<String> expectedPaths, List<String> actualArgs) {
    assertThat(actualArgs).contains("--primaryData");
    String actualFlagValue = actualArgs.get(actualArgs.indexOf("--primaryData") + 1);
    List<String> actualPaths = null;
    if (actualFlagValue.matches("[^;]*;[^;]*;.*")) {
      actualPaths = Arrays.asList(actualFlagValue.split(";")[0].split("#"));

    } else if (actualFlagValue.matches("[^:]*:[^:]*:.*")) {
      actualPaths = Arrays.asList(actualFlagValue.split(":")[0].split("#"));
    } else {
      fail(String.format("Failed to parse --primaryData: %s", actualFlagValue));
    }
    assertThat(actualPaths).containsAllIn(expectedPaths);
  }

  protected List<String> getDirectDependentResourceDirs(List<String> actualArgs) {
    assertThat(actualArgs).contains("--directData");
    String actualFlagValue = actualArgs.get(actualArgs.indexOf("--directData") + 1);
    return getDependentResourceDirs(actualFlagValue);
  }

  protected List<String> getTransitiveDependentResourceDirs(List<String> actualArgs) {
    assertThat(actualArgs).contains("--data");
    String actualFlagValue = actualArgs.get(actualArgs.indexOf("--data") + 1);
    return getDependentResourceDirs(actualFlagValue);
  }

  private static List<String> getDependentResourceDirs(String actualFlagValue) {
    String separator = null;
    if (actualFlagValue.matches("[^;]*;[^;]*;[^;]*;.*")) {
      separator = ";";
    } else if (actualFlagValue.matches("[^:]*:[^:]*:[^:]*:.*")) {
      separator = ":";
    } else {
      fail(String.format("Failed to parse flag: %s", actualFlagValue));
    }
    ImmutableList.Builder<String> actualPaths = ImmutableList.builder();
    for (String resourceDependency :  actualFlagValue.split(",")) {
      actualPaths.add(resourceDependency.split(separator)[0].split("#"));
    }
    return actualPaths.build();
  }

  protected String execPathEndingWith(Iterable<Artifact> inputs, String suffix) {
    return getFirstArtifactEndingWith(inputs, suffix).getExecPathString();
  }

  @Nullable
  protected AndroidDeployInfo getAndroidDeployInfo(Artifact artifact) throws IOException {
    Action generatingAction = getGeneratingAction(artifact);
    if (generatingAction instanceof AndroidDeployInfoAction) {
      AndroidDeployInfoAction writeAction = (AndroidDeployInfoAction) generatingAction;
      return writeAction.getDeployInfo();
    }
    return null;
  }

  protected List<String> getProcessorNames(String outputTarget) throws Exception {
    OutputFileConfiguredTarget out = (OutputFileConfiguredTarget)
        getFileConfiguredTarget(outputTarget);
    JavaCompileAction compileAction = (JavaCompileAction) getGeneratingAction(out.getArtifact());
    return compileAction.getProcessorNames();
  }

  // Returns an artifact that will be generated when a rule has resources.
  protected static Artifact getResourceArtifact(ConfiguredTarget target) {
    // the last provider is the provider from the target.
    return Iterables.getLast(
            target.getProvider(AndroidResourcesProvider.class).getDirectAndroidResources())
        .getJavaClassJar();
  }

  protected static Set<Artifact> getNonToolInputs(Action action) {
    return Sets.difference(
        ImmutableSet.copyOf(action.getInputs()), ImmutableSet.copyOf(action.getTools()));
  }

  protected void checkDebugKey(String debugKeyFile, boolean hasDebugKeyTarget) throws Exception {
    ConfiguredTarget binary = getConfiguredTarget("//java/com/google/android/hello:b");
    String defaultKeyStoreFile =
        ruleClassProvider.getToolsRepository() + "//tools/android:debug_keystore";

    if (hasDebugKeyTarget) {
      assertWithMessage("Debug key file target missing.")
          .that(checkKeyPresence(binary, debugKeyFile, defaultKeyStoreFile))
          .isTrue();
    } else {
      assertWithMessage("Debug key file is default, although different target specified.")
          .that(checkKeyPresence(binary, defaultKeyStoreFile, debugKeyFile))
          .isTrue();
    }
  }

  private boolean checkKeyPresence(
      ConfiguredTarget binary, String shouldHaveKey, String shouldNotHaveKey) throws Exception {
    boolean hasKey = false;
    boolean doesNotHaveKey = false;

    for (ConfiguredTarget debugKeyTarget : getDirectPrerequisites(binary)) {
      if (debugKeyTarget.getLabel().toString().equals(shouldHaveKey)) {
        hasKey = true;
      }
      if (debugKeyTarget.getLabel().toString().equals(shouldNotHaveKey)) {
        doesNotHaveKey = true;
      }
    }

    return hasKey && !doesNotHaveKey;
  }

  protected String getAndroidJarPath() throws Exception {
    return getAndroidSdk().getAndroidJar().getRootRelativePathString();
  }

  protected Artifact getProguardBinary() throws Exception {
    return getAndroidSdk().getProguard().getExecutable();
  }

  private AndroidSdkProvider getAndroidSdk() {
    Label sdk = targetConfig.getFragment(AndroidConfiguration.class).getSdk();
    return getConfiguredTarget(sdk, targetConfig).getProvider(AndroidSdkProvider.class);
  }

  protected void checkProguardUse(String target, String artifact, boolean expectMapping,
      @Nullable Integer passes,
      String... expectedlibraryJars) throws Exception {
    ConfiguredTarget binary = getConfiguredTarget(target);
    assertProguardUsed(binary);
    assertProguardGenerated(binary);

    Action dexAction = actionsTestUtil().getActionForArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(binary)), "classes.dex");
    Artifact trimmedJar =
        getFirstArtifactEndingWith(dexAction.getInputs(), artifact);
    assertWithMessage("Dex should be built from jar trimmed with Proguard.")
        .that(trimmedJar)
        .isNotNull();
    SpawnAction proguardAction = getGeneratingSpawnAction(trimmedJar);

    if (passes == null) {
      // Verify proguard as a single action.
      Action proguardMap = actionsTestUtil().getActionForArtifactEndingWith(getFilesToBuild(binary),
          "_proguard.map");
      if (expectMapping) {
        assertWithMessage("proguard.map is not in the rule output").that(proguardMap).isNotNull();
      } else {
        assertWithMessage("proguard.map is in the rule output").that(proguardMap).isNull();
      }
      checkProguardLibJars(proguardAction, expectedlibraryJars);
    } else {
      // Verify the multi-stage system generated the correct number of stages.
      Artifact proguardMap = ActionsTestUtil.getFirstArtifactEndingWith(
          proguardAction.getOutputs(), "_proguard.map");
      if (expectMapping) {
        assertWithMessage("proguard.map is not in the rule output").that(proguardMap).isNotNull();
      } else {
        assertWithMessage("proguard.map is in the rule output").that(proguardMap).isNull();
      }

      assertThat(proguardAction.getArguments()).contains("-runtype FINAL");
      checkProguardLibJars(proguardAction, expectedlibraryJars);

      SpawnAction lastStageAction = proguardAction;
      // Verify Obfuscation config.
      for (int pass = passes; pass > 0; pass--) {
        Artifact lastStageOutput = ActionsTestUtil.getFirstArtifactEndingWith(
            lastStageAction.getInputs(),
            "Proguard_optimization_" + pass + ".jar");
        assertWithMessage("Proguard_optimization_" + pass + ".jar is not in rule output")
            .that(lastStageOutput)
            .isNotNull();
        lastStageAction = getGeneratingSpawnAction(lastStageOutput);

        // Verify Optimization pass config.
        assertThat(lastStageAction.getArguments()).contains("-runtype OPTIMIZATION");
        checkProguardLibJars(lastStageAction, expectedlibraryJars);
      }

      Artifact preoptimizationOutput = ActionsTestUtil.getFirstArtifactEndingWith(
          lastStageAction.getInputs(), "proguard_preoptimization.jar");
      assertWithMessage("proguard_preoptimization.jar is not in rule output")
          .that(preoptimizationOutput)
          .isNotNull();
      SpawnAction proOptimization = getGeneratingSpawnAction(preoptimizationOutput);

      // Verify intitial step.
      assertThat(proOptimization.getArguments()).contains("-runtype INITIAL");
      checkProguardLibJars(proOptimization, expectedlibraryJars);
    }
  }

  void checkProguardLibJars(SpawnAction proguardAction, String... expectedlibraryJars)
      throws Exception {
    Collection<String> libraryJars = new ArrayList<>();
    Iterator<String> argsIterator = proguardAction.getArguments().iterator();
    for (String argument = argsIterator.next(); argsIterator.hasNext();
        argument = argsIterator.next()) {
      if (argument.equals("-libraryjars")) {
        libraryJars.add(argsIterator.next());
      }
    }
    assertThat(libraryJars).containsExactly((Object[]) expectedlibraryJars);
  }

  protected void assertProguardGenerated(ConfiguredTarget binary) {
    Action generateProguardAction = actionsTestUtil().getActionForArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(binary)), "_proguard.cfg");
    assertWithMessage("proguard generating action not spawned")
        .that(generateProguardAction)
        .isNotNull();
    Action proguardAction =
        actionsTestUtil().getActionForArtifactEndingWith(getFilesToBuild(binary), "_proguard.jar");
    actionsTestUtil();
    assertWithMessage("Generated config not in inputs to proguard action")
        .that(proguardAction.getInputs()).contains(ActionsTestUtil.getFirstArtifactEndingWith(
        generateProguardAction.getOutputs(), "_proguard.cfg"));
  }

  protected void assertProguardNotUsed(ConfiguredTarget binary) {
    assertWithMessage("proguard.jar is in the rule output")
        .that(
            actionsTestUtil()
                .getActionForArtifactEndingWith(getFilesToBuild(binary), "_proguard.jar"))
        .isNull();
  }
}
