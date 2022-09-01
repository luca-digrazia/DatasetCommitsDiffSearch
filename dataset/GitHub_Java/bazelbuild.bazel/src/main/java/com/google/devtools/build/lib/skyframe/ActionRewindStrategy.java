// Copyright 2018 The Bazel Authors. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.ActionInput;
import com.google.devtools.build.lib.actions.ActionLookupData;
import com.google.devtools.build.lib.actions.AlreadyReportedActionExecutionException;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.LostInputsExecException.LostInputsActionExecutionException;
import com.google.devtools.build.lib.skyframe.ActionExecutionFunction.ActionExecutionFunctionException;
import com.google.devtools.build.skyframe.SkyFunction.Environment;
import com.google.devtools.build.skyframe.SkyFunction.Restart;
import com.google.devtools.build.skyframe.SkyKey;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Given an action that failed to execute because of missing inputs which were generated by other
 * actions, this finds the Skyframe nodes corresponding to those inputs and the actions which
 * generated them.
 */
public class ActionRewindStrategy {

  /**
   * Returns a {@link RewindPlan} specifying:
   *
   * <ol>
   *   <li>the Skyframe nodes to restart to recreate the lost inputs specified by {@code
   *       lostInputsException}
   *   <li>the actions whose execution state (in {@link SkyframeActionExecutor}) must be reset
   * </ol>
   *
   * @throws ActionExecutionFunctionException if any lost inputs are not the outputs of previously
   *     executed actions
   */
  // TODO(mschaller): support special/tree artifact types
  RewindPlan getRewindPlan(
      Action failedAction,
      Iterable<SkyKey> inputDepKeys,
      LostInputsActionExecutionException lostInputsException,
      Environment env)
      throws ActionExecutionFunctionException, InterruptedException {
    ImmutableList<ActionInput> lostInputs = lostInputsException.getLostInputs();
    for (ActionInput actionInput : lostInputs) {
      if (!(actionInput instanceof Artifact) || ((Artifact) actionInput).isSourceArtifact()) {
        throw new ActionExecutionFunctionException(
            new AlreadyReportedActionExecutionException(lostInputsException));
      }
    }

    // This collection tracks which Skyframe nodes must be restarted.
    HashSet<SkyKey> depsToRestart = new HashSet<>();

    // SkyframeActionExecutor must re-execute the actions being restarted, so we must tell it to
    // evict its cached action results. This collection tracks those actions.
    HashSet<Action> actionsToReset = new HashSet<>();

    actionsToReset.add(failedAction);
    ImmutableSet<SkyKey> inputDepKeysSet = ImmutableSet.copyOf(inputDepKeys);
    for (ActionInput lostInput : lostInputs) {
      Preconditions.checkState(
          inputDepKeysSet.contains(lostInput),
          "Lost input not a dep of action.\nLost input: %s\nDeps: %s\nAction: %s",
          lostInput,
          inputDepKeysSet,
          failedAction);
      // Restart the artifact.
      depsToRestart.add((Artifact) lostInput);

      Map<ActionLookupData, Action> actionMap = getActionsForLostInput((Artifact) lostInput, env);
      if (actionMap == null) {
        // Some deps of the artifact are not done. Another rewind must be in-flight, and there is no
        // need to restart the shared deps twice.
        continue;
      }
      // Restart the actions which produced the artifact.
      depsToRestart.addAll(actionMap.keySet());
      actionsToReset.addAll(actionMap.values());

      LinkedList<Action> possiblyPropagatingActions = new LinkedList<>(actionMap.values());
      while (!possiblyPropagatingActions.isEmpty()) {
        Action action = possiblyPropagatingActions.poll();

        if (!action.mayInsensitivelyPropagateInputs()) {
          continue;
        }
        // Restarting this action is insufficient. Doing so will not recreate the missing input.
        // We need to also restart this action's non-source inputs and the actions which created
        // those inputs.
        //
        // Note that the artifacts returned by Action#getAllowedDerivedInputs do not need to be
        // considered because none of the actions which provide non-throwing implementations of
        // getAllowedDerivedInputs "insensitively propagate inputs".
        Iterable<Artifact> inputs = action.getInputs();
        for (Artifact input : inputs) {
          if (input.isSourceArtifact()) {
            continue;
          }
          // Restarting all derived inputs of propagating actions is overkill. Preferably, we'd want
          // to only restart the inputs which corresponds to the known lost outputs, somehow.
          // Rewinding is expected to be rare, so perhaps refining this isn't necessary.
          depsToRestart.add(input);
          Map<ActionLookupData, Action> otherActionMap = getActionsForLostInput(input, env);
          if (otherActionMap == null) {
            continue;
          }
          depsToRestart.addAll(otherActionMap.keySet());
          for (Action nextAction : otherActionMap.values()) {
            Preconditions.checkState(
                actionsToReset.add(nextAction), "Action-artifact cycle? Visited twice: %s", action);
          }
          // The preceding precondition guarantees that no action gets added to this list twice.
          possiblyPropagatingActions.addAll(otherActionMap.values());
        }
      }
    }

    return new RewindPlan(
        Restart.selfAnd(ImmutableList.copyOf(depsToRestart)), ImmutableList.copyOf(actionsToReset));
  }

  @Nullable
  private Map<ActionLookupData, Action> getActionsForLostInput(Artifact lostInput, Environment env)
      throws InterruptedException {
    Set<ActionLookupData> actionExecutionDeps = getActionExecutionDeps(lostInput, env);
    if (actionExecutionDeps == null) {
      return null;
    }

    Map<ActionLookupData, Action> actions =
        Maps.newHashMapWithExpectedSize(actionExecutionDeps.size());
    for (ActionLookupData dep : actionExecutionDeps) {
      actions.put(dep, ActionExecutionFunction.getActionForLookupData(env, dep));
    }
    return actions;
  }

  /**
   * Returns the set of {@code lostInput}'s execution-phase dependencies, or {@code null} if any of
   * those dependencies are not done.
   */
  @Nullable
  private Set<ActionLookupData> getActionExecutionDeps(Artifact lostInput, Environment env)
      throws InterruptedException {
    ArtifactFunction.ArtifactDependencies artifactDependencies =
        ArtifactFunction.ArtifactDependencies.discoverDependencies(lostInput, env);
    if (artifactDependencies == null) {
      return null;
    }
    Preconditions.checkState(
        !artifactDependencies.isTemplateActionForTreeArtifact(),
        "Rewinding template actions not yet supported: %s",
        artifactDependencies);
    // TODO(mschaller): extend ArtifactDependencies to support template actions (and other special
    // cases). This return type is a collection assuming that those cases may require multiple keys.
    // Scalarize it if that's untrue.
    return ImmutableSet.of(artifactDependencies.getNontemplateActionExecutionKey());
  }

  static class RewindPlan {
    private final Restart nodesToRestart;
    private final ImmutableList<Action> actionsToReset;

    RewindPlan(Restart nodesToRestart, ImmutableList<Action> actionsToReset) {
      this.nodesToRestart = nodesToRestart;
      this.actionsToReset = actionsToReset;
    }

    Restart getNodesToRestart() {
      return nodesToRestart;
    }

    ImmutableList<Action> getActionsToReset() {
      return actionsToReset;
    }
  }
}
