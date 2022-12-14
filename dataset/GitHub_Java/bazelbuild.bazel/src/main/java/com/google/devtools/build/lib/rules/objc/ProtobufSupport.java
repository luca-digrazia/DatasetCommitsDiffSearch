// Copyright 2016 The Bazel Authors. All rights reserved.
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

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.devtools.build.lib.rules.objc.XcodeProductType.LIBRARY_STATIC;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Ordering;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.actions.CustomCommandLine;
import com.google.devtools.build.lib.analysis.actions.FileWriteAction;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.LabelSyntaxException;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.packages.RuleClass.ConfiguredTargetFactory.RuleErrorException;
import com.google.devtools.build.lib.rules.proto.ProtoSourcesProvider;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Support for generating Objective C proto static libraries that registers actions which generate
 * and compile the Objective C protos by using the open source protobuf library and compiler.
 *
 * <p>Each group represents one proto_library target depended on by objc_proto_library targets using
 * the portable_proto_filters attribute. This group contains all the necessary protos to satisfy its
 * internal dependencies.
 *
 * <p>Grouping has a first pass in which for each proto required to be built, we find the smallest
 * group containing it, and store that information in a map. We then reverse that map into a multi
 * map, in which the keys are the input protos and the values are the output protos to be
 * generated/compiled with the input group as dependencies. This minimizes the number of inputs
 * required for each generation/compilation action and the probability of regeneration when one of
 * the proto files change, improving cache hits.
 */
final class ProtobufSupport {

  private static final PathFragment BAZEL_TOOLS_PREFIX = new PathFragment("external/bazel_tools/");

  private static final String BUNDLED_PROTOS_IDENTIFIER = "BundledProtos";

  /**
   * List of file name segments that should be upper cased when being generated. More information
   * available in the generateProtobufFilename() method.
   */
  private static final ImmutableSet<String> UPPERCASE_SEGMENTS =
      ImmutableSet.of("url", "http", "https");

  private static final String UNIQUE_DIRECTORY_NAME = "_generated_protos";

  private final RuleContext ruleContext;
  private final BuildConfiguration buildConfiguration;
  private final ProtoAttributes attributes;

  // Each entry of this map represents a generation action and a compilation action. The input set
  // are dependencies of the output set. The output set is always a subset of, or the same set as,
  // the input set. For example, given a sample entry of the inputsToOutputsMap like:
  //
  //    {A, B, C} => {B, C}
  //
  // this represents:
  // 1. A generation action in which the inputs are A, B and C, and the outputs are B.pbobjc.h,
  //    B.pbobjc.m, C.pbobjc.h and C.pbobjc.m.
  // 2. A compilation action in which the inputs are A.pbobjc.h, B.pbobjc.h, C.pbobjc.h,
  //    B.pbobjc.m and C.pbobjc.m, while the outputs are B.pbobjc.o and C.pbobjc.o.
  //
  // Given that each input set appears only once, by the nature of the structure, we can safely use
  // it as an identifier of the entry.
  private final ImmutableSetMultimap<ImmutableSet<Artifact>, Artifact> inputsToOutputsMap;

  /**
   * Creates a new proto support for the protobuf library. This support code bundles up all the
   * transitive protos within the groups in which they were defined. We use that information to
   * minimize the number of inputs per generation/compilation actions by only providing what is
   * really needed to the actions.
   *
   * @param ruleContext context this proto library is constructed in
   */
  public ProtobufSupport(RuleContext ruleContext) throws RuleErrorException {
    this(ruleContext, null);
  }

  /**
   * Creates a new proto support for the protobuf library. This support code bundles up all the
   * transitive protos within the groups in which they were defined. We use that information to
   * minimize the number of inputs per generation/compilation actions by only providing what is
   * really needed to the actions.
   *
   * @param ruleContext context this proto library is constructed in
   * @param buildConfiguration the configuration from which to get prerequisites when building proto
   *     targets in a split configuration
   */
  public ProtobufSupport(RuleContext ruleContext, BuildConfiguration buildConfiguration)
      throws RuleErrorException {
    this.ruleContext = ruleContext;
    this.buildConfiguration = buildConfiguration;
    this.attributes = new ProtoAttributes(ruleContext);
    this.inputsToOutputsMap = getInputsToOutputsMap();
  }

