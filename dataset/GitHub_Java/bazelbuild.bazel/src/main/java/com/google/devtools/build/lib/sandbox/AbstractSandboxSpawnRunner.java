// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.sandbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.ActionExecutionMetadata;
import com.google.devtools.build.lib.actions.ExecException;
import com.google.devtools.build.lib.actions.ResourceManager;
import com.google.devtools.build.lib.actions.ResourceManager.ResourceHandle;
import com.google.devtools.build.lib.actions.Spawn;
import com.google.devtools.build.lib.actions.UserExecException;
import com.google.devtools.build.lib.exec.SpawnExecException;
import com.google.devtools.build.lib.exec.SpawnResult;
import com.google.devtools.build.lib.exec.SpawnResult.Status;
import com.google.devtools.build.lib.exec.SpawnRunner;
import com.google.devtools.build.lib.runtime.CommandEnvironment;
import com.google.devtools.build.lib.shell.AbnormalTerminationException;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.shell.CommandResult;
import com.google.devtools.build.lib.util.CommandFailureUtils;
import com.google.devtools.build.lib.util.io.OutErr;
import com.google.devtools.build.lib.vfs.FileSystem;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.io.IOException;
import java.util.Map;

/** Abstract common ancestor for sandbox spawn runners implementing the common parts. */
abstract class AbstractSandboxSpawnRunner implements SpawnRunner {
  private static final int LOCAL_EXEC_ERROR = -1;
  private static final int POSIX_TIMEOUT_EXIT_CODE = /*SIGNAL_BASE=*/128 + /*SIGALRM=*/14;

  private static final String SANDBOX_DEBUG_SUGGESTION =
      "\n\nUse --sandbox_debug to see verbose messages from the sandbox";

  private final Path sandboxBase;
  private final SandboxOptions sandboxOptions;
  private final ImmutableSet<Path> inaccessiblePaths;

  public AbstractSandboxSpawnRunner(
      CommandEnvironment cmdEnv,
      Path sandboxBase,
      SandboxOptions sandboxOptions) {
    this.sandboxBase = sandboxBase;
    this.sandboxOptions = sandboxOptions;
    this.inaccessiblePaths =
        sandboxOptions.getInaccessiblePaths(cmdEnv.getDirectories().getFileSystem());
  }

  @Override
  public SpawnResult exec(Spawn spawn, SpawnExecutionPolicy policy)
      throws ExecException, InterruptedException {
    ActionExecutionMetadata owner = spawn.getResourceOwner();
    policy.report(ProgressStatus.SCHEDULING);
    try (ResourceHandle ignored =
        ResourceManager.instance().acquireResources(owner, spawn.getLocalResources())) {
      policy.report(ProgressStatus.EXECUTING);
      return actuallyExec(spawn, policy);
    } catch (IOException e) {
      throw new UserExecException("I/O exception during sandboxed execution", e);
    }
  }

  protected abstract SpawnResult actuallyExec(Spawn spawn, SpawnExecutionPolicy policy)
      throws ExecException, InterruptedException, IOException;

  protected SpawnResult runSpawn(
      Spawn originalSpawn,
      SandboxedSpawn sandbox,
      SpawnExecutionPolicy policy,
      Path execRoot,
      int timeoutSeconds)
          throws ExecException, IOException, InterruptedException {
    try {
      sandbox.createFileSystem();
      OutErr outErr = policy.getFileOutErr();
      SpawnResult result = run(sandbox, outErr, timeoutSeconds);
  
      policy.lockOutputFiles();
  
      try {
        // We copy the outputs even when the command failed.
        sandbox.copyOutputs(execRoot);
      } catch (IOException e) {
        throw new IOException("Could not move output artifacts from sandboxed execution", e);
      }

      if (result.status() != Status.SUCCESS || result.exitCode() != 0) {
        String message;
        if (sandboxOptions.sandboxDebug) {
          message =
              CommandFailureUtils.describeCommandFailure(
                  true, sandbox.getArguments(), sandbox.getEnvironment(), null);
        } else {
          message =
              CommandFailureUtils.describeCommandFailure(
                  false, originalSpawn.getArguments(), originalSpawn.getEnvironment(), null)
                  + SANDBOX_DEBUG_SUGGESTION;
        }
        throw new SpawnExecException(
            message, result, /*forciblyRunRemotely=*/false, /*catastrophe=*/false);
      }
      return result;
    } finally {
      if (!sandboxOptions.sandboxDebug) {
        sandbox.delete();
      }
    }
  }

