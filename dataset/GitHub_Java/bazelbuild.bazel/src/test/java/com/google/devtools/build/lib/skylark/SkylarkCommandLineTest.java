// Copyright 2014 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.skylark;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.build.lib.skylark.util.SkylarkTestCase;
import com.google.devtools.build.lib.syntax.SkylarkList;

/**
 * Tests for {@link SkylarkCommandLine}.
 */
public class SkylarkCommandLineTest extends SkylarkTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    scratch.file(
        "foo/BUILD",
        "genrule(name = 'foo',",
        "  cmd = 'dummy_cmd',",
        "  srcs = ['a.txt', 'b.img'],",
        "  tools = ['t.exe'],",
        "  outs = ['c.txt'])");
  }

  public void testCmdHelperAll() throws Exception {
    Object result =
        evalRuleContextCode(
            createRuleContext("//foo:foo"),
            "cmd_helper.template(set(ruleContext.files.srcs), '--%{short_path}=%{path}')");
    SkylarkList list = (SkylarkList) result;
    assertThat(list).containsExactly("--foo/a.txt=foo/a.txt", "--foo/b.img=foo/b.img").inOrder();
  }
}
