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

package com.google.devtools.build.lib.rules.cpp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.util.ActionsTestUtil;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.util.AnalysisMock;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that {@link CppLinkstampCompileHelper} creates sane compile actions for linkstamps */
@RunWith(JUnit4.class)
public class CppLinkstampCompileHelperTest extends BuildViewTestCase {

  /** Tests that linkstamp compilation applies expected command line options. */
  @Test
  public void testLinkstampCompileOptionsForExecutable() throws Exception {
    AnalysisMock.get()
        .ccSupport()
        .setupCrosstool(mockToolsConfig, "builtin_sysroot: '/usr/local/custom-sysroot'");
    useConfiguration("--experimental_fix_linkstamp_inputs_bug");
    scratch.file(
        "x/BUILD",
        "cc_binary(",
        "  name = 'foo',",
        "  deps = ['a'],",
        ")",
        "cc_library(",
        "  name = 'a',",
        "  srcs = [ 'a.cc' ],",
        "  linkstamp = 'ls.cc',",
        ")");

    ConfiguredTarget target = getConfiguredTarget("//x:foo");
    Artifact executable = getExecutable(target);
    CppLinkAction generatingAction = (CppLinkAction) getGeneratingAction(executable);

    Artifact compiledLinkstamp =
        ActionsTestUtil.getFirstArtifactEndingWith(generatingAction.getInputs(), "ls.o");
    CppCompileAction linkstampCompileAction =
        (CppCompileAction) getGeneratingAction(compiledLinkstamp);

    List<String> arguments = linkstampCompileAction.getArguments();
    assertThatArgumentsAreValid(
        arguments,
        target.getConfiguration().getFragment(CppConfiguration.class).toString(),
        target.getLabel().getCanonicalForm(),
        executable.getFilename());
  }

  private void assertThatArgumentsAreValid(
      List<String> arguments, String platform, String targetName, String buildTargetNameSuffix) {
    assertThat(arguments).contains("--sysroot=/usr/local/custom-sysroot");
    assertThat(arguments).contains("-include");
    assertThat(arguments).contains("-DG3_VERSION_INFO=\"" + targetName + "\"");
    assertThat(arguments).contains("-DG3_TARGET_NAME=\"" + targetName + "\"");
    assertThat(arguments).contains("-DGPLATFORM=\"" + platform + "\"");
    assertThat(arguments).contains("-I.");
    String correctG3BuildTargetPattern = "-DG3_BUILD_TARGET=\".*" + buildTargetNameSuffix + "\"";
    assertThat(Iterables.tryFind(arguments, (arg) -> arg.matches(correctG3BuildTargetPattern)))
        .named("in " + arguments + " flag matching " + correctG3BuildTargetPattern)
        .isPresent();
    String fdoStampPattern = "-D" + CppConfiguration.FDO_STAMP_MACRO + "=\".*\"";
    assertThat(Iterables.tryFind(arguments, (arg) -> arg.matches(fdoStampPattern)))
        .named("in " + arguments + " flag matching " + fdoStampPattern)
        .isAbsent();
  }

  /** Tests that linkstamp compilation applies expected command line options. */
  @Test
  public void testLinkstampCompileOptionsForSharedLibrary() throws Exception {
    AnalysisMock.get()
        .ccSupport()
        .setupCrosstool(mockToolsConfig, "builtin_sysroot: '/usr/local/custom-sysroot'");
    useConfiguration("--experimental_fix_linkstamp_inputs_bug");
    scratch.file(
        "x/BUILD",
        "cc_binary(",
        "  name = 'libfoo.so',",
        "  deps = ['a'],",
        "  linkshared = 1,",
        ")",
        "cc_library(",
        "  name = 'a',",
        "  srcs = [ 'a.cc' ],",
        "  linkstamp = 'ls.cc',",
        ")");

    ConfiguredTarget target = getConfiguredTarget("//x:libfoo.so");
    Artifact executable = getExecutable(target);
    CppLinkAction generatingAction = (CppLinkAction) getGeneratingAction(executable);
    Artifact compiledLinkstamp =
        ActionsTestUtil.getFirstArtifactEndingWith(generatingAction.getInputs(), "ls.o");
    assertThat(generatingAction.getInputs()).contains(compiledLinkstamp);

    CppCompileAction linkstampCompileAction =
        (CppCompileAction) getGeneratingAction(compiledLinkstamp);

    List<String> arguments = linkstampCompileAction.getArguments();
    assertThatArgumentsAreValid(
        arguments,
        target.getConfiguration().getFragment(CppConfiguration.class).toString(),
        target.getLabel().getCanonicalForm(),
        executable.getFilename());
  }

