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

package com.google.devtools.build.lib.bazel.rules;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.ConfiguredRuleClassProvider;
import com.google.devtools.build.lib.analysis.ConfiguredRuleClassProvider.Builder;
import com.google.devtools.build.lib.analysis.ConfiguredRuleClassProvider.DeprecationValidator;
import com.google.devtools.build.lib.analysis.ConfiguredRuleClassProvider.PrerequisiteValidator;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.ConfigRuleClasses;
import com.google.devtools.build.lib.analysis.constraints.EnvironmentRule;
import com.google.devtools.build.lib.bazel.rules.android.AndroidNdkRepositoryRule;
import com.google.devtools.build.lib.bazel.rules.android.AndroidSdkRepositoryRule;
import com.google.devtools.build.lib.bazel.rules.android.BazelAarImportRule;
import com.google.devtools.build.lib.bazel.rules.android.BazelAndroidBinaryRule;
import com.google.devtools.build.lib.bazel.rules.android.BazelAndroidLibraryRule;
import com.google.devtools.build.lib.bazel.rules.android.BazelAndroidRuleClasses.BazelAndroidToolsDefaultsJarRule;
import com.google.devtools.build.lib.bazel.rules.android.BazelAndroidSemantics;
import com.google.devtools.build.lib.bazel.rules.common.BazelActionListenerRule;
import com.google.devtools.build.lib.bazel.rules.common.BazelExtraActionRule;
import com.google.devtools.build.lib.bazel.rules.common.BazelFilegroupRule;
import com.google.devtools.build.lib.bazel.rules.common.BazelTestSuiteRule;
import com.google.devtools.build.lib.bazel.rules.cpp.BazelCcBinaryRule;
import com.google.devtools.build.lib.bazel.rules.cpp.BazelCcIncLibraryRule;
import com.google.devtools.build.lib.bazel.rules.cpp.BazelCcLibraryRule;
import com.google.devtools.build.lib.bazel.rules.cpp.BazelCcTestRule;
import com.google.devtools.build.lib.bazel.rules.cpp.BazelCppRuleClasses;
import com.google.devtools.build.lib.bazel.rules.genrule.BazelGenRuleRule;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaBinaryRule;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaBuildInfoFactory;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaImportRule;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaLibraryRule;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaPluginRule;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaRuleClasses;
import com.google.devtools.build.lib.bazel.rules.java.BazelJavaTestRule;
import com.google.devtools.build.lib.bazel.rules.java.proto.BazelJavaLiteProtoAspect;
import com.google.devtools.build.lib.bazel.rules.java.proto.BazelJavaLiteProtoLibraryRule;
import com.google.devtools.build.lib.bazel.rules.java.proto.BazelJavaProtoAspect;
import com.google.devtools.build.lib.bazel.rules.java.proto.BazelJavaProtoLibraryRule;
import com.google.devtools.build.lib.bazel.rules.objc.BazelJ2ObjcLibraryRule;
import com.google.devtools.build.lib.bazel.rules.python.BazelPyBinaryRule;
import com.google.devtools.build.lib.bazel.rules.python.BazelPyLibraryRule;
import com.google.devtools.build.lib.bazel.rules.python.BazelPyRuleClasses;
import com.google.devtools.build.lib.bazel.rules.python.BazelPyTestRule;
import com.google.devtools.build.lib.bazel.rules.python.BazelPythonConfiguration;
import com.google.devtools.build.lib.bazel.rules.sh.BazelShBinaryRule;
import com.google.devtools.build.lib.bazel.rules.sh.BazelShLibraryRule;
import com.google.devtools.build.lib.bazel.rules.sh.BazelShRuleClasses;
import com.google.devtools.build.lib.bazel.rules.sh.BazelShTestRule;
import com.google.devtools.build.lib.bazel.rules.workspace.GitRepositoryRule;
import com.google.devtools.build.lib.bazel.rules.workspace.HttpArchiveRule;
import com.google.devtools.build.lib.bazel.rules.workspace.HttpFileRule;
import com.google.devtools.build.lib.bazel.rules.workspace.HttpJarRule;
import com.google.devtools.build.lib.bazel.rules.workspace.MavenJarRule;
import com.google.devtools.build.lib.bazel.rules.workspace.MavenServerRule;
import com.google.devtools.build.lib.bazel.rules.workspace.NewGitRepositoryRule;
import com.google.devtools.build.lib.bazel.rules.workspace.NewHttpArchiveRule;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.ideinfo.AndroidStudioInfoAspect;
import com.google.devtools.build.lib.ideinfo.BazelAndroidStudioInfoSemantics;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.PackageGroup;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.rules.Alias.AliasRule;
import com.google.devtools.build.lib.rules.AliasProvider;
import com.google.devtools.build.lib.rules.android.AarImportBaseRule;
import com.google.devtools.build.lib.rules.android.AndroidBinaryOnlyRule;
import com.google.devtools.build.lib.rules.android.AndroidConfiguration;
import com.google.devtools.build.lib.rules.android.AndroidLibraryBaseRule;
import com.google.devtools.build.lib.rules.android.AndroidNeverlinkAspect;
import com.google.devtools.build.lib.rules.android.AndroidRuleClasses;
import com.google.devtools.build.lib.rules.android.AndroidSkylarkCommon;
import com.google.devtools.build.lib.rules.android.DexArchiveAspect;
import com.google.devtools.build.lib.rules.android.JackAspect;
import com.google.devtools.build.lib.rules.apple.AppleCommandLineOptions;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;
import com.google.devtools.build.lib.rules.apple.AppleToolchain;
import com.google.devtools.build.lib.rules.apple.XcodeConfigRule;
import com.google.devtools.build.lib.rules.apple.XcodeVersionRule;
import com.google.devtools.build.lib.rules.apple.cpp.AppleCcToolchainRule;
import com.google.devtools.build.lib.rules.cpp.CcIncLibraryRule;
import com.google.devtools.build.lib.rules.cpp.CcToolchainRule;
import com.google.devtools.build.lib.rules.cpp.CcToolchainSuiteRule;
import com.google.devtools.build.lib.rules.cpp.CppBuildInfo;
import com.google.devtools.build.lib.rules.cpp.CppConfigurationLoader;
import com.google.devtools.build.lib.rules.cpp.CppOptions;
import com.google.devtools.build.lib.rules.genquery.GenQueryRule;
import com.google.devtools.build.lib.rules.java.JavaConfigurationLoader;
import com.google.devtools.build.lib.rules.java.JavaImportBaseRule;
import com.google.devtools.build.lib.rules.java.JavaOptions;
import com.google.devtools.build.lib.rules.java.JavaToolchainRule;
import com.google.devtools.build.lib.rules.java.JvmConfigurationLoader;
import com.google.devtools.build.lib.rules.java.ProguardLibraryRule;
import com.google.devtools.build.lib.rules.objc.AppleBinaryRule;
import com.google.devtools.build.lib.rules.objc.AppleDynamicLibraryRule;
import com.google.devtools.build.lib.rules.objc.AppleSkylarkCommon;
import com.google.devtools.build.lib.rules.objc.AppleStaticLibraryRule;
import com.google.devtools.build.lib.rules.objc.AppleWatch1ExtensionRule;
import com.google.devtools.build.lib.rules.objc.AppleWatch2ExtensionRule;
import com.google.devtools.build.lib.rules.objc.AppleWatchExtensionBinaryRule;
import com.google.devtools.build.lib.rules.objc.BazelJ2ObjcProtoAspect;
import com.google.devtools.build.lib.rules.objc.ExperimentalObjcLibraryRule;
import com.google.devtools.build.lib.rules.objc.IosApplicationRule;
import com.google.devtools.build.lib.rules.objc.IosDeviceRule;
import com.google.devtools.build.lib.rules.objc.IosExtensionBinaryRule;
import com.google.devtools.build.lib.rules.objc.IosExtensionRule;
import com.google.devtools.build.lib.rules.objc.IosFrameworkBinaryRule;
import com.google.devtools.build.lib.rules.objc.IosFrameworkRule;
import com.google.devtools.build.lib.rules.objc.IosTestRule;
import com.google.devtools.build.lib.rules.objc.J2ObjcAspect;
import com.google.devtools.build.lib.rules.objc.J2ObjcCommandLineOptions;
import com.google.devtools.build.lib.rules.objc.J2ObjcConfiguration;
import com.google.devtools.build.lib.rules.objc.J2ObjcLibraryBaseRule;
import com.google.devtools.build.lib.rules.objc.ObjcBinaryRule;
import com.google.devtools.build.lib.rules.objc.ObjcBuildInfoFactory;
import com.google.devtools.build.lib.rules.objc.ObjcBundleLibraryRule;
import com.google.devtools.build.lib.rules.objc.ObjcBundleRule;
import com.google.devtools.build.lib.rules.objc.ObjcCommandLineOptions;
import com.google.devtools.build.lib.rules.objc.ObjcConfigurationLoader;
import com.google.devtools.build.lib.rules.objc.ObjcFrameworkRule;
import com.google.devtools.build.lib.rules.objc.ObjcImportRule;
import com.google.devtools.build.lib.rules.objc.ObjcLibraryRule;
import com.google.devtools.build.lib.rules.objc.ObjcProtoAspect;
import com.google.devtools.build.lib.rules.objc.ObjcProtoLibraryRule;
import com.google.devtools.build.lib.rules.objc.ObjcProvider;
import com.google.devtools.build.lib.rules.objc.ObjcRuleClasses;
import com.google.devtools.build.lib.rules.objc.ObjcXcodeprojRule;
import com.google.devtools.build.lib.rules.proto.BazelProtoLibraryRule;
import com.google.devtools.build.lib.rules.proto.ProtoConfiguration;
import com.google.devtools.build.lib.rules.python.PythonConfigurationLoader;
import com.google.devtools.build.lib.rules.python.PythonOptions;
import com.google.devtools.build.lib.rules.repository.BindRule;
import com.google.devtools.build.lib.rules.repository.LocalRepositoryRule;
import com.google.devtools.build.lib.rules.repository.NewLocalRepositoryRule;
import com.google.devtools.build.lib.rules.repository.WorkspaceBaseRule;
import com.google.devtools.build.lib.util.ResourceFileLoader;
import java.io.IOException;

