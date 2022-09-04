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

package com.google.devtools.build.lib.rules.objc;

import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;
import static com.google.devtools.build.lib.packages.BuildType.NODEP_LABEL;
import static com.google.devtools.build.lib.packages.ImplicitOutputsFunction.fromTemplates;
import static com.google.devtools.build.lib.packages.Type.BOOLEAN;
import static com.google.devtools.build.lib.packages.Type.STRING;
import static com.google.devtools.build.lib.packages.Type.STRING_LIST;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ExecutionRequirements;
import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.RuleConfiguredTargetBuilder;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.analysis.Runfiles;
import com.google.devtools.build.lib.analysis.RunfilesProvider;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.HostTransition;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.Attribute.LabelLateBoundDefault;
import com.google.devtools.build.lib.packages.ImplicitOutputsFunction.SafeImplicitOutputsFunction;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.packages.SkylarkProviderIdentifier;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;
import com.google.devtools.build.lib.rules.apple.ApplePlatform;
import com.google.devtools.build.lib.rules.apple.AppleToolchain.RequiresXcodeConfigRule;
import com.google.devtools.build.lib.rules.apple.XcodeConfigProvider;
import com.google.devtools.build.lib.rules.cpp.CcToolchain;
import com.google.devtools.build.lib.rules.cpp.CcToolchainProvider;
import com.google.devtools.build.lib.rules.cpp.CppModuleMap.UmbrellaHeaderStrategy;
import com.google.devtools.build.lib.rules.cpp.CppRuleClasses;
import com.google.devtools.build.lib.rules.proto.ProtoSourceFileBlacklist;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.util.FileType;
import com.google.devtools.build.lib.util.FileTypeSet;
import java.io.Serializable;

/**
 * Shared rule classes and associated utility code for Objective-C rules.
 */
public class ObjcRuleClasses {

  /**
   * Name of the attribute used for implicit dependency on the libtool wrapper.
   */
  public static final String LIBTOOL_ATTRIBUTE = "$libtool";
  /** Name of the attribute used for implicit dependency on the header_scanner tool. */
  public static final String HEADER_SCANNER_ATTRIBUTE = ":header_scanner";
  /** Name of attribute used for implicit dependency on the apple SDKs. */
  public static final String APPLE_SDK_ATTRIBUTE = ":apple_sdk";

  static final String LIPO = "lipo";
  static final String STRIP = "strip";

  private ObjcRuleClasses() {
    throw new UnsupportedOperationException("static-only");
  }

  /**
   * Creates and returns an {@link IntermediateArtifacts} object, using the given rule context
   * for fetching current-rule attributes, and using the given build configuration to determine
   * the appropriate output directory in which to root artifacts.
   */
  public static IntermediateArtifacts intermediateArtifacts(RuleContext ruleContext,
      BuildConfiguration buildConfiguration) {
    return new IntermediateArtifacts(ruleContext, /*archiveFileNameSuffix*/ "",
        /*outputPrefix*/ "", buildConfiguration);
  }

  /**
   * Creates and returns an {@link IntermediateArtifacts} object, using the given rule context
   * for fetching current-rule attributes and the current rule's configuration for determining the
   * appropriate output output directory in which to root artifacts.
   */
  public static IntermediateArtifacts intermediateArtifacts(RuleContext ruleContext) {
    return new IntermediateArtifacts(ruleContext, /*archiveFileNameSuffix=*/ "");
  }

  /** Attribute name for a dummy target in a child configuration. */
  static final String CHILD_CONFIG_ATTR = "$child_configuration_dummy";

  /**
   * Returns a {@link IntermediateArtifacts} to be used to compile and link the ObjC source files
   * generated by J2ObjC.
   */
  static IntermediateArtifacts j2objcIntermediateArtifacts(RuleContext ruleContext) {
    // We need to append "_j2objc" to the name of the generated archive file to distinguish it from
    // the C/C++ archive file created by proto_library targets with attribute cc_api_version
    // specified.
    // Generate an umbrella header for the module map. The headers declared in module maps are
    // compiled using the #import directives which are incompatible with J2ObjC segmented headers.
    // We need to #iclude all the headers in an umbrella header and then declare the umbrella header
    // in the module map.
    return new IntermediateArtifacts(
        ruleContext,
        /*archiveFileNameSuffix=*/"_j2objc",
        UmbrellaHeaderStrategy.GENERATE);
  }

