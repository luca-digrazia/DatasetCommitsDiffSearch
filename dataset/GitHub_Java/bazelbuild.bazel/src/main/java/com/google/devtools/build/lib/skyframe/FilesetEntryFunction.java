// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.skyframe;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.actions.FilesetOutputSymlink;
import com.google.devtools.build.lib.actions.FilesetTraversalParams;
import com.google.devtools.build.lib.actions.FilesetTraversalParams.DirectTraversal;
import com.google.devtools.build.lib.skyframe.RecursiveFilesystemTraversalFunction.DanglingSymlinkException;
import com.google.devtools.build.lib.skyframe.RecursiveFilesystemTraversalFunction.RecursiveFilesystemTraversalException;
import com.google.devtools.build.lib.skyframe.RecursiveFilesystemTraversalValue.ResolvedFile;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionException;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/** SkyFunction for {@link FilesetEntryValue}. */
public final class FilesetEntryFunction implements SkyFunction {

  private static final class MissingDepException extends Exception {}

  private static final class FilesetEntryFunctionException extends SkyFunctionException {
    FilesetEntryFunctionException(RecursiveFilesystemTraversalException e) {
      super(e, Transience.PERSISTENT);
    }
  }

  @Override
  public SkyValue compute(SkyKey key, Environment env) throws FilesetEntryFunctionException {
    FilesetTraversalParams t = (FilesetTraversalParams) key.argument();
    Preconditions.checkState(
        t.getNestedTraversal().isPresent() != t.getDirectTraversal().isPresent(),
        "Exactly one of the nested and direct traversals must be specified: %s", t);

    // Create the set of excluded files. Only top-level files can be excluded, i.e. ones that are
    // directly under the root if the root is a directory.
    Set<String> exclusions = createExclusionSet(t.getExcludedFiles());

    // The map of output symlinks. Each key is the path of a output symlink that the Fileset must
    // create, relative to the Fileset.out directory, and each value specifies extra information
    // about the link (its target, associated metadata and again its name).
    Map<PathFragment, FilesetOutputSymlink> outputSymlinks = new LinkedHashMap<>();

    if (t.getNestedTraversal().isPresent()) {
      // The "nested" traversal parameters are present if and only if FilesetEntry.srcdir specifies
      // another Fileset (a "nested" one).
      FilesetEntryValue nested = (FilesetEntryValue) env.getValue(
          FilesetEntryValue.key(t.getNestedTraversal().get()));
      if (env.valuesMissing()) {
        return null;
      }

      for (FilesetOutputSymlink s : nested.getSymlinks()) {
        maybeStoreSymlink(s, t.getDestPath(), exclusions, outputSymlinks);
      }
    } else {
      // The "nested" traversal params are absent if and only if the "direct" traversal params are
      // present, which is the case when the FilesetEntry specifies a package's BUILD file, a
      // directory or a list of files.

      // The root of the direct traversal is defined as follows.
      //
      // If FilesetEntry.files is specified, then a TraversalRequest is created for each entry, the
      // root being the respective entry itself. These are all traversed for they may be
      // directories or symlinks to directories, and we need to establish Skyframe dependencies on
      // their contents for incremental correctness. If an entry is indeed a directory (but not when
      // it's a symlink to one) then we have to create symlinks to each of their childen.
      // (NB: there seems to be no good reason for this, it's just how legacy Fileset works. We may
      // want to consider creating a symlink just for the directory and not for its child elements.)
      //
      // If FilesetEntry.files is not specified, then srcdir refers to either a BUILD file or a
      // directory. For the former, the root will be the parent of the BUILD file. For the latter,
      // the root will be srcdir itself.
      DirectTraversal direct = t.getDirectTraversal().get();

      RecursiveFilesystemTraversalValue rftv;
      try {
        // Traverse the filesystem to establish skyframe dependencies.
        rftv = traverse(env, createErrorInfo(t), direct);
      } catch (MissingDepException e) {
        return null;
      }

      // The root can only be absent for the EMPTY rftv instance.
      if (!rftv.getResolvedRoot().isPresent()) {
        return FilesetEntryValue.EMPTY;
      }

      ResolvedFile resolvedRoot = rftv.getResolvedRoot().get();

      // Handle dangling symlinks gracefully be returning empty results.
      if (!resolvedRoot.type.exists()) {
        return FilesetEntryValue.EMPTY;
      }

      // The prefix to remove is the entire path of the root. This is OK:
      // - when the root is a file, this removes the entire path, but the traversal's destination
      //   path is actually the name of the output symlink, so this works out correctly
      // - when the root is a directory or a symlink to one then we need to strip off the
      //   directory's path from every result (this is how the output symlinks must be created)
      //   before making them relative to the destination path
      PathFragment prefixToRemove = direct.getRoot().getRelativePart();

      Iterable<ResolvedFile> results = null;

      if (direct.isRecursive()
          || (resolvedRoot.type.isDirectory() && !resolvedRoot.type.isSymlink())) {
        // The traversal is recursive (requested for an entire FilesetEntry.srcdir) or it was
        // requested for a FilesetEntry.files entry which turned out to be a directory. We need to
        // create an output symlink for every file in it and all of its subdirectories. Only
        // exception is when the subdirectory is really a symlink to a directory -- no output
        // shall be created for the contents of those.
        // Now we create Dir objects to model the filesystem tree. The object employs a trick to
        // find directory symlinks: directory symlinks have corresponding ResolvedFile entries and
        // are added as files too, while their children, also added as files, contain the path of
        // the parent. Finding and discarding the children is easy if we traverse the tree from
        // root to leaf.
        DirectoryTree root = new DirectoryTree();
        for (ResolvedFile f : rftv.getTransitiveFiles().toCollection()) {
          PathFragment path = f.getNameInSymlinkTree().relativeTo(prefixToRemove);
          if (path.segmentCount() > 0) {
            path = t.getDestPath().getRelative(path);
            DirectoryTree dir = root;
            for (int i = 0; i < path.segmentCount() - 1; ++i) {
              dir = dir.addOrGetSubdir(path.getSegment(i));
            }
            dir.maybeAddFile(f);
          }
        }
        // Here's where the magic happens. The returned iterable will yield all files in the
        // directory that are not under symlinked directories, as well as all directory symlinks.
        results = root.iterateFiles();
      } else {
        // If we're on this branch then the traversal was done for just one entry in
        // FilesetEntry.files (which was not a directory, so it was either a file, a symlink to one
        // or a symlink to a directory), meaning we'll have only one output symlink.
        results = ImmutableList.of(resolvedRoot);
      }

      // Create one output symlink for each entry in the results.
      for (ResolvedFile f : results) {
        PathFragment targetName;
        try {
          targetName = f.getTargetInSymlinkTree(direct.isFollowingSymlinks());
        } catch (DanglingSymlinkException e) {
          throw new FilesetEntryFunctionException(e);
        }

        // Metadata field must be present. It can only be absent when stripped by tests.
        String metadata = Integer.toHexString(f.metadata.get().hashCode());

        // The linkName has to be under the traversal's root, which is also the prefix to remove.
        PathFragment linkName = f.getNameInSymlinkTree().relativeTo(prefixToRemove);
        maybeStoreSymlink(linkName, targetName, metadata, t.getDestPath(), exclusions,
            outputSymlinks);
      }
    }

    return FilesetEntryValue.of(ImmutableSet.copyOf(outputSymlinks.values()));
  }

