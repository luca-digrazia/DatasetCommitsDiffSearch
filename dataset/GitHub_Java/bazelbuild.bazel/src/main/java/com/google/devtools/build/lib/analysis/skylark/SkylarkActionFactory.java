// Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.analysis.skylark;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.ActionAnalysisMetadata;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ArtifactRoot;
import com.google.devtools.build.lib.actions.CommandLine;
import com.google.devtools.build.lib.actions.CommandLineItemSimpleFormatter;
import com.google.devtools.build.lib.actions.ParamFileInfo;
import com.google.devtools.build.lib.actions.ParameterFile.ParameterFileType;
import com.google.devtools.build.lib.actions.RunfilesSupplier;
import com.google.devtools.build.lib.actions.extra.SpawnInfo;
import com.google.devtools.build.lib.analysis.CommandHelper;
import com.google.devtools.build.lib.analysis.FilesToRunProvider;
import com.google.devtools.build.lib.analysis.PseudoAction;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.ShToolchain;
import com.google.devtools.build.lib.analysis.actions.ActionConstructionContext;
import com.google.devtools.build.lib.analysis.actions.FileWriteAction;
import com.google.devtools.build.lib.analysis.actions.ParameterFileWriteAction;
import com.google.devtools.build.lib.analysis.actions.SpawnAction;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction;
import com.google.devtools.build.lib.analysis.actions.TemplateExpansionAction.Substitution;
import com.google.devtools.build.lib.analysis.skylark.SkylarkCustomCommandLine.ScalarArg;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.TargetUtils;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.ParamType;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.FunctionSignature.Shape;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.SkylarkMutable;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;
import com.google.devtools.build.lib.syntax.SkylarkSemantics;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/** Provides a Skylark interface for all action creation needs. */
@SkylarkModule(
  name = "actions",
  category = SkylarkModuleCategory.BUILTIN,
  doc =
      "Module providing functions to create actions. "
          + "Access this module using <a href=\"ctx.html#actions\"><code>ctx.actions</code></a>."
)
public class SkylarkActionFactory implements SkylarkValue {
  private final SkylarkRuleContext context;
  private final SkylarkSemantics skylarkSemantics;
  private RuleContext ruleContext;
  /** Counter for actions.run_shell helper scripts. Every script must have a unique name. */
  private int runShellOutputCounter = 0;

  public SkylarkActionFactory(
      SkylarkRuleContext context,
      SkylarkSemantics skylarkSemantics,
      RuleContext ruleContext) {
    this.context = context;
    this.skylarkSemantics = skylarkSemantics;
    this.ruleContext = ruleContext;
  }

  ArtifactRoot newFileRoot() throws EvalException {
    return context.isForAspect()
        ? ruleContext.getConfiguration().getBinDirectory(ruleContext.getRule().getRepository())
        : ruleContext.getBinOrGenfilesDirectory();
  }

  @SkylarkCallable(
    name = "declare_file",
    doc =
        "Declares that the rule or aspect creates a file with the given filename. "
            + "If <code>sibling</code> is not specified, the file name is relative to the package"
            + "directory, otherwise the file is in the same directory as <code>sibling</code>."
            + "Files cannot be created outside of the current package."
            + "<p>Remember that in addition to declaring a file, you must separately create an "
            + "action that emits the file. Creating that action will require passing the returned "
            + "<code>File</code> object to the action's construction function."
            + "<p>Note that <a href='../rules.$DOC_EXT#files'>predeclared output files</a> do not "
            + "need to be (and cannot be) declared using this function. You can obtain their "
            + "<code>File</code> objects from <a href=\"ctx.html#outputs\"><code>ctx.outputs</code>"
            + "</a> instead. "
            + "<a href=\"https://github.com/bazelbuild/examples/tree/master/rules/"
            + "computed_dependencies/hash.bzl\">See example of use</a>",
    parameters = {
      @Param(
        name = "filename",
        type = String.class,
        doc =
            "If no 'sibling' provided, path of the new file, relative "
                + "to the current package. Otherwise a base name for a file "
                + "('sibling' determines a directory)."
      ),
      @Param(
        name = "sibling",
        doc =
            "A file that lives in the same directory as the newly created file. "
                + "The file must be in the current package.",
        type = Artifact.class,
        noneable = true,
        positional = false,
        named = true,
        defaultValue = "None"
      )
    }
  )
  public Artifact declareFile(String filename, Object sibling) throws EvalException {
    context.checkMutable("actions.declare_file");
    if (Runtime.NONE.equals(sibling)) {
      return ruleContext.getPackageRelativeArtifact(filename, newFileRoot());
    } else {
      PathFragment original = ((Artifact) sibling).getRootRelativePath();
      PathFragment fragment = original.replaceName(filename);
      return ruleContext.getDerivedArtifact(fragment, newFileRoot());
    }
  }

  @SkylarkCallable(
      name = "declare_directory",
      doc =
          "Declares that rule or aspect create a directory with the given name, in the "
              + "current package. You must create an action that generates the directory.",
      parameters = {
          @Param(
              name = "filename",
              type = String.class,
              doc =
                  "If no 'sibling' provided, path of the new directory, relative "
                      + "to the current package. Otherwise a base name for a file "
                      + "('sibling' defines a directory)."
          ),
          @Param(
              name = "sibling",
              doc = "A file that lives in the same directory as the newly declared directory.",
              type = Artifact.class,
              noneable = true,
              positional = false,
              named = true,
              defaultValue = "None"
          )
      }
  )
  public Artifact declareDirectory(String filename, Object sibling) throws EvalException {
    context.checkMutable("actions.declare_directory");
    if (Runtime.NONE.equals(sibling)) {
      return ruleContext.getPackageRelativeTreeArtifact(
          PathFragment.create(filename), newFileRoot());
    } else {
      PathFragment original = ((Artifact) sibling).getRootRelativePath();
      PathFragment fragment = original.replaceName(filename);
      return ruleContext.getTreeArtifact(fragment, newFileRoot());
    }
  }


  @SkylarkCallable(
      name = "do_nothing",
      doc =
          "Creates an empty action that neither executes a command nor produces any "
              + "output, but that is useful for inserting 'extra actions'.",
      parameters = {
          @Param(
              name = "mnemonic",
              type = String.class,
              named = true,
              positional = false,
              doc = "A one-word description of the action, for example, CppCompile or GoLink."
          ),
          @Param(
              name = "inputs",
              allowedTypes = {
                  @ParamType(type = SkylarkList.class),
                  @ParamType(type = SkylarkNestedSet.class),
              },
              generic1 = Artifact.class,
              named = true,
              positional = false,
              defaultValue = "[]",
              doc = "List of the input files of the action."
          ),
      }
  )
  public void doNothing(String mnemonic, Object inputs) throws EvalException {
    context.checkMutable("actions.do_nothing");
    NestedSet<Artifact> inputSet = inputs instanceof SkylarkNestedSet
        ? ((SkylarkNestedSet) inputs).getSet(Artifact.class)
        : NestedSetBuilder.<Artifact>compileOrder()
            .addAll(((SkylarkList) inputs).getContents(Artifact.class, "inputs"))
            .build();
    Action action =
        new PseudoAction<>(
            UUID.nameUUIDFromBytes(
                String.format("empty action %s", ruleContext.getLabel())
                    .getBytes(StandardCharsets.UTF_8)),
            ruleContext.getActionOwner(),
            inputSet,
            ImmutableList.of(PseudoAction.getDummyOutput(ruleContext)),
            mnemonic,
            SpawnInfo.spawnInfo,
            SpawnInfo.newBuilder().build());
    ruleContext.registerAction(action);
  }

