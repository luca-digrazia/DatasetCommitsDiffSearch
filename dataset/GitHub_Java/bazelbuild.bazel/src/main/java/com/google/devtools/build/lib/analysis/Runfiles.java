// Copyright 2014 Google Inc. All rights reserved.
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

package com.google.devtools.build.lib.analysis;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.Constants;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.Runfiles.Builder;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An object that encapsulates runfiles. Conceptually, the runfiles are a map of paths to files,
 * forming a symlink tree.
 *
 * <p>In order to reduce memory consumption, this map is not explicitly stored here, but instead as
 * a combination of four parts: artifacts placed at their root-relative paths, source tree symlinks,
 * root symlinks (outside of the source tree), and artifacts included as parts of "pruning
 * manifests" (see {@link PruningManifest}).
 */
@Immutable
public final class Runfiles {
  private static final Function<Map.Entry<PathFragment, Artifact>, Artifact> TO_ARTIFACT =
      new Function<Map.Entry<PathFragment, Artifact>, Artifact>() {
        @Override
        public Artifact apply(Map.Entry<PathFragment, Artifact> input) {
          return input.getValue();
        }
      };

  private static final Function<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>>
      DUMMY_SYMLINK_EXPANDER =
      new Function<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>>() {
        @Override
        public Map<PathFragment, Artifact> apply(Map<PathFragment, Artifact> input) {
          return ImmutableMap.of();
        }
      };

  // It is important to declare this *after* the DUMMY_SYMLINK_EXPANDER to avoid NPEs
  public static final Runfiles EMPTY = new Builder().build();

  /**
   * The directory to put all runfiles under.
   *
   * <p>Using "foo" will put runfiles under &lt;target&gt;.runfiles/foo.</p>
   */
  private final String suffix;

  /**
   * The artifacts that should *always* be present in the runfiles directory. These are
   * differentiated from the artifacts that may or may not be included by a pruning manifest
   * (see {@link PruningManifest} below).
   *
   * <p>This collection may not include any middlemen. These artifacts will be placed at a location
   * that corresponds to the root-relative path of each artifact. It's possible for several
   * artifacts to have the same root-relative path, in which case the last one will win.
   */
  private final NestedSet<Artifact> unconditionalArtifacts;

  /**
   * A map of symlinks that should be present in the runfiles directory. In general, the symlink can
   * be determined from the artifact by using the root-relative path, so this should only be used
   * for cases where that isn't possible.
   *
   * <p>This may include runfiles symlinks from the root of the runfiles tree.
   */
  private final NestedSet<Map.Entry<PathFragment, Artifact>> symlinks;

  /**
   * A map of symlinks that should be present above the runfiles directory. These are useful for
   * certain rule types like AppEngine apps which have root level config files outside of the
   * regular source tree.
   */
  private final NestedSet<Map.Entry<PathFragment, Artifact>> rootSymlinks;

  /**
   * A function to generate extra manifest entries.
   */
  private final Function<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>>
      manifestExpander;

  /**
   * Defines a set of artifacts that may or may not be included in the runfiles directory and
   * a manifest file that makes that determination. These are applied on top of any artifacts
   * specified in {@link #unconditionalArtifacts}.
   *
   * <p>The incentive behind this is to enable execution-phase "pruning" of runfiles. Anything
   * set in unconditionalArtifacts is hard-set in Blaze's analysis phase, and thus unchangeable in
   * response to execution phase results. This isn't always convenient. For example, say we have an
   * action that consumes a set of "possible" runtime dependencies for a source file, parses that
   * file for "import a.b.c" statements, and outputs a manifest of the actual dependencies that are
   * referenced and thus really needed. This can reduce the size of the runfiles set, but we can't
   * use this information until the manifest output is available.
   *
   * <p>Only artifacts present in the candidate set AND the manifest output make it into the
   * runfiles tree. The candidate set requirement guarantees that analysis-time dependencies are a
   * superset of the pruned dependencies, so undeclared inclusions (which can break build
   * correctness) aren't possible.
   */
  public static class PruningManifest {
    private final NestedSet<Artifact> candidateRunfiles;
    private final Artifact manifestFile;

