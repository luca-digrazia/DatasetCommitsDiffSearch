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

package com.google.devtools.build.lib.runtime.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.CommandLine;
import com.google.devtools.build.lib.actions.CommandLineExpansionException;
import com.google.devtools.build.lib.actions.ExecException;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.FilesToRunProvider;
import com.google.devtools.build.lib.analysis.RunfilesSupport;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.RunUnder;
import com.google.devtools.build.lib.analysis.test.TestProvider;
import com.google.devtools.build.lib.analysis.test.TestRunnerAction;
import com.google.devtools.build.lib.analysis.test.TestTargetExecutionSettings;
import com.google.devtools.build.lib.buildtool.BuildRequest;
import com.google.devtools.build.lib.buildtool.BuildRequestOptions;
import com.google.devtools.build.lib.buildtool.BuildResult;
import com.google.devtools.build.lib.buildtool.BuildTool;
import com.google.devtools.build.lib.buildtool.OutputDirectoryLinksUtils;
import com.google.devtools.build.lib.buildtool.TargetValidator;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.Reporter;
import com.google.devtools.build.lib.exec.ExecutionOptions;
import com.google.devtools.build.lib.exec.StandaloneTestStrategy;
import com.google.devtools.build.lib.exec.SymlinkTreeHelper;
import com.google.devtools.build.lib.exec.TestStrategy;
import com.google.devtools.build.lib.packages.InputFile;
import com.google.devtools.build.lib.packages.NoSuchPackageException;
import com.google.devtools.build.lib.packages.NoSuchTargetException;
import com.google.devtools.build.lib.packages.NonconfigurableAttributeMapper;
import com.google.devtools.build.lib.packages.OutputFile;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.packages.TargetUtils;
import com.google.devtools.build.lib.pkgcache.LoadingFailedException;
import com.google.devtools.build.lib.runtime.BlazeCommand;
import com.google.devtools.build.lib.runtime.BlazeCommandResult;
import com.google.devtools.build.lib.runtime.Command;
import com.google.devtools.build.lib.runtime.CommandEnvironment;
import com.google.devtools.build.lib.runtime.ProcessWrapperUtil;
import com.google.devtools.build.lib.server.CommandProtos.EnvironmentVariable;
import com.google.devtools.build.lib.server.CommandProtos.ExecRequest;
import com.google.devtools.build.lib.shell.AbnormalTerminationException;
import com.google.devtools.build.lib.shell.BadExitStatusException;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.util.CommandBuilder;
import com.google.devtools.build.lib.util.CommandDescriptionForm;
import com.google.devtools.build.lib.util.CommandFailureUtils;
import com.google.devtools.build.lib.util.ExitCode;
import com.google.devtools.build.lib.util.FileType;
import com.google.devtools.build.lib.util.OS;
import com.google.devtools.build.lib.util.OptionsUtils;
import com.google.devtools.build.lib.util.ShellEscaper;
import com.google.devtools.build.lib.util.io.OutErr;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionDocumentationCategory;
import com.google.devtools.common.options.OptionEffectTag;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;
import com.google.devtools.common.options.OptionsProvider;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * Builds and run a target with the given command line arguments.
 */
@Command(name = "run",
         builds = true,
         options = { RunCommand.RunOptions.class },
         inherits = { BuildCommand.class },
         shortDescription = "Runs the specified target.",
         help = "resource:run.txt",
         allowResidue = true,
         hasSensitiveResidue = true,
         binaryStdOut = true,
         completion = "label-bin",
         binaryStdErr = true)
public class RunCommand implements BlazeCommand  {
  public static class RunOptions extends OptionsBase {
    @Option(
      name = "direct_run",
      category = "run",
      defaultValue = "false",
      documentationCategory =  OptionDocumentationCategory.OUTPUT_PARAMETERS,
      effectTags = {OptionEffectTag.EXECUTION},
      help = "If set, the 'run' command will execute the binary to be executed in the terminal "
          + "where the command was called. Otherwise, it'll be executed as a child of the server "
          + "process. If set, the binary will have access to direct terminal I/O and the command "
          + "lock will not be held during its execution. This makes it possible to run other "
          + "commands in parallel.")
    public boolean direct;

