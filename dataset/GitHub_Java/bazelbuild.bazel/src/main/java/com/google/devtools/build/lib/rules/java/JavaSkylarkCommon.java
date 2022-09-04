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
package com.google.devtools.build.lib.rules.java;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.PlatformOptions;
import com.google.devtools.build.lib.analysis.platform.ConstraintValueInfo;
import com.google.devtools.build.lib.analysis.platform.ToolchainInfo;
import com.google.devtools.build.lib.analysis.skylark.SkylarkActionFactory;
import com.google.devtools.build.lib.analysis.skylark.SkylarkRuleContext;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.Provider;
import com.google.devtools.build.lib.skylarkbuildapi.core.ProviderApi;
import com.google.devtools.build.lib.skylarkbuildapi.java.JavaCommonApi;
import com.google.devtools.build.lib.skylarkbuildapi.java.JavaToolchainSkylarkApiProviderApi;
import com.google.devtools.build.lib.syntax.Depset;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Sequence;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkThread;

/** A module that contains Skylark utilities for Java support. */
public class JavaSkylarkCommon
    implements JavaCommonApi<
        Artifact,
        JavaInfo,
        JavaToolchainProvider,
        JavaRuntimeInfo,
        ConstraintValueInfo,
        SkylarkRuleContext,
        SkylarkActionFactory> {
  private final JavaSemantics javaSemantics;

  public JavaSkylarkCommon(JavaSemantics javaSemantics) {
    this.javaSemantics = javaSemantics;
  }

  @Override
  public Provider getJavaProvider() {
    return JavaInfo.PROVIDER;
  }

  @Override
  public JavaInfo createJavaCompileAction(
      SkylarkRuleContext skylarkRuleContext,
      Sequence<?> sourceJars, // <Artifact> expected
      Sequence<?> sourceFiles, // <Artifact> expected
      Artifact outputJar,
      Object outputSourceJar,
      Sequence<?> javacOpts, // <String> expected
      Sequence<?> deps, // <JavaInfo> expected
      Sequence<?> exports, // <JavaInfo> expected
      Sequence<?> plugins, // <JavaInfo> expected
      Sequence<?> exportedPlugins, // <JavaInfo> expected
      Sequence<?> annotationProcessorAdditionalInputs, // <Artifact> expected
      Sequence<?> annotationProcessorAdditionalOutputs, // <Artifact> expected
      String strictDepsMode,
      JavaToolchainProvider javaToolchain,
      JavaRuntimeInfo hostJavabase,
      Sequence<?> sourcepathEntries, // <Artifact> expected
      Sequence<?> resources, // <Artifact> expected
      Boolean neverlink,
      Location location,
      StarlarkThread thread)
      throws EvalException, InterruptedException {

    return JavaInfoBuildHelper.getInstance()
        .createJavaCompileAction(
            skylarkRuleContext,
            sourceJars.getContents(Artifact.class, "source_jars"),
            sourceFiles.getContents(Artifact.class, "source_files"),
            outputJar,
            outputSourceJar == Starlark.NONE ? null : (Artifact) outputSourceJar,
            javacOpts.getContents(String.class, "javac_opts"),
            deps.getContents(JavaInfo.class, "deps"),
            exports.getContents(JavaInfo.class, "exports"),
            plugins.getContents(JavaInfo.class, "plugins"),
            exportedPlugins.getContents(JavaInfo.class, "exported_plugins"),
            annotationProcessorAdditionalInputs.getContents(
                Artifact.class, "annotation_processor_additional_inputs"),
            annotationProcessorAdditionalOutputs.getContents(
                Artifact.class, "annotation_processor_additional_outputs"),
            strictDepsMode,
            javaToolchain,
            hostJavabase,
            sourcepathEntries.getContents(Artifact.class, "sourcepath"),
            resources.getContents(Artifact.class, "resources"),
            neverlink,
            javaSemantics,
            location,
            thread);
  }

  @Override
  public Artifact runIjar(
      SkylarkActionFactory actions,
      Artifact jar,
      Object targetLabel,
      JavaToolchainProvider javaToolchain,
      Location location)
      throws EvalException {
    return JavaInfoBuildHelper.getInstance()
        .buildIjar(
            actions,
            jar,
            targetLabel != Starlark.NONE ? (Label) targetLabel : null,
            javaToolchain,
            location);
  }

  @Override
  public Artifact stampJar(
      SkylarkActionFactory actions,
      Artifact jar,
      Label targetLabel,
      JavaToolchainProvider javaToolchain,
      Location location)
      throws EvalException {
    return JavaInfoBuildHelper.getInstance()
        .stampJar(actions, jar, targetLabel, javaToolchain, location);
  }

  @Override
  public Artifact packSources(
      SkylarkActionFactory actions,
      Artifact outputJar,
      Sequence<?> sourceFiles, // <Artifact> expected.
      Sequence<?> sourceJars, // <Artifact> expected.
      JavaToolchainProvider javaToolchain,
      JavaRuntimeInfo hostJavabase,
      Location location)
      throws EvalException {
    return JavaInfoBuildHelper.getInstance()
        .packSourceFiles(
            actions,
            outputJar,
            /* outputSourceJar= */ null,
            sourceFiles.getContents(Artifact.class, "sources"),
            sourceJars.getContents(Artifact.class, "source_jars"),
            javaToolchain,
            hostJavabase,
            location);
  }

  @Override
  // TODO(b/78512644): migrate callers to passing explicit javacopts or using custom toolchains, and
  // delete
  public ImmutableList<String> getDefaultJavacOpts(
      JavaToolchainProvider javaToolchain, Location location) throws EvalException {
    // We don't have a rule context if the default_javac_opts.java_toolchain parameter is set
    return ((JavaToolchainProvider) javaToolchain).getJavacOptions(/* ruleContext= */ null);
  }

  @Override
  public JavaInfo mergeJavaProviders(Sequence<?> providers /* <JavaInfo> expected. */)
      throws EvalException {
    return JavaInfo.merge(providers.getContents(JavaInfo.class, "providers"));
  }

  // TODO(b/65113771): Remove this method because it's incorrect.
  @Override
  public JavaInfo makeNonStrict(JavaInfo javaInfo) {
    return JavaInfo.Builder.copyOf(javaInfo)
        // Overwrites the old provider.
        .addProvider(
            JavaCompilationArgsProvider.class,
            JavaCompilationArgsProvider.makeNonStrict(
                javaInfo.getProvider(JavaCompilationArgsProvider.class)))
        .build();
  }

  @Override
  public Provider getJavaToolchainProvider() {
    return ToolchainInfo.PROVIDER;
  }

  @Override
  public Provider getJavaRuntimeProvider() {
    return ToolchainInfo.PROVIDER;
  }

  @Override
  public boolean isJavaToolchainResolutionEnabled(SkylarkRuleContext ruleContext)
      throws EvalException {
    return ruleContext
        .getConfiguration()
        .getOptions()
        .get(PlatformOptions.class)
        .useToolchainResolutionForJavaRules;
  }

  @Override
  public ProviderApi getMessageBundleInfo() {
    // No implementation in Bazel. This method not callable in Starlark except through
    // (discouraged) use of --experimental_google_legacy_api.
    return null;
  }

  @Override
  public JavaInfo addConstraints(JavaInfo javaInfo, Sequence<?> constraints) throws EvalException {
    // No implementation in Bazel. This method not callable in Starlark except through
    // (discouraged) use of --experimental_google_legacy_api.
    return null;
  }

  @Override
  public JavaInfo removeAnnotationProcessors(JavaInfo javaInfo) {
    // No implementation in Bazel. This method not callable in Starlark except through
    // (discouraged) use of --experimental_google_legacy_api.
    return null;
  }

  @Override
  public Depset /*<Artifact>*/ getCompileTimeJavaDependencyArtifacts(JavaInfo javaInfo) {
    // No implementation in Bazel. This method not callable in Starlark except through
    // (discouraged) use of --experimental_google_legacy_api.
    return null;
  }

  @Override
  public JavaInfo addCompileTimeJavaDependencyArtifacts(
      JavaInfo javaInfo, Sequence<?> compileTimeJavaDependencyArtifacts) throws EvalException {
    // No implementation in Bazel. This method not callable in Starlark except through
    // (discouraged) use of --experimental_google_legacy_api.
    return null;
  }

  @Override
  public Label getJavaToolchainLabel(
      JavaToolchainSkylarkApiProviderApi toolchain, Location location) throws EvalException {
    // No implementation in Bazel. This method not callable in Starlark except through
    // (discouraged) use of --experimental_google_legacy_api.
    return null;
  }
}