  private final SpawnResult run(SandboxedSpawn sandbox, OutErr outErr, int timeoutSeconds)
      throws IOException, InterruptedException {
    Command cmd = new Command(
        sandbox.getArguments().toArray(new String[0]),
        sandbox.getEnvironment(),
        sandbox.getSandboxExecRoot().getPathFile());

    long startTime = System.currentTimeMillis();
    CommandResult result;
    try {
      result = cmd.execute(
          /* stdin */ new byte[] {},
          Command.NO_OBSERVER,
          outErr.getOutputStream(),
          outErr.getErrorStream(),
          /* killSubprocessOnInterrupt */ true);
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      }
    } catch (AbnormalTerminationException e) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      }
      result = e.getResult();
    } catch (CommandException e) {
      // At the time this comment was written, this must be a ExecFailedException encapsulating an
      // IOException from the underlying Subprocess.Factory.
      String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
      outErr.getErrorStream().write(("Action failed to execute: " + msg + "\n").getBytes(UTF_8));
      outErr.getErrorStream().flush();
      return new SpawnResult.Builder()
          .setStatus(Status.EXECUTION_FAILED)
          .setExitCode(LOCAL_EXEC_ERROR)
          .build();
    }

    long wallTime = System.currentTimeMillis() - startTime;
    boolean wasTimeout = wasTimeout(timeoutSeconds, wallTime);
    Status status = wasTimeout ? Status.TIMEOUT : Status.SUCCESS;
    int exitCode = status == Status.TIMEOUT
        ? POSIX_TIMEOUT_EXIT_CODE
        : result.getTerminationStatus().getRawExitCode();
    return new SpawnResult.Builder()
        .setStatus(status)
        .setExitCode(exitCode)
        .setWallTimeMillis(wallTime)
        .build();
  }

  private boolean wasTimeout(int timeoutSeconds, long wallTimeMillis) {
    return timeoutSeconds > 0 && wallTimeMillis / 1000.0 > timeoutSeconds;
  }

  /**
   * Returns a temporary directory that should be used as the sandbox directory for a single action.
   */
  protected Path getSandboxRoot() throws IOException {
    return sandboxBase.getRelative(
        java.nio.file.Files.createTempDirectory(
                java.nio.file.Paths.get(sandboxBase.getPathString()), "")
            .getFileName()
            .toString());
  }

  /**
   * Gets the list of directories that the spawn will assume to be writable.
   *
   * @throws IOException because we might resolve symlinks, which throws {@link IOException}.
   */
  protected ImmutableSet<Path> getWritableDirs(Path sandboxExecRoot, Map<String, String> env)
      throws IOException {
    // We have to make the TEST_TMPDIR directory writable if it is specified.
    ImmutableSet.Builder<Path> writablePaths = ImmutableSet.builder();
    writablePaths.add(sandboxExecRoot);
    String tmpDirString = env.get("TEST_TMPDIR");
    if (tmpDirString != null) {
      PathFragment testTmpDir = PathFragment.create(tmpDirString);
      if (testTmpDir.isAbsolute()) {
        writablePaths.add(sandboxExecRoot.getRelative(testTmpDir).resolveSymbolicLinks());
      } else {
        // We add this even though it is below sandboxExecRoot (and thus already writable as a
        // subpath) to take advantage of the side-effect that SymlinkedExecRoot also creates this
        // needed directory if it doesn't exist yet.
        writablePaths.add(sandboxExecRoot.getRelative(testTmpDir));
      }
    }

    FileSystem fileSystem = sandboxExecRoot.getFileSystem();
    for (String writablePath : sandboxOptions.sandboxWritablePath) {
      Path path = fileSystem.getPath(writablePath);
      writablePaths.add(path);
      writablePaths.add(path.resolveSymbolicLinks());
    }

    return writablePaths.build();
  }

  protected ImmutableSet<Path> getInaccessiblePaths() {
    return inaccessiblePaths;
  }

  protected abstract String getName();
}
