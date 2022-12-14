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
package com.google.devtools.build.lib.analysis;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.ExtraActionArtifactsProvider.ExtraArtifactSet;
import com.google.devtools.build.lib.analysis.LicensesProvider.TargetLicense;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.constraints.ConstraintSemantics;
import com.google.devtools.build.lib.analysis.constraints.EnvironmentCollection;
import com.google.devtools.build.lib.analysis.constraints.SupportedEnvironments;
import com.google.devtools.build.lib.analysis.constraints.SupportedEnvironmentsProvider;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.License;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.TargetUtils;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.rules.extra.ExtraActionMapProvider;
import com.google.devtools.build.lib.rules.extra.ExtraActionSpec;
import com.google.devtools.build.lib.rules.test.ExecutionInfoProvider;
import com.google.devtools.build.lib.rules.test.InstrumentedFilesProvider;
import com.google.devtools.build.lib.rules.test.TestActionBuilder;
import com.google.devtools.build.lib.rules.test.TestProvider;
import com.google.devtools.build.lib.rules.test.TestProvider.TestParams;
import com.google.devtools.build.lib.syntax.ClassObject;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Builder class for analyzed rule instances (i.e., instances of {@link ConfiguredTarget}).
 */
public final class RuleConfiguredTargetBuilder {
  private final RuleContext ruleContext;
  private final Map<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider> providers =
      new LinkedHashMap<>();
  private final ImmutableMap.Builder<String, Object> skylarkProviders = ImmutableMap.builder();
  private final ImmutableMap.Builder<String, NestedSet<Artifact>> outputGroups =
      ImmutableMap.builder();

  /** These are supported by all configured targets and need to be specially handled. */
  private NestedSet<Artifact> filesToBuild = NestedSetBuilder.emptySet(Order.STABLE_ORDER);
  private RunfilesSupport runfilesSupport;
  private Artifact executable;
  private ImmutableList<Artifact> mandatoryStampFiles;
  private ImmutableSet<Action> actionsWithoutExtraAction = ImmutableSet.of();

  public RuleConfiguredTargetBuilder(RuleContext ruleContext) {
    this.ruleContext = ruleContext;
    add(LicensesProvider.class, initializeLicensesProvider());
    add(VisibilityProvider.class, new VisibilityProviderImpl(ruleContext.getVisibility()));
  }

  /**
   * Constructs the RuleConfiguredTarget instance based on the values set for this Builder.
   */
  public ConfiguredTarget build() {
    if (ruleContext.getConfiguration().enforceConstraints()) {
      checkConstraints();
    }
    if (ruleContext.hasErrors()) {
      return null;
    }

    ImmutableMap<String, NestedSet<Artifact>> outputGroupMap = outputGroups.build();
    if (!outputGroupMap.isEmpty()) {
      add(TopLevelArtifactProvider.class, new TopLevelArtifactProvider(outputGroupMap));
    }

    FilesToRunProvider filesToRunProvider = new FilesToRunProvider(ruleContext.getLabel(),
        RuleContext.getFilesToRun(runfilesSupport, filesToBuild), runfilesSupport, executable);
    add(FileProvider.class, new FileProvider(ruleContext.getLabel(), filesToBuild));
    add(FilesToRunProvider.class, filesToRunProvider);

    // Create test action and artifacts if target was successfully initialized
    // and is a test.
    if (TargetUtils.isTestRule(ruleContext.getTarget())) {
      Preconditions.checkState(runfilesSupport != null);
      add(TestProvider.class, initializeTestProvider(filesToRunProvider));
    }
    add(ExtraActionArtifactsProvider.class, initializeExtraActions());
    return new RuleConfiguredTarget(
        ruleContext, mandatoryStampFiles, skylarkProviders.build(), providers);
  }

