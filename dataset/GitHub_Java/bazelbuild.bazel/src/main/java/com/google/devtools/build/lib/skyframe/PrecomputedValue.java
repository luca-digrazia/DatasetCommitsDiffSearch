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
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.ActionAnalysisMetadata;
import com.google.devtools.build.lib.analysis.buildinfo.BuildInfoFactory;
import com.google.devtools.build.lib.analysis.buildinfo.BuildInfoFactory.BuildInfoKey;
import com.google.devtools.build.lib.packages.RuleVisibility;
import com.google.devtools.build.lib.pkgcache.PathPackageLocator;
import com.google.devtools.build.lib.skyframe.SkyframeActionExecutor.ConflictException;
import com.google.devtools.build.lib.syntax.SkylarkSemantics;
import com.google.devtools.build.skyframe.Injectable;
import com.google.devtools.build.skyframe.LegacySkyKey;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A value that represents something computed outside of the skyframe framework. These values are
 * "precomputed" from skyframe's perspective and so the graph needs to be prepopulated with them
 * (e.g. via injection).
 */
public final class PrecomputedValue implements SkyValue {
  /**
   * An externally-injected precomputed value. Exists so that modules can inject precomputed values
   * into Skyframe's graph.
   *
   * @see com.google.devtools.build.lib.runtime.BlazeModule#getPrecomputedValues
   */
  public static final class Injected {
    private final Precomputed<?> precomputed;
    private final Supplier<? extends Object> supplier;

    private Injected(Precomputed<?> precomputed, Supplier<? extends Object> supplier) {
      this.precomputed = precomputed;
      this.supplier = supplier;
    }

    public void inject(Injectable injectable) {
      injectable.inject(precomputed.key, new PrecomputedValue(supplier.get()));
    }

    @Override
    public String toString() {
      return precomputed + ": " + supplier.get();
    }
  }

  public static <T> Injected injected(Precomputed<T> precomputed, Supplier<T> value) {
    return new Injected(precomputed, value);
  }

  public static <T> Injected injected(Precomputed<T> precomputed, T value) {
    return new Injected(precomputed, Suppliers.ofInstance(value));
  }

  public static final Precomputed<String> DEFAULTS_PACKAGE_CONTENTS =
      new Precomputed<>(LegacySkyKey.create(SkyFunctions.PRECOMPUTED, "default_pkg"));

  public static final Precomputed<RuleVisibility> DEFAULT_VISIBILITY =
      new Precomputed<>(LegacySkyKey.create(SkyFunctions.PRECOMPUTED, "default_visibility"));

  public static final Precomputed<SkylarkSemantics> SKYLARK_SEMANTICS =
      new Precomputed<>(LegacySkyKey.create(SkyFunctions.PRECOMPUTED, "skylark_semantics"));

  static final Precomputed<UUID> BUILD_ID =
      new Precomputed<>(LegacySkyKey.create(SkyFunctions.PRECOMPUTED, "build_id"));

  static final Precomputed<Map<String, String>> ACTION_ENV =
      new Precomputed<>(LegacySkyKey.create(SkyFunctions.PRECOMPUTED, "action_env"));

  static final Precomputed<ImmutableList<ActionAnalysisMetadata>> COVERAGE_REPORT_KEY =
      new Precomputed<>(LegacySkyKey.create(SkyFunctions.PRECOMPUTED, "coverage_report_actions"));

  public static final Precomputed<Map<BuildInfoKey, BuildInfoFactory>> BUILD_INFO_FACTORIES =
      new Precomputed<>(LegacySkyKey.create(SkyFunctions.PRECOMPUTED, "build_info_factories"));

  static final Precomputed<ImmutableMap<ActionAnalysisMetadata, ConflictException>> BAD_ACTIONS =
      new Precomputed<>(LegacySkyKey.create(SkyFunctions.PRECOMPUTED, "bad_actions"));

  public static final Precomputed<PathPackageLocator> PATH_PACKAGE_LOCATOR =
      new Precomputed<>(LegacySkyKey.create(SkyFunctions.PRECOMPUTED, "path_package_locator"));

  private final Object value;

  public PrecomputedValue(Object value) {
    this.value = Preconditions.checkNotNull(value);
  }

  /**
   * Returns the value of the variable.
   */
  public Object get() {
    return value;
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof PrecomputedValue)) {
      return false;
    }
    PrecomputedValue other = (PrecomputedValue) obj;
    return value.equals(other.value);
  }

  @Override
  public String toString() {
    return "<BuildVariable " + value + ">";
  }

  public static void dependOnBuildId(SkyFunction.Environment env) throws InterruptedException {
    BUILD_ID.get(env);
  }

  /**
   * A helper object corresponding to a variable in Skyframe.
   *
   * <p>Instances do not have internal state.
   */
  public static final class Precomputed<T> {
    private final SkyKey key;

    public Precomputed(SkyKey key) {
      this.key = key;
    }

    @VisibleForTesting
    SkyKey getKeyForTesting() {
      return key;
    }

    /**
     * Retrieves the value of this variable from Skyframe.
     *
     * <p>If the value was not set, an exception will be raised.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public T get(SkyFunction.Environment env) throws InterruptedException {
      PrecomputedValue value = (PrecomputedValue) env.getValue(key);
      if (value == null) {
        return null;
      }
      return (T) value.get();
    }

    /**
     * Injects a new variable value.
     */
    public void set(Injectable injectable, T value) {
      injectable.inject(key, new PrecomputedValue(value));
    }
  }
}