  /**
   * Registers the proto generation actions. These actions generate the ObjC/CPP code to be compiled
   * by this rule.
   */
  public ProtobufSupport registerGenerationActions() {
    int actionId = 0;
    for (ImmutableSet<Artifact> inputProtos : inputsToOutputsMap.keySet()) {
      Iterable<Artifact> outputProtos = inputsToOutputsMap.get(inputProtos);

      registerGenerationAction(outputProtos, inputProtos, getUniqueBundledProtosSuffix(actionId));

      IntermediateArtifacts intermediateArtifacts = getUniqueIntermediateArtifacts(actionId);

      CompilationArtifacts compilationArtifacts =
          getCompilationArtifacts(intermediateArtifacts, inputProtos, outputProtos);

      ObjcCommon common = getCommon(intermediateArtifacts, compilationArtifacts);

      new CompilationSupport(
              ruleContext, intermediateArtifacts, new CompilationAttributes.Builder().build())
          .registerGenerateModuleMapAction(common.getCompilationArtifacts());
      actionId++;
    }
    return this;
  }

  /** Registers the actions that will compile the generated code. */
  public ProtobufSupport registerCompilationActions() {
    int actionId = 0;
    Iterable<PathFragment> userHeaderSearchPaths =
        ImmutableList.of(getWorkspaceRelativeOutputDir());
    for (ImmutableSet<Artifact> inputProtos : inputsToOutputsMap.keySet()) {
      ImmutableSet<Artifact> outputProtos = inputsToOutputsMap.get(inputProtos);

      IntermediateArtifacts intermediateArtifacts = getUniqueIntermediateArtifacts(actionId);

      CompilationArtifacts compilationArtifacts =
          getCompilationArtifacts(intermediateArtifacts, inputProtos, outputProtos);

      ObjcCommon common = getCommon(intermediateArtifacts, compilationArtifacts);

      new CompilationSupport(
              ruleContext, intermediateArtifacts, new CompilationAttributes.Builder().build())
          .registerCompileAndArchiveActions(common, userHeaderSearchPaths);

      actionId++;
    }
    return this;
  }

  /** Adds the generated files to the set of files to be output when this rule is built. */
  public ProtobufSupport addFilesToBuild(NestedSetBuilder<Artifact> filesToBuild) {
    for (ImmutableSet<Artifact> inputProtoFiles : inputsToOutputsMap.keySet()) {
      ImmutableSet<Artifact> outputProtoFiles = inputsToOutputsMap.get(inputProtoFiles);
      Iterable<Artifact> generatedSources = getGeneratedProtoOutputs(outputProtoFiles, ".pbobjc.m");
      Iterable<Artifact> generatedHeaders = getGeneratedProtoOutputs(outputProtoFiles, ".pbobjc.h");

      filesToBuild.addAll(generatedSources).addAll(generatedHeaders);
    }

    int actionId = 0;
    for (ImmutableSet<Artifact> inputProtos : inputsToOutputsMap.keySet()) {
      ImmutableSet<Artifact> outputProtos = inputsToOutputsMap.get(inputProtos);
      IntermediateArtifacts intermediateArtifacts = getUniqueIntermediateArtifacts(actionId);

      CompilationArtifacts compilationArtifacts =
          getCompilationArtifacts(intermediateArtifacts, inputProtos, outputProtos);

      ObjcCommon common = getCommon(intermediateArtifacts, compilationArtifacts);
      filesToBuild.addAll(common.getCompiledArchive().asSet());
      actionId++;
    }

    return this;
  }