  /**
   * Invokes Blaze's constraint enforcement system: checks that this rule's dependencies
   * support its environments and reports appropriate errors if violations are found. Also
   * publishes this rule's supported environments for the rules that depend on it.
   */
  private void checkConstraints() {
    if (providers.get(SupportedEnvironmentsProvider.class) == null) {
      // Note the "environment" rule sets its own SupportedEnvironmentProvider instance, so this
      // logic is for "normal" rules that just want to apply default semantics.
      EnvironmentCollection supportedEnvironments =
          ConstraintSemantics.getSupportedEnvironments(ruleContext);
      if (supportedEnvironments != null) {
        add(SupportedEnvironmentsProvider.class, new SupportedEnvironments(supportedEnvironments));
        ConstraintSemantics.checkConstraints(ruleContext, supportedEnvironments);
      }
    }
  }

  private TestProvider initializeTestProvider(FilesToRunProvider filesToRunProvider) {
    int explicitShardCount = ruleContext.attributes().get("shard_count", Type.INTEGER);
    if (explicitShardCount < 0
        && ruleContext.getRule().isAttributeValueExplicitlySpecified("shard_count")) {
      ruleContext.attributeError("shard_count", "Must not be negative.");
    }
    if (explicitShardCount > 50) {
      ruleContext.attributeError("shard_count",
          "Having more than 50 shards is indicative of poor test organization. "
          + "Please reduce the number of shards.");
    }
    final TestParams testParams = new TestActionBuilder(ruleContext)
        .setFilesToRunProvider(filesToRunProvider)
        .setInstrumentedFiles(findProvider(InstrumentedFilesProvider.class))
        .setExecutionRequirements(findProvider(ExecutionInfoProvider.class))
        .setShardCount(explicitShardCount)
        .build();
    final ImmutableList<String> testTags =
        ImmutableList.copyOf(ruleContext.getRule().getRuleTags());
    return new TestProvider(testParams, testTags);
  }

  private LicensesProvider initializeLicensesProvider() {
    if (!ruleContext.getConfiguration().checkLicenses()) {
      return LicensesProviderImpl.EMPTY;
    }

    NestedSetBuilder<TargetLicense> builder = NestedSetBuilder.linkOrder();
    BuildConfiguration configuration = ruleContext.getConfiguration();
    Rule rule = ruleContext.getRule();
    License toolOutputLicense = rule.getToolOutputLicense(ruleContext.attributes());
    if (configuration.isHostConfiguration() && toolOutputLicense != null) {
      if (toolOutputLicense != License.NO_LICENSE) {
        builder.add(new TargetLicense(rule.getLabel(), toolOutputLicense));
      }
    } else {
      if (rule.getLicense() != License.NO_LICENSE) {
        builder.add(new TargetLicense(rule.getLabel(), rule.getLicense()));
      }

      for (TransitiveInfoCollection dep : ruleContext.getConfiguredTargetMap().values()) {
        LicensesProvider provider = dep.getProvider(LicensesProvider.class);
        if (provider != null) {
          builder.addTransitive(provider.getTransitiveLicenses());
        }
      }
    }

    return new LicensesProviderImpl(builder.build());
  }

  /**
   * Scans {@code action_listeners} associated with this build to see if any
   * {@code extra_actions} should be added to this configured target. If any
   * action_listeners are present, a partial visit of the artifact/action graph
   * is performed (for as long as actions found are owned by this {@link
   * ConfiguredTarget}). Any actions that match the {@code action_listener}
   * get an {@code extra_action} associated. The output artifacts of the
   * extra_action are reported to the {@link AnalysisEnvironment} for
   * bookkeeping.
   */
  private ExtraActionArtifactsProvider initializeExtraActions() {
    BuildConfiguration configuration = ruleContext.getConfiguration();
    if (configuration.isHostConfiguration()) {
      return ExtraActionArtifactsProvider.EMPTY;
    }

    ImmutableList<Artifact> extraActionArtifacts = ImmutableList.of();
    NestedSetBuilder<ExtraArtifactSet> builder = NestedSetBuilder.stableOrder();

    List<Label> actionListenerLabels = configuration.getActionListeners();
    if (!actionListenerLabels.isEmpty()
        && ruleContext.getRule().getAttributeDefinition(":action_listener") != null) {
      ExtraActionsVisitor visitor = new ExtraActionsVisitor(ruleContext,
          computeMnemonicsToExtraActionMap());

      // The action list is modified within the body of the loop by the addExtraAction() call,
      // thus the copy
      for (Action action : ImmutableList.copyOf(
          ruleContext.getAnalysisEnvironment().getRegisteredActions())) {
        if (!actionsWithoutExtraAction.contains(action)) {
          visitor.addExtraAction(action);
        }
      }

      extraActionArtifacts = visitor.getAndResetExtraArtifacts();
      if (!extraActionArtifacts.isEmpty()) {
        builder.add(ExtraArtifactSet.of(ruleContext.getLabel(), extraActionArtifacts));
      }
    }

    // Add extra action artifacts from dependencies
    for (TransitiveInfoCollection dep : ruleContext.getConfiguredTargetMap().values()) {
      ExtraActionArtifactsProvider provider =
          dep.getProvider(ExtraActionArtifactsProvider.class);
      if (provider != null) {
        builder.addTransitive(provider.getTransitiveExtraActionArtifacts());
      }
    }

    if (mandatoryStampFiles != null && !mandatoryStampFiles.isEmpty()) {
      builder.add(ExtraArtifactSet.of(ruleContext.getLabel(), mandatoryStampFiles));
    }

    if (extraActionArtifacts.isEmpty() && builder.isEmpty()) {
      return ExtraActionArtifactsProvider.EMPTY;
    }
    return new ExtraActionArtifactsProvider(extraActionArtifacts, builder.build());
  }

