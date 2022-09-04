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
package com.google.devtools.build.lib.rules.cpp;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.actions.ActionAnalysisMetadata;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.AnalysisEnvironment;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.MakeVariableSupplier;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.TemplateVariableInfo;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.config.CompilationMode;
import com.google.devtools.build.lib.analysis.configuredtargets.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.platform.ToolchainInfo;
import com.google.devtools.build.lib.analysis.test.InstrumentedFilesCollector;
import com.google.devtools.build.lib.analysis.test.InstrumentedFilesCollector.LocalMetadataCollector;
import com.google.devtools.build.lib.analysis.test.InstrumentedFilesProvider;
import com.google.devtools.build.lib.analysis.test.InstrumentedFilesProviderImpl;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.PackageIdentifier;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.rules.apple.ApplePlatform;
import com.google.devtools.build.lib.rules.cpp.CcCompilationHelper.SourceCategory;
import com.google.devtools.build.lib.rules.cpp.CcToolchainFeatures.CollidingProvidesException;
import com.google.devtools.build.lib.rules.cpp.CcToolchainFeatures.FeatureConfiguration;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration.DynamicMode;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration.HeadersCheckingMode;
import com.google.devtools.build.lib.rules.cpp.FdoSupport.FdoMode;
import com.google.devtools.build.lib.rules.cpp.Link.LinkTargetType;
import com.google.devtools.build.lib.shell.ShellUtils;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec.VisibleForSerialization;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.util.FileType;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.Nullable;

/**
 * Common parts of the implementation of cc rules.
 */
public final class CcCommon {

  /** Name of the build variable for the sysroot path variable name. */
  public static final String SYSROOT_VARIABLE_NAME = "sysroot";

  /** Name of the build variable for the path to the input file being processed. */
  public static final String INPUT_FILE_VARIABLE_NAME = "input_file";

  public static final String PIC_CONFIGURATION_ERROR =
      "PIC compilation is requested but the toolchain does not support it";

  private static final String NO_COPTS_ATTRIBUTE = "nocopts";

  /**
   * Collects all metadata files generated by C++ compilation actions that output the .o files
   * on the input.
   */
  private static final LocalMetadataCollector CC_METADATA_COLLECTOR =
      new LocalMetadataCollector() {
    @Override
    public void collectMetadataArtifacts(Iterable<Artifact> objectFiles,
        AnalysisEnvironment analysisEnvironment, NestedSetBuilder<Artifact> metadataFilesBuilder) {
      for (Artifact artifact : objectFiles) {
        ActionAnalysisMetadata action = analysisEnvironment.getLocalGeneratingAction(artifact);
        if (action instanceof CppCompileAction) {
          addOutputs(metadataFilesBuilder, action, CppFileTypes.COVERAGE_NOTES);
        }
      }
    }
  };

  public static final ImmutableSet<String> ALL_COMPILE_ACTIONS =
      ImmutableSet.of(
          CppCompileAction.C_COMPILE,
          CppCompileAction.CPP_COMPILE,
          CppCompileAction.CPP_HEADER_PARSING,
          CppCompileAction.CPP_HEADER_PREPROCESSING,
          CppCompileAction.CPP_MODULE_COMPILE,
          CppCompileAction.CPP_MODULE_CODEGEN,
          CppCompileAction.ASSEMBLE,
          CppCompileAction.PREPROCESS_ASSEMBLE,
          CppCompileAction.CLIF_MATCH,
          CppCompileAction.LINKSTAMP_COMPILE,
          CppCompileAction.CC_FLAGS_MAKE_VARIABLE_ACTION_NAME);

  public static final ImmutableSet<String> ALL_LINK_ACTIONS =
      ImmutableSet.of(
          LinkTargetType.EXECUTABLE.getActionName(),
          Link.LinkTargetType.DYNAMIC_LIBRARY.getActionName(),
          Link.LinkTargetType.NODEPS_DYNAMIC_LIBRARY.getActionName());

  public static final ImmutableSet<String> ALL_ARCHIVE_ACTIONS =
      ImmutableSet.of(Link.LinkTargetType.STATIC_LIBRARY.getActionName());

  public static final ImmutableSet<String> ALL_OTHER_ACTIONS =
      ImmutableSet.of(CppCompileAction.STRIP_ACTION_NAME);

  /** Action configs we request to enable. */
  public static final ImmutableSet<String> DEFAULT_ACTION_CONFIGS =
      ImmutableSet.<String>builder()
          .addAll(ALL_COMPILE_ACTIONS)
          .addAll(ALL_LINK_ACTIONS)
          .addAll(ALL_ARCHIVE_ACTIONS)
          .addAll(ALL_OTHER_ACTIONS)
          .build();

