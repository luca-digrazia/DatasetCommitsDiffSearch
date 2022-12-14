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

package com.google.devtools.build.lib.rules.objc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.lib.actions.util.ActionsTestUtil.getFirstArtifactEndingWith;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.CommandAction;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.analysis.actions.SymlinkAction;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration.ConfigurationDistinguisher;
import com.google.devtools.build.lib.rules.objc.AppleBinary.BinaryType;
import com.google.devtools.build.lib.rules.objc.CompilationSupport.ExtraLinkArgs;
import com.google.devtools.build.lib.rules.objc.ObjcCommandLineOptions.ObjcCrosstoolMode;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesProvider;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.testutil.Scratch;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for apple_binary. */
@RunWith(JUnit4.class)
public class AppleBinaryTest extends ObjcRuleTestCase {
  static final RuleType RULE_TYPE = new RuleType("apple_binary") {
    @Override
    Iterable<String> requiredAttributes(Scratch scratch, String packageDir,
        Set<String> alreadyAdded) throws IOException {
      ImmutableList.Builder<String> attributes = new ImmutableList.Builder<>();
      if (!alreadyAdded.contains("srcs") && !alreadyAdded.contains("non_arc_srcs")) {
        scratch.file(packageDir + "/a.m");
        scratch.file(packageDir + "/private.h");
        attributes.add("srcs = ['a.m', 'private.h']");
      }
      if (!alreadyAdded.contains("platform_type")) {
        attributes.add("platform_type = 'ios'");
      }
      return attributes.build();
    }
  };

  private static final String COCOA_FRAMEWORK_FLAG = "-framework Cocoa";
  private static final String FOUNDATION_FRAMEWORK_FLAG = "-framework Foundation";
  private static final String UIKIT_FRAMEWORK_FLAG = "-framework UIKit";
  private static final ImmutableSet<String> IMPLICIT_NON_MAC_FRAMEWORK_FLAGS =
      ImmutableSet.of(FOUNDATION_FRAMEWORK_FLAG, UIKIT_FRAMEWORK_FLAG);
  private static final ImmutableSet<String> IMPLICIT_MAC_FRAMEWORK_FLAGS =
      ImmutableSet.of(FOUNDATION_FRAMEWORK_FLAG);
  private static final ImmutableSet<String> COCOA_FEATURE_FLAGS =
      ImmutableSet.of(COCOA_FRAMEWORK_FLAG);

  @Test
  public void testLipoActionEnv() throws Exception {
    RULE_TYPE.scratchTarget(scratch,
        "srcs", "['a.m']",
        "platform_type", "'watchos'");

    useConfiguration("--watchos_cpus=i386,armv7k", "--xcode_version=7.3",
        "--watchos_sdk_version=2.1");

    CommandAction action = (CommandAction) lipoBinAction("//x:x");
    assertAppleSdkVersionEnv(action, "2.1");
    assertAppleSdkPlatformEnv(action, "WatchOS");
    assertXcodeVersionEnv(action, "7.3");
  }

  @Test
  public void testSymlinkInsteadOfLipoSingleArch() throws Exception {
    RULE_TYPE.scratchTarget(scratch,
        "srcs", "['a.m']");

    SymlinkAction action = (SymlinkAction) lipoBinAction("//x:x");
    CommandAction linkAction = linkAction("//x:x");

    assertThat(action.getInputs())
        .containsExactly(Iterables.getOnlyElement(linkAction.getOutputs()));
  }

  @Test
  public void testLipoActionEnv_sdkVersionPadding() throws Exception {
    RULE_TYPE.scratchTarget(scratch,
        "srcs", "['a.m']",
        "platform_type", "'watchos'");

    useConfiguration("--watchos_cpus=i386,armv7k",
        "--xcode_version=7.3", "--watchos_sdk_version=2");

    CommandAction action = (CommandAction) lipoBinAction("//x:x");
    assertAppleSdkVersionEnv(action, "2.0");
  }

  @Test
  public void testCcDependencyLinkoptsArePropagatedToLinkAction() throws Exception {
    checkCcDependencyLinkoptsArePropagatedToLinkAction(RULE_TYPE);
  }

  @Test
  public void testUnknownPlatformType() throws Exception {
    checkError(
        "package",
        "test",
        String.format(MultiArchSplitTransitionProvider.UNSUPPORTED_PLATFORM_TYPE_ERROR_FORMAT,
            "meow_meow_os"),
        "apple_binary(name = 'test', srcs = [ 'a.m' ], platform_type = 'meow_meow_os')");
  }

  @Test
  public void testProtoDylibDeps() throws Exception {
    checkProtoDedupingDeps(BinaryType.DYLIB);
  }

  @Test
  public void testProtoBundleLoaderDeps() throws Exception {
    checkProtoDedupingDeps(BinaryType.LOADABLE_BUNDLE);
  }