/**
 * A rule class provider implementing the rules Bazel knows.
 */
public class BazelRuleClassProvider {
  public static final String TOOLS_REPOSITORY = "@bazel_tools";

  /**
   * A coherent set of options, fragments, aspects and rules; each of these may declare a dependency
   * on other such sets.
   */
  public static interface RuleModule {
    /** Add stuff to the configured rule class provider builder. */
    void init(ConfiguredRuleClassProvider.Builder builder);

    /** List of required modules. */
    ImmutableList<RuleModule> requires();
  }

  /** Used by the build encyclopedia generator. */
  public static ConfiguredRuleClassProvider create() {
    ConfiguredRuleClassProvider.Builder builder =
        new ConfiguredRuleClassProvider.Builder();
    builder.setToolsRepository(TOOLS_REPOSITORY);
    setup(builder);
    return builder.build();
  }

  private static class BazelPrerequisiteValidator implements PrerequisiteValidator {
    @Override
    public void validate(RuleContext.Builder context,
        ConfiguredTarget prerequisite, Attribute attribute) {
      validateDirectPrerequisiteVisibility(context, prerequisite, attribute.getName());
      DeprecationValidator.validateDirectPrerequisiteForDeprecation(
          context, context.getRule(), prerequisite, context.forAspect());
    }

