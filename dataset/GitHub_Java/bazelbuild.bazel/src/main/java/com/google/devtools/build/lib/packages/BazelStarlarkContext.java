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

package com.google.devtools.build.lib.packages;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.analysis.RuleDefinitionContext;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.RepositoryName;
import com.google.devtools.build.lib.skylarkinterface.StarlarkContext;
import javax.annotation.Nullable;

/**
 * Contextual information associated with each Starlark thread created by Bazel.
 */
public class BazelStarlarkContext
    implements StarlarkContext, RuleDefinitionContext, Label.HasRepoMapping {
  private final String toolsRepository;
  @Nullable private final ImmutableMap<String, Class<?>> fragmentNameToClass;
  private final ImmutableMap<RepositoryName, RepositoryName> repoMapping;
  private final SymbolGenerator<?> symbolGenerator;
  @Nullable private final Label analysisRuleLabel;

  /**
   * @param toolsRepository the name of the tools repository, such as "@bazel_tools"
   * @param fragmentNameToClass a map from configuration fragment name to configuration fragment
   *     class, such as "apple" to AppleConfiguration.class
   * @param repoMapping a map from RepositoryName to RepositoryName to be used for external
   * @param symbolGenerator a {@link SymbolGenerator} to be used when creating objects to be
   *     compared using reference equality.
   * @param analysisRuleLabel is the label of the rule for an analysis phase (rule or aspect
   *     'implementation') thread, or null for all other threads.
   */
  // TODO(adonovan): clearly demarcate which fields are defined in which kinds of threads (loading,
  // analysis, workspace, implicit outputs, computed defaults, etc), perhaps by splitting these into
  // separate structs, exactly one of which is populated (plus the common fields). And eliminate
  // SkylarkUtils.Phase.
  // TODO(adonovan): add a PackageIdentifier here, for use by the Starlark Label function.
  // TODO(adonovan): is there any reason not to put the entire RuleContext in this thread, for
  // analysis threads?
  public BazelStarlarkContext(
      String toolsRepository,
      @Nullable ImmutableMap<String, Class<?>> fragmentNameToClass,
      ImmutableMap<RepositoryName, RepositoryName> repoMapping,
      SymbolGenerator<?> symbolGenerator,
      @Nullable Label analysisRuleLabel) {
    this.toolsRepository = toolsRepository;
    this.fragmentNameToClass = fragmentNameToClass;
    this.repoMapping = repoMapping;
    this.symbolGenerator = Preconditions.checkNotNull(symbolGenerator);
    this.analysisRuleLabel = analysisRuleLabel;
  }

  /** Returns the name of the tools repository, such as "@bazel_tools". */
  @Override
  public String getToolsRepository() {
    return toolsRepository;
  }

  /** Returns a map from configuration fragment name to configuration fragment class. */
  @Nullable
  public ImmutableMap<String, Class<?>> getFragmentNameToClass() {
    return fragmentNameToClass;
  }

  /**
   * Returns a map of {@code RepositoryName}s where the keys are repository names that are
   * written in the BUILD files and the values are new repository names chosen by the main
   * repository.
   */
  public ImmutableMap<RepositoryName, RepositoryName> getRepoMapping() {
    return repoMapping;
  }

  public SymbolGenerator<?> getSymbolGenerator() {
    return symbolGenerator;
  }

  /**
   * Returns the label of the rule, if this is an analysis-phase (rule or aspect 'implementation')
   * thread, or null otherwise.
   */
  @Nullable
  public Label getAnalysisRuleLabel() {
    return analysisRuleLabel;
  }
}
