// Copyright 2016 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.bazel.rules.android;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.FilesToRunProvider;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import com.google.devtools.build.lib.packages.BuildFileNotFoundException;
import com.google.devtools.build.lib.packages.util.ResourceLoader;
import com.google.devtools.build.lib.rules.android.AndroidSdkProvider;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AndroidSdkRepositoryFunction}. */
@RunWith(JUnit4.class)
public class AndroidSdkRepositoryTest extends BuildViewTestCase {
  @Before
  public void setup() throws Exception {
    scratch.file(
        "/bazel_tools_workspace/tools/android/android_sdk_repository_template.bzl",
        ResourceLoader.readFromResources("tools/android/android_sdk_repository_template.bzl"));
  }

  private void scratchPlatformsDirectories(int... apiLevels) throws Exception {
    for (int apiLevel : apiLevels) {
      scratch.dir("/sdk/platforms/android-" + apiLevel);
      scratch.file("/sdk/platforms/android-" + apiLevel + "/android.jar");
    }
  }

  private void scratchSystemImagesDirectories(String... pathFragments) throws Exception {
    for (String pathFragment : pathFragments) {
      scratch.dir("/sdk/system-images/" + pathFragment);
      scratch.file("/sdk/system-images/" + pathFragment + "/system.img");
    }
  }

  private void scratchBuildToolsDirectories(String... versions) throws Exception {
    for (String version : versions) {
      scratch.dir("/sdk/build-tools/" + version);
    }
  }

  private void scratchExtrasLibrary(
      String groupId, String artifactId, String version, String packaging) throws Exception {
    scratch.file(
        String.format(
            "/sdk/extras/google/m2repository/%s/%s/%s/%s.pom",
            groupId.replace(".", "/"),
            artifactId,
            version,
            artifactId),
        "<project>",
        "  <groupId>" + groupId + "</groupId>",
        "  <artifactId>" + artifactId + "</artifactId>",
        "  <version>" + version + "</version>",
        "  <packaging>" + packaging + "</packaging>",
        "</project>");
  }

  @Test
  public void testGeneratedAarImport() throws Exception {
    scratchPlatformsDirectories(25);
    scratchBuildToolsDirectories("25.0.0");
    scratchExtrasLibrary("com.google.android", "foo", "1.0.0", "aar");
    FileSystemUtils.appendIsoLatin1(scratch.resolve("WORKSPACE"),
        "local_repository(name = 'bazel_tools', path = '/bazel_tools_workspace')",
        "android_sdk_repository(",
        "    name = 'androidsdk',",
        "    path = '/sdk',",
        ")");
    invalidatePackages();

    ConfiguredTarget aarImportTarget =
        getConfiguredTarget("@androidsdk//com.google.android:foo-1.0.0");
    assertThat(aarImportTarget).isNotNull();
    assertThat(aarImportTarget.getTarget().getAssociatedRule().getRuleClass())
        .isEqualTo("aar_import");
  }

  @Test
  public void testExportsExtrasLibraryArtifacts() throws Exception {
    scratchPlatformsDirectories(25);
    scratchBuildToolsDirectories("25.0.0");
    scratchExtrasLibrary("com.google.android", "foo", "1.0.0", "aar");
    FileSystemUtils.appendIsoLatin1(scratch.resolve("WORKSPACE"),
        "local_repository(name = 'bazel_tools', path = '/bazel_tools_workspace')",
        "android_sdk_repository(",
        "    name = 'androidsdk',",
        "    path = '/sdk',",
        ")");
    invalidatePackages();

    ConfiguredTarget aarTarget = getConfiguredTarget(
        "@androidsdk//:extras/google/m2repository/com/google/android/foo/1.0.0/foo.aar");
    assertThat(aarTarget).isNotNull();
  }

  @Test
  public void testSystemImageDirectoriesAreFound() throws Exception {
    scratchPlatformsDirectories(25);
    scratchBuildToolsDirectories("25.0.0");
    FileSystemUtils.appendIsoLatin1(scratch.resolve("WORKSPACE"),
        "local_repository(name = 'bazel_tools', path = '/bazel_tools_workspace')",
        "android_sdk_repository(",
        "    name = 'androidsdk',",
        "    path = '/sdk',",
        ")");
    scratchSystemImagesDirectories("android-25/default/armeabi-v7a", "android-24/google_apis/x86");
    invalidatePackages();

    ConfiguredTarget android25ArmFilegroup =
        getConfiguredTarget("@androidsdk//:android-25_default_armeabi-v7a_files");
    assertThat(android25ArmFilegroup).isNotNull();
    assertThat(
        artifactsToStrings(
            android25ArmFilegroup.getProvider(FilesToRunProvider.class).getFilesToRun()))
        .containsExactly(
            "src external/androidsdk/system-images/android-25/default/armeabi-v7a/system.img");

    ConfiguredTarget android24X86Filegroup =
        getConfiguredTarget("@androidsdk//:android-24_google_apis_x86_files");
    assertThat(android24X86Filegroup).isNotNull();
    assertThat(
        artifactsToStrings(
            android24X86Filegroup.getProvider(FilesToRunProvider.class).getFilesToRun()))
        .containsExactly(
            "src external/androidsdk/system-images/android-24/google_apis/x86/system.img");
  }