    private void validateDirectPrerequisiteVisibility(
        RuleContext.Builder context, ConfiguredTarget prerequisite, String attrName) {
      Rule rule = context.getRule();
      if (rule.getLabel().getPackageFragment().equals(Label.EXTERNAL_PACKAGE_NAME)) {
        // //external: labels are special. They have access to everything and visibility is checked
        // at the edge that points to the //external: label.
        return;
      }
      Target prerequisiteTarget = prerequisite.getTarget();
      Label prerequisiteLabel = prerequisiteTarget.getLabel();
      if (!context.getRule().getLabel().getPackageIdentifier().equals(
              prerequisite.getTarget().getLabel().getPackageIdentifier())
          && !context.isVisible(prerequisite)) {
        if (!context.getConfiguration().checkVisibility()) {
          context.ruleWarning(String.format("Target '%s' violates visibility of target "
              + "'%s'. Continuing because --nocheck_visibility is active",
              rule.getLabel(), prerequisiteLabel));
        } else {
          // Oddly enough, we use reportError rather than ruleError here.
          context.reportError(rule.getLocation(),
              String.format("Target '%s' is not visible from target '%s'%s. Check "
                  + "the visibility declaration of the former target if you think "
                  + "the dependency is legitimate",
                  prerequisiteLabel,
                  rule.getLabel(),
                  AliasProvider.printVisibilityChain(prerequisite)));
        }
      }

      if (prerequisiteTarget instanceof PackageGroup && !attrName.equals("visibility")) {
        context.reportError(
            rule.getAttributeLocation(attrName),
            "in "
                + attrName
                + " attribute of "
                + rule.getRuleClass()
                + " rule "
                + rule.getLabel()
                + ": package group '"
                + prerequisiteLabel
                + "' is misplaced here "
                + "(they are only allowed in the visibility attribute)");
      }
    }
  }

