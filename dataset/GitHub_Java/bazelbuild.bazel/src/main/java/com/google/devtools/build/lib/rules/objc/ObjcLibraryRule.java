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

package com.google.devtools.build.lib.rules.objc;

import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;

/**
 * Rule definition for objc_library.
 */
public class ObjcLibraryRule implements RuleDefinition {

  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment env) {
    return builder
        .requiresConfigurationFragments(ObjcConfiguration.class)
        /*<!-- #BLAZE_RULE(objc_library).IMPLICIT_OUTPUTS -->
        <ul>
         <li><code><var>name</var>.xcodeproj/project.pbxproj</code>: An Xcode project file which
             can be used to develop or build on a Mac.</li>
        </ul>
        <!-- #END_BLAZE_RULE.IMPLICIT_OUTPUTS -->*/
        .setImplicitOutputsFunction(XcodeSupport.PBXPROJ)
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return RuleDefinition.Metadata.builder()
        .name("objc_library")
        .factoryClass(ObjcLibrary.class)
        .ancestors(BaseRuleClasses.BaseRule.class, ObjcRuleClasses.CompilingRule.class,
            ObjcRuleClasses.AlwaysLinkRule.class, ObjcRuleClasses.XcodegenRule.class)
        .build();
  }
}

/*<!-- #BLAZE_RULE (NAME = objc_library, TYPE = LIBRARY, FAMILY = Objective-C) -->

${ATTRIBUTE_SIGNATURE}

<p>This rule produces a static library from the given Objective-C source files.</p>

${IMPLICIT_OUTPUTS}

${ATTRIBUTE_DEFINITION}

<!-- #END_BLAZE_RULE -->*/
