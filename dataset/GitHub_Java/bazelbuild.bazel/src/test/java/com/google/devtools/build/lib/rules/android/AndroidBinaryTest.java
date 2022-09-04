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
import static com.google.devtools.build.lib.actions.util.ActionsTestUtil.getFirstArtifactEndingWith;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.util.ActionsTestUtil;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.FilesToRunProvider;
import com.google.devtools.build.lib.analysis.actions.FileWriteAction;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.cmdline.RepositoryName;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.rules.android.AndroidRuleClasses.MultidexMode;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.devtools.build.lib.rules.cpp.CppFileTypes;
import com.google.devtools.build.lib.rules.java.JavaCompilationArgsProvider;
import com.google.devtools.build.lib.rules.java.JavaCompileAction;
import com.google.devtools.build.lib.rules.java.JavaSemantics;
import com.google.devtools.build.lib.testutil.MoreAsserts;
import com.google.devtools.build.lib.util.FileType;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A test for {@link com.google.devtools.build.lib.rules.android.AndroidBinary}.
 */
@RunWith(JUnit4.class)
public class AndroidBinaryTest extends AndroidBuildViewTestCase {

  @Before
  public void createFiles() throws Exception {
    scratch.file("java/android/BUILD",
        "android_binary(name = 'app',",
        "               srcs = ['A.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = glob(['res/**']),",
        "              )");
    scratch.file("java/android/res/values/strings.xml",
        "<resources><string name = 'hello'>Hello Android!</string></resources>");
    scratch.file("java/android/A.java",
        "package android; public class A {};");
  }

  @Test
  public void testAssetsInExternalRepository() throws Exception {
    FileSystemUtils.appendIsoLatin1(
        scratch.resolve("WORKSPACE"), "local_repository(name='r', path='/r')");
    scratch.file("/r/WORKSPACE");
    scratch.file("/r/p/BUILD", "filegroup(name='assets', srcs=['a/b'])");
    scratch.file("/r/p/a/b");
    invalidatePackages();
    scratchConfiguredTarget("java/a", "a",
        "android_binary(",
        "    name = 'a',",
        "    srcs = ['A.java'],",
        "    manifest = 'AndroidManifest.xml',",
        "    assets = ['@r//p:assets'],",
        "    assets_dir = '')");
  }

  @Test
  public void testMultidexModeAndMainDexProguardSpecs() throws Exception {
    checkError("java/a", "a", "only allowed if 'multidex' is set to 'legacy'",
        "android_binary(",
        "    name = 'a',",
        "    srcs = ['A.java'],",
        "    main_dex_proguard_specs = ['foo'])");
  }

  @Test
  public void testMainDexProguardSpecs() throws Exception {
    ConfiguredTarget ct = scratchConfiguredTarget("java/a", "a",
        "android_binary(",
        "    name = 'a',",
        "    srcs = ['A.java'],",
        "    manifest = 'AndroidManifest.xml',",
        "    multidex = 'legacy',",
        "    main_dex_proguard_specs = ['a.spec'])");

    Artifact intermediateJar = artifactByPath(ImmutableList.of(getCompressedUnsignedApk(ct)),
        ".apk", ".dex.zip", ".dex.zip", "main_dex_list.txt", "_intermediate.jar");
    List<String> args = getGeneratingSpawnAction(intermediateJar).getArguments();
    MoreAsserts.assertContainsSublist(args, "-include", "java/a/a.spec");
    assertThat(Joiner.on(" ").join(args)).doesNotContain("mainDexClasses.rules");
  }

  @Test
  public void testNonLegacyNativeDepsDoesNotPolluteDexSharding() throws Exception {
    scratch.file("java/a/BUILD",
        "android_binary(name = 'a',",
        "               manifest = 'AndroidManifest.xml',",
        "               multidex = 'native',",
        "               deps = [':cc'],",
        "               dex_shards = 2)",
        "cc_library(name = 'cc',",
        "           srcs = ['cc.cc'])");

    Artifact jarShard = artifactByPath(
        ImmutableList.of(getCompressedUnsignedApk(getConfiguredTarget("//java/a:a"))),
        ".apk", "classes.dex.zip", "shard1.dex.zip", "shard1.jar");
    Iterable<Artifact> shardInputs = getGeneratingAction(jarShard).getInputs();
    assertThat(getFirstArtifactEndingWith(shardInputs, ".txt")).isNull();
  }

  @Test
  public void testJavaPluginProcessorPath() throws Exception {
    scratch.file("java/test/BUILD",
        "java_library(name = 'plugin_dep',",
        "    srcs = [ 'ProcessorDep.java'])",
        "java_plugin(name = 'plugin',",
        "    srcs = ['AnnotationProcessor.java'],",
        "    processor_class = 'com.google.process.stuff',",
        "    deps = [ ':plugin_dep' ])",
        "android_binary(name = 'to_be_processed',",
        "    manifest = 'AndroidManifest.xml',",
        "    plugins = [':plugin'],",
        "    srcs = ['ToBeProcessed.java'])");
    ConfiguredTarget target = getConfiguredTarget("//java/test:to_be_processed");
    JavaCompileAction javacAction = (JavaCompileAction) getGeneratingAction(
        getBinArtifact("libto_be_processed.jar", target));

    assertThat(javacAction.getProcessorNames()).contains("com.google.process.stuff");
    assertThat(javacAction.getProcessorNames()).hasSize(1);

    assertEquals("libplugin.jar libplugin_dep.jar", ActionsTestUtil.baseNamesOf(
        javacAction.getProcessorpath()));
    assertEquals("ToBeProcessed.java AnnotationProcessor.java ProcessorDep.java",
        actionsTestUtil().predecessorClosureOf(getFilesToBuild(target),
            JavaSemantics.JAVA_SOURCE));
  }

  // Same test as above, enabling the plugin through the command line.
  @Test
  public void testPluginCommandLine() throws Exception {
    scratch.file("java/test/BUILD",
        "java_library(name = 'plugin_dep',",
        "    srcs = [ 'ProcessorDep.java'])",
        "java_plugin(name = 'plugin',",
        "    srcs = ['AnnotationProcessor.java'],",
        "    processor_class = 'com.google.process.stuff',",
        "    deps = [ ':plugin_dep' ])",
        "android_binary(name = 'to_be_processed',",
        "    manifest = 'AndroidManifest.xml',",
        "    srcs = ['ToBeProcessed.java'])");

    useConfiguration("--plugin=//java/test:plugin");
    ConfiguredTarget target = getConfiguredTarget("//java/test:to_be_processed");
    JavaCompileAction javacAction = (JavaCompileAction) getGeneratingAction(
        getBinArtifact("libto_be_processed.jar", target));

    assertThat(javacAction.getProcessorNames()).contains("com.google.process.stuff");
    assertThat(javacAction.getProcessorNames()).hasSize(1);
    assertEquals("libplugin.jar libplugin_dep.jar",
        ActionsTestUtil.baseNamesOf(javacAction.getProcessorpath()));
    assertEquals("ToBeProcessed.java AnnotationProcessor.java ProcessorDep.java",
        actionsTestUtil().predecessorClosureOf(getFilesToBuild(target),
            JavaSemantics.JAVA_SOURCE));
  }

  @Test
  public void testInvalidPlugin() throws Exception {
    checkError("java/test", "lib",
        // error:
        getErrorMsgMisplacedRules("plugins", "android_binary",
            "//java/test:lib", "java_library", "//java/test:not_a_plugin"),
        // BUILD file:
        "java_library(name = 'not_a_plugin',",
        "    srcs = [ 'NotAPlugin.java'])",
        "android_binary(name = 'lib',",
        "    plugins = [':not_a_plugin'],",
        "    manifest = 'AndroidManifest.xml',",
        "    srcs = ['Lib.java'])");
  }

  @Test
  public void testBaselineCoverageArtifacts() throws Exception {
    useConfiguration("--collect_code_coverage");
    ConfiguredTarget target = scratchConfiguredTarget("java/com/google/a", "bin",
        "android_binary(name='bin', srcs=['Main.java'], manifest='AndroidManifest.xml')");

    assertThat(baselineCoverageArtifactBasenames(target)).containsExactly("Main.java");
  }

  @Test
  public void testSameSoFromMultipleDeps() throws Exception {
    scratch.file("java/d/BUILD",
        "genrule(name='genrule', srcs=[], outs=['genrule.so'], cmd='')",
        "cc_library(name='cc1', srcs=[':genrule.so'])",
        "cc_library(name='cc2', srcs=[':genrule.so'])",
        "android_binary(name='ab', deps=[':cc1', ':cc2'], manifest='AndroidManifest.xml')");
    getConfiguredTarget("//java/d:ab");
  }