  @SkylarkCallable(
    name = "write",
    doc =
        "Creates a file write action. When the action is executed, it will write the given content "
            + "to a file. This is used to generate files using information available in the "
            + "analysis phase. If the file is large and with a lot of static content, consider "
            + "using <a href=\"#expand_template\"><code>expand_template</code></a>.",
    parameters = {
      @Param(name = "output", type = Artifact.class, doc = "The output file.", named = true),
      @Param(
        name = "content",
        type = Object.class,
        allowedTypes = {@ParamType(type = String.class), @ParamType(type = Args.class)},
        doc =
            "the contents of the file. "
                + "May be a either a string or an "
                + "<a href=\"actions.html#args\"><code>actions.args()</code></a> object.",
        named = true
      ),
      @Param(
        name = "is_executable",
        type = Boolean.class,
        defaultValue = "False",
        doc = "Whether the output file should be executable.",
        named = true
      )
    }
  )
  public void write(Artifact output, Object content, Boolean isExecutable) throws EvalException {
    context.checkMutable("actions.write");
    final Action action;
    if (content instanceof String) {
      action = FileWriteAction.create(ruleContext, output, (String) content, isExecutable);
    } else if (content instanceof Args) {
      Args args = (Args) content;
      action =
          new ParameterFileWriteAction(
              ruleContext.getActionOwner(),
              output,
              args.build(),
              args.parameterFileType,
              StandardCharsets.UTF_8);
    } else {
      throw new AssertionError("Unexpected type: " + content.getClass().getSimpleName());
    }
    ruleContext.registerAction(action);
  }

  @SkylarkCallable(
    name = "run",
    doc =
        "Creates an action that runs an executable. "
            + "<a href=\"https://github.com/bazelbuild/examples/tree/master/rules/"
            + "actions_run/execute.bzl\">See example of use</a>",
    parameters = {
      @Param(
        name = "outputs",
        type = SkylarkList.class,
        generic1 = Artifact.class,
        named = true,
        positional = false,
        doc = "List of the output files of the action."
      ),
      @Param(
        name = "inputs",
        allowedTypes = {
          @ParamType(type = SkylarkList.class),
          @ParamType(type = SkylarkNestedSet.class),
        },
        generic1 = Artifact.class,
        defaultValue = "[]",
        named = true,
        positional = false,
        doc = "List or depset of the input files of the action."
      ),
      @Param(
        name = "executable",
        type = Object.class,
        allowedTypes = {
          @ParamType(type = Artifact.class),
          @ParamType(type = String.class),
        },
        named = true,
        positional = false,
        doc = "The executable file to be called by the action."
      ),
      @Param(
        name = "tools",
        allowedTypes = {
          @ParamType(type = SkylarkList.class),
          @ParamType(type = SkylarkNestedSet.class),
        },
        generic1 = Artifact.class,
        defaultValue = "unbound",
        named = true,
        positional = false,
        doc =
            "List or depset of any tools needed by the action. Tools are inputs with additional "
                + "runfiles that are automatically made available to the action."
      ),
      @Param(
        name = "arguments",
        type = Object.class,
        allowedTypes = {
          @ParamType(type = SkylarkList.class),
        },
        defaultValue = "[]",
        named = true,
        positional = false,
        doc =
            "Command line arguments of the action. "
                + "Must be a list of strings or "
                + "<a href=\"actions.html#args\"><code>actions.args()</code></a> objects."
      ),
      @Param(
        name = "mnemonic",
        type = String.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc = "A one-word description of the action, for example, CppCompile or GoLink."
      ),
      @Param(
        name = "progress_message",
        type = String.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc =
            "Progress message to show to the user during the build, "
                + "for example, \"Compiling foo.cc to create foo.o\"."
      ),
      @Param(
        name = "use_default_shell_env",
        type = Boolean.class,
        defaultValue = "False",
        named = true,
        positional = false,
        doc = "Whether the action should use the built in shell environment or not."
      ),
      @Param(
        name = "env",
        type = SkylarkDict.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc = "Sets the dictionary of environment variables."
      ),
      @Param(
        name = "execution_requirements",
        type = SkylarkDict.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc =
            "Information for scheduling the action. See "
                + "<a href=\"$BE_ROOT/common-definitions.html#common.tags\">tags</a> "
                + "for useful keys."
      ),
      @Param(
        // TODO(bazel-team): The name here isn't accurate anymore.
        // This is technically experimental, so folks shouldn't be too attached,
        // but consider renaming to be more accurate/opaque.
        name = "input_manifests",
        type = SkylarkList.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc =
            "(Experimental) sets the input runfiles metadata; "
                + "they are typically generated by resolve_command."
      )
    },
    useLocation = true
  )
  public void run(
      SkylarkList outputs,
      Object inputs,
      Object executableUnchecked,
      Object toolsUnchecked,
      Object arguments,
      Object mnemonicUnchecked,
      Object progressMessage,
      Boolean useDefaultShellEnv,
      Object envUnchecked,
      Object executionRequirementsUnchecked,
      Object inputManifestsUnchecked,
      Location location)
      throws EvalException {
    context.checkMutable("actions.run");
    SpawnAction.Builder builder = new SpawnAction.Builder();

    SkylarkList argumentsList = ((SkylarkList) arguments);
    buildCommandLine(builder, argumentsList);
    if (executableUnchecked instanceof Artifact) {
      Artifact executable = (Artifact) executableUnchecked;
      builder.addInput(executable);
      FilesToRunProvider provider = context.getExecutableRunfiles(executable);
      if (provider == null) {
        builder.setExecutable(executable);
      } else {
        builder.setExecutable(provider);
      }
    } else if (executableUnchecked instanceof String) {
      builder.setExecutable(PathFragment.create((String) executableUnchecked));
    } else {
      throw new EvalException(
          null,
          "expected file or string for "
              + "executable but got "
              + EvalUtils.getDataTypeName(executableUnchecked)
              + " instead");
    }
    registerSpawnAction(
        outputs,
        inputs,
        toolsUnchecked,
        mnemonicUnchecked,
        progressMessage,
        useDefaultShellEnv,
        envUnchecked,
        executionRequirementsUnchecked,
        inputManifestsUnchecked,
        location,
        builder);
  }

