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

import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.EventReportingArtifacts;
import com.google.devtools.build.lib.buildeventstream.ArtifactGroupNamer;
import com.google.devtools.build.lib.buildeventstream.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventContext;
import com.google.devtools.build.lib.buildeventstream.BuildEventId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.GenericBuildEvent;
import com.google.devtools.build.lib.buildeventstream.PathConverter;
import com.google.devtools.build.lib.collect.nestedset.NestedSetView;
import java.util.Collection;

/**
 * A {@link BuildEvent} introducing a set of artifacts to be referred to later by its name. Those
 * events are generated by the {@link BuildEventStreamer} upon seeing an {@link
 * EventReportingArtifacts}, if necessary.
 */
class NamedArtifactGroup implements BuildEvent {
  private final String name;
  private final NestedSetView<Artifact> view;

  NamedArtifactGroup(String name, NestedSetView<Artifact> view) {
    this.name = name;
    this.view = view;
  }

  @Override
  public BuildEventId getEventId() {
    return BuildEventId.fromArtifactGroupName(name);
  }

  @Override
  public Collection<BuildEventId> getChildrenEvents() {
    return ImmutableSet.<BuildEventId>of();
  }

  @Override
  public BuildEventStreamProtos.BuildEvent asStreamProto(BuildEventContext converters) {
    PathConverter pathConverter = converters.pathConverter();
    ArtifactGroupNamer namer = converters.artifactGroupNamer();

    BuildEventStreamProtos.NamedSetOfFiles.Builder builder =
        BuildEventStreamProtos.NamedSetOfFiles.newBuilder();
    for (Artifact artifact : view.directs()) {
      if (artifact.isMiddlemanArtifact()) {
        continue;
      }
      String name = artifact.getRootRelativePathString();
      String uri = pathConverter.apply(artifact.getPath());
      builder.addFiles(BuildEventStreamProtos.File.newBuilder().setName(name).setUri(uri));
    }
    for (NestedSetView<Artifact> child : view.transitives()) {
      builder.addFileSets(namer.apply(child.identifier()));
    }
    return GenericBuildEvent.protoChaining(this).setNamedSetOfFiles(builder.build()).build();
  }
}