    /**
     * Creates a new pruning manifest.
     *
     * @param candidateRunfiles set of possible artifacts that the manifest file may reference
     * @param manifestFile the manifest file, expected to be a newline-separated list of
     *     source tree root-relative paths (i.e. "my/package/myfile.txt"). Anything that can't be
     *     resolved back to an entry in candidateRunfiles is ignored and will *not* make it into
     *     the runfiles tree.
     */
    public PruningManifest(NestedSet<Artifact> candidateRunfiles, Artifact manifestFile) {
      this.candidateRunfiles = candidateRunfiles;
      this.manifestFile = manifestFile;
    }

    public NestedSet<Artifact> getCandidateRunfiles() {
      return candidateRunfiles;
    }

    public Artifact getManifestFile() {
      return manifestFile;
    }
  }

  /**
   * The pruning manifests that should be applied to these runfiles.
   */
  private final NestedSet<PruningManifest> pruningManifests;

  private Runfiles(String suffix,
      NestedSet<Artifact> artifacts,
      NestedSet<Map.Entry<PathFragment, Artifact>> symlinks,
      NestedSet<Map.Entry<PathFragment, Artifact>> rootSymlinks,
      NestedSet<PruningManifest> pruningManifests,
      Function<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>> expander) {
    this.suffix = suffix == null ? Constants.RUNFILES_PREFIX : suffix;
    this.unconditionalArtifacts = Preconditions.checkNotNull(artifacts);
    this.symlinks = Preconditions.checkNotNull(symlinks);
    this.rootSymlinks = Preconditions.checkNotNull(rootSymlinks);
    this.pruningManifests = Preconditions.checkNotNull(pruningManifests);
    this.manifestExpander = Preconditions.checkNotNull(expander);
  }

  /**
   * Returns the runfiles' suffix.
   */
  public String getSuffix() {
    return suffix;
  }

  /**
   * Returns the artifacts that are unconditionally included in the runfiles (as opposed to
   * pruning manifest candidates, which may or may not be included).
   */
  @VisibleForTesting
  public NestedSet<Artifact> getUnconditionalArtifacts() {
    return unconditionalArtifacts;
  }

  /**
   * Returns the artifacts that are unconditionally included in the runfiles (as opposed to
   * pruning manifest candidates, which may or may not be included). Middleman artifacts are
   * excluded.
   */
  public Iterable<Artifact> getUnconditionalArtifactsWithoutMiddlemen() {
    return Iterables.filter(unconditionalArtifacts, Artifact.MIDDLEMAN_FILTER);
  }

  /**
   * Returns the collection of runfiles as artifacts, including both unconditional artifacts
   * and pruning manifest candidates.
   */
  @VisibleForTesting
  public NestedSet<Artifact> getArtifacts() {
    NestedSetBuilder<Artifact> allArtifacts = NestedSetBuilder.stableOrder();
    allArtifacts.addAll(unconditionalArtifacts.toCollection());
    for (PruningManifest manifest : getPruningManifests()) {
      allArtifacts.addTransitive(manifest.getCandidateRunfiles());
    }
    return allArtifacts.build();
  }

  /**
   * Returns the collection of runfiles as artifacts, including both unconditional artifacts
   * and pruning manifest candidates. Middleman artifacts are excluded.
   */
  public Iterable<Artifact> getArtifactsWithoutMiddlemen() {
    return Iterables.filter(getArtifacts(), Artifact.MIDDLEMAN_FILTER);
  }

  /**
   * Returns the symlinks.
   */
  public NestedSet<Map.Entry<PathFragment, Artifact>> getSymlinks() {
    return symlinks;
  }

  /**
   * Returns the symlinks as a map from path fragment to artifact.
   */
  public Map<PathFragment, Artifact> getSymlinksAsMap() {
    return entriesToMap(symlinks);
  }

