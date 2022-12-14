// Copyright 2014 Google Inc. All rights reserved.
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

package com.google.devtools.build.lib.analysis.config;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.Attribute.SplitTransition;
import com.google.devtools.build.lib.packages.Attribute.Transition;
import com.google.devtools.build.lib.packages.InputFile;
import com.google.devtools.build.lib.packages.PackageGroup;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.Target;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The primary container for all main {@link BuildConfiguration} instances,
 * currently "target", "data", and "host".
 *
 * <p>The target configuration is used for all targets specified on the command
 * line. Data dependencies of targets in the target configuration use the data
 * configuration instead.
 *
 * <p>The host configuration is used for tools that are executed during the
 * build, e. g, compilers.
 *
 * <p>The "related" configurations are also contained in this class.
 */
@ThreadSafe
public final class BuildConfigurationCollection implements Serializable {
  private final ImmutableList<BuildConfiguration> targetConfigurations;
  private final BuildConfiguration hostConfiguration;

  public BuildConfigurationCollection(List<BuildConfiguration> targetConfigurations,
      BuildConfiguration hostConfiguration)
      throws InvalidConfigurationException {
    this.targetConfigurations = ImmutableList.copyOf(targetConfigurations);
    this.hostConfiguration = hostConfiguration;

    // Except for the host configuration (which may be identical across target configs), the other
    // configurations must all have different cache keys or we will end up with problems.
    HashMap<String, BuildConfiguration> cacheKeyConflictDetector = new HashMap<>();
    for (BuildConfiguration config : getAllConfigurations()) {
      if (cacheKeyConflictDetector.containsKey(config.cacheKey())) {
        throw new InvalidConfigurationException("Conflicting configurations: " + config + " & "
            + cacheKeyConflictDetector.get(config.cacheKey()));
      }
      cacheKeyConflictDetector.put(config.cacheKey(), config);
    }
  }

  public static BuildConfiguration configureTopLevelTarget(BuildConfiguration topLevelConfiguration,
      Target toTarget) {
    if (toTarget instanceof InputFile || toTarget instanceof PackageGroup) {
      return null;
    }
    return topLevelConfiguration.getTransitions().toplevelConfigurationHook(toTarget);
  }

  public ImmutableList<BuildConfiguration> getTargetConfigurations() {
    return targetConfigurations;
  }

  /**
   * Returns the host configuration for this collection.
   *
   * <p>Don't use this method. It's limited in that it assumes a single host configuration for
   * the entire collection. This may not be true in the future and more flexible interfaces based
   * on dynamic configurations will likely supplant this interface anyway. Its main utility is
   * to keep Bazel working while dynamic configuration progress is under way.
   */
  public BuildConfiguration getHostConfiguration() {
    return hostConfiguration;
  }

