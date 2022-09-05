// Copyright 2015 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.skyframe;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCaseForJunit4;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.PackageIdentifier;
import com.google.devtools.build.lib.pkgcache.FilteringPolicies;
import com.google.devtools.build.lib.pkgcache.FilteringPolicy;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.vfs.RootedPath;
import com.google.devtools.build.skyframe.BuildDriver;
import com.google.devtools.build.skyframe.EvaluationResult;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.WalkableGraph;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

/**
 * Tests for {@link PrepareDepsOfTargetsUnderDirectoryFunction}. Insert excuses here.
 */
@RunWith(JUnit4.class)
public class PrepareDepsOfTargetsUnderDirectoryFunctionTest extends BuildViewTestCaseForJunit4 {

  private SkyframeExecutor skyframeExecutor;

  @Before
  public final void setSkyframeExecutor() throws Exception {
    skyframeExecutor = getSkyframeExecutor();
  }

  private SkyKey createPrepDepsKey(Path root, PathFragment rootRelativePath) {
    return createPrepDepsKey(root, rootRelativePath, ImmutableSet.<PathFragment>of());
  }

  private SkyKey createPrepDepsKey(Path root, PathFragment rootRelativePath,
      ImmutableSet<PathFragment> excludedPaths) {
    RootedPath rootedPath = RootedPath.toRootedPath(root, rootRelativePath);
    return PrepareDepsOfTargetsUnderDirectoryValue.key(
        PackageIdentifier.DEFAULT_REPOSITORY_NAME, rootedPath, excludedPaths);
  }

  private SkyKey createPrepDepsKey(Path root, PathFragment rootRelativePath,
      ImmutableSet<PathFragment> excludedPaths, FilteringPolicy filteringPolicy) {
    RootedPath rootedPath = RootedPath.toRootedPath(root, rootRelativePath);
    return PrepareDepsOfTargetsUnderDirectoryValue.key(
        PackageIdentifier.DEFAULT_REPOSITORY_NAME, rootedPath, excludedPaths, filteringPolicy);
  }

  private EvaluationResult<PrepareDepsOfTargetsUnderDirectoryValue> getEvaluationResult(SkyKey key)
      throws InterruptedException {
    BuildDriver driver = skyframeExecutor.getDriverForTesting();
    EvaluationResult<PrepareDepsOfTargetsUnderDirectoryValue> evaluationResult =
        driver.evaluate(ImmutableList.of(key), /*keepGoing=*/false,
            SequencedSkyframeExecutor.DEFAULT_THREAD_COUNT, reporter);
    Preconditions.checkState(!evaluationResult.hasError());
    return evaluationResult;
  }

  @Test
  public void testTransitiveLoading() throws Exception {
    // Given a package "a" with a genrule "a" that depends on a target in package "b",
    createPackages();

    // When package "a" is evaluated,
    SkyKey key = createPrepDepsKey(rootDirectory, new PathFragment("a"));
    EvaluationResult<PrepareDepsOfTargetsUnderDirectoryValue> evaluationResult =
        getEvaluationResult(key);
    WalkableGraph graph = Preconditions.checkNotNull(evaluationResult.getWalkableGraph());

    // Then the TransitiveTraversalValue for "a:a" is evaluated,
    SkyKey aaKey = TransitiveTraversalValue.key(Label.create("a", "a"));
    assertThat(graph.exists(aaKey)).isTrue();

    // And that TransitiveTraversalValue depends on "b:b.txt".
    Iterable<SkyKey> depsOfAa =
        Iterables.getOnlyElement(graph.getDirectDeps(ImmutableList.of(aaKey)).values());
    SkyKey bTxtKey = TransitiveTraversalValue.key(Label.create("b", "b.txt"));
    assertThat(depsOfAa).contains(bTxtKey);

    // And the TransitiveTraversalValue for "b:b.txt" is evaluated.
    assertThat(graph.exists(bTxtKey)).isTrue();
  }

  @Test
  public void testTargetFilterSensitivity() throws Exception {
    // Given a package "a" with a genrule "a" that depends on a target in package "b", and a test
    // rule "aTest",
    createPackages();

    // When package "a" is evaluated under a test-only filtering policy,
    SkyKey key = createPrepDepsKey(rootDirectory, new PathFragment("a"),
        ImmutableSet.<PathFragment>of(), FilteringPolicies.FILTER_TESTS);
    EvaluationResult<PrepareDepsOfTargetsUnderDirectoryValue> evaluationResult =
        getEvaluationResult(key);
    WalkableGraph graph = Preconditions.checkNotNull(evaluationResult.getWalkableGraph());

    // Then the TransitiveTraversalValue for "a:a" is not evaluated,
    SkyKey aaKey = TransitiveTraversalValue.key(Label.create("a", "a"));
    assertThat(graph.exists(aaKey)).isFalse();

    // But the TransitiveTraversalValue for "a:aTest" is.
    SkyKey aaTestKey = TransitiveTraversalValue.key(Label.create("a", "aTest"));
    assertThat(graph.exists(aaTestKey)).isTrue();
  }