  /** Stores an output symlink unless it's excluded or would overwrite an existing one. */
  private static void maybeStoreSymlink(FilesetOutputSymlink nestedLink, PathFragment destPath,
      Set<String> exclusions, Map<PathFragment, FilesetOutputSymlink> result) {
    maybeStoreSymlink(nestedLink.name, nestedLink.target, nestedLink.metadata, destPath,
        exclusions, result);
  }

  /** Stores an output symlink unless it's excluded or would overwrite an existing one. */
  private static void maybeStoreSymlink(PathFragment linkName, PathFragment linkTarget,
      String metadata, PathFragment destPath, Set<String> exclusions,
      Map<PathFragment, FilesetOutputSymlink> result) {
    if (!exclusions.contains(linkName.getPathString())) {
      linkName = destPath.getRelative(linkName);
      if (!result.containsKey(linkName)) {
        result.put(linkName, new FilesetOutputSymlink(linkName, linkTarget, metadata));
      }
    }
  }

  private static Set<String> createExclusionSet(Set<String> input) {
    return Sets.filter(input, new Predicate<String>() {
      @Override
      public boolean apply(String e) {
        // Keep the top-level exclusions only. Do not look for "/" but count the path segments
        // instead, in anticipation of future Windows support.
        return new PathFragment(e).segmentCount() == 1;
      }
    });
  }

  @Override
  public String extractTag(SkyKey skyKey) {
    return null;
  }