  /**
   * Returns the ObjcProvider for this target, or Optional.absent() if there were no protos to
   * generate.
   */
  public Optional<ObjcProvider> getObjcProvider() {
    if (inputsToOutputsMap.isEmpty()) {
      return Optional.absent();
    }

    Iterable<PathFragment> userHeaderSearchPaths =
        ImmutableList.of(getWorkspaceRelativeOutputDir());
    ObjcCommon.Builder commonBuilder = new ObjcCommon.Builder(ruleContext);

    int actionId = 0;
    for (ImmutableSet<Artifact> inputProtos : inputsToOutputsMap.keySet()) {
      ImmutableSet<Artifact> outputProtos = inputsToOutputsMap.get(inputProtos);
      IntermediateArtifacts intermediateArtifacts = getUniqueIntermediateArtifacts(actionId);

      CompilationArtifacts compilationArtifacts =
          getCompilationArtifacts(intermediateArtifacts, inputProtos, outputProtos);

      ObjcCommon common = getCommon(intermediateArtifacts, compilationArtifacts);
      commonBuilder.addDepObjcProviders(ImmutableSet.of(common.getObjcProvider()));
      actionId++;
    }

    commonBuilder.addDirectDependencyHeaderSearchPaths(userHeaderSearchPaths);
    return Optional.of(commonBuilder.build().getObjcProvider());
  }

  /**
   * Returns the XcodeProvider for this target or Optional.absent() if there were no protos to
   * generate.
   */
  public Optional<XcodeProvider> getXcodeProvider() throws RuleErrorException {
    if (inputsToOutputsMap.isEmpty()) {
      return Optional.absent();
    }

    XcodeProvider.Builder xcodeProviderBuilder = new XcodeProvider.Builder();
    IntermediateArtifacts intermediateArtifacts =
        ObjcRuleClasses.intermediateArtifacts(ruleContext);
    new XcodeSupport(ruleContext, intermediateArtifacts, getXcodeLabel(getBundledProtosSuffix()))
        .addXcodeSettings(xcodeProviderBuilder, getObjcProvider().get(), LIBRARY_STATIC);

    int actionId = 0;
    for (ImmutableSet<Artifact> inputProtos : inputsToOutputsMap.keySet()) {
      ImmutableSet<Artifact> outputProtos = inputsToOutputsMap.get(inputProtos);
      IntermediateArtifacts bundleIntermediateArtifacts = getUniqueIntermediateArtifacts(actionId);

      CompilationArtifacts compilationArtifacts =
          getCompilationArtifacts(bundleIntermediateArtifacts, inputProtos, outputProtos);

      ObjcCommon common = getCommon(bundleIntermediateArtifacts, compilationArtifacts);

      XcodeProvider bundleProvider =
          getBundleXcodeProvider(
              common, bundleIntermediateArtifacts, getUniqueBundledProtosSuffix(actionId));
      xcodeProviderBuilder.addPropagatedDependencies(ImmutableSet.of(bundleProvider));
      actionId++;
    }

    return Optional.of(xcodeProviderBuilder.build());
  }

  private NestedSet<Artifact> getPortableProtoFilters() {
    Iterable<ObjcProtoProvider> objcProtoProviders = getObjcProtoProviders();

    NestedSetBuilder<Artifact> portableProtoFilters = NestedSetBuilder.stableOrder();
    for (ObjcProtoProvider objcProtoProvider : objcProtoProviders) {
      portableProtoFilters.addTransitive(objcProtoProvider.getPortableProtoFilters());
    }
    portableProtoFilters.addAll(attributes.getPortableProtoFilters());
    return portableProtoFilters.build();
  }

  private NestedSet<Artifact> getProtobufHeaders() {
    Iterable<ObjcProtoProvider> objcProtoProviders = getObjcProtoProviders();

    NestedSetBuilder<Artifact> protobufHeaders = NestedSetBuilder.stableOrder();
    for (ObjcProtoProvider objcProtoProvider : objcProtoProviders) {
      protobufHeaders.addTransitive(objcProtoProvider.getProtobufHeaders());
    }
    return protobufHeaders.build();
  }

