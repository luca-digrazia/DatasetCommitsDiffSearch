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

package com.google.devtools.build.lib.sandbox;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.devtools.build.lib.actions.ExecException;
import com.google.devtools.build.lib.actions.Spawn;
import com.google.devtools.build.lib.actions.UserExecException;
import com.google.devtools.build.lib.analysis.BlazeDirectories;
import com.google.devtools.build.lib.buildtool.BuildRequest;
import com.google.devtools.build.lib.exec.SpawnResult;
import com.google.devtools.build.lib.runtime.CommandEnvironment;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.util.OS;
import com.google.devtools.build.lib.vfs.FileSystem;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.vfs.Symlinks;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

/** Spawn runner that uses linux sandboxing APIs to execute a local subprocess. */
final class LinuxSandboxedSpawnRunner extends AbstractSandboxSpawnRunner {
  private static final String LINUX_SANDBOX = "linux-sandbox";

  public static boolean isSupported(CommandEnvironment cmdEnv) {
    if (OS.getCurrent() != OS.LINUX) {
      return false;
    }
    Path embeddedTool = getLinuxSandbox(cmdEnv);
    if (embeddedTool == null) {
      // The embedded tool does not exist, meaning that we don't support sandboxing (e.g., while
      // bootstrapping).
      return false;
    }

    Path execRoot = cmdEnv.getExecRoot();

    List<String> args = new ArrayList<>();
    args.add(embeddedTool.getPathString());
    args.add("--");
    args.add("/bin/true");

    ImmutableMap<String, String> env = ImmutableMap.of();
    File cwd = execRoot.getPathFile();

    Command cmd = new Command(args.toArray(new String[0]), env, cwd);
    try {
      cmd.execute(
          /* stdin */ new byte[] {},
          Command.NO_OBSERVER,
          ByteStreams.nullOutputStream(),
          ByteStreams.nullOutputStream(),
          /* killSubprocessOnInterrupt */ true);
    } catch (CommandException e) {
      return false;
    }

    return true;
  }

  private static Path getLinuxSandbox(CommandEnvironment cmdEnv) {
    PathFragment execPath = cmdEnv.getBlazeWorkspace().getBinTools().getExecPath(LINUX_SANDBOX);
    return execPath != null ? cmdEnv.getExecRoot().getRelative(execPath) : null;
  }

  private final SandboxOptions sandboxOptions;
  private final BlazeDirectories blazeDirs;
  private final Path execRoot;
  private final boolean allowNetwork;
  private final Path linuxSandbox;
  private final Path inaccessibleHelperFile;
  private final Path inaccessibleHelperDir;

  LinuxSandboxedSpawnRunner(
      CommandEnvironment cmdEnv,
      BuildRequest buildRequest,
      Path sandboxBase,
      Path inaccessibleHelperFile,
      Path inaccessibleHelperDir) {
    super(
        cmdEnv,
        sandboxBase,
        buildRequest.getOptions(SandboxOptions.class));
    this.sandboxOptions = cmdEnv.getOptions().getOptions(SandboxOptions.class);
    this.blazeDirs = cmdEnv.getDirectories();
    this.execRoot = cmdEnv.getExecRoot();
    this.allowNetwork = SandboxHelpers.shouldAllowNetwork(cmdEnv.getOptions());
    this.linuxSandbox = getLinuxSandbox(cmdEnv);
    this.inaccessibleHelperFile = inaccessibleHelperFile;
    this.inaccessibleHelperDir = inaccessibleHelperDir;
  }

  @Override
  protected SpawnResult actuallyExec(Spawn spawn, SpawnExecutionPolicy policy)
      throws IOException, ExecException, InterruptedException {
    // Each invocation of "exec" gets its own sandbox.
    Path sandboxPath = getSandboxRoot();
    Path sandboxExecRoot = sandboxPath.getRelative("execroot").getRelative(execRoot.getBaseName());

    Set<Path> writableDirs = getWritableDirs(sandboxExecRoot, spawn.getEnvironment());
    ImmutableSet<PathFragment> outputs = SandboxHelpers.getOutputFiles(spawn);
    int timeoutSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(policy.getTimeoutMillis());
    List<String> arguments = computeCommandLine(
        spawn,
        timeoutSeconds,
        linuxSandbox,
        writableDirs,
        getTmpfsPaths(),
        getReadOnlyBindMounts(blazeDirs, sandboxExecRoot),
        allowNetwork || SandboxHelpers.shouldAllowNetwork(spawn));

    SandboxedSpawn sandbox = new SymlinkedSandboxedSpawn(
        sandboxPath,
        sandboxExecRoot,
        arguments,
        spawn.getEnvironment(),
        SandboxHelpers.getInputFiles(spawn, policy, execRoot),
        outputs,
        writableDirs);
    return runSpawn(spawn, sandbox, policy, execRoot, timeoutSeconds);
  }