  /** Features we request to enable unless a rule explicitly doesn't support them. */
  private static final ImmutableSet<String> DEFAULT_FEATURES =
      ImmutableSet.of(
          CppRuleClasses.DEPENDENCY_FILE,
          CppRuleClasses.RANDOM_SEED,
          CppRuleClasses.MODULE_MAPS,
          CppRuleClasses.MODULE_MAP_HOME_CWD,
          CppRuleClasses.HEADER_MODULE_COMPILE,
          CppRuleClasses.INCLUDE_PATHS,
          CppRuleClasses.PIC,
          CppRuleClasses.PREPROCESSOR_DEFINES);

  public static final String CC_TOOLCHAIN_DEFAULT_ATTRIBUTE_NAME = ":cc_toolchain";

  /** C++ configuration */
  private final CppConfiguration cppConfiguration;

  private final RuleContext ruleContext;

  private final CcToolchainProvider ccToolchain;

  private final FdoSupportProvider fdoSupport;

  public CcCommon(RuleContext ruleContext) {
    this.ruleContext = ruleContext;
    this.cppConfiguration = ruleContext.getFragment(CppConfiguration.class);
    this.ccToolchain =
        Preconditions.checkNotNull(
            CppHelper.getToolchainUsingDefaultCcToolchainAttribute(ruleContext));
    this.fdoSupport =
        Preconditions.checkNotNull(
            CppHelper.getFdoSupportUsingDefaultCcToolchainAttribute(ruleContext));
  }

  /**
   * Merges a list of output groups into one. The sets for each entry with a given key are merged.
   */
  public static Map<String, NestedSet<Artifact>> mergeOutputGroups(
      ImmutableList<Map<String, NestedSet<Artifact>>> outputGroups) {
    Map<String, NestedSetBuilder<Artifact>> mergedOutputGroupsBuilder = new TreeMap<>();

    for (Map<String, NestedSet<Artifact>> outputGroup : outputGroups) {
      for (Map.Entry<String, NestedSet<Artifact>> entryOutputGroup : outputGroup.entrySet()) {
        String key = entryOutputGroup.getKey();
        mergedOutputGroupsBuilder.computeIfAbsent(
            key, (String k) -> NestedSetBuilder.compileOrder());
        mergedOutputGroupsBuilder.get(key).addTransitive(entryOutputGroup.getValue());
      }
    }

    Map<String, NestedSet<Artifact>> mergedOutputGroups = new TreeMap<>();
    for (Map.Entry<String, NestedSetBuilder<Artifact>> entryOutputGroupBuilder :
        mergedOutputGroupsBuilder.entrySet()) {
      mergedOutputGroups.put(
          entryOutputGroupBuilder.getKey(), entryOutputGroupBuilder.getValue().build());
    }
    return mergedOutputGroups;
  }

  /**
   * Returns our own linkopts from the rule attribute. This determines linker
   * options to use when building this target and anything that depends on it.
   */
  public ImmutableList<String> getLinkopts() {
    Preconditions.checkState(hasAttribute("linkopts", Type.STRING_LIST));
    Iterable<String> ourLinkopts = ruleContext.attributes().get("linkopts", Type.STRING_LIST);
    List<String> result;
    if (ourLinkopts != null) {
      boolean allowDashStatic =
          !cppConfiguration.forceIgnoreDashStatic()
              && (CppHelper.getDynamicMode(cppConfiguration, ccToolchain) != DynamicMode.FULLY);
      if (!allowDashStatic) {
        ourLinkopts = Iterables.filter(ourLinkopts, (v) -> !"-static".equals(v));
      }
      result = CppHelper.expandLinkopts(ruleContext, "linkopts", ourLinkopts);
    } else {
      result = ImmutableList.of();
    }

    if (ApplePlatform.isApplePlatform(ccToolchain.getTargetCpu()) && result.contains("-static")) {
      ruleContext.attributeError(
          "linkopts", "Apple builds do not support statically linked binaries");
    }

    return ImmutableList.copyOf(result);
  }

  public ImmutableList<String> getCopts() {
    if (!getCoptsFilter(ruleContext).passesFilter("-Wno-future-warnings")) {
      ruleContext.attributeWarning(
          "nocopts",
          String.format(
              "Regular expression '%s' is too general; for example, it matches "
                  + "'-Wno-future-warnings'.  Thus it might *re-enable* compiler warnings we wish "
                  + "to disable globally.  To disable all compiler warnings, add '-w' to copts "
                  + "instead",
              Preconditions.checkNotNull(getNoCoptsPattern(ruleContext))));
    }

    return ImmutableList.<String>builder()
        .addAll(CppHelper.getPackageCopts(ruleContext))
        .addAll(CppHelper.getAttributeCopts(ruleContext))
        .build();
  }

  private boolean hasAttribute(String name, Type<?> type) {
    return ruleContext.attributes().has(name, type);
  }