  /**
   * Test scenario where all proto symbols are contained within the lower level dependency. There is
   * an implicit dependency hierarchy between the different apple_binary types (executable,
   * loadable_bundle, dylib) which looks like this (top level binary types can depend on lower level
   * binary types):
   *
   *              loadable_bundle
   *            /                \
   *        dylib(s)          executable
   *                              |
   *                            dylibs
   *
   * The mechanism to remove duplicate dependencies between dylibs and executable binaries works the
   * same way between executable and loadable_bundle binaries; the only difference is through which
   * attribute the dependency is declared (dylibs vs bundle_loader). This test scenario sets up
   * dependencies for low level and top level binaries and checks that the correct files are linked
   * for each of the binaries.
   *
   * @param depBinaryType either {@link BinaryType#DYLIB} or {@link BinaryType#LOADABLE_BUNDLE}, as
   *     this deduping test is applicable for either
   */
  private void checkProtoDedupingDeps(BinaryType depBinaryType) throws Exception {
    scratch.file(
        "protos/BUILD",
        "proto_library(",
        "    name = 'protos_1',",
        "    srcs = ['data_a.proto', 'data_b.proto'],",
        ")",
        "proto_library(",
        "    name = 'protos_2',",
        "    srcs = ['data_b.proto', 'data_c.proto'],",
        ")",
        "proto_library(",
        "    name = 'protos_3',",
        "    srcs = ['data_a.proto', 'data_c.proto', 'data_d.proto'],",
        ")",
        "objc_proto_library(",
        "    name = 'objc_protos_a',",
        "    portable_proto_filters = ['filter_a.pbascii'],",
        "    deps = [':protos_1'],",
        ")",
        "objc_proto_library(",
        "    name = 'objc_protos_b',",
        "    portable_proto_filters = ['filter_b.pbascii'],",
        "    deps = [':protos_2', ':protos_3'],",
        ")");
    scratch.file(
        "libs/BUILD",
        "objc_library(",
        "    name = 'objc_lib',",
        "    srcs = ['a.m'],",
        "    deps = ['//protos:objc_protos_a', '//protos:objc_protos_b']",
        ")");

    if (depBinaryType == BinaryType.DYLIB) {
      scratchFrameworkSkylarkStub("frameworkstub/framework_stub.bzl");
      scratch.file(
          "depBinary/BUILD",
          "load('//frameworkstub:framework_stub.bzl', 'framework_stub_rule')",
          "apple_binary(",
          "    name = 'apple_low_level_binary',",
          "    srcs = ['b.m'],",
          "    deps = ['//libs:objc_lib'],",
          "    platform_type = 'ios',",
          "    binary_type = 'dylib',",
          ")",
          "framework_stub_rule(",
          "  name = 'low_level_framework',",
          "  deps = [':apple_low_level_binary'],",
          "  binary = ':apple_low_level_binary')");
      RULE_TYPE.scratchTarget(
          scratch,
          "binary_type",
          "'executable'",
          "srcs",
          "['main.m']",
          "deps",
          "['//libs:objc_lib']",
          "dylibs",
          "['//depBinary:low_level_framework']",
          "defines",
          "['SHOULDNOTBEINPROTOS']",
          "copts",
          "['-ISHOULDNOTBEINPROTOS']");

    } else {
      assertThat(depBinaryType == BinaryType.LOADABLE_BUNDLE).isTrue();
      scratch.file(
          "depBinary/BUILD",
          "apple_binary(",
          "    name = 'apple_low_level_binary',",
          "    srcs = ['b.m'],",
          "    deps = ['//libs:objc_lib'],",
          "    platform_type = 'ios',",
          "    binary_type = 'executable',",
          ")");
      RULE_TYPE.scratchTarget(
          scratch,
          "binary_type",
          "'loadable_bundle'",
          "srcs",
          "['main.m']",
          "deps",
          "['//libs:objc_lib']",
          "bundle_loader",
          "'//depBinary:apple_low_level_binary'");
    }

    // The proto libraries objc_lib depends on should be linked into the low level binary but not x.
    Artifact lowLevelBinary =
        Iterables.getOnlyElement(linkAction("//depBinary:apple_low_level_binary").getOutputs());
    ImmutableList<Artifact> lowLevelObjectFiles = getAllObjectFilesLinkedInBin(lowLevelBinary);
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataA.pbobjc.o")).isNotNull();
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataB.pbobjc.o")).isNotNull();
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataC.pbobjc.o")).isNotNull();
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataD.pbobjc.o")).isNotNull();

    Artifact bin = Iterables.getOnlyElement(linkAction("//x:x").getOutputs());
    ImmutableList<Artifact> binObjectFiles = getAllObjectFilesLinkedInBin(bin);
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataA.pbobjc.o")).isNull();
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataB.pbobjc.o")).isNull();
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataC.pbobjc.o")).isNull();
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataD.pbobjc.o")).isNull();
  }

  @Test
  public void testProtoDylibDepsPartial() throws Exception {
    checkProtoDedupingDepsPartial(AppleBinary.BinaryType.DYLIB);
  }

  @Test
  public void testProtoBundleLoaderDepsPartial() throws Exception {
    checkProtoDedupingDepsPartial(AppleBinary.BinaryType.LOADABLE_BUNDLE);
  }

  /**
   * Test scenario where proto symbols are mixed between the low and top level binaries. There is
   * an implicit dependency hierarchy between the different apple_binary types (executable,
   * loadable_bundle, dylib) which looks like this (top level binary types can depend on lower level
   * binary types):
   *
   *              loadable_bundle
   *            /                \
   *        dylib(s)          executable
   *                              |
   *                            dylibs
   *
   * The mechanism to remove duplicate dependencies between dylibs and executable binaries works the
   * same way between executable and loadable_bundle binaries; the only difference is through which
   * attribute the dependency is declared (dylibs vs bundle_loader). This test scenario sets up
   * dependencies for low level and top level binaries and checks that the correct files are linked
   * for each of the binaries.
   *
   * @param depBinaryType either {@link BinaryType#DYLIB} or {@link BinaryType#LOADABLE_BUNDLE}, as
   *     this deduping test is applicable for either
   */
  private void checkProtoDedupingDepsPartial(BinaryType depBinaryType) throws Exception {
    scratch.file(
        "protos/BUILD",
        "proto_library(",
        "    name = 'protos_1',",
        "    srcs = ['data_a.proto', 'data_b.proto'],",
        ")",
        "proto_library(",
        "    name = 'protos_2',",
        "    srcs = ['data_b.proto', 'data_c.proto'],",
        ")",
        "proto_library(",
        "    name = 'protos_3',",
        "    srcs = ['data_a.proto', 'data_c.proto', 'data_d.proto'],",
        ")",
        "objc_proto_library(",
        "    name = 'objc_protos_a',",
        "    portable_proto_filters = ['filter_a.pbascii'],",
        "    deps = [':protos_1'],",
        ")",
        "objc_proto_library(",
        "    name = 'objc_protos_b',",
        "    portable_proto_filters = ['filter_b.pbascii'],",
        "    deps = [':protos_2', ':protos_3'],",
        ")");
    scratch.file(
        "libs/BUILD",
        "objc_library(",
        "    name = 'main_lib',",
        "    srcs = ['a.m'],",
        "    deps = ['//protos:objc_protos_a', '//protos:objc_protos_b']",
        ")",
        "objc_library(",
        "    name = 'apple_low_level_lib',",
        "    srcs = ['b.m'],",
        "    deps = ['//protos:objc_protos_a']",
        ")");

    if (depBinaryType == BinaryType.DYLIB) {
      scratchFrameworkSkylarkStub("frameworkstub/framework_stub.bzl");
      scratch.file(
          "depBinary/BUILD",
          "load('//frameworkstub:framework_stub.bzl', 'framework_stub_rule')",
          "apple_binary(",
          "    name = 'apple_low_level_binary',",
          "    srcs = ['b.m'],",
          "    deps = ['//libs:apple_low_level_lib'],",
          "    platform_type = 'ios',",
          "    binary_type = 'dylib',",
          ")",
          "framework_stub_rule(",
          "  name = 'low_level_framework',",
          "  deps = [':apple_low_level_binary'],",
          "  binary = ':apple_low_level_binary')");
      RULE_TYPE.scratchTarget(
          scratch,
          "binary_type",
          "'executable'",
          "srcs",
          "['main.m']",
          "deps",
          "['//libs:main_lib']",
          "dylibs",
          "['//depBinary:low_level_framework']",
          "defines",
          "['SHOULDNOTBEINPROTOS']",
          "copts",
          "['-ISHOULDNOTBEINPROTOS']");

    } else {
      assertThat(depBinaryType == BinaryType.LOADABLE_BUNDLE).isTrue();
      scratch.file(
          "depBinary/BUILD",
          "apple_binary(",
          "    name = 'apple_low_level_binary',",
          "    srcs = ['b.m'],",
          "    deps = ['//libs:apple_low_level_lib'],",
          "    platform_type = 'ios',",
          "    binary_type = 'executable',",
          ")");
      RULE_TYPE.scratchTarget(
          scratch,
          "binary_type",
          "'loadable_bundle'",
          "srcs",
          "['main.m']",
          "deps",
          "['//libs:main_lib']",
          "bundle_loader",
          "'//depBinary:apple_low_level_binary'",
          "defines",
          "['SHOULDNOTBEINPROTOS']",
          "copts",
          "['-ISHOULDNOTBEINPROTOS']");
    }

    // The proto libraries objc_lib depends on should be linked into the low level binary but not x.
    Artifact lowLevelBinary =
        Iterables.getOnlyElement(linkAction("//depBinary:apple_low_level_binary").getOutputs());
    ImmutableList<Artifact> lowLevelObjectFiles = getAllObjectFilesLinkedInBin(lowLevelBinary);
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataA.pbobjc.o")).isNotNull();
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataB.pbobjc.o")).isNotNull();
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataC.pbobjc.o")).isNull();
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataD.pbobjc.o")).isNull();

    Artifact bin = Iterables.getOnlyElement(linkAction("//x:x").getOutputs());
    ImmutableList<Artifact> binObjectFiles = getAllObjectFilesLinkedInBin(bin);
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataA.pbobjc.o")).isNull();
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataB.pbobjc.o")).isNull();
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataC.pbobjc.o")).isNotNull();
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataD.pbobjc.o")).isNotNull();
  }

