// Copyright 2015 The Bazel Authors. All rights reserved.
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

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.OutputGroupInfo;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetBuilder;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * The ResourceApk represents the packaged resources that serve as the basis for the signed and the
 * unsigned APKs.
 */
@Immutable
public final class ResourceApk {
  // TODO(bazel-team): The only fields that are legitimately nullable are javaSrcJar and
  // mainDexProguardConfig. The rest are marked as such due to .fromTransitiveResources().
  // It seems like there should be a better way to do this.
  @Nullable private final Artifact resourceApk; // The .ap_ file
  @Nullable private final Artifact resourceJavaSrcJar; // Source jar containing R.java and friends
  @Nullable private final Artifact resourceJavaClassJar; // Class jar containing R.class files
  private final ResourceDependencies resourceDeps;
  private final AssetDependencies assetDeps;
  /**
   * Validated Android resource information. Will be null when this class is built from transitive
   * resources only, and will be a superset of primaryResources otherwise.
   */
  @Nullable private final ValidatedAndroidData validatedResources;

  private final AndroidResources primaryResources;
  private final AndroidAssets primaryAssets;

  // The non-binary XML version of AndroidManifest.xml
  private final ProcessedAndroidManifest manifest;
  private final Artifact rTxt;
  @Nullable private final Artifact resourceProguardConfig;
  @Nullable private final Artifact mainDexProguardConfig;

  static ResourceApk of(ResourceContainer resourceContainer, ResourceDependencies resourceDeps) {
    return of(resourceContainer, resourceDeps, null, null);
  }

  static ResourceApk of(
      ResourceContainer resourceContainer,
      ResourceDependencies resourceDeps,
      @Nullable Artifact resourceProguardConfig,
      @Nullable Artifact mainDexProguardConfig) {
    return new ResourceApk(
        resourceContainer.getApk(),
        resourceContainer.getJavaSourceJar(),
        resourceContainer.getJavaClassJar(),
        resourceDeps,
        AssetDependencies.empty(),
        resourceContainer,
        resourceContainer.getAndroidResources(),
        resourceContainer.getAndroidAssets(),
        resourceContainer.getProcessedManifest(),
        resourceContainer.getRTxt(),
        resourceProguardConfig,
        mainDexProguardConfig);
  }

  public static ResourceApk of(
      ValidatedAndroidResources resources,
      MergedAndroidAssets assets,
      @Nullable Artifact resourceProguardConfig,
      @Nullable Artifact mainDexProguardConfig) {
    return new ResourceApk(
        resources.getApk(),
        resources.getJavaSourceJar(),
        resources.getJavaClassJar(),
        resources.getResourceDependencies(),
        assets.getAssetDependencies(),
        resources,
        resources,
        assets,
        resources.getProcessedManifest(),
        resources.getRTxt(),
        resourceProguardConfig,
        mainDexProguardConfig);
  }

  private ResourceApk(
      @Nullable Artifact resourceApk,
      @Nullable Artifact resourceJavaSrcJar,
      @Nullable Artifact resourceJavaClassJar,
      ResourceDependencies resourceDeps,
      AssetDependencies assetDeps,
      @Nullable ValidatedAndroidData validatedResources,
      AndroidResources primaryResources,
      AndroidAssets primaryAssets,
      ProcessedAndroidManifest manifest,
      Artifact rTxt,
      @Nullable Artifact resourceProguardConfig,
      @Nullable Artifact mainDexProguardConfig) {
    this.resourceApk = resourceApk;
    this.resourceJavaSrcJar = resourceJavaSrcJar;
    this.resourceJavaClassJar = resourceJavaClassJar;
    this.resourceDeps = resourceDeps;
    this.assetDeps = assetDeps;
    this.validatedResources = validatedResources;
    this.primaryResources = primaryResources;
    this.primaryAssets = primaryAssets;
    this.manifest = manifest;
    this.rTxt = rTxt;
    this.resourceProguardConfig = resourceProguardConfig;
    this.mainDexProguardConfig = mainDexProguardConfig;
  }

  ResourceApk withApk(Artifact apk) {
    return new ResourceApk(
        apk,
        resourceJavaSrcJar,
        resourceJavaClassJar,
        resourceDeps,
        assetDeps,
        validatedResources,
        primaryResources,
        primaryAssets,
        manifest,
        rTxt,
        resourceProguardConfig,
        mainDexProguardConfig);
  }

  public Artifact getArtifact() {
    return resourceApk;
  }

  @Nullable
  public ValidatedAndroidData getValidatedResources() {
    return validatedResources;
  }

  public AndroidResources getPrimaryResources() {
    return primaryResources;
  }

  public AndroidAssets getPrimaryAssets() {
    return primaryAssets;
  }

  public ProcessedAndroidManifest getProcessedManifest() {
    return manifest;
  }

  public Artifact getManifest() {
    return manifest.getManifest();
  }

  public Artifact getRTxt() {
    return rTxt;
  }

  public Artifact getResourceJavaSrcJar() {
    return resourceJavaSrcJar;
  }

  public Artifact getResourceJavaClassJar() {
    return resourceJavaClassJar;
  }

