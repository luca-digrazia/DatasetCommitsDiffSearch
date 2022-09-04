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
package com.google.devtools.build.lib.exec.local;

import com.google.devtools.build.lib.vfs.Path;
import java.io.IOException;
import java.util.Map;

/**
 * Allows just-in-time rewriting of the environment used for local actions. Do not use! This class
 * probably should not exist, but is currently necessary for our local MacOS support.
 */
public interface LocalEnvProvider {

  public static final LocalEnvProvider UNMODIFIED =
      new LocalEnvProvider() {
        @Override
        public Map<String, String> rewriteLocalEnv(
            Map<String, String> env, Path execRoot, String fallbackTmpDir) {
          return env;
        }
      };

  /**
   * Rewrites a {@code Spawn}'s the environment if necessary.
   *
   * @param env the Spawn's environment to rewrite
   * @param execRoot the path where the Spawn is executed
   * @param fallbackTmpDir an absolute path to a temp directory that the Spawn could use. The
   *     particular implementation of {@link LocalEnvProvider} may choose to use some other path,
   *     typically the "TMPDIR" environment variable in the Bazel client's environment, but if
   *     that's unavailable, the implementation may decide to use this {@code fallbackTmpDir}.
   */
  Map<String, String> rewriteLocalEnv(
      Map<String, String> env, Path execRoot, String fallbackTmpDir)
      throws IOException;
}