  /**
   * @param eventHandler Used for throwing an error if we have an obscuring runlink.
   *                 May be null, in which case obscuring symlinks are silently discarded.
   * @param location Location for reporter. Ignored if reporter is null.
   * @param workingManifest Manifest to be checked for obscuring symlinks.
   * @return map of source file names mapped to their location on disk.
   */
  public static Map<PathFragment, Artifact> filterListForObscuringSymlinks(
      EventHandler eventHandler, Location location, Map<PathFragment, Artifact> workingManifest) {
    Map<PathFragment, Artifact> newManifest = new HashMap<>();

    outer:
    for (Iterator<Entry<PathFragment, Artifact>> i = workingManifest.entrySet().iterator();
         i.hasNext(); ) {
      Entry<PathFragment, Artifact> entry = i.next();
      PathFragment source = entry.getKey();
      Artifact symlink = entry.getValue();
      // drop nested entries; warn if this changes anything
      int n = source.segmentCount();
      for (int j = 1; j < n; ++j) {
        PathFragment prefix = source.subFragment(0, n - j);
        Artifact ancestor = workingManifest.get(prefix);
        if (ancestor != null) {
          // This is an obscuring symlink, so just drop it and move on if there's no reporter.
          if (eventHandler == null) {
            continue outer;
          }
          PathFragment suffix = source.subFragment(n - j, n);
          Path viaAncestor = ancestor.getPath().getRelative(suffix);
          Path expected = symlink.getPath();
          if (!viaAncestor.equals(expected)) {
            eventHandler.handle(Event.warn(location, "runfiles symlink " + source + " -> "
                + expected + " obscured by " + prefix + " -> " + ancestor.getPath()));
          }
          continue outer;
        }
      }
      newManifest.put(entry.getKey(), entry.getValue());
    }
    return newManifest;
  }

  /**
   * Returns the symlinks as a map from PathFragment to Artifact, with PathFragments relativized
   * and rooted at the specified points.
   * @param root The root the PathFragment is computed relative to (before it is
   *             rooted again). May be null.
   * @param eventHandler Used for throwing an error if we have an obscuring runlink.
   *                 May be null, in which case obscuring symlinks are silently discarded.
   * @param location Location for eventHandler warnings. Ignored if eventHandler is null.
   * @return Pair of Maps from remote path fragment to artifact, the first of normal source tree
   *         entries, the second of any elements that live outside the source tree.
   */
  public Pair<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>> getRunfilesInputs(
      PathFragment root, EventHandler eventHandler, Location location)
          throws IOException {
    Map<PathFragment, Artifact> manifest = getSymlinksAsMap();
    // Add unconditional artifacts (committed to inclusion on construction of runfiles).
    for (Artifact artifact : getUnconditionalArtifactsWithoutMiddlemen()) {
      addToManifest(manifest, artifact, root);
    }

    // Add conditional artifacts (only included if they appear in a pruning manifest).
    for (Runfiles.PruningManifest pruningManifest : getPruningManifests()) {
      // This map helps us convert from source tree root-relative paths back to artifacts.
      Map<String, Artifact> allowedRunfiles = new HashMap<>();
      for (Artifact artifact : pruningManifest.getCandidateRunfiles()) {
        allowedRunfiles.put(artifact.getRootRelativePath().getPathString(), artifact);
      }
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(pruningManifest.getManifestFile().getPath().getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        Artifact artifact = allowedRunfiles.get(line);
        if (artifact != null) {
          addToManifest(manifest, artifact, root);
        }
      }
    }

    manifest = filterListForObscuringSymlinks(eventHandler, location, manifest);
    manifest.putAll(manifestExpander.apply(manifest));
    PathFragment path = new PathFragment(suffix);
    Map<PathFragment, Artifact> result = new HashMap<>();
    for (Map.Entry<PathFragment, Artifact> entry : manifest.entrySet()) {
      result.put(path.getRelative(entry.getKey()), entry.getValue());
    }
    return Pair.of(result, (Map<PathFragment, Artifact>) new HashMap<>(getRootSymlinksAsMap()));
  }

  @VisibleForTesting
  protected static void addToManifest(Map<PathFragment, Artifact> manifest, Artifact artifact,
      PathFragment root) {
    PathFragment rootRelativePath = root != null
        ? artifact.getRootRelativePath().relativeTo(root)
        : artifact.getRootRelativePath();
    manifest.put(rootRelativePath, artifact);
  }