  public static Artifact artifactByAppendingToBaseName(RuleContext context, String suffix) {
    return context.getPackageRelativeArtifact(
        context.getLabel().getName() + suffix, context.getBinOrGenfilesDirectory());
  }

  public static ObjcConfiguration objcConfiguration(RuleContext ruleContext) {
    return ruleContext.getFragment(ObjcConfiguration.class);
  }

  /**
   * Returns true if the source file can be instrumented for coverage.
   */
  public static boolean isInstrumentable(Artifact sourceArtifact) {
    return !ASSEMBLY_SOURCES.matches(sourceArtifact.getFilename());
  }

  /**
   * Creates a new spawn action builder with apple environment variables set that are typically
   * needed by the apple toolchain. This should be used to start to build spawn actions that, in
   * order to run, require both a darwin architecture and a collection of environment variables
   * which contain information about the target and host architectures.
   */
  static SpawnAction.Builder spawnAppleEnvActionBuilder(
      XcodeConfigProvider xcodeConfigProvider, ApplePlatform targetPlatform) {
    return spawnOnDarwinActionBuilder()
        .setEnvironment(appleToolchainEnvironment(xcodeConfigProvider, targetPlatform));
  }

  /** Returns apple environment variables that are typically needed by the apple toolchain. */
  static ImmutableMap<String, String> appleToolchainEnvironment(
      XcodeConfigProvider xcodeConfigProvider, ApplePlatform targetPlatform) {
    return ImmutableMap.<String, String>builder()
        .putAll(AppleConfiguration.appleTargetPlatformEnv(
            targetPlatform, xcodeConfigProvider.getSdkVersionForPlatform(targetPlatform)))
        .putAll(AppleConfiguration.getXcodeVersionEnv(xcodeConfigProvider.getXcodeVersion()))
        .build();
  }

  /**
   * Creates a new spawn action builder that requires a darwin architecture to run.
   */
  static SpawnAction.Builder spawnOnDarwinActionBuilder() {
    return new SpawnAction.Builder().setExecutionInfo(darwinActionExecutionRequirement());
  }

  /**
   * Returns action requirement information for darwin architecture.
   */
  static ImmutableMap<String, String> darwinActionExecutionRequirement() {
    return ImmutableMap.of(ExecutionRequirements.REQUIRES_DARWIN, "");
  }

  /**
   * Creates a new spawn action builder that requires a darwin architecture to run and calls bash
   * to execute cmd.
   * Once we have a fix for b/21874752  we should be able to call setShellCommand(cmd)
   * directly, but right now we don't have a buildhelpers package on Macs so we must specify
   * the path to /bin/bash explicitly.
   */
  static SpawnAction.Builder spawnBashOnDarwinActionBuilder(String cmd) {
    return spawnOnDarwinActionBuilder()
        .setShellCommand(ImmutableList.of("/bin/bash", "-c", cmd));
  }

  /**
   * Creates a new configured target builder with the given {@code filesToBuild}, which are also
   * used as runfiles.
   *
   * @param ruleContext the current rule context
   * @param filesToBuild files to build for this target. These also become the data runfiles
   */
  static RuleConfiguredTargetBuilder ruleConfiguredTarget(RuleContext ruleContext,
      NestedSet<Artifact> filesToBuild) {
    RunfilesProvider runfilesProvider = RunfilesProvider.withData(
        new Runfiles.Builder(
            ruleContext.getWorkspaceName(),
            ruleContext.getConfiguration().legacyExternalRunfiles())
            .addRunfiles(ruleContext, RunfilesProvider.DEFAULT_RUNFILES).build(),
        new Runfiles.Builder(
            ruleContext.getWorkspaceName(),
            ruleContext.getConfiguration().legacyExternalRunfiles())
            .addTransitiveArtifacts(filesToBuild).build());

    return new RuleConfiguredTargetBuilder(ruleContext)
        .setFilesToBuild(filesToBuild)
        .add(RunfilesProvider.class, runfilesProvider);
  }

