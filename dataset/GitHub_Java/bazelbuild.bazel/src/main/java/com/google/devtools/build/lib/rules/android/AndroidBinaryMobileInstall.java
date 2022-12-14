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

import static com.google.devtools.build.lib.analysis.OutputGroupProvider.INTERNAL_SUFFIX;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ParameterFile.ParameterFileType;
import com.google.devtools.build.lib.analysis.FilesToRunProvider;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetBuilder;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.actions.CustomCommandLine;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.packages.RuleClass.ConfiguredTargetFactory.RuleErrorException;
import com.google.devtools.build.lib.rules.java.DeployArchiveBuilder;
import com.google.devtools.build.lib.rules.java.JavaCompilationArgsProvider;
import com.google.devtools.build.lib.rules.java.JavaSemantics;
import com.google.devtools.build.lib.rules.java.JavaTargetAttributes;
import com.google.devtools.build.lib.rules.java.ProguardHelper;
import com.google.devtools.build.lib.syntax.Type;
import java.util.Map;

/**
 * Encapsulates the logic for creating actions for mobile-install.
 */
public final class AndroidBinaryMobileInstall {

  /**
   * Data class for the resource apks created for mobile-install.
   */
  public static final class MobileInstallResourceApks {
    final ResourceApk incrementalResourceApk;
    final ResourceApk splitResourceApk;

    public MobileInstallResourceApks(
        ResourceApk incrementalResourceApk,
        ResourceApk splitResourceApk) {
      this.incrementalResourceApk = incrementalResourceApk;
      this.splitResourceApk = splitResourceApk;
    }
  }

  static MobileInstallResourceApks createMobileInstallResourceApks(
      RuleContext ruleContext,
      ApplicationManifest applicationManifest,
      ResourceDependencies resourceDeps) throws RuleErrorException, InterruptedException {

    ResourceApk incrementalResourceApk;
    ResourceApk splitResourceApk;

    if (LocalResourceContainer.definesAndroidResources(ruleContext.attributes())) {
      incrementalResourceApk =
          applicationManifest
              .addMobileInstallStubApplication(ruleContext)
              .packIncrementalBinaryWithDataAndResources(
                  ruleContext,
                  ruleContext.getImplicitOutputArtifact(
                      AndroidRuleClasses.ANDROID_INCREMENTAL_RESOURCES_APK),
                  resourceDeps,
                  ruleContext.getTokenizedStringListAttr("nocompress_extensions"),
                  ruleContext.attributes().get("crunch_png", Type.BOOLEAN),
                  ProguardHelper.getProguardConfigArtifact(ruleContext, "incremental"));
      ruleContext.assertNoErrors();

      splitResourceApk =
          applicationManifest
              .createSplitManifest(ruleContext, "android_resources", false)
              .packIncrementalBinaryWithDataAndResources(
                  ruleContext,
                  AndroidBinary.getDxArtifact(ruleContext, "android_resources.ap_"),
                  resourceDeps,
                  ruleContext.getTokenizedStringListAttr("nocompress_extensions"),
                  ruleContext.attributes().get("crunch_png", Type.BOOLEAN),
                  ProguardHelper.getProguardConfigArtifact(ruleContext, "incremental_split"));
      ruleContext.assertNoErrors();

    } else {

      incrementalResourceApk = applicationManifest
          .addMobileInstallStubApplication(ruleContext)
          .packWithResources(
              ruleContext.getImplicitOutputArtifact(
                  AndroidRuleClasses.ANDROID_INCREMENTAL_RESOURCES_APK),
              ruleContext,
              resourceDeps,
              false, /* createSource */
              ProguardHelper.getProguardConfigArtifact(ruleContext, "incremental"),
              null /* mainDexProguardConfig */);
      ruleContext.assertNoErrors();

      splitResourceApk = applicationManifest
          .createSplitManifest(ruleContext, "android_resources", false)
          .packWithResources(AndroidBinary.getDxArtifact(ruleContext, "android_resources.ap_"),
              ruleContext,
              resourceDeps,
              false, /* createSource */
              ProguardHelper.getProguardConfigArtifact(ruleContext, "incremental_split"),
              null /* mainDexProguardConfig */);
      ruleContext.assertNoErrors();
    }

    return new MobileInstallResourceApks(incrementalResourceApk, splitResourceApk);
  }

