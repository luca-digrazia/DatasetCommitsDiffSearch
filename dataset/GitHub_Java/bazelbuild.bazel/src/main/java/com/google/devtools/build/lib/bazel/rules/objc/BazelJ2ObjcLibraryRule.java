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

package com.google.devtools.build.lib.bazel.rules.objc;

import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;

import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;
import com.google.devtools.build.lib.rules.java.J2ObjcConfiguration;
import com.google.devtools.build.lib.rules.objc.J2ObjcAspect;
import com.google.devtools.build.lib.rules.objc.J2ObjcLibrary;
import com.google.devtools.build.lib.rules.objc.J2ObjcLibraryBaseRule;
import com.google.devtools.build.lib.rules.objc.ObjcConfiguration;

/**
 * Concrete implementation of J2ObjCLibraryBaseRule.
 */
public class BazelJ2ObjcLibraryRule implements RuleDefinition {

  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment env) {
    return builder
        .requiresConfigurationFragments(J2ObjcConfiguration.class, ObjcConfiguration.class,
            AppleConfiguration.class)
          /* <!-- #BLAZE_RULE(j2objc_library).ATTRIBUTE(deps) -->
          A list of <code>j2objc_library</code>, <code>java_library</code>
          and <code>java_import</code> targets that contain
          Java files to be transpiled to Objective-C.
          ${SYNOPSIS}
          <p>All <code>java_library</code> and <code>java_import</code> targets that can be reached
          transitively through <code>exports</code>, <code>deps</code> and <code>runtime_deps</code>
          will be translated and compiled. Currently there is no support for files generated by Java
          annotation processing or <code>java_import</code> targets with no <code>srcjar</code>
          specified.
          </p>
          <p>The J2ObjC translation works differently depending on the type of source Java source
          files included in the transitive closure. For each .java source files included in
          <code>srcs</code> of <code>java_library</code>, a corresponding .h and .m source file
          will be generated. For each source jar included in <code>srcs</code> of
          <code>java_library</code> or <code>srcjar</code> of <code>java_import</code>, a
          corresponding .h and .m source file will be generated with all the code for that jar.
          </p>
          <p>Users can import the J2ObjC-generated header files in their code. The import paths for
          these files are the root-relative path of the original Java artifacts. For example,
          <code>//some/package/foo.java</code> has an import path of <code>some/package/foo.h</code>
          and <code>//some/package/bar.srcjar</code> has <code>some/package/bar.h</code
          </p>
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
        .add(attr("deps", LABEL_LIST)
            .aspect(J2ObjcAspect.class)
            .direct_compile_time_input()
            .allowedRuleClasses("j2objc_library", "java_library", "java_import")
            .allowedFileTypes())
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return RuleDefinition.Metadata.builder()
        .name("j2objc_library")
        .factoryClass(J2ObjcLibrary.class)
        .ancestors(J2ObjcLibraryBaseRule.class)
        .build();
  }
}
