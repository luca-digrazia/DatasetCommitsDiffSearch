// Copyright 2016 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.shell;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * A builder class that starts a subprocess.
 */
public class SubprocessBuilder {
  /**
   * What to do with an output stream of the process.
   */
  public enum StreamAction {
    /** Redirect to a file */
    REDIRECT,

    /** Discard. */
    DISCARD,

    /** Stream back to the parent process using an output stream. */
    STREAM };

  private ImmutableList<String> argv;
  private ImmutableMap<String, String> env;
  private StreamAction stdoutAction;
  private File stdoutFile;
  private StreamAction stderrAction;
  private File stderrFile;
  private File workingDirectory;

  public SubprocessBuilder() {
    stdoutAction = StreamAction.STREAM;
    stderrAction = StreamAction.STREAM;
  }

  public ImmutableList<String> getArgv() {
    return argv;
  }

  /**
   * Sets the argv, including argv[0], that is, the binary to execute.
   */
  public SubprocessBuilder setArgv(Iterable<String> argv) {
    this.argv = ImmutableList.copyOf(argv);
    return this;
  }

  public ImmutableMap<String, String> getEnv() {
    return env;
  }

  /**
   * Sets the environment passed to the child process. If null, inherit the environment of the
   * server.
   */
  public SubprocessBuilder setEnv(Map<String, String> env) {
    this.env = env == null
        ? null : ImmutableMap.copyOf(env);
    return this;
  }

  public StreamAction getStdout() {
    return stdoutAction;
  }

  public File getStdoutFile() {
    return stdoutFile;
  }

  /**
   * Tells the object what to do with stdout: either stream as a {@code InputStream} or discard.
   * 
   * <p>It can also be redirected to a file using {@link #setStdout(File)}.
   */
  public SubprocessBuilder setStdout(StreamAction action) {
    if (action == StreamAction.REDIRECT) {
      throw new IllegalStateException();
    }
    this.stdoutAction = action;
    this.stdoutFile = null;
    return this;
  }
  
  /**
   * Sets the file stdout is appended to. If null, the stdout will be available as an input stream
   * on the resulting object representing the process.
   */
  public SubprocessBuilder setStdout(File file) {
    this.stdoutAction = StreamAction.REDIRECT;
    this.stdoutFile = file;
    return this;
  }

  public StreamAction getStderr() {
    return stderrAction;
  }

  public File getStderrFile() {
    return stderrFile;
  }

  /**
   * Tells the object what to do with stderr: either stream as a {@code InputStream} or discard.
   * 
   * <p>It can also be redirected to a file using {@link #setStderr(File)}.
   */
  public SubprocessBuilder setStderr(StreamAction action) {
    if (action == StreamAction.REDIRECT) {
      throw new IllegalStateException();
    }
    this.stderrAction = action;
    this.stderrFile = null;
    return this;
  }
  
  /**
   * Sets the file stderr is appended to. If null, the stderr will be available as an input stream
   * on the resulting object representing the process.
   */
  public SubprocessBuilder setStderr(File file) {
    this.stderrAction = StreamAction.REDIRECT;
    this.stderrFile = file;
    return this;
  }

  public File getWorkingDirectory() {
    return workingDirectory;
  }

  /**
   * Sets the current working directory. If null, it will be that of this process.
   */
  public SubprocessBuilder setWorkingDirectory(File workingDirectory) {
    this.workingDirectory = workingDirectory;
    return this;
  }

  public Subprocess start() throws IOException {
    return JavaSubprocessFactory.INSTANCE.create(this);
  }
}