  /**
   * Returns the root symlinks.
   */
  public NestedSet<Map.Entry<PathFragment, Artifact>> getRootSymlinks() {
    return rootSymlinks;
  }

  /**
   * Returns the root symlinks.
   */
  public Map<PathFragment, Artifact> getRootSymlinksAsMap() {
    return entriesToMap(rootSymlinks);
  }

  /**
   * Returns the unified map of path fragments to artifacts, taking both artifacts and symlinks into
   * account.
   */
  public Map<PathFragment, Artifact> asMapWithoutRootSymlinks() {
    Map<PathFragment, Artifact> result = entriesToMap(symlinks);
    // If multiple artifacts have the same root-relative path, the last one in the list will win.
    // That is because the runfiles tree cannot contain the same artifact for different
    // configurations, because it only uses root-relative paths.
    for (Artifact artifact : Iterables.filter(unconditionalArtifacts, Artifact.MIDDLEMAN_FILTER)) {
      result.put(artifact.getRootRelativePath(), artifact);
    }
    return result;
  }

  /**
   * Returns the pruning manifests specified for this runfiles tree.
   */
  public NestedSet<PruningManifest> getPruningManifests() {
    return pruningManifests;
  }

  /**
   * Returns the symlinks expander specified for this runfiles tree.
   */
  public Function<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>> getSymlinkExpander() {
    return manifestExpander;
  }

  /**
   * Returns the unified map of path fragments to artifacts, taking into account artifacts,
   * symlinks, and pruning manifest candidates. The returned set is guaranteed to be a (not
   * necessarily strict) superset of the actual runfiles tree created at execution time.
   */
  public NestedSet<Artifact> getAllArtifacts() {
    if (isEmpty()) {
      return NestedSetBuilder.emptySet(Order.STABLE_ORDER);
    }
    NestedSetBuilder<Artifact> allArtifacts = NestedSetBuilder.stableOrder();
    allArtifacts
        .addTransitive(unconditionalArtifacts)
        .addAll(Iterables.transform(symlinks, TO_ARTIFACT))
        .addAll(Iterables.transform(rootSymlinks, TO_ARTIFACT));
    for (PruningManifest manifest : getPruningManifests()) {
      allArtifacts.addTransitive(manifest.getCandidateRunfiles());
    }
    return allArtifacts.build();
  }

  /**
   * Returns if there are no runfiles.
   */
  public boolean isEmpty() {
    return unconditionalArtifacts.isEmpty() && symlinks.isEmpty() && rootSymlinks.isEmpty() &&
        pruningManifests.isEmpty();
  }

  private static <K, V> Map<K, V> entriesToMap(Iterable<Map.Entry<K, V>> entrySet) {
    Map<K, V> map = new LinkedHashMap<>();
    for (Map.Entry<K, V> entry : entrySet) {
      map.put(entry.getKey(), entry.getValue());
    }
    return map;
  }

  /**
   * Builder for Runfiles objects.
   */
  public static final class Builder {

    private String suffix;

    /**
     * This must be COMPILE_ORDER because {@link #asMapWithoutRootSymlinks} overwrites earlier
     * entries with later ones, so we want a post-order iteration.
     */
    private NestedSetBuilder<Artifact> artifactsBuilder =
        NestedSetBuilder.compileOrder();
    private NestedSetBuilder<Map.Entry<PathFragment, Artifact>> symlinksBuilder =
        NestedSetBuilder.stableOrder();
    private NestedSetBuilder<Map.Entry<PathFragment, Artifact>> rootSymlinksBuilder =
        NestedSetBuilder.stableOrder();
    private NestedSetBuilder<PruningManifest> pruningManifestsBuilder =
        NestedSetBuilder.stableOrder();
    private Function<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>>
        manifestExpander = DUMMY_SYMLINK_EXPANDER;