  @Test
  public void testProtoDepsViaDylib() throws Exception {
    checkProtoDisjointDeps(BinaryType.DYLIB);
  }

  @Test
  public void testProtoDepsViaBundleLoader() throws Exception {
    checkProtoDisjointDeps(BinaryType.LOADABLE_BUNDLE);
  }

  /**
   * Test scenario where a proto in the top level binary depends on a proto in a low level binary.
   * There is an implicit dependency hierarchy between the different apple_binary types (executable,
   * loadable_bundle, dylib) which looks like this (top level binary types can depend on lower level
   * binary types):
   *
   *              loadable_bundle
   *            /                \
   *        dylib(s)          executable
   *                              |
   *                            dylibs
   *
   * The mechanism to remove duplicate dependencies between dylibs and executable binaries works the
   * same way between executable and loadable_bundle binaries; the only difference is through which
   * attribute the dependency is declared (dylibs vs bundle_loader). This test scenario sets up
   * dependencies for low level and top level binaries and checks that the correct files are linked
   * for each of the binaries.
   *
   * @param depBinaryType either {@link BinaryType#DYLIB} or {@link BinaryType#LOADABLE_BUNDLE}, as
   *     this deduping test is applicable for either
   */
  private void checkProtoDisjointDeps(BinaryType depBinaryType) throws Exception {
    scratch.file(
        "protos/BUILD",
        "proto_library(",
        "    name = 'protos_main',",
        "    srcs = ['data_a.proto', 'data_b.proto'],",
        ")",
        "proto_library(",
        "    name = 'protos_low_level',",
        "    srcs = ['data_b.proto'],",
        ")",
        "objc_proto_library(",
        "    name = 'objc_protos_main',",
        "    portable_proto_filters = ['filter_a.pbascii'],",
        "    deps = [':protos_main'],",
        ")",
        "objc_proto_library(",
        "    name = 'objc_protos_low_level',",
        "    portable_proto_filters = ['filter_b.pbascii'],",
        "    deps = [':protos_low_level'],",
        ")");
    scratch.file(
        "libs/BUILD",
        "objc_library(",
        "    name = 'main_lib',",
        "    srcs = ['a.m'],",
        "    deps = ['//protos:objc_protos_main',]",
        ")",
        "objc_library(",
        "    name = 'apple_low_level_lib',",
        "    srcs = ['a.m'],",
        "    deps = ['//protos:objc_protos_low_level',]",
        ")");
    
    if (depBinaryType == BinaryType.DYLIB) {
      scratchFrameworkSkylarkStub("frameworkstub/framework_stub.bzl");
      scratch.file(
          "depBinary/BUILD",
          "load('//frameworkstub:framework_stub.bzl', 'framework_stub_rule')",
          "apple_binary(",
          "    name = 'apple_low_level_binary',",
          "    srcs = ['b.m'],",
          "    deps = ['//libs:apple_low_level_lib'],",
          "    platform_type = 'ios',",
          "    binary_type = 'dylib',",
          ")",
          "framework_stub_rule(",
          "  name = 'low_level_framework',",
          "  deps = [':apple_low_level_binary'],",
          "  binary = ':apple_low_level_binary')");
      RULE_TYPE.scratchTarget(
          scratch,
          "binary_type",
          "'executable'",
          "srcs",
          "['main.m']",
          "deps",
          "['//libs:main_lib']",
          "dylibs",
          "['//depBinary:low_level_framework']",
          "defines",
          "['SHOULDNOTBEINPROTOS']",
          "copts",
          "['-ISHOULDNOTBEINPROTOS']");

    } else {
      assertThat(depBinaryType == BinaryType.LOADABLE_BUNDLE).isTrue();
      scratch.file(
          "depBinary/BUILD",
          "apple_binary(",
          "    name = 'apple_low_level_binary',",
          "    srcs = ['b.m'],",
          "    deps = ['//libs:apple_low_level_lib'],",
          "    platform_type = 'ios',",
          "    binary_type = 'executable',",
          ")");
      RULE_TYPE.scratchTarget(
          scratch,
          "binary_type",
          "'loadable_bundle'",
          "srcs",
          "['main.m']",
          "deps",
          "['//libs:main_lib']",
          "bundle_loader",
          "'//depBinary:apple_low_level_binary'",
          "defines",
          "['SHOULDNOTBEINPROTOS']",
          "copts",
          "['-ISHOULDNOTBEINPROTOS']");
    }

    // The proto libraries objc_lib depends on should be linked into apple_dylib but not x.
    Artifact lowLevelBinary =
        Iterables.getOnlyElement(linkAction("//depBinary:apple_low_level_binary").getOutputs());
    ImmutableList<Artifact> lowLevelObjectFiles = getAllObjectFilesLinkedInBin(lowLevelBinary);
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataA.pbobjc.o")).isNull();
    assertThat(getFirstArtifactEndingWith(lowLevelObjectFiles, "DataB.pbobjc.o")).isNotNull();

    Artifact bin = Iterables.getOnlyElement(linkAction("//x:x").getOutputs());
    ImmutableList<Artifact> binObjectFiles = getAllObjectFilesLinkedInBin(bin);
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataA.pbobjc.o")).isNotNull();
    assertThat(getFirstArtifactEndingWith(binObjectFiles, "DataB.pbobjc.o")).isNull();
    Action dataAObjectAction =
        getGeneratingAction(getFirstArtifactEndingWith(binObjectFiles, "DataA.pbobjc.o"));
    assertThat(
            getFirstArtifactEndingWith(
                getExpandedActionInputs(dataAObjectAction), "DataB.pbobjc.h"))
        .isNotNull();
  }

  @Test
  public void testProtoBundlingWithTargetsWithNoDeps() throws Exception {
    checkProtoBundlingWithTargetsWithNoDeps(RULE_TYPE);
  }

  @Test
  public void testProtoBundlingDoesNotHappen() throws Exception {
    useConfiguration("--noenable_apple_binary_native_protos");
    checkProtoBundlingDoesNotHappen(RULE_TYPE);
  }