  public static void setup(ConfiguredRuleClassProvider.Builder builder) {
    MINIMAL_RULES.init(builder);
    PROTO_RULES.init(builder);
    SH_RULES.init(builder);
    CPP_RULES.init(builder);
    JAVA_RULES.init(builder);
    JAVA_PROTO_RULES.init(builder);
    ANDROID_RULES.init(builder);
    PYTHON_RULES.init(builder);
    OBJC_RULES.init(builder);
    ANDROID_STUDIO_ASPECT.init(builder);
    VARIOUS_WORKSPACE_RULES.init(builder);
  }

  public static final RuleModule MINIMAL_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          builder
              .setConfigurationCollectionFactory(new BazelConfigurationCollection())
              .setPrelude("//tools/build_rules:prelude_bazel")
              .setRunfilesPrefix(Label.DEFAULT_REPOSITORY_DIRECTORY)
              .setPrerequisiteValidator(new BazelPrerequisiteValidator());

          builder.setUniversalConfigurationFragment(BazelConfiguration.class);
          builder.addConfigurationOptions(BuildConfiguration.Options.class);
          builder.addConfigurationFragment(new BazelConfiguration.Loader());

          builder.addRuleDefinition(new BaseRuleClasses.BaseRule());
          builder.addRuleDefinition(new BaseRuleClasses.RuleBase());
          builder.addRuleDefinition(new BaseRuleClasses.BinaryBaseRule());
          builder.addRuleDefinition(new BaseRuleClasses.TestBaseRule());
          builder.addRuleDefinition(new BaseRuleClasses.ErrorRule());

          builder.addRuleDefinition(new EnvironmentRule());

          builder.addRuleDefinition(new ConfigRuleClasses.ConfigBaseRule());
          builder.addRuleDefinition(new ConfigRuleClasses.ConfigSettingRule());

          builder.addRuleDefinition(new AliasRule());
          builder.addRuleDefinition(new BazelFilegroupRule());
          builder.addRuleDefinition(new BazelTestSuiteRule());
          builder.addRuleDefinition(new BazelGenRuleRule());
          builder.addRuleDefinition(new GenQueryRule());

          builder.addRuleDefinition(new BindRule());
          builder.addRuleDefinition(new WorkspaceBaseRule());
          builder.addRuleDefinition(new LocalRepositoryRule());

