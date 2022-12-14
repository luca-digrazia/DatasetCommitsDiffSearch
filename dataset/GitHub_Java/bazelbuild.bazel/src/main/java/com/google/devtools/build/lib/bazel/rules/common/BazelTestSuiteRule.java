// Copyright 2014 Google Inc. All rights reserved.
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
package com.google.devtools.build.lib.bazel.rules.common;

import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.Type.BOOLEAN;
import static com.google.devtools.build.lib.packages.Type.LABEL_LIST;

import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.BlazeRule;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.rules.test.TestSuite;

/**
 * Rule object implementing "test_suite".
 */
@BlazeRule(name = "test_suite",
             ancestors = { BaseRuleClasses.BaseRule.class },
             factoryClass = TestSuite.class)
public final class BazelTestSuiteRule implements RuleDefinition {
  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment env) {
    return builder
        .override(attr("testonly", BOOLEAN).value(true)
            .nonconfigurable("policy decision: should be consistent across configurations"))
        /* <!-- #BLAZE_RULE(test_suite).ATTRIBUTE(tags) -->
        List of text tags such as "small" or "database" or "-flaky". Tags may be any valid string.
        ${SYNOPSIS}
        <p>
          Tags which begin with a "-" character are considered negative tags. The
          preceding "-" character is not considered part of the tag, so a suite tag
          of "-small" matches a test's "small" size. All other tags are considered
          positive tags.
        </p>
        <p>
          Optionally, to make positive tags more explicit, tags may also begin with the
          "+" character, which will not be evaluated as part of the text of the tag. It
          merely makes the positive and negative distinction easier to read.
        </p>
        <p>
          Only test rules that match <b>all</b> of the positive tags and <b>none</b> of the negative
          tags will be included in the test suite. Note that this does not mean that error checking
          for dependencies on tests that are filtered out is skipped; the dependencies on skipped
          tests still need to be legal (e.g. not blocked by visibility or obsoleteness constraints).
        </p>
        <p>
          The <code>manual</code> tag keyword is treated specially. It marks the
          <code>test_suite</code> target as "manual" so that it will be ignored by the wildcard
          expansion and automated testing facilities. It does not work as a filter on the set
          of tests in the suite. So when the <code>manual</code> tag is used on a test_suite, test
          rules do not have to be tagged as <code>manual</code> to be included in the test suite.
        </p>
        <p>
          Note that a test's <code>size</code> is treated as a tag for the purpose of filtering.
        </p>
        <p>
          If you need a <code>test_suite</code> that contains tests with mutually exclusive tags
          (e.g. all small and medium tests), you'll have to create three <code>test_suite</code>
          rules: one for all small tests, one for all medium tests, and one that includes the
          previous two.
        </p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */

        // TODO(bazel-team): we should have a boolean attribute instead of the "manual" hack

        /* <!-- #BLAZE_RULE(test_suite).ATTRIBUTE(tests) -->
        A list of test suites and test targets of any language.
        ${SYNOPSIS}
        <p>
          Any <code>*_test</code> is accepted here, independent of the language. No
          <code>*_binary</code> targets are accepted however, even if they happen to run a test.
          Filtering by the specified <code>tags</code> is only done for tests listed directly in
          this attribute. If this attribute contains <code>test_suite</code>s, the tests inside
          those will not be filtered by this <code>test_suite</code> (they are considered to be
          filtered already).
        </p>
        <p>
          If the <code>tests</code> attribute is unspecified or empty, the rule will default to
          including all test rules in the current BUILD file that are not tagged as
          <code>manual</code> or marked as <code>obsolete</code>. These rules are still subject
          to <code>tag</code> filtering.
        </p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("tests", LABEL_LIST).orderIndependent().allowedFileTypes()
            .nonconfigurable("policy decision: should be consistent across configurations"))
        // This magic attribute contains all *test rules in the package, iff
        // tests=[] and suites=[]:
        .add(attr("$implicit_tests", LABEL_LIST)
            .nonconfigurable("Accessed in TestTargetUtils without config context"))
        .build();
  }
}
