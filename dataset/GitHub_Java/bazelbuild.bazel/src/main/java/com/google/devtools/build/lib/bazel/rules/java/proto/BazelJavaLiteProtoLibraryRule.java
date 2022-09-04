// Copyright 2016 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.bazel.rules.java.proto;

import static com.google.devtools.build.lib.bazel.rules.java.proto.BazelJavaLiteProtoAspect.DEFAULT_PROTO_TOOLCHAIN_LABEL;
import static com.google.devtools.build.lib.packages.Aspect.INJECTING_RULE_KIND_PARAMETER_KEY;
import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;
import static com.google.devtools.build.lib.rules.java.proto.JavaLiteProtoAspect.PROTO_TOOLCHAIN_ATTR;
import static com.google.devtools.build.lib.rules.java.proto.JavaLiteProtoAspect.getProtoToolchainLabel;
import static com.google.devtools.build.lib.syntax.Type.BOOLEAN;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.packages.AspectParameters;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.SkylarkProviderIdentifier;
import com.google.devtools.build.lib.rules.java.JavaConfiguration;
import com.google.devtools.build.lib.rules.java.JavaInfo;
import com.google.devtools.build.lib.rules.java.proto.JavaLiteProtoLibrary;
import com.google.devtools.build.lib.rules.proto.ProtoLangToolchainProvider;

/** Declaration of the {@code java_lite_proto_library} rule. */
public class BazelJavaLiteProtoLibraryRule implements RuleDefinition {

  private final BazelJavaLiteProtoAspect javaProtoAspect;

  public BazelJavaLiteProtoLibraryRule(BazelJavaLiteProtoAspect javaProtoAspect) {
    this.javaProtoAspect = javaProtoAspect;
  }

  @Override
  public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment environment) {
    Function<Rule, AspectParameters> aspectParameters =
        rule ->
            new AspectParameters.Builder()
                .addAttribute(INJECTING_RULE_KIND_PARAMETER_KEY, "java_lite_proto_library")
                .build();

    return builder
        .requiresConfigurationFragments(JavaConfiguration.class)
        /* <!-- #BLAZE_RULE(java_lite_proto_library).ATTRIBUTE(deps) -->
        The list of <a href="protocol-buffer.html#proto_library"><code>proto_library</code></a>
        rules to generate Java code for.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .override(
            attr("deps", LABEL_LIST)
                .allowedRuleClasses("proto_library")
                .allowedFileTypes()
                .aspect(javaProtoAspect, aspectParameters))
        .add(attr("strict_deps", BOOLEAN).value(true).undocumented("for migration"))
        .add(
            attr(PROTO_TOOLCHAIN_ATTR, LABEL)
                .mandatoryNativeProviders(
                    ImmutableList.<Class<? extends TransitiveInfoProvider>>of(
                        ProtoLangToolchainProvider.class))
                .value(getProtoToolchainLabel(DEFAULT_PROTO_TOOLCHAIN_LABEL)))
        .advertiseSkylarkProvider(SkylarkProviderIdentifier.forKey(JavaInfo.PROVIDER.getKey()))
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return RuleDefinition.Metadata.builder()
        .name("java_lite_proto_library")
        .factoryClass(JavaLiteProtoLibrary.class)
        .ancestors(BaseRuleClasses.RuleBase.class)
        .build();
  }
}

/*<!-- #BLAZE_RULE (NAME = java_lite_proto_library, TYPE = LIBRARY, FAMILY = Java) -->

<p>
<code>java_lite_proto_library</code> generates Java code from <code>.proto</code> files.
</p>

<p>
<code>deps</code> must point to <a href="protocol-buffer.html#proto_library"><code>proto_library
</code></a> rules.
</p>

<p>
Example:
</p>

<pre class="code">
java_library(
    name = "lib",
    deps = [":foo"],
)

java_lite_proto_library(
    name = "foo",
    deps = [":bar"],
)

proto_library(
    name = "bar",
)
</pre>


<!-- #END_BLAZE_RULE -->*/