  /**
   * Registers actions in the context of this {@link SkylarkActionFactory}.
   *
   * Use {@link #getActionConstructionContext()} to obtain the context required to
   * create those actions.
   */
  public void registerAction(ActionAnalysisMetadata... actions) {
    ruleContext.registerAction(actions);
  }

  /**
   * Returns information needed to construct actions that can be
   * registered with {@link #registerAction(ActionAnalysisMetadata...)}.
   */
  public ActionConstructionContext getActionConstructionContext() {
    return ruleContext;
  }

  @SkylarkCallable(
    name = "run_shell",
    doc =
        "Creates an action that runs a shell command. "
            + "<a href=\"https://github.com/bazelbuild/examples/tree/master/rules/"
            + "shell_command/size.bzl\">See example of use</a>",
    parameters = {
      @Param(
        name = "outputs",
        type = SkylarkList.class,
        generic1 = Artifact.class,
        named = true,
        positional = false,
        doc = "List of the output files of the action."
      ),
      @Param(
        name = "inputs",
        allowedTypes = {
          @ParamType(type = SkylarkList.class),
          @ParamType(type = SkylarkNestedSet.class),
        },
        generic1 = Artifact.class,
        defaultValue = "[]",
        named = true,
        positional = false,
        doc = "List or depset of the input files of the action."
      ),
      @Param(
        name = "tools",
        allowedTypes = {
          @ParamType(type = SkylarkList.class),
          @ParamType(type = SkylarkNestedSet.class),
        },
        generic1 = Artifact.class,
        defaultValue = "unbound",
        named = true,
        positional = false,
        doc =
            "List or depset of any tools needed by the action. Tools are inputs with additional "
                + "runfiles that are automatically made available to the action."
      ),
      @Param(
        name = "arguments",
        allowedTypes = {
          @ParamType(type = SkylarkList.class),
        },
        defaultValue = "[]",
        named = true,
        positional = false,
        doc =
            "Command line arguments of the action. "
                + "Must be a list of strings or "
                + "<a href=\"actions.html#args\"><code>actions.args()</code></a> objects.<br>"
                + "Blaze passes the elements in this attribute as arguments to the command."
                + "The command can access these arguments as <code>$1</code>, <code>$2</code>, etc."
      ),
      @Param(
        name = "mnemonic",
        type = String.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc = "A one-word description of the action, for example, CppCompile or GoLink."
      ),
      @Param(
        name = "command",
        type = Object.class,
        allowedTypes = {
          @ParamType(type = String.class),
          @ParamType(type = SkylarkList.class, generic1 = String.class),
          @ParamType(type = Runtime.NoneType.class),
        },
        named = true,
        positional = false,
        doc =
            "Shell command to execute.<br><br>"
                + "<b>Passing a sequence of strings to this attribute is deprecated and Blaze may "
                + "stop accepting such values in the future.</b><br><br>"
                + "The command can access the elements of the <code>arguments</code> object via "
                + "<code>$1</code>, <code>$2</code>, etc.<br>"
                + "When this argument is a string, it must be a valid shell command. For example: "
                + "\"<code>echo foo > $1</code>\". Blaze uses the same shell to execute the "
                + "command as it does for genrules."
      ),
      @Param(
        name = "progress_message",
        type = String.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc =
            "Progress message to show to the user during the build, "
                + "for example, \"Compiling foo.cc to create foo.o\"."
      ),
      @Param(
        name = "use_default_shell_env",
        type = Boolean.class,
        defaultValue = "False",
        named = true,
        positional = false,
        doc = "Whether the action should use the built in shell environment or not."
      ),
      @Param(
        name = "env",
        type = SkylarkDict.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc = "Sets the dictionary of environment variables."
      ),
      @Param(
        name = "execution_requirements",
        type = SkylarkDict.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc =
            "Information for scheduling the action. See "
                + "<a href=\"$BE_ROOT/common-definitions.html#common.tags\">tags</a> "
                + "for useful keys."
      ),
      @Param(
        // TODO(bazel-team): The name here isn't accurate anymore.
        // This is technically experimental, so folks shouldn't be too attached,
        // but consider renaming to be more accurate/opaque.
        name = "input_manifests",
        type = SkylarkList.class,
        noneable = true,
        defaultValue = "None",
        named = true,
        positional = false,
        doc =
            "(Experimental) sets the input runfiles metadata; "
                + "they are typically generated by resolve_command."
      )
    },
    useLocation = true
  )
  public void runShell(
      SkylarkList outputs,
      Object inputs,
      Object toolsUnchecked,
      Object arguments,
      Object mnemonicUnchecked,
      Object commandUnchecked,
      Object progressMessage,
      Boolean useDefaultShellEnv,
      Object envUnchecked,
      Object executionRequirementsUnchecked,
      Object inputManifestsUnchecked,
      Location location)
      throws EvalException {
    context.checkMutable("actions.run_shell");

    SkylarkList argumentList = (SkylarkList) arguments;
    SpawnAction.Builder builder = new SpawnAction.Builder();
    buildCommandLine(builder, argumentList);

    if (commandUnchecked instanceof String) {
      Map<String, String> executionInfo =
          ImmutableMap.copyOf(TargetUtils.getExecutionInfo(ruleContext.getRule()));
      String helperScriptSuffix = String.format(".run_shell_%d.sh", runShellOutputCounter++);
      String command = (String) commandUnchecked;
      Artifact helperScript =
          CommandHelper.shellCommandHelperScriptMaybe(
              ruleContext, command, helperScriptSuffix, executionInfo);
      PathFragment shExecutable = ShToolchain.getPathOrError(ruleContext);
      if (helperScript == null) {
        builder.setShellCommand(shExecutable, command);
      } else {
        builder.setShellCommand(shExecutable, helperScript.getExecPathString());
        builder.addInput(helperScript);
        FilesToRunProvider provider = context.getExecutableRunfiles(helperScript);
        if (provider != null) {
          builder.addTool(provider);
        }
      }
    } else if (commandUnchecked instanceof SkylarkList) {
      SkylarkList commandList = (SkylarkList) commandUnchecked;
      if (commandList.size() < 3) {
        throw new EvalException(null, "'command' list has to be of size at least 3");
      }
      @SuppressWarnings("unchecked")
      List<String> command = commandList.getContents(String.class, "command");
      builder.setShellCommand(command);
    } else {
      throw new EvalException(
          null,
          "expected string or list of strings for command instead of "
              + EvalUtils.getDataTypeName(commandUnchecked));
    }
    if (argumentList.size() > 0) {
      // When we use a shell command, add an empty argument before other arguments.
      //   e.g.  bash -c "cmd" '' 'arg1' 'arg2'
      // bash will use the empty argument as the value of $0 (which we don't care about).
      // arg1 and arg2 will be $1 and $2, as a user expects.
      builder.addExecutableArguments("");
    }
    registerSpawnAction(
        outputs,
        inputs,
        toolsUnchecked,
        mnemonicUnchecked,
        progressMessage,
        useDefaultShellEnv,
        envUnchecked,
        executionRequirementsUnchecked,
        inputManifestsUnchecked,
        location,
        builder);
  }

