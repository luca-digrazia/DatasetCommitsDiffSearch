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
package com.google.devtools.build.lib.rules.android;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.AttributeMap;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * The collected resources and assets artifacts and roots.
 *
 * <p>This is used to encapsulate the logic and the data associated with the resources and assets
 * derived from an appropriate android rule in a reusable instance.
 */
@Immutable
public final class LocalResourceContainer {
  public static final String[] RESOURCES_ATTRIBUTES = new String[] {
      "manifest",
      "resource_files",
      "assets",
      "assets_dir",
      "inline_constants",
      "exports_manifest",
      "application_id",
      "version_name",
      "version_code"
  };

  /**
   * Determines if the attributes contain resource and asset attributes.
   */
  public static boolean definesAndroidResources(AttributeMap attributes) {
    for (String attribute : RESOURCES_ATTRIBUTES) {
      if (attributes.isAttributeValueExplicitlySpecified(attribute)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks validity of a RuleContext to produce an AndroidData.
   */
  public static boolean validateRuleContext(RuleContext ruleContext) {
    boolean valid = validateAssetsAndAssetsDir(ruleContext);
    valid = valid && validateNoResourcesAttribute(ruleContext);
    valid = valid && validateNoAndroidResourcesInSources(ruleContext);
    valid = valid && validateManifest(ruleContext);
    return valid;
  }

  private static boolean validateAssetsAndAssetsDir(RuleContext ruleContext) {
    if (ruleContext.attributes().isAttributeValueExplicitlySpecified("assets")
        ^ ruleContext.attributes().isAttributeValueExplicitlySpecified("assets_dir")) {
      ruleContext.ruleError(
          "'assets' and 'assets_dir' should be either both empty or both non-empty");
      return false;
    }
    return true;
  }

  /**
   * Validates that there are no resources defined if there are resource attribute defined.
   */
  private static boolean validateNoResourcesAttribute(RuleContext ruleContext) {
    if (ruleContext.attributes().isAttributeValueExplicitlySpecified("resources")) {
      ruleContext.attributeError("resources",
          String.format("cannot be set when any of %s are defined.",
              Joiner.on(", ").join(RESOURCES_ATTRIBUTES)));
      return false;
    }
    return true;
  }

  /**
   * Validates that there are no android_resources srcjars in the srcs, as android_resource rules
   * should not be used with the Android data logic.
   */
  private static boolean validateNoAndroidResourcesInSources(RuleContext ruleContext) {
    Iterable<AndroidResourcesProvider> resources =
        ruleContext.getPrerequisites("srcs", Mode.TARGET, AndroidResourcesProvider.class);
    for (AndroidResourcesProvider provider : resources) {
      ruleContext.attributeError("srcs",
          String.format("should not contain android_resource label %s", provider.getLabel()));
      return false;
    }
    return true;
  }

  private static boolean validateManifest(RuleContext ruleContext) {
    if (ruleContext.getPrerequisiteArtifact("manifest", Mode.TARGET) == null) {
      ruleContext.attributeError("manifest",
          "is required when resource_files or assets are defined.");
      return false;
    }
    return true;
  }

  public static class Builder {
    public static final class InvalidAssetPath extends RuntimeException {
      InvalidAssetPath(String message) {
        super(message);
      }
    }

    public static final class InvalidResourcePath extends RuntimeException {
      InvalidResourcePath(String message) {
        super(message);
      }
    }

    /**
     * Set of allowable android directories prefixes.
     */
    // TODO(bazel-team): Pull from AOSP constant?
    public static final ImmutableSet<String> RESOURCE_DIRECTORY_TYPES = ImmutableSet.of(
        "animator",
        "anim",
        "color",
        "drawable",
        "interpolator",
        "layout",
        "menu",
        "mipmap",
        "raw",
        "transition",
        "values",
        "xml");

    public static final String INCORRECT_RESOURCE_LAYOUT_MESSAGE = String.format(
        "'%%s' is not in the expected resource directory structure of "
        + "<resource directory>/{%s}/<file>", Joiner.on(',').join(RESOURCE_DIRECTORY_TYPES));

    private Collection<Artifact> assets = new LinkedHashSet<>();
    private Collection<Artifact> resources = new LinkedHashSet<>();
    private Collection<PathFragment> resourceRoots = new LinkedHashSet<>();
    private Collection<PathFragment> assetRoots = new LinkedHashSet<>();

    /**
     * Retrieves the asset {@link Artifact} and asset root {@link PathFragment}.
     * @param assetsDir The common directory for the assets, used to get the directory roots and
     *   verify the artifacts are located beneath the assetsDir
     * @param targets {@link FileProvider}s for a given set of assets.
     * @return The Builder.
     * @throws InvalidAssetPath when a path does not reside under the correct directory.
     */
    public LocalResourceContainer.Builder withAssets(
        PathFragment assetsDir, Iterable<FileProvider> targets) {
      for (FileProvider target : targets) {
        for (Artifact file : target.getFilesToBuild()) {
          PathFragment packageFragment = file.getArtifactOwner().getLabel().getPackageFragment();
          PathFragment packageRelativePath =
              file.getRootRelativePath().relativeTo(packageFragment);
          if (packageRelativePath.startsWith(assetsDir)) {
            PathFragment relativePath = packageRelativePath.relativeTo(assetsDir);
            assetRoots.add(trimTail(file.getExecPath(), relativePath));
          } else {
            throw new InvalidAssetPath(String.format(
                "'%s' (generated by '%s') is not beneath '%s'",
                file.getRootRelativePath(), target.getLabel(), assetsDir));
          }
          assets.add(file);
        }
      }
      return this;
    }

    /**
     * Retrieves the resource {@link Artifact} and resource root {@link PathFragment}.
     * @param targets {@link FileProvider}s for a given set of resource.
     * @return The Builder.
     * @throws InvalidResourcePath when a path does not reside under the correct directory.
     */
    public LocalResourceContainer.Builder withResources(Iterable<FileProvider> targets) {
      PathFragment lastResourceDir = null;
      Artifact lastFile = null;
      for (FileProvider target : targets) {
        for (Artifact file : target.getFilesToBuild()) {
          PathFragment packageFragment = file.getArtifactOwner().getLabel().getPackageFragment();
          PathFragment packageRelativePath =
              file.getRootRelativePath().relativeTo(packageFragment);
          PathFragment resourceDir = findResourceDir(file);
          if (lastResourceDir == null || resourceDir.equals(lastResourceDir)) {
            resourceRoots.add(
                trimTail(file.getExecPath(), makeRelativeTo(resourceDir, packageRelativePath)));
          } else {
            throw new InvalidResourcePath(String.format(
                "'%s' (generated by '%s') is not in the same directory '%s' (derived from %s)."
                + " All resources must share a common directory.",
                file.getRootRelativePath(), file.getArtifactOwner().getLabel(), lastResourceDir,
                lastFile.getRootRelativePath()));
          }
          resources.add(file);
          lastFile = file;
          lastResourceDir = resourceDir;
        }
      }
      return this;
    }

    /**
     * Finds and validates the resource directory PathFragment from the artifact Path.
     *
     * <p>If the artifact is not a Fileset, the resource directory is presumed to be the second
     * directory from the end. Filesets are expect to have the last directory as the resource
     * directory.
     *
     */
    public static PathFragment findResourceDir(Artifact artifact) {
      PathFragment fragment = artifact.getPath().asFragment();
      int segmentCount = fragment.segmentCount();
      if (segmentCount < 3) {
        throw new InvalidResourcePath(String.format(
            INCORRECT_RESOURCE_LAYOUT_MESSAGE, artifact.getRootRelativePath()));
      }
      // TODO(bazel-team): Expand Fileset to verify, or remove Fileset as an option for resources.
      if (artifact.isFileset()) {
        return fragment.subFragment(segmentCount - 1, segmentCount);
      }

      // Check the resource folder type layout.
      // get the prefix of the parent folder of the fragment.
      String parentDirectory = fragment.getSegment(segmentCount - 2);
      int dashIndex = parentDirectory.indexOf('-');
      String androidFolder =
          dashIndex == -1 ? parentDirectory : parentDirectory.substring(0, dashIndex);
      if (!RESOURCE_DIRECTORY_TYPES.contains(androidFolder)) {
        throw new InvalidResourcePath(String.format(
            INCORRECT_RESOURCE_LAYOUT_MESSAGE, artifact.getRootRelativePath()));
      }

      return fragment.subFragment(segmentCount - 3, segmentCount - 2);
    }

    /**
     * Returns the root-part of a given path by trimming off the end specified by
     * a given tail. Assumes that the tail is known to match, and simply relies on
     * the segment lengths.
     */
    private static PathFragment trimTail(PathFragment path, PathFragment tail) {
      return path.subFragment(0, path.segmentCount() - tail.segmentCount());
    }

    private static PathFragment makeRelativeTo(PathFragment ancestor, PathFragment path) {
      String cutAtSegment = ancestor.getSegment(ancestor.segmentCount() - 1);
      int totalPathSegments = path.segmentCount() - 1;
      for (int i = totalPathSegments; i >= 0; i--) {
        if (path.getSegment(i).equals(cutAtSegment)) {
          return path.subFragment(i, totalPathSegments);
        }
      }
      throw new IllegalArgumentException("PathFragment " + path
          + " is not beneath " + ancestor);
    }

    public LocalResourceContainer build() {
      return new LocalResourceContainer(
          ImmutableList.copyOf(resources),
          ImmutableList.copyOf(resourceRoots),
          ImmutableList.copyOf(assets),
          ImmutableList.copyOf(assetRoots));
    }
  }

  private final ImmutableList<Artifact> resources;
  private final ImmutableList<Artifact> assets;
  private final ImmutableList<PathFragment> assetRoots;
  private final ImmutableList<PathFragment> resourceRoots;

  @VisibleForTesting
  public LocalResourceContainer(
      ImmutableList<Artifact> resources,
      ImmutableList<PathFragment> resourceRoots,
      ImmutableList<Artifact> assets,
      ImmutableList<PathFragment> assetRoots) {
        this.resources = resources;
        this.resourceRoots = resourceRoots;
        this.assets = assets;
        this.assetRoots = assetRoots;
  }

  public ImmutableList<Artifact> getResources() {
    return resources;
  }

  public ImmutableList<Artifact> getAssets() {
    return assets;
  }

  public ImmutableList<PathFragment> getAssetRoots() {
    return assetRoots;
  }

  public ImmutableList<PathFragment> getResourceRoots() {
    return resourceRoots;
  }
}
