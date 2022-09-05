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
package com.google.devtools.build.lib.rules.cpp;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.AnalysisEnvironment;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.rules.cpp.CcToolchainFeatures.FeatureConfiguration;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration.DynamicMode;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration.HeadersCheckingMode;
import com.google.devtools.build.lib.rules.cpp.LinkerInputs.LibraryToLink;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesCollector;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesCollector.LocalMetadataCollector;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesProvider;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesProviderImpl;
import com.google.devtools.build.lib.shell.ShellUtils;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.util.FileType;
import com.google.devtools.build.lib.util.FileTypeSet;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Common parts of the implementation of cc rules.
 */
public final class CcCommon {

  private static final String NO_COPTS_ATTRIBUTE = "nocopts";

  private static final FileTypeSet SOURCE_TYPES = FileTypeSet.of(
      CppFileTypes.CPP_SOURCE,
      CppFileTypes.CPP_HEADER,
      CppFileTypes.C_SOURCE,
      CppFileTypes.ASSEMBLER_WITH_C_PREPROCESSOR);

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
        Action action = analysisEnvironment.getLocalGeneratingAction(artifact);
        if (action instanceof CppCompileAction) {
          addOutputs(metadataFilesBuilder, action, CppFileTypes.COVERAGE_NOTES);
        }
      }
    }
  };
    
  /**
   * Features we request to enable unless a rule explicitly doesn't support them.
   */
  private static final ImmutableSet<String> DEFAULT_FEATURES = ImmutableSet.of(
      CppRuleClasses.MODULE_MAPS,
      CppRuleClasses.MODULE_MAP_HOME_CWD,
      CppRuleClasses.HEADER_MODULE_INCLUDES_DEPENDENCIES,
      CppRuleClasses.INCLUDE_PATHS);

  /** C++ configuration */
  private final CppConfiguration cppConfiguration;
  
  /** Active toolchain features. */
  private final FeatureConfiguration featureConfiguration;

  /** The Artifacts from srcs. */
  private final ImmutableList<Artifact> sources;

  private final ImmutableList<Pair<Artifact, Label>> cAndCppSources;

  /** Expanded and tokenized copts attribute.  Set by initCopts(). */
  private final ImmutableList<String> copts;

  /**
   * The expanded linkopts for this rule.
   */
  private final ImmutableList<String> linkopts;

  private final RuleContext ruleContext;

  public CcCommon(RuleContext ruleContext, FeatureConfiguration featureConfiguration) {
    this.ruleContext = ruleContext;
    this.cppConfiguration = ruleContext.getFragment(CppConfiguration.class);
    this.featureConfiguration = featureConfiguration;
    this.sources = hasAttribute("srcs", Type.LABEL_LIST)
        ? ruleContext.getPrerequisiteArtifacts("srcs", Mode.TARGET).list()
        : ImmutableList.<Artifact>of();

    this.cAndCppSources = collectCAndCppSources();
    copts = initCopts();
    linkopts = initLinkopts();
  }

  NestedSet<Artifact> getTemps(CcCompilationOutputs compilationOutputs) {
    return cppConfiguration.isLipoContextCollector()
        ? NestedSetBuilder.<Artifact>emptySet(Order.STABLE_ORDER)
        : compilationOutputs.getTemps();
  }

  /**
   * Returns our own linkopts from the rule attribute. This determines linker
   * options to use when building this target and anything that depends on it.
   */
  public ImmutableList<String> getLinkopts() {
    return linkopts;
  }

  public ImmutableList<String> getCopts() {
    return copts;
  }

  private boolean hasAttribute(String name, Type<?> type) {
    return ruleContext.attributes().has(name, type);
  }

  /**
   * Collects all .dwo artifacts in this target's transitive closure.
   */
  public static DwoArtifactsCollector collectTransitiveDwoArtifacts(
      RuleContext ruleContext,
      CcCompilationOutputs compilationOutputs) {
    ImmutableList.Builder<TransitiveInfoCollection> deps =
        ImmutableList.<TransitiveInfoCollection>builder();

    deps.addAll(ruleContext.getPrerequisites("deps", Mode.TARGET));

    if (ruleContext.attributes().has("malloc", Type.LABEL)) {
      deps.add(CppHelper.mallocForTarget(ruleContext));
    }

    return compilationOutputs == null  // Possible in LIPO collection mode (see initializationHook).
        ? DwoArtifactsCollector.emptyCollector()
        : DwoArtifactsCollector.transitiveCollector(compilationOutputs, deps.build());
  }

  public TransitiveLipoInfoProvider collectTransitiveLipoLabels(CcCompilationOutputs outputs) {
    if (cppConfiguration.getFdoSupport().getFdoRoot() == null
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
   * itself if it is an input file)
   */
  ImmutableList<Pair<Artifact, Label>> getCAndCppSources() {
    return cAndCppSources;
  }

  private boolean shouldProcessHeaders() {
    return featureConfiguration.isEnabled(CppRuleClasses.PREPROCESS_HEADERS)
        || featureConfiguration.isEnabled(CppRuleClasses.PARSE_HEADERS);
  }

  private ImmutableList<Pair<Artifact, Label>> collectCAndCppSources() {
    Map<Artifact, Label> map = Maps.newLinkedHashMap();
    if (!hasAttribute("srcs", Type.LABEL_LIST)) {
      return ImmutableList.<Pair<Artifact, Label>>of();
    }
    Iterable<FileProvider> providers =
        ruleContext.getPrerequisites("srcs", Mode.TARGET, FileProvider.class);
    // TODO(bazel-team): Move header processing logic down in the stack (to CcLibraryHelper or
    // such).
    boolean processHeaders = shouldProcessHeaders();
    if (processHeaders && hasAttribute("hdrs", Type.LABEL_LIST)) {
      providers = Iterables.concat(providers,
          ruleContext.getPrerequisites("hdrs", Mode.TARGET, FileProvider.class));
    }
    for (FileProvider provider : providers) {
      for (Artifact artifact : FileType.filter(provider.getFilesToBuild(), SOURCE_TYPES)) {
        boolean isHeader = CppFileTypes.CPP_HEADER.matches(artifact.getExecPath());
        if ((isHeader && !processHeaders)
            || CppFileTypes.CPP_TEXTUAL_INCLUDE.matches(artifact.getExecPath())) {
          continue;
        }
        Label oldLabel = map.put(artifact, provider.getLabel());
        // TODO(bazel-team): We currently do not warn for duplicate headers with
        // different labels, as that would require cleaning up the code base
        // without significant benefit; we should eventually make this
        // consistent one way or the other.
        if (!isHeader && oldLabel != null && !oldLabel.equals(provider.getLabel())) {
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

  Iterable<Artifact> getLibrariesFromSrcs() {
    return FileType.filter(sources, CppFileTypes.ARCHIVE, CppFileTypes.PIC_ARCHIVE,
        CppFileTypes.ALWAYS_LINK_LIBRARY, CppFileTypes.ALWAYS_LINK_PIC_LIBRARY,
        CppFileTypes.SHARED_LIBRARY,
        CppFileTypes.VERSIONED_SHARED_LIBRARY);
  }

  Iterable<Artifact> getSharedLibrariesFromSrcs() {
    return getSharedLibrariesFrom(sources);
  }

  static Iterable<Artifact> getSharedLibrariesFrom(Iterable<Artifact> collection) {
    return FileType.filter(collection, CppFileTypes.SHARED_LIBRARY,
        CppFileTypes.VERSIONED_SHARED_LIBRARY);
  }

  Iterable<Artifact> getStaticLibrariesFromSrcs() {
    return FileType.filter(sources, CppFileTypes.ARCHIVE, CppFileTypes.ALWAYS_LINK_LIBRARY);
  }

  Iterable<LibraryToLink> getPicStaticLibrariesFromSrcs() {
    return LinkerInputs.opaqueLibrariesToLink(
        FileType.filter(sources, CppFileTypes.PIC_ARCHIVE,
            CppFileTypes.ALWAYS_LINK_PIC_LIBRARY));
  }

  Iterable<Artifact> getObjectFilesFromSrcs(final boolean usePic) {
    if (usePic) {
      return Iterables.filter(sources, new Predicate<Artifact>() {
        @Override
        public boolean apply(Artifact artifact) {
          String filename = artifact.getExecPathString();

          // For compatibility with existing BUILD files, any ".o" files listed
          // in srcs are assumed to be position-independent code, or
          // at least suitable for inclusion in shared libraries, unless they
          // end with ".nopic.o". (The ".nopic.o" extension is an undocumented
          // feature to give users at least some control over this.) Note that
          // some target platforms do not require shared library code to be PIC.
          return CppFileTypes.PIC_OBJECT_FILE.matches(filename) ||
              (CppFileTypes.OBJECT_FILE.matches(filename) && !filename.endsWith(".nopic.o"));
        }
      });
    } else {
      return FileType.filter(sources, CppFileTypes.OBJECT_FILE);
    }
  }

  /**
   * Returns the files from headers and does some sanity checks. Note that this method reports
   * warnings to the {@link RuleContext} as a side effect, and so should only be called once for any
   * given rule.
   */
  public static List<Artifact> getHeaders(RuleContext ruleContext) {
    List<Artifact> hdrs = new ArrayList<>();
    for (TransitiveInfoCollection target :
        ruleContext.getPrerequisitesIf("hdrs", Mode.TARGET, FileProvider.class)) {
      FileProvider provider = target.getProvider(FileProvider.class);
      for (Artifact artifact : provider.getFilesToBuild()) {
        if (!CppRuleClasses.DISALLOWED_HDRS_FILES.matches(artifact.getFilename())) {
          hdrs.add(artifact);
        } else {
          ruleContext.attributeWarning("hdrs", "file '" + artifact.getFilename()
              + "' from target '" + target.getLabel() + "' is not allowed in hdrs");
        }
      }
    }
    return hdrs;
  }

  /**
   * Uses {@link #getHeaders(RuleContext)} to get the {@code hdrs} on this target. This method will
   * return an empty list if there is no {@code hdrs} attribute on this rule type.
   */
  List<Artifact> getHeaders() {
    if (!hasAttribute("hdrs", Type.LABEL_LIST)) {
      return ImmutableList.of();
    }
    return getHeaders(ruleContext);
  }

  HeadersCheckingMode determineHeadersCheckingMode() {
    HeadersCheckingMode headersCheckingMode = cppConfiguration.getHeadersCheckingMode();

    // Package default overrides command line option.
    if (ruleContext.getRule().getPackage().isDefaultHdrsCheckSet()) {
      String value =
          ruleContext.getRule().getPackage().getDefaultHdrsCheck().toUpperCase(Locale.ENGLISH);
      headersCheckingMode = HeadersCheckingMode.valueOf(value);
    }

    // 'hdrs_check' attribute overrides package default.
    if (hasAttribute("hdrs_check", Type.STRING)
        && ruleContext.getRule().isAttributeValueExplicitlySpecified("hdrs_check")) {
      try {
        String value = ruleContext.attributes().get("hdrs_check", Type.STRING)
            .toUpperCase(Locale.ENGLISH);
        headersCheckingMode = HeadersCheckingMode.valueOf(value);
      } catch (IllegalArgumentException e) {
        ruleContext.attributeError("hdrs_check", "must be one of: 'loose', 'warn' or 'strict'");
      }
    }

    return headersCheckingMode;
  }

  /**
   * Expand and tokenize the copts and nocopts attributes.
   */
  private ImmutableList<String> initCopts() {
    if (!hasAttribute("copts", Type.STRING_LIST)) {
      return ImmutableList.<String>of();
    }
    // TODO(bazel-team): getAttributeCopts should not tokenize the strings.
    // Make a warning for now.
    List<String> tokens = new ArrayList<>();
    for (String str : ruleContext.attributes().get("copts", Type.STRING_LIST)) {
      tokens.clear();
      try {
        ShellUtils.tokenize(tokens, str);
        if (tokens.size() > 1) {
          ruleContext.attributeWarning("copts",
              "each item in the list should contain only one option");
        }
      } catch (ShellUtils.TokenizationException e) {
        // ignore, the error is reported in the getAttributeCopts call
      }
    }

    Pattern nocopts = getNoCopts(ruleContext);
    if (nocopts != null && nocopts.matcher("-Wno-future-warnings").matches()) {
      ruleContext.attributeWarning("nocopts",
          "Regular expression '" + nocopts.pattern() + "' is too general; for example, it matches "
          + "'-Wno-future-warnings'.  Thus it might *re-enable* compiler warnings we wish to "
          + "disable globally.  To disable all compiler warnings, add '-w' to copts instead");
    }

    return ImmutableList.<String>builder()
        .addAll(getPackageCopts(ruleContext))
        .addAll(CppHelper.getAttributeCopts(ruleContext, "copts"))
        .build();
  }

  private static ImmutableList<String> getPackageCopts(RuleContext ruleContext) {
    List<String> unexpanded = ruleContext.getRule().getPackage().getDefaultCopts();
    return ImmutableList.copyOf(CppHelper.expandMakeVariables(ruleContext, "copts", unexpanded));
  }

  Pattern getNoCopts() {
    return getNoCopts(ruleContext);
  }

  /**
   * Returns nocopts pattern built from the make variable expanded nocopts
   * attribute.
   */
  private static Pattern getNoCopts(RuleContext ruleContext) {
    Pattern nocopts = null;
    if (ruleContext.getRule().isAttrDefined(NO_COPTS_ATTRIBUTE, Type.STRING)) {
      String nocoptsAttr = ruleContext.expandMakeVariables(NO_COPTS_ATTRIBUTE,
          ruleContext.attributes().get(NO_COPTS_ATTRIBUTE, Type.STRING));
      try {
        nocopts = Pattern.compile(nocoptsAttr);
      } catch (PatternSyntaxException e) {
        ruleContext.attributeError(NO_COPTS_ATTRIBUTE,
            "invalid regular expression '" + nocoptsAttr + "': " + e.getMessage());
      }
    }
    return nocopts;
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
    Pattern nocopts = getNoCopts(ruleContext);
    return nocopts == null ? false : nocopts.matcher(option).matches();
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
    for (String define :
      ruleContext.attributes().get(DEFINES_ATTRIBUTE, Type.STRING_LIST)) {
      List<String> tokens = new ArrayList<>();
      try {
        ShellUtils.tokenize(tokens, ruleContext.expandMakeVariables(DEFINES_ATTRIBUTE, define));
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
   * Collects our own linkopts from the rule attribute. This determines linker
   * options to use when building this library and anything that depends on it.
   */
  private final ImmutableList<String> initLinkopts() {
    if (!hasAttribute("linkopts", Type.STRING_LIST)) {
      return ImmutableList.<String>of();
    }
    List<String> ourLinkopts = ruleContext.attributes().get("linkopts", Type.STRING_LIST);
    List<String> result = new ArrayList<>();
    if (ourLinkopts != null) {
      boolean allowDashStatic = !cppConfiguration.forceIgnoreDashStatic()
          && (cppConfiguration.getDynamicMode() != DynamicMode.FULLY);
      for (String linkopt : ourLinkopts) {
        if (linkopt.equals("-static") && !allowDashStatic) {
          continue;
        }
        CppHelper.expandAttribute(ruleContext, result, "linkopts", linkopt, true);
      }
    }
    return ImmutableList.copyOf(result);
  }

  /**
   * Determines a list of loose include directories that are only allowed to be referenced when
   * headers checking is {@link HeadersCheckingMode#LOOSE} or {@link HeadersCheckingMode#WARN}.
   */
  List<PathFragment> getLooseIncludeDirs() {
    List<PathFragment> result = new ArrayList<>();
    // The package directory of the rule contributes includes. Note that this also covers all
    // non-subpackage sub-directories.
    PathFragment rulePackage = ruleContext.getLabel().getPackageIdentifier().getPathFragment();
    result.add(rulePackage);

    // Gather up all the dirs from the rule's srcs as well as any of the srcs outputs.
    if (hasAttribute("srcs", Type.LABEL_LIST)) {
      for (FileProvider src :
          ruleContext.getPrerequisites("srcs", Mode.TARGET, FileProvider.class)) {
        PathFragment packageDir = src.getLabel().getPackageIdentifier().getPathFragment();
        for (Artifact a : src.getFilesToBuild()) {
          result.add(packageDir);
          // Attempt to gather subdirectories that might contain include files.
          result.add(a.getRootRelativePath().getParentDirectory());
        }
      }
    }

    // Add in any 'includes' attribute values as relative path fragments
    if (ruleContext.getRule().isAttributeValueExplicitlySpecified("includes")) {
      PathFragment packageFragment = ruleContext.getLabel().getPackageIdentifier()
          .getPathFragment();
      // For now, anything with an 'includes' needs a blanket declaration
      result.add(packageFragment.getRelative("**"));
    }
    return result;
  }

  List<PathFragment> getSystemIncludeDirs() {
    // Add in any 'includes' attribute values as relative path fragments
    if (!ruleContext.getRule().isAttributeValueExplicitlySpecified("includes")
        || !cppConfiguration.useIsystemForIncludes()) {
      return ImmutableList.of();
    }
    return getIncludeDirsFromIncludesAttribute();
  }

  List<PathFragment> getIncludeDirs() {
    if (!ruleContext.getRule().isAttributeValueExplicitlySpecified("includes")
        || cppConfiguration.useIsystemForIncludes()) {
      return ImmutableList.of();
    }
    return getIncludeDirsFromIncludesAttribute();
  }

  private List<PathFragment> getIncludeDirsFromIncludesAttribute() {
    List<PathFragment> result = new ArrayList<>();
    PathFragment packageFragment = ruleContext.getLabel().getPackageIdentifier().getPathFragment();
    for (String includesAttr : ruleContext.attributes().get("includes", Type.STRING_LIST)) {
      includesAttr = ruleContext.expandMakeVariables("includes", includesAttr);
      if (includesAttr.startsWith("/")) {
        ruleContext.attributeWarning("includes",
            "ignoring invalid absolute path '" + includesAttr + "'");
        continue;
      }
      PathFragment includesPath = packageFragment.getRelative(includesAttr).normalize();
      if (!includesPath.isNormalized()) {
        ruleContext.attributeError("includes",
            "Path references a path above the execution root.");
      }
      result.add(includesPath);
      result.add(ruleContext.getConfiguration().getGenfilesFragment().getRelative(includesPath));
    }
    return result;
  }

  /**
   * Collects compilation prerequisite artifacts.
   */
  static NestedSet<Artifact> collectCompilationPrerequisites(
      RuleContext ruleContext, CppCompilationContext context) {
    // TODO(bazel-team): Use context.getCompilationPrerequisites() instead.
    NestedSetBuilder<Artifact> prerequisites = NestedSetBuilder.stableOrder();
    if (ruleContext.attributes().has("srcs", Type.LABEL_LIST)) {
      for (FileProvider provider : ruleContext
          .getPrerequisites("srcs", Mode.TARGET, FileProvider.class)) {
        prerequisites.addAll(FileType.filter(provider.getFilesToBuild(), SOURCE_TYPES));
      }
    }
    prerequisites.addTransitive(context.getDeclaredIncludeSrcs());
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
  public LibraryToLink getDynamicLibrarySymlink(Artifact library, boolean preserveName) {
    return SolibSymlinkAction.getDynamicLibrarySymlink(
        ruleContext, library, preserveName, true, ruleContext.getConfiguration());
  }

  /**
   * Returns any linker scripts found in the dependencies of the rule.
   */
  Iterable<Artifact> getLinkerScripts() {
    return FileType.filter(ruleContext.getPrerequisiteArtifacts("deps", Mode.TARGET).list(),
        CppFileTypes.LINKER_SCRIPT);
  }

  ImmutableList<Artifact> getFilesToCompile(CcCompilationOutputs compilationOutputs) {
    return cppConfiguration.isLipoContextCollector()
        ? ImmutableList.<Artifact>of()
        : compilationOutputs.getObjectFiles(CppHelper.usePic(ruleContext, false));
  }

  InstrumentedFilesProvider getInstrumentedFilesProvider(Iterable<Artifact> files) {
    return cppConfiguration.isLipoContextCollector()
        ? InstrumentedFilesProviderImpl.EMPTY
        : new InstrumentedFilesProviderImpl(new InstrumentedFilesCollector(
            ruleContext, CppRuleClasses.INSTRUMENTATION_SPEC, CC_METADATA_COLLECTOR, files));
  }

  /**
   * Creates the feature configuration for a given rule.
   * 
   * @param ruleContext the context of the rule we want the feature configuration for. 
   * @param ruleSpecificRequestedFeatures features that will be requested, and thus be always
   * enabled if the toolchain supports them.
   * @param ruleSpecificUnsupportedFeatures features that are not supported in the current context.
   * @return the feature configuration for the given {@code ruleContext}.
   */
  public static FeatureConfiguration configureFeatures(RuleContext ruleContext,
      Set<String> ruleSpecificRequestedFeatures,
      Set<String> ruleSpecificUnsupportedFeatures) {
    CcToolchainProvider toolchain = CppHelper.getToolchain(ruleContext);    
    ImmutableSet.Builder<String> unsupportedFeaturesBuilder = ImmutableSet.builder();
    unsupportedFeaturesBuilder.addAll(ruleSpecificUnsupportedFeatures);
    if (!toolchain.supportsHeaderParsing()) {
      // TODO(bazel-team): Remove once supports_header_parsing has been removed from the
      // cc_toolchain rule.
      unsupportedFeaturesBuilder.add(CppRuleClasses.PARSE_HEADERS);
      unsupportedFeaturesBuilder.add(CppRuleClasses.PREPROCESS_HEADERS);
    }
    if (toolchain.getCppCompilationContext().getCppModuleMap() == null) {
      unsupportedFeaturesBuilder.add(CppRuleClasses.MODULE_MAPS);
    }
    Set<String> unsupportedFeatures = unsupportedFeaturesBuilder.build();
    ImmutableSet.Builder<String> requestedFeatures = ImmutableSet.builder();
    for (String feature : Iterables.concat(
        ImmutableSet.of(toolchain.getCompilationMode().toString()), DEFAULT_FEATURES,
        ruleContext.getFeatures())) {
      if (!unsupportedFeatures.contains(feature)) {
        requestedFeatures.add(feature);
      }
    }
    requestedFeatures.addAll(ruleSpecificRequestedFeatures);

    // Enable FDO related features requested by options.
    CppConfiguration cppConfiguration =
        ruleContext.getConfiguration().getFragment(CppConfiguration.class);
    FdoSupport fdoSupport = cppConfiguration.getFdoSupport();
    if (fdoSupport.getFdoInstrument() != null) {
      requestedFeatures.add(CppRuleClasses.FDO_INSTRUMENT);
    }
    if (fdoSupport.getFdoOptimizeProfile() != null
        && !fdoSupport.isAutoFdoEnabled()) {
      requestedFeatures.add(CppRuleClasses.FDO_OPTIMIZE);
    }
    if (fdoSupport.isAutoFdoEnabled()) {
      requestedFeatures.add(CppRuleClasses.AUTOFDO);
    }
    if (cppConfiguration.isLipoOptimizationOrInstrumentation()) {
      requestedFeatures.add(CppRuleClasses.LIPO);
    }

    FeatureConfiguration configuration =
        toolchain.getFeatures().getFeatureConfiguration(requestedFeatures.build());
    for (String feature : unsupportedFeatures) {
      if (configuration.isEnabled(feature)) {
        ruleContext.ruleError("The C++ toolchain '"
            + ruleContext.getPrerequisite(":cc_toolchain", Mode.TARGET).getLabel()
            + "' unconditionally implies feature '" + feature
            + "', which is unsupported by this rule. "
            + "This is most likely a misconfiguration in the C++ toolchain.");
      }
    }
    return configuration; 
  }
  
  /**
   * Creates a feature configuration for a given rule.
   * 
   * @param ruleContext the context of the rule we want the feature configuration for. 
   * @return the feature configuration for the given {@code ruleContext}.
   */
  public static FeatureConfiguration configureFeatures(RuleContext ruleContext) {
    return configureFeatures(ruleContext, ImmutableSet.<String>of(), ImmutableSet.<String>of());
  }
}
