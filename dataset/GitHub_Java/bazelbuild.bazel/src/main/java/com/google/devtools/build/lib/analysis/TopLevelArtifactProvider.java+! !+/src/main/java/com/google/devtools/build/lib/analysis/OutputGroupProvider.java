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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;

/**
 * {@code ConfiguredTarget}s implementing this interface can provide artifacts that <b>can</b> be
 * built when the target is mentioned on the command line (as opposed to being always built, like
 * {@link com.google.devtools.build.lib.analysis.FileProvider})
 *
 * <p>The artifacts are grouped into "output groups". Which output groups are built is controlled
 * by the {@code --output_groups} undocumented command line option, which in turn is added to the
 * command line at the discretion of the build command being run.
 *
 * <p>Output groups starting with an underscore are "not important". This means that artifacts built
 * because such an output group is mentioned in a {@code --output_groups} command line option are
 * not mentioned on the output.
 */
@Immutable
public final class TopLevelArtifactProvider implements TransitiveInfoProvider {

  /**
   * Prefix for output groups that are not reported to the user on the terminal output of Blaze when
   * they are built.
   */
  public static final String HIDDEN_OUTPUT_GROUP_PREFIX = "_";

  /**
   * Baseline coverage artifacts.
   */
  public static final String BASELINE_COVERAGE = HIDDEN_OUTPUT_GROUP_PREFIX + "baseline_coverage";

  /**
   * Building these artifacts only results in the compilation (and not e.g. linking) of the
   * associated target. Mostly useful for C++, less so for e.g. Java.
   */
  public static final String FILES_TO_COMPILE = "files_to_compile";

  /**
   * These artifacts are the direct requirements for compilation, but building these does not
   * actually compile the target. Mostly useful when IDEs want Blaze to emit generated code so that
   * they can do the compilation in their own way.
   */
  public static final String COMPILATION_PREREQUISITES = "compilation_prerequisites";

  /**
   * These files are built when a target is mentioned on the command line, but are not reported to
   * the user. This is mostly runfiles, which is necessary because we don't want a target to
   * successfully build if a file in its runfiles is broken.
   */
  public static final String HIDDEN_TOP_LEVEL = HIDDEN_OUTPUT_GROUP_PREFIX + "hidden_top_level";

  /**
   * Temporary files created during building a rule, for example, .i, .d and .s files for C++
   * compilation.
   *
   * <p>This output group is somewhat special: it is always built, but it only contains files when
   * the {@code --save_temps} command line option present. I'm not sure if this is to save RAM by
   * not creating the associated actions and artifacts if we don't need them or just historical
   * baggage.
   */
  public static final String TEMP_FILES = "temp_files";

  /**
   * The default group of files built by a target that is also used when e.g. it is mentioned in
   * a data dependency.
   */
  public static final String DEFAULT = "default";

  private final ImmutableMap<String, NestedSet<Artifact>> outputGroups;

  TopLevelArtifactProvider(ImmutableMap<String, NestedSet<Artifact>> outputGroups) {
    this.outputGroups = outputGroups;
  }

  /** Return the artifacts in a particular output group.
   *
   * @return the artifacts in the output group with the given name. The return value is never null.
   *     If the specified output group is not present, the empty set is returned.
   */
  public NestedSet<Artifact> getOutputGroup(String outputGroupName) {
    return outputGroups.containsKey(outputGroupName)
        ? outputGroups.get(outputGroupName)
        : NestedSetBuilder.<Artifact>emptySet(Order.STABLE_ORDER);
  }
}
