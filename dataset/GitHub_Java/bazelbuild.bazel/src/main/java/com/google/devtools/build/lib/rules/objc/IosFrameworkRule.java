// Copyright 2015 The Bazel Authors. All rights reserved.
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

import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;

import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.packages.ImplicitOutputsFunction;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;

/**
 * Rule definition for ios_framework.
 */
public class IosFrameworkRule implements RuleDefinition {

  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
    return builder
        .requiresConfigurationFragments(ObjcConfiguration.class, AppleConfiguration.class)
        // TODO(blaze-team): IPA is not right here, should probably be just zipped framework bundle.
        .setImplicitOutputsFunction(
            ImplicitOutputsFunction.fromFunctions(ReleaseBundlingSupport.IPA, XcodeSupport.PBXPROJ))
        /* <!-- #BLAZE_RULE(ios_framework).ATTRIBUTE(binary) -->
        The binary target included in the framework bundle.
        ${SYNOPSIS}
        <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
        .add(attr("binary", LABEL)
            .allowedRuleClasses("ios_framework_binary")
            .allowedFileTypes()
            .mandatory()
            .direct_compile_time_input()
            .cfg(IosExtension.MINIMUM_OS_AND_SPLIT_ARCH_TRANSITION))
        /* <!-- #BLAZE_RULE(ios_framework).ATTRIBUTE(hdrs) -->
        Public headers to include in the framework bundle.
        ${SYNOPSIS}
        <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
        .add(attr("hdrs", LABEL_LIST)
            .direct_compile_time_input()
            .allowedFileTypes(ObjcRuleClasses.HDRS_TYPE))
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return RuleDefinition.Metadata.builder()
        .name("ios_framework")
        .factoryClass(IosFramework.class)
        .ancestors(BaseRuleClasses.BaseRule.class, ObjcRuleClasses.ReleaseBundlingRule.class,
            ObjcRuleClasses.XcodegenRule.class)
        .build();
  }
}

/*<!-- #BLAZE_RULE (NAME = ios_framework, TYPE = BINARY, FAMILY = Objective-C) -->

<p>This rule produces a bundled binary for a framework from a compiled binary and bundle
metadata.</p>

<p>A framework is a bundle that contains a dynamic library (the "binary"), public headers (that
clients of the framework can use) and any resources. Framework headers are stripped out of the
final app bundle and only used during compilation stage.

<p>Bundles generated by this rule use a bundle directory called
<code>Frameworks/<var>framework_name</var>.framework</code>.

<!-- #END_BLAZE_RULE -->*/