  /** Collects all .dwo artifacts in this target's transitive closure. */
  public static DwoArtifactsCollector collectTransitiveDwoArtifacts(
      RuleContext ruleContext,
      CcCompilationOutputs compilationOutputs,
      boolean generateDwo,
      boolean ltoBackendArtifactsUsePic,
      Iterable<LtoBackendArtifacts> ltoBackendArtifacts) {
    ImmutableList.Builder<TransitiveInfoCollection> deps =
        ImmutableList.<TransitiveInfoCollection>builder();

    deps.addAll(ruleContext.getPrerequisites("deps", Mode.TARGET));

    if (ruleContext.attributes().has("malloc", BuildType.LABEL)) {
      deps.add(CppHelper.mallocForTarget(ruleContext));
    }

    return compilationOutputs == null // Possible in LIPO collection mode (see initializationHook).
        ? DwoArtifactsCollector.emptyCollector()
        : DwoArtifactsCollector.transitiveCollector(
            compilationOutputs,
            deps.build(),
            generateDwo,
            ltoBackendArtifactsUsePic,
            ltoBackendArtifacts);
  }

  public TransitiveLipoInfoProvider collectTransitiveLipoLabels(CcCompilationOutputs outputs) {
    if (fdoSupport.getFdoSupport().getFdoRoot() == null
        || !cppConfiguration.isLipoContextCollector()) {
      return TransitiveLipoInfoProvider.EMPTY;
    }

    NestedSetBuilder<IncludeScannable> scannableBuilder = NestedSetBuilder.stableOrder();
    CppHelper.addTransitiveLipoInfoForCommonAttributes(ruleContext, outputs, scannableBuilder);
    return new TransitiveLipoInfoProvider(scannableBuilder.build());
  }

  /**
   * Returns a list of ({@link Artifact}, {@link Label}) pairs. Each pair represents an input
   * source file and the label of the rule that generates it (or the label of the source file
   * itself if it is an input file).
   */
  List<Pair<Artifact, Label>> getSources() {
    Map<Artifact, Label> map = Maps.newLinkedHashMap();
    Iterable<? extends TransitiveInfoCollection> providers =
        ruleContext.getPrerequisitesIf("srcs", Mode.TARGET, FileProvider.class);
    for (TransitiveInfoCollection provider : providers) {
      for (Artifact artifact : provider.getProvider(FileProvider.class).getFilesToBuild()) {
        // TODO(bazel-team): We currently do not produce an error for duplicate headers and other
        // non-source artifacts with different labels, as that would require cleaning up the code
        // base without significant benefit; we should eventually make this consistent one way or
        // the other.
        Label oldLabel = map.put(artifact, provider.getLabel());
        boolean isHeader = CppFileTypes.CPP_HEADER.matches(artifact.getExecPath());
        if (!isHeader
            && SourceCategory.CC_AND_OBJC.getSourceTypes().matches(artifact.getExecPathString())
            && oldLabel != null
            && !oldLabel.equals(provider.getLabel())) {
          ruleContext.attributeError("srcs", String.format(
              "Artifact '%s' is duplicated (through '%s' and '%s')",
              artifact.getExecPathString(), oldLabel, provider.getLabel()));
        }
      }
    }

    ImmutableList.Builder<Pair<Artifact, Label>> result = ImmutableList.builder();
    for (Map.Entry<Artifact, Label> entry : map.entrySet()) {
      result.add(Pair.of(entry.getKey(), entry.getValue()));
    }
    return result.build();
  }

  /**
   * Returns the files from headers and does some sanity checks. Note that this method reports
   * warnings to the {@link RuleContext} as a side effect, and so should only be called once for any
   * given rule.
   */
  public static List<Pair<Artifact, Label>> getHeaders(RuleContext ruleContext) {
    Map<Artifact, Label> map = Maps.newLinkedHashMap();
    for (TransitiveInfoCollection target :
        ruleContext.getPrerequisitesIf("hdrs", Mode.TARGET, FileProvider.class)) {
      FileProvider provider = target.getProvider(FileProvider.class);
      for (Artifact artifact : provider.getFilesToBuild()) {
        if (CppRuleClasses.DISALLOWED_HDRS_FILES.matches(artifact.getFilename())) {
          ruleContext.attributeWarning("hdrs", "file '" + artifact.getFilename()
              + "' from target '" + target.getLabel() + "' is not allowed in hdrs");
          continue;
        }
        Label oldLabel = map.put(artifact, target.getLabel());
        if (oldLabel != null && !oldLabel.equals(target.getLabel())) {
          ruleContext.attributeWarning(
              "hdrs",
              String.format(
                  "Artifact '%s' is duplicated (through '%s' and '%s')",
                  artifact.getExecPathString(),
                  oldLabel,
                  target.getLabel()));
        }
      }
    }

    ImmutableList.Builder<Pair<Artifact, Label>> result = ImmutableList.builder();
    for (Map.Entry<Artifact, Label> entry : map.entrySet()) {
      result.add(Pair.of(entry.getKey(), entry.getValue()));
    }
    return result.build();
  }

  /**
   * Returns the C++ toolchain provider.
   */
  public CcToolchainProvider getToolchain() {
    return ccToolchain;
  }