  private NestedSet<PathFragment> getProtobufHeaderSearchPaths() {
    Iterable<ObjcProtoProvider> objcProtoProviders = getObjcProtoProviders();

    NestedSetBuilder<PathFragment> protobufHeaderSearchPaths = NestedSetBuilder.stableOrder();
    for (ObjcProtoProvider objcProtoProvider : objcProtoProviders) {
      protobufHeaderSearchPaths.addTransitive(objcProtoProvider.getProtobufHeaderSearchPaths());
    }
    return protobufHeaderSearchPaths.build();
  }

  private ImmutableSetMultimap<ImmutableSet<Artifact>, Artifact> getInputsToOutputsMap()
      throws RuleErrorException {
    Iterable<ObjcProtoProvider> objcProtoProviders = getObjcProtoProviders();
    Iterable<ProtoSourcesProvider> protoProviders = getProtoSourcesProviders();

    ImmutableList.Builder<NestedSet<Artifact>> protoSets =
        new ImmutableList.Builder<NestedSet<Artifact>>();

    // Traverse all the dependencies ObjcProtoProviders and ProtoSourcesProviders to aggregate
    // all the transitive groups of proto.
    for (ObjcProtoProvider objcProtoProvider : objcProtoProviders) {
      protoSets.addAll(objcProtoProvider.getProtoGroups());
    }
    for (ProtoSourcesProvider protoProvider : protoProviders) {
      protoSets.add(protoProvider.getTransitiveProtoSources());
    }

    HashMap<Artifact, ImmutableSet<Artifact>> protoToGroupMap =
        new HashMap<Artifact, ImmutableSet<Artifact>>();

    // For each proto in each proto group, store the smallest group in which it is contained. This
    // group will be considered the smallest input group with which the proto can be generated.
    for (NestedSet<Artifact> nestedProtoSet : protoSets.build()) {
      ImmutableSet<Artifact> protoSet = ImmutableSet.copyOf(nestedProtoSet.toSet());
      for (Artifact proto : protoSet) {
        // If the proto is well known, don't store it as we don't need to generate it; it comes
        // generated with the runtime library.
        if (isProtoWellKnown(proto)) {
          continue;
        }
        if (!protoToGroupMap.containsKey(proto)
            || protoToGroupMap.get(proto).size() > protoSet.size()) {
          protoToGroupMap.put(proto, protoSet);
        }
      }
    }

    // Now that we have the smallest proto inputs groups for each proto to be generated, we reverse
    // that map into a multimap to take advantage of the fact that multiple protos can be generated
    // with the same inputs, to avoid having multiple generation actions with the same inputs and
    // different ouputs. This only applies for the generation actions, as the compilation actions
    // compile one generated file at a time.
    // It's OK to use ImmutableSet<Artifact> as the key, since Artifact caches it's hashCode, and
    // ImmutableSet calculates it's hashCode in O(n).
    ImmutableSetMultimap.Builder<ImmutableSet<Artifact>, Artifact> inputsToOutputsMapBuilder =
        ImmutableSetMultimap.builder();

    for (Artifact proto : protoToGroupMap.keySet()) {
      inputsToOutputsMapBuilder.put(protoToGroupMap.get(proto), proto);
    }
    return inputsToOutputsMapBuilder.build();
  }

  private XcodeProvider getBundleXcodeProvider(
      ObjcCommon common, IntermediateArtifacts intermediateArtifacts, String labelSuffix)
      throws RuleErrorException {
    Iterable<PathFragment> userHeaderSearchPaths =
        ImmutableList.of(getWorkspaceRelativeOutputDir());

    XcodeProvider.Builder xcodeProviderBuilder =
        new XcodeProvider.Builder()
            .addUserHeaderSearchPaths(userHeaderSearchPaths)
            .setCompilationArtifacts(common.getCompilationArtifacts().get());

    XcodeSupport xcodeSupport =
        new XcodeSupport(ruleContext, intermediateArtifacts, getXcodeLabel(labelSuffix))
            .addXcodeSettings(xcodeProviderBuilder, common.getObjcProvider(), LIBRARY_STATIC);
    if (isLinkingTarget()) {
      xcodeProviderBuilder
          .addHeaders(getProtobufHeaders())
          .addUserHeaderSearchPaths(getProtobufHeaderSearchPaths());
    } else {
      xcodeSupport.addDependencies(
          xcodeProviderBuilder, new Attribute(ObjcRuleClasses.PROTO_LIB_ATTR, Mode.TARGET));
    }

    return xcodeProviderBuilder.build();
  }

