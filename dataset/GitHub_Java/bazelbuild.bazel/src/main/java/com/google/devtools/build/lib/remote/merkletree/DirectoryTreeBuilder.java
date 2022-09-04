// Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.remote.merkletree;

import build.bazel.remote.execution.v2.Digest;
import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.actions.ActionInput;
import com.google.devtools.build.lib.actions.ActionInputHelper;
import com.google.devtools.build.lib.actions.FileArtifactValue;
import com.google.devtools.build.lib.actions.MetadataProvider;
import com.google.devtools.build.lib.actions.cache.VirtualActionInput;
import com.google.devtools.build.lib.remote.merkletree.DirectoryTree.DirectoryNode;
import com.google.devtools.build.lib.remote.merkletree.DirectoryTree.FileNode;
import com.google.devtools.build.lib.remote.util.DigestUtil;
import com.google.devtools.build.lib.vfs.Dirent;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.vfs.Symlinks;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Builder for directory trees. */
class DirectoryTreeBuilder {

  static DirectoryTree fromActionInputs(
      SortedMap<PathFragment, ActionInput> inputs,
      MetadataProvider metadataProvider,
      Path execRoot,
      DigestUtil digestUtil)
      throws IOException {
    Map<PathFragment, DirectoryNode> tree = new HashMap<>();
    int numFiles = fromActionInputs(inputs, metadataProvider, execRoot, digestUtil, tree);
    return new DirectoryTree(tree, numFiles);
  }

  private static int fromActionInputs(
      SortedMap<PathFragment, ActionInput> inputs,
      MetadataProvider metadataProvider,
      Path execRoot,
      DigestUtil digestUtil,
      Map<PathFragment, DirectoryNode> tree)
      throws IOException {
    if (inputs.isEmpty()) {
      return 0;
    }

    PathFragment dirname = null;
    DirectoryNode dir = null;
    int numFiles = inputs.size();
    for (Map.Entry<PathFragment, ActionInput> e : inputs.entrySet()) {
      // Path relative to the exec root
      PathFragment path = e.getKey();
      ActionInput input = e.getValue();
      if (dirname == null || !path.getParentDirectory().equals(dirname)) {
        dirname = path.getParentDirectory();
        dir = tree.get(dirname);
        if (dir == null) {
          dir = new DirectoryNode(dirname.getBaseName());
          tree.put(dirname, dir);
          createParentDirectoriesIfNotExist(dirname, dir, tree);
        }
      }

      if (input instanceof VirtualActionInput) {
        VirtualActionInput virtualActionInput = (VirtualActionInput) input;
        Digest d = digestUtil.compute(virtualActionInput);
        dir.addChild(new FileNode(path.getBaseName(), virtualActionInput.getBytes(), d));
        continue;
      }

      FileArtifactValue metadata =
          Preconditions.checkNotNull(
              metadataProvider.getMetadata(input),
              "missing metadata for '%s'",
              input.getExecPathString());
      switch (metadata.getType()) {
        case REGULAR_FILE:
          Digest d = DigestUtil.buildDigest(metadata.getDigest(), metadata.getSize());
          dir.addChild(
              new FileNode(path.getBaseName(), ActionInputHelper.toInputPath(input, execRoot), d));
          break;

        case DIRECTORY:
          SortedMap<PathFragment, ActionInput> directoryInputs = explodeDirectory(path, execRoot);
          numFiles +=
              fromActionInputs(directoryInputs, metadataProvider, execRoot, digestUtil, tree);
          break;

        case SYMLINK:
          throw new IllegalStateException(
              String.format(
                  "Encountered symlink input '%s', but all"
                      + " symlinks should have been resolved by SkyFrame. This is a bug.",
                  path));

        case SPECIAL_FILE:
          throw new IOException(
              String.format(
                  "The '%s' is a special input which is not supported"
                      + " by remote caching and execution.",
                  path));

        case NONEXISTENT:
          throw new IOException(String.format("The file type of '%s' is not supported.", path));
      }
    }
    return numFiles;
  }

  private static SortedMap<PathFragment, ActionInput> explodeDirectory(
      PathFragment dirname, Path execRoot) throws IOException {
    SortedMap<PathFragment, ActionInput> inputs = new TreeMap<>();
    explodeDirectory(dirname, inputs, execRoot);
    return inputs;
  }

  private static void explodeDirectory(
      PathFragment dirname, SortedMap<PathFragment, ActionInput> inputs, Path execRoot)
      throws IOException {
    Collection<Dirent> entries = execRoot.getRelative(dirname).readdir(Symlinks.FOLLOW);
    for (Dirent entry : entries) {
      String basename = entry.getName();
      PathFragment path = dirname.getChild(basename);
      switch (entry.getType()) {
        case FILE:
          inputs.put(path, ActionInputHelper.fromPath(path));
          break;

        case DIRECTORY:
          explodeDirectory(path, inputs, execRoot);
          break;

        case SYMLINK:
          throw new IllegalStateException(
              String.format(
                  "Encountered symlink input '%s', but all"
                      + " symlinks should have been resolved by readdir. This is a bug.",
                  path));

        case UNKNOWN:
          throw new IOException(String.format("The file type of '%s' is not supported.", path));
      }
    }
  }

  private static void createParentDirectoriesIfNotExist(
      PathFragment dirname, DirectoryNode dir, Map<PathFragment, DirectoryNode> tree) {
    PathFragment parentDirname = dirname.getParentDirectory();
    DirectoryNode prevDir = dir;
    while (parentDirname != null) {
      DirectoryNode parentDir = tree.get(parentDirname);
      if (parentDir != null) {
        parentDir.addChild(prevDir);
        break;
      }

      parentDir = new DirectoryNode(parentDirname.getBaseName());
      parentDir.addChild(prevDir);
      tree.put(parentDirname, parentDir);

      parentDirname = parentDirname.getParentDirectory();
      prevDir = parentDir;
    }
  }

  private DirectoryTreeBuilder() {}
}