  /**
   * Returns the C++ FDO optimization support provider.
   */
  public FdoSupportProvider getFdoSupport() {
    return fdoSupport;
  }

  /**
   * Returns the files from headers and does some sanity checks. Note that this method reports
   * warnings to the {@link RuleContext} as a side effect, and so should only be called once for any
   * given rule.
   */
  public List<Pair<Artifact, Label>> getHeaders() {
    return getHeaders(ruleContext);
  }

  /**
   * Supply CC_FLAGS Make variable value computed from FeatureConfiguration. Appends them to
   * original CC_FLAGS, so FeatureConfiguration can override legacy values.
   */
  public static class CcFlagsSupplier implements MakeVariableSupplier {

    private final RuleContext ruleContext;

    public CcFlagsSupplier(RuleContext ruleContext) {
      this.ruleContext = Preconditions.checkNotNull(ruleContext);
    }

    @Override
    @Nullable
    public String getMakeVariable(String variableName) {
      if (!variableName.equals(CppConfiguration.CC_FLAGS_MAKE_VARIABLE_NAME)) {
        return null;
      }

      return CcCommon.computeCcFlags(ruleContext, ruleContext.getPrerequisite(
          CcToolchain.CC_TOOLCHAIN_DEFAULT_ATTRIBUTE_NAME, Mode.TARGET));
    }

    @Override
    public ImmutableMap<String, String> getAllMakeVariables() {
      return ImmutableMap.of(
          CppConfiguration.CC_FLAGS_MAKE_VARIABLE_NAME,
          getMakeVariable(CppConfiguration.CC_FLAGS_MAKE_VARIABLE_NAME));
    }
  }

  /** A filter that removes copts from a c++ compile action according to a nocopts regex. */
  @AutoCodec
  static class CoptsFilter {
    private final Pattern noCoptsPattern;
    private final boolean allPasses;

    @VisibleForSerialization
    CoptsFilter(Pattern noCoptsPattern, boolean allPasses) {
      this.noCoptsPattern = noCoptsPattern;
      this.allPasses = allPasses;
    }

    /** Creates a filter that filters all matches to a regex. */
    public static CoptsFilter fromRegex(Pattern noCoptsPattern) {
      return new CoptsFilter(noCoptsPattern, false);
    }

    /** Creates a filter that passes on all inputs. */
    public static CoptsFilter alwaysPasses() {
      return new CoptsFilter(null, true);
    }

    /**
     * Returns true if the provided string passes through the filter, or false if it should be
     * removed.
     */
    public boolean passesFilter(String flag) {
      if (allPasses) {
        return true;
      } else {
        return !noCoptsPattern.matcher(flag).matches();
      }
    }
  }

  /** Returns copts filter built from the make variable expanded nocopts attribute. */
  CoptsFilter getCoptsFilter() {
    return getCoptsFilter(ruleContext);
  }

  /** @see CcCommon#getCoptsFilter() */
  private static CoptsFilter getCoptsFilter(RuleContext ruleContext) {
    Pattern noCoptsPattern = getNoCoptsPattern(ruleContext);
    if (noCoptsPattern == null) {
      return CoptsFilter.alwaysPasses();
    }
    return CoptsFilter.fromRegex(noCoptsPattern);
  }

  @Nullable
  private static Pattern getNoCoptsPattern(RuleContext ruleContext) {
    if (!ruleContext.getRule().isAttrDefined(NO_COPTS_ATTRIBUTE, Type.STRING)) {
      return null;
    }
    String nocoptsValue = ruleContext.attributes().get(NO_COPTS_ATTRIBUTE, Type.STRING);
    if (Strings.isNullOrEmpty(nocoptsValue)) {
      return null;
    }
    String nocoptsAttr = ruleContext.getExpander().expand(NO_COPTS_ATTRIBUTE, nocoptsValue);
    try {
      return Pattern.compile(nocoptsAttr);
    } catch (PatternSyntaxException e) {
      ruleContext.attributeError(
          NO_COPTS_ATTRIBUTE,
          "invalid regular expression '" + nocoptsAttr + "': " + e.getMessage());
      return null;
    }
  }

  // TODO(bazel-team): calculating nocopts every time is not very efficient,
  // fix this after the rule migration. The problem is that in some cases we call this after
  // the RCT is created (so RuleContext is not accessible), in some cases during the creation.
  // It would probably make more sense to use TransitiveInfoProviders.
  /**
   * Returns true if the rule context has a nocopts regex that matches the given value, false
   * otherwise.
   */
  static boolean noCoptsMatches(String option, RuleContext ruleContext) {
    return !getCoptsFilter(ruleContext).passesFilter(option);
  }

  private static final String DEFINES_ATTRIBUTE = "defines";

