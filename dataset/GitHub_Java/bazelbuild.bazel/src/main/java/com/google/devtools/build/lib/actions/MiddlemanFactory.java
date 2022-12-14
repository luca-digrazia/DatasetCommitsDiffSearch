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
package com.google.devtools.build.lib.actions;

import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Action.MiddlemanType;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.util.Preconditions;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.util.Iterator;

/**
 * A factory to create middleman objects.
 */
@ThreadSafe
public final class MiddlemanFactory {

  private final ArtifactFactory artifactFactory;
  private final ActionRegistry actionRegistry;

  public MiddlemanFactory(
      ArtifactFactory artifactFactory, ActionRegistry actionRegistry) {
    this.artifactFactory = Preconditions.checkNotNull(artifactFactory);
    this.actionRegistry = Preconditions.checkNotNull(actionRegistry);
  }

  /**
   * Creates a {@link MiddlemanType#AGGREGATING_MIDDLEMAN aggregating} middleman.
   *
   * @param owner the owner of the action that will be created; must not be null
   * @param purpose the purpose for which this middleman is created. This should be a string which
   *        is suitable for use as a filename. A single rule may have many middlemen with distinct
   *        purposes.
   * @param inputs the set of artifacts for which the created artifact is to be the middleman.
   * @param middlemanDir the directory in which to place the middleman.
   * @return null iff {@code inputs} is empty; the single element of {@code inputs} if there's only
   *         one; a new aggregating middleman for the {@code inputs} otherwise
   */
  public Artifact createAggregatingMiddleman(
      ActionOwner owner, String purpose, Iterable<Artifact> inputs, Root middlemanDir) {
    if (hasExactlyOneInput(inputs)) { // Optimization: No middleman for just one input.
      return Iterables.getOnlyElement(inputs);
    }
    Pair<Artifact, Action> result = createMiddleman(
        owner, Label.print(owner.getLabel()), purpose, inputs, middlemanDir,
        MiddlemanType.AGGREGATING_MIDDLEMAN);
    return result == null ? null : result.getFirst();
  }

  /**
   * Returns <code>null</code> iff inputs is empty. Returns the sole element
   * of inputs iff <code>inputs.size()==1</code>. Otherwise, returns a
   * middleman artifact and creates a middleman action that generates that
   * artifact.
   *
   * @param owner the owner of the action that will be created.
   * @param owningArtifact the artifact of the file for which the runfiles
   *        should be created. There may be at most one set of runfiles for
   *        an owning artifact, unless the owning artifact is null. There
   *        may be at most one set of runfiles per owner with a null
   *        owning artifact.
   *        Further, if the owning Artifact is non-null, the owning Artifacts'
   *        root-relative path must be unique and the artifact must be part
   *        of the runfiles tree for which this middleman is created. Usually
   *        this artifact will be an executable program.
   * @param inputs the set of artifacts for which the created artifact is to be
   *        the middleman.
   * @param middlemanDir the directory in which to place the middleman.
   */
  public Artifact createRunfilesMiddleman(
      ActionOwner owner, Artifact owningArtifact, Iterable<Artifact> inputs, Root middlemanDir,
      String tag) {
    if (hasExactlyOneInput(inputs)) { // Optimization: No middleman for just one input.
      return Iterables.getOnlyElement(inputs);
    }
    String middlemanPath = owningArtifact == null
       ? Label.print(owner.getLabel())
       : owningArtifact.getRootRelativePath().getPathString();
    return createMiddleman(owner, middlemanPath, tag, inputs, middlemanDir,
        MiddlemanType.RUNFILES_MIDDLEMAN).getFirst();
  }

  private <T> boolean hasExactlyOneInput(Iterable<T> iterable) {
    Iterator<T> it = iterable.iterator();
    if (!it.hasNext()) {
      return false;
    }
    it.next();
    return !it.hasNext();
  }

