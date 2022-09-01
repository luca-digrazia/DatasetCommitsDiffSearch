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
package com.google.devtools.build.lib.rules.test;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;

import java.util.Map;

/**
 * This provider can be implemented by rules which need special environments to run in (especially
 * tests).
 */
@Immutable
public final class ExecutionInfoProvider implements TransitiveInfoProvider {

  private final ImmutableMap<String, String> executionInfo;

  public ExecutionInfoProvider(Map<String, String> requirements) {
    this.executionInfo = ImmutableMap.copyOf(requirements);
  }

  /**
   * Returns a map to indicate special execution requirements, such as hardware
   * platforms, web browsers, etc. Rule tags, such as "requires-XXX", may also be added
   * as keys to the map.
   */
  public ImmutableMap<String, String> getExecutionInfo() {
    return executionInfo;
  }
}