  @Test
  public void testBuildToolsHighestVersionDetection() throws Exception {
    scratchPlatformsDirectories(25);
    scratchBuildToolsDirectories("24.0.3", "25.0.1");
    FileSystemUtils.appendIsoLatin1(
        scratch.resolve("WORKSPACE"),
        "local_repository(name = 'bazel_tools', path = '/bazel_tools_workspace')",
        "android_sdk_repository(",
        "    name = 'androidsdk',",
        "    path = '/sdk',",
        "    api_level = 25,",
        ")");
    invalidatePackages();

    ConfiguredTarget androidSdk = getConfiguredTarget("@androidsdk//:sdk");
    assertThat(androidSdk).isNotNull();
    assertThat(androidSdk.getProvider(AndroidSdkProvider.class).getBuildToolsVersion())
        .isEqualTo("25.0.1");
  }

  @Test
  public void testApiLevelHighestVersionDetection() throws Exception {
    scratchPlatformsDirectories(24, 25, 23);
    scratchBuildToolsDirectories("25.0.1");
    FileSystemUtils.appendIsoLatin1(
        scratch.resolve("WORKSPACE"),
        "local_repository(name = 'bazel_tools', path = '/bazel_tools_workspace')",
        "android_sdk_repository(",
        "    name = 'androidsdk',",
        "    path = '/sdk',",
        "    build_tools_version = '25.0.1',",
        ")");
    invalidatePackages();

    ConfiguredTarget androidSdk = getConfiguredTarget("@androidsdk//:sdk");
    assertThat(androidSdk).isNotNull();
    assertThat(androidSdk.getProvider(AndroidSdkProvider.class).getAndroidJar().getExecPathString())
        .isEqualTo("external/androidsdk/platforms/android-25/android.jar");
  }

  @Test
  public void testMultipleAndroidSdkApiLevels() throws Exception {
    int[] apiLevels = {23, 24, 25};
    scratchPlatformsDirectories(apiLevels);
    scratchBuildToolsDirectories("25.0.1");
    FileSystemUtils.appendIsoLatin1(
        scratch.resolve("WORKSPACE"),
        "local_repository(name = 'bazel_tools', path = '/bazel_tools_workspace')",
        "android_sdk_repository(",
        "    name = 'androidsdk',",
        "    path = '/sdk',",
        "    api_level = 24,",
        "    build_tools_version = '25.0.1',",
        ")");
    invalidatePackages();

    for (int apiLevel : apiLevels) {
      ConfiguredTarget androidSdk = getConfiguredTarget("@androidsdk//:sdk-" + apiLevel);
      assertThat(androidSdk).isNotNull();
      assertThat(
          androidSdk.getProvider(AndroidSdkProvider.class).getAndroidJar().getExecPathString())
          .isEqualTo(
              String.format("external/androidsdk/platforms/android-%d/android.jar", apiLevel));
    }
  }

  @Test
  public void testMissingApiLevel() throws Exception {
    scratchPlatformsDirectories(24);
    scratchBuildToolsDirectories("25.0.1");
    FileSystemUtils.appendIsoLatin1(
        scratch.resolve("WORKSPACE"),
        "local_repository(name = 'bazel_tools', path = '/bazel_tools_workspace')",
        "android_sdk_repository(",
        "    name = 'androidsdk',",
        "    path = '/sdk',",
        "    api_level = 25,",
        "    build_tools_version = '25.0.1',",
        ")");
    invalidatePackages();

    try {
      getTarget("@androidsdk//:files");
      fail("android_sdk_repository should have failed due to missing SDK api level.");
    } catch (BuildFileNotFoundException e) {
      assertThat(e.getMessage())
          .contains(
              "Android SDK api level 25 was requested but it is not installed in the Android SDK "
                  + "at /sdk. The api levels found were [24]. Please choose an available api level "
                  + "or install api level 25 from the Android SDK Manager.");
    }
  }

  // Regression test for https://github.com/bazelbuild/bazel/issues/2739.
  @Test
  public void testFilesInSystemImagesDirectories() throws Exception {
    scratchPlatformsDirectories(24);
    scratchBuildToolsDirectories("25.0.1");
    scratch.file("/sdk/system-images/.DS_Store");
    FileSystemUtils.appendIsoLatin1(scratch.resolve("WORKSPACE"),
        "local_repository(name = 'bazel_tools', path = '/bazel_tools_workspace')",
        "android_sdk_repository(",
        "    name = 'androidsdk',",
        "    path = '/sdk',",
        ")");
    invalidatePackages();

    assertThat(getConfiguredTarget("@androidsdk//:sdk")).isNotNull();
  }
}
