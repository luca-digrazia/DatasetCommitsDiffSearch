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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.BaseSpawn;
import com.google.devtools.build.lib.analysis.actions.FileWriteAction;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.syntax.SkylarkCallable;
import com.google.devtools.build.lib.syntax.SkylarkModule;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Provides shared functionality for parameterized command-line launching
 * e.g. {@link com.google.devtools.build.lib.view.genrule.GenRule}
 * Also used by {@link com.google.devtools.build.lib.rules.extra.ExtraActionFactory}.
 *
 * Two largely independent separate sets of functionality are provided:
 * 1- string interpolation for {@code $(location[s] ...)} and {@code $(MakeVariable)}
 * 2- a utility to build potentially large command lines (presumably made of multiple commands),
 *  that if presumed too large for the kernel's taste can be dumped into a shell script
 *  that will contain the same commands,
 *  at which point the shell script is added to the list of inputs.
 */
@SkylarkModule(name = "command_helper", doc = "A helper class to create shell commands.")
public final class CommandHelper {

  /**
   * Maximum total command-line length, in bytes, not counting "/bin/bash -c ".
   * If the command is very long, then we write the command to a script file,
   * to avoid overflowing any limits on command-line length.
   * For short commands, we just use /bin/bash -c command.
   */
  @VisibleForTesting
  public static int maxCommandLength = 64000;

  /**
   *  A map of remote path prefixes and corresponding runfiles manifests for tools
   *  used by this rule.
   */
  private final ImmutableMap<PathFragment, Artifact> remoteRunfileManifestMap;

  /**
   * Use labelMap for heuristically expanding labels (does not include "outs")
   * This is similar to heuristic location expansion in LocationExpander
   * and should be kept in sync.
   */
  private final ImmutableMap<Label, ImmutableCollection<Artifact>> labelMap;

  /**
   * The ruleContext this helper works on
   */
  private final RuleContext ruleContext;

  /**
   * Output executable files from the 'tools' attribute.
   */
  private final ImmutableList<Artifact> resolvedTools;

  /**
   * Creates a {@link CommandHelper}.
   *
   * @param tools resolves set of tools into set of executable binaries. Populates manifests,
   *     remoteRunfiles and label map where required.
   * @param labelMap adds files to set of known files of label. Used for resolving $(location)
   *     variables.
   */
  public CommandHelper(RuleContext ruleContext,
      Iterable<FilesToRunProvider> tools,
      ImmutableMap<Label, Iterable<Artifact>> labelMap) {
    this.ruleContext = ruleContext;

    ImmutableList.Builder<Artifact> resolvedToolsBuilder = ImmutableList.builder();
    ImmutableMap.Builder<PathFragment, Artifact> remoteRunfileManifestBuilder =
        ImmutableMap.builder();
    Map<Label, Collection<Artifact>> tempLabelMap = new HashMap<>();

    for (Map.Entry<Label, Iterable<Artifact>> entry : labelMap.entrySet()) {
      Iterables.addAll(mapGet(tempLabelMap, entry.getKey()), entry.getValue());
    }

    for (FilesToRunProvider tool : tools) { // (Note: host configuration)
      Label label = tool.getLabel();
      Collection<Artifact> files = tool.getFilesToRun();
      resolvedToolsBuilder.addAll(files);
      Artifact executableArtifact = tool.getExecutable();
      // If the label has an executable artifact add that to the multimaps.
      if (executableArtifact != null) {
        mapGet(tempLabelMap, label).add(executableArtifact);
        // Also send the runfiles when running remotely.
        Artifact runfilesManifest = tool.getRunfilesManifest();
        if (runfilesManifest != null) {
          remoteRunfileManifestBuilder.put(
              BaseSpawn.runfilesForFragment(executableArtifact.getExecPath()), runfilesManifest);
        }
      } else {
        // Map all depArtifacts to the respective label using the multimaps.
        Iterables.addAll(mapGet(tempLabelMap, label), files);
      }
    }

    this.resolvedTools = resolvedToolsBuilder.build();
    this.remoteRunfileManifestMap = remoteRunfileManifestBuilder.build();
    ImmutableMap.Builder<Label, ImmutableCollection<Artifact>> labelMapBuilder =
        ImmutableMap.builder();
    for (Entry<Label, Collection<Artifact>> entry : tempLabelMap.entrySet()) {
      labelMapBuilder.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
    }
    this.labelMap = labelMapBuilder.build();
  }

  @SkylarkCallable(name = "resolved_tools", doc = "", structField = true)
  public List<Artifact> getResolvedTools() {
    return resolvedTools;
  }

  @SkylarkCallable(name = "runfiles_manifests", doc = "", structField = true)
  public ImmutableMap<PathFragment, Artifact> getRemoteRunfileManifestMap() {
    return remoteRunfileManifestMap;
  }