    /**
     * Builds a new Runfiles object.
     */
    public Runfiles build() {
      return new Runfiles(suffix, artifactsBuilder.build(), symlinksBuilder.build(),
          rootSymlinksBuilder.build(), pruningManifestsBuilder.build(),
          manifestExpander);
    }

    /**
     * Adds an artifact to the internal collection of artifacts.
     */
    public Builder addArtifact(Artifact artifact) {
      Preconditions.checkNotNull(artifact);
      artifactsBuilder.add(artifact);
      return this;
    }

    /**
     * Adds several artifacts to the internal collection.
     */
    public Builder addArtifacts(Iterable<Artifact> artifacts) {
      for (Artifact artifact : artifacts) {
        addArtifact(artifact);
      }
      return this;
    }


    /**
     * Use {@link #addTransitiveArtifacts} instead, to prevent increased memory use.
     */
    @Deprecated
    public Builder addArtifacts(NestedSet<Artifact> artifacts) {
      // Do not delete this method, or else addArtifacts(Iterable) calls with a NestedSet argument
      // will not be flagged.
      Iterable<Artifact> it = artifacts;
      addArtifacts(it);
      return this;
    }
    /**
     * Adds a nested set to the internal collection.
     */
    public Builder addTransitiveArtifacts(NestedSet<Artifact> artifacts) {
      artifactsBuilder.addTransitive(artifacts);
      return this;
    }

    /**
     * Adds a symlink.
     */
    public Builder addSymlink(PathFragment link, Artifact target) {
      Preconditions.checkNotNull(link);
      Preconditions.checkNotNull(target);
      symlinksBuilder.add(Maps.immutableEntry(link, target));
      return this;
    }

    /**
     * Adds several symlinks.
     */
    public Builder addSymlinks(Map<PathFragment, Artifact> symlinks) {
      symlinksBuilder.addAll(symlinks.entrySet());
      return this;
    }

    /**
     * Adds several symlinks as a NestedSet.
     */
    public Builder addSymlinks(NestedSet<Map.Entry<PathFragment, Artifact>> symlinks) {
      symlinksBuilder.addTransitive(symlinks);
      return this;
    }

    /**
     * Adds several root symlinks.
     */
    public Builder addRootSymlinks(Map<PathFragment, Artifact> symlinks) {
      rootSymlinksBuilder.addAll(symlinks.entrySet());
      return this;
    }

    /**
     * Adds several root symlinks as a NestedSet.
     */
    public Builder addRootSymlinks(NestedSet<Map.Entry<PathFragment, Artifact>> symlinks) {
      rootSymlinksBuilder.addTransitive(symlinks);
      return this;
    }

    /**
     * Adds a pruning manifest. See {@link PruningManifest} for an explanation.
     */
    public Builder addPruningManifest(PruningManifest manifest) {
      pruningManifestsBuilder.add(manifest);
      return this;
    }

    /**
     * Adds several pruning manifests as a NestedSet. See {@link PruningManifest} for an
     * explanation.
     */
    public Builder addPruningManifests(NestedSet<PruningManifest> manifests) {
      pruningManifestsBuilder.addTransitive(manifests);
      return this;
    }

    /**
     * Specify a function that can create additional manifest entries based on the input entries.
     */
    public Builder setManifestExpander(
        Function<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>> expander) {
      manifestExpander = Preconditions.checkNotNull(expander);
      return this;
    }

    /**
     * Merges runfiles from a given runfiles support.
     *
     * @param runfilesSupport the runfiles support to be merged in
     */
    public Builder merge(@Nullable RunfilesSupport runfilesSupport) {
      return merge(runfilesSupport, null);
    }

