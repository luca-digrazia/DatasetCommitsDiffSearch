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

import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.skyframe.serialization.InjectingObjectCodecAdapter;
import com.google.devtools.build.lib.skyframe.serialization.ObjectCodec;
import com.google.devtools.build.lib.skyframe.serialization.SerializationException;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

/**
 * A {@link PathFragment} relative to a {@link Root}. Typically the root will be a package path
 * entry.
 *
 * <p>Two {@link RootedPath}s are considered equal iff they have equal roots and equal relative
 * paths.
 *
 * <p>TODO(bazel-team): refactor Artifact to use this instead of Root. TODO(bazel-team): use an
 * opaque root representation so as to not expose the absolute path to clients via #asPath or
 * #getRoot.
 */
public class RootedPath implements Serializable {

  private final Root root;
  private final PathFragment rootRelativePath;
  private final Path path;

  /** Constructs a {@link RootedPath} from a {@link Root} and path fragment relative to the root. */
  private RootedPath(Root root, PathFragment rootRelativePath) {
    Preconditions.checkState(
        rootRelativePath.isAbsolute() == root.isAbsolute(),
        "rootRelativePath: %s root: %s",
        rootRelativePath,
        root);
    this.root = root;
    this.rootRelativePath = rootRelativePath.normalize();
    this.path = root.getRelative(this.rootRelativePath);
  }

  /** Returns a rooted path representing {@code rootRelativePath} relative to {@code root}. */
  public static RootedPath toRootedPath(Root root, PathFragment rootRelativePath) {
    if (rootRelativePath.isAbsolute()) {
      if (root.isAbsolute()) {
        return new RootedPath(root, rootRelativePath);
      } else {
        Preconditions.checkArgument(
            root.contains(rootRelativePath),
            "rootRelativePath '%s' is absolute, but it's not under root '%s'",
            rootRelativePath,
            root);
        return new RootedPath(root, root.relativize(rootRelativePath));
      }
    } else {
      return new RootedPath(root, rootRelativePath);
    }
  }

  /** Returns a rooted path representing {@code path} under the root {@code root}. */
  public static RootedPath toRootedPath(Root root, Path path) {
    Preconditions.checkState(root.contains(path), "path: %s root: %s", path, root);
    return toRootedPath(root, path.asFragment());
  }

  /**
   * Returns a rooted path representing {@code path} under one of the package roots, or under the
   * filesystem root if it's not under any package root.
   */
  public static RootedPath toRootedPathMaybeUnderRoot(Path path, Iterable<Root> packagePathRoots) {
    for (Root root : packagePathRoots) {
      if (root.contains(path)) {
        return toRootedPath(root, path);
      }
    }
    return toRootedPath(Root.absoluteRoot(path.getFileSystem()), path);
  }

  public Path asPath() {
    // Ideally, this helper method would not be needed. But Skyframe's FileFunction and
    // DirectoryListingFunction need to do filesystem operations on the absolute path and
    // Path#getRelative(relPath) is O(relPath.segmentCount()). Therefore we precompute the absolute
    // path represented by this relative path.
    return path;
  }

  public Root getRoot() {
    return root;
  }

  /** Returns the path fragment relative to {@code #getRoot}. */
  public PathFragment getRootRelativePath() {
    return rootRelativePath;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof RootedPath)) {
      return false;
    }
    RootedPath other = (RootedPath) obj;
    return Objects.equals(root, other.root)
        && Objects.equals(rootRelativePath, other.rootRelativePath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(root, rootRelativePath);
  }

  @Override
  public String toString() {
    return "[" + root + "]/[" + rootRelativePath + "]";
  }

  /** Custom serialization for {@link RootedPath}s. */
  public static class RootedPathCodec implements ObjectCodec<RootedPath> {

    private final ObjectCodec<Root> rootCodec;

    /** Create an instance which will deserialize RootedPaths on {@code fileSystem}. */
    public RootedPathCodec(FileSystem fileSystem) {
      this.rootCodec = new InjectingObjectCodecAdapter<>(Root.CODEC, () -> fileSystem);
    }

    @Override
    public Class<RootedPath> getEncodedClass() {
      return RootedPath.class;
    }

    @Override
    public void serialize(RootedPath rootedPath, CodedOutputStream codedOut)
        throws IOException, SerializationException {
      rootCodec.serialize(rootedPath.getRoot(), codedOut);
      PathFragment.CODEC.serialize(rootedPath.getRootRelativePath(), codedOut);
    }

    @Override
    public RootedPath deserialize(CodedInputStream codedIn)
        throws IOException, SerializationException {
      Root root = rootCodec.deserialize(codedIn);
      PathFragment rootRelativePath = PathFragment.CODEC.deserialize(codedIn);
      return toRootedPath(root, rootRelativePath);
    }
  }
}