  @Test
  public void testSimpleBinary_desugarJava8() throws Exception {
    useConfiguration("--experimental_desugar_for_android");
    ConfiguredTarget binary = getConfiguredTarget("//java/android:app");

    SpawnAction action = (SpawnAction) actionsTestUtil().getActionForArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(binary)), "_deploy.jar");
    assertThat(ActionsTestUtil.baseArtifactNames(action.getInputs()))
        .contains("libapp.jar_desugared.jar");
    assertThat(ActionsTestUtil.baseArtifactNames(action.getInputs()))
        .doesNotContain("libapp.jar");
  }

  // regression test for #3169099
  @Test
  public void testBinarySrcs() throws Exception {
    scratch.file("java/srcs/a.foo", "foo");
    scratch.file("java/srcs/BUILD",
        "android_binary(name = 'valid', manifest = 'AndroidManifest.xml', "
            + "srcs = ['a.java', 'b.srcjar', ':gvalid', ':gmix'])",
        "android_binary(name = 'invalid', manifest = 'AndroidManifest.xml', "
            + "srcs = ['a.foo', ':ginvalid'])",
        "android_binary(name = 'mix', manifest = 'AndroidManifest.xml', "
            + "srcs = ['a.java', 'a.foo'])",
        "genrule(name = 'gvalid', srcs = ['a.java'], outs = ['b.java'], cmd = '')",
        "genrule(name = 'ginvalid', srcs = ['a.java'], outs = ['b.foo'], cmd = '')",
        "genrule(name = 'gmix', srcs = ['a.java'], outs = ['c.java', 'c.foo'], cmd = '')"
    );
    assertSrcsValidityForRuleType("//java/srcs", "android_binary", ".java or .srcjar");
  }

  // regression test for #3169095
  @Test
  public void testXmbInSrcs_NotPermittedButDoesNotThrow() throws Exception {
    reporter.removeHandler(failFastHandler);
    scratchConfiguredTarget("java/xmb", "a",
        "android_binary(name = 'a', manifest = 'AndroidManifest.xml', srcs = ['a.xmb'])");
    // We expect there to be an error here because a.xmb is not a valid src,
    // and more importantly, no exception to have been thrown.
    assertContainsEvent("in srcs attribute of android_binary rule //java/xmb:a: "
        + "target '//java/xmb:a.xmb' does not exist");
  }

  @Test
  public void testNativeLibraryBasenameCollision() throws Exception {
    reporter.removeHandler(failFastHandler); // expect errors
    scratch.file("java/android/common/BUILD",
        "cc_library(name = 'libcommon_armeabi',",
        "           srcs = ['armeabi/native.so'],)");
    scratch.file("java/android/app/BUILD",
        "cc_library(name = 'libnative',",
        "           srcs = ['native.so'],)",
        "android_binary(name = 'b',",
        "               srcs = ['A.java'],",
        "               deps = [':libnative', '//java/android/common:libcommon_armeabi'],",
        "               manifest = 'AndroidManifest.xml',",
        "              )");
    getConfiguredTarget("//java/android/app:b");
    assertContainsEvent("Each library in the transitive closure must have a unique basename to "
        + "avoid name collisions when packaged into an apk, but two libraries have the basename "
        + "'native.so': java/android/common/armeabi/native.so and java/android/app/native.so");
  }

  private void setupNativeLibrariesForLinking() throws Exception {
    scratch.file("java/android/common/BUILD",
        "cc_library(name = 'common_native',",
        "           srcs = ['common.cc'],)",
        "android_library(name = 'common',",
        "                deps = [':common_native'],)");
    scratch.file("java/android/app/BUILD",
        "cc_library(name = 'native',",
        "           srcs = ['native.cc'],)",
        "android_binary(name = 'auto',",
        "               srcs = ['A.java'],",
        "               deps = [':native', '//java/android/common:common'],",
        "               manifest = 'AndroidManifest.xml',",
        "              )",
        "android_binary(name = 'off',",
        "               srcs = ['A.java'],",
        "               deps = [':native', '//java/android/common:common'],",
        "               manifest = 'AndroidManifest.xml',",
        "               legacy_native_support = 0,",
        "              )");
  }

  private void assertNativeLibraryLinked(ConfiguredTarget target, String... srcNames) {
    Artifact linkedLib = getOnlyElement(getNativeLibrariesInApk(target));
    assertEquals(
        "lib" + target.getLabel().toPathFragment().getBaseName() + ".so", linkedLib.getFilename());
    assertFalse(linkedLib.isSourceArtifact());
    assertEquals("Native libraries were not linked to produce " + linkedLib,
        target.getLabel(), getGeneratingLabelForArtifact(linkedLib));
    assertThat(artifactsToStrings(actionsTestUtil().artifactClosureOf(linkedLib)))
        .containsAllIn(ImmutableSet.copyOf(Arrays.asList(srcNames)));
  }

  @Test
  public void testNativeLibrary_LinksLibrariesWhenCodeIsPresent() throws Exception {
    setupNativeLibrariesForLinking();
    assertNativeLibraryLinked(getConfiguredTarget("//java/android/app:auto"),
        "src java/android/common/common.cc", "src java/android/app/native.cc");
    assertNativeLibraryLinked(getConfiguredTarget("//java/android/app:off"),
        "src java/android/common/common.cc", "src java/android/app/native.cc");
  }

  @Test
  public void testNativeLibrary_CopiesLibrariesDespiteExtraLayersOfIndirection() throws Exception {
    scratch.file("java/android/app/BUILD",
        "cc_library(name = 'native_dep',",
        "           srcs = ['dep.so'])",
        "cc_library(name = 'native',",
        "           srcs = ['native_prebuilt.so'],",
        "           deps = [':native_dep'])",
        "cc_library(name = 'native_wrapper',",
        "           deps = [':native'])",
        "android_binary(name = 'app',",
        "               srcs = ['A.java'],",
        "               deps = [':native_wrapper'],",
        "               manifest = 'AndroidManifest.xml',",
        "              )");
    assertNativeLibrariesCopiedNotLinked(getConfiguredTarget("//java/android/app:app"),
        "src java/android/app/dep.so", "src java/android/app/native_prebuilt.so");
  }

  @Test
  public void testNativeLibrary_CopiesLibrariesWrappedInCcLibraryWithSameName() throws Exception {
    scratch.file("java/android/app/BUILD",
        "cc_library(name = 'native',",
        "           srcs = ['libnative.so'])",
        "android_binary(name = 'app',",
        "               srcs = ['A.java'],",
        "               deps = [':native'],",
        "               manifest = 'AndroidManifest.xml',",
        "              )");
    assertNativeLibrariesCopiedNotLinked(getConfiguredTarget("//java/android/app:app"),
        "src java/android/app/libnative.so");
  }

  @Test
  public void testNativeLibrary_LinksWhenPrebuiltArchiveIsSupplied() throws Exception {
    scratch.file("java/android/app/BUILD",
        "cc_library(name = 'native_dep',",
        "           srcs = ['dep.lo'])",
        "cc_library(name = 'native',",
        "           srcs = ['native_prebuilt.a'],",
        "           deps = [':native_dep'])",
        "cc_library(name = 'native_wrapper',",
        "           deps = [':native'])",
        "android_binary(name = 'app',",
        "               srcs = ['A.java'],",
        "               deps = [':native_wrapper'],",
        "               manifest = 'AndroidManifest.xml',",
        "              )");
    assertNativeLibraryLinked(getConfiguredTarget("//java/android/app:app"),
        "src java/android/app/native_prebuilt.a");
  }

  @Test
  public void testNativeLibrary_CopiesFullLibrariesInIfsoMode() throws Exception {
    useConfiguration("--interface_shared_objects");
    scratch.file("java/android/app/BUILD",
      "cc_library(name = 'native_dep',",
      "           srcs = ['dep.so'])",
      "cc_library(name = 'native',",
      "           srcs = ['native.cc', 'native_prebuilt.so'],",
      "           deps = [':native_dep'])",
      "android_binary(name = 'app',",
      "               srcs = ['A.java'],",
      "               deps = [':native'],",
      "               manifest = 'AndroidManifest.xml',",
      "              )");
      ConfiguredTarget app = getConfiguredTarget("//java/android/app:app");
      Iterable<Artifact> nativeLibraries = getNativeLibrariesInApk(app);
      assertThat(artifactsToStrings(nativeLibraries))
          .containsAllOf(
              "src java/android/app/native_prebuilt.so",
              "src java/android/app/dep.so");
      assertThat(FileType.filter(nativeLibraries, CppFileTypes.INTERFACE_SHARED_LIBRARY))
          .isEmpty();
  }

  @Test
  public void testIncrementalDexingWithAidlRuntimeDependency() throws Exception {
    useConfiguration(
        "--incremental_dexing", "--incremental_dexing_binary_types=all", "--android_sdk=//sdk:sdk");

    scratch.file("sdk/BUILD",
        "android_sdk(",
        "    name = 'sdk',",
        "    aapt = 'aapt',",
        "    adb = 'adb',",
        "    aidl = 'aidl',",
        "    android_jar = 'android.jar',",
        "    annotations_jar = 'annotations_jar',",
        "    apksigner = 'apksigner',",
        "    dx = 'dx',",
        "    framework_aidl = 'framework_aidl',",
        // TODO(b/35630874): set aidl_lib in MockAndroidSupport once b/35630874 is fixed
        "    aidl_lib = ':aidl_runtime',",
        "    main_dex_classes = 'main_dex_classes',",
        "    main_dex_list_creator = 'main_dex_list_creator',",
        "    proguard = 'proguard',",
        "    shrinked_android_jar = 'shrinked_android_jar',",
        "    zipalign = 'zipalign',",
        "    resource_extractor = 'resource_extractor'",
        ")",
        "java_library(",
        "    name = 'aidl_runtime',",
        "    srcs = ['AidlRuntime.java'],",
        ")");
    scratch.file(
        "java/com/google/android/BUILD",
        "android_library(",
        "  name = 'dep',",
        "  srcs = ['dep.java'],",
        "  manifest = 'AndroidManifest.xml',",
        "  resource_files = glob(['res/**']),",
        "  idl_srcs = ['dep.aidl'],",
        ")",
        "android_binary(",
        "  name = 'top',",
        "  srcs = ['foo.java', 'bar.srcjar'],",
        "  manifest = 'AndroidManifest.xml',",
        "  deps = [':dep'],",
        ")");

    ConfiguredTarget topTarget = getConfiguredTarget("//java/com/google/android:top");
    assertNoEvents();

    Action shardAction =
        getGeneratingAction(getBinArtifact("_dx/top/classes.jar", topTarget));
    for (String basename : ActionsTestUtil.baseArtifactNames(shardAction.getInputs())) {
      // all jars are converted to dex archives
      assertThat(!basename.contains(".jar") || basename.endsWith(".jar.dex.zip"))
          .named(basename).isTrue();
    }
    assertThat(ActionsTestUtil.baseArtifactNames(shardAction.getInputs()))
        .contains("libaidl_runtime.jar.dex.zip");
  }

  /** Regression for b/35630874. */
  @Test
  public void testIncrementalDexingWithoutAidlRuntimeDependency() throws Exception {
    useConfiguration(
        "--incremental_dexing", "--incremental_dexing_binary_types=all", "--android_sdk=//sdk:sdk");

    scratch.file("sdk/BUILD",
        "android_sdk(",
        "    name = 'sdk',",
        "    aapt = 'aapt',",
        "    adb = 'adb',",
        "    aidl = 'aidl',",
        "    android_jar = 'android.jar',",
        "    annotations_jar = 'annotations_jar',",
        "    apksigner = 'apksigner',",
        "    dx = 'dx',",
        "    framework_aidl = 'framework_aidl',",
        // TODO(b/35630874): set aidl_lib in MockAndroidSupport once b/35630874 is fixed
        "    aidl_lib = ':aidl_runtime',",
        "    main_dex_classes = 'main_dex_classes',",
        "    main_dex_list_creator = 'main_dex_list_creator',",
        "    proguard = 'proguard',",
        "    shrinked_android_jar = 'shrinked_android_jar',",
        "    zipalign = 'zipalign',",
        "    resource_extractor = 'resource_extractor'",
        ")",
        "java_library(",
        "    name = 'aidl_runtime',",
        "    srcs = ['AidlRuntime.java'],",
        ")");
    scratch.file(
        "java/com/google/android/BUILD",
        "android_library(",
        "  name = 'dep',",
        "  srcs = ['dep.java'],",
        "  manifest = 'AndroidManifest.xml',",
        "  resource_files = glob(['res/**']),",
        ")",
        "android_binary(",
        "  name = 'top',",
        "  srcs = ['foo.java', 'bar.srcjar'],",
        "  manifest = 'AndroidManifest.xml',",
        "  deps = [':dep'],",
        ")");

    ConfiguredTarget topTarget = getConfiguredTarget("//java/com/google/android:top");
    assertNoEvents();

    Action shardAction =
        getGeneratingAction(getBinArtifact("_dx/top/classes.jar", topTarget));
    for (String basename : ActionsTestUtil.baseArtifactNames(shardAction.getInputs())) {
      // all jars are converted to dex archives
      assertThat(!basename.contains(".jar") || basename.endsWith(".jar.dex.zip"))
          .named(basename).isTrue();
    }
    assertThat(ActionsTestUtil.baseArtifactNames(shardAction.getInputs()))
        .doesNotContain("libaidl_runtime.jar.dex.zip");
  }

  /** Regression test for http://b/33173461. */
  @Test
  public void testIncrementalDexingUsesDexArchives_binaryDependingOnAliasTarget()
      throws Exception {
    useConfiguration("--incremental_dexing", "--incremental_dexing_binary_types=all",
        "--experimental_desugar_for_android");
    scratch.file(
        "java/com/google/android/BUILD",
        "android_library(",
        "  name = 'dep',",
        "  srcs = ['dep.java'],",
        "  resource_files = glob(['res/**']),",
        "  manifest = 'AndroidManifest.xml',",
        ")",
        "alias(",
        "  name = 'alt',",
        "  actual = ':dep',",
        ")",
        "android_binary(",
        "  name = 'top',",
        "  srcs = ['foo.java', 'bar.srcjar'],",
        "  multidex = 'native',",
        "  manifest = 'AndroidManifest.xml',",
        "  deps = [':alt',],",
        ")");

    ConfiguredTarget topTarget = getConfiguredTarget("//java/com/google/android:top");
    assertNoEvents();

    Action shardAction =
        getGeneratingAction(getBinArtifact("_dx/top/classes.jar", topTarget));
    for (Artifact input : shardAction.getInputs()) {
      String basename = input.getFilename();
      // all jars are converted to dex archives
      assertThat(!basename.contains(".jar") || basename.endsWith(".jar.dex.zip"))
          .named(basename).isTrue();
      // all jars are desugared before being converted
      if (basename.endsWith(".jar.dex.zip")) {
        assertThat(getGeneratingAction(input).getPrimaryInput().getFilename())
            .isEqualTo(basename.substring(0, basename.length() - ".jar.dex.zip".length())
                + ".jar_desugared.jar");
      }
    }
    // Make sure exactly the dex archives generated for top and dependents appear.  We also *don't*
    // want neverlink and unused_dep to appear, and to be safe we do so by explicitly enumerating
    // *all* expected input dex archives.
    assertThat(
            Iterables.filter(
                ActionsTestUtil.baseArtifactNames(shardAction.getInputs()),
                Predicates.containsPattern("\\.jar")))
        .containsExactly(
            // top's dex archives
            "libtop.jar.dex.zip",
            "top_resources.jar.dex.zip",
            // dep's dex archives
            "libdep.jar.dex.zip",
            "dep_resources.jar.dex.zip");
  }

  @Test
  public void testIncrementalDexingDisabledWithBlacklistedDexopts() throws Exception {
    // Even if we mark a dx flag as supported, incremental dexing isn't used with blacklisted
    // dexopts (unless incremental_dexing attribute is set, which a different test covers)
    useConfiguration("--incremental_dexing",
        "--non_incremental_per_target_dexopts=--no-locals",
        "--dexopts_supported_in_incremental_dexing=--no-locals");
    scratch.file(
        "java/com/google/android/BUILD",
        "android_binary(",
        "  name = 'top',",
        "  srcs = ['foo.java', 'bar.srcjar'],",
        "  manifest = 'AndroidManifest.xml',",
        "  dexopts = ['--no-locals'],",
        "  dex_shards = 2,",
        "  multidex = 'native',",
        ")");

    ConfiguredTarget topTarget = getConfiguredTarget("//java/com/google/android:top");
    assertNoEvents();
    Action shardAction = getGeneratingAction(getBinArtifact("_dx/top/shard1.jar", topTarget));
    assertThat(
        Iterables.filter(
            ActionsTestUtil.baseArtifactNames(shardAction.getInputs()),
            Predicates.containsPattern("\\.jar\\.dex\\.zip")))
        .isEmpty(); // no dex archives are used
  }

  @Test
  public void testIncrementalDexingDisabledWithProguard() throws Exception {
    useConfiguration("--incremental_dexing", "--incremental_dexing_binary_types=all");
    scratch.file(
        "java/com/google/android/BUILD",
        "android_binary(",
        "  name = 'top',",
        "  srcs = ['foo.java', 'bar.srcjar'],",
        "  manifest = 'AndroidManifest.xml',",
        "  proguard_specs = ['proguard.cfg'],",
        ")");

    ConfiguredTarget topTarget = getConfiguredTarget("//java/com/google/android:top");
    assertNoEvents();
    Action dexAction = getGeneratingAction(getBinArtifact("_dx/top/classes.dex", topTarget));
    assertThat(
        Iterables.filter(
            ActionsTestUtil.baseArtifactNames(dexAction.getInputs()),
            Predicates.containsPattern("\\.jar")))
        .containsExactly("top_proguard.jar", "dx_binary.jar"); // proguard output is used directly
  }

  @Test
  public void testIncrementalDexing_attributeIncompatibleWithProguard() throws Exception {
    checkError("java/com/google/android", "top", "target cannot be incrementally dexed",
        "android_binary(",
        "  name = 'top',",
        "  srcs = ['foo.java', 'bar.srcjar'],",
        "  manifest = 'AndroidManifest.xml',",
        "  proguard_specs = ['proguard.cfg'],",
        "  incremental_dexing = 1,",
        ")");
  }

  @Test
  public void testV1SigningMethod() throws Exception {
    actualSignerToolTests("v1", "true", "false");
  }

  @Test
  public void testV2SigningMethod() throws Exception {
    actualSignerToolTests("v2", "false", "true");
  }

  @Test
  public void testV1V2SigningMethod() throws Exception {
    actualSignerToolTests("v1_v2", "true", "true");
  }

  private void actualSignerToolTests(String apkSigningMethod, String signV1, String signV2)
      throws Exception {
    scratch.file("sdk/BUILD",
        "android_sdk(",
        "    name = 'sdk',",
        "    aapt = 'aapt',",
        "    adb = 'adb',",
        "    aidl = 'aidl',",
        "    android_jar = 'android.jar',",
        "    annotations_jar = 'annotations_jar',",
        "    apksigner = 'apksigner',",
        "    dx = 'dx',",
        "    framework_aidl = 'framework_aidl',",
        "    main_dex_classes = 'main_dex_classes',",
        "    main_dex_list_creator = 'main_dex_list_creator',",
        "    proguard = 'proguard',",
        "    shrinked_android_jar = 'shrinked_android_jar',",
        "    zipalign = 'zipalign',",
        "    resource_extractor = 'resource_extractor')");
    scratch.file("java/com/google/android/hello/BUILD",
        "android_binary(name = 'hello',",
        "               srcs = ['Foo.java'],",
        "               manifest = 'AndroidManifest.xml',)");
    useConfiguration("--android_sdk=//sdk:sdk", "--apk_signing_method=" + apkSigningMethod);
    ConfiguredTarget binary = getConfiguredTarget("//java/com/google/android/hello:hello");

    Set<Artifact> artifacts = actionsTestUtil().artifactClosureOf(getFilesToBuild(binary));
    assertThat(getFirstArtifactEndingWith(artifacts, "signed_hello.apk")).isNull();
    SpawnAction unsignedApkAction = (SpawnAction) actionsTestUtil()
        .getActionForArtifactEndingWith(artifacts, "/hello_unsigned.apk");
    assertTrue(
        Iterables.any(
            unsignedApkAction.getInputs(),
            new Predicate<Artifact>() {
              @Override
              public boolean apply(Artifact artifact) {
                return artifact.getFilename().equals("SingleJar_deploy.jar");
              }
            }));
    SpawnAction compressedUnsignedApkAction = (SpawnAction) actionsTestUtil()
        .getActionForArtifactEndingWith(artifacts, "compressed_hello_unsigned.apk");
    assertTrue(
        Iterables.any(
            compressedUnsignedApkAction.getInputs(),
            new Predicate<Artifact>() {
              @Override
              public boolean apply(Artifact artifact) {
                return artifact.getFilename().equals("SingleJar_deploy.jar");
              }
            }));
    SpawnAction zipalignAction = (SpawnAction) actionsTestUtil()
        .getActionForArtifactEndingWith(artifacts, "zipaligned_hello.apk");
    assertThat(zipalignAction.getCommandFilename()).endsWith("sdk/zipalign");
    SpawnAction apkAction = (SpawnAction) actionsTestUtil()
        .getActionForArtifactEndingWith(artifacts, "hello.apk");
    assertThat(apkAction.getCommandFilename()).endsWith("sdk/apksigner");

    assertThat(flagValue("--v1-signing-enabled", apkAction.getArguments())).isEqualTo(signV1);
    assertThat(flagValue("--v2-signing-enabled", apkAction.getArguments())).isEqualTo(signV2);
  }

  @Test
  public void testResourceShrinkingAction() throws Exception {
    scratch.file("java/com/google/android/hello/BUILD",
        "android_binary(name = 'hello',",
        "               srcs = ['Foo.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               inline_constants = 0,",
        "               resource_files = ['res/values/strings.xml'],",
        "               shrink_resources = 1,",
        "               proguard_specs = ['proguard-spec.pro'],)");

    ConfiguredTarget binary = getConfiguredTarget("//java/com/google/android/hello:hello");

    Set<Artifact> artifacts = actionsTestUtil().artifactClosureOf(getFilesToBuild(binary));

    assertThat(artifacts).containsAllOf(
        getFirstArtifactEndingWith(artifacts, "resource_files.zip"),
        getFirstArtifactEndingWith(artifacts, "proguard.jar"),
        getFirstArtifactEndingWith(artifacts, "shrunk.ap_"));

    final SpawnAction resourceProcessing =
        getGeneratingSpawnAction(getFirstArtifactEndingWith(artifacts, "resource_files.zip"));
    List<String> processingArgs = resourceProcessing.getArguments();

    assertThat(flagValue("--resourcesOutput", processingArgs))
        .endsWith("hello_files/resource_files.zip");

    final SpawnAction proguard =
        getGeneratingSpawnAction(getFirstArtifactEndingWith(artifacts, "proguard.jar"));
    assertThat(proguard).isNotNull();
    List<String> proguardArgs = proguard.getArguments();

    assertThat(flagValue("-outjars", proguardArgs)).endsWith("hello_proguard.jar");

    final SpawnAction resourceShrinking =
        getGeneratingSpawnAction(getFirstArtifactEndingWith(artifacts, "shrunk.ap_"));
    assertThat(resourceShrinking).isNotNull();

    List<String> shrinkingArgs = resourceShrinking.getArguments();
    assertThat(flagValue("--resources", shrinkingArgs))
        .isEqualTo(flagValue("--resourcesOutput", processingArgs));
    assertThat(flagValue("--shrunkJar", shrinkingArgs))
        .isEqualTo(flagValue("-outjars", proguardArgs));
    assertThat(flagValue("--proguardMapping", shrinkingArgs))
        .isEqualTo(flagValue("-printmapping", proguardArgs));
    assertThat(flagValue("--rTxt", shrinkingArgs))
        .isEqualTo(flagValue("--rOutput", processingArgs));
    assertThat(flagValue("--primaryManifest", shrinkingArgs))
        .isEqualTo(flagValue("--manifestOutput", processingArgs));
  }

  @Test
  public void testResourceShrinking_RequiresProguard() throws Exception {
    scratch.file("java/com/google/android/hello/BUILD",
        "android_binary(name = 'hello',",
        "               srcs = ['Foo.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               inline_constants = 0,",
        "               resource_files = ['res/values/strings.xml'],",
        "               shrink_resources = 1,)");

    ConfiguredTarget binary = getConfiguredTarget("//java/com/google/android/hello:hello");

    Set<Artifact> artifacts = actionsTestUtil().artifactClosureOf(getFilesToBuild(binary));

    assertThat(artifacts).containsNoneOf(
        getFirstArtifactEndingWith(artifacts, "shrunk.jar"),
        getFirstArtifactEndingWith(artifacts, "shrunk.ap_"));
  }

  @Test
  public void testProguardExtraOutputs() throws Exception {
    scratch.file(
        "java/com/google/android/hello/BUILD",
        "android_binary(name = 'b',",
        "               srcs = ['HelloApp.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               proguard_specs = ['proguard-spec.pro'])");
    ConfiguredTarget output = getConfiguredTarget("//java/com/google/android/hello:b");

    // Checks that ProGuard is called with the appropriate options.
    SpawnAction action =
        (SpawnAction)
            actionsTestUtil()
                .getActionForArtifactEndingWith(getFilesToBuild(output), "_proguard.jar");

    // Assert that the ProGuard executable set in the android_sdk rule appeared in the command-line
    // of the SpawnAction that generated the _proguard.jar.
    assertTrue(
        Iterables.any(
            action.getArguments(),
            new Predicate<String>() {
              @Override
              public boolean apply(String s) {
                return s.endsWith("ProGuard");
              }
            }));
    assertThat(action.getArguments())
        .containsAllOf(
            "-injars",
            execPathEndingWith(action.getInputs(), "b_deploy.jar"),
            "-printseeds",
            execPathEndingWith(action.getOutputs(), "b_proguard.seeds"),
            "-printusage",
            execPathEndingWith(action.getOutputs(), "b_proguard.usage"))
        .inOrder();

    // Checks that the output files are produced.
    assertProguardUsed(output);
    assertNotNull(getBinArtifact("b_proguard.usage", output));
    assertNotNull(getBinArtifact("b_proguard.seeds", output));
  }

  @Test
  public void testProGuardExecutableMatchesConfiguration() throws Exception {
    scratch.file("java/com/google/devtools/build/jkrunchy/BUILD",
        "package(default_visibility=['//visibility:public'])",
        "java_binary(name = 'jkrunchy',",
        "            srcs = glob(['*.java']),",
        "            main_class = 'com.google.devtools.build.jkrunchy.JKrunchyMain')");

    useConfiguration("--proguard_top=//java/com/google/devtools/build/jkrunchy:jkrunchy");

    scratch.file("java/com/google/android/hello/BUILD",
        "android_binary(name = 'b',",
        "               srcs = ['HelloApp.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               proguard_specs = ['proguard-spec.pro'])");

    ConfiguredTarget output = getConfiguredTarget("//java/com/google/android/hello:b_proguard.jar");
    assertProguardUsed(output);

    SpawnAction proguardAction = (SpawnAction) actionsTestUtil().getActionForArtifactEndingWith(
        getFilesToBuild(output), "_proguard.jar");
    Artifact jkrunchyExecutable =
        getHostConfiguredTarget("//java/com/google/devtools/build/jkrunchy")
            .getProvider(FilesToRunProvider.class)
            .getExecutable();
    assertEquals("ProGuard implementation was not correctly taken from the configuration",
        jkrunchyExecutable.getExecPathString(),
        proguardAction.getCommandFilename());
  }

  @Test
  public void testNeverlinkTransitivity() throws Exception {
    scratch.file("java/com/google/android/neversayneveragain/BUILD",
        "android_library(name = 'l1',",
        "                srcs = ['l1.java'])",
        "android_library(name = 'l2',",
        "                srcs = ['l2.java'],",
        "                deps = [':l1'],",
        "                neverlink = 1)",
        "android_library(name = 'l3',",
        "                srcs = ['l3.java'],",
        "                deps = [':l2'])",
        "android_library(name = 'l4',",
        "                srcs = ['l4.java'],",
        "                deps = [':l1'])",
        "android_binary(name = 'b1',",
        "               srcs = ['b1.java'],",
        "               deps = [':l2'],",
        "               manifest = 'AndroidManifest.xml')",
        "android_binary(name = 'b2',",
        "               srcs = ['b2.java'],",
        "               deps = [':l3'],",
        "               manifest = 'AndroidManifest.xml')",
        "android_binary(name = 'b3',",
        "               srcs = ['b3.java'],",
        "               deps = [':l3', ':l4'],",
        "               manifest = 'AndroidManifest.xml')");
    ConfiguredTarget b1 = getConfiguredTarget("//java/com/google/android/neversayneveragain:b1");
    Action b1DeployAction = actionsTestUtil().getActionForArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(b1)), "b1_deploy.jar");
    List<String> b1Inputs = ActionsTestUtil.prettyArtifactNames(b1DeployAction.getInputs());

    assertThat(b1Inputs).containsNoneOf("java/com/google/android/neversayneveragain/libl1.jar",
        "java/com/google/android/neversayneveragain/libl2.jar",
        "java/com/google/android/neversayneveragain/libl3.jar",
        "java/com/google/android/neversayneveragain/libl4.jar");
    assertThat(b1Inputs).contains(
        "java/com/google/android/neversayneveragain/libb1.jar");

    ConfiguredTarget b2 = getConfiguredTarget("//java/com/google/android/neversayneveragain:b2");
    Action b2DeployAction = actionsTestUtil().getActionForArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(b2)), "b2_deploy.jar");
    List<String> b2Inputs = ActionsTestUtil.prettyArtifactNames(b2DeployAction.getInputs());

    assertThat(b2Inputs).containsNoneOf(
        "java/com/google/android/neversayneveragain/libl1.jar",
        "java/com/google/android/neversayneveragain/libl2.jar",
        "java/com/google/android/neversayneveragain/libl4.jar");
    assertThat(b2Inputs).containsAllOf(
        "java/com/google/android/neversayneveragain/libl3.jar",
        "java/com/google/android/neversayneveragain/libb2.jar");

    ConfiguredTarget b3 = getConfiguredTarget("//java/com/google/android/neversayneveragain:b3");
    Action b3DeployAction = actionsTestUtil().getActionForArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(b3)), "b3_deploy.jar");
    List<String> b3Inputs = ActionsTestUtil.prettyArtifactNames(b3DeployAction.getInputs());

    assertThat(b3Inputs).containsAllOf("java/com/google/android/neversayneveragain/libl1.jar",
        "java/com/google/android/neversayneveragain/libl3.jar",
        "java/com/google/android/neversayneveragain/libl4.jar",
        "java/com/google/android/neversayneveragain/libb3.jar");
    assertThat(b3Inputs).doesNotContain("java/com/google/android/neversayneveragain/libl2.jar");
  }

  @Test
  public void testDexopts() throws Exception {
    checkDexopts("[ '--opt1', '--opt2' ]", ImmutableList.of("--opt1", "--opt2"));
  }

  @Test
  public void testDexoptsTokenization() throws Exception {
    checkDexopts("[ '--opt1', '--opt2 tokenized' ]",
        ImmutableList.of("--opt1", "--opt2", "tokenized"));
  }

  @Test
  public void testDexoptsMakeVariableSubstitution() throws Exception {
    checkDexopts("[ '--opt1', '$(COMPILATION_MODE)' ]", ImmutableList.of("--opt1", "fastbuild"));
  }

  private void checkDexopts(String dexopts, List<String> expectedArgs) throws Exception {
    scratch.file("java/com/google/android/BUILD",
        "android_binary(name = 'b',",
        "    srcs = ['dummy1.java'],",
        "    dexopts = " + dexopts + ",",
        "    manifest = 'AndroidManifest.xml')");

    // Include arguments that are always included.
    List<String> fixedArgs = ImmutableList.of("--num-threads=5");
    expectedArgs = new ImmutableList.Builder<String>()
        .addAll(fixedArgs).addAll(expectedArgs).build();

    // Ensure that the args that immediately follow "--dex" match the expectation.
    ConfiguredTarget binary = getConfiguredTarget("//java/com/google/android:b");
    SpawnAction dexAction = (SpawnAction) actionsTestUtil().getActionForArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(binary)), "classes.dex");
    List<String> args = dexAction.getArguments();
    int start = args.indexOf("--dex") + 1;
    assertThat(start).isNotEqualTo(0);
    int end = Math.min(args.size(), start + expectedArgs.size());
    assertEquals(expectedArgs, args.subList(start, end));
  }

  @Test
  public void testDexMainListOpts() throws Exception {
    checkDexMainListOpts("[ '--opt1', '--opt2' ]", "--opt1", "--opt2");
  }

  @Test
  public void testDexMainListOptsTokenization() throws Exception {
    checkDexMainListOpts("[ '--opt1', '--opt2 tokenized' ]", "--opt1", "--opt2", "tokenized");
  }

  @Test
  public void testDexMainListOptsMakeVariableSubstitution() throws Exception {
    checkDexMainListOpts("[ '--opt1', '$(COMPILATION_MODE)' ]", "--opt1", "fastbuild");
  }

  private void checkDexMainListOpts(String mainDexListOpts, String... expectedArgs)
      throws Exception {
    scratch.file("java/com/google/android/BUILD",
        "android_binary(name = 'b',",
        "    srcs = ['dummy1.java'],",
        "    multidex = \"legacy\",",
        "    main_dex_list_opts = " + mainDexListOpts + ",",
        "    manifest = 'AndroidManifest.xml')");

    // Ensure that the args that immediately follow the main class in the shell command
    // match the expectation.
    ConfiguredTarget binary = getConfiguredTarget("//java/com/google/android:b");
    SpawnAction mainDexListAction = (SpawnAction) actionsTestUtil().getActionForArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(binary)), "main_dex_list.txt");
    List<String> args = mainDexListAction.getArguments();
    // args: [ "bash", "-c", "java -cp dx.jar main opts other" ]
    MoreAsserts.assertContainsSublist(args, expectedArgs);
  }

  @Test
  public void testResourceConfigurationFilters() throws Exception {
    scratch.file("java/com/google/android/BUILD",
        "android_binary(name = 'b',",
        "    srcs = ['dummy1.java'],",
        "    manifest = 'AndroidManifest.xml',",
        "    resource_configuration_filters = [ 'en', 'fr'],)");

    // Ensure that the args are present
    ConfiguredTarget binary = getConfiguredTarget("//java/com/google/android:b");
    List<String> args = resourceArguments(getResourceContainer(binary));
    assertThat(flagValue("--resourceConfigs", args)).contains("en,fr");
  }

  @Test
  public void testFilteredResourcesInvalidFilter() throws Exception {
    String badQualifier = "invalid-qualifier";

    useConfiguration("--experimental_android_resource_filtering_method", "filter_in_analysis");

    checkError(
        "java/r/android",
        "r",
        badQualifier,
        "android_binary(name = 'r',",
        "  manifest = 'AndroidManifest.xml',",
        "  resource_files = ['res/values/foo.xml'],",
        "  resource_configuration_filters = ['" + badQualifier + "'])");
  }

  @Test
  public void testFilteredResourcesInvalidResourceDir() throws Exception {
    String badQualifierDir = "values-invalid-qualifier";

    useConfiguration("--experimental_android_resource_filtering_method", "filter_in_execution");

    checkError(
        "java/r/android",
        "r",
        badQualifierDir,
        "android_binary(name = 'r',",
        "  manifest = 'AndroidManifest.xml',",
        "  resource_files = ['res/" + badQualifierDir + "/foo.xml'],",
        "  prefilter_resources = 1,",
        "  resource_configuration_filters = ['en'])");
  }

  @Test
  public void testFilteredResourcesFilteringDisabled() throws Exception {
    List<String> resources =
        ImmutableList.of("res/values/foo.xml", "res/values-en/foo.xml", "res/values-fr/foo.xml");
    String dir = "java/r/android";

    useConfiguration("--experimental_android_resource_filtering_method", "filter_in_execution");

    ConfiguredTarget binary =
        scratchConfiguredTarget(
            dir,
            "r",
            "android_binary(name = 'r',",
            "  manifest = 'AndroidManifest.xml',",
            "  resource_configuration_filters = ['', 'en, es, '],",
            "  densities = ['hdpi, , ', 'xhdpi'],",
            "  resource_files = ['" + Joiner.on("', '").join(resources) + "'])");
    ResourceContainer directResources = getResourceContainer(binary, /* transitive= */ false);

    // Validate that the AndroidResourceProvider for this binary contains all values.
    assertThat(resourceContentsPaths(dir, directResources)).containsExactlyElementsIn(resources);

    // Validate that the input to resource processing contains all values.
    assertThat(resourceInputPaths(dir, directResources)).containsAllIn(resources);

    // Validate that the filters are correctly passed to the resource processing action
    // This includes trimming whitespace and ignoring empty filters.
    assertThat(resourceGeneratingAction(directResources).getArguments()).contains("en,es");
    assertThat(resourceGeneratingAction(directResources).getArguments()).contains("hdpi,xhdpi");
  }

  @Test
  public void testFilteredResourcesFilteringNotSpecified() throws Exception {
    // TODO(asteinb): Once prefiltering is run by default, remove this test and remove the
    // prefilter_resources flag from tests that currently explicitly specify to filter
    List<String> resources =
        ImmutableList.of("res/values/foo.xml", "res/values-en/foo.xml", "res/values-fr/foo.xml");
    String dir = "java/r/android";

    ConfiguredTarget binary =
        scratchConfiguredTarget(
            dir,
            "r",
            "android_binary(name = 'r',",
            "  manifest = 'AndroidManifest.xml',",
            "  resource_configuration_filters = ['en'],",
            "  resource_files = ['" + Joiner.on("', '").join(resources) + "'])");

    ResourceContainer directResources = getResourceContainer(binary, /* transitive= */ false);

    // Validate that the AndroidResourceProvider for this binary contains all values.
    assertThat(resourceContentsPaths(dir, directResources)).containsExactlyElementsIn(resources);

    // Validate that the input to resource processing contains all values.
    assertThat(resourceInputPaths(dir, directResources)).containsAllIn(resources);
  }

  @Test
  public void testFilteredResourcesSimple() throws Exception {
    testDirectResourceFiltering(
        "en",
        /* unexpectedQualifiers= */ ImmutableList.of("fr"),
        /* expectedQualifiers= */ ImmutableList.of("en"));
  }

  @Test
  public void testFilteredResourcesNoopFilter() throws Exception {
    testDirectResourceFiltering(
        /* filters= */ "",
        /* unexpectedQualifiers= */ ImmutableList.<String>of(),
        /* expectedQualifiers= */ ImmutableList.of("en", "fr"));
  }

  @Test
  public void testFilteredResourcesMultipleFilters() throws Exception {
    testDirectResourceFiltering(
        "en,es",
        /* unexpectedQualifiers= */ ImmutableList.of("fr"),
        /* expectedQualifiers= */ ImmutableList.of("en", "es"));
  }

  @Test
  public void testFilteredResourcesMultipleFiltersWithWhitespace() throws Exception {
    testDirectResourceFiltering(
        " en , es ",
        /* unexpectedQualifiers= */ ImmutableList.of("fr"),
        /* expectedQualifiers= */ ImmutableList.of("en", "es"));
  }

  @Test
  public void testFilteredResourcesMultipleFilterStrings() throws Exception {
    testDirectResourceFiltering(
        "en', 'es",
        /* unexpectedQualifiers= */ ImmutableList.of("fr"),
        /* expectedQualifiers= */ ImmutableList.of("en", "es"));
  }

  @Test
  public void testFilteredResourcesLocaleWithoutRegion() throws Exception {
    testDirectResourceFiltering(
        "en",
        /* unexpectedQualifiers= */ ImmutableList.of("fr-rCA"),
        /* expectedQualifiers= */ ImmutableList.of("en-rCA", "en-rUS", "en"));
  }

  @Test
  public void testFilteredResourcesLocaleWithRegion() throws Exception {
    testDirectResourceFiltering(
        "en-rUS",
        /* unexpectedQualifiers= */ ImmutableList.of("en-rGB"),
        /* expectedQualifiers= */ ImmutableList.of("en-rUS", "en"));
  }

  @Test
  public void testFilteredResourcesOldAaptLocale() throws Exception {
    testDirectResourceFiltering(
        "en_US,fr_CA",
        /* unexepectedQualifiers= */ ImmutableList.of("en-rCA"),
        /* expectedQualifiers = */ ImmutableList.of("en-rUS", "fr-rCA"));
  }

  @Test
  public void testFilteredResourcesOldAaptLocaleOtherQualifiers() throws Exception {
    testDirectResourceFiltering(
        "mcc310-en_US-ldrtl,mcc311-mnc312-fr_CA",
        /* unexepectedQualifiers= */ ImmutableList.of("en-rCA", "mcc312", "mcc311-mnc311"),
        /* expectedQualifiers = */ ImmutableList.of("en-rUS", "fr-rCA", "mcc310", "mcc311-mnc312"));
  }

  @Test
  public void testFilteredResourcesSmallestScreenWidth() throws Exception {
    testDirectResourceFiltering(
        "sw600dp",
        /* unexpectedQualifiers= */ ImmutableList.of("sw700dp"),
        /* expectedQualifiers= */ ImmutableList.of("sw500dp", "sw600dp"));
  }

  @Test
  public void testFilteredResourcesScreenWidth() throws Exception {
    testDirectResourceFiltering(
        "w600dp",
        /* unexpectedQualifiers= */ ImmutableList.of("w700dp"),
        /* expectedQualifiers= */ ImmutableList.of("w500dp", "w600dp"));
  }

  @Test
  public void testFilteredResourcesScreenHeight() throws Exception {
    testDirectResourceFiltering(
        "h600dp",
        /* unexpectedQualifiers= */ ImmutableList.of("h700dp"),
        /* expectedQualifiers= */ ImmutableList.of("h500dp", "h600dp"));
  }

  @Test
  public void testFilteredResourcesScreenSize() throws Exception {
    testDirectResourceFiltering(
        "normal",
        /* unexpectedQualifiers= */ ImmutableList.of("large", "xlarge"),
        /* expectedQualifiers= */ ImmutableList.of("small", "normal"));
  }

  /** Tests that filtering on density is ignored to match aapt behavior. */
  @Test
  public void testFilteredResourcesDensity() throws Exception {

    testDirectResourceFiltering(
        "hdpi",
        /* unexpectedQualifiers= */ ImmutableList.<String>of(),
        /* expectedQualifiers= */ ImmutableList.of("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi"));
  }

  /** Tests that filtering on API version is ignored to match aapt behavior. */
  @Test
  public void testFilteredResourcesApiVersion() throws Exception {
    testDirectResourceFiltering(
        "v4",
        /* unexpectedQualifiers= */ ImmutableList.<String>of(),
        /* expectedQualifiers= */ ImmutableList.of("v3", "v4", "v5"));
  }

  @Test
  public void testFilteredResourcesRegularQualifiers() throws Exception {
    // Include one value for each qualifier not tested above
    String filters =
        "mcc310-mnc004-ldrtl-long-round-port-car-night-notouch-keysexposed-nokeys-navexposed-nonav";

    // In the qualifiers we expect to be removed, include one value that contradicts each qualifier
    // of the filter
    testDirectResourceFiltering(
        filters,
        /* unexpectedQualifiers= */ ImmutableList.of(
            "mcc309",
            "mnc03",
            "ldltr",
            "notlong",
            "notround",
            "land",
            "watch",
            "notnight",
            "finger",
            "keyshidden",
            "qwerty",
            "navhidden",
            "dpad"),
        /* expectedQualifiers= */ ImmutableList.of(filters));
  }

  @Test
  public void testDensityFilteredResourcesSingleDensity() throws Exception {
    testDensityResourceFiltering(
        "hdpi", ImmutableList.of("ldpi", "mdpi", "xhdpi"), ImmutableList.of("hdpi"));
  }

  @Test
  public void testDensityFilteredResourcesSingleClosestDensity() throws Exception {
    testDensityResourceFiltering(
        "hdpi", ImmutableList.of("ldpi", "mdpi"), ImmutableList.of("xhdpi"));
  }

  @Test
  public void testDensityFilteredResourcesDifferingQualifiers() throws Exception {
    testDensityResourceFiltering(
        "hdpi",
        ImmutableList.of("en-xhdpi", "fr"),
        ImmutableList.of("en-hdpi", "fr-mdpi", "xhdpi"));
  }

  @Test
  public void testDensityFilteredResourcesMultipleDensities() throws Exception {
    testDensityResourceFiltering(
        "ldpi,hdpi', 'xhdpi",
        ImmutableList.of("mdpi", "xxhdpi"),
        ImmutableList.of("ldpi", "hdpi", "xhdpi"));
  }

  @Test
  public void testDensityFilteredResourcesDoubledDensity() throws Exception {
    testDensityResourceFiltering(
        "hdpi", ImmutableList.of("ldpi", "mdpi", "xhdpi"), ImmutableList.of("xxhdpi"));
  }

  @Test
  public void testDensityFilteredResourcesDifferentRatiosSmallerCloser() throws Exception {
    testDensityResourceFiltering("mdpi", ImmutableList.of("hdpi"), ImmutableList.of("ldpi"));
  }

  @Test
  public void testDensityFilteredResourcesDifferentRatiosLargerCloser() throws Exception {
    testDensityResourceFiltering("hdpi", ImmutableList.of("mdpi"), ImmutableList.of("xhdpi"));
  }

  @Test
  public void testDensityFilteredResourcesSameRatioPreferLarger() throws Exception {
    // xxhdpi is 480dpi and xxxhdpi is 640dpi.
    // The ratios between 360dpi and 480dpi and between 480dpi and 640dpi are both 3:4.
    testDensityResourceFiltering("xxhdpi", ImmutableList.of("360dpi"), ImmutableList.of("xxxhdpi"));
  }

  @Test
  public void testDensityFilteredResourcesOptionsSmallerThanDesired() throws Exception {
    testDensityResourceFiltering(
        "xxxhdpi", ImmutableList.of("xhdpi", "mdpi", "ldpi"), ImmutableList.of("xxhdpi"));
  }

  @Test
  public void testDensityFilteredResourcesOptionsLargerThanDesired() throws Exception {
    testDensityResourceFiltering(
        "ldpi", ImmutableList.of("xxxhdpi", "xxhdpi", "xhdpi"), ImmutableList.of("mdpi"));
  }

  @Test
  public void testDensityFilteredResourcesSpecialValues() throws Exception {
    testDensityResourceFiltering(
        "xxxhdpi", ImmutableList.of("anydpi", "nodpi"), ImmutableList.of("ldpi"));
  }

  @Test
  public void testDensityFilteredResourcesXml() throws Exception {
    testDirectResourceFiltering(
        /* resourceConfigurationFilters= */ "",
        "hdpi",
        ImmutableList.<String>of(),
        ImmutableList.of("ldpi", "mdpi", "hdpi", "xhdpi"),
        /* expectUnqualifiedResource= */ true,
        "drawable",
        "xml");
  }

  @Test
  public void testDensityFilteredResourcesNotDrawable() throws Exception {
    testDirectResourceFiltering(
        /* resourceConfigurationFilters= */ "",
        "hdpi",
        ImmutableList.<String>of(),
        ImmutableList.of("ldpi", "mdpi", "hdpi", "xhdpi"),
        /* expectUnqualifiedResource= */ true,
        "layout",
        "png");
  }

  @Test
  public void testQualifierAndDensityFilteredResources() throws Exception {
    testDirectResourceFiltering(
        "en,fr-mdpi",
        "ldpi,hdpi",
        ImmutableList.of("mdpi", "es-ldpi", "en-xxxhdpi", "fr-mdpi"),
        ImmutableList.of("ldpi", "hdpi", "en-xhdpi", "fr-hdpi"),
        /* expectUnqualifiedResource= */ false,
        "drawable",
        "png");
  }

  private void testDirectResourceFiltering(
      String filters, List<String> unexpectedQualifiers, ImmutableList<String> expectedQualifiers)
      throws Exception {
    testDirectResourceFiltering(
        filters,
        /* densities= */ "",
        unexpectedQualifiers,
        expectedQualifiers,
        /* expectUnqualifiedResource= */ true,
        "drawable",
        "png");
  }

  private void testDensityResourceFiltering(
      String densities, List<String> unexpectedQualifiers, List<String> expectedQualifiers)
      throws Exception {
    testDirectResourceFiltering(
        /* resourceConfigurationFilters= */ "",
        densities,
        unexpectedQualifiers,
        expectedQualifiers,
        /* expectUnqualifiedResource= */ false,
        "drawable",
        "png");
  }

  private void testDirectResourceFiltering(
      String resourceConfigurationFilters,
      String densities,
      List<String> unexpectedQualifiers,
      List<String> expectedQualifiers,
      boolean expectUnqualifiedResource,
      String folderType,
      String suffix)
      throws Exception {

    List<String> unexpectedResources = new ArrayList<>();
    List<String> expectedFiltered = new ArrayList<>();
    for (String qualifier : unexpectedQualifiers) {
      expectedFiltered.add(folderType + "-" + qualifier + "/foo." + suffix);
      unexpectedResources.add("res/" + folderType + "-" + qualifier + "/foo." + suffix);
    }

    List<String> expectedResources = new ArrayList<>();
    for (String qualifier : expectedQualifiers) {
      expectedResources.add("res/" + folderType + "-" + qualifier + "/foo." + suffix);
    }

    String unqualifiedResource = "res/" + folderType + "/foo." + suffix;
    if (expectUnqualifiedResource) {
      expectedResources.add(unqualifiedResource);
    } else {
      unexpectedResources.add(unqualifiedResource);
      expectedFiltered.add(folderType + "/foo." + suffix);
    }

    // Default resources should never be filtered
    expectedResources.add("res/values/foo.xml");

    Iterable<String> allResources = Iterables.concat(unexpectedResources, expectedResources);

    String dir = "java/r/android";

    useConfiguration("--experimental_android_resource_filtering_method", "filter_in_analysis");

    ConfiguredTarget binary =
        scratchConfiguredTarget(
            dir,
            "r",
            "android_binary(name = 'r',",
            "  manifest = 'AndroidManifest.xml',",
            "  resource_configuration_filters = ['" + resourceConfigurationFilters + "'],",
            "  densities = ['" + densities + "'],",
            "  resource_files = ['" + Joiner.on("', '").join(allResources) + "'])");

    ResourceContainer directResources = getResourceContainer(binary, /* transitive= */ false);

    // Validate that the AndroidResourceProvider for this binary contains only the filtered values.
    assertThat(resourceContentsPaths(dir, directResources))
        .containsExactlyElementsIn(expectedResources);

    // Validate that the input to resource processing contains only the filtered values.
    assertThat(resourceInputPaths(dir, directResources)).containsAllIn(expectedResources);
    assertThat(resourceInputPaths(dir, directResources)).containsNoneIn(unexpectedResources);

    if (expectedFiltered.isEmpty()) {
      assertThat(resourceArguments(directResources)).doesNotContain("--prefilteredResources");
    } else {
      String[] flagValues =
          flagValue("--prefilteredResources", resourceArguments(directResources)).split(",");
      assertThat(flagValues).asList().containsAllIn(expectedFiltered);
      assertThat(flagValues).hasLength(expectedFiltered.size());
    }

    // Validate resource filters are not passed to execution, since they were applied in analysis
    assertThat(resourceGeneratingAction(directResources).getArguments())
        .doesNotContain(ResourceFilter.RESOURCE_CONFIGURATION_FILTERS_NAME);
    assertThat(resourceGeneratingAction(directResources).getArguments())
        .doesNotContain(ResourceFilter.DENSITIES_NAME);
  }

  @Test
  public void testFilteredTransitiveResources() throws Exception {
    String matchingResource = "res/values-en/foo.xml";
    String unqualifiedResource = "res/values/foo.xml";
    String notMatchingResource = "res/values-fr/foo.xml";

    String dir = "java/r/android";

    useConfiguration("--experimental_android_resource_filtering_method", "filter_in_analysis");

    ConfiguredTarget binary =
        scratchConfiguredTarget(
            dir,
            "r",
            "android_library(name = 'lib',",
            "  manifest = 'AndroidManifest.xml',",
            "  resource_files = [",
            "    '" + matchingResource + "',",
            "    '" + unqualifiedResource + "',",
            "    '" + notMatchingResource + "'",
            "  ])",
            "android_binary(name = 'r',",
            "  manifest = 'AndroidManifest.xml',",
            "  deps = [':lib'],",
            "  resource_configuration_filters = ['en'])");

    ResourceContainer directResources = getResourceContainer(binary, /* transitive= */ false);
    ResourceContainer transitiveResources = getResourceContainer(binary, /* transitive= */ true);

    assertThat(resourceContentsPaths(dir, directResources)).isEmpty();

    assertThat(resourceContentsPaths(dir, transitiveResources))
        .containsExactly(matchingResource, unqualifiedResource);

    assertThat(resourceInputPaths(dir, directResources))
        .containsAllOf(matchingResource, unqualifiedResource);
  }

  /**
   * Gets the paths of matching artifacts contained within a resource container
   *
   * @param dir the directory to look for artifacts in
   * @param resource the container that contains eligible artifacts
   * @return the paths to all artifacts from the input that are contained within the given
   *     directory, relative to that directory.
   */
  private List<String> resourceContentsPaths(String dir, ResourceContainer resource) {
    return pathsToArtifacts(dir, resource.getArtifacts());
  }

  /**
   * Gets the paths of matching artifacts that are used as input to resource processing
   *
   * @param dir the directory to look for artifacts in
   * @param resource the ResourceContainer output from the resource processing that uses these
   *     artifacts as inputs
   * @return the paths to all artifacts used as inputs to resource processing that are contained
   *     within the given directory, relative to that directory.
   */
  private List<String> resourceInputPaths(String dir, ResourceContainer resource) {
    return pathsToArtifacts(dir, resourceGeneratingAction(resource).getInputs());
  }

  /**
   * Gets the paths of matching artifacts from an iterable
   *
   * @param dir the directory to look for artifacts in
   * @param artifacts all available artifacts
   * @return the paths to all artifacts from the input that are contained within the given
   *     directory, relative to that directory.
   */
  private List<String> pathsToArtifacts(String dir, Iterable<Artifact> artifacts) {
    List<String> paths = new ArrayList<>();

    Path containingDir = rootDirectory;
    for (String part : dir.split("/")) {
      containingDir = containingDir.getChild(part);
    }

    for (Artifact a : artifacts) {
      if (a.getPath().startsWith(containingDir)) {
        paths.add(a.getPath().relativeTo(containingDir).toString());
      }
    }

    return paths;
  }

  @Test
  public void testInheritedRNotInRuntimeJars() throws Exception {
    String dir = "java/r/android/";
    scratch.file(
        dir + "BUILD",
        "android_library(name = 'sublib',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = glob(['res3/**']),",
        "                srcs =['sublib.java'],",
        "                )",
        "android_library(name = 'lib',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = glob(['res2/**']),",
        "                deps = [':sublib'],",
        "                srcs =['lib.java'],",
        "                )",
        "android_binary(name = 'bin',",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = glob(['res/**']),",
        "               deps = [':lib'],",
        "               srcs =['bin.java'],",
        "               )");

    // TODO(b/37087277): Remove this once this behavior is the default
    useConfiguration("--noexperimental_android_include_library_resource_jars");

    Action deployJarAction =
        getGeneratingAction(
            getFileConfiguredTarget("//java/r/android:bin_deploy.jar").getArtifact());
    List<String> inputs = ActionsTestUtil.prettyArtifactNames(deployJarAction.getInputs());

    assertThat(inputs)
        .containsAllOf(
            dir + "libsublib.jar",
            dir + "liblib.jar",
            dir + "libbin.jar",
            dir + "bin_resources.jar");
    assertThat(inputs).containsNoneOf(dir + "lib_resources.jar", dir + "sublib_resources.jar");
  }

  @Test
  public void testInheritedRInRuntimeJarsWhenSpecified() throws Exception {
    String dir = "java/r/android/";
    scratch.file(
        dir + "BUILD",
        "android_library(name = 'sublib',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = glob(['res3/**']),",
        "                srcs =['sublib.java'],",
        "                )",
        "android_library(name = 'lib',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = glob(['res2/**']),",
        "                deps = [':sublib'],",
        "                srcs =['lib.java'],",
        "                )",
        "android_binary(name = 'bin',",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = glob(['res/**']),",
        "               deps = [':lib'],",
        "               srcs =['bin.java'],",
        "               )");

    // TODO(b/37087277): Add a configuration flag once this behavior is no longer the default.

    Action deployJarAction =
        getGeneratingAction(
            getFileConfiguredTarget("//java/r/android:bin_deploy.jar").getArtifact());
    List<String> inputs = ActionsTestUtil.prettyArtifactNames(deployJarAction.getInputs());

    assertThat(inputs)
        .containsAllOf(
            dir + "libsublib.jar",
            dir + "liblib.jar",
            dir + "libbin.jar",
            dir + "bin_resources.jar",
            dir + "lib_resources.jar",
            dir + "sublib_resources.jar");
  }

  @Test
  public void testLocalResourcesUseRClassGenerator() throws Exception {
    scratch.file("java/r/android/BUILD",
        "android_library(name = 'lib',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = glob(['res2/**']),",
        "                )",
        "android_binary(name = 'r',",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = glob(['res/**']),",
        "               deps = [':lib'],",
        "               )");
    scratch.file("java/r/android/res2/values/strings.xml",
        "<resources><string name = 'lib_string'>Libs!</string></resources>");
    scratch.file("java/r/android/res/values/strings.xml",
        "<resources><string name = 'hello'>Hello Android!</string></resources>");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    SpawnAction compilerAction = ((SpawnAction) getResourceClassJarAction(binary));
    assertThat(compilerAction.getMnemonic()).isEqualTo("RClassGenerator");
    List<String> args = compilerAction.getArguments();
    assertThat(args)
        .containsAllOf("--primaryRTxt", "--primaryManifest", "--libraries", "--classJarOutput");
  }

  @Test
  public void testLocalResourcesUseRClassGeneratorNoLibraries() throws Exception {
    scratch.file("java/r/android/BUILD",
        "android_binary(name = 'r',",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = glob(['res/**']),",
        "               )");
    scratch.file("java/r/android/res/values/strings.xml",
        "<resources><string name = 'hello'>Hello Android!</string></resources>");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    SpawnAction compilerAction = ((SpawnAction) getResourceClassJarAction(binary));
    assertThat(compilerAction.getMnemonic()).isEqualTo("RClassGenerator");
    List<String> args = compilerAction.getArguments();
    assertThat(args).containsAllOf("--primaryRTxt", "--primaryManifest", "--classJarOutput");
    assertThat(args).doesNotContain("--libraries");
  }

  @Test
  public void testUseRClassGeneratorCustomPackage() throws Exception {
    scratch.file("java/r/android/BUILD",
        "android_library(name = 'lib',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = glob(['res2/**']),",
        "                custom_package = 'com.lib.custom',",
        "                )",
        "android_binary(name = 'r',",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = glob(['res/**']),",
        "               custom_package = 'com.binary.custom',",
        "               deps = [':lib'],",
        "               )");
    scratch.file("java/r/android/res2/values/strings.xml",
        "<resources><string name = 'lib_string'>Libs!</string></resources>");
    scratch.file("java/r/android/res/values/strings.xml",
        "<resources><string name = 'hello'>Hello Android!</string></resources>");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    SpawnAction compilerAction = ((SpawnAction) getResourceClassJarAction(binary));
    assertThat(compilerAction.getMnemonic()).isEqualTo("RClassGenerator");
    List<String> args = compilerAction.getArguments();
    assertThat(args)
        .containsAllOf("--primaryRTxt", "--primaryManifest", "--libraries", "--classJarOutput",
            "--packageForR", "com.binary.custom");
  }

  @Test
  public void testNoCrunchBinaryOnly() throws Exception {
    scratch.file("java/r/android/BUILD",
        "android_binary(name = 'r',",
        "               srcs = ['Foo.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = ['res/drawable-hdpi-v4/foo.png',",
        "                                 'res/drawable-hdpi-v4/bar.9.png'],",
        "               crunch_png = 0,",
        "               )");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    List<String> args = getGeneratingSpawnAction(getResourceApk(binary)).getArguments();
    assertThat(args).contains("--useAaptCruncher=no");
  }

  @Test
  public void testDoCrunch() throws Exception {
    scratch.file("java/r/android/BUILD",
        "android_binary(name = 'r',",
        "               srcs = ['Foo.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = ['res/drawable-hdpi-v4/foo.png',",
        "                                 'res/drawable-hdpi-v4/bar.9.png'],",
        "               crunch_png = 1,",
        "               )");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    List<String> args = getGeneratingSpawnAction(getResourceApk(binary)).getArguments();
    assertThat(args).doesNotContain("--useAaptCruncher=no");
  }

  @Test
  public void testDoCrunchDefault() throws Exception {
    scratch.file("java/r/android/BUILD",
        "android_binary(name = 'r',",
        "               srcs = ['Foo.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = ['res/drawable-hdpi-v4/foo.png',",
        "                                 'res/drawable-hdpi-v4/bar.9.png'],",
        "               )");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    List<String> args = getGeneratingSpawnAction(getResourceApk(binary)).getArguments();
    assertThat(args).doesNotContain("--useAaptCruncher=no");
  }

  @Test
  public void testNoCrunchWithAndroidLibraryNoBinaryResources() throws Exception {
    scratch.file("java/r/android/BUILD",
        "android_library(name = 'resources',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = ['res/values/strings.xml',",
        "                                  'res/drawable-hdpi-v4/foo.png',",
        "                                  'res/drawable-hdpi-v4/bar.9.png'],",
        "               )",
        "android_binary(name = 'r',",
        "               srcs = ['Foo.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               deps = [':resources'],",
        "               crunch_png = 0,",
        "               )");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    List<String> args = getGeneratingSpawnAction(getResourceApk(binary)).getArguments();
    assertThat(args).contains("--useAaptCruncher=no");
  }

  @Test
  public void testNoCrunchWithMultidexNative() throws Exception {
    scratch.file("java/r/android/BUILD",
        "android_library(name = 'resources',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = ['res/values/strings.xml',",
        "                                  'res/drawable-hdpi-v4/foo.png',",
        "                                  'res/drawable-hdpi-v4/bar.9.png'],",
        "               )",
        "android_binary(name = 'r',",
        "               srcs = ['Foo.java'],",
        "               manifest = 'AndroidManifest.xml',",
        "               deps = [':resources'],",
        "               multidex = 'native',",
        "               crunch_png = 0,",
        "               )");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    List<String> args = getGeneratingSpawnAction(getResourceApk(binary)).getArguments();
    assertThat(args).contains("--useAaptCruncher=no");
  }

  @Test
  public void testZipaligned() throws Exception {
    ConfiguredTarget binary = getConfiguredTarget("//java/android:app");
    SpawnAction action = (SpawnAction) actionsTestUtil().getActionForArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(binary)), "zipaligned_app.apk");
    assertEquals("AndroidZipAlign", action.getMnemonic());

    List<String> arguments = action.getArguments();
    assertEquals(1, Iterables.frequency(arguments, "4"));

    Artifact zipAlignTool =
        getFirstArtifactEndingWith(action.getInputs(), "/zipalign");
    assertEquals(1, Iterables.frequency(arguments, zipAlignTool.getExecPathString()));

    Artifact unsignedApk =
        getFirstArtifactEndingWith(action.getInputs(), "/app_unsigned.apk");
    assertEquals(1, Iterables.frequency(arguments, unsignedApk.getExecPathString()));

    Artifact zipalignedApk =
        getFirstArtifactEndingWith(action.getOutputs(), "/zipaligned_app.apk");
    assertEquals(1, Iterables.frequency(arguments, zipalignedApk.getExecPathString()));
  }

  @Test
  public void testDeployInfo() throws Exception {
    ConfiguredTarget binary = getConfiguredTarget("//java/android:app");
    NestedSet<Artifact> outputGroup = getOutputGroup(binary, "android_deploy_info");
    Artifact deployInfoArtifact = ActionsTestUtil
        .getFirstArtifactEndingWith(outputGroup, "/deploy_info.deployinfo.pb");
    assertThat(deployInfoArtifact).isNotNull();
    AndroidDeployInfo deployInfo = getAndroidDeployInfo(deployInfoArtifact);
    assertThat(deployInfo).isNotNull();
    assertThat(deployInfo.getMergedManifest().getExecRootPath()).endsWith(
        "/AndroidManifest.xml");
    assertThat(deployInfo.getAdditionalMergedManifestsList()).isEmpty();
    assertThat(deployInfo.getApksToDeploy(0).getExecRootPath()).endsWith("/app.apk");
    assertThat(deployInfo.getDataToDeployList()).isEmpty();
  }

  /**
   * Internal helper method: checks that dex sharding input and output is correct for
   * different combinations of multidex mode and build with and without proguard.
   */
  private void internalTestDexShardStructure(MultidexMode multidexMode, boolean proguard,
      String nonProguardSuffix) throws Exception {
    ConfiguredTarget target = getConfiguredTarget("//java/a:a");
    assertNoEvents();
    Action shardAction = getGeneratingAction(getBinArtifact("_dx/a/shard1.jar", target));

    // Verify command line arguments
    List<String> arguments = ((SpawnAction) shardAction).getRemainingArguments();
    List<String> expectedArguments = new ArrayList<>();
    Set<Artifact> artifacts = actionsTestUtil().artifactClosureOf(getFilesToBuild(target));
    Artifact shard1 = getFirstArtifactEndingWith(artifacts, "shard1.jar");
    Artifact shard2 = getFirstArtifactEndingWith(artifacts, "shard2.jar");
    Artifact resourceJar = getFirstArtifactEndingWith(artifacts, "/java_resources.jar");
    expectedArguments.add("--output_jar");
    expectedArguments.add(shard1.getExecPathString());
    expectedArguments.add("--output_jar");
    expectedArguments.add(shard2.getExecPathString());
    expectedArguments.add("--output_resources");
    expectedArguments.add(resourceJar.getExecPathString());
    if (multidexMode == MultidexMode.LEGACY) {
      Artifact mainDexList = getFirstArtifactEndingWith(artifacts,
          "main_dex_list.txt");
      expectedArguments.add("--main_dex_filter");
      expectedArguments.add(mainDexList.getExecPathString());
    }
    if (!proguard) {
      expectedArguments.add("--input_jar");
      expectedArguments.add(
          getFirstArtifactEndingWith(artifacts, "a_resources.jar" + nonProguardSuffix)
          .getExecPathString());
    }
    Artifact inputJar;
    if (proguard) {
      inputJar = getFirstArtifactEndingWith(artifacts, "a_proguard.jar");
    } else {
      inputJar = getFirstArtifactEndingWith(artifacts, "liba.jar" + nonProguardSuffix);
    }
    expectedArguments.add("--input_jar");
    expectedArguments.add(inputJar.getExecPathString());
    assertThat(arguments).containsExactlyElementsIn(expectedArguments).inOrder();

    // Verify input and output artifacts
    List<String> shardOutputs = ActionsTestUtil.baseArtifactNames(shardAction.getOutputs());
    List<String> shardInputs = ActionsTestUtil.baseArtifactNames(shardAction.getInputs());
    assertThat(shardOutputs)
        .containsExactly("shard1.jar", "shard2.jar", "java_resources.jar");
    if (multidexMode == MultidexMode.LEGACY) {
      assertThat(shardInputs).contains("main_dex_list.txt");
    } else {
      assertThat(shardInputs).doesNotContain("main_dex_list.txt");
    }
    if (proguard) {
      assertThat(shardInputs).contains("a_proguard.jar");
      assertThat(shardInputs).doesNotContain("liba.jar" + nonProguardSuffix);
    } else {
      assertThat(shardInputs).contains("liba.jar" + nonProguardSuffix);
      assertThat(shardInputs).doesNotContain("a_proguard.jar");
    }
    assertThat(shardInputs).doesNotContain("a_deploy.jar");

    // Verify that dex compilation is followed by the correct merge operation
    Action apkAction = getGeneratingAction(getFirstArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(target)), "compressed_a_unsigned.apk"));
    Action mergeAction = getGeneratingAction(getFirstArtifactEndingWith(
        apkAction.getInputs(), "classes.dex.zip"));
    Iterable<Artifact> dexShards = Iterables.filter(
        mergeAction.getInputs(), ActionsTestUtil.getArtifactSuffixMatcher(".dex.zip"));
    assertThat(ActionsTestUtil.baseArtifactNames(dexShards))
        .containsExactly("shard1.dex.zip", "shard2.dex.zip");
  }

  @Test
  public void testDexShardingNeedsMultidex() throws Exception {
    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name='a',",
        "    srcs=['A.java'],",
        "    dex_shards=2,",
        "    manifest='AndroidManifest.xml')");
    reporter.removeHandler(failFastHandler);
    getConfiguredTarget("//java/a:a");
    assertContainsEvent(".dex sharding is only available in multidex mode");
  }

  @Test
  public void testDexShardingDoesNotWorkWithManualMultidex() throws Exception {
    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name='a',",
        "    srcs=['A.java'],",
        "    dex_shards=2,",
        "    multidex='manual_main_dex',",
        "    main_dex_list='main_dex_list.txt',",
        "    manifest='AndroidManifest.xml')");
    reporter.removeHandler(failFastHandler);
    getConfiguredTarget("//java/a:a");
    assertContainsEvent(".dex sharding is not available in manual multidex mode");
  }

  @Test
  public void testDexShardingLegacyStructure() throws Exception {
    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name='a',",
        "    srcs=['A.java'],",
        "    dex_shards=2,",
        "    multidex='legacy',",
        "    manifest='AndroidManifest.xml')");

    internalTestDexShardStructure(MultidexMode.LEGACY, false, "");
  }

  @Test
  public void testDexShardingNativeStructure() throws Exception {
    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name='a',",
        "    srcs=['A.java'],",
        "    dex_shards=2,",
        "    multidex='native',",
        "    manifest='AndroidManifest.xml')");

    internalTestDexShardStructure(MultidexMode.NATIVE, false, "");
  }

  @Test
  public void testDexShardingNativeStructure_withDesugaring() throws Exception {
    useConfiguration("--experimental_desugar_for_android");
    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name='a',",
        "    srcs=['A.java'],",
        "    dex_shards=2,",
        "    multidex='native',",
        "    manifest='AndroidManifest.xml')");

    internalTestDexShardStructure(MultidexMode.NATIVE, false, "_desugared.jar");
  }

  @Test
  public void testDexShardingLegacyAndProguardStructure() throws Exception {
    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name='a',",
        "    srcs=['A.java'],",
        "    dex_shards=2,",
        "    multidex='legacy',",
        "    manifest='AndroidManifest.xml',",
        "    proguard_specs=['proguard.cfg'])");

    internalTestDexShardStructure(MultidexMode.LEGACY, true, "");
  }

  @Test
  public void testDexShardingLegacyAndProguardStructure_withDesugaring() throws Exception {
    useConfiguration("--experimental_desugar_for_android");
    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name='a',",
        "    srcs=['A.java'],",
        "    dex_shards=2,",
        "    multidex='legacy',",
        "    manifest='AndroidManifest.xml',",
        "    proguard_specs=['proguard.cfg'])");

    internalTestDexShardStructure(MultidexMode.LEGACY, true, "_desugared.jar");
  }

  @Test
  public void testDexShardingNativeAndProguardStructure() throws Exception {
    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name='a',",
        "    srcs=['A.java'],",
        "    dex_shards=2,",
        "    multidex='native',",
        "    manifest='AndroidManifest.xml',",
        "    proguard_specs=['proguard.cfg'])");

    internalTestDexShardStructure(MultidexMode.NATIVE, true, "");
  }

  @Test
  public void testIncrementalApkAndProguardBuildStructure() throws Exception {
    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name='a',",
        "    srcs=['A.java'],",
        "    dex_shards=2,",
        "    multidex='native',",
        "    manifest='AndroidManifest.xml',",
        "    proguard_specs=['proguard.cfg'])");

    ConfiguredTarget target = getConfiguredTarget("//java/a:a");
    Action shardAction = getGeneratingAction(getBinArtifact("_dx/a/shard1.jar", target));
    List<String> shardOutputs = ActionsTestUtil.baseArtifactNames(shardAction.getOutputs());
    assertThat(shardOutputs).contains("java_resources.jar");
    assertThat(shardOutputs).doesNotContain("a_deploy.jar");
  }

  @Test
  public void testManualMainDexBuildStructure() throws Exception {
    checkError("java/foo",
        "maindex_nomultidex",
        "Both \"main_dex_list\" and \"multidex='manual_main_dex'\" must be specified",
        "android_binary(",
        "    name = 'maindex_nomultidex',",
        "    srcs = ['a.java'],",
        "    manifest = 'AndroidManifest.xml',",
        "    multidex = 'manual_main_dex')");
  }

  @Test
  public void testMainDexListLegacyMultidex() throws Exception {
    checkError("java/foo",
        "maindex_nomultidex",
        "Both \"main_dex_list\" and \"multidex='manual_main_dex'\" must be specified",
        "android_binary(",
        "    name = 'maindex_nomultidex',",
        "    srcs = ['a.java'],",
        "    manifest = 'AndroidManifest.xml',",
        "    multidex = 'legacy',",
        "    main_dex_list = 'main_dex_list.txt')");
  }

  @Test
  public void testMainDexListNativeMultidex() throws Exception {
    checkError("java/foo",
        "maindex_nomultidex",
        "Both \"main_dex_list\" and \"multidex='manual_main_dex'\" must be specified",
        "android_binary(",
        "    name = 'maindex_nomultidex',",
        "    srcs = ['a.java'],",
        "    manifest = 'AndroidManifest.xml',",
        "    multidex = 'native',",
        "    main_dex_list = 'main_dex_list.txt')");
  }

  @Test
  public void testMainDexListNoMultidex() throws Exception {
    checkError("java/foo",
        "maindex_nomultidex",
        "Both \"main_dex_list\" and \"multidex='manual_main_dex'\" must be specified",
        "android_binary(",
        "    name = 'maindex_nomultidex',",
        "    srcs = ['a.java'],",
        "    manifest = 'AndroidManifest.xml',",
        "    main_dex_list = 'main_dex_list.txt')");
  }

  @Test
  public void testMainDexListWithAndroidSdk() throws Exception {
    scratch.file("sdk/BUILD",
        "android_sdk(",
        "    name = 'sdk',",
        "    aapt = 'aapt',",
        "    adb = 'adb',",
        "    aidl = 'aidl',",
        "    android_jar = 'android.jar',",
        "    annotations_jar = 'annotations_jar',",
        "    apksigner = 'apksigner',",
        "    dx = 'dx',",
        "    framework_aidl = 'framework_aidl',",
        "    main_dex_classes = 'main_dex_classes',",
        "    main_dex_list_creator = 'main_dex_list_creator',",
        "    proguard = 'proguard',",
        "    shrinked_android_jar = 'shrinked_android_jar',",
        "    zipalign = 'zipalign',",
        "    resource_extractor = 'resource_extractor')");

    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name = 'a',",
        "    srcs = ['A.java'],",
        "    manifest = 'AndroidManifest.xml',",
        "    multidex = 'legacy',",
        "    main_dex_list_opts = ['--hello', '--world'])");

    useConfiguration("--android_sdk=//sdk:sdk");
    ConfiguredTarget a = getConfiguredTarget("//java/a:a");
    Artifact mainDexList = ActionsTestUtil.getFirstArtifactEndingWith(
        actionsTestUtil().artifactClosureOf(getFilesToBuild(a)), "main_dex_list.txt");
    SpawnAction spawnAction = getGeneratingSpawnAction(mainDexList);
    assertThat(spawnAction.getArguments()).containsAllOf("--hello", "--world");
  }

  @Test
  public void testMainDexAaptGenerationSupported() throws Exception {
    scratch.file("sdk/BUILD",
        "android_sdk(",
        "    name = 'sdk',",
        "    build_tools_version = '24.0.0',",
        "    aapt = 'aapt',",
        "    adb = 'adb',",
        "    aidl = 'aidl',",
        "    android_jar = 'android.jar',",
        "    annotations_jar = 'annotations_jar',",
        "    apksigner = 'apksigner',",
        "    dx = 'dx',",
        "    framework_aidl = 'framework_aidl',",
        "    main_dex_classes = 'main_dex_classes',",
        "    main_dex_list_creator = 'main_dex_list_creator',",
        "    proguard = 'proguard',",
        "    shrinked_android_jar = 'shrinked_android_jar',",
        "    zipalign = 'zipalign',",
        "    resource_extractor = 'resource_extractor')");

    scratch.file("java/a/BUILD",
        "android_binary(",
        "    name = 'a',",
        "    srcs = ['A.java'],",
        "    manifest = 'AndroidManifest.xml',",
        "    multidex = 'legacy')");

    useConfiguration("--android_sdk=//sdk:sdk");
    ConfiguredTarget a = getConfiguredTarget("//java/a:a");
    Artifact intermediateJar = artifactByPath(ImmutableList.of(getCompressedUnsignedApk(a)),
        ".apk", ".dex.zip", ".dex.zip", "main_dex_list.txt", "_intermediate.jar");
    List<String> args = getGeneratingSpawnAction(intermediateJar).getArguments();
    assertContainsSublist(
        args,
        ImmutableList.of(
            "-include",
            targetConfig.getBinFragment() + "/java/a/proguard/a/main_dex_a_proguard.cfg"));
  }

  @Test
  public void testMainDexGenerationWithoutProguardMap() throws Exception {
    scratchConfiguredTarget("java/foo", "abin",
        "android_binary(",
        "    name = 'abin',",
        "    srcs = ['a.java'],",
        "    proguard_specs = [],",
        "    manifest = 'AndroidManifest.xml',",
        "    multidex = 'legacy',)");
    ConfiguredTarget a = getConfiguredTarget("//java/foo:abin");
    Artifact intermediateJar = artifactByPath(ImmutableList.of(getCompressedUnsignedApk(a)),
        ".apk", ".dex.zip", ".dex.zip", "main_dex_list.txt", "_intermediate.jar");
    List<String> args = getGeneratingSpawnAction(intermediateJar).getArguments();
    MoreAsserts.assertDoesNotContainSublist(
        args,
        "-previousobfuscationmap");
  }

  // regression test for b/14288948
  @Test
  public void testEmptyListAsProguardSpec() throws Exception {
    scratchConfiguredTarget("java/foo", "abin",
        "android_binary(",
        "    name = 'abin',",
        "    srcs = ['a.java'],",
        "    proguard_specs = [],",
        "    manifest = 'AndroidManifest.xml')");
  }

  @Test
  public void testConfigurableProguardSpecsEmptyList() throws Exception {
    scratchConfiguredTarget("java/foo", "abin",
        "android_binary(",
        "    name = 'abin',",
        "    srcs = ['a.java'],",
        "    proguard_specs = select({",
        "        '" + BuildType.Selector.DEFAULT_CONDITION_KEY + "': [],",
        "    }),",
        "    manifest = 'AndroidManifest.xml')");
    assertNoEvents();
  }

  @Test
  public void testConfigurableProguardSpecsEmptyListWithMapping() throws Exception {
    scratchConfiguredTarget("java/foo", "abin",
        "android_binary(",
        "    name = 'abin',",
        "    srcs = ['a.java'],",
        "    proguard_specs = select({",
        "        '" + BuildType.Selector.DEFAULT_CONDITION_KEY + "': [],",
        "    }),",
        "    proguard_generate_mapping = 1,",
        "    manifest = 'AndroidManifest.xml')");
    assertNoEvents();
  }

  @Test
  public void testResourcesWithConfigurationQualifier_LocalResources() throws Exception {
    scratch.file("java/android/resources/BUILD",
        "android_binary(name = 'r',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = glob(['res/**']),",
        "                )");
    scratch.file("java/android/resources/res/values-en/strings.xml",
        "<resources><string name = 'hello'>Hello Android!</string></resources>");
    scratch.file("java/android/resources/res/values/strings.xml",
        "<resources><string name = 'hello'>Hello Android!</string></resources>");
    ConfiguredTarget resource = getConfiguredTarget("//java/android/resources:r");

    List<String> args = getGeneratingSpawnAction(getResourceApk(resource)).getArguments();

    assertPrimaryResourceDirs(ImmutableList.of("java/android/resources/res"), args);
  }

  @Test
  public void testResourcesInOtherPackage_exported_LocalResources() throws Exception {
    scratch.file("java/android/resources/BUILD",
        "android_binary(name = 'r',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = ['//java/resources/other:res/values/strings.xml'],",
        "                )");
    scratch.file("java/resources/other/BUILD",
        "exports_files(['res/values/strings.xml'])");
    ConfiguredTarget resource = getConfiguredTarget("//java/android/resources:r");

    List<String> args = getGeneratingSpawnAction(getResourceApk(resource)).getArguments();
    assertPrimaryResourceDirs(ImmutableList.of("java/resources/other/res"), args);
    assertNoEvents();
  }

  @Test
  public void testResourcesInOtherPackage_filegroup_LocalResources() throws Exception {
    scratch.file("java/android/resources/BUILD",
        "android_binary(name = 'r',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = ['//java/other/resources:fg'],",
        "                )");
    scratch.file("java/other/resources/BUILD",
        "filegroup(name = 'fg',",
        "          srcs = ['res/values/strings.xml'],",
        ")");
    ConfiguredTarget resource = getConfiguredTarget("//java/android/resources:r");

    List<String> args = getGeneratingSpawnAction(getResourceApk(resource)).getArguments();
    assertPrimaryResourceDirs(ImmutableList.of("java/other/resources/res"), args);
    assertNoEvents();
  }

  @Test
  public void testResourcesInOtherPackage_filegroupWithExternalSources_LocalResources()
      throws Exception {
  scratch.file("java/android/resources/BUILD",
        "android_binary(name = 'r',",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = [':fg'],",
        "               )",
        "filegroup(name = 'fg',",
        "          srcs = ['//java/other/resources:res/values/strings.xml'])");
    scratch.file("java/other/resources/BUILD",
        "exports_files(['res/values/strings.xml'])");
    ConfiguredTarget resource = getConfiguredTarget("//java/android/resources:r");

    List<String> args = getGeneratingSpawnAction(getResourceApk(resource)).getArguments();
    assertPrimaryResourceDirs(ImmutableList.of("java/other/resources/res"), args);
    assertNoEvents();
  }

  @Test
  public void testMultipleDependentResourceDirectories_LocalResources()
      throws Exception {
    scratch.file("java/android/resources/d1/BUILD",
        "android_library(name = 'd1',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = ['d1-res/values/strings.xml'],",
        "                )");
    scratch.file("java/android/resources/d2/BUILD",
        "android_library(name = 'd2',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = ['d2-res/values/strings.xml'],",
        "                )");
    scratch.file("java/android/resources/BUILD",
        "android_binary(name = 'r',",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = ['bin-res/values/strings.xml'],",
        "               deps = [",
        "                   '//java/android/resources/d1:d1','//java/android/resources/d2:d2'",
        "               ])");
    ConfiguredTarget resource = getConfiguredTarget("//java/android/resources:r");

    List<String> args = getGeneratingSpawnAction(getResourceApk(resource)).getArguments();
    assertPrimaryResourceDirs(ImmutableList.of("java/android/resources/bin-res"), args);
    assertThat(getDirectDependentResourceDirs(args)).containsAllIn(ImmutableList.of(
        "java/android/resources/d1/d1-res", "java/android/resources/d2/d2-res"));
    assertNoEvents();
  }

  // Regression test for b/11924769
  @Test
  public void testResourcesInOtherPackage_doubleFilegroup_LocalResources() throws Exception {
    scratch.file("java/android/resources/BUILD",
        "android_binary(name = 'r',",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = [':fg'],",
        "               )",
        "filegroup(name = 'fg',",
        "          srcs = ['//java/other/resources:fg'])");
    scratch.file("java/other/resources/BUILD",
        "filegroup(name = 'fg',",
        "          srcs = ['res/values/strings.xml'],",
        ")");
    ConfiguredTarget resource = getConfiguredTarget("//java/android/resources:r");

    List<String> args = getGeneratingSpawnAction(getResourceApk(resource)).getArguments();
    assertPrimaryResourceDirs(ImmutableList.of("java/other/resources/res"), args);
    assertNoEvents();
  }

  @Test
  public void testManifestMissingFails_LocalResources() throws Exception {
    checkError("java/android/resources", "r",
        "manifest attribute of android_library rule //java/android/resources:r: manifest is "
        + "required when resource_files or assets are defined.",
        "filegroup(name = 'b')",
        "android_library(name = 'r',",
        "                resource_files = [':b'],",
        "                )");
  }

  @Test
  public void testResourcesDoesNotMatchDirectoryLayout_BadFile_LocalResources() throws Exception {
    checkError("java/android/resources", "r",
        "'java/android/resources/res/somefile.xml' is not in the expected resource directory "
        + "structure of <resource directory>/{"
        + Joiner.on(',').join(LocalResourceContainer.Builder.RESOURCE_DIRECTORY_TYPES) + "}",
        "android_binary(name = 'r',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = ['res/somefile.xml', 'r/t/f/m/raw/fold']",
        "                )");
  }

  @Test
  public void testResourcesDoesNotMatchDirectoryLayout_BadDirectory_LocalResources()
      throws Exception {
    checkError("java/android/resources",
        "r",
        "'java/android/resources/res/other/somefile.xml' is not in the expected resource directory "
        + "structure of <resource directory>/{"
        + Joiner.on(',').join(LocalResourceContainer.Builder.RESOURCE_DIRECTORY_TYPES) + "}",
        "android_binary(name = 'r',",
        "               manifest = 'AndroidManifest.xml',",
        "               resource_files = ['res/other/somefile.xml', 'r/t/f/m/raw/fold']",
        "               )");
  }

  @Test
  public void testResourcesNotUnderCommonDirectoryFails_LocalResources() throws Exception {
    checkError("java/android/resources", "r",
        "'java/android/resources/r/t/f/m/raw/fold' (generated by '//java/android/resources:r/t/f/m/"
        + "raw/fold') is not in the same directory 'res' "
        + "(derived from java/android/resources/res/raw/speed). "
        + "All resources must share a common directory",
        "android_binary(name = 'r',",
        "                manifest = 'AndroidManifest.xml',",
        "                resource_files = ['res/raw/speed', 'r/t/f/m/raw/fold']",
        "                )");
  }

  @Test
  public void testAssetsAndNoAssetsDirFails_LocalResources() throws Exception {
    scratch.file("java/android/resources/assets/values/strings.xml",
        "<resources><string name = 'hello'>Hello Android!</string></resources>");
    checkError("java/android/resources", "r",
        "'assets' and 'assets_dir' should be either both empty or both non-empty",
        "android_binary(name = 'r',",
        "               manifest = 'AndroidManifest.xml',",
        "               assets = glob(['assets/**']),",
        "               )");
  }

  @Test
  public void testAssetsDirAndNoAssetsFails_LocalResources() throws Exception {
    checkError("java/cpp/android", "r",
        "'assets' and 'assets_dir' should be either both empty or both non-empty",
        "android_binary(name = 'r',",
        "               manifest = 'AndroidManifest.xml',",
        "               assets_dir = 'assets',",
        "                )");
  }

  @Test
  public void testAssetsNotUnderAssetsDirFails_LocalResources() throws Exception {
    checkError("java/android/resources", "r",
        "'java/android/resources/r/t/f/m' (generated by '//java/android/resources:r/t/f/m') "
        + "is not beneath 'assets'",
        "android_binary(name = 'r',",
        "                manifest = 'AndroidManifest.xml',",
        "                assets_dir = 'assets',",
        "                assets = ['assets/valuable', 'r/t/f/m']",
        "                )");
  }

  @Test
  public void testFileLocation_LocalResources() throws Exception {
    scratch.file("java/android/resources/BUILD",
      "android_binary(name = 'r',",
      "               srcs = ['Foo.java'],",
      "               manifest = 'AndroidManifest.xml',",
      "               resource_files = ['res/values/strings.xml'],",
      "               )");
    ConfiguredTarget r = getConfiguredTarget("//java/android/resources:r");
    assertEquals(getTargetConfiguration().getBinDirectory(RepositoryName.MAIN),
        getFirstArtifactEndingWith(getFilesToBuild(r), ".apk").getRoot());
  }

  @Test
  public void testCustomPackage_LocalResources() throws Exception {
    scratch.file("a/r/BUILD",
      "android_binary(name = 'r',",
      "               srcs = ['Foo.java'],",
      "               custom_package = 'com.google.android.bar',",
      "               manifest = 'AndroidManifest.xml',",
      "               resource_files = ['res/values/strings.xml'],",
      "               )");
    ConfiguredTarget r = getConfiguredTarget("//a/r:r");
    assertNoEvents();
    List<String> args = getGeneratingSpawnAction(getResourceApk(r)).getArguments();
    assertContainsSublist(args, ImmutableList.of("--packageForR", "com.google.android.bar"));
  }

  @Test
  public void testCustomJavacopts() throws Exception {
    scratch.file("java/foo/A.java", "foo");
    scratch.file("java/foo/BUILD",
        "android_binary(name = 'a', manifest = 'AndroidManifest.xml', ",
        "  srcs = ['A.java'], javacopts = ['-g:lines,source'])");


    Artifact deployJar = getFileConfiguredTarget("//java/foo:a_deploy.jar").getArtifact();
    Action deployAction = getGeneratingAction(deployJar);
    JavaCompileAction javacAction = (JavaCompileAction)
        actionsTestUtil().getActionForArtifactEndingWith(
              actionsTestUtil().artifactClosureOf(deployAction.getInputs()), "liba.jar");

    assertThat(javacAction.buildCommandLine()).contains("-g:lines,source");
  }

  @Test
  public void testAndroidBinaryExportsJavaCompilationArgsProvider() throws Exception {

    scratch.file("java/foo/A.java", "foo");
    scratch.file("java/foo/BUILD",
        "android_binary(name = 'a', manifest = 'AndroidManifest.xml', ",
        "  srcs = ['A.java'], javacopts = ['-g:lines,source'])");

    final JavaCompilationArgsProvider provider =
        getConfiguredTarget("//java/foo:a").getProvider(JavaCompilationArgsProvider.class);

    assertThat(provider).isNotNull();
  }

  @Test
  public void testNoApplicationId_LocalResources() throws Exception {
    scratch.file("java/a/r/BUILD",
      "android_binary(name = 'r',",
      "               srcs = ['Foo.java'],",
      "               manifest = 'AndroidManifest.xml',",
      "               resource_files = ['res/values/strings.xml'],",
      "               )");
    ConfiguredTarget r = getConfiguredTarget("//java/a/r:r");
    assertNoEvents();
    List<String> args = getGeneratingSpawnAction(getResourceApk(r)).getArguments();
    Truth.assertThat(args).doesNotContain("--applicationId");
  }

  @Test
  public void testDisallowPrecompiledJars() throws Exception {
    checkError("java/precompiled", "binary",
        // messages:
        "does not produce any android_binary srcs files (expected .java or .srcjar)",
        // build file:
        "android_binary(name = 'binary',",
        "    manifest='AndroidManifest.xml',",
        "    srcs = [':jar'])",
        "filegroup(name = 'jar',",
        "    srcs = ['lib.jar'])");
  }

  @Test
  public void testApplyProguardMapping() throws Exception {
    scratch.file(
        "java/com/google/android/BUILD",
        "android_binary(",
        "  name = 'foo',",
        "  srcs = ['foo.java'],",
        "  proguard_apply_mapping = 'proguard.map',",
        "  proguard_specs = ['foo.pro'],",
        "  manifest = 'AndroidManifest.xml',",
        ")");

    ConfiguredTarget ct = getConfiguredTarget("//java/com/google/android:foo");
    SpawnAction proguardAction =
        getGeneratingSpawnAction(artifactByPath(getFilesToBuild(ct), "_proguard.jar"));
    MoreAsserts.assertContainsSublist(
        proguardAction.getArguments(),
        "-applymapping", "java/com/google/android/proguard.map");
  }

  @Test
  public void testApplyProguardMappingWithNoSpec() throws Exception {
    checkError(
        "java/com/google/android", "foo",
        // messages:
        "'proguard_apply_mapping' can only be used when 'proguard_specs' is also set",
        // build file:
        "android_binary(",
        "  name = 'foo',",
        "  srcs = ['foo.java'],",
        "  proguard_apply_mapping = 'proguard.map',",
        "  manifest = 'AndroidManifest.xml',",
        ")");
  }

  @Test
  public void testFeatureFlagsAttributeSetsSelectInDependency() throws Exception {
    useConfiguration("--experimental_dynamic_configs=on");
    scratch.file(
        "java/com/foo/BUILD",
        "config_feature_flag(",
        "  name = 'flag1',",
        "  allowed_values = ['on', 'off'],",
        "  default_value = 'off',",
        ")",
        "config_setting(",
        "  name = 'flag1@on',",
        "  flag_values = {':flag1': 'on'},",
        ")",
        "config_feature_flag(",
        "  name = 'flag2',",
        "  allowed_values = ['on', 'off'],",
        "  default_value = 'off',",
        ")",
        "config_setting(",
        "  name = 'flag2@on',",
        "  flag_values = {':flag2': 'on'},",
        ")",
        "android_library(",
        "  name = 'lib',",
        "  srcs = select({",
        "    ':flag1@on': ['Flag1On.java'],",
        "    '//conditions:default': ['Flag1Off.java'],",
        "  }) + select({",
        "    ':flag2@on': ['Flag2On.java'],",
        "    '//conditions:default': ['Flag2Off.java'],",
        "  }),",
        ")",
        "android_binary(",
        "  name = 'foo',",
        "  manifest = 'AndroidManifest.xml',",
        "  deps = [':lib'],",
        "  feature_flags = {",
        "    'flag1': 'on',",
        "  }",
        ")");
    ConfiguredTarget binary = getConfiguredTarget("//java/com/foo");
    List<String> inputs =
        actionsTestUtil()
            .prettyArtifactNames(actionsTestUtil().artifactClosureOf(getFinalUnsignedApk(binary)));

    assertThat(inputs).containsAllOf("java/com/foo/Flag1On.java", "java/com/foo/Flag2Off.java");
    assertThat(inputs).containsNoneOf("java/com/foo/Flag1Off.java", "java/com/foo/Flag2On.java");
  }

  @Test
  public void testFeatureFlagsAttributeSetsSelectInBinary() throws Exception {
    useConfiguration("--experimental_dynamic_configs=on");
    scratch.file(
        "java/com/foo/BUILD",
        "config_feature_flag(",
        "  name = 'flag1',",
        "  allowed_values = ['on', 'off'],",
        "  default_value = 'off',",
        ")",
        "config_setting(",
        "  name = 'flag1@on',",
        "  flag_values = {':flag1': 'on'},",
        ")",
        "config_feature_flag(",
        "  name = 'flag2',",
        "  allowed_values = ['on', 'off'],",
        "  default_value = 'off',",
        ")",
        "config_setting(",
        "  name = 'flag2@on',",
        "  flag_values = {':flag2': 'on'},",
        ")",
        "android_binary(",
        "  name = 'foo',",
        "  manifest = 'AndroidManifest.xml',",
        "  srcs = select({",
        "    ':flag1@on': ['Flag1On.java'],",
        "    '//conditions:default': ['Flag1Off.java'],",
        "  }) + select({",
        "    ':flag2@on': ['Flag2On.java'],",
        "    '//conditions:default': ['Flag2Off.java'],",
        "  }),",
        "  feature_flags = {",
        "    'flag1': 'on',",
        "  }",
        ")");
    ConfiguredTarget binary = getConfiguredTarget("//java/com/foo");
    List<String> inputs =
        actionsTestUtil()
            .prettyArtifactNames(actionsTestUtil().artifactClosureOf(getFinalUnsignedApk(binary)));

    assertThat(inputs).containsAllOf("java/com/foo/Flag1On.java", "java/com/foo/Flag2Off.java");
    assertThat(inputs).containsNoneOf("java/com/foo/Flag1Off.java", "java/com/foo/Flag2On.java");
  }

  @Test
  public void testFeatureFlagsAttributeFailsAnalysisIfFlagValueIsInvalid() throws Exception {
    reporter.removeHandler(failFastHandler);
    useConfiguration("--experimental_dynamic_configs=on");
    scratch.file(
        "java/com/foo/BUILD",
        "config_feature_flag(",
        "  name = 'flag1',",
        "  allowed_values = ['on', 'off'],",
        "  default_value = 'off',",
        ")",
        "config_setting(",
        "  name = 'flag1@on',",
        "  flag_values = {':flag1': 'on'},",
        ")",
        "android_library(",
        "  name = 'lib',",
        "  srcs = select({",
        "    ':flag1@on': ['Flag1On.java'],",
        "    '//conditions:default': ['Flag1Off.java'],",
        "  })",
        ")",
        "android_binary(",
        "  name = 'foo',",
        "  manifest = 'AndroidManifest.xml',",
        "  deps = [':lib'],",
        "  feature_flags = {",
        "    'flag1': 'invalid',",
        "  }",
        ")");
    assertThat(getConfiguredTarget("//java/com/foo")).isNull();
    assertContainsEvent(
        "in config_feature_flag rule //java/com/foo:flag1: "
            + "value must be one of ['off', 'on'], but was 'invalid'");
  }

  @Test
  public void testFeatureFlagsAttributeFailsAnalysisIfFlagValueIsInvalidEvenIfNotUsed()
      throws Exception {
    reporter.removeHandler(failFastHandler);
    useConfiguration("--experimental_dynamic_configs=on");
    scratch.file(
        "java/com/foo/BUILD",
        "config_feature_flag(",
        "  name = 'flag1',",
        "  allowed_values = ['on', 'off'],",
        "  default_value = 'off',",
        ")",
        "config_setting(",
        "  name = 'flag1@on',",
        "  flag_values = {':flag1': 'on'},",
        ")",
        "android_binary(",
        "  name = 'foo',",
        "  manifest = 'AndroidManifest.xml',",
        "  feature_flags = {",
        "    'flag1': 'invalid',",
        "  }",
        ")");
    assertThat(getConfiguredTarget("//java/com/foo")).isNull();
    assertContainsEvent(
        "in config_feature_flag rule //java/com/foo:flag1: "
            + "value must be one of ['off', 'on'], but was 'invalid'");
  }

  @Test
  public void testFeatureFlagsAttributeSetsFeatureFlagProviderValues() throws Exception {
    useConfiguration("--experimental_dynamic_configs=on");
    scratch.file(
        "java/com/foo/reader.bzl",
        "def _impl(ctx):",
        "  ctx.file_action(",
        "      ctx.outputs.java,",
        "      '\\n'.join([",
        "          str(target.label) + ': ' + target[config_common.FeatureFlagInfo].value",
        "          for target in ctx.attr.flags]))",
        "  return struct(files=depset([ctx.outputs.java]))",
        "flag_reader = rule(",
        "  implementation=_impl,",
        "  attrs={'flags': attr.label_list(providers=[config_common.FeatureFlagInfo])},",
        "  outputs={'java': '%{name}.java'},",
        ")");
    scratch.file(
        "java/com/foo/BUILD",
        "load('//java/com/foo:reader.bzl', 'flag_reader')",
        "config_feature_flag(",
        "  name = 'flag1',",
        "  allowed_values = ['on', 'off'],",
        "  default_value = 'off',",
        ")",
        "config_feature_flag(",
        "  name = 'flag2',",
        "  allowed_values = ['on', 'off'],",
        "  default_value = 'off',",
        ")",
        "flag_reader(",
        "  name = 'FooFlags',",
        "  flags = [':flag1', ':flag2'],",
        ")",
        "android_binary(",
        "  name = 'foo',",
        "  manifest = 'AndroidManifest.xml',",
        "  srcs = [':FooFlags.java'],",
        "  feature_flags = {",
        "    'flag1': 'on',",
        "  }",
        ")");
    Artifact flagList =
        getFirstArtifactEndingWith(
            actionsTestUtil()
                .artifactClosureOf(getFinalUnsignedApk(getConfiguredTarget("//java/com/foo"))),
            "/FooFlags.java");
    FileWriteAction action = (FileWriteAction) getGeneratingAction(flagList);
    assertThat(action.getFileContents())
        .isEqualTo("//java/com/foo:flag1: on\n//java/com/foo:flag2: off");
  }

  @Test
  public void testNocompressExtensions() throws Exception {
    scratch.file(
        "java/r/android/BUILD",
        "android_binary(",
        "  name = 'r',",
        "  srcs = ['Foo.java'],",
        "  manifest = 'AndroidManifest.xml',",
        "  resource_files = ['res/raw/foo.apk'],",
        "  nocompress_extensions = ['.apk', '.so'],",
        ")");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    ResourceContainer resource = getResourceContainer(binary);
    List<String> args = resourceArguments(resource);
    Artifact inputManifest =
        getFirstArtifactEndingWith(
            getGeneratingSpawnAction(resource.getManifest()).getInputs(), "AndroidManifest.xml");
    assertContainsSublist(
        args,
        ImmutableList.of(
            "--primaryData", "java/r/android/res::" + inputManifest.getExecPathString()));
    assertThat(args).contains("--uncompressedExtensions");
    assertThat(args.get(args.indexOf("--uncompressedExtensions") + 1)).isEqualTo(".apk,.so");
  }

  @Test
  public void testNocompressExtensions_useNocompressExtensionsOnApk() throws Exception {
    scratch.file(
        "java/r/android/BUILD",
        "android_binary(",
        "  name = 'r',",
        "  srcs = ['Foo.java'],",
        "  manifest = 'AndroidManifest.xml',",
        "  resource_files = ['res/raw/foo.apk'],",
        "  nocompress_extensions = ['.apk', '.so'],",
        ")");
    useConfiguration("--experimental_android_use_nocompress_extensions_on_apk");
    ConfiguredTarget binary = getConfiguredTarget("//java/r/android:r");
    assertThat(getCompressedUnsignedApkAction(binary).getArguments())
        .containsAllOf("--nocompress_suffixes", ".apk", ".so")
        .inOrder();
    assertThat(getFinalUnsignedApkAction(binary).getArguments())
        .containsAllOf("--nocompress_suffixes", ".apk", ".so")
        .inOrder();
  }
  
  @Test
  public void testAndroidBinaryWithTestOnlySetsTestOnly() throws Exception {
    scratch.file(
        "java/com/google/android/foo/BUILD",
        "android_binary(",
        "  name = 'foo',",
        "  srcs = ['Foo.java'],",
        "  testonly = 1,",
        "  manifest = 'AndroidManifest.xml',",
        "  resource_files = ['res/raw/foo.apk'],",
        "  nocompress_extensions = ['.apk', '.so'],",
        ")");
    JavaCompileAction javacAction =
        (JavaCompileAction)
            getGeneratingAction(
                getBinArtifact("libfoo.jar", getConfiguredTarget("//java/com/google/android/foo")));

    assertThat(javacAction.buildCommandLine()).contains("--testonly");
  }

  @Test
  public void testAndroidBinaryWithoutTestOnlyDoesntSetTestOnly() throws Exception {
    scratch.file(
        "java/com/google/android/foo/BUILD",
        "android_binary(",
        "  name = 'foo',",
        "  srcs = ['Foo.java'],",
        "  manifest = 'AndroidManifest.xml',",
        "  resource_files = ['res/raw/foo.apk'],",
        "  nocompress_extensions = ['.apk', '.so'],",
        ")");
    JavaCompileAction javacAction =
        (JavaCompileAction)
            getGeneratingAction(
                getBinArtifact("libfoo.jar", getConfiguredTarget("//java/com/google/android/foo")));

    assertThat(javacAction.buildCommandLine()).doesNotContain("--testonly");
  }
}
