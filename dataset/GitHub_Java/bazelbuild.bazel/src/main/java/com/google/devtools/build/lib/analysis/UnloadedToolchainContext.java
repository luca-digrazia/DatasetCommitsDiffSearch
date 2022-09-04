// Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.analysis.platform.PlatformInfo;
import com.google.devtools.build.lib.analysis.platform.ToolchainTypeInfo;
import com.google.devtools.build.lib.cmdline.Label;
import java.util.Set;

/**
 * Represents the state of toolchain resolution once the specific required toolchains have been
 * determined, but before the toolchain dependencies have been resolved.
 */
@AutoValue
public abstract class UnloadedToolchainContext implements ToolchainContext {

  static Builder builder() {
    return new AutoValue_UnloadedToolchainContext.Builder();
  }

  /** Builder class to help create the {@link UnloadedToolchainContext}. */
  @AutoValue.Builder
  public interface Builder {
    /** Sets the selected execution platform that these toolchains use. */
    Builder setExecutionPlatform(PlatformInfo executionPlatform);

    /** Sets the target platform that these toolchains generate output for. */
    Builder setTargetPlatform(PlatformInfo targetPlatform);

    /** Sets the toolchain types that were requested. */
    Builder setRequiredToolchainTypes(Set<ToolchainTypeInfo> requiredToolchainTypes);

    Builder setToolchainTypeToResolved(
        ImmutableBiMap<ToolchainTypeInfo, Label> toolchainTypeToResolved);

    UnloadedToolchainContext build();
  }

  /** The map of toolchain type to resolved toolchain to be used. */
  abstract ImmutableBiMap<ToolchainTypeInfo, Label> toolchainTypeToResolved();

  @Override
  public ImmutableSet<Label> resolvedToolchainLabels() {
    return toolchainTypeToResolved().values();
  }
}