  /**
   * Creates a {@link MiddlemanType#ERROR_PROPAGATING_MIDDLEMAN error-propagating} middleman.
   *
   * @param owner the owner of the action that will be created. May not be null.
   * @param middlemanName a unique file name for the middleman artifact in the {@code middlemanDir};
   *        in practice this is usually the owning rule's label (so it gets escaped as such)
   * @param purpose the purpose for which this middleman is created. This should be a string which
   *        is suitable for use as a filename. A single rule may have many middlemen with distinct
   *        purposes.
   * @param inputs the set of artifacts for which the created artifact is to be the middleman; must
   *        not be null or empty
   * @param middlemanDir the directory in which to place the middleman.
   * @return a middleman that enforces scheduling order (just like a scheduling middleman) and
   *         propagates errors, but is ignored by the dependency checker
   * @throws IllegalArgumentException if {@code inputs} is null or empty
   */
  public Artifact createErrorPropagatingMiddleman(ActionOwner owner, String middlemanName,
      String purpose, Iterable<Artifact> inputs, Root middlemanDir) {
    Preconditions.checkArgument(inputs != null);
    Preconditions.checkArgument(!Iterables.isEmpty(inputs));
    // We must always create this middleman even if there is only one input.
    return createMiddleman(owner, middlemanName, purpose, inputs, middlemanDir,
        MiddlemanType.ERROR_PROPAGATING_MIDDLEMAN).getFirst();
  }

  /**
   * Returns the same artifact as {@code createErrorPropagatingMiddleman} would return,
   * but doesn't create any action.
   */
  public Artifact getErrorPropagatingMiddlemanArtifact(String middlemanName, String purpose,
      Root middlemanDir) {
    return getStampFileArtifact(middlemanName, purpose, middlemanDir);
  }

  /**
   * Creates both normal and scheduling middlemen.
   *
   * <p>Note: there's no need to synchronize this method; the only use of a field is via a call to
   * another synchronized method (getArtifact()).
   *
   * @return null iff {@code inputs} is null or empty; the middleman file and the middleman action
   *         otherwise
   */
  private Pair<Artifact, Action> createMiddleman(
      ActionOwner owner, String middlemanName, String purpose, Iterable<Artifact> inputs,
      Root middlemanDir, MiddlemanType middlemanType) {
    if (inputs == null || Iterables.isEmpty(inputs)) {
      return null;
    }

    Artifact stampFile = getStampFileArtifact(middlemanName, purpose, middlemanDir);
    Action action = new MiddlemanAction(owner, inputs, stampFile, purpose, middlemanType);
    actionRegistry.registerAction(action);
    return Pair.of(stampFile, action);
  }

  /**
   * Creates a normal middleman.
   *
   * <p>If called multiple times, it always returns the same object depending on the {@code
   * purpose}. It does not check that the list of inputs is identical. In contrast to other
   * middleman methods, this one also returns an object if the list of inputs is empty.
   *
   * <p>Note: there's no need to synchronize this method; the only use of a field is via a call to
   * another synchronized method (getArtifact()).
   */
  public Artifact createMiddlemanAllowMultiple(ActionRegistry registry, ActionOwner owner,
      PathFragment packageDirectory, String purpose, Iterable<Artifact> inputs, Root middlemanDir) {
    String escapedPackageDirectory = Actions.escapedPath(packageDirectory.getPathString());
    PathFragment stampName =
        new PathFragment("_middlemen/" + (purpose.startsWith(escapedPackageDirectory)
                                 ? purpose : (escapedPackageDirectory + purpose)));
    Artifact stampFile = artifactFactory.getDerivedArtifact(stampName, middlemanDir,
        actionRegistry.getOwner());
    MiddlemanAction.create(
        registry, owner, inputs, stampFile, purpose, MiddlemanType.AGGREGATING_MIDDLEMAN);
    return stampFile;
  }

  private Artifact getStampFileArtifact(String middlemanName, String purpose, Root middlemanDir) {
    String escapedFilename = Actions.escapedPath(middlemanName);
    PathFragment stampName = new PathFragment("_middlemen/" + escapedFilename + "-" + purpose);
    Artifact stampFile = artifactFactory.getDerivedArtifact(stampName, middlemanDir,
        actionRegistry.getOwner());
    return stampFile;
  }
}