  private List<String> computeCommandLine(
      Spawn spawn,
      int timeoutSeconds,
      Path linuxSandbox,
      Set<Path> writableDirs,
      Set<Path> tmpfsPaths,
      Map<Path, Path> bindMounts,
      boolean allowNetwork) {
    List<String> commandLineArgs = new ArrayList<>();
    commandLineArgs.add(linuxSandbox.getPathString());

    if (sandboxOptions.sandboxDebug) {
      commandLineArgs.add("-D");
    }

    // Kill the process after a timeout.
    if (timeoutSeconds != -1) {
      commandLineArgs.add("-T");
      commandLineArgs.add(Integer.toString(timeoutSeconds));
    }

    // Create all needed directories.
    for (Path writablePath : writableDirs) {
      commandLineArgs.add("-w");
      commandLineArgs.add(writablePath.getPathString());
    }

    for (Path tmpfsPath : tmpfsPaths) {
      commandLineArgs.add("-e");
      commandLineArgs.add(tmpfsPath.getPathString());
    }

    for (ImmutableMap.Entry<Path, Path> bindMount : bindMounts.entrySet()) {
      commandLineArgs.add("-M");
      commandLineArgs.add(bindMount.getValue().getPathString());

      // The file is mounted in a custom location inside the sandbox.
      if (!bindMount.getKey().equals(bindMount.getValue())) {
        commandLineArgs.add("-m");
        commandLineArgs.add(bindMount.getKey().getPathString());
      }
    }

    if (!allowNetwork) {
      // Block network access out of the namespace.
      commandLineArgs.add("-N");
    }

    if (sandboxOptions.sandboxFakeHostname) {
      // Use a fake hostname ("localhost") inside the sandbox.
      commandLineArgs.add("-H");
    }

    if (sandboxOptions.sandboxFakeUsername) {
      // Use a fake username ("nobody") inside the sandbox.
      commandLineArgs.add("-U");
    }

    commandLineArgs.add("--");
    commandLineArgs.addAll(spawn.getArguments());
    return commandLineArgs;
  }

  @Override
  protected String getName() {
    return "linux-sandbox";
  }

  @Override
  protected ImmutableSet<Path> getWritableDirs(Path sandboxExecRoot, Map<String, String> env)
      throws IOException {
    ImmutableSet.Builder<Path> writableDirs = ImmutableSet.builder();
    writableDirs.addAll(super.getWritableDirs(sandboxExecRoot, env));

    FileSystem fs = sandboxExecRoot.getFileSystem();
    writableDirs.add(fs.getPath("/dev/shm").resolveSymbolicLinks());
    writableDirs.add(fs.getPath("/tmp"));

    return writableDirs.build();
  }

  private ImmutableSet<Path> getTmpfsPaths() {
    ImmutableSet.Builder<Path> tmpfsPaths = ImmutableSet.builder();
    for (String tmpfsPath : sandboxOptions.sandboxTmpfsPath) {
      tmpfsPaths.add(blazeDirs.getFileSystem().getPath(tmpfsPath));
    }
    return tmpfsPaths.build();
  }

  private SortedMap<Path, Path> getReadOnlyBindMounts(
      BlazeDirectories blazeDirs, Path sandboxExecRoot) throws UserExecException {
    Path tmpPath = blazeDirs.getFileSystem().getPath("/tmp");
    final SortedMap<Path, Path> bindMounts = Maps.newTreeMap();
    if (blazeDirs.getWorkspace().startsWith(tmpPath)) {
      bindMounts.put(blazeDirs.getWorkspace(), blazeDirs.getWorkspace());
    }
    if (blazeDirs.getOutputBase().startsWith(tmpPath)) {
      bindMounts.put(blazeDirs.getOutputBase(), blazeDirs.getOutputBase());
    }
    for (ImmutableMap.Entry<String, String> additionalMountPath :
        sandboxOptions.sandboxAdditionalMounts) {
      try {
        final Path mountTarget = blazeDirs.getFileSystem().getPath(additionalMountPath.getValue());
        // If source path is relative, treat it as a relative path inside the execution root
        final Path mountSource = sandboxExecRoot.getRelative(additionalMountPath.getKey());
        // If a target has more than one source path, the latter one will take effect.
        bindMounts.put(mountTarget, mountSource);
      } catch (IllegalArgumentException e) {
        throw new UserExecException(
            String.format("Error occurred when analyzing bind mount pairs. %s", e.getMessage()));
      }
    }
    for (Path inaccessiblePath : getInaccessiblePaths()) {
      if (inaccessiblePath.isDirectory(Symlinks.NOFOLLOW)) {
        bindMounts.put(inaccessiblePath, inaccessibleHelperDir);
      } else {
        bindMounts.put(inaccessiblePath, inaccessibleHelperFile);
      }
    }
    validateBindMounts(bindMounts);
    return bindMounts;
  }

  /**
   * This method does the following things: - If mount source does not exist on the host system,
   * throw an error message - If mount target exists, check whether the source and target are of the
   * same type - If mount target does not exist on the host system, throw an error message
   *
   * @param bindMounts the bind mounts map with target as key and source as value
   * @throws UserExecException
   */
  private void validateBindMounts(SortedMap<Path, Path> bindMounts) throws UserExecException {
    for (SortedMap.Entry<Path, Path> bindMount : bindMounts.entrySet()) {
      final Path source = bindMount.getValue();
      final Path target = bindMount.getKey();
      // Mount source should exist in the file system
      if (!source.exists()) {
        throw new UserExecException(String.format("Mount source '%s' does not exist.", source));
      }
      // If target exists, but is not of the same type as the source, then we cannot mount it.
      if (target.exists()) {
        boolean areBothDirectories = source.isDirectory() && target.isDirectory();
        boolean isSourceFile = source.isFile() || source.isSymbolicLink();
        boolean isTargetFile = target.isFile() || target.isSymbolicLink();
        boolean areBothFiles = isSourceFile && isTargetFile;
        if (!(areBothDirectories || areBothFiles)) {
          // Source and target are not of the same type; we cannot mount it.
          throw new UserExecException(
              String.format(
                  "Mount target '%s' is not of the same type as mount source '%s'.",
                  target, source));
        }
      } else {
        // Mount target should exist in the file system
        throw new UserExecException(
            String.format(
                "Mount target '%s' does not exist. Bazel only supports bind mounting on top of "
                    + "existing files/directories. Please create an empty file or directory at "
                    + "the mount target path according to the type of mount source.",
                target));
      }
    }
  }
}
