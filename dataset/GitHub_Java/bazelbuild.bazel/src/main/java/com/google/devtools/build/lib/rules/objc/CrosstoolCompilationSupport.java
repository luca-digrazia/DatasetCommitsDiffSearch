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

import static com.google.devtools.build.lib.rules.objc.ObjcProvider.DEFINE;
import static com.google.devtools.build.lib.rules.objc.ObjcProvider.DYNAMIC_FRAMEWORK_FILE;
import static com.google.devtools.build.lib.rules.objc.ObjcProvider.HEADER;
import static com.google.devtools.build.lib.rules.objc.ObjcProvider.IMPORTED_LIBRARY;
import static com.google.devtools.build.lib.rules.objc.ObjcProvider.INCLUDE;
import static com.google.devtools.build.lib.rules.objc.ObjcProvider.INCLUDE_SYSTEM;
import static com.google.devtools.build.lib.rules.objc.ObjcProvider.STATIC_FRAMEWORK_FILE;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.ActionAnalysisMetadata;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.AnalysisEnvironment;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.packages.RuleClass.ConfiguredTargetFactory.RuleErrorException;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;
import com.google.devtools.build.lib.rules.cpp.CcLibraryHelper;
import com.google.devtools.build.lib.rules.cpp.CcLibraryHelper.Info;
import com.google.devtools.build.lib.rules.cpp.CcToolchainFeatures.FeatureConfiguration;
import com.google.devtools.build.lib.rules.cpp.CcToolchainFeatures.Variables.VariablesExtension;
import com.google.devtools.build.lib.rules.cpp.CcToolchainProvider;
import com.google.devtools.build.lib.rules.cpp.CppCompileAction;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration;
import com.google.devtools.build.lib.rules.cpp.CppHelper;
import com.google.devtools.build.lib.rules.cpp.CppLinkAction;
import com.google.devtools.build.lib.rules.cpp.CppLinkActionBuilder;
import com.google.devtools.build.lib.rules.cpp.CppRuleClasses;
import com.google.devtools.build.lib.rules.cpp.FdoSupportProvider;
import com.google.devtools.build.lib.rules.cpp.IncludeProcessing;
import com.google.devtools.build.lib.rules.cpp.Link.LinkStaticness;
import com.google.devtools.build.lib.rules.cpp.Link.LinkTargetType;
import com.google.devtools.build.lib.rules.cpp.NoProcessing;
import com.google.devtools.build.lib.rules.cpp.PrecompiledFiles;
import com.google.devtools.build.lib.rules.objc.ObjcProvider.Flag;
import com.google.devtools.build.lib.rules.objc.ObjcVariablesExtension.VariableCategory;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * Constructs command lines for objc compilation, archiving, and linking. Uses the crosstool
 * infrastructure to register {@link CppCompileAction} and {@link CppLinkAction} instances, making
 * use of a provided toolchain.
 *
 * <p>TODO(b/28403953): Deprecate LegacyCompilationSupport in favor of this implementation for all
 * objc rules.
 */
public class CrosstoolCompilationSupport extends CompilationSupport {

  private static final String OBJC_MODULE_FEATURE_NAME = "use_objc_modules";
  private static final String NO_ENABLE_MODULES_FEATURE_NAME = "no_enable_modules";
  private static final String DEAD_STRIP_FEATURE_NAME = "dead_strip";
  private static final String RUN_COVERAGE_FEATURE_NAME = "run_coverage";
  /** Produce artifacts for coverage in llvm coverage mapping format. */
  private static final String LLVM_COVERAGE_MAP_FORMAT = "llvm_coverage_map_format";
  /** Produce artifacts for coverage in gcc coverage mapping format. */
  private static final String GCC_COVERAGE_MAP_FORMAT = "gcc_coverage_map_format";

  private static final Iterable<String> ACTIVATED_ACTIONS =
      ImmutableList.of(
          "objc-compile",
          "objc++-compile",
          "objc-archive",
          "objc-fully-link",
          "objc-executable",
          "objc++-executable",
          "assemble",
          "preprocess-assemble",
          "c-compile",
          "c++-compile");