  /**
   * Returns all configurations that can be reached from the target configuration through any kind
   * of configuration transition.
   */
  public Collection<BuildConfiguration> getAllConfigurations() {
    Set<BuildConfiguration> result = new LinkedHashSet<>();
    for (BuildConfiguration config : targetConfigurations) {
      result.addAll(config.getAllReachableConfigurations());
    }
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BuildConfigurationCollection)) {
      return false;
    }
    BuildConfigurationCollection that = (BuildConfigurationCollection) obj;
    return this.targetConfigurations.equals(that.targetConfigurations);
  }

  @Override
  public int hashCode() {
    return targetConfigurations.hashCode();
  }

  /**
   * Prints the configuration graph in dot format to the given print stream. This is only intended
   * for debugging.
   */
  public void dumpAsDotGraph(PrintStream out) {
    out.println("digraph g {");
    out.println("  ratio = 0.3;");
    for (BuildConfiguration config : getAllConfigurations()) {
      String from = config.shortCacheKey();
      for (Map.Entry<? extends Transition, ConfigurationHolder> entry :
          config.getTransitions().getTransitionTable().entrySet()) {
        BuildConfiguration toConfig = entry.getValue().getConfiguration();
        if (toConfig == config) {
          continue;
        }
        String to = toConfig == null ? "ERROR" : toConfig.shortCacheKey();
        out.println("  \"" + from + "\" -> \"" + to + "\" [label=\"" + entry.getKey() + "\"]");
      }
    }
    out.println("}");
  }

  /**
   * The outgoing transitions for a build configuration.
   */
  public static class Transitions implements Serializable {
    protected final BuildConfiguration configuration;

    /**
     * Look up table for the configuration transitions, i.e., HOST, DATA, etc.
     */
    private final Map<? extends Transition, ConfigurationHolder> transitionTable;

    // TODO(bazel-team): Consider merging transitionTable into this.
    private final ListMultimap<? super SplitTransition<?>, BuildConfiguration> splitTransitionTable;

    public Transitions(BuildConfiguration configuration,
        Map<? extends Transition, ConfigurationHolder> transitionTable,
        ListMultimap<? extends SplitTransition<?>, BuildConfiguration> splitTransitionTable) {
      this.configuration = configuration;
      this.transitionTable = ImmutableMap.copyOf(transitionTable);
      this.splitTransitionTable = ImmutableListMultimap.copyOf(splitTransitionTable);
    }

    public Transitions(BuildConfiguration configuration,
        Map<? extends Transition, ConfigurationHolder> transitionTable) {
      this(configuration, transitionTable,
          ImmutableListMultimap.<SplitTransition<?>, BuildConfiguration>of());
    }

    public Map<? extends Transition, ConfigurationHolder> getTransitionTable() {
      return transitionTable;
    }

    public ListMultimap<? super SplitTransition<?>, BuildConfiguration> getSplitTransitionTable() {
      return splitTransitionTable;
    }

    public List<BuildConfiguration> getSplitConfigurations(SplitTransition<?> transition) {
      if (splitTransitionTable.containsKey(transition)) {
        return splitTransitionTable.get(transition);
      } else {
        Preconditions.checkState(transition.defaultsToSelf());
        return ImmutableList.of(configuration);
      }
    }

    /**
     * Adds all configurations that are directly reachable from this configuration through
     * any kind of configuration transition.
     */
    public void addDirectlyReachableConfigurations(Collection<BuildConfiguration> queue) {
      for (ConfigurationHolder holder : transitionTable.values()) {
        if (holder.configuration != null) {
          queue.add(holder.configuration);
        }
      }
      queue.addAll(splitTransitionTable.values());
    }

    /**
     * Artifacts need an owner in Skyframe. By default it's the same configuration as what
     * the configured target has, but it can be overridden if necessary.
     *
     * @return the artifact owner configuration
     */
    public BuildConfiguration getArtifactOwnerConfiguration() {
      return configuration;
    }

    /**
     * Returns the new configuration after traversing a dependency edge with a
     * given configuration transition.
     *
     * @param configurationTransition the configuration transition
     * @return the new configuration
     */
    public BuildConfiguration getConfiguration(Transition configurationTransition) {
      ConfigurationHolder holder = transitionTable.get(configurationTransition);
      if (holder == null && configurationTransition.defaultsToSelf()) {
        return configuration;
      }
      return holder.configuration;
    }

    /**
     * Arbitrary configuration transitions can be implemented by overriding this hook.
     */
    @SuppressWarnings("unused")
    public BuildConfiguration configurationHook(Rule fromTarget,
        Attribute attribute, Target toTarget, BuildConfiguration toConfiguration) {
      return toConfiguration;
    }

    /**
     * Associating configurations to top-level targets can be implemented by overriding this hook.
     */
    @SuppressWarnings("unused")
    public BuildConfiguration toplevelConfigurationHook(Target toTarget) {
      return configuration;
    }
  }

  /**
   * A holder class for {@link BuildConfiguration} instances that allows {@code null} values,
   * because none of the Table implementations allow them.
   */
  public static final class ConfigurationHolder implements Serializable {
    private final BuildConfiguration configuration;

    public ConfigurationHolder(BuildConfiguration configuration) {
      this.configuration = configuration;
    }

    public BuildConfiguration getConfiguration() {
      return configuration;
    }

    @Override
    public int hashCode() {
      return configuration == null ? 0 : configuration.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof ConfigurationHolder)) {
        return false;
      }
      return Objects.equals(configuration, ((ConfigurationHolder) o).configuration);
    }
  }
}