  /**
   * Returns a list of define tokens from "defines" attribute.
   *
   * <p>We tokenize the "defines" attribute, to ensure that the handling of
   * quotes and backslash escapes is consistent Bazel's treatment of the "copts" attribute.
   *
   * <p>But we require that the "defines" attribute consists of a single token.
   */
  public List<String> getDefines() {
    List<String> defines = new ArrayList<>();
    for (String define : ruleContext.getExpander().list(DEFINES_ATTRIBUTE)) {
      List<String> tokens = new ArrayList<>();
      try {
        ShellUtils.tokenize(tokens, define);
        if (tokens.size() == 1) {
          defines.add(tokens.get(0));
        } else if (tokens.isEmpty()) {
          ruleContext.attributeError(DEFINES_ATTRIBUTE, "empty definition not allowed");
        } else {
          ruleContext.attributeError(DEFINES_ATTRIBUTE,
              "definition contains too many tokens (found " + tokens.size()
              + ", expecting exactly one)");
        }
      } catch (ShellUtils.TokenizationException e) {
        ruleContext.attributeError(DEFINES_ATTRIBUTE, e.getMessage());
      }
    }
    return defines;
  }

  /**
   * Determines a list of loose include directories that are only allowed to be referenced when
   * headers checking is {@link HeadersCheckingMode#LOOSE} or {@link HeadersCheckingMode#WARN}.
   */
  List<PathFragment> getLooseIncludeDirs() {
    List<PathFragment> result = new ArrayList<>();
    // The package directory of the rule contributes includes. Note that this also covers all
    // non-subpackage sub-directories.
    PathFragment rulePackage = ruleContext.getLabel().getPackageIdentifier()
        .getPathUnderExecRoot();
    result.add(rulePackage);

    // Gather up all the dirs from the rule's srcs as well as any of the srcs outputs.
    if (hasAttribute("srcs", BuildType.LABEL_LIST)) {
      for (TransitiveInfoCollection src :
          ruleContext.getPrerequisitesIf("srcs", Mode.TARGET, FileProvider.class)) {
        PathFragment packageDir = src.getLabel().getPackageIdentifier().getPathUnderExecRoot();
        for (Artifact a : src.getProvider(FileProvider.class).getFilesToBuild()) {
          result.add(packageDir);
          // Attempt to gather subdirectories that might contain include files.
          result.add(a.getRootRelativePath().getParentDirectory());
        }
      }
    }

    // Add in any 'includes' attribute values as relative path fragments
    if (ruleContext.getRule().isAttributeValueExplicitlySpecified("includes")) {
      PathFragment packageFragment = ruleContext.getLabel().getPackageIdentifier()
          .getPathUnderExecRoot();
      // For now, anything with an 'includes' needs a blanket declaration
      result.add(packageFragment.getRelative("**"));
    }
    return result;
  }

  List<PathFragment> getSystemIncludeDirs() {
    List<PathFragment> result = new ArrayList<>();
    PackageIdentifier packageIdentifier = ruleContext.getLabel().getPackageIdentifier();
    PathFragment packageFragment = packageIdentifier.getPathUnderExecRoot();
    for (String includesAttr : ruleContext.getExpander().list("includes")) {
      if (includesAttr.startsWith("/")) {
        ruleContext.attributeWarning("includes",
            "ignoring invalid absolute path '" + includesAttr + "'");
        continue;
      }
      PathFragment includesPath = packageFragment.getRelative(includesAttr);
      if (includesPath.containsUplevelReferences()) {
        ruleContext.attributeError("includes",
            "Path references a path above the execution root.");
      }
      if (includesPath.isEmpty()) {
        ruleContext.attributeError(
            "includes",
            "'"
                + includesAttr
                + "' resolves to the workspace root, which would allow this rule and all of its "
                + "transitive dependents to include any file in your workspace. Please include only"
                + " what you need");
      } else if (!includesPath.startsWith(packageFragment)) {
        ruleContext.attributeWarning(
            "includes",
            "'"
                + includesAttr
                + "' resolves to '"
                + includesPath
                + "' not below the relative path of its package '"
                + packageFragment
                + "'. This will be an error in the future");
      }
      result.add(includesPath);
      result.add(ruleContext.getConfiguration().getGenfilesFragment().getRelative(includesPath));
      result.add(ruleContext.getConfiguration().getBinFragment().getRelative(includesPath));
    }
    return result;
  }

  /** Collects compilation prerequisite artifacts. */
  static NestedSet<Artifact> collectCompilationPrerequisites(
      RuleContext ruleContext, CcCompilationContext ccCompilationContext) {
    // TODO(bazel-team): Use ccCompilationContext.getCompilationPrerequisites() instead; note
    // that this
    // will
    // need cleaning up the prerequisites, as the {@code CcCompilationContext} currently
    // collects them
    // transitively (to get transitive headers), but source files are not transitive compilation
    // prerequisites.
    NestedSetBuilder<Artifact> prerequisites = NestedSetBuilder.stableOrder();
    if (ruleContext.attributes().has("srcs", BuildType.LABEL_LIST)) {
      for (FileProvider provider :
          ruleContext.getPrerequisites("srcs", Mode.TARGET, FileProvider.class)) {
        prerequisites.addAll(
            FileType.filter(
                provider.getFilesToBuild(), SourceCategory.CC_AND_OBJC.getSourceTypes()));
      }
    }
    prerequisites.addTransitive(ccCompilationContext.getDeclaredIncludeSrcs());
    prerequisites.addTransitive(ccCompilationContext.getAdditionalInputs());
    prerequisites.addTransitive(ccCompilationContext.getTransitiveModules(true));
    prerequisites.addTransitive(ccCompilationContext.getTransitiveModules(false));
    return prerequisites.build();
  }

