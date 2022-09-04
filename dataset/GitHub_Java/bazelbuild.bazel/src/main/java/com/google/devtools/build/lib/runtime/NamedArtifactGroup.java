// Copyright 2016 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.runtime;

import static com.google.devtools.build.lib.analysis.TargetCompleteEvent.newFileFromArtifact;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.CompletionContext;
import com.google.devtools.build.lib.actions.CompletionContext.ArtifactReceiver;
import com.google.devtools.build.lib.actions.EventReportingArtifacts;
import com.google.devtools.build.lib.buildeventstream.ArtifactGroupNamer;
import com.google.devtools.build.lib.buildeventstream.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEvent.LocalFile.LocalFileType;
import com.google.devtools.build.lib.buildeventstream.BuildEventContext;
import com.google.devtools.build.lib.buildeventstream.BuildEventId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.GenericBuildEvent;
import com.google.devtools.build.lib.buildeventstream.PathConverter;
import com.google.devtools.build.lib.collect.nestedset.NestedSetView;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A {@link BuildEvent} introducing a set of artifacts to be referred to later by its name. Those
 * events are generated by the {@link BuildEventStreamer} upon seeing an {@link
 * EventReportingArtifacts}, if necessary.
 */
class NamedArtifactGroup implements BuildEvent {
  private final String name;
  private final CompletionContext completionContext;
  private final NestedSetView<?> view;

  /**
   * Create a {@link NamedArtifactGroup}. The view may contain as direct entries {@link Artifact} or
   * {@link ExpandedArtifact}.
   */
  NamedArtifactGroup(String name, CompletionContext completionContext, NestedSetView<?> view) {
    this.name = name;
    this.completionContext = completionContext;
    this.view = view;
  }

  @Override
  public BuildEventId getEventId() {
    return BuildEventId.fromArtifactGroupName(name);
  }

  @Override
  public Collection<BuildEventId> getChildrenEvents() {
    return ImmutableSet.of();
  }

  @Override
  public Collection<LocalFile> referencedLocalFiles() {
    ImmutableList.Builder<LocalFile> artifacts = ImmutableList.builder();
    for (Object o : view.directs()) {
      ExpandedArtifact expandedArtifact = (ExpandedArtifact) o;
      if (expandedArtifact.relPath == null) {
        artifacts.add(
            new LocalFile(
                completionContext.pathResolver().toPath(expandedArtifact.artifact),
                LocalFileType.OUTPUT));
      } else {
        artifacts.add(
            new LocalFile(
                completionContext.pathResolver().convertPath(expandedArtifact.target),
                LocalFileType.OUTPUT));
      }
    }
    return artifacts.build();
  }

  @Override
  public BuildEventStreamProtos.BuildEvent asStreamProto(BuildEventContext converters) {
    PathConverter pathConverter = converters.pathConverter();
    ArtifactGroupNamer namer = converters.artifactGroupNamer();

    BuildEventStreamProtos.NamedSetOfFiles.Builder builder =
        BuildEventStreamProtos.NamedSetOfFiles.newBuilder();
    for (Object o : view.directs()) {
      ExpandedArtifact expandedArtifact = (ExpandedArtifact) o;
      if (expandedArtifact.relPath == null) {
        String uri =
            pathConverter.apply(completionContext.pathResolver().toPath(expandedArtifact.artifact));
        if (uri != null) {
          builder.addFiles(newFileFromArtifact(expandedArtifact.artifact).setUri(uri));
        }
      } else {
        String uri =
            converters
                .pathConverter()
                .apply(completionContext.pathResolver().convertPath(expandedArtifact.target));
        if (uri != null) {
          builder.addFiles(
              newFileFromArtifact(null, expandedArtifact.artifact, expandedArtifact.relPath)
                  .setUri(uri)
                  .build());
        }
      }
    }

    for (NestedSetView<?> child : view.transitives()) {
      builder.addFileSets(namer.apply(child.identifier()));
    }
    return GenericBuildEvent.protoChaining(this).setNamedSetOfFiles(builder.build()).build();
  }

  /**
   * Given a view with direct entries of {@link Artifact} and {@link ExpandedArtifact}, return a
   * transformed view with any {@link Artifact} expanded to a set of {@link ExpandedArtifact}.
   */
  static NestedSetView<Object> expandView(CompletionContext ctx, NestedSetView<?> artifacts) {
    ImmutableList.Builder<ExpandedArtifact> expandedArtifacts = ImmutableList.builder();
    for (Object artifact : artifacts.directs()) {
      if (artifact instanceof ExpandedArtifact) {
        expandedArtifacts.add((ExpandedArtifact) artifact);
      } else if (artifact instanceof Artifact) {
        ctx.visitArtifacts(
            ImmutableList.of((Artifact) artifact),
            new ArtifactReceiver() {
              @Override
              public void accept(Artifact artifact) {
                expandedArtifacts.add(new ExpandedArtifact(artifact, null, null));
              }

              @Override
              public void acceptFilesetMapping(
                  Artifact fileset, PathFragment relName, Path targetFile) {
                expandedArtifacts.add(new ExpandedArtifact(fileset, relName, targetFile));
              }
            });
      } else {
        throw new IllegalStateException("Unexpected type in artifact view:  " + artifact);
      }
    }
    ImmutableList<ExpandedArtifact> expandedDirects = expandedArtifacts.build();

    Set<? extends NestedSetView<?>> transitives = artifacts.transitives();
    Object[] directAndTransitiveArtifacts = new Object[expandedDirects.size() + transitives.size()];
    int i = 0;
    for (ExpandedArtifact a : expandedDirects) {
      directAndTransitiveArtifacts[i++] = a;
    }
    for (NestedSetView<?> t : transitives) {
      directAndTransitiveArtifacts[i++] = t.identifier();
    }

    return new NestedSetView<>(directAndTransitiveArtifacts);
  }

  private static final class ExpandedArtifact {
    public final Artifact artifact;
    // These fields are used only for Fileset links.
    @Nullable public final PathFragment relPath;
    @Nullable public final Path target;

    public ExpandedArtifact(Artifact artifact, PathFragment relPath, Path target) {
      this.artifact = artifact;
      this.relPath = relPath;
      this.target = target;
    }
  }
}
