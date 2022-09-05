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

package com.google.devtools.build.lib.bazel.rules.java;

import static com.google.devtools.build.lib.packages.Attribute.ConfigurationTransition.HOST;
import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;
import static com.google.devtools.build.lib.packages.BuildType.TRISTATE;
import static com.google.devtools.build.lib.syntax.Type.BOOLEAN;
import static com.google.devtools.build.lib.syntax.Type.STRING;

import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaRuleClasses.BaseJavaBinaryRule;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.packages.TriState;
import com.google.devtools.build.lib.rules.java.JavaConfiguration;
import com.google.devtools.build.lib.rules.java.JavaSemantics;
import com.google.devtools.build.lib.rules.java.Jvm;

/**
 * Rule definition for the java_test rule.
 */
public final class BazelJavaTestRule implements RuleDefinition {

  private static final String JUNIT4_RUNNER = "org.junit.runner.JUnitCore";

  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment env) {
    /* <!-- #BLAZE_RULE(java_test).IMPLICIT_OUTPUTS -->
    <ul>
      <li><code><var>name</var>.jar</code>: A Java archive.</li>
      <li><code><var>name</var>_deploy.jar</code>: A Java archive suitable for deployment. (Only
        built if explicitly requested.)</li>
    </ul>
    <!-- #END_BLAZE_RULE.IMPLICIT_OUTPUTS --> */
    return builder
        .requiresConfigurationFragments(JavaConfiguration.class, Jvm.class)
        .setImplicitOutputsFunction(BazelJavaRuleClasses.JAVA_BINARY_IMPLICIT_OUTPUTS)
        // Proguard can be run over java_test targets using the --java_optimization_mode flag.
        // Primarily this is intended to help test changes to Proguard.
        .add(attr(":proguard", LABEL).cfg(HOST).value(JavaSemantics.PROGUARD).exec())
        .add(attr(":extra_proguard_specs", LABEL_LIST).value(JavaSemantics.EXTRA_PROGUARD_SPECS))
        .override(attr("stamp", TRISTATE).value(TriState.NO))
        .override(attr("use_testrunner", BOOLEAN).value(true))
        // TODO(dmarting): remove once we drop the legacy bazel java_test behavior.
        .override(attr("main_class", STRING).value(JUNIT4_RUNNER))
        /* <!-- #BLAZE_RULE(java_test).ATTRIBUTE(test_class) -->
        The Java class to be loaded by the test runner.<br/>
        <p>
          By default, if this argument is not defined then the legacy mode is used and the
          test arguments are used instead. Set the <code>--nolegacy_bazel_java_test</code> flag
          to not fallback on the first argument.
        </p>
        <p>
          This attribute specifies the name of a Java class to be run by
          this test. It is rare to need to set this. If this argument is omitted, the Java class
          whose name corresponds to the <code>name</code> of this
          <code>java_test</code> rule will be used.
        </p>
        <p>
          For JUnit3, the test class needs to either be a subclass of
          <code>junit.framework.TestCase</code> or it needs to have a public
          static <code>suite()</code> method that returns a
          <code>junit.framework.Test</code> (or a subclass of <code>Test</code>).
          For JUnit4, the class needs to be annotated with
          <code>org.junit.runner.RunWith</code>.
        </p>
        <p>
          This attribute allows several <code>java_test</code> rules to
          share the same <code>Test</code>
          (<code>TestCase</code>, <code>TestSuite</code>, ...).  Typically
          additional information is passed to it
          (e.g. via <code>jvm_flags=['-Dkey=value']</code>) so that its
          behavior differs in each case, such as running a different
          subset of the tests.  This attribute also enables the use of
          Java tests outside the <code>javatests</code> tree.
        </p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("test_class", STRING))
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return RuleDefinition.Metadata.builder()
        .name("java_test")
        .type(RuleClassType.TEST)
        .ancestors(BaseJavaBinaryRule.class, BaseRuleClasses.TestBaseRule.class)
        .factoryClass(BazelJavaTest.class)
        .build();
  }
}

/*<!-- #BLAZE_RULE (NAME = java_test, TYPE = TEST, FAMILY = Java) -->

<p>
A <code>java_test()</code> rule compiles a Java test. A test is a binary wrapper around your
test code. The test runner's main method is invoked instead of the main class being compiled.
</p>

${IMPLICIT_OUTPUTS}

<p>
See the section on <a href="${link java_binary_args}">java_binary()</a> arguments. This rule also
supports all <a href="${link common-definitions#common-attributes-tests}">attributes common
to all test rules (*_test)</a>.
</p>

<h4 id="java_test_examples">Examples</h4>

<pre class="code">
java_library(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/foo/base:testResources",
        "//java/com/foo/testing/util",
    ],
)

java_test(
    name = "AllTests",
    size = "small",
    runtime_deps = [
        ":tests",
        "//util/mysql",
    ],
)
</pre>

<!-- #END_BLAZE_RULE -->*/