  @Test
  public void testAvoidDepsObjectsWithCrosstool() throws Exception {
    checkAvoidDepsObjectsWithCrosstool(RULE_TYPE);
  }

  @Test
  public void testBundleLoaderCantBeSetWithoutBundleBinaryType() throws Exception {
    scratch.file("bin/BUILD",
        "apple_binary(",
        "    name = 'bin',",
        "    srcs = ['a.m'],",
        "    platform_type = 'ios',",
        ")");
    checkError(
        "bundle", "bundle", AppleBinary.BUNDLE_LOADER_NOT_IN_BUNDLE_ERROR,
        "apple_binary(",
        "    name = 'bundle',",
        "    bundle_loader = '//bin:bin',",
        "    platform_type = 'ios',",
        ")");
  }

  /** Returns the bcsymbolmap artifact for given architecture. */
  protected Artifact bitcodeSymbol(String arch) throws Exception {
    SpawnAction lipoAction = (SpawnAction) lipoBinAction("//examples/apple_skylark:bin");

    String bin =
        configurationBin(arch, ConfigurationDistinguisher.APPLEBIN_IOS)
            + "examples/apple_skylark/bin_bin";
    Artifact binArtifact = getFirstArtifactEndingWith(lipoAction.getInputs(), bin);
    CommandAction linkAction = (CommandAction) getGeneratingAction(binArtifact);
    return getFirstArtifactEndingWith(linkAction.getOutputs(), "bcsymbolmap");
  }

  /** Returns the path to the dSYM binary artifact for given architecture. */
  protected String dsymBinaryPath(String arch) throws Exception {
    return configurationBin(arch, ConfigurationDistinguisher.APPLEBIN_IOS)
        + "examples/apple_skylark/bin.app.dSYM/Contents/Resources/DWARF/bin_bin";
  }

