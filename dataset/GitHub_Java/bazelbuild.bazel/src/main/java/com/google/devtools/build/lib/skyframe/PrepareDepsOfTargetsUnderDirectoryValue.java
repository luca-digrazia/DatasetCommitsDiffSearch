// Copyright 2015 Google Inc. All rights reserved.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.lib.pkgcache.FilteringPolicies;
import com.google.devtools.build.lib.pkgcache.FilteringPolicy;
import com.google.devtools.build.lib.skyframe.RecursivePkgValue.RecursivePkgKey;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.vfs.RootedPath;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

import java.io.Serializable;
import java.util.Objects;

/**
 * The value computed by {@link PrepareDepsOfTargetsUnderDirectoryFunction}. Contains a mapping for
 * all its non-excluded directories to the count of packages beneath them.
 *
 * <p>This value is used by {@link GraphBackedRecursivePackageProvider#getPackagesUnderDirectory}
 * to help it traverse the graph and find the set of packages under a directory, and recursively by
 * {@link PrepareDepsOfTargetsUnderDirectoryFunction} which computes a value for a directory by
 * aggregating results calculated from its subdirectories.
 *
 * <p>Note that even though the {@link PrepareDepsOfTargetsUnderDirectoryFunction} is evaluated in
 * part because of its side-effects (i.e. loading transitive dependencies of targets), this value
 * interacts safely with change pruning, despite the fact that this value is a lossy representation
 * of the packages beneath a directory (i.e. it doesn't care <b>which</b> packages are under a
 * directory, just the count of them). When the targets in a package change, the
 * {@link PackageValue} that {@link PrepareDepsOfTargetsUnderDirectoryFunction} depends on will be
 * invalidated, and the PrepareDeps function for that package's directory will be reevaluated,
 * loading any new transitive dependencies. Change pruning may prevent the reevaluation of
 * PrepareDeps for directories above that one, but they don't need to be re-run.
 */
public final class PrepareDepsOfTargetsUnderDirectoryValue implements SkyValue {
  public static final PrepareDepsOfTargetsUnderDirectoryValue EMPTY =
      new PrepareDepsOfTargetsUnderDirectoryValue(false, ImmutableMap.<RootedPath, Integer>of());

  private final boolean isDirectoryPackage;
  private final ImmutableMap<RootedPath, Integer> subdirectoryPackageCount;

  public PrepareDepsOfTargetsUnderDirectoryValue(boolean isDirectoryPackage,
      ImmutableMap<RootedPath, Integer> subdirectoryPackageCount) {
    this.isDirectoryPackage = isDirectoryPackage;
    this.subdirectoryPackageCount = Preconditions.checkNotNull(subdirectoryPackageCount);
  }

  /** Whether the directory is a package (i.e. contains a BUILD file). */
  public boolean isDirectoryPackage() {
    return isDirectoryPackage;
  }

  /**
   * Returns a map from non-excluded immediate subdirectories of this directory to the number of
   * non-excluded packages under them.
   */
  public ImmutableMap<RootedPath, Integer> getSubdirectoryPackageCount() {
    return subdirectoryPackageCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PrepareDepsOfTargetsUnderDirectoryValue)) {
      return false;
    }
    PrepareDepsOfTargetsUnderDirectoryValue that = (PrepareDepsOfTargetsUnderDirectoryValue) o;
    return isDirectoryPackage == that.isDirectoryPackage
        && Objects.equals(subdirectoryPackageCount, that.subdirectoryPackageCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isDirectoryPackage, subdirectoryPackageCount);
  }

  /** Create a prepare deps of targets under directory request. */
  @ThreadSafe
  public static SkyKey key(RootedPath rootedPath, ImmutableSet<PathFragment> excludedPaths) {
    return key(rootedPath, excludedPaths, FilteringPolicies.NO_FILTER);
  }

  /**
   * Create a prepare deps of targets under directory request, specifying a filtering policy for
   * targets.
   */
  @ThreadSafe
  public static SkyKey key(RootedPath rootedPath, ImmutableSet<PathFragment> excludedPaths,
      FilteringPolicy filteringPolicy) {
    return new SkyKey(SkyFunctions.PREPARE_DEPS_OF_TARGETS_UNDER_DIRECTORY,
        new PrepareDepsOfTargetsUnderDirectoryKey(new RecursivePkgKey(rootedPath, excludedPaths),
            filteringPolicy));
  }

  /**
   * The argument value for {@link SkyKey}s of {@link PrepareDepsOfTargetsUnderDirectoryFunction}.
   */
  public static final class PrepareDepsOfTargetsUnderDirectoryKey implements Serializable {
    private final RecursivePkgKey recursivePkgKey;
    private final FilteringPolicy filteringPolicy;

    private PrepareDepsOfTargetsUnderDirectoryKey(RecursivePkgKey recursivePkgKey,
        FilteringPolicy filteringPolicy) {
      this.recursivePkgKey = Preconditions.checkNotNull(recursivePkgKey);
      this.filteringPolicy = Preconditions.checkNotNull(filteringPolicy);
    }

    public RecursivePkgKey getRecursivePkgKey() {
      return recursivePkgKey;
    }

    public FilteringPolicy getFilteringPolicy() {
      return filteringPolicy;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof PrepareDepsOfTargetsUnderDirectoryKey)) {
        return false;
      }

      PrepareDepsOfTargetsUnderDirectoryKey that = (PrepareDepsOfTargetsUnderDirectoryKey) o;
      return Objects.equals(recursivePkgKey, that.recursivePkgKey)
          && Objects.equals(filteringPolicy, that.filteringPolicy);
    }

    @Override
    public int hashCode() {
      return Objects.hash(recursivePkgKey, filteringPolicy);
    }
  }
}
