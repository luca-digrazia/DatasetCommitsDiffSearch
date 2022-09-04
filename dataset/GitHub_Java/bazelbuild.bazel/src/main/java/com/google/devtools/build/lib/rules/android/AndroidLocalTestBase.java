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

import static com.google.devtools.build.lib.rules.java.DeployArchiveBuilder.Compression.COMPRESSED;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.MutableActionGraph.ActionConflictException;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.OutputGroupInfo;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetBuilder;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetFactory;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.Runfiles;
import com.google.devtools.build.lib.analysis.RunfilesProvider;
import com.google.devtools.build.lib.analysis.RunfilesSupport;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction.Substitution;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction.Template;
import com.google.devtools.build.lib.analysis.configuredtargets.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.rules.android.AndroidConfiguration.AndroidAaptVersion;
import com.google.devtools.build.lib.rules.java.ClasspathConfiguredFragment;
import com.google.devtools.build.lib.rules.java.DeployArchiveBuilder;
import com.google.devtools.build.lib.rules.java.JavaCommon;
import com.google.devtools.build.lib.rules.java.JavaCompilationArgs.ClasspathType;
import com.google.devtools.build.lib.rules.java.JavaCompilationArgsProvider;
import com.google.devtools.build.lib.rules.java.JavaCompilationArtifacts;
import com.google.devtools.build.lib.rules.java.JavaCompilationHelper;
import com.google.devtools.build.lib.rules.java.JavaConfiguration;
import com.google.devtools.build.lib.rules.java.JavaConfiguration.OneVersionEnforcementLevel;
import com.google.devtools.build.lib.rules.java.JavaHelper;
import com.google.devtools.build.lib.rules.java.JavaInfo;
import com.google.devtools.build.lib.rules.java.JavaPrimaryClassProvider;
import com.google.devtools.build.lib.rules.java.JavaRuleOutputJarsProvider;
import com.google.devtools.build.lib.rules.java.JavaRunfilesProvider;
import com.google.devtools.build.lib.rules.java.JavaRuntimeClasspathProvider;
import com.google.devtools.build.lib.rules.java.JavaSemantics;
import com.google.devtools.build.lib.rules.java.JavaSkylarkApiProvider;
import com.google.devtools.build.lib.rules.java.JavaSourceInfoProvider;
import com.google.devtools.build.lib.rules.java.JavaSourceJarsProvider;
import com.google.devtools.build.lib.rules.java.JavaTargetAttributes;
import com.google.devtools.build.lib.rules.java.JavaToolchainProvider;
import com.google.devtools.build.lib.rules.java.OneVersionCheckActionBuilder;
import com.google.devtools.build.lib.rules.java.ProguardHelper;
import com.google.devtools.build.lib.rules.java.proto.GeneratedExtensionRegistryProvider;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.util.OS;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** A base implementation for the "android_local_test" rule. */
public abstract class AndroidLocalTestBase implements RuleConfiguredTargetFactory {