  private String getBundledProtosSuffix() {
    return "_" + BUNDLED_PROTOS_IDENTIFIER;
  }

  private String getUniqueBundledProtosPrefix(int actionId) {
    return BUNDLED_PROTOS_IDENTIFIER + "_" + actionId;
  }

  private String getUniqueBundledProtosSuffix(int actionId) {
    return getBundledProtosSuffix() + "_" + actionId;
  }

  private Label getXcodeLabel(String suffix) throws RuleErrorException {
    Label xcodeLabel = null;
    try {
      xcodeLabel =
          ruleContext.getLabel().getLocalTargetLabel(ruleContext.getLabel().getName() + suffix);
    } catch (LabelSyntaxException e) {
      ruleContext.throwWithRuleError(e.getLocalizedMessage());
    }
    return xcodeLabel;
  }

  private IntermediateArtifacts getUniqueIntermediateArtifacts(int actionId) {
    return new IntermediateArtifacts(
        ruleContext,
        getUniqueBundledProtosSuffix(actionId),
        getUniqueBundledProtosPrefix(actionId),
        ruleContext.getConfiguration());
  }

  private ObjcCommon getCommon(
      IntermediateArtifacts intermediateArtifacts, CompilationArtifacts compilationArtifacts) {
    ObjcCommon.Builder commonBuilder =
        new ObjcCommon.Builder(ruleContext)
            .setIntermediateArtifacts(intermediateArtifacts)
            .setHasModuleMap()
            .setCompilationArtifacts(compilationArtifacts);
    if (isLinkingTarget()) {
      commonBuilder.addUserHeaderSearchPaths(getProtobufHeaderSearchPaths());
    } else {
      commonBuilder.addDepObjcProviders(
          ruleContext.getPrerequisites(
              ObjcRuleClasses.PROTO_LIB_ATTR, Mode.TARGET, ObjcProvider.class));
    }
    return commonBuilder.build();
  }

  private CompilationArtifacts getCompilationArtifacts(
      IntermediateArtifacts intermediateArtifacts,
      Iterable<Artifact> inputProtoFiles,
      Iterable<Artifact> outputProtoFiles) {
    // Filter the well known protos from the set of headers. We don't generate the headers for them
    // as they are part of the runtime library.
    Iterable<Artifact> filteredInputProtos = filterWellKnownProtos(inputProtoFiles);

    CompilationArtifacts.Builder compilationArtifacts =
        new CompilationArtifacts.Builder()
            .setIntermediateArtifacts(intermediateArtifacts)
            .setPchFile(Optional.<Artifact>absent())
            .addAdditionalHdrs(getGeneratedProtoOutputs(filteredInputProtos, ".pbobjc.h"))
            .addAdditionalHdrs(getProtobufHeaders());

    if (isLinkingTarget()) {
      compilationArtifacts.addNonArcSrcs(getGeneratedProtoOutputs(outputProtoFiles, ".pbobjc.m"));
    }

    return compilationArtifacts.build();
  }

  private void registerGenerationAction(
      Iterable<Artifact> outputProtos, Iterable<Artifact> inputProtos, String protoFileSuffix) {
    Artifact protoInputsFile = getProtoInputsFile(protoFileSuffix);

    ruleContext.registerAction(
        new FileWriteAction(
            ruleContext.getActionOwner(),
            protoInputsFile,
            getProtoInputsFileContents(outputProtos),
            false));

    ruleContext.registerAction(
        ObjcRuleClasses.spawnOnDarwinActionBuilder()
            .setMnemonic("GenObjcBundledProtos")
            .addInput(attributes.getProtoCompiler())
            .addInputs(attributes.getProtoCompilerSupport())
            .addTransitiveInputs(getPortableProtoFilters())
            .addInput(protoInputsFile)
            .addInputs(inputProtos)
            .addOutputs(getGeneratedProtoOutputs(outputProtos, ".pbobjc.h"))
            .addOutputs(getGeneratedProtoOutputs(outputProtos, ".pbobjc.m"))
            .setExecutable(new PathFragment("/usr/bin/python"))
            .setCommandLine(getGenerationCommandLine(protoInputsFile))
            .build(ruleContext));
  }