  /**
   * Attributes for {@code objc_*} rules that have compiler options.
   */
  public static class CoptsRule implements RuleDefinition {
    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          /* <!-- #BLAZE_RULE($objc_opts_rule).ATTRIBUTE(copts) -->
          Extra flags to pass to the compiler.
          Subject to <a href="${link make-variables}">"Make variable"</a> substitution and
          <a href="${link common-definitions#sh-tokenization}">Bourne shell tokenization</a>.
          These flags will only apply to this target, and not those upon which
          it depends, or those which depend on it.
          <p>
          Note that for the generated Xcode project, directory paths specified using "-I" flags in
          copts are parsed out, prepended with "$(WORKSPACE_ROOT)/" if they are relative paths, and
          added to the header search paths for the associated Xcode target.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("copts", STRING_LIST))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$objc_opts_rule")
          .type(RuleClassType.ABSTRACT)
          .build();
    }
  }

  /**
   * Attributes for {@code objc_*} rules that can link in SDK frameworks.
   */
  public static class SdkFrameworksDependerRule implements RuleDefinition {
    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment environment) {
      return builder
          /* <!-- #BLAZE_RULE($objc_sdk_frameworks_depender_rule).ATTRIBUTE(sdk_frameworks) -->
          Names of SDK frameworks to link with (e.g. "AddressBook", "QuartzCore"). "UIKit" and
          "Foundation" are always included when building for the iOS, tvOS and watchOS platforms.
          For macOS, only "Foundation" is always included.

          <p> When linking a top level binary (e.g. apple_binary), all SDK frameworks listed in that
          binary's transitive dependency graph are linked.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("sdk_frameworks", STRING_LIST))
          /* <!-- #BLAZE_RULE($objc_sdk_frameworks_depender_rule).ATTRIBUTE(weak_sdk_frameworks) -->
          Names of SDK frameworks to weakly link with. For instance,
          "MediaAccessibility".

          In difference to regularly linked SDK frameworks, symbols
          from weakly linked frameworks do not cause an error if they
          are not present at runtime.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("weak_sdk_frameworks", STRING_LIST))
          /* <!-- #BLAZE_RULE($objc_sdk_frameworks_depender_rule).ATTRIBUTE(sdk_dylibs) -->
          Names of SDK .dylib libraries to link with. For instance, "libz" or
          "libarchive".

          "libc++" is included automatically if the binary has any C++ or
          Objective-C++ sources in its dependency tree. When linking a binary,
          all libraries named in that binary's transitive dependency graph are
          used.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("sdk_dylibs", STRING_LIST))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$objc_sdk_frameworks_depender_rule")
          .type(RuleClassType.ABSTRACT)
          .build();
    }
  }

  /**
   * Iff a file matches this type, it is considered to use C++.
   */
  static final FileType CPP_SOURCES = FileType.of(".cc", ".cpp", ".mm", ".cxx", ".C");

  static final FileType NON_CPP_SOURCES = FileType.of(".m", ".c");

  static final FileType ASSEMBLY_SOURCES = FileType.of(".s", ".S", ".asm");

  static final FileType OBJECT_FILE_SOURCES = FileType.of(".o");

  /**
   * Header files, which are not compiled directly, but may be included/imported from source files.
   */
  static final FileType HEADERS = FileType.of(".h", ".inc", ".hpp", ".hh");

  /**
   * Files allowed in the srcs attribute. This includes private headers.
   */
  static final FileTypeSet SRCS_TYPE =
      FileTypeSet.of(
          NON_CPP_SOURCES,
          CPP_SOURCES,
          ASSEMBLY_SOURCES,
          OBJECT_FILE_SOURCES,
          HEADERS);

  /** Files that should actually be compiled. */
  static final FileTypeSet COMPILABLE_SRCS_TYPE =
      FileTypeSet.of(NON_CPP_SOURCES, CPP_SOURCES, ASSEMBLY_SOURCES);

  /**
   * Files that are already compiled.
   */
  static final FileTypeSet PRECOMPILED_SRCS_TYPE = FileTypeSet.of(OBJECT_FILE_SOURCES);

  static final FileTypeSet NON_ARC_SRCS_TYPE = FileTypeSet.of(FileType.of(".m", ".mm"));

  // TODO(bazel-team): Restrict this to actual header files only.
  static final FileTypeSet HDRS_TYPE = FileTypeSet.ANY_FILE;

  /**
   * Coverage note files which contain information to reconstruct the basic block graphs and assign
   * source line numbers to blocks.
   */
  static final FileType COVERAGE_NOTES = FileType.of(".gcno");

  /**
   * Common attributes for {@code objc_*} rules that depend on a crosstool.
   */
  public static class CrosstoolRule implements RuleDefinition {
    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      return builder
          .add(
              attr(CcToolchain.CC_TOOLCHAIN_DEFAULT_ATTRIBUTE_NAME, LABEL)
                  .mandatoryProviders(CcToolchainProvider.PROVIDER.id())
                  .value(CppRuleClasses.ccToolchainAttribute(env)))
          .add(
              attr(CcToolchain.CC_TOOLCHAIN_TYPE_ATTRIBUTE_NAME, NODEP_LABEL)
                  .value(CppRuleClasses.ccToolchainTypeAttribute(env)))
          .addRequiredToolchains(ImmutableList.of(CppRuleClasses.ccToolchainTypeAttribute(env)))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$objc_crosstool_rule")
          .type(RuleClassType.ABSTRACT)
          .build();
    }
  }