    @Option(
      name = "script_path",
      category = "run",
      defaultValue = "null",
      documentationCategory = OptionDocumentationCategory.OUTPUT_PARAMETERS,
      effectTags = {OptionEffectTag.AFFECTS_OUTPUTS, OptionEffectTag.EXECUTION},
      converter = OptionsUtils.PathFragmentConverter.class,
      help =
          "If set, write a shell script to the given file which invokes the target. "
              + "If this option is set, the target is not run from %{product}. "
              + "Use '%{product} run --script_path=foo //foo && ./foo' to invoke target '//foo' "
              + "This differs from '%{product} run //foo' in that the %{product} lock is released "
              + "and the executable is connected to the terminal's stdin."
    )
    public PathFragment scriptPath;
  }

  @VisibleForTesting
  public static final String SINGLE_TARGET_MESSAGE =
      "Only a single target can be run. "
          + "Do not use wildcards that match more than one target";

  @VisibleForTesting
  public static final String NO_TARGET_MESSAGE = "No targets found to run";

  public static final String MULTIPLE_TESTS_MESSAGE =
      "'run' only works with tests with one shard ('--test_sharding_strategy=disabled' is okay) "
      + "and without --runs_per_test";

  // Value of --run_under as of the most recent command invocation.
  private RunUnder currentRunUnder;

  private static final FileType RUNFILES_MANIFEST = FileType.of(".runfiles_manifest");

  @VisibleForTesting  // productionVisibility = Visibility.PRIVATE
  protected BuildResult processRequest(final CommandEnvironment env, BuildRequest request) {
    return new BuildTool(env).processRequest(request, new TargetValidator() {
      @Override
      public void validateTargets(Collection<Target> targets, boolean keepGoing)
          throws LoadingFailedException {
        RunCommand.this.validateTargets(env.getReporter(), targets, keepGoing);
      }
    });
  }

  @Override
  public void editOptions(OptionsParser optionsParser) { }

  /**
   * Compute the arguments the binary should be run with by concatenating the arguments in its
   * {@code args=} attribute and the arguments on the Blaze command line.
   */
  @Nullable
  private List<String> computeArgs(CommandEnvironment env, ConfiguredTarget targetToRun,
      List<String> commandLineArgs) {
    List<String> args = Lists.newArrayList();

    FilesToRunProvider provider = targetToRun.getProvider(FilesToRunProvider.class);
    RunfilesSupport runfilesSupport = provider == null ? null : provider.getRunfilesSupport();
    if (runfilesSupport != null && runfilesSupport.getArgs() != null) {
      CommandLine targetArgs = runfilesSupport.getArgs();
      try {
        Iterables.addAll(args, targetArgs.arguments());
      } catch (CommandLineExpansionException e) {
        env.getReporter().handle(Event.error("Could not expand target command line: " + e));
        return null;
      }
    }
    args.addAll(commandLineArgs);
    return args;
  }