  private void buildCommandLine(SpawnAction.Builder builder, SkylarkList argumentsList)
      throws EvalException {
    List<String> stringArgs = new ArrayList<>();
    for (Object value : argumentsList) {
      if (value instanceof String) {
        stringArgs.add((String) value);
      } else if (value instanceof Args) {
        if (!stringArgs.isEmpty()) {
          builder.addCommandLine(CommandLine.of(stringArgs));
          stringArgs = new ArrayList<>();
        }
        Args args = (Args) value;
        ParamFileInfo paramFileInfo = null;
        if (args.flagFormatString != null) {
          paramFileInfo =
              ParamFileInfo.builder(args.parameterFileType)
                  .setFlagFormatString(args.flagFormatString)
                  .setUseAlways(args.useAlways)
                  .setCharset(StandardCharsets.UTF_8)
                  .build();
        }
        builder.addCommandLine(args.commandLine.build(), paramFileInfo);
      } else {
        throw new EvalException(
            null,
            "expected list of strings or ctx.actions.args() for arguments instead of "
                + EvalUtils.getDataTypeName(value));
      }
    }
    if (!stringArgs.isEmpty()) {
      builder.addCommandLine(CommandLine.of(stringArgs));
    }
  }

  /**
   * Setup for spawn actions common between {@link #run} and {@link #runShell}.
   *
   * <p>{@code builder} should have either executable or a command set.
   */
  private void registerSpawnAction(
      SkylarkList outputs,
      Object inputs,
      Object toolsUnchecked,
      Object mnemonicUnchecked,
      Object progressMessage,
      Boolean useDefaultShellEnv,
      Object envUnchecked,
      Object executionRequirementsUnchecked,
      Object inputManifestsUnchecked,
      Location location,
      SpawnAction.Builder builder)
      throws EvalException {
    Iterable<Artifact> inputArtifacts;
    if (inputs instanceof SkylarkList) {
      inputArtifacts = ((SkylarkList) inputs).getContents(Artifact.class, "inputs");
      builder.addInputs(inputArtifacts);
    } else {
      NestedSet<Artifact> inputSet = ((SkylarkNestedSet) inputs).getSet(Artifact.class);
      builder.addTransitiveInputs(inputSet);
      inputArtifacts = inputSet;
    }
    builder.addOutputs(outputs.getContents(Artifact.class, "outputs"));

    if (toolsUnchecked != Runtime.UNBOUND) {
      final Iterable<Artifact> toolsIterable;
      if (toolsUnchecked instanceof SkylarkList) {
        toolsIterable = ((SkylarkList) toolsUnchecked).getContents(Artifact.class, "tools");
      } else {
        toolsIterable = ((SkylarkNestedSet) toolsUnchecked).getSet(Artifact.class);
      }
      for (Artifact artifact : toolsIterable) {
        builder.addInput(artifact);
        FilesToRunProvider provider = context.getExecutableRunfiles(artifact);
        if (provider != null) {
          builder.addTool(provider);
        }
      }
    } else {
      // Users didn't pass 'tools', kick in compatibility modes
      // Full legacy support -- add tools from inputs
      for (Artifact artifact : inputArtifacts) {
        FilesToRunProvider provider = context.getExecutableRunfiles(artifact);
        if (provider != null) {
          builder.addTool(provider);
        }
      }
    }

    String mnemonic = getMnemonic(mnemonicUnchecked);
    builder.setMnemonic(mnemonic);
    if (envUnchecked != Runtime.NONE) {
      builder.setEnvironment(
          ImmutableMap.copyOf(
              SkylarkDict.castSkylarkDictOrNoneToDict(
                  envUnchecked, String.class, String.class, "env")));
    }
    if (progressMessage != Runtime.NONE) {
      builder.setProgressMessageNonLazy((String) progressMessage);
    }
    if (EvalUtils.toBoolean(useDefaultShellEnv)) {
      builder.useDefaultShellEnvironment();
    }
    if (executionRequirementsUnchecked != Runtime.NONE) {
      builder.setExecutionInfo(
          TargetUtils.filter(
              SkylarkDict.castSkylarkDictOrNoneToDict(
                  executionRequirementsUnchecked,
                  String.class,
                  String.class,
                  "execution_requirements")));
    }
    if (inputManifestsUnchecked != Runtime.NONE) {
      for (RunfilesSupplier supplier : SkylarkList.castSkylarkListOrNoneToList(
          inputManifestsUnchecked, RunfilesSupplier.class, "runfiles suppliers")) {
        builder.addRunfilesSupplier(supplier);
      }
    }
    // Always register the action
    ruleContext.registerAction(builder.build(ruleContext));
  }

  private String getMnemonic(Object mnemonicUnchecked) {
    String mnemonic =
        mnemonicUnchecked == Runtime.NONE ? "SkylarkAction" : (String) mnemonicUnchecked;
    if (ruleContext.getConfiguration().getReservedActionMnemonics().contains(mnemonic)) {
      mnemonic = mangleMnemonic(mnemonic);
    }
    return mnemonic;
  }

  private static String mangleMnemonic(String mnemonic) {
    return mnemonic + "FromSkylark";
  }

  @SkylarkCallable(
    name = "expand_template",
    doc =
        "Creates a template expansion action. When the action is executed, it will "
            + "generate a file based on a template. Parts of the template will be replaced "
            + "using the <code>substitutions</code> dictionary. Whenever a key of the "
            + "dictionary appears in the template, it is replaced with the associated value. "
            + "There is no special syntax for the keys. You may, for example, use curly braces "
            + "to avoid conflicts (for example, <code>{KEY}</code>). "
            + "<a href=\"https://github.com/bazelbuild/examples/blob/master/rules/expand_template/hello.bzl\">"
            + "See example of use</a>",
    parameters = {
      @Param(
        name = "template",
        type = Artifact.class,
        named = true,
        positional = false,
        doc = "The template file, which is a UTF-8 encoded text file."
      ),
      @Param(
        name = "output",
        type = Artifact.class,
        named = true,
        positional = false,
        doc = "The output file, which is a UTF-8 encoded text file."
      ),
      @Param(
        name = "substitutions",
        type = SkylarkDict.class,
        named = true,
        positional = false,
        doc = "Substitutions to make when expanding the template."
      ),
      @Param(
        name = "is_executable",
        type = Boolean.class,
        defaultValue = "False",
        named = true,
        positional = false,
        doc = "Whether the output file should be executable."
      )
    }
  )
  public void expandTemplate(
      Artifact template,
      Artifact output,
      SkylarkDict<?, ?> substitutionsUnchecked,
      Boolean executable)
      throws EvalException {
    context.checkMutable("actions.expand_template");
    ImmutableList.Builder<Substitution> substitutionsBuilder = ImmutableList.builder();
    for (Map.Entry<String, String> substitution :
        substitutionsUnchecked
            .getContents(String.class, String.class, "substitutions")
            .entrySet()) {
      // ParserInputSource.create(Path) uses Latin1 when reading BUILD files, which might
      // contain UTF-8 encoded symbols as part of template substitution.
      // As a quick fix, the substitution values are corrected before being passed on.
      // In the long term, fixing ParserInputSource.create(Path) would be a better approach.
      substitutionsBuilder.add(
          Substitution.of(
              substitution.getKey(), convertLatin1ToUtf8(substitution.getValue())));
    }
    TemplateExpansionAction action =
        new TemplateExpansionAction(
            ruleContext.getActionOwner(),
            template,
            output,
            substitutionsBuilder.build(),
            executable);
    ruleContext.registerAction(action);
  }