  private Artifact getProtoInputsFile(String suffix) {
    return ruleContext.getUniqueDirectoryArtifact(
        "_protos",
        "_proto_input_files" + suffix,
        ruleContext.getConfiguration().getGenfilesDirectory());
  }

  private String getProtoInputsFileContents(Iterable<Artifact> outputProtos) {
    // Sort the file names to make the remote action key independent of the precise deps structure.
    // compile_protos.py will sort the input list anyway.
    Iterable<Artifact> sorted = Ordering.natural().immutableSortedCopy(outputProtos);
    return Artifact.joinExecPaths("\n", sorted);
  }

  private CustomCommandLine getGenerationCommandLine(Artifact protoInputsFile) {
    return new CustomCommandLine.Builder()
        .add(attributes.getProtoCompiler().getExecPathString())
        .add("--input-file-list")
        .add(protoInputsFile.getExecPathString())
        .add("--output-dir")
        .add(getWorkspaceRelativeOutputDir().getSafePathString())
        .add("--force")
        .add("--proto-root-dir")
        .add(".")
        .addBeforeEachExecPath("--config", getPortableProtoFilters())
        .build();
  }

  private PathFragment getWorkspaceRelativeOutputDir() {
    // Generate sources in a package-and-rule-scoped directory; adds both the
    // package-and-rule-scoped directory and the header-containing-directory to the include path
    // of dependers.
    PathFragment rootRelativeOutputDir = ruleContext.getUniqueDirectory(UNIQUE_DIRECTORY_NAME);

    return new PathFragment(
        ruleContext.getBinOrGenfilesDirectory().getExecPath(), rootRelativeOutputDir);
  }

  private Iterable<Artifact> getGeneratedProtoOutputs(
      Iterable<Artifact> outputProtos, String extension) {
    ImmutableList.Builder<Artifact> builder = new ImmutableList.Builder<>();
    for (Artifact protoFile : outputProtos) {
      String protoFileName = FileSystemUtils.removeExtension(protoFile.getFilename());
      String generatedOutputName = getProtobufFilename(protoFileName);

      PathFragment generatedFilePath =
          new PathFragment(
              protoFile.getRootRelativePath().getParentDirectory(),
              new PathFragment(generatedOutputName));

      PathFragment outputFile = FileSystemUtils.appendExtension(generatedFilePath, extension);

      if (outputFile != null) {
        builder.add(
            ruleContext.getUniqueDirectoryArtifact(
                UNIQUE_DIRECTORY_NAME, outputFile, ruleContext.getBinOrGenfilesDirectory()));
      }
    }
    return builder.build();
  }

  private Iterable<ObjcProtoProvider> getObjcProtoProviders() {
    if (buildConfiguration != null) {
      return ruleContext
          .getPrerequisitesByConfiguration("deps", Mode.SPLIT, ObjcProtoProvider.class)
          .get(buildConfiguration);
    }
    return ruleContext.getPrerequisites("deps", Mode.TARGET, ObjcProtoProvider.class);
  }

  private Iterable<ProtoSourcesProvider> getProtoSourcesProviders() {
    if (buildConfiguration != null) {
      return ruleContext
          .getPrerequisitesByConfiguration("deps", Mode.SPLIT, ProtoSourcesProvider.class)
          .get(buildConfiguration);
    }
    return ruleContext.getPrerequisites("deps", Mode.TARGET, ProtoSourcesProvider.class);
  }