  private BlazeCommandResult runTargetUnderServer(CommandEnvironment env,
      ConfiguredTarget targetToRun, BuildConfiguration configuration,
      ConfiguredTarget runUnderTarget, Path workingDir, List<String> commandLineArgs) {
    RunOptions runOptions = env.getOptions().getOptions(RunOptions.class);
    List<String> args = computeArgs(env, targetToRun, commandLineArgs);
    if (args == null) {
      return BlazeCommandResult.exitCode(ExitCode.ANALYSIS_FAILURE);
    }

    // We now have a unique executable ready to be run.
    //
    // We build up two different versions of the command to run: one with an absolute path, which
    // we'll actually run, and a prettier one with the long absolute path to the executable
    // replaced with a shorter relative path that uses the symlinks in the workspace.
    List<String> cmdLine = new ArrayList<>();
    // process-wrapper does not work on Windows (nor is it necessary), so don't use it
    // on that platform. Also we skip it when writing the command-line to a file instead
    // of executing it directly.
    if (OS.getCurrent() != OS.WINDOWS && runOptions.scriptPath == null) {
      Preconditions.checkState(
          ProcessWrapperUtil.isSupported(env), "process-wrapper not found in embedded tools");
      cmdLine.add(ProcessWrapperUtil.getProcessWrapper(env).getPathString());
    }
    List<String> prettyCmdLine = new ArrayList<>();
    constructCommandLine(cmdLine, prettyCmdLine, env, configuration.getShellExecutable(),
        targetToRun, runUnderTarget, args);

    // Add a newline between the blaze output and the binary's output.
    env.getReporter().getOutErr().printErrLn("");

    if (runOptions.scriptPath != null) {
      String unisolatedCommand = CommandFailureUtils.describeCommand(
          CommandDescriptionForm.COMPLETE_UNISOLATED,
          cmdLine, null, workingDir.getPathString());
      if (writeScript(env, runOptions.scriptPath, unisolatedCommand)) {
        return BlazeCommandResult.exitCode(ExitCode.SUCCESS);
      } else {
        return BlazeCommandResult.exitCode(ExitCode.RUN_FAILURE);
      }
    }

    env.getReporter().handle(Event.info(
        null, "Running command line: " + ShellEscaper.escapeJoinAll(prettyCmdLine)));

    try {
      com.google.devtools.build.lib.shell.Command command = new CommandBuilder()
          .addArgs(cmdLine).setEnv(env.getClientEnv()).setWorkingDir(workingDir).build();

      // Restore a raw EventHandler if it is registered. This allows for blaze run to produce the
      // actual output of the command being run even if --color=no is specified.
      env.getReporter().switchToAnsiAllowingHandler();

      // The command API is a little strange in that the following statement will return normally
      // only if the program exits with exit code 0. If it ends with any other code, we have to
      // catch BadExitStatusException.
      command
          .execute(
              env.getReporter().getOutErr().getOutputStream(),
              env.getReporter().getOutErr().getErrorStream())
          .getTerminationStatus()
          .getExitCode();
      return BlazeCommandResult.exitCode(ExitCode.SUCCESS);
    } catch (BadExitStatusException e) {
      String message = "Non-zero return code '"
          + e.getResult().getTerminationStatus().getExitCode()
          + "' from command: " + e.getMessage();
      env.getReporter().handle(Event.error(message));
      return BlazeCommandResult.exitCode(ExitCode.RUN_FAILURE);
    } catch (AbnormalTerminationException e) {
      // The process was likely terminated by a signal in this case.
      return BlazeCommandResult.exitCode(ExitCode.INTERRUPTED);
    } catch (CommandException e) {
      env.getReporter().handle(Event.error("Error running program: " + e.getMessage()));
      return BlazeCommandResult.exitCode(ExitCode.RUN_FAILURE);
    }
  }

  private void constructCommandLine(List<String> cmdLine, List<String> prettyCmdLine,
      CommandEnvironment env, PathFragment shellExecutable, ConfiguredTarget targetToRun,
      ConfiguredTarget runUnderTarget, List<String> args) {
    String productName = env.getRuntime().getProductName();
    Path executablePath =
        targetToRun.getProvider(FilesToRunProvider.class).getExecutable().getPath();
    PathFragment prettyExecutablePath = OutputDirectoryLinksUtils.getPrettyPath(executablePath,
        env.getWorkspaceName(), env.getWorkspace(),
        env.getOptions().getOptions(BuildRequestOptions.class).getSymlinkPrefix(productName),
        productName);

    RunUnder runUnder = env.getOptions().getOptions(BuildConfiguration.Options.class).runUnder;
    // Insert the command prefix specified by the "--run_under=<command-prefix>" option
    // at the start of the command line.
    if (runUnder != null) {
      String runUnderValue = runUnder.getValue();
      if (runUnderTarget != null) {
        // --run_under specifies a target. Get the corresponding executable.
        // This must be an absolute path, because the run_under target is only
        // in the runfiles of test targets.
        runUnderValue = runUnderTarget
            .getProvider(FilesToRunProvider.class).getExecutable().getPath().getPathString();
        // If the run_under command contains any options, make sure to add them
        // to the command line as well.
        List<String> opts = runUnder.getOptions();
        if (!opts.isEmpty()) {
          runUnderValue += " " + ShellEscaper.escapeJoinAll(opts);
        }
      }
      cmdLine.add(shellExecutable.getPathString());
      cmdLine.add("-c");
      cmdLine.add(runUnderValue + " " + executablePath.getPathString() + " "
          + ShellEscaper.escapeJoinAll(args));
      prettyCmdLine.add(shellExecutable.getPathString());
      prettyCmdLine.add("-c");
      prettyCmdLine.add(runUnderValue + " " + prettyExecutablePath.getPathString() + " "
          + ShellEscaper.escapeJoinAll(args));
    } else {
      cmdLine.add(executablePath.getPathString());
      cmdLine.addAll(args);
      prettyCmdLine.add(prettyExecutablePath.getPathString());
      prettyCmdLine.addAll(args);
    }
  }

