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

package com.google.devtools.build.buildjar;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.buildjar.instrumentation.JacocoInstrumentationProcessor;
import com.google.devtools.build.buildjar.javac.BlazeJavacArguments;
import com.google.devtools.build.buildjar.javac.plugins.BlazeJavaCompilerPlugin;
import com.google.devtools.build.buildjar.javac.plugins.dependency.DependencyModule;
import com.google.devtools.build.buildjar.javac.plugins.processing.AnnotationProcessingModule;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** All the information needed to perform a single Java library build operation. */
public final class JavaLibraryBuildRequest {
  private ImmutableList<String> javacOpts;

  /** Where to store source files generated by annotation processors. */
  private final Path sourceGenDir;

  /** The path to an output jar for source files generated by annotation processors. */
  private final Path generatedSourcesOutputJar;

  /** The path to an output jar for classfiles generated by annotation processors. */
  private final Path generatedClassOutputJar;

  private final ArrayList<Path> sourceFiles;
  private final ImmutableList<Path> sourceJars;

  private final ImmutableList<Path> sourcePath;
  private final ImmutableList<Path> classPath;
  private final ImmutableList<Path> bootClassPath;
  private final ImmutableList<Path> extClassPath;

  private final ImmutableList<Path> processorPath;
  private final List<String> processorNames;

  private final Path outputJar;
  private final Path nativeHeaderOutput;
  @Nullable private final String targetLabel;
  @Nullable private final String injectingRuleKind;

  private final Path classDir;
  private final Path tempDir;

  private JacocoInstrumentationProcessor jacocoInstrumentationProcessor;

  private final boolean compressJar;

  /** Repository for all dependency-related information. */
  private final DependencyModule dependencyModule;

  /** Repository for information about annotation processor-generated symbols. */
  private final AnnotationProcessingModule processingModule;

  /** List of plugins that are given to javac. */
  private final ImmutableList<BlazeJavaCompilerPlugin> plugins;

  /**
   * Constructs a build from a list of command args. Sets the same JavacRunner for both compilation
   * and annotation processing.
   *
   * @param optionsParser the parsed command line args.
   * @param extraPlugins extraneous plugins to use in addition to the strict dependency module.
   * @throws InvalidCommandLineException on any command line error
   */
  public JavaLibraryBuildRequest(
      OptionsParser optionsParser, List<BlazeJavaCompilerPlugin> extraPlugins)
      throws InvalidCommandLineException, IOException {
    this(optionsParser, extraPlugins, new DependencyModule.Builder());
  }