    /**
     * Merges runfiles from a given runfiles support.
     *
     * <p>Sometimes a particular symlink from the runfiles support must not be included in runfiles.
     * In such cases the path fragment denoting the symlink should be passed in as {@code
     * ommittedAdditionalSymlink}. The symlink will then be filtered away from the set of additional
     * symlinks of the target.
     *
     * @param runfilesSupport the runfiles support to be merged in
     * @param omittedAdditionalSymlink the symlink to be omitted, or null if no filtering is needed
     */
    public Builder merge(@Nullable RunfilesSupport runfilesSupport,
        @Nullable final PathFragment omittedAdditionalSymlink) {
      if (runfilesSupport == null) {
        return this;
      }
      // TODO(bazel-team): We may be able to remove this now.
      addArtifact(runfilesSupport.getRunfilesMiddleman());
      Runfiles runfiles = runfilesSupport.getRunfiles();
      if (omittedAdditionalSymlink == null) {
        merge(runfiles);
      } else {
        artifactsBuilder.addTransitive(runfiles.getUnconditionalArtifacts());
        symlinksBuilder.addAll(Maps.filterKeys(entriesToMap(runfiles.getSymlinks()),
            Predicates.not(Predicates.equalTo(omittedAdditionalSymlink))).entrySet());
        rootSymlinksBuilder.addTransitive(runfiles.getRootSymlinks());
        pruningManifestsBuilder.addTransitive(runfiles.getPruningManifests());
        if (manifestExpander == DUMMY_SYMLINK_EXPANDER) {
          manifestExpander = runfiles.getSymlinkExpander();
        } else {
          Function<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>> otherExpander =
              runfiles.getSymlinkExpander();
          Preconditions.checkState((otherExpander == DUMMY_SYMLINK_EXPANDER)
            || manifestExpander.equals(otherExpander));
        }
      }
      return this;
    }

    /**
     * Adds the runfiles for a particular target and visits the transitive closure of "srcs",
     * "deps" and "data", collecting all of their respective runfiles.
     */
    public Builder addRunfiles(RuleContext ruleContext,
        Function<TransitiveInfoCollection, Runfiles> mapping) {
      Preconditions.checkNotNull(mapping);
      Preconditions.checkNotNull(ruleContext);
      suffix = ruleContext.getWorkspaceName();
      addDataDeps(ruleContext);
      addNonDataDeps(ruleContext, mapping);
      return this;
    }

    /**
     * Adds the files specified by a mapping from the transitive info collection to the runfiles.
     *
     * <p>Dependencies in {@code srcs} and {@code deps} are considered.
     */
    public Builder add(RuleContext ruleContext,
        Function<TransitiveInfoCollection, Runfiles> mapping) {
      Preconditions.checkNotNull(ruleContext);
      Preconditions.checkNotNull(mapping);
      suffix = ruleContext.getWorkspaceName();
      for (TransitiveInfoCollection dep : getNonDataDeps(ruleContext)) {
        Runfiles runfiles = mapping.apply(dep);
        if (runfiles != null) {
          merge(runfiles);
        }
      }

      return this;
    }

    /**
     * Collects runfiles from data dependencies of a target.
     */
    public Builder addDataDeps(RuleContext ruleContext) {
      suffix = ruleContext.getWorkspaceName();
      addTargets(getPrerequisites(ruleContext, "data", Mode.DATA), RunfilesProvider.DATA_RUNFILES);
      return this;
    }

    /**
     * Collects runfiles from "srcs" and "deps" of a target.
     */
    public Builder addNonDataDeps(RuleContext ruleContext,
        Function<TransitiveInfoCollection, Runfiles> mapping) {
      suffix = ruleContext.getWorkspaceName();
      for (TransitiveInfoCollection target : getNonDataDeps(ruleContext)) {
        addTargetExceptFileTargets(target, mapping);
      }
      return this;
    }

    public Builder addTargets(Iterable<? extends TransitiveInfoCollection> targets,
        Function<TransitiveInfoCollection, Runfiles> mapping) {
      for (TransitiveInfoCollection target : targets) {
        addTarget(target, mapping);
      }
      return this;
    }

    public Builder addTarget(TransitiveInfoCollection target,
        Function<TransitiveInfoCollection, Runfiles> mapping) {
      return addTargetIncludingFileTargets(target, mapping);
    }

    private Builder addTargetExceptFileTargets(TransitiveInfoCollection target,
        Function<TransitiveInfoCollection, Runfiles> mapping) {
      Runfiles runfiles = mapping.apply(target);
      if (runfiles != null) {
        merge(runfiles);
      }

      return this;
    }

