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

package com.google.devtools.build.lib.analysis.constraints;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.EnvironmentGroup;
import com.google.devtools.build.lib.syntax.Label;

import java.util.Map;

/**
 * Contains a set of {@link Environment} labels and their associated groups.
 */
@Immutable
public class EnvironmentCollection {
  private final ImmutableMultimap<EnvironmentGroup, Label> map;

  private EnvironmentCollection(ImmutableMultimap<EnvironmentGroup, Label> map) {
    this.map = map;
  }

  /**
   * Stores an environment's build label along with the group it belongs to.
   */
  static class EnvironmentWithGroup {
    private final Label environment;
    private final EnvironmentGroup group;
    EnvironmentWithGroup(Label environment, EnvironmentGroup group) {
      this.environment = environment;
      this.group = group;
    }
    Label environment() { return environment; }
    EnvironmentGroup group() { return group; }
  }

  /**
   * Returns the build labels of each environment in this collection, ordered by
   * their insertion order in {@link Builder}.
   */
  ImmutableCollection<Label> getEnvironments() {
    return map.values();
  }

  /**
   * Returns the set of groups the environments in this collection belong to, ordered by
   * their insertion order in {@link Builder}
   */
  ImmutableSet<EnvironmentGroup> getGroups() {
    return map.keySet();
  }

  /**
   * Returns the build labels of each environment in this collection paired with the
   * group each environment belongs to, ordered by their insertion order in {@link Builder}.
   */
  ImmutableCollection<EnvironmentWithGroup> getGroupedEnvironments() {
    ImmutableSet.Builder<EnvironmentWithGroup> builder = ImmutableSet.builder();
    for (Map.Entry<EnvironmentGroup, Label> entry : map.entries()) {
      builder.add(new EnvironmentWithGroup(entry.getValue(), entry.getKey()));
    }
    return builder.build();
  }

  /**
   * Returns the environments in this collection that belong to the given group, ordered by
   * their insertion order in {@link Builder}. If no environments belong to the given group,
   * returns an empty collection.
   */
  ImmutableCollection<Label> getEnvironments(EnvironmentGroup group) {
    return map.get(group);
  }

  /**
   * An empty collection.
   */
  static final EnvironmentCollection EMPTY =
      new EnvironmentCollection(ImmutableMultimap.<EnvironmentGroup, Label>of());

  static class Builder {
    private final ImmutableMultimap.Builder<EnvironmentGroup, Label> mapBuilder =
        ImmutableMultimap.builder();

    /**
     * Inserts the given environment / owning group pair.
     */
    Builder put(EnvironmentGroup group, Label environment) {
      mapBuilder.put(group, environment);
      return this;
    }

    /**
     * Inserts the given set of environments, all belonging to the specified group.
     */
    Builder putAll(EnvironmentGroup group, Iterable<Label> environments) {
      mapBuilder.putAll(group, environments);
      return this;
    }

    /**
     * Inserts the contents of another {@link EnvironmentCollection} into this one.
     */
    Builder putAll(EnvironmentCollection other) {
      mapBuilder.putAll(other.map);
      return this;
    }

    EnvironmentCollection build() {
      return new EnvironmentCollection(mapBuilder.build());
    }
  }
}