  /**
   * Common attributes for {@code objc_*} rules that can be input to compilation (i.e. can be
   * dependencies of other compiling rules).
   */
  public static class CompileDependencyRule implements RuleDefinition {
    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      return builder
          /* <!-- #BLAZE_RULE($objc_compile_dependency_rule).ATTRIBUTE(hdrs) -->
          The list of C, C++, Objective-C, and Objective-C++ header files published
          by this library to be included by sources in dependent rules.
          <p>
          These headers describe the public interface for the library and will be
          made available for inclusion by sources in this rule or in dependent
          rules. Headers not meant to be included by a client of this library
          should be listed in the srcs attribute instead.
          <p>
          These will be compiled separately from the source if modules are enabled.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("hdrs", LABEL_LIST)
              .direct_compile_time_input()
              .allowedFileTypes(HDRS_TYPE))
          /* <!-- #BLAZE_RULE($objc_compile_dependency_rule).ATTRIBUTE(textual_hdrs) -->
          The list of C, C++, Objective-C, and Objective-C++ files that are
          included as headers by source files in this rule or by users of this
          library. Unlike hdrs, these will not be compiled separately from the
          sources.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("textual_hdrs", LABEL_LIST)
              .direct_compile_time_input()
              .allowedFileTypes(HDRS_TYPE))
          /* <!-- #BLAZE_RULE($objc_compile_dependency_rule).ATTRIBUTE(includes) -->
          List of <code>#include/#import</code> search paths to add to this target
          and all depending targets.

          This is to support third party and open-sourced libraries that do not
          specify the entire workspace path in their
          <code>#import/#include</code> statements.
          <p>
          The paths are interpreted relative to the package directory, and the
          genfiles and bin roots (e.g. <code>blaze-genfiles/pkg/includedir</code>
          and <code>blaze-out/pkg/includedir</code>) are included in addition to the
          actual client root.
          <p>
          Unlike <a href="${link objc_library.copts}">COPTS</a>, these flags are added for this rule
          and every rule that depends on it. (Note: not the rules it depends upon!) Be
          very careful, since this may have far-reaching effects.  When in doubt, add
          "-iquote" flags to <a href="${link objc_library.copts}">COPTS</a> instead.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("includes", Type.STRING_LIST))
          /* <!-- #BLAZE_RULE($objc_compile_dependency_rule).ATTRIBUTE(sdk_includes) -->
          List of <code>#include/#import</code> search paths to add to this target
          and all depending targets, where each path is relative to
          <code>$(SDKROOT)/usr/include</code>.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("sdk_includes", Type.STRING_LIST))
          .build();
    }
    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$objc_compile_dependency_rule")
          .type(RuleClassType.ABSTRACT)
          .ancestors(CppRuleClasses.CcIncludeScanningRule.class, SdkFrameworksDependerRule.class)
          .build();
    }
  }

  /**
   * Common attributes for {@code objc_*} rules that contain compilable content.
   */
  public static class CompilingRule implements RuleDefinition {

    /**
     * Rule class names for cc rules which are allowed as targets of the 'deps' attribute of this
     * rule.
     */
    static final ImmutableSet<String> ALLOWED_CC_DEPS_RULE_CLASSES =
        ImmutableSet.of("cc_library", "cc_inc_library");