  private RecursiveFilesystemTraversalValue traverse(Environment env, String errorInfo,
      DirectTraversal traversal) throws MissingDepException {
    SkyKey depKey = RecursiveFilesystemTraversalValue.key(
        new RecursiveFilesystemTraversalValue.TraversalRequest(traversal.getRoot().asRootedPath(),
            traversal.isGenerated(), traversal.getCrossPackageBoundary(), traversal.isPackage(),
            errorInfo));
    RecursiveFilesystemTraversalValue v = (RecursiveFilesystemTraversalValue) env.getValue(depKey);
    if (env.valuesMissing()) {
      throw new MissingDepException();
    }
    return v;
  }

  private static String createErrorInfo(FilesetTraversalParams t) {
    if (t.getDirectTraversal().isPresent()) {
      DirectTraversal direct = t.getDirectTraversal().get();
      return String.format("Fileset '%s' traversing %s %s", t.getOwnerLabel(),
          direct.isPackage() ? "package" : "file (or directory)",
          direct.getRoot().getRelativePart().getPathString());
    } else {
      return String.format("Fileset '%s' traversing another Fileset", t.getOwnerLabel());
    }
  }

  /**
   * Models a FilesetEntryFunction's portion of the symlink output tree created by a Fileset rule.
   *
   * <p>A Fileset rule's output is computed by zero or more {@link FilesetEntryFunction}s, resulting
   * in one {@link FilesetEntryValue} for each. Each of those represents a portion of the grand
   * output tree of the Fileset. These portions are later merged and written to the fileset manifest
   * file, which is then consumed by a tool that ultimately creates the symlinks in the filesystem.
   *
   * <p>Because the Fileset doesn't process the lists in the FilesetEntryValues any further than
   * merging them, they have to adhere to the conventions of the manifest file. One of these is that
   * files are alphabetically ordered (enables the consumer of the manifest to work faster than
   * otherwise) and another is that the contents of regular directories are listed, but contents
   * of directory symlinks are not, only the symlinks are. (Other details of the manifest file are
   * not relevant here.)
   *
   * <p>See {@link DirectoryTree#iterateFiles()} for more details.
   */
  private static final class DirectoryTree {
    // Use TreeMaps for the benefit of alphabetically ordered iteration.
    public final Map<String, ResolvedFile> files = new TreeMap<>();
    public final Map<String, DirectoryTree> dirs = new TreeMap<>();

    DirectoryTree addOrGetSubdir(String name) {
      DirectoryTree result = dirs.get(name);
      if (result == null) {
        result = new DirectoryTree();
        dirs.put(name, result);
      }
      return result;
    }

    void maybeAddFile(ResolvedFile r) {
      String name = r.getNameInSymlinkTree().getBaseName();
      if (!files.containsKey(name)) {
        files.put(name, r);
      }
    }

    /**
     * Lazily yields all files in this directory and all of its subdirectories.
     *
     * <p>The function first yields all the files directly under this directory, in alphabetical
     * order. Then come the contents of subdirectories, processed recursively in the same fashion
     * as this directory, and also in alphabetical order.
     *
     * <p>If a directory symlink is encountered its contents are not listed, only the symlink is.
     */
    Iterable<ResolvedFile> iterateFiles() {
      // 1. Filter directory symlinks. If the symlink target contains files, those were added
      // as normal files so their parent directory (the symlink) would show up in the dirs map
      // (as a directory) as well as in the files map (as a symlink to a directory).
      final Set<String> fileNames = files.keySet();
      Iterable<Map.Entry<String, DirectoryTree>> noDirSymlinkes = Iterables.filter(dirs.entrySet(),
          new Predicate<Map.Entry<String, DirectoryTree>>() {
            @Override
            public boolean apply(Map.Entry<String, DirectoryTree> input) {
              return !fileNames.contains(input.getKey());
            }
          });

      // 2. Extract the iterables of the true subdirectories.
      Iterable<Iterable<ResolvedFile>> subdirIters = Iterables.transform(noDirSymlinkes,
          new Function<Map.Entry<String, DirectoryTree>, Iterable<ResolvedFile>>() {
            @Override
            public Iterable<ResolvedFile> apply(Entry<String, DirectoryTree> input) {
              return input.getValue().iterateFiles();
            }
          });

      // 3. Just concat all subdirectory iterations for one, seamless iteration.
      Iterable<ResolvedFile> dirsIter = Iterables.concat(subdirIters);

      return Iterables.concat(files.values(), dirsIter);
    }
  }
}