  @Test
  public void testLinkstampRespectsPicnessFromConfiguration() throws Exception {
    useConfiguration("--force_pic");
    scratch.file(
        "x/BUILD",
        "cc_binary(",
        "  name = 'foo',",
        "  deps = ['a'],",
        ")",
        "cc_library(",
        "  name = 'a',",
        "  srcs = [ 'a.cc' ],",
        "  linkstamp = 'ls.cc',",
        ")");
    ConfiguredTarget target = getConfiguredTarget("//x:foo");
    Artifact executable = getExecutable(target);
    CppLinkAction generatingAction = (CppLinkAction) getGeneratingAction(executable);
    Artifact compiledLinkstamp =
        ActionsTestUtil.getFirstArtifactEndingWith(generatingAction.getInputs(), "ls.o");
    assertThat(generatingAction.getInputs()).contains(compiledLinkstamp);

    CppCompileAction linkstampCompileAction =
        (CppCompileAction) getGeneratingAction(compiledLinkstamp);
    assertThat(linkstampCompileAction.getArguments()).contains("-fPIC");
  }

  @Test
  public void testLinkstampRespectsFdoFromConfiguration() throws Exception {
    useConfiguration("--fdo_instrument=foo");
    scratch.file(
        "x/BUILD",
        "cc_binary(",
        "  name = 'foo',",
        "  deps = ['a'],",
        ")",
        "cc_library(",
        "  name = 'a',",
        "  srcs = [ 'a.cc' ],",
        "  linkstamp = 'ls.cc',",
        ")");
    ConfiguredTarget target = getConfiguredTarget("//x:foo");
    Artifact executable = getExecutable(target);
    CppLinkAction generatingAction = (CppLinkAction) getGeneratingAction(executable);
    Artifact compiledLinkstamp =
        ActionsTestUtil.getFirstArtifactEndingWith(generatingAction.getInputs(), "ls.o");
    assertThat(generatingAction.getInputs()).contains(compiledLinkstamp);

    CppCompileAction linkstampCompileAction =
        (CppCompileAction) getGeneratingAction(compiledLinkstamp);
    assertThat(linkstampCompileAction.getArguments())
        .contains("-D" + CppConfiguration.FDO_STAMP_MACRO + "=\"FDO\"");
  }

  /**
   * Regression test for b/73447914: Linkstamps were not re-built when only volatile data changed,
   * i.e. when we modified cc_binary source, linkstamp was not recompiled so we got old timestamps.
   * The proper behavior is to recompile linkstamp whenever any input to cc_binary action changes.
   * And the current implementation solves this by adding all linking inputs as inputs to linkstamp
   * compile action.
   */
  @Test
  public void testLinkstampCompileDependsOnAllCcBinaryLinkingInputs() throws Exception {
    scratch.file(
        "x/BUILD",
        "cc_binary(",
        "  name = 'foo',",
        "  deps = ['bar'],",
        "  srcs = [ 'main.cc' ],",
        ")",
        "cc_library(",
        "  name = 'bar',",
        "  srcs = [ 'bar.cc' ],",
        "  linkstamp = 'ls.cc',",
        ")");
    useConfiguration("--experimental_fix_linkstamp_inputs_bug");

    ConfiguredTarget target = getConfiguredTarget("//x:foo");
    Artifact executable = getExecutable(target);
    CcToolchainProvider toolchain =
        CppHelper.getToolchainUsingDefaultCcToolchainAttribute(getRuleContext(target));
    boolean usePic =
        CppHelper.usePicObjectsForBinaries(
            target.getConfiguration().getFragment(CppConfiguration.class), toolchain);

    CppLinkAction generatingAction = (CppLinkAction) getGeneratingAction(executable);

    Artifact compiledLinkstamp =
        ActionsTestUtil.getFirstArtifactEndingWith(generatingAction.getInputs(), "ls.o");
    CppCompileAction linkstampCompileAction =
        (CppCompileAction) getGeneratingAction(compiledLinkstamp);

    Artifact mainObject =
        ActionsTestUtil.getFirstArtifactEndingWith(
            generatingAction.getInputs(), usePic ? "main.pic.o" : "main.o");
    Artifact bar =
        ImmutableList.copyOf(generatingAction.getInputs())
            .stream()
            .filter(a -> a.getExecPath().getBaseName().contains("bar"))
            .findFirst()
            .get();
    ImmutableList<Artifact> linkstampInputs =
        ImmutableList.copyOf(linkstampCompileAction.getInputs());
    assertThat(linkstampInputs).containsAllOf(mainObject, bar);
  }
}