  /** Returns the path to the linkmap artifact for a given architecture. */
  protected String linkmapPath(String arch) throws Exception {
    return configurationBin(arch, ConfigurationDistinguisher.APPLEBIN_IOS)
        + "examples/apple_skylark/bin.linkmap";
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testProvider_dylib() throws Exception {
    scratch.file("examples/rule/BUILD");
    scratch.file(
        "examples/rule/apple_rules.bzl",
        "def _test_rule_impl(ctx):",
        "   dep = ctx.attr.deps[0]",
        "   provider = dep[apple_common.AppleDylibBinary]",
        "   return struct(",
        "      binary = provider.binary,",
        "      objc = provider.objc,",
        "      dep_dir = dir(dep),",
        "   )",
        "test_rule = rule(implementation = _test_rule_impl,",
        "   attrs = {",
        "   'deps': attr.label_list(allow_files = False, mandatory = False,)",
        "})");

    scratch.file(
        "examples/apple_skylark/BUILD",
        "package(default_visibility = ['//visibility:public'])",
        "load('/examples/rule/apple_rules', 'test_rule')",
        "apple_binary(",
        "    name = 'bin',",
        "    srcs = ['a.m'],",
        "    binary_type = '" + BinaryType.DYLIB + "',",
        "    platform_type = 'ios',",
        ")",
        "test_rule(",
        "    name = 'my_target',",
        "    deps = [':bin'],",
        ")");

    useConfiguration("--ios_multi_cpus=armv7,arm64");
    ConfiguredTarget skylarkTarget = getConfiguredTarget("//examples/apple_skylark:my_target");

    assertThat(skylarkTarget.get("binary")).isInstanceOf(Artifact.class);
    assertThat(skylarkTarget.get("objc")).isInstanceOf(ObjcProvider.class);

    List<String> depProviders = (List<String>) skylarkTarget.getValue("dep_dir");
    assertThat(depProviders).doesNotContain("AppleExecutableBinary");
    assertThat(depProviders).doesNotContain("AppleLoadableBundleBinary");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testProvider_executable() throws Exception {
    scratch.file("examples/rule/BUILD");
    scratch.file(
        "examples/rule/apple_rules.bzl",
        "def _test_rule_impl(ctx):",
        "   dep = ctx.attr.deps[0]",
        "   provider = dep[apple_common.AppleExecutableBinary]",
        "   return struct(",
        "      binary = provider.binary,",
        "      objc = provider.objc,",
        "      dep_dir = dir(dep),",
        "   )",
        "test_rule = rule(implementation = _test_rule_impl,",
        "   attrs = {",
        "   'deps': attr.label_list(allow_files = False, mandatory = False,)",
        "})");

    scratch.file(
        "examples/apple_skylark/BUILD",
        "package(default_visibility = ['//visibility:public'])",
        "load('/examples/rule/apple_rules', 'test_rule')",
        "apple_binary(",
        "    name = 'bin',",
        "    srcs = ['a.m'],",
        "    binary_type = '" + BinaryType.EXECUTABLE + "',",
        "    platform_type = 'ios',",
        ")",
        "test_rule(",
        "    name = 'my_target',",
        "    deps = [':bin'],",
        ")");

    useConfiguration("--ios_multi_cpus=armv7,arm64");
    ConfiguredTarget skylarkTarget = getConfiguredTarget("//examples/apple_skylark:my_target");

    assertThat(skylarkTarget.get("binary")).isInstanceOf(Artifact.class);
    assertThat(skylarkTarget.get("objc")).isInstanceOf(ObjcProvider.class);

    List<String> depProviders = (List<String>) skylarkTarget.get("dep_dir");
    assertThat(depProviders).doesNotContain("AppleDylibBinary");
    assertThat(depProviders).doesNotContain("AppleLoadableBundleBinary");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testProvider_loadableBundle() throws Exception {
    scratch.file("examples/rule/BUILD");
    scratch.file(
        "examples/rule/apple_rules.bzl",
        "def _test_rule_impl(ctx):",
        "   dep = ctx.attr.deps[0]",
        "   provider = dep[apple_common.AppleLoadableBundleBinary]",
        "   return struct(",
        "      binary = provider.binary,",
        "      dep_dir = dir(dep),",
        "   )",
        "test_rule = rule(implementation = _test_rule_impl,",
        "   attrs = {",
        "   'deps': attr.label_list(allow_files = False, mandatory = False,)",
        "})");

    scratch.file(
        "examples/apple_skylark/BUILD",
        "package(default_visibility = ['//visibility:public'])",
        "load('/examples/rule/apple_rules', 'test_rule')",
        "apple_binary(",
        "    name = 'bin',",
        "    srcs = ['a.m'],",
        "    binary_type = '" + BinaryType.LOADABLE_BUNDLE + "',",
        "    platform_type = 'ios',",
        ")",
        "test_rule(",
        "    name = 'my_target',",
        "    deps = [':bin'],",
        ")");

    useConfiguration("--ios_multi_cpus=armv7,arm64");
    ConfiguredTarget skylarkTarget = getConfiguredTarget("//examples/apple_skylark:my_target");

    assertThat((Artifact) skylarkTarget.get("binary")).isNotNull();

    List<String> depProviders = (List<String>) skylarkTarget.get("dep_dir");
    assertThat(depProviders).doesNotContain("AppleExecutableBinary");
    assertThat(depProviders).doesNotContain("AppleDylibBinary");
  }

  @Test
  public void testDuplicateLinkopts() throws Exception {
    RULE_TYPE.scratchTarget(scratch, "linkopts", "['-foo', 'bar', '-foo', 'baz']");

    CommandAction linkAction = linkAction("//x:x");
    String linkArgs = Joiner.on(" ").join(linkAction.getArguments());
    assertThat(linkArgs).contains("-Wl,-foo -Wl,bar");
    assertThat(linkArgs).contains("-Wl,-foo -Wl,baz");
  }

  @Test
  public void testCanUseCrosstool_singleArch() throws Exception {
    checkLinkingRuleCanUseCrosstool_singleArch(RULE_TYPE);
  }

  @Test
  public void testCanUseCrosstool_multiArch() throws Exception {
    checkLinkingRuleCanUseCrosstool_multiArch(RULE_TYPE);
  }

  @Test
  public void testAppleSdkIphoneosPlatformEnv() throws Exception {
    checkAppleSdkIphoneosPlatformEnv(RULE_TYPE);
  }

  @Test
  public void testXcodeVersionEnv() throws Exception {
    checkXcodeVersionEnv(RULE_TYPE);
  }

  @Test
  public void testLinksImplicitFrameworksWithCrosstoolIos() throws Exception {
    useConfiguration(
        ObjcCrosstoolMode.ALL,
        "--ios_multi_cpus=x86_64",
        "--ios_sdk_version=10.0",
        "--ios_minimum_os=8.0");
    RULE_TYPE.scratchTarget(scratch, "srcs", "['a.m']", "platform_type", "'ios'");

    Action lipoAction = actionProducingArtifact("//x:x", "_lipobin");
    Artifact binArtifact = getFirstArtifactEndingWith(lipoAction.getInputs(), "x/x_bin");
    CommandAction linkAction = (CommandAction) getGeneratingAction(binArtifact);

    assertThat(linkAction.getArguments()).containsAllIn(IMPLICIT_NON_MAC_FRAMEWORK_FLAGS);
  }

  @Test
  public void testLinksImplicitFrameworksWithCrosstoolWatchos() throws Exception {
    useConfiguration(
        ObjcCrosstoolMode.ALL,
        "--watchos_cpus=i386",
        "--watchos_sdk_version=3.0",
        "--watchos_minimum_os=2.0");
    RULE_TYPE.scratchTarget(scratch, "srcs", "['a.m']", "platform_type", "'watchos'");

    Action lipoAction = actionProducingArtifact("//x:x", "_lipobin");
    Artifact binArtifact = getFirstArtifactEndingWith(lipoAction.getInputs(), "x/x_bin");
    CommandAction linkAction = (CommandAction) getGeneratingAction(binArtifact);

    assertThat(linkAction.getArguments()).containsAllIn(IMPLICIT_NON_MAC_FRAMEWORK_FLAGS);
  }

  @Test
  public void testLinksImplicitFrameworksWithCrosstoolTvos() throws Exception {
    useConfiguration(
        ObjcCrosstoolMode.ALL,
        "--tvos_cpus=x86_64",
        "--tvos_sdk_version=10.1",
        "--tvos_minimum_os=10.0");
    RULE_TYPE.scratchTarget(scratch, "srcs", "['a.m']", "platform_type", "'tvos'");

    Action lipoAction = actionProducingArtifact("//x:x", "_lipobin");
    Artifact binArtifact = getFirstArtifactEndingWith(lipoAction.getInputs(), "x/x_bin");
    CommandAction linkAction = (CommandAction) getGeneratingAction(binArtifact);

    assertThat(linkAction.getArguments()).containsAllIn(IMPLICIT_NON_MAC_FRAMEWORK_FLAGS);
  }

  @Test
  public void testLinksImplicitFrameworksWithCrosstoolMacos() throws Exception {
    useConfiguration(
        ObjcCrosstoolMode.ALL,
        "--macos_cpus=x86_64",
        "--macos_sdk_version=10.11",
        "--macos_minimum_os=10.11");
    RULE_TYPE.scratchTarget(scratch, "srcs", "['a.m']", "platform_type", "'macos'");

    Action lipoAction = actionProducingArtifact("//x:x", "_lipobin");
    Artifact binArtifact = getFirstArtifactEndingWith(lipoAction.getInputs(), "x/x_bin");
    CommandAction linkAction = (CommandAction) getGeneratingAction(binArtifact);

    assertThat(linkAction.getArguments()).containsAllIn(IMPLICIT_MAC_FRAMEWORK_FLAGS);
    assertThat(linkAction.getArguments())
        .containsNoneOf(COCOA_FRAMEWORK_FLAG, UIKIT_FRAMEWORK_FLAG);
  }

  @Test
  public void testLinkCocoaFeatureWithCrosstoolMacos() throws Exception {
    useConfiguration(
        ObjcCrosstoolMode.ALL,
        "--macos_cpus=x86_64",
        "--macos_sdk_version=10.11",
        "--macos_minimum_os=10.11");
    RULE_TYPE.scratchTarget(
        scratch, "srcs", "['a.m']", "platform_type", "'macos'", "features", "['link_cocoa']");

    Action lipoAction = actionProducingArtifact("//x:x", "_lipobin");
    Artifact binArtifact = getFirstArtifactEndingWith(lipoAction.getInputs(), "x/x_bin");
    CommandAction linkAction = (CommandAction) getGeneratingAction(binArtifact);

    assertThat(linkAction.getArguments()).containsAllIn(IMPLICIT_MAC_FRAMEWORK_FLAGS);
    assertThat(linkAction.getArguments()).containsAllIn(COCOA_FEATURE_FLAGS);
    assertThat(linkAction.getArguments()).doesNotContain(UIKIT_FRAMEWORK_FLAG);
  }

  @Test
  public void testAliasedLinkoptsThroughObjcLibrary() throws Exception {
    checkAliasedLinkoptsThroughObjcLibrary(RULE_TYPE);
  }

  @Test
  public void testAppleSdkVersionEnv() throws Exception {
    checkAppleSdkVersionEnv(RULE_TYPE);
  }

  @Test
  public void testNonDefaultAppleSdkVersionEnv() throws Exception {
    checkNonDefaultAppleSdkVersionEnv(RULE_TYPE);
  }

  @Test
  public void testAppleSdkDefaultPlatformEnv() throws Exception {
    checkAppleSdkDefaultPlatformEnv(RULE_TYPE);
  }

  @Test
  public void testAvoidDepsThroughDylib() throws Exception {
    checkAvoidDepsThroughDylib(RULE_TYPE);
  }

  @Test
  public void testAvoidDepsObjects_avoidViaCcLibrary() throws Exception {
    checkAvoidDepsObjects_avoidViaCcLibrary(RULE_TYPE);
  }

  @Test
  public void testBundleLoaderIsCorrectlyPassedToTheLinker() throws Exception {
    checkBundleLoaderIsCorrectlyPassedToTheLinker(RULE_TYPE);
  }

  @Test
  public void testNoSrcs() throws Exception {
    checkNoSrcs(RULE_TYPE);
  }

  @Test
  public void testLipoBinaryAction() throws Exception {
    checkLipoBinaryAction(RULE_TYPE);
  }

  @Test
  public void testLinkActionHasCorrectIosSimulatorMinVersion() throws Exception {
    RULE_TYPE.scratchTarget(scratch, "srcs", "['a.m']", "platform_type", "'ios'");
    useConfiguration("--ios_multi_cpus=x86_64", "--ios_sdk_version=10.0", "--ios_minimum_os=8.0");
    checkLinkMinimumOSVersion(
        ConfigurationDistinguisher.APPLEBIN_IOS, "x86_64", "-mios-simulator-version-min=8.0");
  }

  @Test
  public void testLinkActionHasCorrectIosMinVersion() throws Exception {
    RULE_TYPE.scratchTarget(scratch, "srcs", "['a.m']", "platform_type", "'ios'");
    useConfiguration("--ios_multi_cpus=arm64", "--ios_sdk_version=10.0", "--ios_minimum_os=8.0");
    checkLinkMinimumOSVersion(
        ConfigurationDistinguisher.APPLEBIN_IOS, "arm64", "-miphoneos-version-min=8.0");
  }

  @Test
  public void testWatchSimulatorDepCompile() throws Exception {
    checkWatchSimulatorDepCompile(RULE_TYPE);
  }

  @Test
  public void testDylibBinaryType() throws Exception {
    RULE_TYPE.scratchTarget(scratch, "binary_type", "'dylib'");

    CommandAction linkAction = linkAction("//x:x");
    assertThat(Joiner.on(" ").join(linkAction.getArguments())).contains("-dynamiclib");
  }

  @Test
  public void testBinaryTypeIsCorrectlySetToBundle() throws Exception {
    RULE_TYPE.scratchTarget(scratch, "binary_type", "'loadable_bundle'");

    CommandAction linkAction = linkAction("//x:x");
    assertThat(Joiner.on(" ").join(linkAction.getArguments())).contains("-bundle");
  }

  @Test
  public void testMultiarchCcDep() throws Exception {
    checkMultiarchCcDep(RULE_TYPE);
  }

  @Test
  public void testWatchSimulatorLipoAction() throws Exception {
    checkWatchSimulatorLipoAction(RULE_TYPE);
  }

  @Test
  public void testLinkActionsWithSrcs() throws Exception {
    checkLinkActionsWithSrcs(RULE_TYPE, new ExtraLinkArgs());
  }

  @Test
  public void testFrameworkDepLinkFlags() throws Exception {
    checkFrameworkDepLinkFlags(RULE_TYPE, new ExtraLinkArgs());
  }

  @Test
  public void testDylibDependencies() throws Exception {
    checkDylibDependencies(RULE_TYPE, new ExtraLinkArgs());
  }

  @Test
  public void testMinimumOs() throws Exception {
    checkMinimumOsLinkAndCompileArg(RULE_TYPE);
  }

  @Test
  public void testMinimumOs_watchos() throws Exception {
    checkMinimumOsLinkAndCompileArg_watchos(RULE_TYPE);
  }

  @Test
  public void testMinimumOs_invalid_nonVersion() throws Exception {
    checkMinimumOs_invalid_nonVersion(RULE_TYPE);
  }

  @Test
  public void testMinimumOs_invalid_containsAlphabetic() throws Exception {
    checkMinimumOs_invalid_containsAlphabetic(RULE_TYPE);
  }

  @Test
  public void testMinimumOs_invalid_tooManyComponents() throws Exception {
    checkMinimumOs_invalid_tooManyComponents(RULE_TYPE);
  }

  @Test
  public void testGenfilesProtoGetsCorrectPath() throws Exception {
    scratch.file(
        "examples/BUILD",
        "package(default_visibility = ['//visibility:public'])",
        "apple_binary(",
        "    name = 'bin',",
        "    deps = [':objc_protos'],",
        "    platform_type = 'ios',",
        ")",
        "objc_proto_library(",
        "    name = 'objc_protos',",
        "    portable_proto_filters = ['filter.pbascii'],",
        "    deps = [':protos'],",
        ")",
        "proto_library(",
        "    name = 'protos',",
        "    srcs = ['genfile.proto'],",
        ")",
        "genrule(",
        "    name = 'copy_proto',",
        "    srcs = ['original.proto'],",
        "    outs = ['genfile.proto'],",
        "    cmd = '/bin/cp $< $@',",
        ")");

    useConfiguration("--ios_multi_cpus=armv7,arm64");

    Action lipoAction = actionProducingArtifact("//examples:bin", "_lipobin");
    ArrayList<String> genfileRoots = new ArrayList<>();

    for (Artifact archBinary : lipoAction.getInputs()) {
      if (archBinary.getExecPathString().endsWith("bin_bin")) {
        Artifact protoLib =
            getFirstArtifactEndingWith(
                getGeneratingAction(archBinary).getInputs(), "BundledProtos_0.a");
        Artifact protoObject =
            getFirstArtifactEndingWith(
                getGeneratingAction(protoLib).getInputs(), "Genfile.pbobjc.o");
        Artifact protoObjcSource =
            getFirstArtifactEndingWith(
                getGeneratingAction(protoObject).getInputs(), "Genfile.pbobjc.m");
        Artifact protoSource =
            getFirstArtifactEndingWith(
                getGeneratingAction(protoObjcSource).getInputs(), "genfile.proto");
        genfileRoots.add(protoSource.getRoot().getExecPathString());
      }
    }

    // Make sure there are genrules for both arm64 and armv7 configurations.
    Collections.sort(genfileRoots);
    assertThat(genfileRoots).hasSize(2);
    assertThat(genfileRoots.get(0)).contains("arm64");
    assertThat(genfileRoots.get(1)).contains("armv7");
  }

  @Test
  public void testDifferingProtoDepsPerArchitecture() throws Exception {
    scratch.file(
        "examples/BUILD",
        "package(default_visibility = ['//visibility:public'])",
        "apple_binary(",
        "    name = 'bin',",
        "    deps = [':objc_protos'],",
        "    platform_type = 'ios',",
        ")",
        "objc_proto_library(",
        "    name = 'objc_protos',",
        "    portable_proto_filters = ['filter.pbascii'],",
        "    deps = [':protos'],",
        ")",
        "proto_library(",
        "    name = 'protos',",
        "    srcs = select({",
        "        ':armv7': [ 'one.proto', ],",
        "        '//conditions:default': [ 'two.proto', ],",
        "    }),",
        ")",
        "config_setting(",
        "    name = 'armv7',",
        "    values = {'apple_split_cpu': 'armv7'},",
        ")");

    useConfiguration("--ios_multi_cpus=armv7,arm64");

    Action lipoAction = actionProducingArtifact("//examples:bin", "_lipobin");

    Artifact armv7Binary = getSingleArchBinary(lipoAction, "armv7");
    Artifact arm64Binary = getSingleArchBinary(lipoAction, "arm64");;

    Artifact armv7ProtoLib =
        getFirstArtifactEndingWith(
            getGeneratingAction(armv7Binary).getInputs(), "BundledProtos_0.a");
    Artifact armv7ProtoObject =
        getFirstArtifactEndingWith(
            getGeneratingAction(armv7ProtoLib).getInputs(), "One.pbobjc.o");
    Artifact armv7ProtoObjcSource =
        getFirstArtifactEndingWith(
            getGeneratingAction(armv7ProtoObject).getInputs(), "One.pbobjc.m");
    assertThat(getFirstArtifactEndingWith(
        getGeneratingAction(armv7ProtoObjcSource).getInputs(), "one.proto")).isNotNull();

    Artifact arm64ProtoLib =
        getFirstArtifactEndingWith(
            getGeneratingAction(arm64Binary).getInputs(), "BundledProtos_0.a");
    Artifact arm64ProtoObject =
        getFirstArtifactEndingWith(
            getGeneratingAction(arm64ProtoLib).getInputs(), "Two.pbobjc.o");
    Artifact arm64ProtoObjcSource =
        getFirstArtifactEndingWith(
            getGeneratingAction(arm64ProtoObject).getInputs(), "Two.pbobjc.m");
    assertThat(getFirstArtifactEndingWith(
        getGeneratingAction(arm64ProtoObjcSource).getInputs(), "two.proto")).isNotNull();
  }

  private Artifact getSingleArchBinary(Action lipoAction, String arch) throws Exception {
    for (Artifact archBinary : lipoAction.getInputs()) {
      String execPath = archBinary.getExecPathString();
      if (execPath.endsWith("bin_bin") && execPath.contains(arch)) {
        return archBinary;
      }
    }
    throw new AssertionError("Lipo action does not contain an input binary from arch " + arch);
  }

  private SkylarkDict<String, SkylarkDict<String, Artifact>>
      generateAppleDebugOutputsSkylarkProviderMap() throws Exception {
    scratch.file("examples/rule/BUILD");
    scratch.file(
        "examples/rule/apple_rules.bzl",
        "def _test_rule_impl(ctx):",
        "   dep = ctx.attr.deps[0]",
        "   provider = dep[apple_common.AppleDebugOutputs]",
        "   return struct(",
        "      outputs_map=provider.outputs_map,",
        "   )",
        "test_rule = rule(implementation = _test_rule_impl,",
        "   attrs = {",
        "   'deps': attr.label_list(",
        "       allow_files = False,",
        "       mandatory = False,",
        "       providers = [apple_common.AppleDebugOutputs],",
        "    )",
        "})");

    scratch.file(
        "examples/apple_skylark/BUILD",
        "package(default_visibility = ['//visibility:public'])",
        "load('/examples/rule/apple_rules', 'test_rule')",
        "apple_binary(",
        "    name = 'bin',",
        "    srcs = ['a.m'],",
        "    platform_type = 'ios',",
        ")",
        "test_rule(",
        "    name = 'my_target',",
        "    deps = [':bin'],",
        ")");
    ConfiguredTarget skylarkTarget = getConfiguredTarget("//examples/apple_skylark:my_target");

    // This cast is safe: struct providers are represented as SkylarkDict.
    @SuppressWarnings("unchecked")
    SkylarkDict<String, SkylarkDict<String, Artifact>> outputMap =
        (SkylarkDict<String, SkylarkDict<String, Artifact>>)
            skylarkTarget.get("outputs_map");
    return outputMap;
  }

  private void checkAppleDebugSymbolProvider_DsymEntries(
      SkylarkDict<String, SkylarkDict<String, Artifact>> outputMap) throws Exception {
    assertThat(outputMap).containsKey("arm64");
    assertThat(outputMap).containsKey("armv7");

    Map<String, Artifact> arm64 = outputMap.get("arm64");
    assertThat(arm64).containsEntry("bitcode_symbols", bitcodeSymbol("arm64"));
    assertThat(arm64.get("dsym_binary").getExecPathString()).isEqualTo(dsymBinaryPath("arm64"));

    Map<String, Artifact> armv7 = outputMap.get("armv7");
    assertThat(armv7).containsEntry("bitcode_symbols", bitcodeSymbol("armv7"));
    assertThat(armv7.get("dsym_binary").getExecPathString()).isEqualTo(dsymBinaryPath("armv7"));

    Map<String, Artifact> x8664 = outputMap.get("x86_64");
    // Simulator build has bitcode disabled.
    assertThat(x8664).doesNotContainKey("bitcode_symbols");
    assertThat(x8664.get("dsym_binary").getExecPathString()).isEqualTo(dsymBinaryPath("x86_64"));
  }

  private void checkAppleDebugSymbolProvider_LinkMapEntries(
      SkylarkDict<String, SkylarkDict<String, Artifact>> outputMap) throws Exception {
    assertThat(outputMap).containsKey("arm64");
    assertThat(outputMap).containsKey("armv7");

    Map<String, Artifact> arm64 = outputMap.get("arm64");
    assertThat(arm64.get("linkmap").getExecPathString()).isEqualTo(linkmapPath("arm64"));

    Map<String, Artifact> armv7 = outputMap.get("armv7");
    assertThat(armv7.get("linkmap").getExecPathString()).isEqualTo(linkmapPath("armv7"));

    Map<String, Artifact> x8664 = outputMap.get("x86_64");
    assertThat(x8664.get("linkmap").getExecPathString()).isEqualTo(linkmapPath("x86_64"));
  }

  @Test
  public void testAppleDebugSymbolProviderWithDsymsExposedToSkylark() throws Exception {
    useConfiguration(
        "--apple_bitcode=embedded", "--apple_generate_dsym", "--ios_multi_cpus=armv7,arm64,x86_64");
    checkAppleDebugSymbolProvider_DsymEntries(generateAppleDebugOutputsSkylarkProviderMap());
  }

  @Test
  public void testAppleDebugSymbolProviderWithLinkMapsExposedToSkylark() throws Exception {
    useConfiguration(
        "--apple_bitcode=embedded",
        "--objc_generate_linkmap",
        "--ios_multi_cpus=armv7,arm64,x86_64");
    checkAppleDebugSymbolProvider_LinkMapEntries(generateAppleDebugOutputsSkylarkProviderMap());
  }

  @Test
  public void testAppleDebugSymbolProviderWithDsymsAndLinkMapsExposedToSkylark() throws Exception {
    useConfiguration(
        "--apple_bitcode=embedded",
        "--objc_generate_linkmap",
        "--apple_generate_dsym",
        "--ios_multi_cpus=armv7,arm64,x86_64");

    SkylarkDict<String, SkylarkDict<String, Artifact>> outputMap =
        generateAppleDebugOutputsSkylarkProviderMap();
    checkAppleDebugSymbolProvider_DsymEntries(outputMap);
    checkAppleDebugSymbolProvider_LinkMapEntries(outputMap);
  }

  @Test
  public void testFilesToCompileOutputGroup() throws Exception {
    checkFilesToCompileOutputGroup(RULE_TYPE);
  }

  @Test
  public void testInstrumentedFilesProviderContainsDepsAndBundleLoaderFiles() throws Exception {
    useConfiguration("--collect_code_coverage");
    scratch.file(
        "examples/BUILD",
        "package(default_visibility = ['//visibility:public'])",
        "apple_binary(",
        "    name = 'bin',",
        "    deps = [':lib'],",
        "    platform_type = 'ios',",
        ")",
        "apple_binary(",
        "    name = 'bundle',",
        "    deps = [':bundle_lib'],",
        "    binary_type = '" + BinaryType.LOADABLE_BUNDLE + "',",
        "    bundle_loader = ':bin',",
        "    platform_type = 'ios',",
        ")",
        "objc_library(",
        "    name = 'lib',",
        "    srcs = ['lib.m'],",
        ")",
        "objc_library(",
        "    name = 'bundle_lib',",
        "    srcs = ['bundle_lib.m'],",
        ")");

    ConfiguredTarget bundleTarget = getConfiguredTarget("//examples:bundle");
    InstrumentedFilesProvider instrumentedFilesProvider =
        bundleTarget.getProvider(InstrumentedFilesProvider.class);
    assertThat(instrumentedFilesProvider).isNotNull();

    assertThat(Artifact.toRootRelativePaths(instrumentedFilesProvider.getInstrumentedFiles()))
        .containsAllOf("examples/lib.m", "examples/bundle_lib.m");
  }

  @Test
  public void testAppleSdkWatchsimulatorPlatformEnv() throws Exception {
    checkAppleSdkWatchsimulatorPlatformEnv(RULE_TYPE);
  }

  @Test
  public void testAppleSdkWatchosPlatformEnv() throws Exception {
    checkAppleSdkWatchosPlatformEnv(RULE_TYPE);
  }

  @Test
  public void testAppleSdkTvsimulatorPlatformEnv() throws Exception {
    checkAppleSdkTvsimulatorPlatformEnv(RULE_TYPE);
  }

  @Test
  public void testAppleSdkTvosPlatformEnv() throws Exception {
    checkAppleSdkTvosPlatformEnv(RULE_TYPE);
  }

  @Test
  public void testLinkActionHasCorrectWatchosSimulatorMinVersion() throws Exception {
    RULE_TYPE.scratchTarget(scratch,
        "srcs", "['a.m']",
        "platform_type", "'watchos'");
    useConfiguration(
        "--watchos_cpus=i386", "--watchos_sdk_version=3.0", "--watchos_minimum_os=2.0");
    checkLinkMinimumOSVersion(ConfigurationDistinguisher.APPLEBIN_WATCHOS, "i386",
        "-mwatchos-simulator-version-min=2.0");
  }

  @Test
  public void testLinkActionHasCorrectWatchosMinVersion() throws Exception {
    RULE_TYPE.scratchTarget(scratch,
        "srcs", "['a.m']",
        "platform_type", "'watchos'");
    useConfiguration(
        "--watchos_cpus=armv7k", "--watchos_sdk_version=3.0", "--watchos_minimum_os=2.0");
    checkLinkMinimumOSVersion(ConfigurationDistinguisher.APPLEBIN_WATCHOS, "armv7k",
        "-mwatchos-version-min=2.0");
  }

  @Test
  public void testLinkActionHasCorrectTvosSimulatorMinVersion() throws Exception {
    RULE_TYPE.scratchTarget(scratch,
        "srcs", "['a.m']",
        "platform_type", "'tvos'");
    useConfiguration(
        "--tvos_cpus=x86_64", "--tvos_sdk_version=10.1", "--tvos_minimum_os=10.0");
    checkLinkMinimumOSVersion(ConfigurationDistinguisher.APPLEBIN_TVOS, "x86_64",
        "-mtvos-simulator-version-min=10.0");
  }

  @Test
  public void testLinkActionHasCorrectTvosMinVersion() throws Exception {
    RULE_TYPE.scratchTarget(scratch,
        "srcs", "['a.m']",
        "platform_type", "'tvos'");
    useConfiguration(
        "--tvos_cpus=arm64", "--tvos_sdk_version=10.1", "--tvos_minimum_os=10.0");
    checkLinkMinimumOSVersion(ConfigurationDistinguisher.APPLEBIN_TVOS, "arm64",
        "-mtvos-version-min=10.0");
  }

  @Test
  public void testWatchSimulatorLinkAction() throws Exception {
    checkWatchSimulatorLinkAction(RULE_TYPE);
  }

  @Test
  public void testProtoBundlingAndLinking() throws Exception {
    checkProtoBundlingAndLinking(RULE_TYPE);
  }

  @Test
  public void testAvoidDepsObjects() throws Exception {
    checkAvoidDepsObjects(RULE_TYPE);
  }

  @Test
  public void testBundleLoaderPropagatesAppleExecutableBinaryProvider() throws Exception {
    scratch.file(
        "bin/BUILD",
        "apple_binary(",
        "    name = 'bin',",
        "    srcs = ['a.m'],",
        "    hdrs = ['a.h'],",
        "    platform_type = 'ios',",
        ")");
    scratch.file(
        "test/BUILD",
        "apple_binary(",
        "    name = 'test',",
        "    srcs = ['test.m'],",
        "    binary_type = 'loadable_bundle',",
        "    bundle_loader = '//bin:bin',",
        "    platform_type = 'ios',",
        ")");
    ConfiguredTarget binTarget = getConfiguredTarget("//bin:bin");
    AppleExecutableBinaryProvider executableBinaryProvider =
        (AppleExecutableBinaryProvider) binTarget.get(
            AppleExecutableBinaryProvider.SKYLARK_CONSTRUCTOR.getKey());
    assertThat(executableBinaryProvider).isNotNull();

    CommandAction testLinkAction = linkAction("//test:test");
    assertThat(testLinkAction.getInputs())
        .contains(executableBinaryProvider.getAppleExecutableBinary());
  }

  @Test
  public void testLoadableBundleBinaryAddsRpathLinkOptWithNoBundleLoader() throws Exception {
    scratch.file(
        "test/BUILD",
        "apple_binary(",
        "    name = 'test',",
        "    srcs = ['test.m'],",
        "    binary_type = 'loadable_bundle',",
        "    platform_type = 'ios',",
        ")");

    CommandAction testLinkAction = linkAction("//test:test");
    assertThat(Joiner.on(" ").join(testLinkAction.getArguments()))
        .contains("@loader_path/Frameworks");
  }

  @Test
  public void testLoadableBundleBinaryAddsRpathLinkOptWithBundleLoader() throws Exception {
    scratch.file(
        "bin/BUILD",
        "apple_binary(",
        "    name = 'bin',",
        "    srcs = ['a.m'],",
        "    hdrs = ['a.h'],",
        "    platform_type = 'ios',",
        ")");
    scratch.file(
        "test/BUILD",
        "apple_binary(",
        "    name = 'test',",
        "    srcs = ['test.m'],",
        "    binary_type = 'loadable_bundle',",
        "    bundle_loader = '//bin:bin',",
        "    platform_type = 'ios',",
        ")");

    CommandAction testLinkAction = linkAction("//test:test");
    assertThat(Joiner.on(" ").join(testLinkAction.getArguments()))
        .contains("@loader_path/Frameworks");
  }
}