  /**
   * Creates a new CompilationSupport instance that uses the c++ rule backend
   *
   * @param ruleContext the RuleContext for the calling target
   */
  public CrosstoolCompilationSupport(RuleContext ruleContext) {
    this(
        ruleContext,
        ruleContext.getConfiguration(),
        ObjcRuleClasses.intermediateArtifacts(ruleContext),
        CompilationAttributes.Builder.fromRuleContext(ruleContext).build());
  }

  /**
   * Creates a new CompilationSupport instance that uses the c++ rule backend
   *
   * @param ruleContext the RuleContext for the calling target
   * @param buildConfiguration the configuration for the calling target
   * @param intermediateArtifacts IntermediateArtifacts for deriving artifact paths
   * @param compilationAttributes attributes of the calling target
   */
  public CrosstoolCompilationSupport(RuleContext ruleContext,
      BuildConfiguration buildConfiguration,
      IntermediateArtifacts intermediateArtifacts,
      CompilationAttributes compilationAttributes) {
    super(ruleContext, buildConfiguration, intermediateArtifacts, compilationAttributes);
  }

  @Override
  CompilationSupport registerCompileAndArchiveActions(
      CompilationArtifacts compilationArtifacts,
      ObjcProvider objcProvider, ExtraCompileArgs extraCompileArgs,
      Iterable<PathFragment> priorityHeaders,
      @Nullable CcToolchainProvider ccToolchain,
      @Nullable FdoSupportProvider fdoSupport) throws RuleErrorException, InterruptedException {
    Preconditions.checkNotNull(ccToolchain);
    Preconditions.checkNotNull(fdoSupport);
    ObjcVariablesExtension.Builder extension = new ObjcVariablesExtension.Builder()
        .setRuleContext(ruleContext)
        .setObjcProvider(objcProvider)
        .setCompilationArtifacts(compilationArtifacts)
        .setIntermediateArtifacts(intermediateArtifacts)
        .setConfiguration(ruleContext.getConfiguration());
    CcLibraryHelper helper;

    if (compilationArtifacts.getArchive().isPresent()) {
      Artifact objList = intermediateArtifacts.archiveObjList();

      // TODO(b/30783125): Signal the need for this action in the CROSSTOOL.
      registerObjFilelistAction(getObjFiles(compilationArtifacts, intermediateArtifacts), objList);
  
      extension.addVariableCategory(VariableCategory.ARCHIVE_VARIABLES);
      
      helper =
          createCcLibraryHelper(
                  objcProvider, compilationArtifacts, extension.build(), ccToolchain, fdoSupport)
              .setLinkType(LinkTargetType.OBJC_ARCHIVE)
              .addLinkActionInput(objList);
    } else {
      helper =
          createCcLibraryHelper(
              objcProvider, compilationArtifacts, extension.build(), ccToolchain, fdoSupport);
    }

    registerHeaderScanningActions(helper.build(), objcProvider, compilationArtifacts);

    return this;
  }

  @Override
  protected CompilationSupport registerFullyLinkAction(
      ObjcProvider objcProvider, Iterable<Artifact> inputArtifacts, Artifact outputArchive,
      @Nullable CcToolchainProvider ccToolchain, @Nullable FdoSupportProvider fdoSupport)
      throws InterruptedException {
    Preconditions.checkNotNull(ccToolchain);
    Preconditions.checkNotNull(fdoSupport);
    PathFragment labelName = new PathFragment(ruleContext.getLabel().getName());
    String libraryIdentifier =
        ruleContext
            .getPackageDirectory()
            .getRelative(labelName.replaceName("lib" + labelName.getBaseName()))
            .getPathString();
    ObjcVariablesExtension extension = new ObjcVariablesExtension.Builder()
        .setRuleContext(ruleContext)
        .setObjcProvider(objcProvider)
        .setConfiguration(ruleContext.getConfiguration())
        .setIntermediateArtifacts(intermediateArtifacts)
        .setFullyLinkArchive(outputArchive)
        .addVariableCategory(VariableCategory.FULLY_LINK_VARIABLES)
        .build();
    CppLinkAction fullyLinkAction =
        new CppLinkActionBuilder(ruleContext, outputArchive, ccToolchain, fdoSupport)
            .addActionInputs(objcProvider.getObjcLibraries())
            .addActionInputs(objcProvider.getCcLibraries())
            .addActionInputs(objcProvider.get(IMPORTED_LIBRARY).toSet())
            .setCrosstoolInputs(ccToolchain.getLink())
            .setLinkType(LinkTargetType.OBJC_FULLY_LINKED_ARCHIVE)
            .setLinkStaticness(LinkStaticness.FULLY_STATIC)
            .setLibraryIdentifier(libraryIdentifier)
            .addVariablesExtension(extension)
            .setFeatureConfiguration(getFeatureConfiguration(ruleContext, buildConfiguration))
            .build();
    ruleContext.registerAction(fullyLinkAction);

    return this;
  }