  /**
   * Replaces shared library artifact with mangled symlink and creates related
   * symlink action. For artifacts that should retain filename (e.g. libraries
   * with SONAME tag), link is created to the parent directory instead.
   *
   * This action is performed to minimize number of -rpath entries used during
   * linking process (by essentially "collecting" as many shared libraries as
   * possible in the single directory), since we will be paying quadratic price
   * for each additional entry on the -rpath.
   *
   * @param library Shared library artifact that needs to be mangled
   * @param preserveName true if filename should be preserved, false - mangled.
   * @return mangled symlink artifact.
   */
  public Artifact getDynamicLibrarySymlink(Artifact library, boolean preserveName) {
    return SolibSymlinkAction.getDynamicLibrarySymlink(
        ruleContext,
        ccToolchain.getSolibDirectory(),
        library,
        preserveName,
        true,
        ruleContext.getConfiguration());
  }

  /**
   * Returns any linker scripts found in the dependencies of the rule.
   */
  Iterable<Artifact> getLinkerScripts() {
    return FileType.filter(ruleContext.getPrerequisiteArtifacts("deps", Mode.TARGET).list(),
        CppFileTypes.LINKER_SCRIPT);
  }

  /** Returns the Windows DEF file specified in win_def_file attribute of the rule. */
  @Nullable
  Artifact getWinDefFile() {
    return ruleContext.getPrerequisiteArtifact("win_def_file", Mode.TARGET);
  }

  /**
   * Provides support for instrumentation.
   */
  public InstrumentedFilesProvider getInstrumentedFilesProvider(Iterable<Artifact> files,
      boolean withBaselineCoverage) {
    return cppConfiguration.isLipoContextCollector()
        ? InstrumentedFilesProviderImpl.EMPTY
        : InstrumentedFilesCollector.collect(
            ruleContext, CppRuleClasses.INSTRUMENTATION_SPEC, CC_METADATA_COLLECTOR, files,
            CppHelper.getGcovFilesIfNeeded(ruleContext, ccToolchain),
            CppHelper.getCoverageEnvironmentIfNeeded(ruleContext, ccToolchain),
            withBaselineCoverage);
  }

  public static ImmutableList<String> getCoverageFeatures(CcToolchainProvider toolchain) {
    ImmutableList.Builder<String> coverageFeatures = ImmutableList.builder();
    if (toolchain.isCodeCoverageEnabled()) {
      coverageFeatures.add(CppRuleClasses.COVERAGE);
      if (toolchain.useLLVMCoverageMapFormat()) {
        coverageFeatures.add(CppRuleClasses.LLVM_COVERAGE_MAP_FORMAT);
      } else {
        coverageFeatures.add(CppRuleClasses.GCC_COVERAGE_MAP_FORMAT);
      }
    }
    return coverageFeatures.build();
  }

  /**
   * Determines whether to statically link the C++ runtimes.
   *
   * <p>This is complicated because it depends both on a legacy field in the CROSSTOOL
   * protobuf--supports_embedded_runtimes--and the newer crosstool
   * feature--statically_link_cpp_runtimes. Neither, one, or both could be present or set. Or they
   * could be set in to conflicting values.
   *
   * @return true if we should statically link, false otherwise.
   */
  private static boolean enableStaticLinkCppRuntimesFeature(
      ImmutableSet<String> requestedFeatures,
      ImmutableSet<String> disabledFeatures,
      CcToolchainProvider toolchain) {
    // All of these cases are encountered in various unit tests,
    // integration tests, and obscure CROSSTOOLs.

    // A. If the legacy field "supports_embedded_runtimes" is false (or not present):
    //      dynamically link the cpp runtimes. Done.
    if (!toolchain.supportsEmbeddedRuntimes()) {
      return false;
    }
    // From here, the toolchain _can_ statically link the cpp runtime.
    //
    // B. If the feature static_link_cpp_runtimes is disabled:
    //      dynamically link the cpp runtimes. Done.
    if (disabledFeatures.contains(CppRuleClasses.STATIC_LINK_CPP_RUNTIMES)) {
      return false;
    }
    // C. If the feature is not requested:
    //     the feature is neither disabled nor requested: statically
    //     link (for compatibility with the legacy field).
    if (!requestedFeatures.contains(CppRuleClasses.STATIC_LINK_CPP_RUNTIMES)) {
      return true;
    }
    // D. The feature is requested:
    //     statically link the cpp runtimes. Done.
    return true;
  }

