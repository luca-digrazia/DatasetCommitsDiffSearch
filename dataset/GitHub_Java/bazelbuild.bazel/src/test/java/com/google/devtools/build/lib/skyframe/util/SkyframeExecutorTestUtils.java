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
package com.google.devtools.build.lib.skyframe.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.packages.NoSuchTargetException;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.skyframe.ConfiguredTargetValue;
import com.google.devtools.build.lib.skyframe.PackageValue;
import com.google.devtools.build.lib.skyframe.SkyFunctions;
import com.google.devtools.build.lib.skyframe.SkyframeExecutor;
import com.google.devtools.build.lib.skyframe.TargetMarkerValue;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.skyframe.ErrorInfo;
import com.google.devtools.build.skyframe.EvaluationResult;
import com.google.devtools.build.skyframe.MemoizingEvaluator;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Helper functions for manually dealing with a {@link SkyframeExecutor}'s graph in tests.
 */
public class SkyframeExecutorTestUtils {

  private SkyframeExecutorTestUtils() {
  }

  /**
   * Returns an existing value, or {@code null} if the given key is not currently in the graph.
   */
  @Nullable
  public static SkyValue getExistingValue(SkyframeExecutor skyframeExecutor, SkyKey key) {
    return skyframeExecutor.getEvaluatorForTesting().getExistingValueForTesting(key);
  }

  /**
   * Returns an existing error info, or {@code null} if the given key is not currently in the
   * graph.
   */
  @Nullable
  public static ErrorInfo getExistingError(SkyframeExecutor skyframeExecutor, SkyKey key) {
    return skyframeExecutor.getEvaluatorForTesting().getExistingErrorForTesting(key);
  }

  /**
   * Calls {@link MemoizingEvaluator#evaluate} on the given {@link SkyframeExecutor}'s
   * graph.
   */
  public static <T extends SkyValue> EvaluationResult<T> evaluate(
      SkyframeExecutor skyframeExecutor, SkyKey key, boolean keepGoing,
      EventHandler errorEventListener) throws InterruptedException {
    return skyframeExecutor.getDriverForTesting().evaluate(ImmutableList.of(key), keepGoing,
        SkyframeExecutor.DEFAULT_THREAD_COUNT, errorEventListener);
  }

  /**
   * Returns an existing configured target value, or {@code null} if there is not an appropriate
   * configured target value key in the graph.
   *
   * This helper is provided so legacy tests don't need to know about details of skyframe keys.
   */
  @Nullable
  public static ConfiguredTargetValue getExistingConfiguredTargetValue(
      SkyframeExecutor skyframeExecutor, Label label, BuildConfiguration config) {
    SkyKey key = ConfiguredTargetValue.key(label, config);
    return (ConfiguredTargetValue) getExistingValue(skyframeExecutor, key);
  }

  /**
   * Returns the configured target for an existing configured target value, or {@code null} if there
   * is not an appropriate configured target value key in the graph.
   *
   * This helper is provided so legacy tests don't need to know about details of skyframe keys.
   */
  @Nullable
  public static ConfiguredTarget getExistingConfiguredTarget(
      SkyframeExecutor skyframeExecutor,
      Label label, BuildConfiguration config) {
    ConfiguredTargetValue value = getExistingConfiguredTargetValue(skyframeExecutor, label, config);
    if (value == null) {
      return null;
    }
    return value.getConfiguredTarget();
  }

  /**
   * Returns all configured targets currently in the graph.
   */
  public static Iterable<ConfiguredTarget> getAllExistingConfiguredTargets(
      SkyframeExecutor skyframeExecutor) {
    Collection<SkyValue> values =
        Maps.filterKeys(skyframeExecutor.getEvaluatorForTesting().getValues(),
            SkyFunctions.isSkyFunction(SkyFunctions.CONFIGURED_TARGET)).values();
    List<ConfiguredTarget> cts = Lists.newArrayList();
    for (SkyValue value : values) {
      if (value != null) {
        cts.add(((ConfiguredTargetValue) value).getConfiguredTarget());
      }
    }
    return cts;
  }

  /**
   * Returns the target for an existing target value, or {@code null} if there is not an appropriate
   * target value key in the graph.
   *
   * This helper is provided so legacy tests don't need to know about details of skyframe keys.
   */
  @Nullable
  public static Target getExistingTarget(SkyframeExecutor skyframeExecutor,
      Label label) {
    PackageValue value = (PackageValue) getExistingValue(skyframeExecutor,
        PackageValue.key(label.getPackageIdentifier()));
    if (value == null) {
      return null;
    }
    try {
      return value.getPackage().getTarget(label.getName());
    } catch (NoSuchTargetException e) {
      return null;
    }
  }

  /**
   * Returns the error info for an existing target value, or {@code null} if there is not an
   * appropriate target value key in the graph.
   *
   * This helper is provided so legacy tests don't need to know about details of skyframe keys.
   */
  @Nullable
  public static ErrorInfo getExistingFailedTarget(SkyframeExecutor skyframeExecutor, Label label) {
    SkyKey key = TargetMarkerValue.key(label);
    return getExistingError(skyframeExecutor, key);
  }
}