  @Override
  public BlazeCommandResult exec(CommandEnvironment env, OptionsProvider options) {
    RunOptions runOptions = options.getOptions(RunOptions.class);
    // This list should look like: ["//executable:target", "arg1", "arg2"]
    List<String> targetAndArgs = options.getResidue();

    // The user must at the least specify an executable target.
    if (targetAndArgs.isEmpty()) {
      env.getReporter().handle(Event.error("Must specify a target to run"));
      return BlazeCommandResult.exitCode(ExitCode.COMMAND_LINE_ERROR);
    }
    String targetString = targetAndArgs.get(0);
    List<String> commandLineArgs = targetAndArgs.subList(1, targetAndArgs.size());
    RunUnder runUnder = options.getOptions(BuildConfiguration.Options.class).runUnder;

    OutErr outErr = env.getReporter().getOutErr();
    List<String> targets = (runUnder != null) && (runUnder.getLabel() != null)
        ? ImmutableList.of(targetString, runUnder.getLabel().toString())
        : ImmutableList.of(targetString);
    BuildRequest request = BuildRequest.create(
        this.getClass().getAnnotation(Command.class).name(), options,
        env.getRuntime().getStartupOptionsProvider(), targets, outErr,
        env.getCommandId(), env.getCommandStartTime());

    currentRunUnder = runUnder;
    BuildResult result;
    try {
      result = processRequest(env, request);
    } finally {
      currentRunUnder = null;
    }

    if (!result.getSuccess()) {
      env.getReporter().handle(Event.error("Build failed. Not running target"));
      return BlazeCommandResult.exitCode(result.getExitCondition());
    }

    // Make sure that we have exactly 1 built target (excluding --run_under),
    // and that it is executable.
    // These checks should only fail if keepGoing is true, because we already did
    // validation before the build began.  See {@link #validateTargets()}.
    Collection<ConfiguredTarget> targetsBuilt = result.getSuccessfulTargets();
    ConfiguredTarget targetToRun = null;
    ConfiguredTarget runUnderTarget = null;

    if (targetsBuilt != null) {
      int maxTargets = runUnder != null && runUnder.getLabel() != null ? 2 : 1;
      if (targetsBuilt.size() > maxTargets) {
        env.getReporter().handle(Event.error(SINGLE_TARGET_MESSAGE));
        return BlazeCommandResult.exitCode(ExitCode.COMMAND_LINE_ERROR);
      }
      for (ConfiguredTarget target : targetsBuilt) {
        ExitCode targetValidation = fullyValidateTarget(env, target);
        if (!targetValidation.equals(ExitCode.SUCCESS)) {
          return BlazeCommandResult.exitCode(targetValidation);
        }
        if (runUnder != null && target.getLabel().equals(runUnder.getLabel())) {
          if (runUnderTarget != null) {
            env.getReporter().handle(Event.error(
                null, "Can't identify the run_under target from multiple options?"));
            return BlazeCommandResult.exitCode(ExitCode.COMMAND_LINE_ERROR);
          }
          runUnderTarget = target;
        } else if (targetToRun == null) {
          targetToRun = target;
        } else {
          env.getReporter().handle(Event.error(SINGLE_TARGET_MESSAGE));
          return BlazeCommandResult.exitCode(ExitCode.COMMAND_LINE_ERROR);
        }
      }
    }

    // Handle target & run_under referring to the same target.
    if (targetToRun == null && runUnderTarget != null) {
      targetToRun = runUnderTarget;
    }

    if (targetToRun == null) {
      env.getReporter().handle(Event.error(NO_TARGET_MESSAGE));
      return BlazeCommandResult.exitCode(ExitCode.COMMAND_LINE_ERROR);
    }

    BuildConfiguration configuration =
        env.getSkyframeExecutor()
            .getConfiguration(env.getReporter(), targetToRun.getConfigurationKey());
    if (configuration == null) {
      // The target may be an input file, which doesn't have a configuration. In that case, we
      // choose any target configuration.
      configuration = result.getBuildConfigurationCollection().getTargetConfigurations().get(0);
    }

    if (!configuration.buildRunfilesManifests()) {
      env.getReporter()
          .handle(
              Event.error("--nobuild_runfile_manifests is incompatible with the \"run\" command"));
      return BlazeCommandResult.exitCode(ExitCode.COMMAND_LINE_ERROR);
    }

    Path runfilesDir;
    try {
      runfilesDir = ensureRunfilesBuilt(env, targetToRun);
    } catch (CommandException e) {
      env.getReporter().handle(Event.error("Error creating runfiles: " + e.getMessage()));
      return BlazeCommandResult.exitCode(ExitCode.LOCAL_ENVIRONMENTAL_ERROR);
    }


    if (!runOptions.direct) {
      return runTargetUnderServer(env, targetToRun, configuration, runUnderTarget,
          runfilesDir, commandLineArgs);
    }

    Map<String, String> runEnvironment = new TreeMap<>();
    List<String> cmdLine = new ArrayList<>();
    Path workingDir;

    runEnvironment.put("BUILD_WORKSPACE_DIRECTORY", env.getWorkspace().getPathString());
    runEnvironment.put("BUILD_WORKING_DIRECTORY", env.getWorkingDirectory().getPathString());

    if (targetToRun.getProvider(TestProvider.class) != null) {
      // This is a test. Provide it with a reasonable approximation of the actual test environment
      ImmutableList<Artifact> statusArtifacts = TestProvider.getTestStatusArtifacts(targetToRun);
      if (statusArtifacts.size() != 1) {
        env.getReporter().handle(Event.error(MULTIPLE_TESTS_MESSAGE));
        return BlazeCommandResult.exitCode(ExitCode.COMMAND_LINE_ERROR);
      }

      TestRunnerAction testAction = (TestRunnerAction) env.getSkyframeExecutor()
          .getActionGraph(env.getReporter()).getGeneratingAction(
              Iterables.getOnlyElement(statusArtifacts));
      TestTargetExecutionSettings settings = testAction.getExecutionSettings();
      // ensureRunfilesBuilt does build the runfiles, but an extra consistency check won't hurt.
      Preconditions.checkState(settings.getRunfilesSymlinksCreated()
          == options.getOptions(BuildConfiguration.Options.class).buildRunfiles);

      ExecutionOptions executionOptions = options.getOptions(ExecutionOptions.class);
      Path tmpDirRoot = TestStrategy.getTmpRoot(
          env.getWorkspace(), env.getExecRoot(), executionOptions);
      PathFragment relativeTmpDir = tmpDirRoot.relativeTo(env.getExecRoot());
      Duration timeout = executionOptions.testTimeout.get(
          testAction.getTestProperties().getTimeout());
      runEnvironment.putAll(StandaloneTestStrategy.DEFAULT_LOCAL_POLICY.computeTestEnvironment(
          testAction,
          env.getClientEnv(),
          timeout,
          settings.getRunfilesDir().relativeTo(env.getExecRoot()),
          relativeTmpDir.getRelative(TestStrategy.getTmpDirName(testAction))));
      workingDir = env.getExecRoot();
      try {
        cmdLine.addAll(TestStrategy.getArgs(testAction));
        cmdLine.addAll(commandLineArgs);
      } catch (ExecException e) {
        env.getReporter().handle(Event.error(e.getMessage()));
        return BlazeCommandResult.exitCode(ExitCode.LOCAL_ENVIRONMENTAL_ERROR);
      }
    } else {
      workingDir = runfilesDir;
      List<String> prettyCmdLine = new ArrayList<>();
      List<String> args = computeArgs(env, targetToRun, commandLineArgs);
      constructCommandLine(cmdLine, prettyCmdLine, env,
          configuration.getShellExecutable(), targetToRun, runUnderTarget, args);
    }

    if (runOptions.scriptPath != null) {
      String unisolatedCommand = CommandFailureUtils.describeCommand(
          CommandDescriptionForm.COMPLETE_UNISOLATED,
          cmdLine, runEnvironment, workingDir.getPathString());
      if (writeScript(env, runOptions.scriptPath, unisolatedCommand)) {
        return BlazeCommandResult.exitCode(ExitCode.SUCCESS);
      } else {
        return BlazeCommandResult.exitCode(ExitCode.RUN_FAILURE);
      }
    }

    ExecRequest.Builder execDescription = ExecRequest.newBuilder()
        .setWorkingDirectory(
            ByteString.copyFrom(workingDir.getPathString(), StandardCharsets.ISO_8859_1));

    ImmutableList<String> shellCmdLine = ImmutableList.<String>of(
        configuration.getShellExecutable().getPathString(),
        "-c",
        ShellEscaper.escapeJoinAll(cmdLine));

    for (String arg : shellCmdLine) {
      execDescription.addArgv(ByteString.copyFrom(arg, StandardCharsets.ISO_8859_1));
    }

    for (Map.Entry<String, String> variable : runEnvironment.entrySet()) {
      execDescription.addEnvironmentVariable(EnvironmentVariable.newBuilder()
          .setName(ByteString.copyFrom(variable.getKey(), StandardCharsets.ISO_8859_1))
          .setValue(ByteString.copyFrom(variable.getValue(), StandardCharsets.ISO_8859_1))
          .build());
    }

    return BlazeCommandResult.execute(execDescription.build());
  }

