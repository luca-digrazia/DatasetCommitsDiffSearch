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
// limitations under the License.package com.google.devtools.build.lib.rules.android;
package com.google.devtools.build.lib.rules.android;

import static com.google.devtools.build.lib.packages.Attribute.ConfigurationTransition.HOST;
import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_KEYED_STRING_DICT;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;
import static com.google.devtools.build.lib.rules.android.AndroidRuleClasses.getAndroidSdkLabel;
import static com.google.devtools.build.lib.syntax.Type.STRING;
import static com.google.devtools.build.lib.syntax.Type.STRING_DICT;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.rules.config.ConfigFeatureFlagProvider;
import com.google.devtools.build.lib.rules.java.JavaConfiguration;
import com.google.devtools.build.lib.rules.java.Jvm;
import com.google.devtools.build.lib.util.FileTypeSet;

/** Base rule definition for android_local_test */
public class AndroidLocalTestBaseRule implements RuleDefinition {

  @Override
  public RuleClass build(Builder builder, RuleDefinitionEnvironment environment) {
    return builder
        .requiresConfigurationFragments(JavaConfiguration.class, Jvm.class)

        // Update documentation for inherited attributes

        /* <!-- #BLAZE_RULE($android_local_test_base).ATTRIBUTE(deps) -->
        The list of libraries to be tested as well as additional libraries to be linked
        in to the target.
        All resources, assets and manifest files declared in Android rules in the transitive
        closure of this attribute are made available in the test.
        <p>
        The list of allowed rules in <code>deps</code> are <code>android_library</code>,
        <code>aar_import</code>, <code>java_import</code>, <code>java_library</code>,
        <code>java_lite_proto_library</code>, and <code>proto_library</code>.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */

        /* <!-- #BLAZE_RULE($android_local_test_base).ATTRIBUTE(srcs) -->
        The list of source files that are processed to create the target.
        Required except in special case described below.
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

        <p>All other files are ignored, as long as
        there is at least one file of a file type described above. Otherwise an
        error is raised.
        </p>

        <p>
        The <code>srcs</code> attribute is required and cannot be empty, unless
        <code>runtime_deps</code> is specified.
        </p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */

        .add(
            attr(AndroidFeatureFlagSetProvider.FEATURE_FLAG_ATTR, LABEL_KEYED_STRING_DICT)
                .undocumented("the feature flag feature has not yet been launched")
                .allowedRuleClasses("config_feature_flag")
                .allowedFileTypes()
                .nonconfigurable("defines an aspect of configuration")
                .mandatoryProviders(ImmutableList.of(ConfigFeatureFlagProvider.id())))
        .add(AndroidFeatureFlagSetProvider.getWhitelistAttribute(environment))
        // TODO(b/38314524): Move $android_resources_busybox and :android_sdk to a separate
        // rule so they're not defined in multiple places
        .add(
            attr("$android_resources_busybox", LABEL)
                .cfg(HOST)
                .exec()
                .value(environment.getToolsLabel(AndroidRuleClasses.DEFAULT_RESOURCES_BUSYBOX)))
        .add(
            attr(":android_sdk", LABEL)
                .allowedRuleClasses("android_sdk", "filegroup")
                .value(
                    getAndroidSdkLabel(environment.getToolsLabel(AndroidRuleClasses.DEFAULT_SDK))))
        /* <!-- #BLAZE_RULE($android_local_test_base).ATTRIBUTE(test_class) -->
        The Java class to be loaded by the test runner.<br/>
        <p>
        This attribute specifies the name of a Java class to be run by
        this test. It is rare to need to set this. If this argument is omitted, the Java class
        whose name corresponds to the <code>name</code> of this
        <code>android_local_test</code> rule will be used.
        The test class needs to be annotated with <code>org.junit.runner.RunWith</code>.
        </p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("test_class", STRING)) // every test class adds this
        /* <!-- #BLAZE_RULE($android_local_test_base).ATTRIBUTE(manifest_values) -->
        A dictionary of values to be overridden in the manifest. Any instance of ${name} in the
        manifest will be replaced with the value corresponding to name in this dictionary.
        <code>applicationId</code>, <code>versionCode</code>, <code>versionName</code>,
        <code>minSdkVersion</code>, <code>targetSdkVersion</code> and
        <code>maxSdkVersion</code> will also override the corresponding attributes
        of the manifest and
        uses-sdk tags. <code>packageName</code> will be ignored and will be set from either
        <code>applicationId</code> if
        specified or the package in the manifest.
        It is not necessary to have a manifest on the rule in order to use manifest_values.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("manifest_values", STRING_DICT))
        /* <!-- #BLAZE_RULE($android_local_test_base).ATTRIBUTE(manifest) -->
        The name of the Android manifest file, normally <code>AndroidManifest.xml</code>.
        Must be defined if resource_files or assets are defined or if any of the manifests from
        the libraries under test have a <code>minSdkVersion</code> tag in them.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("manifest", LABEL).allowedFileTypes(FileTypeSet.ANY_FILE))
        /* <!-- #BLAZE_RULE($android_local_test_base).ATTRIBUTE(resource_files) -->
        The list of test resources to be packaged. This is typically a <code>glob</code>
        of all files under the <code>res</code> directory.
        <p>
        Generated files (from genrules) can be referenced by
        <a href="../build-ref.html#labels">Label</a> here as well. The only restriction is that
        the generated outputs must be under the same "<code>res</code>" directory as any other
        resource files that are included. It is rare to need this.
        </p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("resource_files", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE))
        /* <!-- #BLAZE_RULE($android_local_test_base).ATTRIBUTE(assets_dir) -->
        The string giving the path to the files in <code>assets</code>.
        The pair <code>assets</code> and <code>assets_dir</code> describe packaged
        assets and either both attributes should be provided or neither of them.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("assets_dir", STRING))
        /* <!-- #BLAZE_RULE($android_local_test_base).ATTRIBUTE(assets) -->
        The list of test assets to be packaged. This is typically a <code>glob</code> of all files
        under the <code>assets</code> directory. You can also reference other rules (any rule that
        produces files) or exported files in the other packages, as long as all those files are
        under the <code>assets_dir</code> directory in the corresponding package. It is rare to
        need this.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr("assets", LABEL_LIST).allowedFileTypes(FileTypeSet.ANY_FILE))
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return RuleDefinition.Metadata.builder()
        .name("$android_local_test_base")
        .type(RuleClassType.ABSTRACT)
        .build();
  }
}