  /**
   * Populates the configuration specific mnemonicToExtraActionMap
   * based on all action_listers selected by the user (via the blaze option
   * --experimental_action_listener=<target>).
   */
  private Multimap<String, ExtraActionSpec> computeMnemonicsToExtraActionMap() {
    // We copy the multimap here every time. This could be expensive.
    Multimap<String, ExtraActionSpec> mnemonicToExtraActionMap = HashMultimap.create();
    for (TransitiveInfoCollection actionListener :
        ruleContext.getPrerequisites(":action_listener", Mode.HOST)) {
      ExtraActionMapProvider provider = actionListener.getProvider(ExtraActionMapProvider.class);
      if (provider == null) {
        ruleContext.ruleError(String.format(
            "Unable to match experimental_action_listeners to this rule. "
            + "Specified target %s is not an action_listener rule",
            actionListener.getLabel().toString()));
      } else {
        mnemonicToExtraActionMap.putAll(provider.getExtraActionMap());
      }
    }
    return mnemonicToExtraActionMap;
  }

  private <T extends TransitiveInfoProvider> T findProvider(Class<T> clazz) {
    return clazz.cast(providers.get(clazz));
  }

  /**
   * Add a specific provider with a given value.
   */
  public <T extends TransitiveInfoProvider> RuleConfiguredTargetBuilder add(Class<T> key, T value) {
    return addProvider(key, value);
  }

  /**
   * Add a specific provider with a given value.
   */
  public RuleConfiguredTargetBuilder addProvider(
      Class<? extends TransitiveInfoProvider> key, TransitiveInfoProvider value) {
    Preconditions.checkNotNull(key);
    Preconditions.checkNotNull(value);
    AnalysisUtils.checkProvider(key);
    providers.put(key, value);
    return this;
  }

  /**
   * Add multiple providers with given values.
   */
  public RuleConfiguredTargetBuilder addProviders(
      Map<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider> providers) {
    for (Entry<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider> provider :
        providers.entrySet()) {
      addProvider(provider.getKey(), provider.getValue());
    }
    return this;
  }

  /**
   * Add a Skylark transitive info. The provider value must be safe (i.e. a String, a Boolean,
   * an Integer, an Artifact, a Label, None, a Java TransitiveInfoProvider or something composed
   * from these in Skylark using lists, sets, structs or dicts). Otherwise an EvalException is
   * thrown.
   */
  public RuleConfiguredTargetBuilder addSkylarkTransitiveInfo(
      String name, Object value, Location loc) throws EvalException {
    try {
      checkSkylarkObjectSafe(value);
    } catch (IllegalArgumentException e) {
      throw new EvalException(loc, String.format("Value of provider '%s' is of an illegal type: %s",
          name, e.getMessage()));
    }
    skylarkProviders.put(name, value);
    return this;
  }