  static void addMobileInstall(
      RuleContext ruleContext,
      RuleConfiguredTargetBuilder ruleConfiguredTargetBuilder,
      AndroidBinary.DexingOutput dexingOutput,
      JavaSemantics javaSemantics,
      NativeLibs nativeLibs,
      ResourceApk resourceApk,
      MobileInstallResourceApks mobileInstallResourceApks,
      FilesToRunProvider resourceExtractor,
      NestedSet<Artifact> nativeLibsZips,
      Artifact signingKey,
      ImmutableList<Artifact> dataDeps,
      ImmutableList<Artifact> additionalMergedManifests,
      ApplicationManifest applicationManifest)
      throws InterruptedException, RuleErrorException {

    Artifact incrementalApk =
        ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.ANDROID_BINARY_INCREMENTAL_APK);

    Artifact fullDeployMarker =
        ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.FULL_DEPLOY_MARKER);
    Artifact incrementalDeployMarker =
        ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.INCREMENTAL_DEPLOY_MARKER);
    Artifact splitDeployMarker =
        ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.SPLIT_DEPLOY_MARKER);

    Artifact incrementalDexManifest =
        ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.DEX_MANIFEST);
    ruleContext.registerAction(
        new SpawnAction.Builder()
            .useDefaultShellEnvironment()
            .setMnemonic("AndroidDexManifest")
            .setProgressMessage(
                "Generating incremental installation manifest for %s", ruleContext.getLabel())
            .setExecutable(
                ruleContext.getExecutablePrerequisite("$build_incremental_dexmanifest", Mode.HOST))
            .addOutput(incrementalDexManifest)
            .addInputs(dexingOutput.shardDexZips)
            .useParameterFile(ParameterFileType.UNQUOTED)
            .setCommandLine(
                CustomCommandLine.builder()
                    .addExecPath(incrementalDexManifest)
                    .addExecPaths(dexingOutput.shardDexZips)
                    .build())
            .build(ruleContext));

    Artifact stubData = ruleContext.getImplicitOutputArtifact(
        AndroidRuleClasses.MOBILE_INSTALL_STUB_APPLICATION_DATA);
    Artifact stubDex = getStubDex(ruleContext, javaSemantics, false);
    ruleContext.assertNoErrors();

    ApkActionsBuilder incrementalActionsBuilder =
        ApkActionsBuilder.create("incremental apk")
            .setClassesDex(stubDex)
            .addInputZip(mobileInstallResourceApks.incrementalResourceApk.getArtifact())
            .setJavaResourceZip(dexingOutput.javaResourceJar, resourceExtractor)
            .addInputZips(nativeLibsZips)
            .setJavaResourceFile(stubData)
            .setSignedApk(incrementalApk)
            .setSigningKey(signingKey);

    if (!ruleContext.getFragment(AndroidConfiguration.class).useIncrementalNativeLibs()) {
      incrementalActionsBuilder.setNativeLibs(nativeLibs);
    }

    incrementalActionsBuilder.registerActions(ruleContext);

    Artifact argsArtifact =
        ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.MOBILE_INSTALL_ARGS);
    ruleContext.registerAction(
        new WriteAdbArgsAction(ruleContext.getActionOwner(), argsArtifact));

    createInstallAction(
        ruleContext,
        /* incremental = */ false,
        fullDeployMarker,
        argsArtifact,
        incrementalDexManifest,
        mobileInstallResourceApks.incrementalResourceApk.getArtifact(),
        incrementalApk,
        nativeLibs,
        stubData);

    createInstallAction(
        ruleContext,
        /* incremental = */ true,
        incrementalDeployMarker,
        argsArtifact,
        incrementalDexManifest,
        mobileInstallResourceApks.incrementalResourceApk.getArtifact(),
        incrementalApk,
        nativeLibs,
        stubData);

    NestedSetBuilder<Artifact> splitApkSetBuilder = NestedSetBuilder.compileOrder();

    // Put the Android resource APK first so that this split gets installed first.
    //
    // This avoids some logcat spam during installation, because otherwise the Android package
    // manager would complain about references to missing resources in the manifest during the
    // installation of each split (said references would eventually get installed, but it cannot
    // know that in advance)
    Artifact resourceSplitApk = AndroidBinary.getDxArtifact(ruleContext, "android_resources.apk");
    ApkActionsBuilder.create("split Android resource apk")
        .addInputZip(mobileInstallResourceApks.splitResourceApk.getArtifact())
        .setSignedApk(resourceSplitApk)
        .setSigningKey(signingKey)
        .registerActions(ruleContext);
    splitApkSetBuilder.add(resourceSplitApk);

    for (int i = 0; i < dexingOutput.shardDexZips.size(); i++) {
      String splitName = "dex" + (i + 1);
      Artifact splitApkResources = createSplitApkResources(
          ruleContext, applicationManifest, splitName, true);
      Artifact splitApk = AndroidBinary.getDxArtifact(ruleContext, splitName + ".apk");
      ApkActionsBuilder.create("split dex apk " + (i + 1))
          .setClassesDex(dexingOutput.shardDexZips.get(i))
          .addInputZip(splitApkResources)
          .setSignedApk(splitApk)
          .setSigningKey(signingKey)
          .registerActions(ruleContext);
      splitApkSetBuilder.add(splitApk);
    }

    Artifact nativeSplitApkResources = createSplitApkResources(
        ruleContext, applicationManifest, "native", false);
    Artifact nativeSplitApk = AndroidBinary.getDxArtifact(ruleContext, "native.apk");
    ApkActionsBuilder.create("split native apk")
        .addInputZip(nativeSplitApkResources)
        .setNativeLibs(nativeLibs)
        .setSignedApk(nativeSplitApk)
        .setSigningKey(signingKey)
        .registerActions(ruleContext);
    splitApkSetBuilder.add(nativeSplitApk);

    Artifact javaSplitApkResources = createSplitApkResources(
        ruleContext, applicationManifest, "java_resources", false);
    Artifact javaSplitApk = AndroidBinary.getDxArtifact(ruleContext, "java_resources.apk");
    ApkActionsBuilder.create("split Java resource apk")
        .addInputZip(javaSplitApkResources)
        .setJavaResourceZip(dexingOutput.javaResourceJar, resourceExtractor)
        .setSignedApk(javaSplitApk)
        .setSigningKey(signingKey)
        .registerActions(ruleContext);
    splitApkSetBuilder.add(javaSplitApk);

    Artifact splitMainApkResources = AndroidBinary.getDxArtifact(ruleContext, "split_main.ap_");
    ruleContext.registerAction(
        new SpawnAction.Builder()
            .useDefaultShellEnvironment()
            .setMnemonic("AndroidStripResources")
            .setProgressMessage("Stripping resources from split main apk")
            .setExecutable(ruleContext.getExecutablePrerequisite("$strip_resources", Mode.HOST))
            .addInput(resourceApk.getArtifact())
            .addOutput(splitMainApkResources)
            .setCommandLine(
                CustomCommandLine.builder()
                    .addExecPath("--input_resource_apk", resourceApk.getArtifact())
                    .addExecPath("--output_resource_apk", splitMainApkResources)
                    .build())
            .build(ruleContext));

    NestedSet<Artifact> splitApks = splitApkSetBuilder.build();
    Artifact splitMainApk = AndroidBinary.getDxArtifact(ruleContext, "split_main.apk");
    Artifact splitStubDex = getStubDex(ruleContext, javaSemantics, true);
    ruleContext.assertNoErrors();
    ApkActionsBuilder.create("split main apk")
        .setClassesDex(splitStubDex)
        .addInputZip(splitMainApkResources)
        .addInputZips(nativeLibsZips)
        .setSignedApk(splitMainApk)
        .setSigningKey(signingKey)
        .registerActions(ruleContext);
    splitApkSetBuilder.add(splitMainApk);
    NestedSet<Artifact> allSplitApks = splitApkSetBuilder.build();

    createSplitInstallAction(ruleContext, splitDeployMarker, argsArtifact, splitMainApk,
        splitApks, stubData);

    Artifact incrementalDeployInfo = ruleContext.getImplicitOutputArtifact(
        AndroidRuleClasses.DEPLOY_INFO_INCREMENTAL);
    AndroidDeployInfoAction.createDeployInfoAction(ruleContext,
        incrementalDeployInfo,
        resourceApk.getManifest(),
        additionalMergedManifests,
        ImmutableList.<Artifact>of(),
        dataDeps);

    Artifact splitDeployInfo = ruleContext.getImplicitOutputArtifact(
        AndroidRuleClasses.DEPLOY_INFO_SPLIT);
    AndroidDeployInfoAction.createDeployInfoAction(
        ruleContext,
        splitDeployInfo,
        resourceApk.getManifest(),
        additionalMergedManifests,
        ImmutableList.<Artifact>of(),
        dataDeps);

    NestedSet<Artifact> fullInstallOutputGroup = NestedSetBuilder.<Artifact>stableOrder()
        .add(fullDeployMarker)
        .add(incrementalDeployInfo)
        .build();

    NestedSet<Artifact> incrementalInstallOutputGroup = NestedSetBuilder.<Artifact>stableOrder()
        .add(incrementalDeployMarker)
        .add(incrementalDeployInfo)
        .build();

    NestedSet<Artifact> splitInstallOutputGroup = NestedSetBuilder.<Artifact>stableOrder()
        .addTransitive(allSplitApks)
        .add(splitDeployMarker)
        .add(splitDeployInfo)
        .build();

    ruleConfiguredTargetBuilder
        .addOutputGroup("mobile_install_full" + INTERNAL_SUFFIX, fullInstallOutputGroup)
        .addOutputGroup(
            "mobile_install_incremental" + INTERNAL_SUFFIX, incrementalInstallOutputGroup)
        .addOutputGroup("mobile_install_split" + INTERNAL_SUFFIX, splitInstallOutputGroup)
        .addOutputGroup("android_incremental_deploy_info", incrementalDeployInfo);
  }

  private static Artifact getStubDex(
      RuleContext ruleContext, JavaSemantics javaSemantics, boolean split)
      throws InterruptedException {
    String attribute =
        split ? "$incremental_split_stub_application" : "$incremental_stub_application";

    TransitiveInfoCollection dep = ruleContext.getPrerequisite(attribute, Mode.TARGET);
    if (dep == null) {
      ruleContext.attributeError(attribute, "Stub application cannot be found");
      return null;
    }

    JavaCompilationArgsProvider provider = dep.getProvider(JavaCompilationArgsProvider.class);
    if (provider == null) {
      ruleContext.attributeError(attribute, "'" + dep.getLabel() + "' should be a Java target");
      return null;
    }

    JavaTargetAttributes attributes = new JavaTargetAttributes.Builder(javaSemantics)
        .addRuntimeClassPathEntries(provider.getJavaCompilationArgs().getRuntimeJars())
        .build();

    Function<Artifact, Artifact> desugaredJars = Functions.identity();
    if (AndroidCommon.getAndroidConfig(ruleContext).desugarJava8()) {
      desugaredJars =
          AndroidBinary.collectDesugaredJarsFromAttributes(ruleContext, ImmutableList.of(attribute))
              .build()
              .collapseToFunction();
    }
    Artifact stubDeployJar = AndroidBinary.getDxArtifact(ruleContext,
        split ? "split_stub_deploy.jar" : "stub_deploy.jar");
    new DeployArchiveBuilder(javaSemantics, ruleContext)
        .setOutputJar(stubDeployJar)
        .setAttributes(attributes)
        .setDerivedJarFunction(desugaredJars)
        .build();

    Artifact stubDex =
        AndroidBinary.getDxArtifact(
            ruleContext,
            split ? "split_stub_application/classes.dex" : "stub_application/classes.dex");
    AndroidCommon.createDexAction(
        ruleContext,
        stubDeployJar,
        stubDex,
        ImmutableList.<String>of(),
        false,
        null);

    return stubDex;
  }

  private static void createInstallAction(
      RuleContext ruleContext,
      boolean incremental,
      Artifact marker,
      Artifact argsArtifact,
      Artifact dexmanifest,
      Artifact resourceApk,
      Artifact apk,
      NativeLibs nativeLibs,
      Artifact stubDataFile) {

    FilesToRunProvider adb = AndroidSdkProvider.fromRuleContext(ruleContext).getAdb();
    SpawnAction.Builder builder =
        new SpawnAction.Builder()
            .useDefaultShellEnvironment()
            .setExecutable(ruleContext.getExecutablePrerequisite("$incremental_install", Mode.HOST))
            // We cannot know if the user connected a new device, uninstalled the app from the
            // device
            // or did anything strange to it, so we always run this action.
            .executeUnconditionally()
            .setMnemonic("AndroidInstall")
            .setProgressMessage(
                "Installing %s%s", ruleContext.getLabel(), (incremental ? " incrementally" : ""))
            .setExecutionInfo(ImmutableMap.of("local", ""))
            .addTool(adb)
            .addOutput(marker)
            .addInput(dexmanifest)
            .addInput(resourceApk)
            .addInput(stubDataFile)
            .addInput(argsArtifact);

    CustomCommandLine.Builder commandLine =
        CustomCommandLine.builder()
            .addExecPath("--output_marker", marker)
            .addExecPath("--dexmanifest", dexmanifest)
            .addExecPath("--resource_apk", resourceApk)
            .addExecPath("--stub_datafile", stubDataFile)
            .addExecPath("--adb", adb.getExecutable())
            .addExecPath("--flagfile", argsArtifact);

    if (!incremental) {
      builder.addInput(apk);
      commandLine.addExecPath("--apk", apk);
    }

    if (ruleContext.getFragment(AndroidConfiguration.class).useIncrementalNativeLibs()) {
      for (Map.Entry<String, NestedSet<Artifact>> arch : nativeLibs.getMap().entrySet()) {
        for (Artifact lib : arch.getValue()) {
          builder.addInput(lib);
          commandLine.add("--native_lib").addFormatted("%s:%s", arch.getKey(), lib);
        }
      }
    }

    builder.setCommandLine(commandLine.build());
    ruleContext.registerAction(builder.build(ruleContext));
  }

  private static void createSplitInstallAction(RuleContext ruleContext,
      Artifact marker, Artifact argsArtifact, Artifact splitMainApk, NestedSet<Artifact> splitApks,
      Artifact stubDataFile) {
    FilesToRunProvider adb = AndroidSdkProvider.fromRuleContext(ruleContext).getAdb();
    SpawnAction.Builder builder =
        new SpawnAction.Builder()
            .useDefaultShellEnvironment()
            .setExecutable(ruleContext.getExecutablePrerequisite("$incremental_install", Mode.HOST))
            .addTool(adb)
            .executeUnconditionally()
            .setMnemonic("AndroidInstall")
            .setProgressMessage("Installing %s using split apks", ruleContext.getLabel())
            .setExecutionInfo(ImmutableMap.of("local", ""))
            .addTool(adb)
            .addOutput(marker)
            .addInput(stubDataFile)
            .addInput(argsArtifact)
            .addInput(splitMainApk);
    CustomCommandLine.Builder commandLine =
        CustomCommandLine.builder()
            .addExecPath("--output_marker", marker)
            .addExecPath("--stub_datafile", stubDataFile)
            .addExecPath("--adb", adb.getExecutable())
            .addExecPath("--flagfile", argsArtifact)
            .addExecPath("--split_main_apk", splitMainApk);

    for (Artifact splitApk : splitApks) {
      builder.addInput(splitApk);
      commandLine.addExecPath("--split_apk", splitApk);
    }

    builder.setCommandLine(commandLine.build());
    ruleContext.registerAction(builder.build(ruleContext));
  }

  private static Artifact createSplitApkResources(RuleContext ruleContext,
      ApplicationManifest mainManifest, String splitName, boolean hasCode) {
    Artifact splitManifest = mainManifest.createSplitManifest(ruleContext, splitName, hasCode)
        .getManifest();
    Artifact splitResources =
        AndroidBinary.getDxArtifact(ruleContext, "split_" + splitName + ".ap_");
    AndroidSdkProvider sdk = AndroidSdkProvider.fromRuleContext(ruleContext);
    ruleContext.registerAction(
        new SpawnAction.Builder()
            .useDefaultShellEnvironment()
            .setExecutable(sdk.getAapt())
            .setMnemonic("AaptSplitResourceApk")
            .setProgressMessage("Generating resource apk for split %s", splitName)
            .addOutput(splitResources)
            .addInput(splitManifest)
            .addInput(sdk.getAndroidJar())
            .setCommandLine(
                CustomCommandLine.builder()
                    .add("package")
                    .addExecPath("-F", splitResources)
                    .addExecPath("-M", splitManifest)
                    .addExecPath("-I", sdk.getAndroidJar())
                    .build())
            .build(ruleContext));

    return splitResources;
  }
}