  // Returns the value in the specified corresponding to 'key', creating and
  // inserting an empty container if absent.  We use Map not Multimap because
  // we need to distinguish the cases of "empty value" and "absent key".
  private static Collection<Artifact> mapGet(Map<Label, Collection<Artifact>> map, Label key) {
    Collection<Artifact> values = map.get(key);
    if (values == null) {
      // We use sets not lists, because it's conceivable that the same artifact
      // could appear twice, e.g. in "srcs" and "deps".
      values = Sets.newHashSet();
      map.put(key, values);
    }
    return values;
  }

  /**
   * Resolves the 'cmd' attribute, and expands known locations for $(location)
   * variables.
   */
  @SkylarkCallable(doc = "")
  public String resolveCommandAndExpandLabels(Boolean supportLegacyExpansion,
      Boolean allowDataInLabel) {
    String command = ruleContext.attributes().get("cmd", Type.STRING);
    command = new LocationExpander(ruleContext, allowDataInLabel).expand("cmd", command);

    if (supportLegacyExpansion) {
      command = expandLabels(command, labelMap);
    }
    return command;
  }

  /**
   * Expands labels occurring in the string "expr" in the rule 'cmd'.
   * Each label must be valid, be a declared prerequisite, and expand to a
   * unique path.
   *
   * <p>If the expansion fails, an attribute error is reported and the original
   * expression is returned.
   */
  private <T extends Iterable<Artifact>> String expandLabels(String expr, Map<Label, T> labelMap) {
    try {
      return LabelExpander.expand(expr, labelMap, ruleContext.getLabel());
    } catch (LabelExpander.NotUniqueExpansionException nuee) {
      ruleContext.attributeError("cmd", nuee.getMessage());
      return expr;
    }
  }

  private static Pair<List<String>, Artifact> buildCommandLineMaybeWithScriptFile(
      RuleContext ruleContext, String command, String scriptPostFix, PathFragment shellPath) {
    List<String> argv;
    Artifact scriptFileArtifact = null;
    if (command.length() <= maxCommandLength) {
      argv = buildCommandLineSimpleArgv(command, shellPath);
    } else {
      // Use script file.
      scriptFileArtifact = buildCommandLineArtifact(ruleContext, command, scriptPostFix);
      argv = buildCommandLineArgvWithArtifact(scriptFileArtifact, shellPath);
    }
    return Pair.of(argv, scriptFileArtifact);
  }

  private static ImmutableList<String> buildCommandLineArgvWithArtifact(Artifact scriptFileArtifact,
      PathFragment shellPath) {
    return ImmutableList.of(shellPath.getPathString(), scriptFileArtifact.getExecPathString());
  }

  private static Artifact buildCommandLineArtifact(RuleContext ruleContext, String command,
      String scriptPostFix) {
    String scriptFileName = ruleContext.getTarget().getName() + scriptPostFix;
    String scriptFileContents = "#!/bin/bash\n" + command;
    Artifact scriptFileArtifact = FileWriteAction.createFile(
        ruleContext, scriptFileName, scriptFileContents, /*executable=*/true);
    return scriptFileArtifact;
  }

  private static ImmutableList<String> buildCommandLineSimpleArgv(String command,
      PathFragment shellPath) {
    return ImmutableList.of(shellPath.getPathString(), "-c", command);
  }

  /**
   * Builds the set of command-line arguments. Creates a bash script if the
   * command line is longer than the allowed maximum {@link #maxCommandLength}.
   * Fixes up the input artifact list with the created bash script when required.
   */
  public List<String> buildCommandLine(
      String command, NestedSetBuilder<Artifact> inputs, String scriptPostFix) {
    return buildCommandLine(command, inputs, scriptPostFix,
        ruleContext.getConfiguration().getShExecutable());
  }

  /**
   * Builds the set of command-line arguments using the specified shell path. Creates a bash script
   * if the command line is longer than the allowed maximum {@link #maxCommandLength}.
   * Fixes up the input artifact list with the created bash script when required.
   *
   * @param shellPath path to the shell that should invoke this command
   */
  public List<String> buildCommandLine(
      String command, NestedSetBuilder<Artifact> inputs, String scriptPostFix,
      PathFragment shellPath) {
    Pair<List<String>, Artifact> argvAndScriptFile =
        buildCommandLineMaybeWithScriptFile(ruleContext, command, scriptPostFix, shellPath);
    if (argvAndScriptFile.second != null) {
      inputs.add(argvAndScriptFile.second);
    }
    return argvAndScriptFile.first;
  }

  /**
   * Builds the set of command-line arguments. Creates a bash script if the
   * command line is longer than the allowed maximum {@link #maxCommandLength}.
   * Fixes up the input artifact list with the created bash script when required.
   */
  @SkylarkCallable(doc = "")
  public List<String> buildCommandLine(
      String command, List<Artifact> inputs, String scriptPostFix) {
    Pair<List<String>, Artifact> argvAndScriptFile = buildCommandLineMaybeWithScriptFile(
        ruleContext, command, scriptPostFix, ruleContext.getConfiguration().getShExecutable());
    if (argvAndScriptFile.second != null) {
      inputs.add(argvAndScriptFile.second);
    }
    return argvAndScriptFile.first;
  }
}
