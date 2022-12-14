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

import static com.google.devtools.build.lib.collect.nestedset.Order.STABLE_ORDER;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.ActionExecutionContext;
import com.google.devtools.build.lib.actions.ActionExecutionException;
import com.google.devtools.build.lib.actions.ActionOwner;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ArtifactResolver;
import com.google.devtools.build.lib.actions.Executor;
import com.google.devtools.build.lib.actions.ResourceSet;
import com.google.devtools.build.lib.analysis.actions.CommandLine;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadCompatible;
import com.google.devtools.build.lib.profiler.Profiler;
import com.google.devtools.build.lib.profiler.ProfilerTask;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;
import com.google.devtools.build.lib.rules.apple.Platform;
import com.google.devtools.build.lib.rules.cpp.CppCompileAction.DotdFile;
import com.google.devtools.build.lib.rules.cpp.HeaderDiscovery;
import com.google.devtools.build.lib.rules.cpp.IncludeScanningContext;
import com.google.devtools.build.lib.util.DependencySet;
import com.google.devtools.build.lib.util.Fingerprint;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * An action that compiles objc or objc++ source.
 *
 * <p>We don't use a plain SpawnAction here because we implement .d input pruning, which requires
 * post-execution filtering of input artifacts.
 *
 * <p>We don't use a CppCompileAction because the ObjcCompileAction uses custom logic instead of the
 * CROSSTOOL to construct its command line.
 */
public class ObjcCompileAction extends SpawnAction {

  private final DotdFile dotdFile;
  private final Artifact sourceFile;
  private final NestedSet<Artifact> mandatoryInputs;


  private static final String GUID = "a00d5bac-a72c-4f0f-99a7-d5fdc6072137";
  
  private ObjcCompileAction(
      ActionOwner owner,
      Iterable<Artifact> tools,
      Iterable<Artifact> inputs,
      Iterable<Artifact> outputs,
      ResourceSet resourceSet,
      CommandLine argv,
      ImmutableMap<String, String> environment,
      ImmutableMap<String, String> executionInfo,
      String progressMessage,
      ImmutableMap<PathFragment, Artifact> inputManifests,
      String mnemonic,
      boolean executeUnconditionally,
      ExtraActionInfoSupplier<?> extraActionInfoSupplier,
      DotdFile dotdFile,
      Artifact sourceFile,
      NestedSet<Artifact> mandatoryInputs) {
    super(
        owner,
        tools,
        inputs,
        outputs,
        resourceSet,
        argv,
        environment,
        ImmutableSet.<String>of(),
        executionInfo,
        progressMessage,
        inputManifests,
        mnemonic,
        executeUnconditionally,
        extraActionInfoSupplier);

    this.dotdFile = dotdFile;
    this.sourceFile = sourceFile;
    this.mandatoryInputs = mandatoryInputs;
  }

  @Override
  public boolean discoversInputs() {
    return true;
  }

  @Override
  public Iterable<Artifact> discoverInputs(ActionExecutionContext actionExecutionContext) {
    // We do not use include scanning for objc
    return getInputs();
  }

  @Override
  public void execute(ActionExecutionContext actionExecutionContext)
      throws ActionExecutionException, InterruptedException {
    super.execute(actionExecutionContext);

    Executor executor = actionExecutionContext.getExecutor();
    IncludeScanningContext scanningContext = executor.getContext(IncludeScanningContext.class);
    NestedSet<Artifact> discoveredInputs =
        discoverInputsFromDotdFiles(executor.getExecRoot(), scanningContext.getArtifactResolver());

    updateActionInputs(discoveredInputs);
  }

  @VisibleForTesting
  public NestedSet<Artifact> discoverInputsFromDotdFiles(
      Path execRoot, ArtifactResolver artifactResolver) throws ActionExecutionException {
    if (dotdFile == null) {
      return NestedSetBuilder.<Artifact>stableOrder().build();
    }
    return new HeaderDiscovery.Builder()
        .setAction(this)
        .setSourceFile(sourceFile)
        .setDotdFile(dotdFile)
        .setDependencySet(processDepset(execRoot))
        .setPermittedSystemIncludePrefixes(ImmutableList.<Path>of())
        .setAllowedDerivedinputsMap(getAllowedDerivedInputsMap())
        .build()
        .discoverInputsFromDotdFiles(execRoot, artifactResolver);
  }

  private DependencySet processDepset(Path execRoot) throws ActionExecutionException {
    try {
      DependencySet depSet = new DependencySet(execRoot);
      return depSet.read(dotdFile.getPath());
    } catch (IOException e) {
      // Some kind of IO or parse exception--wrap & rethrow it to stop the build.
      throw new ActionExecutionException("error while parsing .d file", e, this, false);
    }
  }

  /** Utility function that adds artifacts to an input map, but only if they are sources. */
  private void addToMapIfSource(Map<PathFragment, Artifact> map, Iterable<Artifact> artifacts) {
    for (Artifact artifact : artifacts) {
      if (!artifact.isSourceArtifact()) {
        map.put(artifact.getExecPath(), artifact);
      }
    }
  }