  static ResourceApk fromTransitiveResources(
      ResourceDependencies resourceDeps,
      AssetDependencies assetDeps,
      ProcessedAndroidManifest manifest,
      Artifact rTxt) {
    return new ResourceApk(
        null,
        null,
        null,
        resourceDeps,
        assetDeps,
        null,
        AndroidResources.empty(),
        AndroidAssets.empty(),
        manifest,
        rTxt,
        null,
        null);
  }

  public Artifact getResourceProguardConfig() {
    return resourceProguardConfig;
  }

  public Artifact getMainDexProguardConfig() {
    return mainDexProguardConfig;
  }

  public ResourceDependencies getResourceDependencies() {
    return resourceDeps;
  }

  public AssetDependencies getAssetDependencies() {
    return assetDeps;
  }

  /**
   * Creates an provider from the resources in the ResourceApk.
   *
   * <p>If the ResourceApk was created from transitive resources, the provider will effectively
   * contain the "forwarded" resources: The merged transitive and merged direct dependencies of this
   * library.
   *
   * <p>If the ResourceApk was generated from local resources, that will be the direct dependencies
   * and the rest will be transitive.
   */
  AndroidResourcesInfo toResourceInfo(Label label) {
    if (validatedResources == null) {
      return resourceDeps.toInfo(label, manifest, rTxt);
    }
    return resourceDeps.toInfo(validatedResources);
  }

  // TODO(b/77574966): Stop returning an Optional once we get rid of ResourceContainer and can
  // guarantee that only properly merged assets are passed into this object.
  Optional<AndroidAssetsInfo> toAssetsInfo(Label label) {
    if (primaryAssets instanceof MergedAndroidAssets) {
      MergedAndroidAssets merged = (MergedAndroidAssets) primaryAssets;
      AndroidAssetsInfo assetsInfo = merged.toProvider();
      return Optional.of(assetsInfo);
    } else if (primaryAssets == null) {
      return Optional.of(assetDeps.toInfo(label));
    } else {
      return Optional.empty();
    }
  }

  // TODO(b/77574966): Remove this cast once we get rid of ResourceContainer and can guarantee
  // that only properly merged resources are passed into this object.
  Optional<AndroidManifestInfo> toManifestInfo() {
    if (validatedResources instanceof ValidatedAndroidResources) {
      ValidatedAndroidResources validated = (ValidatedAndroidResources) validatedResources;

      return Optional.of(validated.getStampedManifest().toProvider());
    }

    return Optional.empty();
  }

  public void addToConfiguredTargetBuilder(
      RuleConfiguredTargetBuilder builder,
      Label label,
      boolean includeSkylarkApiProvider,
      boolean isLibrary) {
    AndroidResourcesInfo resourceInfo = toResourceInfo(label);
    builder.addNativeDeclaredProvider(resourceInfo);

    Optional<AndroidManifestInfo> manifestInfo = toManifestInfo();
    manifestInfo.ifPresent(builder::addNativeDeclaredProvider);

    Optional<AndroidAssetsInfo> assetsInfo = toAssetsInfo(label);
    if (assetsInfo.isPresent()) {
      builder.addNativeDeclaredProvider(assetsInfo.get());
      if (assetsInfo.get().getValidationResult() != null) {
        // Asset merging output isn't consumed by anything. Require it to be run by top-level
        // targets
        // so we can validate there are no asset merging conflicts.
        builder.addOutputGroup(
            OutputGroupInfo.HIDDEN_TOP_LEVEL, assetsInfo.get().getValidationResult());
      }
    }

    if (manifestInfo.isPresent() && assetsInfo.isPresent() && !isLibrary) {
      builder.addNativeDeclaredProvider(
          AndroidBinaryDataInfo.of(
              resourceApk,
              resourceProguardConfig,
              resourceInfo,
              assetsInfo.get(),
              manifestInfo.get()));
    }

    if (includeSkylarkApiProvider) {
      builder.addSkylarkTransitiveInfo(
          AndroidSkylarkApiProvider.NAME, new AndroidSkylarkApiProvider(resourceInfo));
    }
  }

  /**
   * Registers an action to process just the transitive resources and assets of a library.
   *
   * <p>Any local resources and assets will be ignored.
   */
  public static ResourceApk processFromTransitiveLibraryData(
      AndroidDataContext dataContext,
      ResourceDependencies resourceDeps,
      AssetDependencies assetDeps,
      StampedAndroidManifest manifest)
      throws InterruptedException {

    return new AndroidResourcesProcessorBuilder()
        .setLibrary(true)
        .setRTxtOut(dataContext.createOutputArtifact(AndroidRuleClasses.ANDROID_R_TXT))
        .setManifestOut(
            dataContext.createOutputArtifact(AndroidRuleClasses.ANDROID_PROCESSED_MANIFEST))
        .setSourceJarOut(
            dataContext.createOutputArtifact(AndroidRuleClasses.ANDROID_JAVA_SOURCE_JAR))
        .setJavaPackage(manifest.getPackage())
        .withResourceDependencies(resourceDeps)
        .withAssetDependencies(assetDeps)
        .setDebug(dataContext.useDebug())
        .setThrowOnResourceConflict(dataContext.getAndroidConfig().throwOnResourceConflict())
        .buildWithoutLocalResources(dataContext, manifest);
  }
}
