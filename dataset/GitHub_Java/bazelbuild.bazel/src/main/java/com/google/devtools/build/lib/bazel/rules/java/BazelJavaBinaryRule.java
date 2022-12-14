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

package com.google.devtools.build.lib.bazel.rules.java;

import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.Type.BOOLEAN;

import com.google.devtools.build.lib.analysis.BlazeRule;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.bazel.rules.BazelBaseRuleClasses;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaRuleClasses.BaseJavaBinaryRule;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.AttributeMap;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;

/**
 * Rule definition for the java_binary rule.
 */
@BlazeRule(name = "java_binary",
           ancestors = { BaseJavaBinaryRule.class,
                         BazelBaseRuleClasses.BinaryBaseRule.class },
           factoryClass = BazelJavaBinary.class)
public final class BazelJavaBinaryRule implements RuleDefinition {
  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment env) {
    /* <!-- #BLAZE_RULE(java_binary).NAME -->
    <br/>It is good practice to use the name of the source file that is the main entry point of the
    application (minus the extension). For example, if your entry point is called
    <code>Main.java</code>, then your name could be <code>Main</code>.
    <!-- #END_BLAZE_RULE.NAME --> */
    return builder
        /* <!-- #BLAZE_RULE(java_binary).IMPLICIT_OUTPUTS -->
        <ul>
          <li><code><var>name</var>.jar</code>: A Java archive, containing the class files and other
            resources corresponding to the binary's direct dependencies.</li>
          <li><code><var>name</var>-src.jar</code>: An archive containing the sources ("source
            jar").</li>
          <li><code><var>name</var>_deploy.jar</code>: A Java archive suitable for deployment (only
            built if explicitly requested).
            <p>
              Building the <code><var>name</var>&gt;_deploy.jar</code> target for your rule creates
              a self-contained jar file with a manifest that allows it to be run with the <code>java
              -jar</code> command or with the wrapper script's <code>--singlejar</code> option.
              Using the wrapper script is preferred to <code>java -jar</code> because it also passes
              the <a href="#java_binary.jvm_flags">JVM flags</a> and the options to load native
              libraries.
            </p>
            <p>
              The deploy jar contains all the classes that would be found by a classloader that
              searched the classpath from the binary's wrapper script from beginning to end. It also
              contains the native libraries needed for dependencies. These are automatically loaded
              into the JVM at runtime.
            </p>
          </li>
          <li><code><var>name</var>_deploy-src.jar</code>: An archive containing the sources
            collected from the transitive closure of the target. These will match the classes in the
            <code>deploy.jar</code> except where jars have no matching source jar.</li>
        </ul>
        <!-- #END_BLAZE_RULE.IMPLICIT_OUTPUTS --> */
        .setImplicitOutputsFunction(BazelJavaRuleClasses.JAVA_BINARY_IMPLICIT_OUTPUTS)
        .override(attr("$is_executable", BOOLEAN).nonconfigurable("automatic").value(
            new Attribute.ComputedDefault() {
              @Override
              public Object getDefault(AttributeMap rule) {
                return rule.get("create_executable", BOOLEAN);
              }
            }))
        .build();
  }
}

/*<!-- #BLAZE_RULE (NAME = java_binary, TYPE = BINARY, FAMILY = Java) -->

${ATTRIBUTE_SIGNATURE}

<p>
  Builds a Java archive ("jar file"), plus a wrapper shell script with the same name as the rule.
  The wrapper shell script uses a classpath that includes, among other things, a jar file for each
  library on which the binary depends.
</p>
<p>
  The wrapper script accepts several unique flags. Refer to
  <code>//src/main/java/com/google/devtools/build/lib/bazel/rules/java/java_stub_template.txt</code>
  for a list of configurable flags and environment variables accepted by the wrapper.
</p>

${IMPLICIT_OUTPUTS}

<p>
  A <code>deps</code> attribute is not allowed in a <code>java_binary</code> rule without
  <a href="#java_binary.srcs"><code>srcs</code></a>; such a rule requires a
  <a href="#java_binary.main_class"><code>main_class</code></a> provided by
  <a href="#java_binary.runtime_deps"><code>runtime_deps</code></a>.
</p>

<p>The following code snippet illustrates a common mistake:</p>

<pre class="code">
java_binary(
    name = "DontDoThis",
    srcs = [
        <var>...</var>,
        <code class="deprecated">"GeneratedJavaFile.java"</code>,  # a generated .java file
    ],
    deps = [<code class="deprecated">":generating_rule",</code>],  # rule that generates that file
)
</pre>

<p>Do this instead:</p>

<pre class="code">
java_binary(
    name = "DoThisInstead",
    srcs = [
        <var>...</var>,
        ":generating_rule",
    ],
)
</pre>

${ATTRIBUTE_DEFINITION}

<!-- #END_BLAZE_RULE -->*/