  /**
   * Processes the case of the proto file name in the same fashion as the objective_c generator's
   * UnderscoresToCamelCase function.
   *
   * <p>https://github.com/google/protobuf/blob/master/src/google/protobuf/compiler/objectivec/objectivec_helpers.cc
   */
  private String getProtobufFilename(String protoFilename) {
    boolean lastCharWasDigit = false;
    boolean lastCharWasUpper = false;
    boolean lastCharWasLower = false;

    StringBuilder currentSegment = new StringBuilder();

    ArrayList<String> segments = new ArrayList<>();

    for (int i = 0; i < protoFilename.length(); i++) {
      char currentChar = protoFilename.charAt(i);
      if (CharMatcher.javaDigit().matches(currentChar)) {
        if (!lastCharWasDigit) {
          segments.add(currentSegment.toString());
          currentSegment = new StringBuilder();
        }
        currentSegment.append(currentChar);
        lastCharWasDigit = true;
        lastCharWasUpper = false;
        lastCharWasLower = false;
      } else if (CharMatcher.javaLowerCase().matches(currentChar)) {
        if (!lastCharWasLower && !lastCharWasUpper) {
          segments.add(currentSegment.toString());
          currentSegment = new StringBuilder();
        }
        currentSegment.append(currentChar);
        lastCharWasDigit = false;
        lastCharWasUpper = false;
        lastCharWasLower = true;
      } else if (CharMatcher.javaUpperCase().matches(currentChar)) {
        if (!lastCharWasUpper) {
          segments.add(currentSegment.toString());
          currentSegment = new StringBuilder();
        }
        currentSegment.append(Character.toLowerCase(currentChar));
        lastCharWasDigit = false;
        lastCharWasUpper = true;
        lastCharWasLower = false;
      } else {
        lastCharWasDigit = false;
        lastCharWasUpper = false;
        lastCharWasLower = false;
      }
    }

    segments.add(currentSegment.toString());

    StringBuilder casedSegments = new StringBuilder();
    for (String segment : segments) {
      if (UPPERCASE_SEGMENTS.contains(segment)) {
        casedSegments.append(segment.toUpperCase());
      } else {
        casedSegments.append(LOWER_UNDERSCORE.to(UPPER_CAMEL, segment));
      }
    }
    return casedSegments.toString();
  }

  private ImmutableSet<Artifact> filterWellKnownProtos(Iterable<Artifact> protoFiles) {
    // Since well known protos are already linked in the runtime library, we have to filter them
    // so they don't get generated again.
    ImmutableSet.Builder<Artifact> filteredProtos = new ImmutableSet.Builder<Artifact>();
    for (Artifact protoFile : protoFiles) {
      if (!isProtoWellKnown(protoFile)) {
        filteredProtos.add(protoFile);
      }
    }
    return filteredProtos.build();
  }

  private ImmutableSet<PathFragment> getWellKnownProtoPaths(RuleContext ruleContext) {
    ImmutableSet.Builder<PathFragment> wellKnownProtoPathsBuilder = new ImmutableSet.Builder<>();
    Iterable<Artifact> wellKnownProtoFiles =
        ruleContext
            .getPrerequisiteArtifacts(ObjcRuleClasses.PROTOBUF_WELL_KNOWN_TYPES, Mode.HOST)
            .list();
    for (Artifact wellKnownProtoFile : wellKnownProtoFiles) {
      PathFragment execPath = wellKnownProtoFile.getExecPath();
      if (execPath.startsWith(BAZEL_TOOLS_PREFIX)) {
        wellKnownProtoPathsBuilder.add(execPath.relativeTo(BAZEL_TOOLS_PREFIX));
      } else {
        wellKnownProtoPathsBuilder.add(execPath);
      }
    }
    return wellKnownProtoPathsBuilder.build();
  }

  private boolean isProtoWellKnown(Artifact protoFile) {
    return getWellKnownProtoPaths(ruleContext).contains(protoFile.getExecPath());
  }

  private boolean isLinkingTarget() {
    return !ruleContext
        .attributes()
        .isAttributeValueExplicitlySpecified(ObjcProtoLibraryRule.PORTABLE_PROTO_FILTERS_ATTR);
  }
}
