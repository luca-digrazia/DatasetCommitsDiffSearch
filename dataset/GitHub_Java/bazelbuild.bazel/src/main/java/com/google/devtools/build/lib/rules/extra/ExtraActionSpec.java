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
package com.google.devtools.build.lib.rules.extra;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.ActionOwner;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.CommandHelper;
import com.google.devtools.build.lib.analysis.FilesToRunProvider;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.analysis.actions.CommandLine;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.util.Fingerprint;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The specification for a particular extra action type.
 */
@Immutable
public final class ExtraActionSpec implements TransitiveInfoProvider {
  private final ImmutableList<Artifact> resolvedTools;
  private final ImmutableMap<PathFragment, Artifact> manifests;
  private final ImmutableList<Artifact> resolvedData;
  private final ImmutableList<String> outputTemplates;
  private final String command;
  private final boolean requiresActionOutput;
  private final Label label;

  ExtraActionSpec(
      Iterable<Artifact> resolvedTools,
      Map<PathFragment, Artifact> manifests,
      Iterable<Artifact> resolvedData,
      Iterable<String> outputTemplates,
      String command,
      Label label,
      boolean requiresActionOutput) {
    this.resolvedTools = ImmutableList.copyOf(resolvedTools);
    this.manifests = ImmutableMap.copyOf(manifests);
    this.resolvedData = ImmutableList.copyOf(resolvedData);
    this.outputTemplates = ImmutableList.copyOf(outputTemplates);
    this.command = command;
    this.label = label;
    this.requiresActionOutput = requiresActionOutput;
  }

  public Label getLabel() {
    return label;
  }

  /**
   * Adds an extra_action to the action graph based on the action to shadow.
   */
  public Collection<Artifact> addExtraAction(RuleContext owningRule,
      Action actionToShadow) {
    Collection<Artifact> extraActionOutputs = new LinkedHashSet<>();
    NestedSetBuilder<Artifact> extraActionInputs = NestedSetBuilder.stableOrder();

    ActionOwner owner = actionToShadow.getOwner();
    Label ownerLabel = owner.getLabel();
    if (requiresActionOutput) {
      extraActionInputs.addAll(actionToShadow.getOutputs());
    }
    extraActionInputs.addAll(resolvedTools);
    extraActionInputs.addAll(resolvedData);

    boolean createDummyOutput = false;

    for (String outputTemplate : outputTemplates) {
      // We create output for the extra_action based on the 'out_template' attribute.
      // See {link #getExtraActionOutputArtifact} for supported variables.
      extraActionOutputs.add(getExtraActionOutputArtifact(owningRule, actionToShadow,
          owner, outputTemplate));
    }
    // extra_action has no output, we need to create some dummy output to keep the build up-to-date.
    if (extraActionOutputs.size() == 0) {
      createDummyOutput = true;
      extraActionOutputs.add(getExtraActionOutputArtifact(owningRule, actionToShadow,
          owner, "$(ACTION_ID).dummy"));
    }

    // We generate a file containing a protocol buffer describing the action that is being shadowed.
    // It is up to each action being shadowed to decide what contents to store here.
    Artifact extraActionInfoFile = getExtraActionOutputArtifact(owningRule, actionToShadow,
        owner, "$(ACTION_ID).xa");
    extraActionOutputs.add(extraActionInfoFile);

    // Expand extra_action specific variables from the provided command-line.
    // See {@link #createExpandedCommand} for list of supported variables.
    String command = createExpandedCommand(owningRule, actionToShadow, owner, extraActionInfoFile);

    Map<String, String> env = owningRule.getConfiguration().getDefaultShellEnvironment();

    CommandHelper commandHelper = new CommandHelper(owningRule,
        ImmutableList.<FilesToRunProvider>of(),
        ImmutableMap.<Label, Iterable<Artifact>>of());

    List<String> argv = commandHelper.buildCommandLine(command, extraActionInputs,
        ".extra_action_script.sh");

    String commandMessage = String.format("Executing extra_action %s on %s", label, ownerLabel);
    owningRule.registerAction(new ExtraAction(
        actionToShadow.getOwner(),
        ImmutableSet.copyOf(extraActionInputs.build()),
        manifests,
        extraActionInfoFile,
        extraActionOutputs,
        actionToShadow,
        createDummyOutput,
        CommandLine.of(argv, false),
        env,
        commandMessage,
        label.getName()));

    return extraActionOutputs;
  }