    @AutoCodec @AutoCodec.VisibleForSerialization
    static final Attribute.LateBoundDefault<ObjcConfiguration, Label> SDK_LATE_BOUND_DEFAULT =
        LabelLateBoundDefault.fromTargetConfiguration(
            ObjcConfiguration.class,
            null,
            // Apple SDKs are currently only used by ObjC header thinning feature
            (rule, attributes, objcConfig) ->
                objcConfig.useExperimentalHeaderThinning() ? objcConfig.getAppleSdk() : null);

    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      return builder
          /* <!-- #BLAZE_RULE($objc_compiling_rule).ATTRIBUTE(srcs) -->
          The list of C, C++, Objective-C, and Objective-C++ source and header
          files that are processed to create the library target.
          These are your checked-in files, plus any generated files.
          Source files are compiled into .o files with Clang. Header files
          may be included/imported by any source or header in the srcs attribute
          of this target, but not by headers in hdrs or any targets that depend
          on this rule.
          Additionally, precompiled .o files may be given as srcs.  Be careful to
          ensure consistency in the architecture of provided .o files and that of the
          build to avoid missing symbol linker errors.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("srcs", LABEL_LIST).direct_compile_time_input().allowedFileTypes(SRCS_TYPE))
          /* <!-- #BLAZE_RULE($objc_compiling_rule).ATTRIBUTE(non_arc_srcs) -->
          The list of Objective-C files that are processed to create the
          library target that DO NOT use ARC.
          The files in this attribute are treated very similar to those in the
          srcs attribute, but are compiled without ARC enabled.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(
              attr("non_arc_srcs", LABEL_LIST)
                  .direct_compile_time_input()
                  .allowedFileTypes(NON_ARC_SRCS_TYPE))
          /* <!-- #BLAZE_RULE($objc_compiling_rule).ATTRIBUTE(pch) -->
          Header file to prepend to every source file being compiled (both arc
          and non-arc).
          Use of pch files is actively discouraged in BUILD files, and this should be
          considered deprecated. Since pch files are not actually precompiled this is not
          a build-speed enhancement, and instead is just a global dependency. From a build
          efficiency point of view you are actually better including what you need directly
          in your sources where you need it.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("pch", LABEL).direct_compile_time_input().allowedFileTypes(FileType.of(".pch")))
          /* <!-- #BLAZE_RULE($objc_compiling_rule).ATTRIBUTE(deps) -->
          The list of targets that are linked together to form the final bundle.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .override(
              attr("deps", LABEL_LIST)
                  .direct_compile_time_input()
                  .allowedRuleClasses(ALLOWED_CC_DEPS_RULE_CLASSES)
                  .mandatoryProviders(ObjcProvider.SKYLARK_CONSTRUCTOR.id())
                  .allowedFileTypes())
          /* <!-- #BLAZE_RULE($objc_compiling_rule).ATTRIBUTE(runtime_deps) -->
          The list of framework targets that are late loaded at runtime.  They are included in the
          app bundle but not linked against at build time.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(
              attr("runtime_deps", LABEL_LIST)
                  .direct_compile_time_input()
                  .mandatoryProviders(AppleDynamicFrameworkInfo.SKYLARK_CONSTRUCTOR.id())
                  .allowedFileTypes())
          /* <!-- #BLAZE_RULE($objc_compiling_rule).ATTRIBUTE(defines) -->
          Extra <code>-D</code> flags to pass to the compiler. They should be in
          the form <code>KEY=VALUE</code> or simply <code>KEY</code> and are
          passed not only to the compiler for this target (as <code>copts</code>
          are) but also to all <code>objc_</code> dependers of this target.
          Subject to <a href="${link make-variables}">"Make variable"</a> substitution and
          <a href="${link common-definitions#sh-tokenization}">Bourne shell tokenization</a>.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("defines", STRING_LIST))
          /* <!-- #BLAZE_RULE($objc_compiling_rule).ATTRIBUTE(enable_modules) -->
          Enables clang module support (via -fmodules).
          Setting this to 1 will allow you to @import system headers and other targets:
          @import UIKit;
          @import path_to_package_target;
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("enable_modules", BOOLEAN))
          /* <!-- #BLAZE_RULE($objc_compiling_rule).ATTRIBUTE(module_map) -->
          A custom Clang module map for this target. Use of a custom module map is discouraged. Most
          users should use module maps generated by Bazel.
          If specified, Bazel will not generate a module map for this target, but will pass the
          provided module map to the compiler.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("module_map", LABEL).allowedFileTypes(FileType.of(".modulemap")))
          /* <!-- #BLAZE_RULE($objc_compiling_rule).ATTRIBUTE(module_name) -->
          Sets the module name for this target. By default the module name is the target path with
          all special symbols replaced by _, e.g. //foo/baz:bar can be imported as foo_baz_bar.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("module_name", STRING))
          /* Provides the label for header_scanner tool that is used to scan inclusions for ObjC
          sources and provide a list of required headers via a .header_list file.

          Either points to a label for a binary which can be executed for header scanning or an
          empty filegroup to indicate that the tool is unavailable. Due to the possibility of this
          being an empty filegroup and executable prerequisites validating that they contain at
          least one artifact this attribute cannot be #exec(). */
          .add(
              attr(HEADER_SCANNER_ATTRIBUTE, LABEL)
                  .cfg(HostTransition.createFactory())
                  .value(headerScannerAttribute(env)))
          .add(attr(APPLE_SDK_ATTRIBUTE, LABEL).value(SDK_LATE_BOUND_DEFAULT))
          .build();
    }
    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$objc_compiling_rule")
          .type(RuleClassType.ABSTRACT)
          .ancestors(
              BaseRuleClasses.RuleBase.class,
              CompileDependencyRule.class,
              CoptsRule.class,
              LibtoolRule.class,
              XcrunRule.class,
              CrosstoolRule.class)
          .build();
    }
  }