  @Override
  CompilationSupport registerLinkActions(
      ObjcProvider objcProvider,
      J2ObjcMappingFileProvider j2ObjcMappingFileProvider,
      J2ObjcEntryClassProvider j2ObjcEntryClassProvider,
      ExtraLinkArgs extraLinkArgs,
      Iterable<Artifact> extraLinkInputs,
      DsymOutputType dsymOutputType) throws InterruptedException {
    Iterable<Artifact> prunedJ2ObjcArchives =
        computeAndStripPrunedJ2ObjcArchives(
            j2ObjcEntryClassProvider, j2ObjcMappingFileProvider, objcProvider);
    ImmutableList<Artifact> bazelBuiltLibraries =
        Iterables.isEmpty(prunedJ2ObjcArchives)
            ? objcProvider.getObjcLibraries()
            : substituteJ2ObjcPrunedLibraries(objcProvider);

    Artifact inputFileList = intermediateArtifacts.linkerObjList();
    ImmutableSet<Artifact> forceLinkArtifacts = getForceLoadArtifacts(objcProvider);

    Iterable<Artifact> objFiles =
        Iterables.concat(
            bazelBuiltLibraries, objcProvider.get(IMPORTED_LIBRARY), objcProvider.getCcLibraries());
    // Clang loads archives specified in filelists and also specified as -force_load twice,
    // resulting in duplicate symbol errors unless they are deduped.
    objFiles = Iterables.filter(objFiles, Predicates.not(Predicates.in(forceLinkArtifacts)));

    registerObjFilelistAction(objFiles, inputFileList);

    LinkTargetType linkType = (objcProvider.is(Flag.USES_CPP))
        ? LinkTargetType.OBJCPP_EXECUTABLE
        : LinkTargetType.OBJC_EXECUTABLE;
    
    ObjcVariablesExtension extension = new ObjcVariablesExtension.Builder()
        .setRuleContext(ruleContext)
        .setObjcProvider(objcProvider)
        .setConfiguration(ruleContext.getConfiguration())
        .setIntermediateArtifacts(intermediateArtifacts)
        .setFrameworkNames(frameworkNames(objcProvider))
        .setLibraryNames(libraryNames(objcProvider))
        .setForceLoadArtifacts(getForceLoadArtifacts(objcProvider))
        .setAttributeLinkopts(attributes.linkopts())
        .addVariableCategory(VariableCategory.EXECUTABLE_LINKING_VARIABLES)
        .build();
   
    Artifact binaryToLink = getBinaryToLink();
    CcToolchainProvider ccToolchain = CppHelper.getToolchain(ruleContext, ":cc_toolchain");
    FdoSupportProvider fdoSupport = CppHelper.getFdoSupport(ruleContext, ":cc_toolchain");
    CppLinkAction executableLinkAction =
        new CppLinkActionBuilder(ruleContext, binaryToLink, ccToolchain, fdoSupport)
            .setMnemonic("ObjcLink")
            .addActionInputs(bazelBuiltLibraries)
            .addActionInputs(objcProvider.getCcLibraries())
            .addTransitiveActionInputs(objcProvider.get(IMPORTED_LIBRARY))
            .addTransitiveActionInputs(objcProvider.get(STATIC_FRAMEWORK_FILE))
            .addTransitiveActionInputs(objcProvider.get(DYNAMIC_FRAMEWORK_FILE))
            .setCrosstoolInputs(ccToolchain.getLink())
            .addActionInputs(prunedJ2ObjcArchives)
            .addActionInput(inputFileList)
            .setLinkType(linkType)
            .setLinkStaticness(LinkStaticness.FULLY_STATIC)
            .addLinkopts(ImmutableList.copyOf(extraLinkArgs))
            .addVariablesExtension(extension)
            .setFeatureConfiguration(getFeatureConfiguration(ruleContext, buildConfiguration))
            .build();
    ruleContext.registerAction(executableLinkAction);    

    if (objcConfiguration.shouldStripBinary()) {
      registerBinaryStripAction(binaryToLink, StrippingType.DEFAULT);
    }

    return this;
  }