  /**
   * Expand extra_action specific variables:
   * $(EXTRA_ACTION_FILE): expands to a path of the file containing a protocol buffer
   * describing the action being shadowed.
   * $(output <out_template>): expands the output template to the execPath of the file.
   * e.g. $(output $(ACTION_ID).out) ->
   * <build_path>/extra_actions/bar/baz/devtools/build/test_A41234.out
   */
  private String createExpandedCommand(RuleContext owningRule,
      Action action, ActionOwner owner, Artifact extraActionInfoFile) {
    String realCommand = command.replace(
        "$(EXTRA_ACTION_FILE)", extraActionInfoFile.getExecPathString());

    for (String outputTemplate : outputTemplates) {
      String outFile = getExtraActionOutputArtifact(owningRule, action, owner, outputTemplate)
        .getExecPathString();
      realCommand = realCommand.replace("$(output " + outputTemplate + ")", outFile);
    }
    return realCommand;
  }

  /**
   * Creates an output artifact for the extra_action based on the output_template.
   * The path will be in the following form:
   * <output dir>/<target-configuration-specific-path>/extra_actions/<extra_action_label>/ +
   *   <configured_target_label>/<expanded_template>
   *
   * The template can use the following variables:
   * $(ACTION_ID): a unique id for the extra_action.
   *
   *  Sample:
   *    extra_action: foo/bar:extra
   *    template: $(ACTION_ID).analysis
   *    target: foo/bar:main
   *    expands to: output/configuration/extra_actions/\
   *      foo/bar/extra/foo/bar/4683026f7ac1dd1a873ccc8c3d764132.analysis
   */
  private Artifact getExtraActionOutputArtifact(RuleContext owningRule, Action action,
      ActionOwner owner, String template) {
    String actionId = getActionId(owner, action);

    template = template.replace("$(ACTION_ID)", actionId);
    template = template.replace("$(OWNER_LABEL_DIGEST)", getOwnerDigest(owner));

    PathFragment rootRelativePath = getRootRelativePath(template, owner);
    return owningRule.getAnalysisEnvironment().getDerivedArtifact(rootRelativePath,
        owningRule.getConfiguration().getOutputDirectory());
  }

  private PathFragment getRootRelativePath(String template, ActionOwner owner) {
    PathFragment extraActionPackageFragment = label.getPackageFragment();
    PathFragment extraActionPrefix = extraActionPackageFragment.getRelative(label.getName());

    PathFragment ownerFragment = owner.getLabel().getPackageFragment();
    return new PathFragment("extra_actions").getRelative(extraActionPrefix)
        .getRelative(ownerFragment).getRelative(template);
  }

  /**
   * Calculates a digest representing the owner label.  We use the digest instead of the
   * original value as the original value might lead to a filename that is too long.
   * By using a digest, tools can deterministically find all extra_action outputs for a given
   * target, without having to open every file in the package.
   */
  private static String getOwnerDigest(ActionOwner owner) {
    Fingerprint f = new Fingerprint();
    f.addString(owner.getLabel().toString());
    return f.hexDigestAndReset();
  }

  /**
   * Creates a unique id for the action shadowed by this extra_action.
   *
   * We need to have a unique id for the extra_action to use. We build this
   * from the owner's  label and the shadowed action id (which is only
   * guaranteed to be unique per target). Together with the subfolder
   * matching the original target's package name, we believe this is enough
   * of a uniqueness guarantee.
   */
  @VisibleForTesting
  public static String getActionId(ActionOwner owner, Action action) {
    Fingerprint f = new Fingerprint();
    f.addString(owner.getLabel().toString());
    f.addString(action.getKey());
    return f.hexDigestAndReset();
  }
}
