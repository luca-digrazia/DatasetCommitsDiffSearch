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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.ActionOwner;
import com.google.devtools.build.lib.actions.ActionRegistry;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ArtifactOwner;
import com.google.devtools.build.lib.actions.Root;
import com.google.devtools.build.lib.analysis.ConfiguredRuleClassProvider.PrerequisiteValidator;
import com.google.devtools.build.lib.analysis.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.analysis.actions.ActionConstructionContext;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration.Fragment;
import com.google.devtools.build.lib.analysis.config.ConfigMatchingProvider;
import com.google.devtools.build.lib.collect.ImmutableSortedKeyListMultimap;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.Attribute.ConfigurationTransition;
import com.google.devtools.build.lib.packages.Attribute.SplitTransition;
import com.google.devtools.build.lib.packages.AttributeMap;
import com.google.devtools.build.lib.packages.FileTarget;
import com.google.devtools.build.lib.packages.ImplicitOutputsFunction;
import com.google.devtools.build.lib.packages.InputFile;
import com.google.devtools.build.lib.packages.OutputFile;
import com.google.devtools.build.lib.packages.PackageSpecification;
import com.google.devtools.build.lib.packages.RawAttributeMapper;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleErrorConsumer;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.packages.TargetUtils;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.rules.fileset.FilesetProvider;
import com.google.devtools.build.lib.shell.ShellUtils;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.FilesetEntry;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.util.FileTypeSet;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A helper class for rule implementations building and initialization. Objects of this
 * class are intended to be passed to the builder for the configured target, which then creates the
 * configured target.
 */