  private IncludeProcessing createIncludeProcessing(
      Iterable<Artifact> potentialInputs, @Nullable Artifact pchHdr) {
    if (isHeaderThinningEnabled()) {
      if (pchHdr != null) {
        potentialInputs = Iterables.concat(potentialInputs, ImmutableList.of(pchHdr));
      }
      return new HeaderThinning(potentialInputs);
    } else {
      return new NoProcessing();
    }
  }

  private CcLibraryHelper createCcLibraryHelper(
      ObjcProvider objcProvider,
      CompilationArtifacts compilationArtifacts,
      VariablesExtension extension,
      CcToolchainProvider ccToolchain,
      FdoSupportProvider fdoSupport) {
    PrecompiledFiles precompiledFiles = new PrecompiledFiles(ruleContext);
    Collection<Artifact> arcSources = ImmutableSortedSet.copyOf(compilationArtifacts.getSrcs());
    Collection<Artifact> nonArcSources =
        ImmutableSortedSet.copyOf(compilationArtifacts.getNonArcSrcs());
    Collection<Artifact> privateHdrs =
        ImmutableSortedSet.copyOf(compilationArtifacts.getPrivateHdrs());
    Collection<Artifact> publicHdrs = ImmutableSortedSet.copyOf(attributes.hdrs());
    Artifact pchHdr = null;
    if (ruleContext.attributes().has("pch", BuildType.LABEL)) {
      pchHdr = ruleContext.getPrerequisiteArtifact("pch", Mode.TARGET);
    }
    ObjcCppSemantics semantics =
        new ObjcCppSemantics(
            objcProvider,
            createIncludeProcessing(
                Iterables.concat(privateHdrs, publicHdrs, objcProvider.get(HEADER)), pchHdr),
            ruleContext.getFragment(ObjcConfiguration.class),
            isHeaderThinningEnabled(),
            intermediateArtifacts);
    CcLibraryHelper result =
        new CcLibraryHelper(
                ruleContext,
                semantics,
                getFeatureConfiguration(ruleContext, buildConfiguration),
                CcLibraryHelper.SourceCategory.CC_AND_OBJC,
                ccToolchain,
                fdoSupport,
                buildConfiguration)
            .addSources(arcSources, ImmutableMap.of("objc_arc", ""))
            .addSources(nonArcSources, ImmutableMap.of("no_objc_arc", ""))
            .addSources(privateHdrs)
            .addDefines(objcProvider.get(DEFINE))
            .enableCompileProviders()
            .addPublicHeaders(publicHdrs)
            .addPrecompiledFiles(precompiledFiles)
            .addDeps(ruleContext.getPrerequisites("deps", Mode.TARGET))
            // Not all our dependencies need to export cpp information.
            // For example, objc_proto_library can depend on a proto_library rule that does not
            // generate C++ protos.
            .setCheckDepsGenerateCpp(false)
            .addCopts(getCompileRuleCopts())
            .addIncludeDirs(objcProvider.get(INCLUDE))
            .addCopts(ruleContext.getFragment(ObjcConfiguration.class).getCoptsForCompilationMode())
            .addSystemIncludeDirs(objcProvider.get(INCLUDE_SYSTEM))
            .setCppModuleMap(intermediateArtifacts.moduleMap())
            .setLinkedArtifactNameSuffix(intermediateArtifacts.archiveFileNameSuffix())
            .setPropagateModuleMapToCompileAction(false)
            .setNeverLink(true)
            .addVariableExtension(extension);

    if (pchHdr != null) {
      result.addNonModuleMapHeader(pchHdr);
    }
    return result;
  }