    private Builder addTargetIncludingFileTargets(TransitiveInfoCollection target,
        Function<TransitiveInfoCollection, Runfiles> mapping) {
      if (target.getProvider(RunfilesProvider.class) == null
          && mapping == RunfilesProvider.DATA_RUNFILES) {
        // RuleConfiguredTarget implements RunfilesProvider, so this will only be called on
        // FileConfiguredTarget instances.
        // TODO(bazel-team): This is a terrible hack. We should be able to make this go away
        // by implementing RunfilesProvider on FileConfiguredTarget. We'd need to be mindful
        // of the memory use, though, since we have a whole lot of FileConfiguredTarget instances.
        addTransitiveArtifacts(target.getProvider(FileProvider.class).getFilesToBuild());
        return this;
      }

      return addTargetExceptFileTargets(target, mapping);
    }

    /**
     * Adds symlinks to given artifacts at their exec paths.
     */
    public Builder addSymlinksToArtifacts(Iterable<Artifact> artifacts) {
      for (Artifact artifact : artifacts) {
        addSymlink(artifact.getExecPath(), artifact);
      }
      return this;
    }

    /**
     * Add the other {@link Runfiles} object transitively.
     */
    public Builder merge(Runfiles runfiles) {
      return merge(runfiles, true);
    }

    /**
     * Add the other {@link Runfiles} object transitively, but don't merge
     * pruning manifests.
     */
    public Builder mergeExceptPruningManifests(Runfiles runfiles) {
      return merge(runfiles, false);
    }

    /**
     * Sets the directory name to put runfiles under. "" is the default and puts the runfiles
     * immediately under the &lt;target&gt;.runfiles directory.
     */
    public Builder setSuffix(String workspaceName) {
      suffix = workspaceName;
      return this;
    }

    /**
     * Add the other {@link Runfiles} object transitively, with the option to include or exclude
     * pruning manifests in the merge.
     */
    private Builder merge(Runfiles runfiles, boolean includePruningManifests) {
      artifactsBuilder.addTransitive(runfiles.getUnconditionalArtifacts());
      symlinksBuilder.addTransitive(runfiles.getSymlinks());
      rootSymlinksBuilder.addTransitive(runfiles.getRootSymlinks());
      if (suffix == null) {
        suffix = runfiles.suffix;
      }
      if (includePruningManifests) {
        pruningManifestsBuilder.addTransitive(runfiles.getPruningManifests());
      }
      if (manifestExpander == DUMMY_SYMLINK_EXPANDER) {
        manifestExpander = runfiles.getSymlinkExpander();
      } else {
        Function<Map<PathFragment, Artifact>, Map<PathFragment, Artifact>> otherExpander =
            runfiles.getSymlinkExpander();
        Preconditions.checkState((otherExpander == DUMMY_SYMLINK_EXPANDER)
          || manifestExpander.equals(otherExpander));
      }
      return this;
    }

    private static Iterable<TransitiveInfoCollection> getNonDataDeps(RuleContext ruleContext) {
      return Iterables.concat(
          // TODO(bazel-team): This line shouldn't be here. Removing it requires that no rules have
          // dependent rules in srcs (except for filegroups and such), but always in deps.
          // TODO(bazel-team): DONT_CHECK is not optimal here. Rules that use split configs need to
          // be changed not to call into here.
          getPrerequisites(ruleContext, "srcs", Mode.DONT_CHECK),
          getPrerequisites(ruleContext, "deps", Mode.DONT_CHECK));
    }

    /**
     * For the specified attribute "attributeName" (which must be of type list(label)), resolves all
     * the labels into ConfiguredTargets (for the same configuration as this one) and returns them
     * as a list.
     *
     * <p>If the rule does not have the specified attribute, returns the empty list.
     */
    private static Iterable<? extends TransitiveInfoCollection> getPrerequisites(
        RuleContext ruleContext, String attributeName, Mode mode) {
      if (ruleContext.getRule().isAttrDefined(attributeName, Type.LABEL_LIST)) {
        return ruleContext.getPrerequisites(attributeName, mode);
      } else {
        return Collections.emptyList();
      }
    }
  }
}