  @Override
  public ConfiguredTarget create(RuleContext ruleContext)
      throws InterruptedException, RuleErrorException, ActionConflictException {

    ruleContext.checkSrcsSamePackage(true);

    JavaSemantics javaSemantics = createJavaSemantics();
    AndroidSemantics androidSemantics = createAndroidSemantics();
    createAndroidMigrationSemantics().validateRuleContext(ruleContext);
    AndroidLocalTestConfiguration androidLocalTestConfiguration =
        ruleContext.getFragment(AndroidLocalTestConfiguration.class);

    final JavaCommon javaCommon = new JavaCommon(ruleContext, javaSemantics);
    // Use the regular Java javacopts. Enforcing android-compatible Java
    // (-source 7 -target 7 and no TWR) is unnecessary for robolectric tests
    // since they run on a JVM, not an android device.
    JavaTargetAttributes.Builder attributesBuilder = javaCommon.initCommon();

    final AndroidDataContext dataContext = androidSemantics.makeContextForNative(ruleContext);
    final ResourceApk resourceApk;

    if (AndroidResources.decoupleDataProcessing(dataContext)) {
      resourceApk =
          buildResourceApk(
              dataContext,
              AndroidManifest.fromAttributes(ruleContext),
              AndroidResources.from(ruleContext, "resource_files"),
              AndroidAssets.from(ruleContext),
              ResourceDependencies.fromRuleDeps(ruleContext, /* neverlink = */ false),
              AssetDependencies.fromRuleDeps(ruleContext, /* neverlink = */ false),
              ApplicationManifest.getManifestValues(ruleContext),
              AndroidAaptVersion.chooseTargetAaptVersion(ruleContext));
    } else {
      // Create the final merged manifest
      ResourceDependencies resourceDependencies =
          ResourceDependencies.fromRuleDeps(ruleContext, /* neverlink= */ false);

      ApplicationManifest applicationManifest =
          getApplicationManifest(ruleContext, resourceDependencies);

      // Create the final merged R class
      resourceApk =
          applicationManifest.packBinaryWithDataAndResources(
              ruleContext,
              ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.ANDROID_RESOURCES_APK),
              resourceDependencies,
              ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.ANDROID_R_TXT),
              ResourceFilterFactory.fromRuleContextAndAttrs(ruleContext),
              ImmutableList.of(), /* list of uncompressed extensions */
              false, /* crunch png */
              ProguardHelper.getProguardConfigArtifact(ruleContext, ""),
              /* mainDexProguardCfg= */ null,
              /* conditionalKeepRules= */ false,
              ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.ANDROID_PROCESSED_MANIFEST),
              ruleContext.getImplicitOutputArtifact(AndroidRuleClasses.ANDROID_RESOURCES_ZIP),
              DataBinding.isEnabled(ruleContext)
                  ? DataBinding.getLayoutInfoFile(ruleContext)
                  : null,
              null, /* featureOfArtifact */
              null /* featureAfterArtifact */);
    }

    attributesBuilder.addRuntimeClassPathEntry(resourceApk.getResourceJavaClassJar());

    // Exclude the Rs from the library from the runtime classpath.
    NestedSet<Artifact> excludedRuntimeArtifacts = getLibraryResourceJars(ruleContext);
    attributesBuilder.addExcludedArtifacts(excludedRuntimeArtifacts);

    // Create robolectric test_config.properties file
    String name = "_robolectric/" + ruleContext.getRule().getName() + "_test_config.properties";
    Artifact propertiesFile = ruleContext.getGenfilesArtifact(name);

    String resourcesLocation =
        resourceApk.getValidatedResources().getMergedResources().getRunfilesPathString();
    Template template =
        Template.forResource(AndroidLocalTestBase.class, "robolectric_properties_template.txt");
    List<Substitution> substitutions = new ArrayList<>();
    substitutions.add(
        Substitution.of(
            "%android_merged_manifest%", resourceApk.getManifest().getRunfilesPathString()));
    substitutions.add(
        Substitution.of("%android_merged_resources%", "jar:file:" + resourcesLocation + "!/res"));
    substitutions.add(
        Substitution.of("%android_merged_assets%", "jar:file:" + resourcesLocation + "!/assets"));
    substitutions.add(
        Substitution.of(
            "%android_custom_package%", resourceApk.getValidatedResources().getJavaPackage()));

    boolean generateBinaryResources =
        androidLocalTestConfiguration.useAndroidLocalTestBinaryResources();
    if (generateBinaryResources) {
      substitutions.add(
          Substitution.of(
              "%android_resource_apk%", resourceApk.getArtifact().getRunfilesPathString()));
    }

    ruleContext.registerAction(
        new TemplateExpansionAction(
            ruleContext.getActionOwner(),
            propertiesFile,
            template,
            substitutions,
            /* makeExecutable= */ false));
    // Add the properties file to the test jar as a java resource
    attributesBuilder.addResource(
        PathFragment.create("com/android/tools/test_config.properties"), propertiesFile);

    String testClass =
        getAndCheckTestClass(ruleContext, ImmutableList.copyOf(attributesBuilder.getSourceFiles()));
    getAndCheckTestSupport(ruleContext);
    javaSemantics.checkForProtoLibraryAndJavaProtoLibraryOnSameProto(ruleContext, javaCommon);
    if (ruleContext.hasErrors()) {
      return null;
    }

    Artifact srcJar = ruleContext.getImplicitOutputArtifact(JavaSemantics.JAVA_BINARY_SOURCE_JAR);
    JavaSourceJarsProvider.Builder javaSourceJarsProviderBuilder =
        JavaSourceJarsProvider.builder()
            .addSourceJar(srcJar)
            .addAllTransitiveSourceJars(javaCommon.collectTransitiveSourceJars(srcJar));

    Artifact classJar = ruleContext.getImplicitOutputArtifact(JavaSemantics.JAVA_BINARY_CLASS_JAR);
    JavaRuleOutputJarsProvider.Builder javaRuleOutputJarsProviderBuilder =
        JavaRuleOutputJarsProvider.builder()
            .addOutputJar(
                classJar,
                classJar,
                srcJar == null ? ImmutableList.<Artifact>of() : ImmutableList.of(srcJar));

    JavaCompilationArtifacts.Builder javaArtifactsBuilder = new JavaCompilationArtifacts.Builder();
    JavaCompilationHelper helper =
        getJavaCompilationHelperWithDependencies(
            ruleContext, javaSemantics, javaCommon, attributesBuilder);
    Artifact instrumentationMetadata =
        helper.createInstrumentationMetadata(classJar, javaArtifactsBuilder);
    Artifact executable; // the artifact for the rule itself
    if (OS.getCurrent() == OS.WINDOWS
        && ruleContext.getConfiguration().enableWindowsExeLauncher()) {
      executable =
          ruleContext.getImplicitOutputArtifact(ruleContext.getTarget().getName() + ".exe");
    } else {
      executable = ruleContext.createOutputArtifact();
    }
    NestedSetBuilder<Artifact> filesToBuildBuilder =
        NestedSetBuilder.<Artifact>stableOrder().add(classJar).add(executable);

    GeneratedExtensionRegistryProvider generatedExtensionRegistryProvider =
        javaSemantics.createGeneratedExtensionRegistry(
            ruleContext,
            javaCommon,
            filesToBuildBuilder,
            javaArtifactsBuilder,
            javaRuleOutputJarsProviderBuilder,
            javaSourceJarsProviderBuilder);

    String mainClass =
        getMainClass(
            ruleContext,
            javaSemantics,
            helper,
            executable,
            instrumentationMetadata,
            javaArtifactsBuilder,
            attributesBuilder);

    // JavaCompilationHelper.getAttributes() builds the JavaTargetAttributes, after which the
    // JavaTargetAttributes becomes immutable. This is an extra safety check to avoid inconsistent
    // states (i.e. building the JavaTargetAttributes then modifying it again).
    addJavaClassJarToArtifactsBuilder(javaArtifactsBuilder, helper.getAttributes(), classJar);

    // The gensrc jar is created only if the target uses annotation processing. Otherwise,
    // it is null, and the source jar action will not depend on the compile action.
    Artifact manifestProtoOutput = helper.createManifestProtoOutput(classJar);

    Artifact genClassJar = null;
    Artifact genSourceJar = null;
    if (helper.usesAnnotationProcessing()) {
      genClassJar = helper.createGenJar(classJar);
      genSourceJar = helper.createGensrcJar(classJar);
      helper.createGenJarAction(classJar, manifestProtoOutput, genClassJar);
    }
    Artifact outputDepsProtoArtifact =
        helper.createOutputDepsProtoArtifact(classJar, javaArtifactsBuilder);
    javaRuleOutputJarsProviderBuilder.setJdeps(outputDepsProtoArtifact);

    helper.createCompileAction(
        classJar,
        manifestProtoOutput,
        genSourceJar,
        outputDepsProtoArtifact,
        instrumentationMetadata,
        /* nativeHeaderOutput= */ null);
    helper.createSourceJarAction(srcJar, genSourceJar);

    setUpJavaCommon(javaCommon, helper, javaArtifactsBuilder.build());

    Artifact launcher = JavaHelper.launcherArtifactForTarget(javaSemantics, ruleContext);

    String javaExecutable;
    if (javaSemantics.isJavaExecutableSubstitution()) {
      javaExecutable = JavaCommon.getJavaBinSubstitution(ruleContext, launcher);
    } else {
      javaExecutable = JavaCommon.getJavaExecutableForStub(ruleContext, launcher);
    }

    javaSemantics.createStubAction(
        ruleContext,
        javaCommon,
        getJvmFlags(ruleContext, testClass),
        executable,
        mainClass,
        javaExecutable);

    Artifact oneVersionOutputArtifact = null;
    JavaConfiguration javaConfig = ruleContext.getFragment(JavaConfiguration.class);
    OneVersionEnforcementLevel oneVersionEnforcementLevel = javaConfig.oneVersionEnforcementLevel();

    boolean doOneVersionEnforcement =
        oneVersionEnforcementLevel != OneVersionEnforcementLevel.OFF
            && javaConfig.enforceOneVersionOnJavaTests();
    JavaToolchainProvider javaToolchain = JavaToolchainProvider.from(ruleContext);
    if (doOneVersionEnforcement) {
      oneVersionOutputArtifact =
          OneVersionCheckActionBuilder.newBuilder()
              .withEnforcementLevel(oneVersionEnforcementLevel)
              .outputArtifact(
                  ruleContext.getImplicitOutputArtifact(JavaSemantics.JAVA_ONE_VERSION_ARTIFACT))
              .useToolchain(javaToolchain)
              .checkJars(
                  NestedSetBuilder.fromNestedSet(helper.getAttributes().getRuntimeClassPath())
                      .add(classJar)
                      .build())
              .build(ruleContext);
    }

    NestedSet<Artifact> filesToBuild = filesToBuildBuilder.build();

    Runfiles defaultRunfiles =
        collectDefaultRunfiles(
            ruleContext,
            javaCommon,
            filesToBuild,
            resourceApk.getManifest(),
            resourceApk.getResourceJavaClassJar(),
            resourceApk.getValidatedResources().getMergedResources(),
            generateBinaryResources ? resourceApk : null);

    RunfilesSupport runfilesSupport =
        RunfilesSupport.withExecutable(ruleContext, defaultRunfiles, executable);

    Artifact deployJar =
        ruleContext.getImplicitOutputArtifact(JavaSemantics.JAVA_BINARY_DEPLOY_JAR);

    // Create the deploy jar and make it dependent on the runfiles middleman if an executable is
    // created. Do not add the deploy jar to files to build, so we will only build it when it gets
    // requested.
    new DeployArchiveBuilder(javaSemantics, ruleContext)
        .setOutputJar(deployJar)
        .setJavaStartClass(mainClass)
        .setDeployManifestLines(ImmutableList.<String>of())
        .setAttributes(helper.getAttributes())
        .addRuntimeJars(javaCommon.getJavaCompilationArtifacts().getRuntimeJars())
        .setIncludeBuildData(true)
        .setRunfilesMiddleman(runfilesSupport.getRunfilesMiddleman())
        .setCompression(COMPRESSED)
        .setLauncher(launcher)
        .setOneVersionEnforcementLevel(
            doOneVersionEnforcement ? oneVersionEnforcementLevel : OneVersionEnforcementLevel.OFF,
            javaToolchain.getOneVersionWhitelist())
        .build();

    JavaSourceJarsProvider sourceJarsProvider = javaSourceJarsProviderBuilder.build();
    NestedSet<Artifact> transitiveSourceJars = sourceJarsProvider.getTransitiveSourceJars();

    RuleConfiguredTargetBuilder builder = new RuleConfiguredTargetBuilder(ruleContext);

    if (generatedExtensionRegistryProvider != null) {
      builder.addProvider(
          GeneratedExtensionRegistryProvider.class, generatedExtensionRegistryProvider);
    }

    JavaRuleOutputJarsProvider ruleOutputJarsProvider = javaRuleOutputJarsProviderBuilder.build();

    JavaInfo.Builder javaInfoBuilder = JavaInfo.Builder.create();

    javaCommon.addTransitiveInfoProviders(builder, javaInfoBuilder, filesToBuild, classJar);
    javaCommon.addGenJarsProvider(builder, javaInfoBuilder, genClassJar, genSourceJar);

    // Just confirming that there are no aliases being used here.
    AndroidFeatureFlagSetProvider.getAndValidateFlagMapFromRuleContext(ruleContext);

    if (oneVersionOutputArtifact != null) {
      builder.addOutputGroup(OutputGroupInfo.HIDDEN_TOP_LEVEL, oneVersionOutputArtifact);
    }

    NestedSet<Artifact> extraFilesToRun =
        NestedSetBuilder.create(Order.STABLE_ORDER, runfilesSupport.getRunfilesMiddleman());

    JavaInfo javaInfo =
        javaInfoBuilder
            .addProvider(JavaSourceJarsProvider.class, sourceJarsProvider)
            .addProvider(JavaRuleOutputJarsProvider.class, ruleOutputJarsProvider)
            .build();

    return builder
        .setFilesToBuild(filesToBuild)
        .addSkylarkTransitiveInfo(
            JavaSkylarkApiProvider.NAME, JavaSkylarkApiProvider.fromRuleContext())
        .addNativeDeclaredProvider(javaInfo)
        .addProvider(
            RunfilesProvider.class,
            RunfilesProvider.withData(
                defaultRunfiles,
                new Runfiles.Builder(ruleContext.getWorkspaceName())
                    .merge(runfilesSupport)
                    .build()))
        .addFilesToRun(extraFilesToRun)
        .setRunfilesSupport(runfilesSupport, executable)
        .addProvider(
            JavaRuntimeClasspathProvider.class,
            new JavaRuntimeClasspathProvider(javaCommon.getRuntimeClasspath()))
        .addProvider(JavaPrimaryClassProvider.class, new JavaPrimaryClassProvider(testClass))
        .addProvider(
            JavaSourceInfoProvider.class,
            JavaSourceInfoProvider.fromJavaTargetAttributes(helper.getAttributes(), javaSemantics))
        .addOutputGroup(JavaSemantics.SOURCE_JARS_OUTPUT_GROUP, transitiveSourceJars)
        .build();
  }

  /**
   * Returns a merged {@link ApplicationManifest} for the rule. The final merged manifest will be
   * merged into the manifest provided on the rule, or into a placeholder manifest if one is not
   * provided
   *
   * @throws InterruptedException
   * @throws RuleErrorException
   */
  private ApplicationManifest getApplicationManifest(
      RuleContext ruleContext, ResourceDependencies resourceDependencies)
      throws InterruptedException, RuleErrorException {
    ApplicationManifest applicationManifest;

    if (AndroidResources.definesAndroidResources(ruleContext.attributes())) {
      AndroidResources.validateRuleContext(ruleContext);
      ApplicationManifest ruleManifest = ApplicationManifest.renamedFromRule(ruleContext);
      applicationManifest = ruleManifest.mergeWith(ruleContext, resourceDependencies);
    } else {
      // we don't have a manifest, merge like android_library with a stub manifest
      ApplicationManifest dummyManifest = ApplicationManifest.generatedManifest(ruleContext);
      applicationManifest = dummyManifest.mergeWith(ruleContext, resourceDependencies);
    }
    return applicationManifest;
  }

  private static void setUpJavaCommon(
      JavaCommon common,
      JavaCompilationHelper helper,
      JavaCompilationArtifacts javaCompilationArtifacts) {
    common.setJavaCompilationArtifacts(javaCompilationArtifacts);
    common.setClassPathFragment(
        new ClasspathConfiguredFragment(
            common.getJavaCompilationArtifacts(),
            helper.getAttributes(),
            false,
            helper.getBootclasspathOrDefault()));
  }

  private void addJavaClassJarToArtifactsBuilder(
      JavaCompilationArtifacts.Builder javaArtifactsBuilder,
      JavaTargetAttributes attributes,
      Artifact classJar) {
    if (attributes.hasSources() || attributes.hasResources()) {
      // We only want to add a jar to the classpath of a dependent rule if it has content.
      javaArtifactsBuilder.addRuntimeJar(classJar);
    }
  }

  private Runfiles collectDefaultRunfiles(
      RuleContext ruleContext,
      JavaCommon javaCommon,
      NestedSet<Artifact> filesToBuild,
      Artifact manifest,
      Artifact resourcesClassJar,
      Artifact resourcesZip,
      @Nullable ResourceApk resourceApk)
      throws RuleErrorException {

    Runfiles.Builder builder = new Runfiles.Builder(ruleContext.getWorkspaceName());
    builder.addTransitiveArtifacts(filesToBuild);
    builder.addArtifacts(javaCommon.getJavaCompilationArtifacts().getRuntimeJars());

    builder.addRunfiles(ruleContext, RunfilesProvider.DEFAULT_RUNFILES);
    builder.add(ruleContext, JavaRunfilesProvider.TO_RUNFILES);

    ImmutableList<TransitiveInfoCollection> depsForRunfiles =
        ImmutableList.<TransitiveInfoCollection>builder()
            .addAll(ruleContext.getPrerequisites("$robolectric_implicit_classpath", Mode.TARGET))
            .addAll(ruleContext.getPrerequisites("runtime_deps", Mode.TARGET))
            .build();

    Artifact androidAllJarsPropertiesFile = getAndroidAllJarsPropertiesFile(ruleContext);
    if (androidAllJarsPropertiesFile != null) {
      builder.addArtifact(androidAllJarsPropertiesFile);
    }

    builder.addArtifacts(getRuntimeJarsForTargets(getAndCheckTestSupport(ruleContext)));

    builder.addTargets(depsForRunfiles, JavaRunfilesProvider.TO_RUNFILES);
    builder.addTargets(depsForRunfiles, RunfilesProvider.DEFAULT_RUNFILES);

    if (ruleContext.getConfiguration().isCodeCoverageEnabled()) {
      Artifact instrumentedJar = javaCommon.getJavaCompilationArtifacts().getInstrumentedJar();
      if (instrumentedJar != null) {
        builder.addArtifact(instrumentedJar);
      }
    }

    // We assume that the runtime jars will not have conflicting artifacts
    // with the same root relative path
    builder.addTransitiveArtifactsWrappedInStableOrder(javaCommon.getRuntimeClasspath());

    // Add the JDK files if it comes from P4 (see java_stub_template.txt).
    TransitiveInfoCollection javabaseTarget = ruleContext.getPrerequisite(":jvm", Mode.TARGET);

    if (javabaseTarget != null) {
      builder.addTransitiveArtifacts(
          javabaseTarget.getProvider(FileProvider.class).getFilesToBuild());
    }

    builder.addArtifact(manifest);
    builder.addArtifact(resourcesClassJar);
    builder.addArtifact(resourcesZip);
    if (resourceApk != null) {
      builder.addArtifact(resourceApk.getArtifact());
    }

    return builder.build();
  }

  private NestedSet<Artifact> getRuntimeJarsForTargets(TransitiveInfoCollection deps) {
    // The dep may be a simple JAR and not a java rule, hence we can't simply do
    // dep.getProvider(JavaCompilationArgsProvider.class).getRecursiveJavaCompilationArgs(),
    // so we reuse the logic within JavaCompilationArgs to handle both scenarios.
    return JavaCompilationArgsProvider.legacyFromTargets(ImmutableList.of(deps)).getRuntimeJars();
  }

  private static String getAndCheckTestClass(
      RuleContext ruleContext, ImmutableList<Artifact> sourceFiles) {
    String testClass =
        ruleContext.getRule().isAttrDefined("test_class", Type.STRING)
            ? ruleContext.attributes().get("test_class", Type.STRING)
            : "";

    if (testClass.isEmpty()) {
      testClass = JavaCommon.determinePrimaryClass(ruleContext, sourceFiles);
      if (testClass == null) {
        ruleContext.ruleError(
            "cannot determine junit.framework.Test class "
                + "(Found no source file '"
                + ruleContext.getTarget().getName()
                + ".java' and package name doesn't include 'java' or 'javatests'. "
                + "You might want to rename the rule or add a 'test_class' "
                + "attribute.)");
      }
    }
    return testClass;
  }

  static ResourceApk buildResourceApk(
      AndroidDataContext dataContext,
      AndroidManifest manifest,
      AndroidResources resources,
      AndroidAssets assets,
      ResourceDependencies resourceDeps,
      AssetDependencies assetDeps,
      Map<String, String> manifestValues,
      AndroidAaptVersion aaptVersion)
      throws InterruptedException {

    StampedAndroidManifest stamped =
        manifest.mergeWithDeps(
            dataContext.getRuleContext(),
            resourceDeps,
            manifestValues,
            /* useLegacyMerger = */ false);

    return ProcessedAndroidData.processLocalTestDataFrom(
            dataContext,
            stamped,
            manifestValues,
            aaptVersion,
            resources,
            assets,
            resourceDeps,
            assetDeps)
        .generateRClass(dataContext, aaptVersion);
  }

  private static NestedSet<Artifact> getLibraryResourceJars(RuleContext ruleContext) {
    Iterable<AndroidLibraryResourceClassJarProvider> libraryResourceJarProviders =
        AndroidCommon.getTransitivePrerequisites(
            ruleContext, Mode.TARGET, AndroidLibraryResourceClassJarProvider.class);

    NestedSetBuilder<Artifact> libraryResourceJarsBuilder = NestedSetBuilder.naiveLinkOrder();
    for (AndroidLibraryResourceClassJarProvider provider : libraryResourceJarProviders) {
      libraryResourceJarsBuilder.addTransitive(provider.getResourceClassJars());
    }
    return libraryResourceJarsBuilder.build();
  }

  /** Get JavaSemantics */
  protected abstract JavaSemantics createJavaSemantics();

  protected abstract AndroidSemantics createAndroidSemantics();

  /** Get AndroidMigrationSemantics */
  protected abstract AndroidMigrationSemantics createAndroidMigrationSemantics();

  /** Set test and robolectric specific jvm flags */
  protected abstract ImmutableList<String> getJvmFlags(RuleContext ruleContext, String testClass)
      throws RuleErrorException;

  /** Return the testrunner main class */
  protected abstract String getMainClass(
      RuleContext ruleContext,
      JavaSemantics javaSemantics,
      JavaCompilationHelper helper,
      Artifact executable,
      Artifact instrumentationMetadata,
      JavaCompilationArtifacts.Builder javaArtifactsBuilder,
      JavaTargetAttributes.Builder attributesBuilder)
      throws InterruptedException, RuleErrorException;

  /**
   * Add compilation dependencies to the java compilation helper.
   *
   * @throws RuleErrorException
   */
  private JavaCompilationHelper getJavaCompilationHelperWithDependencies(
      RuleContext ruleContext,
      JavaSemantics javaSemantics,
      JavaCommon javaCommon,
      JavaTargetAttributes.Builder javaTargetAttributesBuilder)
      throws RuleErrorException {
    JavaCompilationHelper javaCompilationHelper =
        new JavaCompilationHelper(
            ruleContext, javaSemantics, javaCommon.getJavacOpts(), javaTargetAttributesBuilder);

    if (ruleContext.isAttrDefined("$junit", BuildType.LABEL)) {
      // JUnit jar must be ahead of android runtime jars since these contain stubbed definitions
      // for framework.junit.* classes which Robolectric does not re-write.
      javaCompilationHelper.addLibrariesToAttributes(
          ruleContext.getPrerequisites("$junit", Mode.TARGET));
    }
    // Robolectric jars must be ahead of other potentially conflicting jars
    // (e.g., Android runtime jars) in the classpath to make sure they always take precedence.
    javaCompilationHelper.addLibrariesToAttributes(
        ruleContext.getPrerequisites("$robolectric_implicit_classpath", Mode.TARGET));

    javaCompilationHelper.addLibrariesToAttributes(
        javaCommon.targetsTreatedAsDeps(ClasspathType.COMPILE_ONLY));

    javaCompilationHelper.addLibrariesToAttributes(
        ImmutableList.of(getAndCheckTestSupport(ruleContext)));
    return javaCompilationHelper;
  }

  /** Get the testrunner from the rule */
  protected abstract TransitiveInfoCollection getAndCheckTestSupport(RuleContext ruleContext)
      throws RuleErrorException;

  /** Get the android-all jars properties file from the deps */
  @Nullable
  protected abstract Artifact getAndroidAllJarsPropertiesFile(RuleContext ruleContext)
      throws RuleErrorException;
}
