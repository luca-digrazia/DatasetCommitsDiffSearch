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
package com.google.devtools.build.lib.skyframe;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.ActionAnalysisMetadata;
import com.google.devtools.build.lib.actions.ActionLookupValue;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.configuredtargets.RuleConfiguredTarget;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.lib.packages.Package;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import java.math.BigInteger;
import javax.annotation.Nullable;

/** A configured target in the context of a Skyframe graph. */
@Immutable
@ThreadSafe
@AutoCodec(explicitlyAllowClass = RuleConfiguredTarget.class)
@VisibleForTesting
public final class RuleConfiguredTargetValue extends ActionLookupValue
    implements ConfiguredTargetValue {

  // This variable is non-final because it may be clear()ed to save memory. It is null only after
  // clear(true) is called.
  @Nullable private RuleConfiguredTarget configuredTarget;
  private final ImmutableList<ActionAnalysisMetadata> actions;
  private final ImmutableMap<Artifact, Integer> generatingActionIndex;

  // May be null either after clearing or because transitive packages are not tracked.
  @Nullable private NestedSet<Package> transitivePackagesForPackageRootResolution;

  // Transitive packages are not serialized.
  @AutoCodec.Instantiator
  RuleConfiguredTargetValue(RuleConfiguredTarget configuredTarget) {
    this(
        configuredTarget,
        /*transitivePackagesForPackageRootResolution=*/ null,
        /*nonceVersion=*/ null);
  }

  RuleConfiguredTargetValue(
      RuleConfiguredTarget configuredTarget,
      @Nullable NestedSet<Package> transitivePackagesForPackageRootResolution,
      @Nullable BigInteger nonceVersion) {
    super(nonceVersion);
    this.configuredTarget = Preconditions.checkNotNull(configuredTarget);
    this.transitivePackagesForPackageRootResolution = transitivePackagesForPackageRootResolution;
    // These are specifically *not* copied to save memory.
    this.actions = configuredTarget.getActions();
    this.generatingActionIndex = configuredTarget.getGeneratingActionIndex();
  }

  @VisibleForTesting
  @Override
  public ConfiguredTarget getConfiguredTarget() {
    Preconditions.checkNotNull(configuredTarget);
    return configuredTarget;
  }

  @VisibleForTesting
  @Override
  public ImmutableList<ActionAnalysisMetadata> getActions() {
    return actions;
  }

  @Override
  protected ImmutableMap<Artifact, Integer> getGeneratingActionIndex() {
    return generatingActionIndex;
  }

  @Override
  public NestedSet<Package> getTransitivePackagesForPackageRootResolution() {
    return Preconditions.checkNotNull(transitivePackagesForPackageRootResolution);
  }

  @Override
  public void clear(boolean clearEverything) {
    Preconditions.checkNotNull(configuredTarget);
    Preconditions.checkNotNull(transitivePackagesForPackageRootResolution);
    if (clearEverything) {
      configuredTarget = null;
    }
    transitivePackagesForPackageRootResolution = null;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("generatingActionIndex", generatingActionIndex)
        .add("actions", actions)
        .add("configuredTarget", configuredTarget)
        .toString();
  }
}
