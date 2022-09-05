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

import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.packages.ImplicitOutputsFunction;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;
import com.google.devtools.build.lib.rules.objc.ObjcRuleClasses.IpaRule;

/**
 * Rule definition for ios_extension.
 */
public class IosExtensionRule implements RuleDefinition {
  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment env) {
    return builder
        .requiresConfigurationFragments(ObjcConfiguration.class, AppleConfiguration.class)
        /*<!-- #BLAZE_RULE(ios_extension).IMPLICIT_OUTPUTS -->
        <ul>
         <li><code><var>name</var>.ipa</code>: the extension bundle as an <code>.ipa</code>
             file</li>
         <li><code><var>name</var>.xcodeproj/project.pbxproj</code>: An Xcode project file which
             can be used to develop or build on a Mac.</li>
        </ul>
        <!-- #END_BLAZE_RULE.IMPLICIT_OUTPUTS -->*/
        .setImplicitOutputsFunction(
            ImplicitOutputsFunction.fromFunctions(ReleaseBundlingSupport.IPA, XcodeSupport.PBXPROJ))
        /* <!-- #BLAZE_RULE(ios_extension).ATTRIBUTE(binary) -->
        The binary target containing the logic for the extension.
        <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
        .add(attr("binary", LABEL)
            .allowedRuleClasses("ios_extension_binary")
            .allowedFileTypes()
            .mandatory()
            .direct_compile_time_input()
            .cfg(IosExtension.MINIMUM_OS_AND_SPLIT_ARCH_TRANSITION))
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return RuleDefinition.Metadata.builder()
        .name("ios_extension")
        .factoryClass(IosExtension.class)
        .ancestors(
            BaseRuleClasses.BaseRule.class,
            ObjcRuleClasses.ReleaseBundlingRule.class,
            ObjcRuleClasses.XcodegenRule.class,
            IpaRule.class)
        .build();
  }
}

/*<!-- #BLAZE_RULE (NAME = ios_extension, TYPE = BINARY, FAMILY = Objective-C) -->

<p>This rule produces a bundled binary for an iOS app extension from a compiled binary and bundle
metadata.</p>

<p>An iOS app extension is a nested bundle that is located inside the application bundle and is
released with it. An iOS app extension cannot be released alone, although this rule allows you to
build an <code>.ipa</code> with only the extension.

<p>Bundles generated by this rule use a bundle directory called
<code>PlugIns/<var>target-name</var>.appex</code>, while an application bundle uses
<code>Payload/<var>target-name</var>.app</code>. For instance, if an application call Foo has an app
extension called Bar, the Bar extension bundle files will be stored in
<code>Payload/Foo.app/PlugIns/Bar.appex</code> in the final application <code>.ipa</code>.

<p>There are many similarities between app extensions and applications with little to no difference
between how each thing is processed:
<ul>
  <li>both have entitlements and Info.plist files
  <li>both are code-signed. Signing and merging happens in this order: the extension is code-signed,
      bundles are merged, application is code-signed
  <li>both can have an app icon and launch image, and of course asset catalogs and all kinds of
      resources
  <li>both have linked binaries. The app extension binary is different in that it is linked with
      these additional flags:
      <ul>
          <li><code>-e _NSExtensionMain</code> - sets the entry point to a standard function in the
              iOS runtime rather than <code>main()</code>
          <li><code>-fapplicationextension</code>
      </ul>
</ul>

${IMPLICIT_OUTPUTS}

<!-- #END_BLAZE_RULE -->*/
