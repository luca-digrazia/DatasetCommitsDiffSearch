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
package com.google.devtools.build.lib.skyframe;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.ActionKeyContext;
import com.google.devtools.build.lib.analysis.BlazeDirectories;
import com.google.devtools.build.lib.analysis.WorkspaceStatusAction.Factory;
import com.google.devtools.build.lib.analysis.config.BuildOptions;
import com.google.devtools.build.lib.packages.PackageFactory;
import com.google.devtools.build.lib.rules.repository.ManagedDirectoriesKnowledge;
import com.google.devtools.build.lib.vfs.FileSystem;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionName;
import javax.annotation.Nullable;

/**
 * A factory of SkyframeExecutors that returns SequencedSkyframeExecutor.
 */
public class SequencedSkyframeExecutorFactory implements SkyframeExecutorFactory {

  private final BuildOptions defaultBuildOptions;

  public SequencedSkyframeExecutorFactory(BuildOptions defaultBuildOptions) {
    this.defaultBuildOptions = defaultBuildOptions;
  }

  @Override
  public SkyframeExecutor create(
      PackageFactory pkgFactory,
      FileSystem fileSystem,
      BlazeDirectories directories,
      ActionKeyContext actionKeyContext,
      Factory workspaceStatusActionFactory,
      Iterable<? extends DiffAwareness.Factory> diffAwarenessFactories,
      ImmutableMap<SkyFunctionName, SkyFunction> extraSkyFunctions,
      Iterable<SkyValueDirtinessChecker> customDirtinessCheckers,
      @Nullable ManagedDirectoriesKnowledge managedDirectoriesKnowledge) {
    return BazelSkyframeExecutorConstants.newBazelSkyframeExecutorBuilder()
        .setPkgFactory(pkgFactory)
        .setFileSystem(fileSystem)
        .setDirectories(directories)
        .setActionKeyContext(actionKeyContext)
        .setDefaultBuildOptions(defaultBuildOptions)
        .setWorkspaceStatusActionFactory(workspaceStatusActionFactory)
        .setDiffAwarenessFactories(diffAwarenessFactories)
        .setExtraSkyFunctions(extraSkyFunctions)
        .setCustomDirtinessCheckers(customDirtinessCheckers)
        .setManagedDirectoriesKnowledge(managedDirectoriesKnowledge)
        .build();
  }
}