          try {
            builder.addWorkspaceFilePrefix(
                ResourceFileLoader.loadResource(BazelRuleClassProvider.class, "tools.WORKSPACE"));
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of();
        }
      };

  public static final RuleModule PROTO_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          builder.addConfigurationOptions(ProtoConfiguration.Options.class);
          builder.addConfigurationFragment(new ProtoConfiguration.Loader());
          builder.addRuleDefinition(new BazelProtoLibraryRule());
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES);
        }
      };

  public static final RuleModule SH_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          builder.addRuleDefinition(new BazelShRuleClasses.ShRule());
          builder.addRuleDefinition(new BazelShLibraryRule());
          builder.addRuleDefinition(new BazelShBinaryRule());
          builder.addRuleDefinition(new BazelShTestRule());
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES);
        }
      };

  public static final RuleModule CPP_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          builder.addConfigurationOptions(CppOptions.class);

          builder.addBuildInfoFactory(new CppBuildInfo());

          builder.addRuleDefinition(new CcToolchainRule());
          builder.addRuleDefinition(new CcToolchainSuiteRule());
          builder.addRuleDefinition(new CcIncLibraryRule());
          builder.addRuleDefinition(new BazelCppRuleClasses.CcLinkingRule());
          builder.addRuleDefinition(new BazelCppRuleClasses.CcDeclRule());
          builder.addRuleDefinition(new BazelCppRuleClasses.CcBaseRule());
          builder.addRuleDefinition(new BazelCppRuleClasses.CcRule());
          builder.addRuleDefinition(new BazelCppRuleClasses.CcBinaryBaseRule());
          builder.addRuleDefinition(new BazelCcBinaryRule());
          builder.addRuleDefinition(new BazelCcTestRule());
          builder.addRuleDefinition(new BazelCppRuleClasses.CcLibraryBaseRule());
          builder.addRuleDefinition(new BazelCcLibraryRule());
          builder.addRuleDefinition(new BazelCcIncLibraryRule());

          builder.addConfigurationFragment(
              new CppConfigurationLoader(Functions.<String>identity()));
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES);
        }
      };

  public static final RuleModule JAVA_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          builder.addConfigurationOptions(JavaOptions.class);
          builder.addConfigurationFragment(new JvmConfigurationLoader());
          builder.addConfigurationFragment(new JavaConfigurationLoader());

          builder.addBuildInfoFactory(new BazelJavaBuildInfoFactory());

          builder.addRuleDefinition(new BazelJavaRuleClasses.BaseJavaBinaryRule());
          builder.addRuleDefinition(new BazelJavaRuleClasses.IjarBaseRule());
          builder.addRuleDefinition(new BazelJavaRuleClasses.JavaBaseRule());
          builder.addRuleDefinition(new ProguardLibraryRule());
          builder.addRuleDefinition(new JavaImportBaseRule());
          builder.addRuleDefinition(new BazelJavaRuleClasses.JavaRule());
          builder.addRuleDefinition(new BazelJavaBinaryRule());
          builder.addRuleDefinition(new BazelJavaLibraryRule());
          builder.addRuleDefinition(new BazelJavaImportRule());
          builder.addRuleDefinition(new BazelJavaTestRule());
          builder.addRuleDefinition(new BazelJavaPluginRule());
          builder.addRuleDefinition(new JavaToolchainRule());

          builder.addRuleDefinition(new BazelExtraActionRule());
          builder.addRuleDefinition(new BazelActionListenerRule());

          try {
            builder.addWorkspaceFilePrefix(
                ResourceFileLoader.loadResource(BazelJavaRuleClasses.class, "jdk.WORKSPACE"));
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES, CPP_RULES);
        }
      };

  public static final RuleModule JAVA_PROTO_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          BazelJavaProtoAspect bazelJavaProtoAspect = new BazelJavaProtoAspect();
          BazelJavaLiteProtoAspect bazelJavaLiteProtoAspect = new BazelJavaLiteProtoAspect();
          builder.addNativeAspectClass(bazelJavaProtoAspect);
          builder.addNativeAspectClass(bazelJavaLiteProtoAspect);
          builder.addRuleDefinition(new BazelJavaProtoLibraryRule(bazelJavaProtoAspect));
          builder.addRuleDefinition(new BazelJavaLiteProtoLibraryRule(bazelJavaLiteProtoAspect));
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES, JAVA_RULES);
        }
      };

  public static final RuleModule ANDROID_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          String toolsRepository = checkNotNull(builder.getToolsRepository());

          builder.addConfigurationOptions(AndroidConfiguration.Options.class);
          builder.addConfigurationFragment(new AndroidConfiguration.Loader());

          AndroidNeverlinkAspect androidNeverlinkAspect = new AndroidNeverlinkAspect();
          DexArchiveAspect dexArchiveAspect = new DexArchiveAspect(toolsRepository);
          JackAspect jackAspect = new JackAspect(toolsRepository);
          builder.addNativeAspectClass(androidNeverlinkAspect);
          builder.addNativeAspectClass(dexArchiveAspect);
          builder.addNativeAspectClass(jackAspect);

          builder.addRuleDefinition(new AndroidRuleClasses.AndroidSdkRule());
          builder.addRuleDefinition(new BazelAndroidToolsDefaultsJarRule());
          builder.addRuleDefinition(new AndroidRuleClasses.AndroidBaseRule());
          builder.addRuleDefinition(new AndroidRuleClasses.AndroidAaptBaseRule());
          builder.addRuleDefinition(new AndroidRuleClasses.AndroidResourceSupportRule());
          builder.addRuleDefinition(new AndroidRuleClasses.AndroidBinaryBaseRule(
              androidNeverlinkAspect, dexArchiveAspect, jackAspect));
          builder.addRuleDefinition(new AndroidBinaryOnlyRule());
          builder.addRuleDefinition(new AndroidLibraryBaseRule(androidNeverlinkAspect, jackAspect));
          builder.addRuleDefinition(new BazelAndroidLibraryRule());
          builder.addRuleDefinition(new BazelAndroidBinaryRule());
          builder.addRuleDefinition(new AarImportBaseRule());
          builder.addRuleDefinition(new BazelAarImportRule());

          builder.addSkylarkAccessibleTopLevels("android_common", new AndroidSkylarkCommon());

          try {
            builder.addWorkspaceFilePrefix(
                ResourceFileLoader.loadResource(BazelAndroidSemantics.class, "android.WORKSPACE"));
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES, CPP_RULES, JAVA_RULES);
        }
      };

  public static final RuleModule PYTHON_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          builder.addConfigurationOptions(PythonOptions.class);
          builder.addConfigurationOptions(BazelPythonConfiguration.Options.class);
          builder.addConfigurationFragment(new PythonConfigurationLoader());
          builder.addConfigurationFragment(new BazelPythonConfiguration.Loader());

          builder.addRuleDefinition(new BazelPyRuleClasses.PyBaseRule());
          builder.addRuleDefinition(new BazelPyRuleClasses.PyBinaryBaseRule());
          builder.addRuleDefinition(new BazelPyLibraryRule());
          builder.addRuleDefinition(new BazelPyBinaryRule());
          builder.addRuleDefinition(new BazelPyTestRule());
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES, CPP_RULES);
        }
      };

  public static final RuleModule OBJC_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          String toolsRepository = checkNotNull(builder.getToolsRepository());

          builder.addConfigurationOptions(ObjcCommandLineOptions.class);
          builder.addConfigurationOptions(AppleCommandLineOptions.class);
          builder.addConfigurationFragment(new ObjcConfigurationLoader());
          builder.addConfigurationFragment(new AppleConfiguration.Loader());

          builder.addBuildInfoFactory(new ObjcBuildInfoFactory());
          builder.registerSkylarkProvider(
              ObjcProvider.OBJC_SKYLARK_PROVIDER_NAME, ObjcProvider.class);

          builder.addSkylarkAccessibleTopLevels("apple_common", new AppleSkylarkCommon());

          // objc_proto_library should go into a separate RuleModule!
          ObjcProtoAspect objcProtoAspect = new ObjcProtoAspect();
          builder.addNativeAspectClass(objcProtoAspect);
          builder.addRuleDefinition(new ObjcProtoLibraryRule(objcProtoAspect));

          builder.addRuleDefinition(new AppleCcToolchainRule());
          builder.addRuleDefinition(new AppleToolchain.RequiresXcodeConfigRule(toolsRepository));
          builder.addRuleDefinition(new XcodeConfigRule());
          builder.addRuleDefinition(new IosTestRule());
          builder.addRuleDefinition(new IosDeviceRule());
          builder.addRuleDefinition(new AppleBinaryRule());
          builder.addRuleDefinition(new AppleStaticLibraryRule());
          builder.addRuleDefinition(new AppleDynamicLibraryRule());
          builder.addRuleDefinition(new ObjcBinaryRule());
          builder.addRuleDefinition(new ExperimentalObjcLibraryRule());
          builder.addRuleDefinition(new ObjcBundleRule());
          builder.addRuleDefinition(new ObjcBundleLibraryRule());
          builder.addRuleDefinition(new ObjcFrameworkRule());
          builder.addRuleDefinition(new ObjcImportRule());
          builder.addRuleDefinition(new ObjcLibraryRule());
          builder.addRuleDefinition(new ObjcXcodeprojRule());
          builder.addRuleDefinition(new ObjcRuleClasses.CoptsRule());
          builder.addRuleDefinition(new ObjcRuleClasses.BundlingRule());
          builder.addRuleDefinition(new ObjcRuleClasses.ReleaseBundlingRule());
          builder.addRuleDefinition(new ObjcRuleClasses.SimulatorRule());
          builder.addRuleDefinition(new ObjcRuleClasses.CompilingRule());
          // TODO(ulfjack): Depending on objcProtoAspect from here is a layering violation.
          builder.addRuleDefinition(new ObjcRuleClasses.LinkingRule(objcProtoAspect));
          builder.addRuleDefinition(new ObjcRuleClasses.MultiArchPlatformRule());
          builder.addRuleDefinition(new ObjcRuleClasses.ResourcesRule());
          builder.addRuleDefinition(new ObjcRuleClasses.XcodegenRule());
          builder.addRuleDefinition(new ObjcRuleClasses.AlwaysLinkRule());
          builder.addRuleDefinition(new ObjcRuleClasses.SdkFrameworksDependerRule());
          builder.addRuleDefinition(new ObjcRuleClasses.CompileDependencyRule());
          builder.addRuleDefinition(new ObjcRuleClasses.ResourceToolsRule());
          builder.addRuleDefinition(new ObjcRuleClasses.XcrunRule());
          builder.addRuleDefinition(new ObjcRuleClasses.LibtoolRule());
          builder.addRuleDefinition(new ObjcRuleClasses.IpaRule());
          builder.addRuleDefinition(new ObjcRuleClasses.ReleaseBundlingToolsRule());
          builder.addRuleDefinition(new ObjcRuleClasses.WatchExtensionBundleRule());
          builder.addRuleDefinition(new ObjcRuleClasses.WatchApplicationBundleRule());
          builder.addRuleDefinition(new ObjcRuleClasses.CrosstoolRule());
          builder.addRuleDefinition(new AppleWatch1ExtensionRule());
          builder.addRuleDefinition(new AppleWatch2ExtensionRule());
          builder.addRuleDefinition(new AppleWatchExtensionBinaryRule());
          builder.addRuleDefinition(new IosApplicationRule());
          builder.addRuleDefinition(new IosExtensionBinaryRule());
          builder.addRuleDefinition(new IosExtensionRule());
          builder.addRuleDefinition(new IosFrameworkBinaryRule());
          builder.addRuleDefinition(new IosFrameworkRule());
          builder.addRuleDefinition(new XcodeVersionRule());

          // j2objc also doesn't belong here.
          builder.addConfigurationOptions(J2ObjcCommandLineOptions.class);
          builder.addConfigurationFragment(new J2ObjcConfiguration.Loader());

          BazelJ2ObjcProtoAspect bazelJ2ObjcProtoAspect =
              new BazelJ2ObjcProtoAspect(toolsRepository);
          J2ObjcAspect j2ObjcAspect = new J2ObjcAspect(toolsRepository, bazelJ2ObjcProtoAspect);

          builder.addNativeAspectClass(bazelJ2ObjcProtoAspect);
          builder.addNativeAspectClass(j2ObjcAspect);
          builder.addRuleDefinition(new J2ObjcLibraryBaseRule());
          builder.addRuleDefinition(new BazelJ2ObjcLibraryRule(j2ObjcAspect));

          try {
            builder.addWorkspaceFilePrefix(
                ResourceFileLoader.loadResource(BazelJ2ObjcLibraryRule.class, "j2objc.WORKSPACE"));
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES, CPP_RULES, JAVA_RULES);
        }
      };

  public static final RuleModule ANDROID_STUDIO_ASPECT =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          String toolsRepository = checkNotNull(builder.getToolsRepository());
          AndroidStudioInfoAspect androidStudioInfoAspect =
              new AndroidStudioInfoAspect(toolsRepository, new BazelAndroidStudioInfoSemantics());
          builder.addNativeAspectClass(androidStudioInfoAspect);
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES);
        }
      };

  public static final RuleModule VARIOUS_WORKSPACE_RULES =
      new RuleModule() {
        @Override
        public void init(Builder builder) {
          // TODO(ulfjack): Split this up by conceptual units.
          builder.addRuleDefinition(new GitRepositoryRule());
          builder.addRuleDefinition(new HttpArchiveRule());
          builder.addRuleDefinition(new HttpJarRule());
          builder.addRuleDefinition(new HttpFileRule());
          builder.addRuleDefinition(new MavenJarRule());
          builder.addRuleDefinition(new MavenServerRule());
          builder.addRuleDefinition(new NewHttpArchiveRule());
          builder.addRuleDefinition(new NewGitRepositoryRule());
          builder.addRuleDefinition(new NewLocalRepositoryRule());
          builder.addRuleDefinition(new AndroidSdkRepositoryRule());
          builder.addRuleDefinition(new AndroidNdkRepositoryRule());
        }

        @Override
        public ImmutableList<RuleModule> requires() {
          return ImmutableList.of(MINIMAL_RULES);
        }
      };
}