  /**
   * Ensures that runfiles are built for the specified target. If they already
   * are, does nothing, otherwise builds them.
   *
   * @param target the target to build runfiles for.
   * @return the path of the runfiles directory.
   * @throws CommandException
   */
  private Path ensureRunfilesBuilt(CommandEnvironment env, ConfiguredTarget target)
      throws CommandException {
    FilesToRunProvider provider = target.getProvider(FilesToRunProvider.class);
    RunfilesSupport runfilesSupport = provider == null ? null : provider.getRunfilesSupport();
    if (runfilesSupport == null) {
      return env.getWorkingDirectory();
    }

    Artifact manifest = Preconditions.checkNotNull(runfilesSupport.getRunfilesManifest());
    PathFragment runfilesDir = runfilesSupport.getRunfilesDirectoryExecPath();
    Path workingDir = env.getExecRoot().getRelative(runfilesDir);
    BuildConfiguration configuration =
        env.getSkyframeExecutor().getConfiguration(env.getReporter(), target.getConfigurationKey());
    // On Windows, runfiles tree is disabled.
    // Workspace name directory doesn't exist, so don't add it.
    if (configuration.runfilesEnabled()) {
      workingDir = workingDir.getRelative(runfilesSupport.getRunfiles().getSuffix());
    }

    // When runfiles are not generated, getManifest() returns the
    // .runfiles_manifest file, otherwise it returns the MANIFEST file. This is
    // a handy way to check whether runfiles were built or not.
    if (!RUNFILES_MANIFEST.matches(manifest.getFilename())) {
      // Runfiles already built, nothing to do.
      return workingDir;
    }

    SymlinkTreeHelper helper = new SymlinkTreeHelper(
        manifest.getPath(),
        runfilesSupport.getRunfilesDirectory(),
        false);
    helper.createSymlinksUsingCommand(
        env.getExecRoot(), configuration, env.getBlazeWorkspace().getBinTools());
    return workingDir;
  }

