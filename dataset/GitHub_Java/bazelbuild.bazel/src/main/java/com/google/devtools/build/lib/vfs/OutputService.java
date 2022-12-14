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

package com.google.devtools.build.lib.vfs;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.ActionInputMap;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.BuildFailedException;
import com.google.devtools.build.lib.actions.EnvironmentalExecException;
import com.google.devtools.build.lib.actions.ExecException;
import com.google.devtools.build.lib.actions.MetadataConsumer;
import com.google.devtools.build.lib.actions.cache.MetadataHandler;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.util.AbruptExitException;
import com.google.devtools.build.skyframe.SkyFunction;
import java.io.IOException;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * An OutputService retains control over the Blaze output tree, and provides a higher level of
 * abstraction compared to the VFS layer.
 *
 * <p>Higher-level facilities include batch statting, cleaning the output tree, creating symlink
 * trees, and out-of-band insertion of metadata into the tree.
 */
public interface OutputService {

  /**
   * @return the name of filesystem, akin to what you might see in /proc/mounts
   */
  String getFilesSystemName();

  /**
   * Start the build.
   *
   * @param buildId the UUID build identifier
   * @param finalizeActions whether this build is finalizing actions so that the output service
   *                        can track output tree modifications
   * @return a ModifiedFileSet of changed output files.
   * @throws BuildFailedException if build preparation failed
   * @throws InterruptedException
   */
  ModifiedFileSet startBuild(EventHandler eventHandler, UUID buildId, boolean finalizeActions)
      throws BuildFailedException, AbruptExitException, InterruptedException;

  /**
   * Finish the build.
   *
   * @param buildSuccessful iff build was successful
   * @throws BuildFailedException on failure
   */
  void finalizeBuild(boolean buildSuccessful)
      throws BuildFailedException, AbruptExitException, InterruptedException;

  /** Notify the output service of a completed action. */
  void finalizeAction(Action action, MetadataHandler metadataHandler)
      throws IOException, EnvironmentalExecException;

  /**
   * @return the BatchStat instance or null.
   */
  BatchStat getBatchStatter();

  /**
   * @return true iff createSymlinkTree() is available.
   */
  boolean canCreateSymlinkTree();

  /**
   * Creates the symlink tree
   *
   * @param inputPath the input manifest
   * @param outputPath the output manifest
   * @param filesetTree is true iff we're constructing a Fileset
   * @param symlinkTreeRoot the symlink tree root, relative to the execRoot
   * @throws ExecException on failure
   * @throws InterruptedException
   */
  void createSymlinkTree(Path inputPath, Path outputPath, boolean filesetTree,
      PathFragment symlinkTreeRoot) throws ExecException, InterruptedException;

  /**
   * Cleans the entire output tree.
   *
   * @throws ExecException on failure
   * @throws InterruptedException
   */
  void clean() throws ExecException, InterruptedException;

  /** @return true iff the file actually lives on a remote server */
  boolean isRemoteFile(Artifact file);

  default boolean supportsActionFileSystem() {
    return false;
  }

  /**
   * @param sourceDelegate filesystem for reading source files (excludes output files)
   * @param execRootFragment absolute path fragment pointing to the execution root
   * @param relativeOutputPath execution root relative path to output
   * @param sourceRoots list of directories on the package path (from {@link
   *     com.google.devtools.build.lib.pkgcache.PathPackageLocator})
   * @param inputArtifactData information about required inputs to the action
   * @param outputArtifacts required outputs of the action
   * @return an action-scoped filesystem if {@link supportsActionFileSystem} is true
   */
  @Nullable
  default FileSystem createActionFileSystem(
      FileSystem sourceDelegate,
      PathFragment execRootFragment,
      String relativeOutputPath,
      ImmutableList<Root> sourceRoots,
      ActionInputMap inputArtifactData,
      Iterable<Artifact> outputArtifacts) {
    return null;
  }

  /**
   * Updates the context used by the filesystem returned by {@link createActionFileSystem}.
   *
   * <p>Should be called as context changes throughout action execution.
   *
   * @param actionFileSystem must be a filesystem returned by {@link createActionFileSystem}.
   */
  default void updateActionFileSystemContext(
      FileSystem actionFileSystem, SkyFunction.Environment env, MetadataConsumer consumer) {}
}