  /**
   * Constructs a build from a list of command args. Sets the same JavacRunner for both compilation
   * and annotation processing.
   *
   * @param optionsParser the parsed command line args.
   * @param extraPlugins extraneous plugins to use in addition to the strict dependency module.
   * @param depsBuilder a preconstructed dependency module builder.
   * @throws InvalidCommandLineException on any command line error
   */
  public JavaLibraryBuildRequest(
      OptionsParser optionsParser,
      List<BlazeJavaCompilerPlugin> extraPlugins,
      DependencyModule.Builder depsBuilder)
      throws InvalidCommandLineException, IOException {
    depsBuilder.setDirectJars(
        optionsParser.directJars().stream().map(Paths::get).collect(toImmutableSet()));
    if (optionsParser.getStrictJavaDeps() != null) {
      depsBuilder.setStrictJavaDeps(optionsParser.getStrictJavaDeps());
    }
    if (optionsParser.getOutputDepsProtoFile() != null) {
      depsBuilder.setOutputDepsProtoFile(Paths.get(optionsParser.getOutputDepsProtoFile()));
    }
    depsBuilder.addDepsArtifacts(asPaths(optionsParser.getDepsArtifacts()));
    depsBuilder.setPlatformJars(
        ImmutableSet.<Path>builder()
            .addAll(asPaths(optionsParser.getBootClassPath()))
            .addAll(asPaths(optionsParser.getExtClassPath()))
            .build());
    if (optionsParser.reduceClasspath()) {
      depsBuilder.setReduceClasspath();
    }
    if (optionsParser.getTargetLabel() != null) {
      depsBuilder.setTargetLabel(optionsParser.getTargetLabel());
    }
    this.dependencyModule = depsBuilder.build();

    AnnotationProcessingModule.Builder processingBuilder = AnnotationProcessingModule.builder();
    if (optionsParser.getSourceGenDir() != null) {
      processingBuilder.setSourceGenDir(Paths.get(optionsParser.getSourceGenDir()));
    }
    if (optionsParser.getManifestProtoPath() != null) {
      processingBuilder.setManifestProtoPath(Paths.get(optionsParser.getManifestProtoPath()));
    }
    processingBuilder.addAllSourceRoots(optionsParser.getSourceRoots());
    this.processingModule = processingBuilder.build();

    ImmutableList.Builder<BlazeJavaCompilerPlugin> pluginsBuilder =
        ImmutableList.<BlazeJavaCompilerPlugin>builder().add(dependencyModule.getPlugin());
    processingModule.registerPlugin(pluginsBuilder);
    pluginsBuilder.addAll(extraPlugins);
    this.plugins = pluginsBuilder.build();

    this.compressJar = optionsParser.compressJar();
    this.sourceFiles = new ArrayList<>(asPaths(optionsParser.getSourceFiles()));
    this.sourceJars = asPaths(optionsParser.getSourceJars());
    this.classPath = asPaths(optionsParser.getClassPath());
    this.sourcePath = asPaths(optionsParser.getSourcePath());
    this.bootClassPath = asPaths(optionsParser.getBootClassPath());
    this.extClassPath = asPaths(optionsParser.getExtClassPath());
    this.processorPath = asPaths(optionsParser.getProcessorPath());
    this.processorNames = optionsParser.getProcessorNames();
    // Since the default behavior of this tool with no arguments is "rm -fr <classDir>", let's not
    // default to ".", shall we?
    this.classDir = asPath(firstNonNull(optionsParser.getClassDir(), "classes"));
    this.tempDir = asPath(firstNonNull(optionsParser.getTempDir(), "_tmp"));
    this.outputJar = asPath(optionsParser.getOutputJar());
    this.nativeHeaderOutput = asPath(optionsParser.getNativeHeaderOutput());
    for (Map.Entry<String, List<String>> entry : optionsParser.getPostProcessors().entrySet()) {
      switch (entry.getKey()) {
        case "jacoco":
          this.jacocoInstrumentationProcessor =
              JacocoInstrumentationProcessor.create(entry.getValue());
          break;
        default:
          throw new AssertionError("unsupported post-processor " + entry.getKey());
      }
    }
    this.javacOpts = ImmutableList.copyOf(optionsParser.getJavacOpts());
    this.sourceGenDir = asPath(optionsParser.getSourceGenDir());
    this.generatedSourcesOutputJar = asPath(optionsParser.getGeneratedSourcesOutputJar());
    this.generatedClassOutputJar = asPath(optionsParser.getManifestProtoPath());
    this.targetLabel = optionsParser.getTargetLabel();
    this.injectingRuleKind = optionsParser.getInjectingRuleKind();
  }

  private static ImmutableList<Path> asPaths(Collection<String> paths) {
    return paths.stream().map(Paths::get).collect(toImmutableList());
  }

  private static @Nullable Path asPath(@Nullable String path) {
    return path != null ? Paths.get(path) : null;
  }

  public ImmutableList<String> getJavacOpts() {
    return javacOpts;
  }

  public void setJavacOpts(List<String> javacOpts) {
    this.javacOpts = ImmutableList.copyOf(javacOpts);
  }

  public Path getSourceGenDir() {
    return sourceGenDir;
  }

  public ImmutableList<Path> getSourcePath() {
    return sourcePath;
  }

  public Path getGeneratedSourcesOutputJar() {
    return generatedSourcesOutputJar;
  }

  public Path getGeneratedClassOutputJar() {
    return generatedClassOutputJar;
  }