  /**
   * Creates a feature configuration for a given rule. Assumes strictly cc sources.
   *
   * @param ruleContext the context of the rule we want the feature configuration for.
   * @param toolchain C++ toolchain provider.
   * @return the feature configuration for the given {@code ruleContext}.
   */
  public static FeatureConfiguration configureFeaturesOrReportRuleError(
      RuleContext ruleContext, CcToolchainProvider toolchain) {
    return configureFeaturesOrReportRuleError(
        ruleContext,
        /* requestedFeatures= */ ruleContext.getFeatures(),
        /* unsupportedFeatures= */ ruleContext.getDisabledFeatures(),
        toolchain);
  }

  /**
   * Creates the feature configuration for a given rule.
   *
   * @return the feature configuration for the given {@code ruleContext}.
   */
  public static FeatureConfiguration configureFeaturesOrReportRuleError(
      RuleContext ruleContext,
      ImmutableSet<String> requestedFeatures,
      ImmutableSet<String> unsupportedFeatures,
      CcToolchainProvider toolchain) {
    try {
      return configureFeaturesOrThrowEvalException(
          requestedFeatures, unsupportedFeatures, toolchain);
    } catch (EvalException e) {
      ruleContext.ruleError(e.getMessage());
      return FeatureConfiguration.EMPTY;
    }
  }

  public static FeatureConfiguration configureFeaturesOrThrowEvalException(
      ImmutableSet<String> requestedFeatures,
      ImmutableSet<String> unsupportedFeatures,
      CcToolchainProvider toolchain)
      throws EvalException {
    CppConfiguration cppConfiguration = toolchain.getCppConfiguration();
    ImmutableSet.Builder<String> allRequestedFeaturesBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<String> unsupportedFeaturesBuilder = ImmutableSet.builder();
    unsupportedFeaturesBuilder.addAll(unsupportedFeatures);
    if (!toolchain.supportsHeaderParsing()) {
      // TODO(bazel-team): Remove once supports_header_parsing has been removed from the
      // cc_toolchain rule.
      unsupportedFeaturesBuilder.add(CppRuleClasses.PARSE_HEADERS);
      unsupportedFeaturesBuilder.add(CppRuleClasses.PREPROCESS_HEADERS);
    }
    if (toolchain.getCcCompilationContext().getCppModuleMap() == null) {
      unsupportedFeaturesBuilder.add(CppRuleClasses.MODULE_MAPS);
    }
    if (enableStaticLinkCppRuntimesFeature(requestedFeatures, unsupportedFeatures, toolchain)) {
      allRequestedFeaturesBuilder.add(CppRuleClasses.STATIC_LINK_CPP_RUNTIMES);
    } else {
      unsupportedFeaturesBuilder.add(CppRuleClasses.STATIC_LINK_CPP_RUNTIMES);
    }

    ImmutableSet<String> allUnsupportedFeatures = unsupportedFeaturesBuilder.build();

    // If STATIC_LINK_MSVCRT feature isn't specified by user, we add DYNAMIC_LINK_MSVCRT_* feature
    // according to compilation mode.
    // If STATIC_LINK_MSVCRT feature is specified, we add STATIC_LINK_MSVCRT_* feature
    // according to compilation mode.
    if (requestedFeatures.contains(CppRuleClasses.STATIC_LINK_MSVCRT)) {
      allRequestedFeaturesBuilder.add(
          toolchain.getCompilationMode() == CompilationMode.DBG
              ? CppRuleClasses.STATIC_LINK_MSVCRT_DEBUG
              : CppRuleClasses.STATIC_LINK_MSVCRT_NO_DEBUG);
    } else {
      allRequestedFeaturesBuilder.add(
          toolchain.getCompilationMode() == CompilationMode.DBG
              ? CppRuleClasses.DYNAMIC_LINK_MSVCRT_DEBUG
              : CppRuleClasses.DYNAMIC_LINK_MSVCRT_NO_DEBUG);
    }

    ImmutableList.Builder<String> allFeatures =
        new ImmutableList.Builder<String>()
            .addAll(ImmutableSet.of(toolchain.getCompilationMode().toString()))
            .addAll(DEFAULT_FEATURES)
            .addAll(DEFAULT_ACTION_CONFIGS)
            .addAll(requestedFeatures)
            .addAll(toolchain.getFeatures().getDefaultFeaturesAndActionConfigs());

    if (toolchain.isHostConfiguration()) {
      allFeatures.add("host");
    } else {
      allFeatures.add("nonhost");
    }

    if (toolchain.useFission()) {
      allFeatures.add(CppRuleClasses.PER_OBJECT_DEBUG_INFO);
    }

    allFeatures.addAll(getCoverageFeatures(toolchain));

    if (cppConfiguration.getFdoInstrument() != null
        && !allUnsupportedFeatures.contains(CppRuleClasses.FDO_INSTRUMENT)) {
      allFeatures.add(CppRuleClasses.FDO_INSTRUMENT);
    }

    FdoMode fdoMode = toolchain.getFdoMode();
    boolean isFdo = fdoMode != FdoMode.OFF && toolchain.getCompilationMode() == CompilationMode.OPT;
    if (isFdo
        && fdoMode != FdoMode.AUTO_FDO
        && !allUnsupportedFeatures.contains(CppRuleClasses.FDO_OPTIMIZE)) {
      allFeatures.add(CppRuleClasses.FDO_OPTIMIZE);
      // For LLVM, support implicit enabling of ThinLTO for FDO unless it has been
      // explicitly disabled.
      if (toolchain.isLLVMCompiler() && !allUnsupportedFeatures.contains(CppRuleClasses.THIN_LTO)) {
        allFeatures.add(CppRuleClasses.ENABLE_FDO_THINLTO);
      }
    }
    if (isFdo && fdoMode == FdoMode.AUTO_FDO) {
      allFeatures.add(CppRuleClasses.AUTOFDO);
      // For LLVM, support implicit enabling of ThinLTO for AFDO unless it has been
      // explicitly disabled.
      if (toolchain.isLLVMCompiler() && !allUnsupportedFeatures.contains(CppRuleClasses.THIN_LTO)) {
        allFeatures.add(CppRuleClasses.ENABLE_AFDO_THINLTO);
      }
    }
    if (cppConfiguration.getFdoPrefetchHintsLabel() != null) {
      allRequestedFeaturesBuilder.add(CppRuleClasses.FDO_PREFETCH_HINTS);
    }
    if (cppConfiguration.isLipoOptimizationOrInstrumentation()) {
      // Map LIPO to ThinLTO for LLVM builds.
      if (toolchain.isLLVMCompiler() && fdoMode != FdoMode.OFF) {
        if (!allUnsupportedFeatures.contains(CppRuleClasses.THIN_LTO)) {
          allFeatures.add(CppRuleClasses.THIN_LTO);
        }
      } else {
        allFeatures.add(CppRuleClasses.LIPO);
      }
    }

    for (String feature : allFeatures.build()) {
      if (!allUnsupportedFeatures.contains(feature)) {
        allRequestedFeaturesBuilder.add(feature);
      }
    }

    try {
      FeatureConfiguration featureConfiguration =
          toolchain.getFeatures().getFeatureConfiguration(allRequestedFeaturesBuilder.build());
      for (String feature : unsupportedFeatures) {
        if (featureConfiguration.isEnabled(feature)) {
          throw new EvalException(
              /* location= */ null,
              "The C++ toolchain '"
                  + toolchain.getCcToolchainLabel()
                  + "' unconditionally implies feature '"
                  + feature
                  + "', which is unsupported by this rule. "
                  + "This is most likely a misconfiguration in the C++ toolchain.");
        }
      }
      if ((cppConfiguration.forcePic() || toolchain.toolchainNeedsPic())
          && !featureConfiguration.isEnabled(CppRuleClasses.PIC)) {
        throw new EvalException(/* location= */ null, PIC_CONFIGURATION_ERROR);
      }
      return featureConfiguration;
    } catch (CollidingProvidesException e) {
      throw new EvalException(/* location= */ null, e.getMessage());
    }
  }