  private boolean writeScript(CommandEnvironment env, PathFragment scriptPathFrag, String cmd) {
    final String SH_SHEBANG = "#!/bin/sh";
    Path scriptPath = env.getWorkingDirectory().getRelative(scriptPathFrag);
    try {
      FileSystemUtils.writeContent(scriptPath, StandardCharsets.ISO_8859_1,
          SH_SHEBANG + "\n" + cmd + " \"$@\"");
      scriptPath.setExecutable(true);
    } catch (IOException e) {
      env.getReporter().handle(Event.error("Error writing run script:" + e.getMessage()));
      return false;
    }
    return true;
  }

  // Make sure we are building exactly 1 binary target.
  // If keepGoing, we'll build all the targets even if they are non-binary.
  private void validateTargets(Reporter reporter, Collection<Target> targets, boolean keepGoing)
      throws LoadingFailedException {
    Target targetToRun = null;
    Target runUnderTarget = null;

    boolean singleTargetWarningWasOutput = false;
    int maxTargets = currentRunUnder != null && currentRunUnder.getLabel() != null ? 2 : 1;
    if (targets.size() > maxTargets) {
      warningOrException(reporter, SINGLE_TARGET_MESSAGE, keepGoing);
      singleTargetWarningWasOutput = true;
    }
    for (Target target : targets) {
      String targetError = validateTarget(target);
      if (targetError != null) {
        warningOrException(reporter, targetError, keepGoing);
      }

      if (currentRunUnder != null && target.getLabel().equals(currentRunUnder.getLabel())) {
        // It's impossible to have two targets with the same label.
        Preconditions.checkState(runUnderTarget == null);
        runUnderTarget = target;
      } else if (targetToRun == null) {
        targetToRun = target;
      } else {
        if (!singleTargetWarningWasOutput) {
          warningOrException(reporter, SINGLE_TARGET_MESSAGE, keepGoing);
        }
        return;
      }
    }
    // Handle target & run_under referring to the same target.
    if ((targetToRun == null) && (runUnderTarget != null)) {
      targetToRun = runUnderTarget;
    }
    if (targetToRun == null) {
      warningOrException(reporter, NO_TARGET_MESSAGE, keepGoing);
    }
  }

