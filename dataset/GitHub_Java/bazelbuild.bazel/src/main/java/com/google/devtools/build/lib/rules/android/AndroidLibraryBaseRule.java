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
package com.google.devtools.build.lib.rules.android;

import static com.google.devtools.build.lib.packages.Attribute.ConfigurationTransition.HOST;
import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;
import static com.google.devtools.build.lib.syntax.Type.BOOLEAN;
import static com.google.devtools.build.lib.syntax.Type.STRING;

import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.rules.android.AndroidRuleClasses.AndroidAaptBaseRule;
import com.google.devtools.build.lib.rules.android.AndroidRuleClasses.AndroidBaseRule;
import com.google.devtools.build.lib.rules.android.AndroidRuleClasses.AndroidResourceSupportRule;
import com.google.devtools.build.lib.rules.java.JavaCompilationArgsProvider;
import com.google.devtools.build.lib.rules.java.JavaSemantics;
import com.google.devtools.build.lib.rules.java.ProguardLibraryRule;
import com.google.devtools.build.lib.util.FileTypeSet;

/**
 * Rule definition for the android_library rule.
 */
public final class AndroidLibraryBaseRule implements RuleDefinition {
  private final AndroidNeverlinkAspect androidNeverlinkAspect;
  private final JackAspect jackAspect;

  public AndroidLibraryBaseRule(AndroidNeverlinkAspect androidNeverlinkAspect,
      JackAspect jackAspect) {
    this.androidNeverlinkAspect = androidNeverlinkAspect;
    this.jackAspect = jackAspect;
  }