  /**
   * Returns the proper UTF-8 representation of a String that was erroneously read using Latin1.
   *
   * @param latin1 Input string
   * @return The input string, UTF8 encoded
   */
  private static String convertLatin1ToUtf8(String latin1) {
    return new String(latin1.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
  }

  /** Args module. */
  @SkylarkModule(
    name = "Args",
    category = SkylarkModuleCategory.BUILTIN,
    doc =
        "An object that encapsulates, in a memory-efficient way, the data needed to build part or "
            + "all of a command line."
            + ""
            + "<p>It often happens that an action requires a large command line containing values "
            + "accumulated from transitive dependencies. For example, a linker command line might "
            + "list every object file needed by all of the libraries being linked. It is best "
            + "practice to store such transitive data in <a href='depset.html'><code>depset"
            + "</code></a>s, so that they can be shared by multiple targets. However, if the rule "
            + "author had to convert these depsets into lists of strings in order to construct an "
            + "action command line, it would defeat this memory-sharing optimization."
            + ""
            + "<p>For this reason, the action-constructing functions accept <code>Args</code> "
            + "objects in addition to strings. Each <code>Args</code> object represents a "
            + "concatenation of strings and depsets, with optional transformations for "
            + "manipulating the data. <code>Args</code> objects do not process the depsets they "
            + "encapsulate until the execution phase, when it comes time to calculate the command "
            + "line. This helps defer any expensive copying until after the analysis phase is "
            + "complete. See the <a href='../performance.$DOC_EXT'>Optimizing Performance</a> page "
            + "for more information."
            + ""
            + "<p><code>Args</code> are constructed by calling <a href='actions.html#args'><code>"
            + "ctx.actions.args()</code></a>. They can be passed as the <code>arguments</code> "
            + "parameter of <a href='actions.html#run'><code>ctx.actions.run()</code></a> or "
            + "<a href='actions.html#run_shell'><code>ctx.actions.run_shell()</code></a>. Each "
            + "mutation of an <code>Args</code> object appends values to the eventual command "
            + "line."
            + ""
            + "<p>The <code>map_each</code> feature allows you to customize how items are "
            + "transformed into strings. If you do not provide a <code>map_each</code> function, "
            + "the standard conversion is as follows: "
            + "<ul>"
            + "<li>Values that are already strings are left as-is."
            + "<li><a href='File.html'><code>File</code></a> objects are turned into their "
            + "    <code>File.path</code> values."
            + "<li>All other types are turned into strings in an <i>unspecified</i> manner. For "
            + "    this reason, you should avoid passing values that are not of string or "
            + "    <code>File</code> type to <code>add()</code>, and if you pass them to "
            + "    <code>add_all()</code> or <code>add_joined()</code> then you should provide a "
            + "    <code>map_each</code> function."
            + "</ul>"
            + ""
            + "<p>When using string formatting (<code>format</code>, <code>format_each</code>, and "
            + "<code>format_joined</code> params of the <code>add*()</code> methods), the format "
            + "template is interpreted in the same way as <code>%</code>-substitution on strings, "
            + "except that the template must have exactly one substitution placeholder and it must "
            + "be <code>%s</code>. Literal percents may be escaped as <code>%%</code>. Formatting "
            + "is applied after the value is converted to a string as per the above."
            + ""
            + "<p>Each of the <code>add*()</code> methods have an alternate form that accepts an "
            + "extra positional parameter, an \"arg name\" string to insert before the rest of the "
            + "arguments. For <code>add_all</code> and <code>add_joined</code> the extra string "
            + "will not be added if the sequence turns out to be empty. "
            + "For instance, the same usage can add either <code>--foo val1 val2 val3 --bar"
            + "</code> or just <code>--bar</code> to the command line, depending on whether the "
            + "given sequence contains <code>val1..val3</code> or is empty."
            + ""
            + "<p>If the size of the command line can grow longer than the maximum size allowed by "
            + "the system, the arguments can be spilled over into parameter files. See "
            + "<a href='#use_param_file'><code>use_param_file()</code></a> and "
            + "<a href='#set_param_file_format'><code>set_param_file_format()</code></a>."
            + ""
            + "<p>Example: Suppose we wanted to generate the command line: "
            + "<pre>\n"
            + "--foo foo1.txt foo2.txt ... fooN.txt --bar bar1.txt,bar2.txt,...,barM.txt --baz\n"
            + "</pre>"
            + "We could use the following <code>Args</code> object: "
            + "<pre class=language-python>\n"
            + "# foo_deps and bar_deps are depsets containing\n"
            + "# File objects for the foo and bar .txt files.\n"
            + "args = ctx.actions.args()\n"
            + "args.add_all(\"--foo\", foo_deps)\n"
            + "args.add_joined(\"--bar\", bar_deps, join_with=\",\")\n"
            + "args.add(\"--baz\")\n"
            + "ctx.actions.run(\n"
            + "  ...\n"
            + "  arguments = [args],\n"
            + "  ...\n"
            + ")\n"
            + "</pre>"
  )
  @VisibleForTesting
  public static class Args extends SkylarkMutable {
    private final Mutability mutability;
    private final SkylarkSemantics skylarkSemantics;
    private final SkylarkCustomCommandLine.Builder commandLine;
    private ParameterFileType parameterFileType = ParameterFileType.SHELL_QUOTED;
    private String flagFormatString;
    private boolean useAlways;

    @SkylarkCallable(
      name = "add",
      doc =
          "Appends an argument to this command line."
              + ""
              + "<p><b>Deprecation note:</b> The <code>before_each</code>, <code>join_with</code> "
              + "and <code>map_fn</code> params are replaced by the <a href='#add_all'><code>"
              + "add_all()</code></a> and <a href='#add_joined'><code>add_joined()</code></a> "
              + "methods. These parameters will be removed, and are currently disallowed if the "
              + "<a href='../backward-compatibility.$DOC_EXT#new-args-api'><code>"
              + "--incompatible_disallow_old_style_args_add</code></a> flag is set. Likewise, "
              + "<code>value</code> should now be a scalar value, not a list, tuple, or depset of "
              + "items.",
      parameters = {
        @Param(
          name = "arg_name_or_value",
          doc =
              "If two positional parameters are passed this is interpreted as the arg name. "
                  + "The arg name is added before the value without any processing. "
                  + "If only one positional parameter is passed, it is interpreted as "
                  + "<code>value</code> (see below)."
        ),
        @Param(
          name = "value",
          defaultValue = "unbound",
          doc =
              "The object to append. It will be converted to a string using the standard "
                  + "conversion mentioned above. Since there is no <code>map_each</code> parameter "
                  + "for this function, <code>value</code> should be either a string or a <code>"
                  + "File</code>."
                  + ""
                  + "<p><i>Deprecated behavior:</i> <code>value</code> may also be a list, tuple, "
                  + "or depset of multiple items to append."
        ),
        @Param(
          name = "format",
          type = String.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc =
              "A format string pattern, to be applied to the stringified version of <code>value"
                  + "</code>."
                  + ""
                  + "<p><i>Deprecated behavior:</i> If <code>value</code> is a list or depset, "
                  + "formatting is applied to each item."
        ),
        @Param(
          name = "before_each",
          type = String.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc =
              "<i>Deprecated:</i> Only supported when <code>value</code> is a list, tuple, or "
                  + "depset. This string will be appended prior to appending each item."
        ),
        @Param(
          name = "join_with",
          type = String.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc =
              "<i>Deprecated:</i> Only supported when <code>value</code> is a list, tuple, or "
                  + "depset. All items will be joined together using this string to form a single "
                  + "arg to append."
        ),
        @Param(
          name = "map_fn",
          type = BaseFunction.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc =
              "<i>Deprecated:</i> Only supported when <code>value</code> is a list, tuple, or "
                  + "depset. This is a function that transforms the sequence of items into a list "
                  + "of strings. The sequence of items is given as a positional argument -- the "
                  + "function must not take any other parameters -- and the returned list's length "
                  + "must equal the number of items. Use <code>map_each</code> of <code>add_all"
                  + "</code> or <code>add_joined</code> instead."
        )
      },
      useLocation = true
    )
    public NoneType addArgument(
        Object argNameOrValue,
        Object value,
        Object format,
        Object beforeEach,
        Object joinWith,
        Object mapFn,
        Location loc)
        throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      final String argName;
      if (value == Runtime.UNBOUND) {
        value = argNameOrValue;
        argName = null;
      } else {
        validateArgName(argNameOrValue, loc);
        argName = (String) argNameOrValue;
      }
      if (argName != null) {
        commandLine.add(argName);
      }
      if (value instanceof SkylarkNestedSet || value instanceof SkylarkList) {
        if (skylarkSemantics.incompatibleDisallowOldStyleArgsAdd()) {
          throw new EvalException(
              loc,
              "Args#add no longer accepts vectorized arguments when "
                  + "--incompatible_disallow_old_style_args_add is set. "
                  + "Please use Args#add_all or Args#add_joined.");
        }
        addVectorArg(
            value,
            /* argName= */ null,
            mapFn != Runtime.NONE ? (BaseFunction) mapFn : null,
            /* mapEach= */ null,
            format != Runtime.NONE ? (String) format : null,
            beforeEach != Runtime.NONE ? (String) beforeEach : null,
            joinWith != Runtime.NONE ? (String) joinWith : null,
            /* formatJoined= */ null,
            /* omitIfEmpty= */ false,
            /* uniquify= */ false,
            /* terminateWith= */ null,
            loc);

      } else {
        if (mapFn != Runtime.NONE && skylarkSemantics.incompatibleDisallowOldStyleArgsAdd()) {
          throw new EvalException(
              loc,
              "Args#add no longer accepts map_fn when"
                  + "--incompatible_disallow_old_style_args_add is set. "
                  + "Please eagerly map the value.");
        }
        if (beforeEach != Runtime.NONE) {
          throw new EvalException(null, "'before_each' is not supported for scalar arguments");
        }
        if (joinWith != Runtime.NONE) {
          throw new EvalException(null, "'join_with' is not supported for scalar arguments");
        }
        addScalarArg(
            value,
            format != Runtime.NONE ? (String) format : null,
            mapFn != Runtime.NONE ? (BaseFunction) mapFn : null,
            loc);
      }
      return Runtime.NONE;
    }

    @SkylarkCallable(
      name = "add_all",
      doc =
          "Appends multiple arguments to this command line. For depsets, the items are "
              + "evaluated lazily during the execution phase."
              + ""
              + "<p>Most of the processing occurs over a list of arguments to be appended, as per "
              + "the following steps:"
              + "<ol>"
              + "<li>If <code>map_each</code> is given, it is applied to each input item, and the "
              + "    resulting lists of strings are concatenated to form the initial argument "
              + "    list. Otherwise, the initial argument list is the result of applying the "
              + "    standard conversion to each item."
              + "<li>Each argument in the list is formatted with <code>format_each</code>, if "
              + "    present."
              + "<li>If <code>uniquify</code> is true, duplicate arguments are removed. The first "
              + "    occurrence is the one that remains."
              + "<li>If a <code>before_each</code> string is given, it is inserted as a new "
              + "    argument before each existing argument in the list. This effectively doubles "
              + "    the number of arguments to be appended by this point."
              + "<li>Except in the case that the list is empty and <code>omit_if_empty</code> is "
              + "    true (the default), the arg name and <code>terminate_with</code> are "
              + "    inserted as the first and last arguments, respectively, if they are given."
              + "</ol>"
              + "Note that empty strings are valid arguments that are subject to all these "
              + "processing steps.",
      parameters = {
        @Param(
          name = "arg_name_or_values",
          doc =
              "If two positional parameters are passed this is interpreted as the arg name. "
                  + "The arg name is added before the <code>values</code> without any processing. "
                  + "This arg name will not be added if <code>omit_if_empty</code> is true "
                  + "(the default) and no other items are appended (as happens if "
                  + "<code>values</code> is empty or all of its items are filtered). "
                  + "If only one positional parameter is passed, it is interpreted as "
                  + "<code>values</code> (see below)."
        ),
        @Param(
          name = "values",
          allowedTypes = {
            @ParamType(type = SkylarkList.class),
            @ParamType(type = SkylarkNestedSet.class),
          },
          defaultValue = "unbound",
          doc = "The list, tuple, or depset whose items will be appended."
        ),
        @Param(
          name = "map_each",
          type = BaseFunction.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc =
              "A function that converts each item to zero or more strings, which may be further "
                  + "processed before appending. If this param is not provided, the standard "
                  + "conversion is used."
                  + ""
                  + "<p>The function takes in the item as a positional parameter and must have no "
                  + "other parameters. The return value's type depends on how many arguments "
                  + "are to be produced for the item:"
                  + "<ul>"
                  + "<li>In the common case when each item turns into one string, the function "
                  + "    should return that string."
                  + "<li>If the item is to be filtered out entirely, the function should return "
                  + "    <code>None</code>."
                  + "<li>If the item turns into multiple strings, the function returns a list of "
                  + "    those strings."
                  + "</ul>"
                  + "Returning a single string or <code>None</code> has the same effect as "
                  + "returning a list of length 1 or length 0 respectively. However, it is more "
                  + "efficient and readable to avoid creating a list where it is not needed."
                  + ""
                  + "<p><i>Warning:</i> <a href='globals.html#print'><code>print()</code></a> "
                  + "statements that are executed during the call to <code>map_each</code> will "
                  + "not produce any visible output."
        ),
        @Param(
          name = "format_each",
          type = String.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc =
              "An optional format string pattern, applied to each string returned by the "
                  + "<code>map_each</code> function. "
                  + "The format string must have exactly one '%s' placeholder."
        ),
        @Param(
          name = "before_each",
          type = String.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc =
              "An optional string to append before each argument derived from <code>values</code> "
                  + "is appended."
        ),
        @Param(
          name = "omit_if_empty",
          type = Boolean.class,
          named = true,
          positional = false,
          defaultValue = "True",
          doc =
              "If true, if there are no arguments derived from <code>values</code> to be appended, "
                  + "then all further processing is suppressed and the command line will be "
                  + "unchanged. If false, the arg name and <code>terminate_with</code>, "
                  + "if provided, will still be appended regardless of whether or not there are "
                  + "other arguments."
        ),
        @Param(
          name = "uniquify",
          type = Boolean.class,
          named = true,
          positional = false,
          defaultValue = "False",
          doc =
              "If true, duplicate arguments that are derived from <code>values</code> will be "
                  + "omitted. Only the first occurrence of each argument will remain. Usually this "
                  + "feature is not needed because depsets already omit duplicates, but it can be "
                  + "useful if <code>map_each</code> emits the same string for multiple items."
        ),
        @Param(
          name = "terminate_with",
          type = String.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc =
              "An optional string to append after all other arguments. This string will not be "
                  + "added if <code>omit_if_empty</code> is true (the default) and no other items "
                  + "are appended (as happens if <code>values</code> is empty or all of its items "
                  + "are filtered)."
        ),
      },
      useLocation = true
    )
    public NoneType addAll(
        Object argNameOrValue,
        Object values,
        Object mapEach,
        Object formatEach,
        Object beforeEach,
        Boolean omitIfEmpty,
        Boolean uniquify,
        Object terminateWith,
        Location loc)
        throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      final String argName;
      if (values == Runtime.UNBOUND) {
        values = argNameOrValue;
        validateValues(values, loc);
        argName = null;
      } else {
        validateArgName(argNameOrValue, loc);
        argName = (String) argNameOrValue;
      }
      addVectorArg(
          values,
          argName,
          /* mapAll= */ null,
          mapEach != Runtime.NONE ? (BaseFunction) mapEach : null,
          formatEach != Runtime.NONE ? (String) formatEach : null,
          beforeEach != Runtime.NONE ? (String) beforeEach : null,
          /* joinWith= */ null,
          /* formatJoined= */ null,
          omitIfEmpty,
          uniquify,
          terminateWith != Runtime.NONE ? (String) terminateWith : null,
          loc);
      return Runtime.NONE;
    }