  public ArrayList<Path> getSourceFiles() {
    // TODO(cushon): This is being modified after parsing to add files from source jars.
    return sourceFiles;
  }

  public ImmutableList<Path> getSourceJars() {
    return sourceJars;
  }

  public ImmutableList<Path> getClassPath() {
    return classPath;
  }

  public ImmutableList<Path> getBootClassPath() {
    return bootClassPath;
  }

  public ImmutableList<Path> getExtClassPath() {
    return extClassPath;
  }

  public ImmutableList<Path> getProcessorPath() {
    return processorPath;
  }

  public List<String> getProcessors() {
    // TODO(bazel-team): This might be modified by a JavaLibraryBuilder to enable specific
    // annotation processors.
    return processorNames;
  }

  public Path getOutputJar() {
    return outputJar;
  }

  public Path getNativeHeaderOutput() {
    return nativeHeaderOutput;
  }

  public Path getClassDir() {
    return classDir;
  }

  public Path getTempDir() {
    return tempDir;
  }

  public Path getNativeHeaderDir() {
    return getTempDir().resolve("native_headers");
  }

  public JacocoInstrumentationProcessor getJacocoInstrumentationProcessor() {
    return jacocoInstrumentationProcessor;
  }

  public boolean compressJar() {
    return compressJar;
  }

  public DependencyModule getDependencyModule() {
    return dependencyModule;
  }

  public AnnotationProcessingModule getProcessingModule() {
    return processingModule;
  }

  public ImmutableList<BlazeJavaCompilerPlugin> getPlugins() {
    return plugins;
  }

  @Nullable
  public String getTargetLabel() {
    return targetLabel;
  }

  @Nullable
  public String getInjectingRuleKind() {
    return injectingRuleKind;
  }

  public BlazeJavacArguments toBlazeJavacArguments(ImmutableList<Path> classPath) {
    BlazeJavacArguments.Builder builder =
        BlazeJavacArguments.builder()
            .classPath(classPath)
            .classOutput(getClassDir())
            .bootClassPath(
                ImmutableList.<Path>builder()
                    .addAll(getBootClassPath())
                    .addAll(getExtClassPath())
                    .build())
            .javacOptions(makeJavacArguments())
            .sourceFiles(ImmutableList.copyOf(getSourceFiles()))
            .processors(null)
            .sourcePath(getSourcePath())
            .sourceOutput(getSourceGenDir())
            .processorPath(getProcessorPath())
            .plugins(getPlugins());
    if (getNativeHeaderOutput() != null) {
      builder.nativeHeaderOutput(getNativeHeaderDir());
    }
    return builder.build();
  }

  /** Constructs a command line that can be used for a javac invocation. */
  ImmutableList<String> makeJavacArguments() {
    ImmutableList.Builder<String> javacArguments = ImmutableList.builder();
    // default to -implicit:none, but allow the user to override with -implicit:class.
    javacArguments.add("-implicit:none");
    javacArguments.addAll(getJavacOpts());

    if (!getProcessors().isEmpty() && !getSourceFiles().isEmpty()) {
      // ImmutableSet.copyOf maintains order
      ImmutableSet<String> deduplicatedProcessorNames = ImmutableSet.copyOf(getProcessors());
      javacArguments.add("-processor");
      javacArguments.add(Joiner.on(',').join(deduplicatedProcessorNames));
    } else {
      // This is necessary because some jars contain discoverable annotation processors that
      // previously didn't run, and they break builds if the "-proc:none" option is not passed to
      // javac.
      javacArguments.add("-proc:none");
    }

    for (String option : getJavacOpts()) {
      if (option.startsWith("-J")) { // ignore the VM options.
        continue;
      }
      if (option.equals("-processor") || option.equals("-processorpath")) {
        throw new IllegalStateException(
            "Using "
                + option
                + " in javacopts is no longer supported."
                + " Use a java_plugin() rule instead.");
      }
    }

    return javacArguments.build();
  }
}
