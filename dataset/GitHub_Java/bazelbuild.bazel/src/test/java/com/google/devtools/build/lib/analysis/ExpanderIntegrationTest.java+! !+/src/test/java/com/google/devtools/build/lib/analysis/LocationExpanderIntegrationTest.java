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

package com.google.devtools.build.lib.analysis;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link Expander}. */
@RunWith(JUnit4.class)
public class ExpanderIntegrationTest extends BuildViewTestCase {

  @Before
  public void createFiles() throws Exception {
    // Set up a rule to test expansion in.
    scratch.file("files/fileA");
    scratch.file("files/fileB");

    scratch.file(
        "files/BUILD",
        "filegroup(name='files',",
        "  srcs = ['fileA', 'fileB'])",
        "sh_library(name='lib',",
        "  deps = [':files'])");
  }

  private Expander makeExpander(String label) throws Exception {
    ConfiguredTarget target = getConfiguredTarget(label);
    RuleContext ruleContext = getRuleContext(target);
    return ruleContext.getExpander().withExecLocations(ImmutableMap.of());
  }

  @Test
  public void locations_spaces() throws Exception {
    scratch.file("spaces/file with space A");
    scratch.file("spaces/file with space B");
    scratch.file(
        "spaces/BUILD",
        "filegroup(name='files',",
        "  srcs = ['file with space A', 'file with space B'])",
        "sh_library(name='lib',",
        "  deps = [':files'])");

    Expander expander = makeExpander("//spaces:lib");
    String input = "foo $(locations :files) bar";
    String result = expander.expand(null, input);

    assertThat(result).isEqualTo("foo 'spaces/file with space A' 'spaces/file with space B' bar");
  }

  @Test
  public void otherPathExpansion() throws Exception {
    scratch.file(
        "expansion/BUILD",
        "genrule(name='foo', outs=['foo.txt'], cmd='never executed')",
        "sh_library(name='lib', srcs=[':foo'])");

    Expander expander = makeExpander("//expansion:lib");
    assertThat(expander.expand("<attribute>", "foo $(execpath :foo) bar"))
        .matches("foo .*-out/.*/expansion/foo\\.txt bar");
    assertThat(expander.expand("<attribute>", "foo $(execpaths :foo) bar"))
        .matches("foo .*-out/.*/expansion/foo\\.txt bar");
    assertThat(expander.expand("<attribute>", "foo $(rootpath :foo) bar"))
        .matches("foo expansion/foo.txt bar");
    assertThat(expander.expand("<attribute>", "foo $(rootpaths :foo) bar"))
        .matches("foo expansion/foo.txt bar");
  }

  @Test
  public void otherPathMultiExpansion() throws Exception {
    scratch.file(
        "expansion/BUILD",
        "genrule(name='foo', outs=['foo.txt', 'bar.txt'], cmd='never executed')",
        "sh_library(name='lib', srcs=[':foo'])");

    Expander expander = makeExpander("//expansion:lib");
    assertThat(expander.expand("<attribute>", "foo $(execpaths :foo) bar"))
        .matches("foo .*-out/.*/expansion/bar\\.txt .*-out/.*/expansion/foo\\.txt bar");
    assertThat(expander.expand("<attribute>", "foo $(rootpaths :foo) bar"))
        .matches("foo expansion/bar.txt expansion/foo.txt bar");
  }
}