  @Override
  public RuleClass build(RuleClass.Builder builder, final RuleDefinitionEnvironment env) {
    return builder
        /* <!-- #BLAZE_RULE(android_library).ATTRIBUTE(srcs) -->
         The list of source files that are processed to create the target.
        <p><code>srcs</code> files of type <code>.java</code> are compiled.
        <em>For readability's sake</em>, it is not good to put the name of a
        generated <code>.java</code> source file into the <code>srcs</code>.
        Instead, put the depended-on rule name in the <code>srcs</code>, as
        described below.
        </p>
        <p><code>srcs</code> files of type <code>.srcjar</code> are unpacked and
        compiled. (This is useful if you need to generate a set of .java files with
        a genrule or build extension.)
        </p>
        <p>This rule currently forces source and class compatibility with Java 7,
        although try with resources is not supported.
        </p>
        <p>If <code>srcs</code> is omitted, then any dependency specified in
        <code>deps</code> is exported from this rule (see
        <a href="${link java_library.exports}">java_library's exports</a> for more
        information about exporting dependencies). However, this behavior will be
        deprecated soon; try not to rely on it.
        </p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("srcs", LABEL_LIST)
            .direct_compile_time_input()
            .allowedFileTypes(JavaSemantics.JAVA_SOURCE, JavaSemantics.SOURCE_JAR))
        /* <!-- #BLAZE_RULE(android_library).ATTRIBUTE(deps) -->
        The list of other libraries to link against.
        Permitted library types are: <code>android_library</code>,
        <code>java_library</code> with <code>android</code> constraint and
        <code>cc_library</code> wrapping or producing <code>.so</code> native libraries
        for the Android target platform.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .override(builder.copy("deps")
            .allowedRuleClasses(AndroidRuleClasses.ALLOWED_DEPENDENCIES)
            .allowedFileTypes()
            .aspect(jackAspect)
            .aspect(androidNeverlinkAspect))
        /* <!-- #BLAZE_RULE(android_library).ATTRIBUTE(exports) -->
        The closure of all rules reached via <code>exports</code> attributes
        are considered direct dependencies of any rule that directly depends on the
        target with <code>exports</code>.
        <p>The <code>exports</code> are not direct deps of the rule they belong to.</p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("exports", LABEL_LIST)
            .allowedRuleClasses(AndroidRuleClasses.ALLOWED_DEPENDENCIES)
            .allowedFileTypes(/*May not have files in exports!*/)
            .aspect(jackAspect)
            .aspect(androidNeverlinkAspect))
        /* <!-- #BLAZE_RULE(android_library).ATTRIBUTE(exports_manifest) -->
        Whether to export manifest entries to <code>android_binary</code> targets
        that depend on this target. <code>uses-permissions</code> attributes are never exported.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("exports_manifest", BOOLEAN).value(false))
        /* <!-- #BLAZE_RULE(android_library).ATTRIBUTE(exported_plugins) -->
        The list of <code><a href="#${link java_plugin}">java_plugin</a></code>s (e.g. annotation
        processors) to export to libraries that directly depend on this library.
        <p>
          The specified list of <code>java_plugin</code>s will be applied to any library which
          directly depends on this library, just as if that library had explicitly declared these
          labels in <code><a href="${link android_library.plugins}">plugins</a></code>.
        </p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("exported_plugins", LABEL_LIST).cfg(HOST).allowedRuleClasses("java_plugin")
            .allowedFileTypes(FileTypeSet.NO_FILE))
        .add(attr("alwayslink", BOOLEAN).undocumented("purely informational for now"))
        /* <!-- #BLAZE_RULE(android_library).ATTRIBUTE(neverlink) -->
        Only use this library for compilation and not at runtime.
        The outputs of a rule marked as <code>neverlink</code> will not be used in
        <code>.apk</code> creation. Useful if the library will be provided by the
        runtime environment during execution.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("neverlink", BOOLEAN).value(false))
        /* <!-- #BLAZE_RULE(android_library).ATTRIBUTE(idl_import_root) -->
        Package-relative path to the root of the java package tree containing idl
        sources included in this library.
        <p>This path will be used as the import root when processing idl sources that
        depend on this library.</p>
        <p>When <code>idl_import_root</code> is specified, both <code>idl_parcelables</code>
        and <code>idl_srcs</code> must be at the path specified by the java package of the object
        they represent under <code>idl_import_root</code>. When <code>idl_import_root</code> is
        not specified, both <code>idl_parcelables</code> and <code>idl_srcs</code> must be at the
        path specified by their package under a Java root.</p>
        <p>See <a href="${link android_library#android_library_examples.idl_import_root}">
        examples</a>.</p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("idl_import_root", STRING))
        /* <!-- #BLAZE_RULE(android_library).ATTRIBUTE(idl_srcs) -->
        List of Android IDL definitions to translate to Java interfaces.
        After the Java interfaces are generated, they will be compiled together
        with the contents of <code>srcs</code>.
        <p>These files will be made available as imports for any
        <code>android_library</code> target that depends on this library, directly
        or via its transitive closure.</p>
        <p>These files must be placed appropriately for the aidl compiler to find them.
        See <a href="${link android_library.idl_import_root}">the description of idl_import_root</a>
        for information about what this means.</p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("idl_srcs", LABEL_LIST).direct_compile_time_input()
            .allowedFileTypes(AndroidRuleClasses.ANDROID_IDL))
        /* <!-- #BLAZE_RULE(android_library).ATTRIBUTE(idl_parcelables) -->
        List of Android IDL definitions to supply as imports.
        These files will be made available as imports for any
        <code>android_library</code> target that depends on this library, directly
        or via its transitive closure, but will not be translated to Java
        or compiled.
        <p>Only <code>.aidl</code> files that correspond directly to
        <code>.java</code> sources in this library should be included (e.g., custom
        implementations of Parcelable), otherwise <code>idl_srcs</code> should be
        used.</p>
        <p>These files must be placed appropriately for the aidl compiler to find them.
        See <a href="${link android_library.idl_import_root}">the description of idl_import_root</a>
        for information about what this means.</p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("idl_parcelables", LABEL_LIST).direct_compile_time_input()
            .allowedFileTypes(AndroidRuleClasses.ANDROID_IDL))
        .add(attr("$android_manifest_merge_tool", LABEL).cfg(HOST).exec().value(
            env.getToolsLabel(AndroidRuleClasses.MANIFEST_MERGE_TOOL_LABEL)))
        .advertiseProvider(JavaCompilationArgsProvider.class)
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return RuleDefinition.Metadata.builder()
        .name("$android_library_base")
        .type(RuleClassType.ABSTRACT)
        .ancestors(
            AndroidBaseRule.class,
            AndroidAaptBaseRule.class,
            AndroidResourceSupportRule.class,
            ProguardLibraryRule.class)
        .build();
  }
}