public final class RuleContext extends TargetContext
    implements ActionConstructionContext, ActionRegistry, RuleErrorConsumer {

  /**
   * The configured version of FilesetEntry.
   */
  @Immutable
  public static final class ConfiguredFilesetEntry {
    private final FilesetEntry entry;
    private final TransitiveInfoCollection src;
    private final ImmutableList<TransitiveInfoCollection> files;

    ConfiguredFilesetEntry(FilesetEntry entry, TransitiveInfoCollection src) {
      this.entry = entry;
      this.src = src;
      this.files = null;
    }

    ConfiguredFilesetEntry(FilesetEntry entry, ImmutableList<TransitiveInfoCollection> files) {
      this.entry = entry;
      this.src = null;
      this.files = files;
    }

    public FilesetEntry getEntry() {
      return entry;
    }

    public TransitiveInfoCollection getSrc() {
      return src;
    }

    /**
     * Targets from FilesetEntry.files, or null if the user omitted it.
     */
    @Nullable
    public List<TransitiveInfoCollection> getFiles() {
      return files;
    }
  }

  static final String HOST_CONFIGURATION_PROGRESS_TAG = "for host";

  private final Rule rule;
  private final ListMultimap<String, ConfiguredTarget> targetMap;
  private final ListMultimap<String, ConfiguredFilesetEntry> filesetEntryMap;
  private final Set<ConfigMatchingProvider> configConditions;
  private final AttributeMap attributes;
  private final ImmutableSet<String> features;
  private final Map<String, Attribute> attributeMap;

  private ActionOwner actionOwner;

  /* lazily computed cache for Make variables, computed from the above. See get... method */
  private transient ConfigurationMakeVariableContext configurationMakeVariableContext = null;

  private RuleContext(Builder builder, ListMultimap<String, ConfiguredTarget> targetMap,
      ListMultimap<String, ConfiguredFilesetEntry> filesetEntryMap,
      Set<ConfigMatchingProvider> configConditions, ImmutableSet<String> features,
      Map<String, Attribute> attributeMap) {
    super(builder.env, builder.rule, builder.configuration, builder.prerequisiteMap.get(null),
        builder.visibility);
    this.rule = builder.rule;
    this.targetMap = targetMap;
    this.filesetEntryMap = filesetEntryMap;
    this.configConditions = configConditions;
    this.attributes =
        ConfiguredAttributeMapper.of(builder.rule, configConditions);
    this.features = features;
    this.attributeMap = attributeMap;
  }

  @Override
  public Rule getRule() {
    return rule;
  }

  /**
   * Returns the workspace name for the rule.
   */
  public String getWorkspaceName() {
    return rule.getWorkspaceName();
  }

  /**
   * The configuration conditions that trigger this rule's configurable attributes.
   */
  Set<ConfigMatchingProvider> getConfigConditions() {
    return configConditions;
  }

  /**
   * Returns the host configuration for this rule; keep in mind that there may be multiple different
   * host configurations, even during a single build.
   */
  public BuildConfiguration getHostConfiguration() {
    BuildConfiguration configuration = getConfiguration();
    // Note: the Builder checks that the configuration is non-null.
    return configuration.getConfiguration(ConfigurationTransition.HOST);
  }

  /**
   * Accessor for the Rule's attribute values.
   */
  public AttributeMap attributes() {
    return attributes;
  }

  /**
   * Returns whether this instance is known to have errors at this point during analysis. Do not
   * call this method after the initializationHook has returned.
   */
  public boolean hasErrors() {
    return getAnalysisEnvironment().hasErrors();
  }

  /**
   * Returns an immutable map from attribute name to list of configured targets for that attribute.
   */
  public ListMultimap<String, ? extends TransitiveInfoCollection> getConfiguredTargetMap() {
    return targetMap;
  }

  /**
   * Returns an immutable map from attribute name to list of fileset entries.
   */
  public ListMultimap<String, ConfiguredFilesetEntry> getFilesetEntryMap() {
    return filesetEntryMap;
  }

  @Override
  public ActionOwner getActionOwner() {
    if (actionOwner == null) {
      actionOwner = new RuleActionOwner(rule, getConfiguration());
    }
    return actionOwner;
  }

  /**
   * Returns a configuration fragment for this this target.
   */
  @Nullable
  public <T extends Fragment> T getFragment(Class<T> fragment) {
    // TODO(bazel-team): The fragments can also be accessed directly through BuildConfiguration.
    // Can we lock that down somehow?
    Preconditions.checkArgument(
        rule.getRuleClassObject().isLegalConfigurationFragment(fragment),
        "%s does not have access to %s", rule.getRuleClass(), fragment);
    return getConfiguration().getFragment(fragment);
  }

  @Override
  public ArtifactOwner getOwner() {
    return getAnalysisEnvironment().getOwner();
  }

  // TODO(bazel-team): This class could be simpler if Rule and BuildConfiguration classes
  // were immutable. Then we would need to store only references those two.
  @Immutable
  private static final class RuleActionOwner implements ActionOwner {
    private final Label label;
    private final Location location;
    private final String configurationName;
    private final String mnemonic;
    private final String targetKind;
    private final String shortCacheKey;
    private final boolean hostConfiguration;

    private RuleActionOwner(Rule rule, BuildConfiguration configuration) {
      this.label = rule.getLabel();
      this.location = rule.getLocation();
      this.targetKind = rule.getTargetKind();
      this.configurationName = configuration.getShortName();
      this.mnemonic = configuration.getMnemonic();
      this.shortCacheKey = configuration.shortCacheKey();
      this.hostConfiguration = configuration.isHostConfiguration();
    }

    @Override
    public Location getLocation() {
      return location;
    }

    @Override
    public Label getLabel() {
      return label;
    }

    @Override
    public String getConfigurationName() {
      return configurationName;
    }

    @Override
    public String getConfigurationMnemonic() {
      return mnemonic;
    }

    @Override
    public String getConfigurationShortCacheKey() {
      return shortCacheKey;
    }

    @Override
    public String getTargetKind() {
      return targetKind;
    }

    @Override
    public String getAdditionalProgressInfo() {
      return hostConfiguration ? HOST_CONFIGURATION_PROGRESS_TAG : null;
    }
  }

  @Override
  public void registerAction(Action... action) {
    getAnalysisEnvironment().registerAction(action);
  }

  /**
   * Convenience function for subclasses to report non-attribute-specific
   * errors in the current rule.
   */
  @Override
  public void ruleError(String message) {
    reportError(rule.getLocation(), prefixRuleMessage(message));
  }

  /**
   * Convenience function for subclasses to report non-attribute-specific
   * warnings in the current rule.
   */
  @Override
  public void ruleWarning(String message) {
    reportWarning(rule.getLocation(), prefixRuleMessage(message));
  }

  /**
   * Convenience function for subclasses to report attribute-specific errors in
   * the current rule.
   *
   * <p>If the name of the attribute starts with <code>$</code>
   * it is replaced with a string <code>(an implicit dependency)</code>.
   */
  @Override
  public void attributeError(String attrName, String message) {
    reportError(rule.getAttributeLocation(attrName),
                prefixAttributeMessage(Attribute.isImplicit(attrName)
                                           ? "(an implicit dependency)"
                                           : attrName,
                                       message));
  }

  /**
   * Like attributeError, but does not mark the configured target as errored.
   *
   * <p>If the name of the attribute starts with <code>$</code>
   * it is replaced with a string <code>(an implicit dependency)</code>.
   */
  @Override
  public void attributeWarning(String attrName, String message) {
    reportWarning(rule.getAttributeLocation(attrName),
                  prefixAttributeMessage(Attribute.isImplicit(attrName)
                                             ? "(an implicit dependency)"
                                             : attrName,
                                         message));
  }

  private String prefixAttributeMessage(String attrName, String message) {
    return "in " + attrName + " attribute of "
           + rule.getRuleClass() + " rule "
           + getLabel() + ": " + message;
  }

  private String prefixRuleMessage(String message) {
    return "in " + rule.getRuleClass() + " rule "
           + getLabel() + ": " + message;
  }

  private void reportError(Location location, String message) {
    getAnalysisEnvironment().getEventHandler().handle(Event.error(location, message));
  }

  private void reportWarning(Location location, String message) {
    getAnalysisEnvironment().getEventHandler().handle(Event.warn(location, message));
  }

  /**
   * Returns an artifact beneath the root of either the "bin" or "genfiles"
   * tree, whose path is based on the name of this target and the current
   * configuration.  The choice of which tree to use is based on the rule with
   * which this target (which must be an OutputFile or a Rule) is associated.
   */
  public Artifact createOutputArtifact() {
    return internalCreateOutputArtifact(getTarget());
  }

  /**
   * Returns the output artifact of an {@link OutputFile} of this target.
   *
   * @see #createOutputArtifact()
   */
  public Artifact createOutputArtifact(OutputFile out) {
    return internalCreateOutputArtifact(out);
  }

  /**
   * Implementation for {@link #createOutputArtifact()} and
   * {@link #createOutputArtifact(OutputFile)}. This is private so that
   * {@link #createOutputArtifact(OutputFile)} can have a more specific
   * signature.
   */
  private Artifact internalCreateOutputArtifact(Target target) {
    Root root = getBinOrGenfilesDirectory();
    return getAnalysisEnvironment().getDerivedArtifact(Util.getWorkspaceRelativePath(target), root);
  }

  /**
   * Returns the root of either the "bin" or "genfiles"
   * tree, based on this target and the current configuration.
   * The choice of which tree to use is based on the rule with
   * which this target (which must be an OutputFile or a Rule) is associated.
   */
  public Root getBinOrGenfilesDirectory() {
    return rule.hasBinaryOutput()
        ? getConfiguration().getBinDirectory()
        : getConfiguration().getGenfilesDirectory();
  }

  private Attribute getAttribute(String attributeName) {
    // TODO(bazel-team): We should check original rule for such attribute first, because aspect
    // can't contain empty attribute. Consider changing type of prerequisiteMap from
    // ListMultimap<Attribute, ConfiguredTarget> to Map<Attribute, List<ConfiguredTarget>>. This can
    // also simplify logic in DependencyResolver#resolveExplicitAttributes.
    Attribute result = getRule().getAttributeDefinition(attributeName);
    if (result != null) {
      return result;
    }
    // Also this attribute can come from aspects, so we also have to check attributeMap.
    return attributeMap.get(attributeName);
  }

  /**
   * Returns the list of transitive info collections that feed into this target through the
   * specified attribute. Note that you need to specify the correct mode for the attribute,
   * otherwise an assertion will be raised.
   */
  public List<? extends TransitiveInfoCollection> getPrerequisites(String attributeName,
      Mode mode) {
    Attribute attributeDefinition = getAttribute(attributeName);
    if ((mode == Mode.TARGET)
        && (attributeDefinition.getConfigurationTransition() instanceof SplitTransition)) {
      // TODO(bazel-team): If you request a split-configured attribute in the target configuration,
      // we return only the list of configured targets for the first architecture; this is for
      // backwards compatibility with existing code in cases where the call to getPrerequisites is
      // deeply nested and we can't easily inject the behavior we want. However, we should fix all
      // such call sites.
      checkAttribute(attributeName, Mode.SPLIT);
      Map<String, ? extends List<? extends TransitiveInfoCollection>> map =
          getSplitPrerequisites(attributeName, /*requireSplit=*/false);
      return map.isEmpty()
          ? ImmutableList.<TransitiveInfoCollection>of()
          : map.entrySet().iterator().next().getValue();
    }

    checkAttribute(attributeName, mode);
    return targetMap.get(attributeName);
  }

  /**
   * Returns the a prerequisites keyed by the CPU of their configurations; this method throws an
   * exception if the split transition is not active.
   */
  public Map<String, ? extends List<? extends TransitiveInfoCollection>>
      getSplitPrerequisites(String attributeName) {
    return getSplitPrerequisites(attributeName, /*requireSplit*/true);
  }

  private Map<String, ? extends List<? extends TransitiveInfoCollection>>
      getSplitPrerequisites(String attributeName, boolean requireSplit) {
    checkAttribute(attributeName, Mode.SPLIT);

    Attribute attributeDefinition = getAttribute(attributeName);
    SplitTransition<?> transition =
        (SplitTransition<?>) attributeDefinition.getConfigurationTransition();
    List<BuildConfiguration> configurations =
        getConfiguration().getTransitions().getSplitConfigurations(transition);
    if (configurations.size() == 1) {
      // There are two cases here:
      // 1. Splitting is enabled, but only one target cpu.
      // 2. Splitting is disabled, and no --cpu value was provided on the command line.
      // In the first case, the cpu value is non-null, but in the second case it is null. We only
      // allow that to proceed if the caller specified that he is going to ignore the cpu value
      // anyway.
      String cpu = configurations.get(0).getCpu();
      if (cpu == null) {
        Preconditions.checkState(!requireSplit);
        cpu = "DO_NOT_USE";
      }
      return ImmutableMap.of(cpu, targetMap.get(attributeName));
    }

    Set<String> cpus = new HashSet<>();
    for (BuildConfiguration config : configurations) {
      // This method should only be called when the split config is enabled on the command line, in
      // which case this cpu can't be null.
      Preconditions.checkNotNull(config.getCpu());
      cpus.add(config.getCpu());
    }

    // Use an ImmutableListMultimap.Builder here to preserve ordering.
    ImmutableListMultimap.Builder<String, TransitiveInfoCollection> result =
        ImmutableListMultimap.builder();
    for (TransitiveInfoCollection t : targetMap.get(attributeName)) {
      if (t.getConfiguration() != null) {
        result.put(t.getConfiguration().getCpu(), t);
      } else {
        // Source files don't have a configuration, so we add them to all architecture entries.
        for (String cpu : cpus) {
          result.put(cpu, t);
        }
      }
    }
    return Multimaps.asMap(result.build());
  }

  /**
   * Returns the specified provider of the prerequisite referenced by the attribute in the
   * argument. Note that you need to specify the correct mode for the attribute, otherwise an
   * assertion will be raised. If the attribute is empty of it does not support the specified
   * provider, returns null.
   */
  public <C extends TransitiveInfoProvider> C getPrerequisite(
      String attributeName, Mode mode, Class<C> provider) {
    TransitiveInfoCollection prerequisite = getPrerequisite(attributeName, mode);
    return prerequisite == null ? null : prerequisite.getProvider(provider);
  }

  /**
   * Returns the transitive info collection that feeds into this target through the specified
   * attribute. Note that you need to specify the correct mode for the attribute, otherwise an
   * assertion will be raised. Returns null if the attribute is empty.
   */
  public TransitiveInfoCollection getPrerequisite(String attributeName, Mode mode) {
    checkAttribute(attributeName, mode);
    List<? extends TransitiveInfoCollection> elements = targetMap.get(attributeName);
    if (elements.size() > 1) {
      throw new IllegalStateException(rule.getRuleClass() + " attribute " + attributeName
          + " produces more then one prerequisites");
    }
    return elements.isEmpty() ? null : elements.get(0);
  }

  /**
   * Returns all the providers of the specified type that are listed under the specified attribute
   * of this target in the BUILD file.
   */
  public <C extends TransitiveInfoProvider> Iterable<C> getPrerequisites(String attributeName,
      Mode mode, final Class<C> classType) {
    AnalysisUtils.checkProvider(classType);
    return AnalysisUtils.getProviders(getPrerequisites(attributeName, mode), classType);
  }

  /**
   * Returns all the providers of the specified type that are listed under the specified attribute
   * of this target in the BUILD file, and that contain the specified provider.
   */
  public <C extends TransitiveInfoProvider> Iterable<? extends TransitiveInfoCollection>
      getPrerequisitesIf(String attributeName, Mode mode, final Class<C> classType) {
    AnalysisUtils.checkProvider(classType);
    return AnalysisUtils.filterByProvider(getPrerequisites(attributeName, mode), classType);
  }

  /**
   * Returns the prerequisite referred to by the specified attribute. Also checks whether
   * the attribute is marked as executable and that the target referred to can actually be
   * executed.
   *
   * <p>The {@code mode} argument must match the configuration transition specified in the
   * definition of the attribute.
   *
   * @param attributeName the name of the attribute
   * @param mode the configuration transition of the attribute
   *
   * @return the {@link FilesToRunProvider} interface of the prerequisite.
   */
  public FilesToRunProvider getExecutablePrerequisite(String attributeName, Mode mode) {
    Attribute ruleDefinition = getAttribute(attributeName);

    if (ruleDefinition == null) {
      throw new IllegalStateException(getRule().getRuleClass() + " attribute " + attributeName
          + " is not defined");
    }
    if (!ruleDefinition.isExecutable()) {
      throw new IllegalStateException(getRule().getRuleClass() + " attribute " + attributeName
          + " is not configured to be executable");
    }

    TransitiveInfoCollection prerequisite = getPrerequisite(attributeName, mode);
    if (prerequisite == null) {
      return null;
    }

    FilesToRunProvider result = prerequisite.getProvider(FilesToRunProvider.class);
    if (result == null || result.getExecutable() == null) {
      attributeError(
          attributeName, prerequisite.getLabel() + " does not refer to a valid executable target");
    }
    return result;
  }

  /**
   * Gets an attribute of type STRING_LIST expanding Make variables and
   * tokenizes the result.
   *
   * @param attributeName the name of the attribute to process
   * @return a list of strings containing the expanded and tokenized values for the
   *         attribute
   */
  public List<String> getTokenizedStringListAttr(String attributeName) {
    if (!getRule().isAttrDefined(attributeName, Type.STRING_LIST)) {
      // TODO(bazel-team): This should be an error.
      return ImmutableList.of();
    }
    List<String> original = attributes().get(attributeName, Type.STRING_LIST);
    if (original.isEmpty()) {
      return ImmutableList.of();
    }
    List<String> tokens = new ArrayList<>();
    for (String token : original) {
      tokenizeAndExpandMakeVars(tokens, attributeName, token);
    }
    return ImmutableList.copyOf(tokens);
  }

  /**
   * Expands make variables in value and tokenizes the result into tokens.
   *
   * <p>This methods should be called only during initialization.
   */
  public void tokenizeAndExpandMakeVars(List<String> tokens, String attributeName,
                                        String value) {
    try {
      ShellUtils.tokenize(tokens, expandMakeVariables(attributeName, value));
    } catch (ShellUtils.TokenizationException e) {
      attributeError(attributeName, e.getMessage());
    }
  }

  /**
   * Return a context that maps Make variable names (string) to values (string).
   *
   * @return a ConfigurationMakeVariableContext.
   **/
  public ConfigurationMakeVariableContext getConfigurationMakeVariableContext() {
    if (configurationMakeVariableContext == null) {
      configurationMakeVariableContext = new ConfigurationMakeVariableContext(
          getRule().getPackage(), getConfiguration());
    }
    return configurationMakeVariableContext;
  }

  /**
   * Returns the string "expression" after expanding all embedded references to
   * "Make" variables.  If any errors are encountered, they are reported, and
   * "expression" is returned unchanged.
   *
   * @param attributeName the name of the attribute from which "expression" comes;
   *     used for error reporting.
   * @param expression the string to expand.
   * @return the expansion of "expression".
   */
  public String expandMakeVariables(String attributeName, String expression) {
    return expandMakeVariables(attributeName, expression, getConfigurationMakeVariableContext());
  }

  /**
   * Returns the string "expression" after expanding all embedded references to
   * "Make" variables.  If any errors are encountered, they are reported, and
   * "expression" is returned unchanged.
   *
   * @param attributeName the name of the attribute from which "expression" comes;
   *     used for error reporting.
   * @param expression the string to expand.
   * @param context the ConfigurationMakeVariableContext which can have a customized
   *     lookupMakeVariable(String) method.
   * @return the expansion of "expression".
   */
  public String expandMakeVariables(String attributeName, String expression,
      ConfigurationMakeVariableContext context) {
    try {
      return MakeVariableExpander.expand(expression, context);
    } catch (MakeVariableExpander.ExpansionException e) {
      attributeError(attributeName, e.getMessage());
      return expression;
    }
  }

  /**
   * Gets the value of the STRING_LIST attribute expanding all make variables.
   */
  public List<String> expandedMakeVariablesList(String attrName) {
    List<String> variables = new ArrayList<>();
    for (String variable : attributes().get(attrName, Type.STRING_LIST)) {
      variables.add(expandMakeVariables(attrName, variable));
    }
    return variables;
  }

  /**
   * If the string consists of a single variable, returns the expansion of
   * that variable. Otherwise, returns null. Syntax errors are reported.
   *
   * @param attrName the name of the attribute from which "expression" comes;
   *     used for error reporting.
   * @param expression the string to expand.
   * @return the expansion of "expression", or null.
   */
  public String expandSingleMakeVariable(String attrName, String expression) {
    try {
      return MakeVariableExpander.expandSingleVariable(expression,
          new ConfigurationMakeVariableContext(getRule().getPackage(), getConfiguration()));
    } catch (MakeVariableExpander.ExpansionException e) {
      attributeError(attrName, e.getMessage());
      return expression;
    }
  }

  private void checkAttribute(String attributeName, Mode mode) {
    Attribute attributeDefinition = getAttribute(attributeName);
    if (attributeDefinition == null) {
      throw new IllegalStateException(getRule().getLocation() + ": " + getRule().getRuleClass()
        + " attribute " + attributeName + " is not defined");
    }
    if (!(attributeDefinition.getType() == Type.LABEL
        || attributeDefinition.getType() == Type.LABEL_LIST)) {
      throw new IllegalStateException(rule.getRuleClass() + " attribute " + attributeName
        + " is not a label type attribute");
    }
    if (mode == Mode.HOST) {
      if (attributeDefinition.getConfigurationTransition() != ConfigurationTransition.HOST) {
        throw new IllegalStateException(getRule().getLocation() + ": "
            + getRule().getRuleClass() + " attribute " + attributeName
            + " is not configured for the host configuration");
      }
    } else if (mode == Mode.TARGET) {
      if (attributeDefinition.getConfigurationTransition() != ConfigurationTransition.NONE) {
        throw new IllegalStateException(getRule().getLocation() + ": "
            + getRule().getRuleClass() + " attribute " + attributeName
            + " is not configured for the target configuration");
      }
    } else if (mode == Mode.DATA) {
      if (attributeDefinition.getConfigurationTransition() != ConfigurationTransition.DATA) {
        throw new IllegalStateException(getRule().getLocation() + ": "
            + getRule().getRuleClass() + " attribute " + attributeName
            + " is not configured for the data configuration");
      }
    } else if (mode == Mode.SPLIT) {
      if (!(attributeDefinition.getConfigurationTransition() instanceof SplitTransition)) {
        throw new IllegalStateException(getRule().getLocation() + ": "
            + getRule().getRuleClass() + " attribute " + attributeName
            + " is not configured for a split transition");
      }
    }
  }

  /**
   * Returns the Mode for which the attribute is configured.
   * This is intended for Skylark, where the Mode is implicitly chosen.
   */
  public Mode getAttributeMode(String attributeName) {
    Attribute attributeDefinition = getAttribute(attributeName);
    if (attributeDefinition == null) {
      throw new IllegalStateException(getRule().getLocation() + ": " + getRule().getRuleClass()
        + " attribute " + attributeName + " is not defined");
    }
    if (!(attributeDefinition.getType() == Type.LABEL
        || attributeDefinition.getType() == Type.LABEL_LIST)) {
      throw new IllegalStateException(rule.getRuleClass() + " attribute " + attributeName
        + " is not a label type attribute");
    }
    if (attributeDefinition.getConfigurationTransition() == ConfigurationTransition.HOST) {
      return Mode.HOST;
    } else if (attributeDefinition.getConfigurationTransition() == ConfigurationTransition.NONE) {
      return Mode.TARGET;
    } else if (attributeDefinition.getConfigurationTransition() == ConfigurationTransition.DATA) {
      return Mode.DATA;
    } else if (attributeDefinition.getConfigurationTransition() instanceof SplitTransition) {
      return Mode.SPLIT;
    }
    throw new IllegalStateException(getRule().getLocation() + ": "
        + getRule().getRuleClass() + " attribute " + attributeName + " is not configured");
  }

  /**
   * For the specified attribute "attributeName" (which must be of type
   * list(label)), resolve all the labels into ConfiguredTargets (for the
   * configuration appropriate to the attribute) and return their build
   * artifacts as a {@link PrerequisiteArtifacts} instance.
   *
   * @param attributeName the name of the attribute to traverse
   */
  public PrerequisiteArtifacts getPrerequisiteArtifacts(String attributeName, Mode mode) {
    return PrerequisiteArtifacts.get(this, attributeName, mode);
  }

  /**
   * For the specified attribute "attributeName" (which must be of type label),
   * resolves the ConfiguredTarget and returns its single build artifact.
   *
   * <p>If the attribute is optional, has no default and was not specified, then
   * null will be returned. Note also that null is returned (and an attribute
   * error is raised) if there wasn't exactly one build artifact for the target.
   */
  public Artifact getPrerequisiteArtifact(String attributeName, Mode mode) {
    TransitiveInfoCollection target = getPrerequisite(attributeName, mode);
    return transitiveInfoCollectionToArtifact(attributeName, target);
  }

  /**
   * Equivalent to getPrerequisiteArtifact(), but also asserts that
   * host-configuration is appropriate for the specified attribute.
   */
  public Artifact getHostPrerequisiteArtifact(String attributeName) {
    TransitiveInfoCollection target = getPrerequisite(attributeName, Mode.HOST);
    return transitiveInfoCollectionToArtifact(attributeName, target);
  }

  private Artifact transitiveInfoCollectionToArtifact(
      String attributeName, TransitiveInfoCollection target) {
    if (target != null) {
      Iterable<Artifact> artifacts = target.getProvider(FileProvider.class).getFilesToBuild();
      if (Iterables.size(artifacts) == 1) {
        return Iterables.getOnlyElement(artifacts);
      } else {
        attributeError(attributeName, target.getLabel() + " expected a single artifact");
      }
    }
    return null;
  }

  /**
   * Returns the sole file in the "srcs" attribute. Reports an error and
   * (possibly) returns null if "srcs" does not identify a single file of the
   * expected type.
   */
  public Artifact getSingleSource(String fileTypeName) {
    List<Artifact> srcs = PrerequisiteArtifacts.get(this, "srcs", Mode.TARGET).list();
    switch (srcs.size()) {
      case 0 : // error already issued by getSrc()
        return null;
      case 1 : // ok
        return Iterables.getOnlyElement(srcs);
      default :
        attributeError("srcs", "only a single " + fileTypeName + " is allowed here");
        return srcs.get(0);
    }
  }

  public Artifact getSingleSource() {
    return getSingleSource(getRule().getRuleClass() + " source file");
  }

  /**
   * Returns a path fragment qualified by the rule name and unique fragment to
   * disambiguate artifacts produced from the source file appearing in
   * multiple rules.
   *
   * <p>For example "pkg/dir/name" -> "pkg/&lt;fragment>/rule/dir/name.
   */
  public final PathFragment getUniqueDirectory(String fragment) {
    return AnalysisUtils.getUniqueDirectory(getLabel(), new PathFragment(fragment));
  }

  /**
   * Check that all targets that were specified as sources are from the same
   * package as this rule. Output a warning or an error for every target that is
   * imported from a different package.
   */
  public void checkSrcsSamePackage(boolean onlyWarn) {
    PathFragment packageName = getLabel().getPackageFragment();
    for (Artifact srcItem : PrerequisiteArtifacts.get(this, "srcs", Mode.TARGET).list()) {
      if (!srcItem.isSourceArtifact()) {
        // In theory, we should not do this check. However, in practice, we
        // have a couple of rules that do not obey the "srcs must contain
        // files and only files" rule. Thus, we are stuck with this hack here :(
        continue;
      }
      Label associatedLabel = srcItem.getOwner();
      PathFragment itemPackageName = associatedLabel.getPackageFragment();
      if (!itemPackageName.equals(packageName)) {
        String message = "please do not import '" + associatedLabel + "' directly. "
            + "You should either move the file to this package or depend on "
            + "an appropriate rule there";
        if (onlyWarn) {
          attributeWarning("srcs", message);
        } else {
          attributeError("srcs", message);
        }
      }
    }
  }


  /**
   * Returns the label to which the {@code NODEP_LABEL} attribute
   * {@code attrName} refers, checking that it is a valid label, and that it is
   * referring to a local target. Reports a warning otherwise.
   */
  public Label getLocalNodepLabelAttribute(String attrName) {
    Label label = attributes().get(attrName, Type.NODEP_LABEL);
    if (label == null) {
      return null;
    }

    if (!getTarget().getLabel().getPackageFragment().equals(label.getPackageFragment())) {
      attributeWarning(attrName, "does not reference a local rule");
    }

    return label;
  }

  /**
   * Returns the implicit output artifact for a given template function. If multiple or no artifacts
   * can be found as a result of the template, an exception is thrown.
   */
  public Artifact getImplicitOutputArtifact(ImplicitOutputsFunction function) {
    Iterable<String> result;
    try {
      result = function.getImplicitOutputs(RawAttributeMapper.of(rule));
    } catch (EvalException e) {
      // It's ok as long as we don't use this method from Skylark.
      throw new IllegalStateException(e);
    }
    return getImplicitOutputArtifact(Iterables.getOnlyElement(result));
  }

  /**
   * Only use from Skylark. Returns the implicit output artifact for a given output path.
   */
  public Artifact getImplicitOutputArtifact(String path) {
    Root root = getBinOrGenfilesDirectory();
    PathFragment packageFragment = getLabel().getPackageFragment();
    return getAnalysisEnvironment().getDerivedArtifact(packageFragment.getRelative(path), root);
  }

  /**
   * Convenience method to return a host configured target for the "compiler"
   * attribute. Allows caller to decide whether a warning should be printed if
   * the "compiler" attribute is not set to the default value.
   *
   * @param warnIfNotDefault if true, print a warning if the value for the
   *        "compiler" attribute is set to something other than the default
   * @return a ConfiguredTarget using the host configuration for the "compiler"
   *         attribute
   */
  public final FilesToRunProvider getCompiler(boolean warnIfNotDefault) {
    Label label = attributes().get("compiler", Type.LABEL);
    if (warnIfNotDefault && !label.equals(getRule().getAttrDefaultValue("compiler"))) {
      attributeWarning("compiler", "setting the compiler is strongly discouraged");
    }
    return getExecutablePrerequisite("compiler", Mode.HOST);
  }

  /**
   * Returns the (unmodifiable, ordered) list of artifacts which are the outputs
   * of this target.
   *
   * <p>Each element in this list is associated with a single output, either
   * declared implicitly (via setImplicitOutputsFunction()) or explicitly
   * (listed in the 'outs' attribute of our rule).
   */
  public final ImmutableList<Artifact> getOutputArtifacts() {
    ImmutableList.Builder<Artifact> artifacts = ImmutableList.builder();
    for (OutputFile out : getRule().getOutputFiles()) {
      artifacts.add(createOutputArtifact(out));
    }
    return artifacts.build();
  }

  /**
   * Like getFilesToBuild(), except that it also includes the runfiles middleman, if any.
   * Middlemen are expanded in the SpawnStrategy or by the Distributor.
   */
  public static ImmutableList<Artifact> getFilesToRun(
      RunfilesSupport runfilesSupport, NestedSet<Artifact> filesToBuild) {
    if (runfilesSupport == null) {
      return ImmutableList.copyOf(filesToBuild);
    } else {
      ImmutableList.Builder<Artifact> allFilesToBuild = ImmutableList.builder();
      allFilesToBuild.addAll(filesToBuild);
      allFilesToBuild.add(runfilesSupport.getRunfilesMiddleman());
      return allFilesToBuild.build();
    }
  }

  /**
   * Like {@link #getOutputArtifacts()} but for a singular output item.
   * Reports an error if the "out" attribute is not a singleton.
   *
   * @return null if the output list is empty, the artifact for the first item
   *         of the output list otherwise
   */
  public Artifact getOutputArtifact() {
    List<Artifact> outs = getOutputArtifacts();
    if (outs.size() != 1) {
      attributeError("out", "exactly one output file required");
      if (outs.isEmpty()) {
        return null;
      }
    }
    return outs.get(0);
  }

  /**
   * Returns an artifact with a given file extension. All other path components
   * are the same as in {@code pathFragment}.
   */
  public final Artifact getRelatedArtifact(PathFragment pathFragment, String extension) {
    PathFragment file = FileSystemUtils.replaceExtension(pathFragment, extension);
    return getAnalysisEnvironment().getDerivedArtifact(file, getConfiguration().getBinDirectory());
  }

  /**
   * Returns true if runfiles support should create the runfiles tree, or
   * false if it should just create the manifest.
   */
  public boolean shouldCreateRunfilesSymlinks() {
    // TODO(bazel-team): Ideally we wouldn't need such logic, and we'd
    // always use the BuildConfiguration#buildRunfiles() to determine
    // whether to build the runfiles. The problem is that certain build
    // steps actually consume their runfiles. These include:
    //  a. par files consumes the runfiles directory
    //     We should modify autopar to take a list of files instead.
    //     of the runfiles directory.
    //  b. host tools could potentially use data files, but currently don't
    //     (they're run from the execution root, not a runfiles tree).
    //     Currently hostConfiguration.buildRunfiles() returns true.
    if (TargetUtils.isTestRule(getTarget())) {
      // Tests are only executed during testing (duh),
      // and their runfiles are generated lazily on local
      // execution (see LocalTestStrategy). Therefore, it
      // is safe not to build their runfiles.
      return getConfiguration().buildRunfiles();
    } else {
      return true;
    }
  }

  /**
   * @return true if {@code rule} is visible from {@code prerequisite}.
   *
   * <p>This only computes the logic as implemented by the visibility system. The final decision
   * whether a dependency is allowed is made by
   * {@link ConfiguredRuleClassProvider.PrerequisiteValidator}.
   */
  public static boolean isVisible(Rule rule, TransitiveInfoCollection prerequisite) {
    // Check visibility attribute
    for (PackageSpecification specification :
      prerequisite.getProvider(VisibilityProvider.class).getVisibility()) {
      if (specification.containsPackage(rule.getLabel().getPackageFragment())) {
        return true;
      }
    }

    return false;
  }

  /**
   * @return the set of features applicable for the current rule's package.
   */
  public ImmutableSet<String> getFeatures() {
    return features;
  }

  /**
   * Builder class for a RuleContext.
   */
  public static final class Builder {
    private final AnalysisEnvironment env;
    private final Rule rule;
    private final BuildConfiguration configuration;
    private final PrerequisiteValidator prerequisiteValidator;
    private ListMultimap<Attribute, ConfiguredTarget> prerequisiteMap;
    private Set<ConfigMatchingProvider> configConditions;
    private NestedSet<PackageSpecification> visibility;

    Builder(AnalysisEnvironment env, Rule rule, BuildConfiguration configuration,
        PrerequisiteValidator prerequisiteValidator) {
      this.env = Preconditions.checkNotNull(env);
      this.rule = Preconditions.checkNotNull(rule);
      this.configuration = Preconditions.checkNotNull(configuration);
      this.prerequisiteValidator = prerequisiteValidator;
    }

    RuleContext build() {
      Preconditions.checkNotNull(prerequisiteMap);
      Preconditions.checkNotNull(configConditions);
      Preconditions.checkNotNull(visibility);
      ListMultimap<String, ConfiguredTarget> targetMap = createTargetMap();
      ListMultimap<String, ConfiguredFilesetEntry> filesetEntryMap =
          createFilesetEntryMap(rule, configConditions);
      Map<String, Attribute> attributeMap = new HashMap<>();
      for (Attribute attribute : prerequisiteMap.keySet()) {
        if (attribute == null) {
          continue;
        }
        attributeMap.put(attribute.getName(), attribute);
      }
      return new RuleContext(this, targetMap, filesetEntryMap, configConditions,
          getEnabledFeatures(), attributeMap);
    }

    private ImmutableSet<String> getEnabledFeatures() {
      Set<String> enabled = new HashSet<>();
      Set<String> disabled = new HashSet<>();
      for (String feature : Iterables.concat(getConfiguration().getDefaultFeatures(),
          getRule().getPackage().getFeatures())) {
        if (feature.startsWith("-")) {
          disabled.add(feature.substring(1));
        } else if (feature.equals("no_layering_check")) {
          // TODO(bazel-team): Remove once we do not have BUILD files left that contain
          // 'no_layering_check'.
          disabled.add(feature.substring(3));
        } else {
          enabled.add(feature);
        }
      }
      return Sets.difference(enabled, disabled).immutableCopy();
    }

    Builder setVisibility(NestedSet<PackageSpecification> visibility) {
      this.visibility = visibility;
      return this;
    }

    /**
     * Sets the prerequisites and checks their visibility. It also generates appropriate error or
     * warning messages and sets the error flag as appropriate.
     */
    Builder setPrerequisites(ListMultimap<Attribute, ConfiguredTarget> prerequisiteMap) {
      this.prerequisiteMap = Preconditions.checkNotNull(prerequisiteMap);
      return this;
    }

    /**
     * Sets the configuration conditions needed to determine which paths to follow for this
     * rule's configurable attributes.
     */
    Builder setConfigConditions(Set<ConfigMatchingProvider> configConditions) {
      this.configConditions = Preconditions.checkNotNull(configConditions);
      return this;
    }

    private boolean validateFilesetEntry(FilesetEntry filesetEntry, ConfiguredTarget src) {
      if (src.getProvider(FilesetProvider.class) != null) {
        return true;
      }
      if (filesetEntry.isSourceFileset()) {
        return true;
      }

      Target srcTarget = src.getTarget();
      if (!(srcTarget instanceof FileTarget)) {
        attributeError("entries", String.format(
            "Invalid 'srcdir' target '%s'. Must be another Fileset or package",
            srcTarget.getLabel()));
        return false;
      }

      if (srcTarget instanceof OutputFile) {
        attributeWarning("entries", String.format("'srcdir' target '%s' is not an input file. "
            + "This forces the Fileset to be executed unconditionally",
            srcTarget.getLabel()));
      }

      return true;
    }

    /**
     * Determines and returns a map from attribute name to list of configured fileset entries, based
     * on a PrerequisiteMap instance.
     */
    private ListMultimap<String, ConfiguredFilesetEntry> createFilesetEntryMap(
        final Rule rule, Set<ConfigMatchingProvider> configConditions) {
      final ImmutableSortedKeyListMultimap.Builder<String, ConfiguredFilesetEntry> mapBuilder =
          ImmutableSortedKeyListMultimap.builder();
      for (Attribute attr : rule.getAttributes()) {
        if (attr.getType() != Type.FILESET_ENTRY_LIST) {
          continue;
        }
        String attributeName = attr.getName();
        Map<Label, ConfiguredTarget> ctMap = new HashMap<>();
        for (ConfiguredTarget prerequisite : prerequisiteMap.get(attr)) {
          ctMap.put(prerequisite.getLabel(), prerequisite);
        }
        List<FilesetEntry> entries = ConfiguredAttributeMapper.of(rule, configConditions)
            .get(attributeName, Type.FILESET_ENTRY_LIST);
        for (FilesetEntry entry : entries) {
          if (entry.getFiles() == null) {
            Label label = entry.getSrcLabel();
            ConfiguredTarget src = ctMap.get(label);
            if (!validateFilesetEntry(entry, src)) {
              continue;
            }

            mapBuilder.put(attributeName, new ConfiguredFilesetEntry(entry, src));
          } else {
            ImmutableList.Builder<TransitiveInfoCollection> files = ImmutableList.builder();
            for (Label file : entry.getFiles()) {
              files.add(ctMap.get(file));
            }
            mapBuilder.put(attributeName, new ConfiguredFilesetEntry(entry, files.build()));
          }
        }
      }
      return mapBuilder.build();
    }

    /**
     * Determines and returns a map from attribute name to list of configured targets.
     */
    private ImmutableSortedKeyListMultimap<String, ConfiguredTarget> createTargetMap() {
      ImmutableSortedKeyListMultimap.Builder<String, ConfiguredTarget> mapBuilder =
          ImmutableSortedKeyListMultimap.builder();

      for (Map.Entry<Attribute, Collection<ConfiguredTarget>> entry :
          prerequisiteMap.asMap().entrySet()) {
        Attribute attribute = entry.getKey();
        if (attribute == null) {
          continue;
        }
        if (attribute.isSilentRuleClassFilter()) {
          Predicate<RuleClass> filter = attribute.getAllowedRuleClassesPredicate();
          for (ConfiguredTarget configuredTarget : entry.getValue()) {
            Target prerequisiteTarget = configuredTarget.getTarget();
            if ((prerequisiteTarget instanceof Rule)
                && filter.apply(((Rule) prerequisiteTarget).getRuleClassObject())) {
              validateDirectPrerequisite(attribute, configuredTarget);
              mapBuilder.put(attribute.getName(), configuredTarget);
            }
          }
        } else {
          for (ConfiguredTarget configuredTarget : entry.getValue()) {
            validateDirectPrerequisite(attribute, configuredTarget);
            mapBuilder.put(attribute.getName(), configuredTarget);
          }
        }
      }

      // Handle abi_deps+deps error.
      Attribute abiDepsAttr = rule.getAttributeDefinition("abi_deps");
      if ((abiDepsAttr != null) && rule.isAttributeValueExplicitlySpecified("abi_deps")
          && rule.isAttributeValueExplicitlySpecified("deps")) {
        attributeError("deps", "Only one of deps and abi_deps should be provided");
      }
      return mapBuilder.build();
    }

    private String prefixRuleMessage(String message) {
      return String.format("in %s rule %s: %s", rule.getRuleClass(), rule.getLabel(), message);
    }

    private String maskInternalAttributeNames(String name) {
      return Attribute.isImplicit(name) ? "(an implicit dependency)" : name;
    }

    private String prefixAttributeMessage(String attrName, String message) {
      return String.format("in %s attribute of %s rule %s: %s",
          maskInternalAttributeNames(attrName), rule.getRuleClass(), rule.getLabel(), message);
    }

    public void reportError(Location location, String message) {
      env.getEventHandler().handle(Event.error(location, message));
    }

    public void ruleError(String message) {
      reportError(rule.getLocation(), prefixRuleMessage(message));
    }

    public void attributeError(String attrName, String message) {
      reportError(rule.getAttributeLocation(attrName), prefixAttributeMessage(attrName, message));
    }

    public void reportWarning(Location location, String message) {
      env.getEventHandler().handle(Event.warn(location, message));
    }

    public void ruleWarning(String message) {
      env.getEventHandler().handle(Event.warn(rule.getLocation(), prefixRuleMessage(message)));
    }

    public void attributeWarning(String attrName, String message) {
      reportWarning(rule.getAttributeLocation(attrName), prefixAttributeMessage(attrName, message));
    }

    private void reportBadPrerequisite(Attribute attribute, String targetKind,
        Label prerequisiteLabel, String reason, boolean isWarning) {
      String msgPrefix = targetKind != null ? targetKind + " " : "";
      String msgReason = reason != null ? " (" + reason + ")" : "";
      if (isWarning) {
        attributeWarning(attribute.getName(), String.format(
            "%s'%s' is unexpected here%s; continuing anyway",
            msgPrefix, prerequisiteLabel, msgReason));
      } else {
        attributeError(attribute.getName(), String.format(
            "%s'%s' is misplaced here%s", msgPrefix, prerequisiteLabel, msgReason));
      }
    }

    private void validateDirectPrerequisiteType(ConfiguredTarget prerequisite,
        Attribute attribute) {
      Target prerequisiteTarget = prerequisite.getTarget();
      Label prerequisiteLabel = prerequisiteTarget.getLabel();

      if (prerequisiteTarget instanceof Rule) {
        Rule prerequisiteRule = (Rule) prerequisiteTarget;

        String reason = attribute.getValidityPredicate().checkValid(rule, prerequisiteRule);
        if (reason != null) {
          reportBadPrerequisite(attribute, prerequisiteTarget.getTargetKind(),
              prerequisiteLabel, reason, false);
        }
      }

      if (attribute.isStrictLabelCheckingEnabled()) {
        if (prerequisiteTarget instanceof Rule) {
          RuleClass ruleClass = ((Rule) prerequisiteTarget).getRuleClassObject();
          if (!attribute.getAllowedRuleClassesPredicate().apply(ruleClass)) {
            boolean allowedWithWarning = attribute.getAllowedRuleClassesWarningPredicate()
                .apply(ruleClass);
            reportBadPrerequisite(attribute, prerequisiteTarget.getTargetKind(), prerequisiteLabel,
                "expected " + attribute.getAllowedRuleClassesPredicate(), allowedWithWarning);
          }
        } else if (prerequisiteTarget instanceof FileTarget) {
          if (!attribute.getAllowedFileTypesPredicate()
              .apply(((FileTarget) prerequisiteTarget).getFilename())) {
            if (prerequisiteTarget instanceof InputFile
                && !((InputFile) prerequisiteTarget).getPath().exists()) {
              // Misplaced labels, no corresponding target exists
              if (attribute.getAllowedFileTypesPredicate().isNone()
                  && !((InputFile) prerequisiteTarget).getFilename().contains(".")) {
                // There are no allowed files in the attribute but it's not a valid rule,
                // and the filename doesn't contain a dot --> probably a misspelled rule
                attributeError(attribute.getName(),
                    "rule '" + prerequisiteLabel + "' does not exist");
              } else {
                attributeError(attribute.getName(),
                    "target '" + prerequisiteLabel + "' does not exist");
              }
            } else {
              // The file exists but has a bad extension
              reportBadPrerequisite(attribute, "file", prerequisiteLabel,
                  "expected " + attribute.getAllowedFileTypesPredicate(), false);
            }
          }
        }
      }
    }

    public Rule getRule() {
      return rule;
    }

    public BuildConfiguration getConfiguration() {
      return configuration;
    }

    /**
     * @return true if {@code rule} is visible from {@code prerequisite}.
     *
     * <p>This only computes the logic as implemented by the visibility system. The final decision
     * whether a dependency is allowed is made by
     * {@link ConfiguredRuleClassProvider.PrerequisiteValidator}, who is supposed to call this
     * method to determine whether a dependency is allowed as per visibility rules.
     */
    public boolean isVisible(TransitiveInfoCollection prerequisite) {
      return RuleContext.isVisible(rule, prerequisite);
    }

    private void validateDirectPrerequisiteFileTypes(ConfiguredTarget prerequisite,
        Attribute attribute) {
      if (attribute.isSkipAnalysisTimeFileTypeCheck()) {
        return;
      }
      FileTypeSet allowedFileTypes = attribute.getAllowedFileTypesPredicate();
      if (allowedFileTypes == FileTypeSet.ANY_FILE && !attribute.isNonEmpty()
          && !attribute.isSingleArtifact()) {
        return;
      }

      // If we allow any file we still need to check if there are actually files generated
      // Note that this check only runs for ANY_FILE predicates if the attribute is NON_EMPTY
      // or SINGLE_ARTIFACT
      // If we performed this check when allowedFileTypes == NO_FILE this would
      // always throw an error in those cases
      if (allowedFileTypes != FileTypeSet.NO_FILE) {
        Iterable<Artifact> artifacts = prerequisite.getProvider(FileProvider.class)
            .getFilesToBuild();
        if (attribute.isSingleArtifact() && Iterables.size(artifacts) != 1) {
          attributeError(attribute.getName(),
              "'" + prerequisite.getLabel() + "' must produce a single file");
          return;
        }
        for (Artifact sourceArtifact : artifacts) {
          if (allowedFileTypes.apply(sourceArtifact.getFilename())) {
            return;
          }
        }
        attributeError(attribute.getName(), "'" + prerequisite.getLabel()
            + "' does not produce any " + rule.getRuleClass() + " " + attribute.getName()
            + " files (expected " + allowedFileTypes + ")");
      }
    }

    private void validateMandatoryProviders(ConfiguredTarget prerequisite, Attribute attribute) {
      for (String provider : attribute.getMandatoryProviders()) {
        if (prerequisite.get(provider) == null) {
          attributeError(attribute.getName(), "'" + prerequisite.getLabel()
              + "' does not have mandatory provider '" + provider + "'");
        }
      }
    }

    private void validateDirectPrerequisite(Attribute attribute, ConfiguredTarget prerequisite) {
      validateDirectPrerequisiteType(prerequisite, attribute);
      validateDirectPrerequisiteFileTypes(prerequisite, attribute);
      validateMandatoryProviders(prerequisite, attribute);
      prerequisiteValidator.validate(this, prerequisite, attribute);
    }
  }

  @Override
  public String toString() {
    return "RuleContext(" + getLabel() + ", " + getConfiguration() + ")";
  }
}