    @SkylarkCallable(
      name = "add_joined",
      doc =
          "Appends an argument to this command line by concatenating together multiple values "
              + "using a separator. For depsets, the items are evaluated lazily during the "
              + "execution phase."
              + ""
              + "<p>Processing is similar to <a href='#add_all'><code>add_all()</code></a>, but "
              + "the list of arguments derived from <code>values</code> is combined into a single "
              + "argument as if by <code>join_with.join(...)</code>, and then formatted using the "
              + "given <code>format_joined</code> string template. Unlike <code>add_all()</code>, "
              + "there is no <code>before_each</code> or <code>terminate_with</code> parameter "
              + "since these are not generally useful when the items are combined into a single "
              + "argument."
              + ""
              + "<p>If after filtering there are no strings to join into an argument, and if "
              + "<code>omit_if_empty</code> is true (the default), no processing is done. "
              + "Otherwise if there are no strings to join but <code>omit_if_empty</code> is "
              + "false, the joined string will be an empty string.",
      parameters = {
        @Param(
          name = "arg_name_or_values",
          doc =
              "If two positional parameters are passed this is interpreted as the arg name. "
                  + "The arg name is added before <code>values</code> without any processing. "
                  + "This arg will not be added if <code>omit_if_empty</code> is true "
                  + "(the default) and there are no strings derived from <code>values</code> "
                  + "to join together (which can happen if <code>values</code> is empty "
                  + "or all of its items are filtered)."
                  + "If only one positional parameter is passed, it is interpreted as "
                  + "<code>values</code> (see below)."
        ),
        @Param(
          name = "values",
          allowedTypes = {
            @ParamType(type = SkylarkList.class),
            @ParamType(type = SkylarkNestedSet.class),
          },
          defaultValue = "unbound",
          doc = "The list, tuple, or depset whose items will be joined."
        ),
        @Param(
          name = "join_with",
          type = String.class,
          named = true,
          positional = false,
          doc =
              "A delimiter string used to join together the strings obtained from applying "
                  + "<code>map_each</code> and <code>format_each</code>, in the same manner as "
                  + "<a href='string.html#join'><code>string.join()</code></a>."
        ),
        @Param(
          name = "map_each",
          type = BaseFunction.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc = "Same as for <a href='#add_all.map_each'><code>add_all</code></a>."
        ),
        @Param(
          name = "format_each",
          type = String.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc = "Same as for <a href='#add_all.format_each'><code>add_all</code></a>."
        ),
        @Param(
          name = "format_joined",
          type = String.class,
          named = true,
          positional = false,
          defaultValue = "None",
          noneable = true,
          doc =
              "An optional format string pattern applied to the joined string. "
                  + "The format string must have exactly one '%s' placeholder."
        ),
        @Param(
          name = "omit_if_empty",
          type = Boolean.class,
          named = true,
          positional = false,
          defaultValue = "True",
          doc =
              "If true, if there are no strings to join together (either because <code>values"
                  + "</code> is empty or all its items are filtered), then all further processing "
                  + "is suppressed and the command line will be unchanged. If false, then even if "
                  + "there are no strings to join together, two arguments will be appended: "
                  + "the arg name followed by an empty string (which is the logical join "
                  + "of zero strings)."
        ),
        @Param(
          name = "uniquify",
          type = Boolean.class,
          named = true,
          positional = false,
          defaultValue = "False",
          doc = "Same as for <a href='#add_all.uniquify'><code>add_all</code></a>."
        )
      },
      useLocation = true
    )
    public NoneType addJoined(
        Object argNameOrValue,
        Object values,
        String joinWith,
        Object mapEach,
        Object formatEach,
        Object formatJoined,
        Boolean omitIfEmpty,
        Boolean uniquify,
        Location loc)
        throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      final String argName;
      if (values == Runtime.UNBOUND) {
        values = argNameOrValue;
        validateValues(values, loc);
        argName = null;
      } else {
        validateArgName(argNameOrValue, loc);
        argName = (String) argNameOrValue;
      }
      addVectorArg(
          values,
          argName,
          /* mapAll= */ null,
          mapEach != Runtime.NONE ? (BaseFunction) mapEach : null,
          formatEach != Runtime.NONE ? (String) formatEach : null,
          /* beforeEach= */ null,
          joinWith,
          formatJoined != Runtime.NONE ? (String) formatJoined : null,
          omitIfEmpty,
          uniquify,
          /* terminateWith= */ null,
          loc);
      return Runtime.NONE;
    }

    private void addVectorArg(
        Object value,
        String argName,
        BaseFunction mapAll,
        BaseFunction mapEach,
        String formatEach,
        String beforeEach,
        String joinWith,
        String formatJoined,
        boolean omitIfEmpty,
        boolean uniquify,
        String terminateWith,
        Location loc)
        throws EvalException {
      SkylarkCustomCommandLine.VectorArg.Builder vectorArg;
      if (value instanceof SkylarkNestedSet) {
        NestedSet<?> nestedSet = ((SkylarkNestedSet) value).getSet(Object.class);
        vectorArg = new SkylarkCustomCommandLine.VectorArg.Builder(nestedSet);
      } else {
        SkylarkList skylarkList = (SkylarkList) value;
        vectorArg = new SkylarkCustomCommandLine.VectorArg.Builder(skylarkList);
      }
      validateMapEach(mapEach, loc);
      validateFormatString("format_each", formatEach);
      validateFormatString("format_joined", formatJoined);
      vectorArg
          .setLocation(loc)
          .setArgName(argName)
          .setMapAll(mapAll)
          .setFormatEach(formatEach)
          .setBeforeEach(beforeEach)
          .setJoinWith(joinWith)
          .setFormatJoined(formatJoined)
          .omitIfEmpty(omitIfEmpty)
          .uniquify(uniquify)
          .setTerminateWith(terminateWith)
          .setMapEach(mapEach);
      commandLine.add(vectorArg);
    }

    private void validateArgName(Object argName, Location loc) throws EvalException {
      if (!(argName instanceof String)) {
        throw new EvalException(
            loc,
            String.format(
                "expected value of type 'string' for arg name, got '%s'",
                argName.getClass().getSimpleName()));
      }
    }

    private void validateValues(Object values, Location loc) throws EvalException {
      if (!(values instanceof SkylarkList || values instanceof SkylarkNestedSet)) {
        throw new EvalException(
            loc,
            String.format(
                "expected value of type 'sequence or depset' for values, got '%s'",
                values.getClass().getSimpleName()));
      }
    }

    private void validateMapEach(@Nullable BaseFunction mapEach, Location loc)
        throws EvalException {
      if (mapEach == null) {
        return;
      }
      Shape shape = mapEach.getSignature().getSignature().getShape();
      boolean valid =
          shape.getMandatoryPositionals() == 1
              && shape.getOptionalPositionals() == 0
              && shape.getMandatoryNamedOnly() == 0
              && shape.getOptionalPositionals() == 0;
      if (!valid) {
        throw new EvalException(
            loc, "map_each must be a function that accepts a single positional argument");
      }
    }

    private void validateFormatString(String argumentName, @Nullable String formatStr)
        throws EvalException {
      if (formatStr != null
          && skylarkSemantics.incompatibleDisallowOldStyleArgsAdd()
          && !CommandLineItemSimpleFormatter.isValid(formatStr)) {
        throw new EvalException(
            null,
            String.format(
                "Invalid value for parameter \"%s\": Expected string with a single \"%%s\"",
                argumentName));
      }
    }

    private void addScalarArg(Object value, String format, BaseFunction mapFn, Location loc)
        throws EvalException {
      validateFormatString("format", format);
      if (format == null && mapFn == null) {
        commandLine.add(value);
      } else {
        ScalarArg.Builder scalarArg =
            new ScalarArg.Builder(value).setLocation(loc).setFormat(format).setMapFn(mapFn);
        commandLine.add(scalarArg);
      }
    }

    @SkylarkCallable(
      name = "use_param_file",
      doc =
          "Spills the args to a params file, replacing them with a pointer to the param file. "
              + "Use when your args may be too large for the system's command length limits ",
      parameters = {
        @Param(
          name = "param_file_arg",
          type = String.class,
          named = true,
          doc =
              "A format string with a single \"%s\". "
                  + "If the args are spilled to a params file then they are replaced "
                  + "with an argument consisting of this string formatted with "
                  + "the path of the params file."
        ),
        @Param(
          name = "use_always",
          type = Boolean.class,
          named = true,
          positional = false,
          defaultValue = "False",
          doc =
              "Whether to always spill the args to a params file. If false, "
                  + "bazel will decide whether the arguments need to be spilled "
                  + "based on your system and arg length."
        )
      }
    )
    public void useParamsFile(String paramFileArg, Boolean useAlways) throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      if (!paramFileArg.contains("%s")) {
        throw new EvalException(
            null,
            "Invalid value for parameter \"param_file_arg\": Expected string with a single \"%s\"");
      }
      this.flagFormatString = paramFileArg;
      this.useAlways = useAlways;
    }

    @SkylarkCallable(
      name = "set_param_file_format",
      doc = "Sets the format of the param file when written to disk",
      parameters = {
        @Param(
          name = "format",
          type = String.class,
          named = true,
          doc =
              "The format of the param file. Must be one of:<br>"
                  + "\"shell\": All arguments are shell quoted and separated by whitespace<br>"
                  + "\"multiline\": All arguments are unquoted and separated by newline characters"
                  + "The format defaults to \"shell\" if not called."
        )
      }
    )
    public void setParamFileFormat(String format) throws EvalException {
      if (isImmutable()) {
        throw new EvalException(null, "cannot modify frozen value");
      }
      final ParameterFileType parameterFileType;
      switch (format) {
        case "shell":
          parameterFileType = ParameterFileType.SHELL_QUOTED;
          break;
        case "multiline":
          parameterFileType = ParameterFileType.UNQUOTED;
          break;
        default:
          throw new EvalException(
              null,
              "Invalid value for parameter \"format\": Expected one of \"shell\", \"multiline\"");
      }
      this.parameterFileType = parameterFileType;
    }

    private Args(@Nullable Mutability mutability, SkylarkSemantics skylarkSemantics) {
      this.mutability = mutability != null ? mutability : Mutability.IMMUTABLE;
      this.skylarkSemantics = skylarkSemantics;
      this.commandLine = new SkylarkCustomCommandLine.Builder(skylarkSemantics);
    }

    public SkylarkCustomCommandLine build() {
      return commandLine.build();
    }

    @Override
    public Mutability mutability() {
      return mutability;
    }

    @Override
    public void repr(SkylarkPrinter printer) {
      printer.append("context.args() object");
    }
  }

  @SkylarkCallable(
    name = "args",
    doc = "Returns an Args object that can be used to build memory-efficient command lines.",
    useEnvironment = true
  )
  public Args args(Environment env) {
    return new Args(env.mutability(), skylarkSemantics);
  }

  @Override
  public boolean isImmutable() {
    return context.isImmutable();
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append("actions for");
    context.repr(printer);
  }

  void nullify() {
    ruleContext = null;
  }
}