  /**
   * Add a Skylark transitive info. The provider value must be safe.
   */
  public RuleConfiguredTargetBuilder addSkylarkTransitiveInfo(
      String name, Object value) {
    checkSkylarkObjectSafe(value);
    skylarkProviders.put(name, value);
    return this;
  }

  /**
   * Check if the value provided by a Skylark provider is safe (i.e. can be a
   * TransitiveInfoProvider value).
   */
  private void checkSkylarkObjectSafe(Object value) {
    if (!isSimpleSkylarkObjectSafe(value.getClass())
        // Java transitive Info Providers are accessible from Skylark.
        || value instanceof TransitiveInfoProvider) {
      checkCompositeSkylarkObjectSafe(value);
    }
  }

  private void checkCompositeSkylarkObjectSafe(Object object) {
    if (object instanceof SkylarkList) {
      SkylarkList list = (SkylarkList) object;
      if (list == SkylarkList.EMPTY_LIST || isSimpleSkylarkObjectSafe(list.getGenericType())) {
        // Try not to iterate over the list if avoidable.
        return;
      }
      // The list can be a tuple or a list of composite items.
      for (Object listItem : list) {
        checkSkylarkObjectSafe(listItem);
      }
      return;
    } else if (object instanceof SkylarkNestedSet) {
      // SkylarkNestedSets cannot have composite items.
      Class<?> genericType = ((SkylarkNestedSet) object).getGenericType();
      if (!genericType.equals(Object.class) && !isSimpleSkylarkObjectSafe(genericType)) {
        throw new IllegalArgumentException(EvalUtils.getDataTypeName(genericType));
      }
      return;
    } else if (object instanceof Map<?, ?>) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
        checkSkylarkObjectSafe(entry.getKey());
        checkSkylarkObjectSafe(entry.getValue());
      }
      return;
    } else if (object instanceof ClassObject) {
      ClassObject struct = (ClassObject) object;
      for (String key : struct.getKeys()) {
        checkSkylarkObjectSafe(struct.getValue(key));
      }
      return;
    }
    throw new IllegalArgumentException(EvalUtils.getDataTypeName(object));
  }

  private boolean isSimpleSkylarkObjectSafe(Class<?> type) {
    return type.equals(String.class)
        || type.equals(Integer.class)
        || type.equals(Boolean.class)
        || Artifact.class.isAssignableFrom(type)
        || type.equals(Label.class)
        || type.equals(Environment.NoneType.class);
  }

  /**
   * Set the runfiles support for executable targets.
   */
  public RuleConfiguredTargetBuilder setRunfilesSupport(
      RunfilesSupport runfilesSupport, Artifact executable) {
    this.runfilesSupport = runfilesSupport;
    this.executable = executable;
    return this;
  }

  /**
   * Set the files to build.
   */
  public RuleConfiguredTargetBuilder setFilesToBuild(NestedSet<Artifact> filesToBuild) {
    this.filesToBuild = filesToBuild;
    return this;
  }

  /**
   * Add an output group.
   */
  public RuleConfiguredTargetBuilder addOutputGroup(String name, NestedSet<Artifact> artifacts) {
    outputGroups.put(name, artifacts);
    return this;
  }

  /**
   * Add an output group.
   */
  public RuleConfiguredTargetBuilder addOutputGroup(String name, Artifact artifact) {
    outputGroups.put(name, NestedSetBuilder.create(Order.STABLE_ORDER, artifact));
    return this;
  }

  /**
   * Set the baseline coverage Artifacts.
   */
  public RuleConfiguredTargetBuilder setBaselineCoverageArtifacts(
      Collection<Artifact> artifacts) {
    return add(BaselineCoverageArtifactsProvider.class,
        new BaselineCoverageArtifactsProvider(ImmutableList.copyOf(artifacts)));
  }

  /**
   * Set the mandatory stamp files.
   */
  public RuleConfiguredTargetBuilder setMandatoryStampFiles(ImmutableList<Artifact> files) {
    this.mandatoryStampFiles = files;
    return this;
  }

  /**
   * Set the extra action pseudo actions.
   */
  public RuleConfiguredTargetBuilder setActionsWithoutExtraAction(
      ImmutableSet<Action> actions) {
    this.actionsWithoutExtraAction = actions;
    return this;
  }
}