  private Map<PathFragment, Artifact> getAllowedDerivedInputsMap() {
    Map<PathFragment, Artifact> allowedDerivedInputMap = new HashMap<>();
    addToMapIfSource(allowedDerivedInputMap, getInputs());
    allowedDerivedInputMap.put(sourceFile.getExecPath(), sourceFile);
    return allowedDerivedInputMap;
  }

  /**
   * Recalculates this action's live input collection, including sources, middlemen.
   *
   * @throws ActionExecutionException iff any errors happen during update.
   */
  @VisibleForTesting
  @ThreadCompatible
  public final synchronized void updateActionInputs(NestedSet<Artifact> discoveredInputs)
      throws ActionExecutionException {
    NestedSetBuilder<Artifact> inputs = NestedSetBuilder.stableOrder();
    Profiler.instance().startTask(ProfilerTask.ACTION_UPDATE, this);
    try {
      inputs.addTransitive(mandatoryInputs);
      inputs.addTransitive(discoveredInputs);
    } finally {
      Profiler.instance().completeTask(ProfilerTask.ACTION_UPDATE);
      setInputs(inputs.build());
    }
  }

  @Override
  public String computeKey() {
    Fingerprint f = new Fingerprint();
    f.addString(GUID);
    f.addString(super.computeKey());
    f.addBoolean(dotdFile.artifact() == null);
    f.addPath(dotdFile.getSafeExecPath());
    return f.hexDigestAndReset();
  }

  /** A Builder for ObjcCompileAction */
  public static class Builder extends SpawnAction.Builder {

    private DotdFile dotdFile;
    private Artifact sourceFile;
    private final NestedSetBuilder<Artifact> mandatoryInputs = new NestedSetBuilder<>(STABLE_ORDER);

    /**
     * Creates a new compile action builder with apple environment variables set that are typically
     * needed by the apple toolchain.
     */
    public static ObjcCompileAction.Builder createObjcCompileActionBuilderWithAppleEnv(
        AppleConfiguration appleConfiguration, Platform targetPlatform) {
      return (Builder)
          new ObjcCompileAction.Builder()
              .setExecutionInfo(ObjcRuleClasses.darwinActionExecutionRequirement())
              .setEnvironment(
                  ObjcRuleClasses.appleToolchainEnvironment(appleConfiguration, targetPlatform));
    }

    @Override
    public Builder addTools(Iterable<Artifact> artifacts) {
      super.addTools(artifacts);
      mandatoryInputs.addAll(artifacts);
      return this;
    }

    /** Sets a .d file that will used to prune input headers */
    public Builder setDotdFile(DotdFile dotdFile) {
      Preconditions.checkNotNull(dotdFile);
      this.dotdFile = dotdFile;
      return this;
    }

    /** Sets the source file that is being compiled in this action */
    public Builder setSourceFile(Artifact sourceFile) {
      Preconditions.checkNotNull(sourceFile);
      this.sourceFile = sourceFile;
      this.mandatoryInputs.add(sourceFile);
      this.addInput(sourceFile);
      return this;
    }

    /** Add an input that cannot be pruned */
    public Builder addMandatoryInput(Artifact input) {
      Preconditions.checkNotNull(input);
      this.mandatoryInputs.add(input);
      this.addInput(input);
      return this;
    }

    /** Add inputs that cannot be pruned */
    public Builder addMandatoryInputs(Iterable<Artifact> input) {
      Preconditions.checkNotNull(input);
      this.mandatoryInputs.addAll(input);
      this.addInputs(input);
      return this;
    }

    /** Add inputs that cannot be pruned */
    public Builder addTransitiveMandatoryInputs(NestedSet<Artifact> input) {
      Preconditions.checkNotNull(input);
      this.mandatoryInputs.addTransitive(input);
      this.addTransitiveInputs(input);
      return this;
    }

    @Override
    protected SpawnAction createSpawnAction(
        ActionOwner owner,
        NestedSet<Artifact> tools,
        NestedSet<Artifact> inputsAndTools,
        ImmutableList<Artifact> outputs,
        ResourceSet resourceSet,
        CommandLine actualCommandLine,
        ImmutableMap<String, String> env,
        ImmutableSet<String> clientEnvironmentVariables,
        ImmutableMap<String, String> executionInfo,
        String progressMessage,
        ImmutableMap<PathFragment, Artifact> inputAndToolManifests,
        String mnemonic) {
      return new ObjcCompileAction(
          owner,
          tools,
          inputsAndTools,
          outputs,
          resourceSet,
          actualCommandLine,
          env,
          executionInfo,
          progressMessage,
          inputAndToolManifests,
          mnemonic,
          executeUnconditionally,
          extraActionInfoSupplier,
          dotdFile,
          sourceFile,
          mandatoryInputs.build());
    }
  }
}