  /**
   * Creates a package "a" with a genrule "a" that depends on a target in a created package "b",
   * and a test rule "aTest".
   */
  private void createPackages() throws IOException {
    scratch.file("a/BUILD",
        "genrule(name='a', cmd='', srcs=['//b:b.txt'], outs=['a.out'])",
        "sh_test(name='aTest', size='small', srcs=['aTest.sh'])");
    scratch.file("b/BUILD",
        "exports_files(['b.txt'])");
  }

  @Test
  public void testSubdirectoryExclusion() throws Exception {
    // Given a package "a" with two packages below it, "a/b" and "a/c",
    scratch.file("a/BUILD");
    scratch.file("a/b/BUILD");
    scratch.file("a/c/BUILD");

    // When the top package is evaluated via PrepareDepsOfTargetsUnderDirectoryValue with "a/b"
    // excluded,
    PathFragment excludedPathFragment = new PathFragment("a/b");
    SkyKey key = createPrepDepsKey(rootDirectory, new PathFragment("a"),
        ImmutableSet.of(excludedPathFragment));
    EvaluationResult<PrepareDepsOfTargetsUnderDirectoryValue> evaluationResult =
        getEvaluationResult(key);
    PrepareDepsOfTargetsUnderDirectoryValue value = evaluationResult.get(key);

    // Then the value reports that "a" is a package,
    assertThat(value.isDirectoryPackage()).isTrue();

    // And only the subdirectory corresponding to "a/c" is present in the result,
    RootedPath onlySubdir =
        Iterables.getOnlyElement(value.getSubdirectoryTransitivelyContainsPackages().keySet());
    assertThat(onlySubdir.getRelativePath()).isEqualTo(new PathFragment("a/c"));

    // And the "a/c" subdirectory reports a package under it.
    assertThat(value.getSubdirectoryTransitivelyContainsPackages().get(onlySubdir)).isTrue();

    // Also, the computation graph does not contain a cached value for "a/b".
    WalkableGraph graph = Preconditions.checkNotNull(evaluationResult.getWalkableGraph());
    assertFalse(graph.exists(createPrepDepsKey(rootDirectory, excludedPathFragment,
        ImmutableSet.<PathFragment>of())));

    // And the computation graph does contain a cached value for "a/c" with the empty set excluded,
    // because that key was evaluated.
    assertTrue(graph.exists(createPrepDepsKey(rootDirectory, new PathFragment("a/c"),
        ImmutableSet.<PathFragment>of())));
  }

  @Test
  public void testExcludedSubdirectoryGettingPassedDown() throws Exception {
    // Given a package "a", and a package below it in "a/b/c", and a non-BUILD file below it in
    // "a/b/d",
    scratch.file("a/BUILD");
    scratch.file("a/b/c/BUILD");
    scratch.file("a/b/d/helloworld");

    // When the top package is evaluated for recursive package values, and "a/b/c" is excluded,
    ImmutableSet<PathFragment> excludedPaths = ImmutableSet.of(new PathFragment("a/b/c"));
    SkyKey key = createPrepDepsKey(rootDirectory, new PathFragment("a"), excludedPaths);
    EvaluationResult<PrepareDepsOfTargetsUnderDirectoryValue> evaluationResult =
        getEvaluationResult(key);
    PrepareDepsOfTargetsUnderDirectoryValue value = evaluationResult.get(key);

    // Then the value reports that "a" is a package,
    assertThat(value.isDirectoryPackage()).isTrue();

    // And the subdirectory corresponding to "a/b" is present in the result,
    RootedPath onlySubdir =
        Iterables.getOnlyElement(value.getSubdirectoryTransitivelyContainsPackages().keySet());
    assertThat(onlySubdir.getRelativePath()).isEqualTo(new PathFragment("a/b"));

    // And the "a/b" subdirectory does not report a package under it (because it got excluded).
    assertThat(value.getSubdirectoryTransitivelyContainsPackages().get(onlySubdir)).isFalse();

    // Also, the computation graph contains a cached value for "a/b" with "a/b/c" excluded, because
    // "a/b/c" does live underneath "a/b".
    WalkableGraph graph = Preconditions.checkNotNull(evaluationResult.getWalkableGraph());
    SkyKey abKey = createPrepDepsKey(rootDirectory, new PathFragment("a/b"), excludedPaths);
    assertThat(graph.exists(abKey)).isTrue();
    PrepareDepsOfTargetsUnderDirectoryValue abValue =
        (PrepareDepsOfTargetsUnderDirectoryValue) Preconditions.checkNotNull(graph.getValue(abKey));

    // And that value says that "a/b" is not a package,
    assertThat(abValue.isDirectoryPackage()).isFalse();

    // And only the subdirectory "a/b/d" is present in that value,
    RootedPath abd =
        Iterables.getOnlyElement(abValue.getSubdirectoryTransitivelyContainsPackages().keySet());
    assertThat(abd.getRelativePath()).isEqualTo(new PathFragment("a/b/d"));

    // And no package is under "a/b/d".
    assertThat(abValue.getSubdirectoryTransitivelyContainsPackages().get(abd)).isFalse();
  }
}
