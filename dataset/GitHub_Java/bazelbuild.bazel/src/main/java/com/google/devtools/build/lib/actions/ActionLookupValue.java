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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.actions.Artifact.SourceArtifact;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Base class for all values which can provide the generating action of an artifact.
 */
public abstract class ActionLookupValue implements SkyValue {

  /** Returns a list of actions registered by this {@link SkyValue}. */
  protected abstract ImmutableList<ActionAnalysisMetadata> getActions();

  /**
   * Returns a map where keys are artifacts generated by this {@link SkyValue}, and values are
   * the index of the action which generates this artifact.
   */
  protected abstract ImmutableMap<Artifact, Integer> getGeneratingActionIndex();

  /**
   * Returns the index of the action that generates {@code artifact} in this value, or null if this
   * value does not have a generating action for this artifact. The index together with the key for
   * this {@link ActionLookupValue} uniquely identifies the action.
   *
   * <p>Unlike {@link #getAction}, this may be called after action execution.
   */
  @Nullable
  public Integer getGeneratingActionIndex(Artifact artifact) {
    return getGeneratingActionIndex().get(artifact);
  }

  /**
   * Returns the action that generates {@code artifact}, if known to this value, or null. This
   * method should be avoided. Call it only when the action is really needed, and it is known to be
   * present.
   */
  @Nullable
  public ActionAnalysisMetadata getGeneratingActionDangerousReadJavadoc(Artifact artifact) {
    Integer actionIndex = getGeneratingActionIndex(artifact);
    if (actionIndex == null) {
      return null;
    }
    return getActions().get(actionIndex);
  }

  /**
   * Returns the {@link Action} with index {@code index} in this value. Never null. Should only be
   * called during action execution by {@code ArtifactFunction} and {@code ActionExecutionFunction}
   * -- after an action has executed, calling this with its index may crash.
   */
  @SuppressWarnings("unchecked") // We test to make sure it's an Action.
  public Action getAction(int index) {
    Preconditions.checkState(index < getActions().size(), "Bad index: %s %s", index, this);
    ActionAnalysisMetadata result = getActions().get(index);
    Preconditions.checkState(result instanceof Action, "Not action: %s %s %s", result, index, this);
    return (Action) result;
  }

  public ActionTemplate<?> getActionTemplate(int index) {
    ActionAnalysisMetadata result = getActions().get(index);
    Preconditions.checkState(
        result instanceof ActionTemplate, "Not action template: %s %s %s", result, index, this);
    return (ActionTemplate<?>) result;
  }

  /**
   * Returns if the action at {@code index} is an {@link ActionTemplate} so that tree artifacts can
   * take the proper action.
   */
  public boolean isActionTemplate(int index) {
    return getActions().get(index) instanceof ActionTemplate;
  }

  /** To be used only when checking consistency of the action graph -- not by other values. */
  public Map<Artifact, ActionAnalysisMetadata> getMapForConsistencyCheck() {
    return getMapForConsistencyCheck(getGeneratingActionIndex(), getActions());
  }

  public static Map<Artifact, ActionAnalysisMetadata> getMapForConsistencyCheck(
      Map<Artifact, Integer> generatingActionIndex,
      final List<? extends ActionAnalysisMetadata> actions) {
    return Maps.transformValues(generatingActionIndex, actions::get);
  }

  /**
   * Returns the number of {@link Action} objects present in this value.
   */
  public int getNumActions() {
    return getActions().size();
  }

  /** Returns a source artifact if the underlying configured target is an input file. */
  @Nullable
  public SourceArtifact getSourceArtifact() {
    return null;
  }

  /**
   * All subclasses of ActionLookupValue "own" artifacts with {@link ArtifactOwner}s that are
   * subclasses of ActionLookupKey. This allows callers to easily find the value key, while
   * remaining agnostic to what ActionLookupValues actually exist.
   */
  public abstract static class ActionLookupKey implements ArtifactOwner, SkyKey {
    @Override
    public Label getLabel() {
      return null;
    }
  }
}