  // If keepGoing, print a warning and return the given collection.
  // Otherwise, throw InvalidTargetException.
  private void warningOrException(Reporter reporter, String message,
      boolean keepGoing) throws LoadingFailedException {
    if (keepGoing) {
      reporter.handle(Event.warn(message + ". Will continue anyway"));
    } else {
      throw new LoadingFailedException(message);
    }
  }

  private static String notExecutableError(Target target) {
    return "Cannot run target " + target.getLabel() + ": Not executable";
  }

  /** Returns null if the target is a runnable rule, or an appropriate error message otherwise. */
  private static String validateTarget(Target target) {
    return isExecutable(target)
        ? null
        : notExecutableError(target);
  }

  /**
   * Performs all available validation checks on an individual target.
   *
   * @param configuredTarget ConfiguredTarget to validate
   * @return BlazeCommandResult.exitCode(ExitCode.SUCCESS) if all checks succeeded, otherwise a
   *     different error code.
   * @throws IllegalStateException if unable to find a target from the package manager.
   */
  private ExitCode fullyValidateTarget(CommandEnvironment env, ConfiguredTarget configuredTarget) {

    Target target = null;
    try {
      target = env.getPackageManager().getTarget(env.getReporter(), configuredTarget.getLabel());
    } catch (NoSuchTargetException | NoSuchPackageException | InterruptedException e) {
      env.getReporter().handle(Event.error("Failed to find a target to validate. " + e));
      throw new IllegalStateException("Failed to find a target to validate");
    }

    String targetError = validateTarget(target);

    if (targetError != null) {
      env.getReporter().handle(Event.error(targetError));
      return ExitCode.COMMAND_LINE_ERROR;
    }

    Artifact executable = configuredTarget.getProvider(FilesToRunProvider.class).getExecutable();
    if (executable == null) {
      env.getReporter().handle(Event.error(notExecutableError(target)));
      return ExitCode.COMMAND_LINE_ERROR;
    }

    // Shouldn't happen: We just validated the target.
    Preconditions.checkState(
        executable != null, "Could not find executable for target %s", configuredTarget);
    Path executablePath = executable.getPath();
    try {
      if (!executablePath.exists() || !executablePath.isExecutable()) {
        env.getReporter().handle(Event.error(
            null, "Non-existent or non-executable " + executablePath));
        return ExitCode.BLAZE_INTERNAL_ERROR;
      }
    } catch (IOException e) {
      env.getReporter().handle(Event.error(
          "Error checking " + executablePath.getPathString() + ": " + e.getMessage()));
      return ExitCode.LOCAL_ENVIRONMENTAL_ERROR;
    }

    return ExitCode.SUCCESS;
  }

  /**
   * Return true iff {@code target} is a rule that has an executable file. This includes
   * *_test rules, *_binary rules, generated outputs, and inputs.
   */
  private static boolean isExecutable(Target target) {
    return isPlainFile(target) || isExecutableNonTestRule(target) || TargetUtils.isTestRule(target)
        || isAliasRule(target);
  }

  /**
   * Return true iff {@code target} is a rule that generates an executable file and is user-executed
   * code.
   */
  private static boolean isExecutableNonTestRule(Target target) {
    if (!(target instanceof Rule)) {
      return false;
    }
    Rule rule = ((Rule) target);
    if (rule.getRuleClassObject().hasAttr("$is_executable", Type.BOOLEAN)) {
      return NonconfigurableAttributeMapper.of(rule).get("$is_executable", Type.BOOLEAN);
    }
    return false;
  }

  private static boolean isPlainFile(Target target) {
    return (target instanceof OutputFile) || (target instanceof InputFile);
  }

  private static boolean isAliasRule(Target target) {
    if (!(target instanceof Rule)) {
      return false;
    }

    Rule rule = (Rule) target;
    return rule.getRuleClass().equals("alias") || rule.getRuleClass().equals("bind");
  }
}