  static LabelLateBoundDefault<ObjcConfiguration> headerScannerAttribute(
      RuleDefinitionEnvironment env) {
    return LabelLateBoundDefault.fromTargetConfiguration(
        ObjcConfiguration.class,
        env.getToolsLabel("//tools/objc:header_scanner"),
        (Attribute.LateBoundDefault.Resolver<ObjcConfiguration, Label> & Serializable)
            (rule, attributes, objcConfig) -> objcConfig.getObjcHeaderScannerTool());
  }

  /**
   * Common attributes for {@code objc_*} rules that need to call libtool.
   */
  public static class LibtoolRule implements RuleDefinition {
    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      return builder
          .add(
              attr(LIBTOOL_ATTRIBUTE, LABEL)
                  .cfg(HostTransition.createFactory())
                  .exec()
                  .value(env.getToolsLabel("//tools/objc:libtool")))
          .build();
    }
    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$objc_libtool_rule")
          .type(RuleClassType.ABSTRACT)
          .ancestors(XcrunRule.class)
          .build();
    }
  }

  /**
   * Common attributes for {@code objc_*} rules that can optionally be set to {@code alwayslink}.
   */
  public static class AlwaysLinkRule implements RuleDefinition {
    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      return builder
          /* <!-- #BLAZE_RULE($objc_alwayslink_rule).ATTRIBUTE(alwayslink) -->
          If 1, any bundle or binary that depends (directly or indirectly) on this
          library will link in all the object files for the files listed in
          <code>srcs</code> and <code>non_arc_srcs</code>, even if some contain no
          symbols referenced by the binary.
          This is useful if your code isn't explicitly called by code in
          the binary, e.g., if your code registers to receive some callback
          provided by some service.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("alwayslink", BOOLEAN))
          .build();
    }
    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$objc_alwayslink_rule")
          .type(RuleClassType.ABSTRACT)
          .ancestors(CompileDependencyRule.class)
          .build();
    }
  }

  /**
   * Protocol buffer related implicit attributes.
   */
  static final String PROTO_COMPILER_ATTR = "$googlemac_proto_compiler";
  static final String PROTO_COMPILER_SUPPORT_ATTR = "$googlemac_proto_compiler_support";
  static final String PROTO_LIB_ATTR = "$lib_protobuf";
  static final String PROTOBUF_WELL_KNOWN_TYPES = "$protobuf_well_known_types";

  /**
   * Template for the fat binary output (using Apple's "lipo" tool to combine binaries of multiple
   * architectures).
   */
  static final SafeImplicitOutputsFunction LIPOBIN_OUTPUT = fromTemplates("%{name}_lipobin");

  /**
   * Common attributes for {@code objc_*} rules that link sources and dependencies.
   */
  public static class LinkingRule implements RuleDefinition {

    private final ObjcProtoAspect objcProtoAspect;

    public LinkingRule(ObjcProtoAspect objcProtoAspect) {
      this.objcProtoAspect = objcProtoAspect;
    }

    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      return builder
          .add(
              attr("$j2objc_dead_code_pruner", LABEL)
                  .allowedFileTypes(FileType.of(".py"))
                  .cfg(HostTransition.createFactory())
                  .exec()
                  .singleArtifact()
                  .value(env.getToolsLabel("//tools/objc:j2objc_dead_code_pruner")))
          .add(attr("$dummy_lib", LABEL).value(env.getToolsLabel("//tools/objc:dummy_lib")))
          .add(
              attr(PROTO_COMPILER_ATTR, LABEL)
                  .allowedFileTypes(FileType.of(".sh"))
                  .cfg(HostTransition.createFactory())
                  .singleArtifact()
                  .value(env.getToolsLabel("//tools/objc:protobuf_compiler_wrapper")))
          .add(
              attr(PROTO_COMPILER_SUPPORT_ATTR, LABEL)
                  .legacyAllowAnyFileType()
                  .cfg(HostTransition.createFactory())
                  .value(env.getToolsLabel("//tools/objc:protobuf_compiler_support")))
          .add(
              ProtoSourceFileBlacklist.blacklistFilegroupAttribute(
                  PROTOBUF_WELL_KNOWN_TYPES,
                  ImmutableList.of(env.getToolsLabel("//tools/objc:protobuf_well_known_types"))))
          .override(builder.copy("deps").aspect(objcProtoAspect))
          /* <!-- #BLAZE_RULE($objc_linking_rule).ATTRIBUTE(linkopts) -->
          Extra flags to pass to the linker.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("linkopts", STRING_LIST))
          .build();
    }
    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$objc_linking_rule")
          .type(RuleClassType.ABSTRACT)
          .ancestors(CompilingRule.class)
          .build();
    }
  }

  /**
   * Common attributes for apple rules that produce outputs for a given platform type (such as ios
   * or watchos).
   */
  public static class PlatformRule implements RuleDefinition {

    /**
     * Attribute name for apple platform type (e.g. ios or watchos).
     */
    static final String PLATFORM_TYPE_ATTR_NAME = "platform_type";

    /**
     * Attribute name for the minimum OS version (e.g. "7.3").
     */
    static final String MINIMUM_OS_VERSION = "minimum_os_version";

    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      return builder
          /* <!-- #BLAZE_RULE($apple_platform_rule).ATTRIBUTE(platform_type) -->
          The type of platform for which to create artifacts in this rule.

          This dictates which Apple platform SDK is used for compilation/linking and which flag is
          used to determine the architectures for which to build. For example, if <code>ios</code>
          is selected, then the output binaries/libraries will be created combining all
          architectures specified in <code>--ios_multi_cpus</code>.

          Options are:
          <ul>
            <li>
              <code>ios</code>: architectures gathered from <code>--ios_multi_cpus</code>.
            </li>
            <li>
              <code>macos</code>: architectures gathered from <code>--macos_cpus</code>.
            </li>
            <li>
              <code>tvos</code>: architectures gathered from <code>--tvos_cpus</code>.
            </li>
            <li>
              <code>watchos</code>: architectures gathered from <code>--watchos_cpus</code>.
            </li>
          </ul>
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(
              attr(PLATFORM_TYPE_ATTR_NAME, STRING)
                  .mandatory())
          /* <!-- #BLAZE_RULE($apple_platform_rule).ATTRIBUTE(minimum_os_version) -->
          The minimum OS version that this target and its dependencies should be built for.

          This should be a dotted version string such as "7.3".
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(
              attr(MINIMUM_OS_VERSION, STRING))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$apple_platform_rule")
          .type(RuleClassType.ABSTRACT)
          .build();
    }
  }

  /**
   * Common attributes for apple rules that build multi-architecture outputs for a given platform
   * type (such as ios or watchos).
   */
  public static class MultiArchPlatformRule implements RuleDefinition {

    private final ObjcProtoAspect objcProtoAspect;

    public MultiArchPlatformRule(ObjcProtoAspect objcProtoAspect) {
      this.objcProtoAspect = objcProtoAspect;
    }

    /**
     * Rule class names for cc rules which are allowed as targets of the 'deps' attribute of this
     * rule.
     */
    static final ImmutableSet<String> ALLOWED_CC_DEPS_RULE_CLASSES =
        ImmutableSet.of("cc_library", "cc_inc_library");

    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      MultiArchSplitTransitionProvider splitTransitionProvider =
          new MultiArchSplitTransitionProvider();
      return builder
          // This is currently a hack to obtain all child configurations regardless of the attribute
          // values of this rule -- this rule does not currently use the actual info provided by
          // this attribute.
          .add(
              attr(CHILD_CONFIG_ATTR, LABEL)
                  .cfg(splitTransitionProvider)
                  .value(env.getToolsLabel("//tools/cpp:current_cc_toolchain")))
          /* <!-- #BLAZE_RULE($apple_multiarch_rule).ATTRIBUTE(deps) -->
          The list of targets that are linked together to form the final binary.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .override(
              attr("deps", LABEL_LIST)
                  .direct_compile_time_input()
                  .allowedRuleClasses(ALLOWED_CC_DEPS_RULE_CLASSES)
                  .mandatoryProviders(ObjcProvider.SKYLARK_CONSTRUCTOR.id())
                  .cfg(splitTransitionProvider)
                  .aspect(objcProtoAspect)
                  .allowedFileTypes())
          /* <!-- #BLAZE_RULE($apple_multiarch_rule).ATTRIBUTE(linkopts) -->
          Extra flags to pass to the linker.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr("linkopts", STRING_LIST))
          .add(
              attr("$j2objc_dead_code_pruner", LABEL)
                  .allowedFileTypes(FileType.of(".py"))
                  .cfg(HostTransition.createFactory())
                  .exec()
                  .singleArtifact()
                  .value(env.getToolsLabel("//tools/objc:j2objc_dead_code_pruner")))
          .add(attr("$dummy_lib", LABEL).value(env.getToolsLabel("//tools/objc:dummy_lib")))
          .add(
              attr(PROTO_COMPILER_ATTR, LABEL)
                  .allowedFileTypes(FileType.of(".sh"))
                  .cfg(HostTransition.createFactory())
                  .singleArtifact()
                  .value(env.getToolsLabel("//tools/objc:protobuf_compiler_wrapper")))
          .add(
              attr(PROTO_COMPILER_SUPPORT_ATTR, LABEL)
                  .legacyAllowAnyFileType()
                  .cfg(HostTransition.createFactory())
                  .value(env.getToolsLabel("//tools/objc:protobuf_compiler_support")))
          .add(
              ProtoSourceFileBlacklist.blacklistFilegroupAttribute(
                  PROTOBUF_WELL_KNOWN_TYPES,
                  ImmutableList.of(env.getToolsLabel("//tools/objc:protobuf_well_known_types"))))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$apple_multiarch_rule")
          .type(RuleClassType.ABSTRACT)
          .ancestors(
              PlatformRule.class,
              CrosstoolRule.class,
              BaseRuleClasses.RuleBase.class,
              CppRuleClasses.CcIncludeScanningRule.class,
              XcrunRule.class,
              SdkFrameworksDependerRule.class,
              LibtoolRule.class)
          .build();
    }
  }

  /**
   * Common attributes for apple rules that can depend on one or more dynamic libraries.
   */
  public static class DylibDependingRule implements RuleDefinition {

    private final ObjcProtoAspect objcProtoAspect;

    public DylibDependingRule(ObjcProtoAspect objcProtoAspect) {
      this.objcProtoAspect = objcProtoAspect;
    }

    /**
     * Attribute name for dylib dependencies.
     */
    static final String DYLIBS_ATTR_NAME = "dylibs";

    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      return builder
          /* <!-- #BLAZE_RULE($apple_dylib_depending_rule).ATTRIBUTE(dylibs) -->
          <p>A list of dynamic library targets to be linked against in this rule and included
          in the final binary. Libraries which are transitive dependencies of any such dylibs will
          not be statically linked in this target (even if they are otherwise
          transitively depended on via the <code>deps</code> attribute) to avoid duplicate symbols.
          <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
          .add(attr(DYLIBS_ATTR_NAME, LABEL_LIST)
              .direct_compile_time_input()
              .mandatoryProviders(ImmutableList.of(
                  SkylarkProviderIdentifier.forKey(
                      AppleDynamicFrameworkInfo.SKYLARK_CONSTRUCTOR.getKey())))
              .allowedFileTypes()
              .aspect(objcProtoAspect))
          .build();
    }

    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$apple_dylib_depending_rule")
          .type(RuleClassType.ABSTRACT)
          .build();
    }
  }

  /**
   * Common attributes for {@code objc_*} rules that need to call xcrun.
   */
  public static class XcrunRule implements RuleDefinition {
    @Override
    public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
      return builder
          .add(
              attr("$xcrunwrapper", LABEL)
                  .cfg(HostTransition.createFactory())
                  .exec()
                  .value(env.getToolsLabel("//tools/objc:xcrunwrapper")))
          .build();
    }
    @Override
    public Metadata getMetadata() {
      return RuleDefinition.Metadata.builder()
          .name("$objc_xcrun_rule")
          .type(RuleClassType.ABSTRACT)
          .ancestors(RequiresXcodeConfigRule.class)
          .build();
    }
  }
}
