// Copyright 2006 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.rules.filegroup;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.build.lib.actions.util.ActionsTestUtil;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.FileConfiguredTarget;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import com.google.devtools.build.lib.util.FileType;

import java.io.IOException;
import java.util.Arrays;

/**
 * Tests for {@link Filegroup}.
 */
public class FilegroupConfiguredTargetTest extends BuildViewTestCase {

  public void testGroup() throws Exception {
    scratch.file("nevermore/BUILD",
        "filegroup(name  = 'staticdata',",
        "          srcs = ['staticdata/spam.txt', 'staticdata/good.txt'])");
    ConfiguredTarget groupTarget = getConfiguredTarget("//nevermore:staticdata");
    assertEquals(Arrays.asList("nevermore/staticdata/spam.txt",
                               "nevermore/staticdata/good.txt"),
        ActionsTestUtil.prettyArtifactNames(getFilesToBuild(groupTarget)));
  }

  public void testDependencyGraph() throws Exception {
    scratch.file("java/com/google/test/BUILD",
        "java_binary(name  = 'test_app',",
        "    resources = [':data'],",
        "    srcs  = ['InputFile.java', 'InputFile2.java'])",
        "filegroup(name  = 'data',",
        "          srcs = ['b.txt', 'a.txt'])");
    FileConfiguredTarget appOutput =
        getFileConfiguredTarget("//java/com/google/test:test_app.jar");
    assertEquals("b.txt a.txt", actionsTestUtil().predecessorClosureOf(
        appOutput.getArtifact(), FileType.of(".txt")));
  }

  public void testEmptyGroupIsAnOk() throws Exception {
    scratchConfiguredTarget("empty", "empty",
        "filegroup(name='empty', srcs=[])");
  }

  public void testEmptyGroupInGenruleIsOk() throws Exception {
    scratchConfiguredTarget("empty", "genempty",
        "filegroup(name='empty', srcs=[])",
        "genrule(name='genempty', tools=[':empty'], outs=['nothing'], cmd='touch $@')");
  }

  private void writeTest() throws IOException {
    scratch.file("another/BUILD",
        "filegroup(name  = 'another',",
        "          srcs = ['another.txt'])");
    scratch.file("test/BUILD",
        "filegroup(name  = 'a',",
        "          srcs = ['a.txt'])",
        "filegroup(name  = 'b',",
        "          srcs = ['a.txt'])",
        "filegroup(name  = 'c',",
        "          srcs = ['a', 'b.txt'])",
        "filegroup(name  = 'd',",
        "          srcs = ['//another:another.txt'])");
  }

  public void testFileCanBeSrcsOfMultipleRules() throws Exception {
    writeTest();
    assertEquals(Arrays.asList("test/a.txt"),
        ActionsTestUtil.prettyArtifactNames(getFilesToBuild(getConfiguredTarget("//test:a"))));
    assertEquals(Arrays.asList("test/a.txt"),
        ActionsTestUtil.prettyArtifactNames(getFilesToBuild(getConfiguredTarget("//test:b"))));
  }

  public void testRuleCanBeSrcsOfOtherRule() throws Exception {
    writeTest();
    assertEquals(Arrays.asList("test/a.txt", "test/b.txt"),
        ActionsTestUtil.prettyArtifactNames(getFilesToBuild(getConfiguredTarget("//test:c"))));
  }

  public void testOtherPackageCanBeSrcsOfRule() throws Exception {
    writeTest();
    assertEquals(Arrays.asList("another/another.txt"),
        ActionsTestUtil.prettyArtifactNames(getFilesToBuild(getConfiguredTarget("//test:d"))));
  }

  public void testIsNotExecutable() throws Exception {
    scratch.file("x/BUILD",
                "filegroup(name = 'not_exec_two_files', srcs = ['bin', 'bin.sh'])");
    assertNull(getExecutable("//x:not_exec_two_files"));
  }

  public void testIsExecutable() throws Exception {
    scratch.file("x/BUILD",
                "filegroup(name = 'exec', srcs = ['bin'])");
    assertEquals("x/bin", getExecutable("//x:exec").getExecPath().getPathString());
  }

  public void testNoDuplicate() throws Exception {
    scratch.file("x/BUILD",
                "filegroup(name = 'a', srcs = ['file'])",
                "filegroup(name = 'b', srcs = ['file'])",
                "filegroup(name = 'c', srcs = [':a', ':b'])");
    assertEquals(Arrays.asList("x/file"),
        ActionsTestUtil.prettyArtifactNames(getFilesToBuild(getConfiguredTarget("//x:c"))));
  }

  public void testGlobMatchesRuleOutputsInsteadOfFileWithTheSameName() throws Exception {
    scratch.file("pkg/file_or_rule");
    scratch.file("pkg/a.txt");
    ConfiguredTarget target = scratchConfiguredTarget("pkg", "my_rule",
                "filegroup(name = 'file_or_rule', srcs = ['a.txt'])",
                "filegroup(name = 'my_rule', srcs = glob(['file_or_rule']))");
    assertThat(ActionsTestUtil.baseArtifactNames(getFilesToBuild(target))).containsExactly("a.txt");
  }
}