  private static FeatureConfiguration getFeatureConfiguration(RuleContext ruleContext,
      BuildConfiguration configuration) {
    ImmutableList.Builder<String> activatedCrosstoolSelectables =
        ImmutableList.<String>builder()
            .addAll(ACTIVATED_ACTIONS)
            .addAll(
                ruleContext
                    .getFragment(AppleConfiguration.class)
                    .getBitcodeMode()
                    .getFeatureNames())
            // We create a module map by default to allow for Swift interop.
            .add(CppRuleClasses.MODULE_MAPS)
            .add(CppRuleClasses.COMPILE_ALL_MODULES)
            .add(CppRuleClasses.EXCLUDE_PRIVATE_HEADERS_IN_MODULE_MAPS)
            .add(CppRuleClasses.ONLY_DOTH_HEADERS_IN_MODULE_MAPS)
            .add(CppRuleClasses.COMPILE_ACTION_FLAGS_IN_FLAG_SET)
            .add(CppRuleClasses.DEPENDENCY_FILE)
            .add(CppRuleClasses.INCLUDE_PATHS)
            .add(configuration.getCompilationMode().toString());

    if (ruleContext.getConfiguration().getFragment(ObjcConfiguration.class).moduleMapsEnabled()) {
      activatedCrosstoolSelectables.add(OBJC_MODULE_FEATURE_NAME);
    }
    if (!CompilationAttributes.Builder.fromRuleContext(ruleContext).build().enableModules()) {
      activatedCrosstoolSelectables.add(NO_ENABLE_MODULES_FEATURE_NAME);
    }
    if (ruleContext.getConfiguration().getFragment(ObjcConfiguration.class).shouldStripBinary()) {
      activatedCrosstoolSelectables.add(DEAD_STRIP_FEATURE_NAME);
    }
    if (ruleContext.attributes().has("pch", BuildType.LABEL)
        && ruleContext.getPrerequisiteArtifact("pch", Mode.TARGET) != null) {
      activatedCrosstoolSelectables.add("pch");
    }
    if (configuration.isCodeCoverageEnabled()) {
      activatedCrosstoolSelectables.add(RUN_COVERAGE_FEATURE_NAME);
    }
    if (configuration.isLLVMCoverageMapFormatEnabled()) {
      activatedCrosstoolSelectables.add(LLVM_COVERAGE_MAP_FORMAT);
    } else {
      activatedCrosstoolSelectables.add(GCC_COVERAGE_MAP_FORMAT);
    }
    return configuration
        .getFragment(CppConfiguration.class)
        .getFeatures()
        .getFeatureConfiguration(activatedCrosstoolSelectables.build());
  }

  private static ImmutableList<Artifact> getObjFiles(
      CompilationArtifacts compilationArtifacts, IntermediateArtifacts intermediateArtifacts) {
    ImmutableList.Builder<Artifact> result = new ImmutableList.Builder<>();
    for (Artifact sourceFile : compilationArtifacts.getSrcs()) {
      result.add(intermediateArtifacts.objFile(sourceFile));
    }
    for (Artifact nonArcSourceFile : compilationArtifacts.getNonArcSrcs()) {
      result.add(intermediateArtifacts.objFile(nonArcSourceFile));
    }
    return result.build();
  }

  private void registerHeaderScanningActions(
      Info info, ObjcProvider objcProvider, CompilationArtifacts compilationArtifacts) {
    // PIC is not used for Obj-C builds, if that changes this method will need to change
    if (!isHeaderThinningEnabled()
        || info.getCcCompilationOutputs().getObjectFiles(false).isEmpty()) {
      return;
    }

    ImmutableList.Builder<ObjcHeaderThinningInfo> headerThinningInfos = ImmutableList.builder();
    AnalysisEnvironment analysisEnvironment = ruleContext.getAnalysisEnvironment();
    for (Artifact objectFile : info.getCcCompilationOutputs().getObjectFiles(false)) {
      ActionAnalysisMetadata generatingAction =
          analysisEnvironment.getLocalGeneratingAction(objectFile);
      if (generatingAction instanceof CppCompileAction) {
        CppCompileAction action = (CppCompileAction) generatingAction;
        Artifact sourceFile = action.getSourceFile();
        if (!sourceFile.isTreeArtifact()
            && SOURCES_FOR_HEADER_THINNING.matches(sourceFile.getFilename())) {
          headerThinningInfos.add(
              new ObjcHeaderThinningInfo(
                  sourceFile,
                  intermediateArtifacts.headersListFile(sourceFile),
                  action.getCompilerOptions()));
        }
      }
    }
    registerHeaderScanningActions(headerThinningInfos.build(), objcProvider, compilationArtifacts);
  }
}
