// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.bazel.rules.cpp;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.platform.ConstraintValueInfo;
import com.google.devtools.build.lib.analysis.starlark.StarlarkActionFactory;
import com.google.devtools.build.lib.analysis.starlark.StarlarkRuleContext;
import com.google.devtools.build.lib.rules.cpp.CcCompilationContext;
import com.google.devtools.build.lib.rules.cpp.CcCompilationOutputs;
import com.google.devtools.build.lib.rules.cpp.CcDebugInfoContext;
import com.google.devtools.build.lib.rules.cpp.CcLinkingContext;
import com.google.devtools.build.lib.rules.cpp.CcLinkingContext.LinkerInput;
import com.google.devtools.build.lib.rules.cpp.CcLinkingOutputs;
import com.google.devtools.build.lib.rules.cpp.CcModule;
import com.google.devtools.build.lib.rules.cpp.CcToolchainConfigInfo;
import com.google.devtools.build.lib.rules.cpp.CcToolchainProvider;
import com.google.devtools.build.lib.rules.cpp.CcToolchainVariables;
import com.google.devtools.build.lib.rules.cpp.CppModuleMap;
import com.google.devtools.build.lib.rules.cpp.CppSemantics;
import com.google.devtools.build.lib.rules.cpp.FdoContext;
import com.google.devtools.build.lib.rules.cpp.FeatureConfigurationForStarlark;
import com.google.devtools.build.lib.rules.cpp.LibraryToLink;
import com.google.devtools.build.lib.rules.cpp.LtoBackendArtifacts;
import com.google.devtools.build.lib.starlarkbuildapi.cpp.BazelCcModuleApi;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.Tuple;

/**
 * A module that contains Starlark utilities for C++ support.
 *
 * <p>This is a work in progress. The API is guarded behind
 * --experimental_cc_skylark_api_enabled_packages. The API is under development and unstable.
 */
public class BazelCcModule extends CcModule
    implements BazelCcModuleApi<
        StarlarkActionFactory,
        Artifact,
        FdoContext,
        ConstraintValueInfo,
        StarlarkRuleContext,
        CcToolchainProvider,
        FeatureConfigurationForStarlark,
        CcCompilationContext,
        CcCompilationOutputs,
        CcLinkingOutputs,
        LtoBackendArtifacts,
        LinkerInput,
        LibraryToLink,
        CcLinkingContext,
        CcToolchainVariables,
        CcToolchainConfigInfo,
        CcDebugInfoContext,
        CppModuleMap> {

  @Override
  public CppSemantics getSemantics() {
    return BazelCppSemantics.INSTANCE;
  }

  @Override
  public Tuple compile(
      StarlarkActionFactory starlarkActionFactoryApi,
      FeatureConfigurationForStarlark starlarkFeatureConfiguration,
      CcToolchainProvider starlarkCcToolchainProvider,
      Sequence<?> sources, // <Artifact> expected
      Sequence<?> publicHeaders, // <Artifact> expected
      Sequence<?> privateHeaders, // <Artifact> expected
      Object textualHeaders,
      Object additionalExportedHeaders,
      Sequence<?> includes, // <String> expected
      Sequence<?> quoteIncludes, // <String> expected
      Sequence<?> systemIncludes, // <String> expected
      Sequence<?> frameworkIncludes, // <String> expected
      Sequence<?> defines, // <String> expected
      Sequence<?> localDefines, // <String> expected
      String includePrefix,
      String stripIncludePrefix,
      Sequence<?> userCompileFlags, // <String> expected
      Sequence<?> ccCompilationContexts, // <CcCompilationContext> expected
      String name,
      boolean disallowPicOutputs,
      boolean disallowNopicOutputs,
      Sequence<?> additionalInputs, // <Artifact> expected
      Object moduleMap,
      Object additionalModuleMaps,
      Object propagateModuleMapToCompileAction,
      Object doNotGenerateModuleMap,
      Object codeCoverageEnabled,
      Object hdrsCheckingMode,
      Object variablesExtension,
      StarlarkThread thread)
      throws EvalException, InterruptedException {
    return compile(
        starlarkActionFactoryApi,
        starlarkFeatureConfiguration,
        starlarkCcToolchainProvider,
        sources,
        publicHeaders,
        privateHeaders,
        textualHeaders,
        additionalExportedHeaders,
        includes,
        quoteIncludes,
        systemIncludes,
        frameworkIncludes,
        defines,
        localDefines,
        includePrefix,
        stripIncludePrefix,
        userCompileFlags,
        ccCompilationContexts,
        name,
        disallowPicOutputs,
        disallowNopicOutputs,
        /* grepIncludes= */ null,
        /* headersForClifDoNotUseThisParam= */ ImmutableList.of(),
        StarlarkList.immutableCopyOf(
            Sequence.cast(additionalInputs, Artifact.class, "additional_inputs")),
        moduleMap,
        additionalModuleMaps,
        propagateModuleMapToCompileAction,
        doNotGenerateModuleMap,
        codeCoverageEnabled,
        hdrsCheckingMode,
        variablesExtension,
        thread);
  }

  @Override
  public CcLinkingOutputs link(
      StarlarkActionFactory actions,
      FeatureConfigurationForStarlark starlarkFeatureConfiguration,
      CcToolchainProvider starlarkCcToolchainProvider,
      Object compilationOutputs,
      Sequence<?> userLinkFlags, // <String> expected
      Sequence<?> linkingContexts, // <CcLinkingContext> expected
      String name,
      String language,
      String outputType,
      boolean linkDepsStatically,
      StarlarkInt stamp,
      Sequence<?> additionalInputs, // <Artifact> expected
      Object grepIncludes,
      Object linkArtifactNameSuffix,
      Object neverLink,
      Object testOnlyTarget,
      Object variablesExtension,
      StarlarkThread thread)
      throws InterruptedException, EvalException {
    return super.link(
        actions,
        starlarkFeatureConfiguration,
        starlarkCcToolchainProvider,
        convertFromNoneable(compilationOutputs, /* defaultValue= */ null),
        userLinkFlags,
        linkingContexts,
        name,
        language,
        outputType,
        linkDepsStatically,
        stamp,
        additionalInputs,
        /* grepIncludes= */ null,
        linkArtifactNameSuffix,
        neverLink,
        testOnlyTarget,
        variablesExtension,
        thread);
  }

  @Override
  public CcCompilationOutputs createCompilationOutputsFromStarlark(
      Object objectsObject, Object picObjectsObject) throws EvalException {
    return super.createCompilationOutputsFromStarlark(objectsObject, picObjectsObject);
  }

  @Override
  public CcCompilationOutputs mergeCcCompilationOutputsFromStarlark(Sequence<?> compilationOutputs)
      throws EvalException {
    CcCompilationOutputs.Builder ccCompilationOutputsBuilder = CcCompilationOutputs.builder();
    for (CcCompilationOutputs ccCompilationOutputs :
        Sequence.cast(compilationOutputs, CcCompilationOutputs.class, "compilation_outputs")) {
      ccCompilationOutputsBuilder.merge(ccCompilationOutputs);
    }
    return ccCompilationOutputsBuilder.build();
  }
}
