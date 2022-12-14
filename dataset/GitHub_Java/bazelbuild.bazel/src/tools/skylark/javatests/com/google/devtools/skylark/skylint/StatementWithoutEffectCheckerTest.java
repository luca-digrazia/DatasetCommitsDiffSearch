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

package com.google.devtools.skylark.skylint;

import com.google.common.truth.Truth;
import com.google.devtools.build.lib.syntax.BuildFileAST;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StatementWithoutEffectCheckerTest {
  private static List<Issue> findIssues(String... lines) {
    String content = String.join("\n", lines);
    BuildFileAST ast =
        BuildFileAST.parseSkylarkString(
            event -> {
              throw new IllegalArgumentException(event.getMessage());
            },
            content);
    return StatementWithoutEffectChecker.check(ast);
  }

  @Test
  public void reportUselessExpressionStatements() throws Exception {
    String messages =
        findIssues("1", "len", "'string'", "'a'.len", "1 + 1", "[1, 2, 3]").toString();
    Truth.assertThat(messages).contains(":1:1: expression result not used");
    Truth.assertThat(messages).contains(":2:1: expression result not used");
    Truth.assertThat(messages).contains(":3:1: expression result not used");
    Truth.assertThat(messages).contains(":4:1: expression result not used");
    Truth.assertThat(messages).contains(":5:1: expression result not used");
    Truth.assertThat(messages).contains(":6:1: expression result not used");
  }

  @Test
  public void testListComprehensions() throws Exception {
    Truth.assertThat(findIssues("[x for x in []] # has no effect").toString())
        .contains(":1:1: expression result not used");
    Truth.assertThat(
            findIssues(
                "[print(x) for x in range(5)] # allowed because top-level and has an effect"))
        .isEmpty();
    Truth.assertThat(
            findIssues(
                    "def f():", "  [print(x) for x in range(5)] # should be replaced by for-loop")
                .toString())
        .contains(":2:3: expression result not used");
  }

  @Test
  public void dontReportDocstrings() throws Exception {
    Truth.assertThat(findIssues("\"\"\" docstring \"\"\"", "def f():", "  \"\"\" docstring \"\"\""))
        .isEmpty();
  }

  @Test
  public void dontReportStatementsWithEffect() throws Exception {
    Truth.assertThat(
            findIssues(
                "print('test')",
                "[print(a) for a in range(10)]",
                "fail('foo')",
                "def f():",
                "  for a in range(5):",
                "    if a == 0:",
                "      print(a)",
                "    else:",
                "      fail('foo')"))
        .isEmpty();
  }
}