  /**
   * Computes the appropriate value of the {@code $(CC_FLAGS)} Make variable based on the given
   * toolchain.
   */
  public static String computeCcFlags(RuleContext ruleContext, TransitiveInfoCollection toolchain) {
    CcToolchainProvider toolchainProvider =
        (CcToolchainProvider) toolchain.get(ToolchainInfo.PROVIDER);
    FeatureConfiguration featureConfiguration =
        CcCommon.configureFeaturesOrReportRuleError(ruleContext, toolchainProvider);
    if (!featureConfiguration.actionIsConfigured(
        CppCompileAction.CC_FLAGS_MAKE_VARIABLE_ACTION_NAME)) {
      return null;
    }

    CcToolchainVariables buildVariables = toolchainProvider.getBuildVariables();
    String toolchainCcFlags =
        Joiner.on(" ")
            .join(
                featureConfiguration.getCommandLine(
                    CppCompileAction.CC_FLAGS_MAKE_VARIABLE_ACTION_NAME, buildVariables));
    String oldCcFlags = "";
    TemplateVariableInfo templateVariableInfo =
        toolchain.get(TemplateVariableInfo.PROVIDER);
    if (templateVariableInfo != null) {
      oldCcFlags = templateVariableInfo.getVariables().getOrDefault(
          CppConfiguration.CC_FLAGS_MAKE_VARIABLE_NAME, "");
    }
    return FluentIterable.of(oldCcFlags)
        .append(toolchainCcFlags)
        .join(Joiner.on(" "));
  }
}
