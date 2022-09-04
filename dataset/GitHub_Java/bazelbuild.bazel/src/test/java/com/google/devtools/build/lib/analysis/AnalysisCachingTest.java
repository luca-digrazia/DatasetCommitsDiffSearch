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

package com.google.devtools.build.lib.analysis;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.analysis.config.BuildOptions;
import com.google.devtools.build.lib.analysis.config.ConfigurationFragmentFactory;
import com.google.devtools.build.lib.analysis.config.FragmentOptions;
import com.google.devtools.build.lib.analysis.config.transitions.NoTransition;
import com.google.devtools.build.lib.analysis.config.transitions.PatchTransition;
import com.google.devtools.build.lib.analysis.util.AnalysisCachingTestBase;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.rules.java.JavaInfo;
import com.google.devtools.build.lib.rules.java.JavaSourceJarsProvider;
import com.google.devtools.build.lib.skyframe.AspectValue;
import com.google.devtools.build.lib.skyframe.ConfiguredTargetKey;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.testutil.Suite;
import com.google.devtools.build.lib.testutil.TestConstants.InternalTestExecutionMode;
import com.google.devtools.build.lib.testutil.TestRuleClassProvider;
import com.google.devtools.build.lib.testutil.TestSpec;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionDefinition;
import com.google.devtools.common.options.OptionDocumentationCategory;
import com.google.devtools.common.options.OptionEffectTag;
import com.google.devtools.common.options.OptionsParser;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Analysis caching tests.
 */
@TestSpec(size = Suite.SMALL_TESTS)
@RunWith(JUnit4.class)
public class AnalysisCachingTest extends AnalysisCachingTestBase {

  @Test
  public void testSimpleCleanAnalysis() throws Exception {
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'])");
    update("//java/a:A");
    ConfiguredTarget javaTest = getConfiguredTarget("//java/a:A");
    assertThat(javaTest).isNotNull();
    assertThat(JavaInfo.getProvider(JavaSourceJarsProvider.class, javaTest)).isNotNull();
  }

  @Test
  public void testTickTock() throws Exception {
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'])",
        "java_test(name = 'B',",
        "          srcs = ['B.java'])");
    update("//java/a:A");
    update("//java/a:B");
    update("//java/a:A");
  }

  @Test
  public void testFullyCached() throws Exception {
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'])");
    update("//java/a:A");
    ConfiguredTarget old = getConfiguredTarget("//java/a:A");
    update("//java/a:A");
    ConfiguredTarget current = getConfiguredTarget("//java/a:A");
    assertThat(current).isSameAs(old);
  }

  @Test
  public void testSubsetCached() throws Exception {
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'])",
        "java_test(name = 'B',",
        "          srcs = ['B.java'])");
    update("//java/a:A", "//java/a:B");
    ConfiguredTarget old = getConfiguredTarget("//java/a:A");
    update("//java/a:A");
    ConfiguredTarget current = getConfiguredTarget("//java/a:A");
    assertThat(current).isSameAs(old);
  }

  @Test
  public void testDependencyChanged() throws Exception {
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'],",
        "          deps = ['//java/b'])");
    scratch.file("java/b/BUILD",
        "java_library(name = 'b',",
        "             srcs = ['B.java'])");
    update("//java/a:A");
    ConfiguredTarget old = getConfiguredTarget("//java/a:A");
    scratch.overwriteFile("java/b/BUILD",
        "java_library(name = 'b',",
        "             srcs = ['C.java'])");
    update("//java/a:A");
    ConfiguredTarget current = getConfiguredTarget("//java/a:A");
    assertThat(current).isNotSameAs(old);
  }

  @Test
  public void testTopLevelChanged() throws Exception {
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'],",
        "          deps = ['//java/b'])");
    scratch.file("java/b/BUILD",
        "java_library(name = 'b',",
        "             srcs = ['B.java'])");
    update("//java/a:A");
    ConfiguredTarget old = getConfiguredTarget("//java/a:A");
    scratch.overwriteFile("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'])");
    update("//java/a:A");
    ConfiguredTarget current = getConfiguredTarget("//java/a:A");
    assertThat(current).isNotSameAs(old);
  }

  // Regression test for:
  // "action conflict detection is incorrect if conflict is in non-top-level configured targets".
  @Test
  public void testActionConflictInDependencyImpliesTopLevelTargetFailure() throws Exception {
    if (getInternalTestExecutionMode() != InternalTestExecutionMode.NORMAL) {
      // TODO(b/67529176): conflicts not detected.
      return;
    }
    useConfiguration("--cpu=k8");
    scratch.file(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['foo.cc'])",
        "cc_binary(name='_objs/x/foo.pic.o', srcs=['bar.cc'])",
        "cc_binary(name='foo', deps=['x'], data=['_objs/x/foo.pic.o'])");
    reporter.removeHandler(failFastHandler); // expect errors
    update(defaultFlags().with(Flag.KEEP_GOING), "//conflict:foo");
    assertContainsEvent("file 'conflict/_objs/x/foo.pic.o' " + CONFLICT_MSG);
    assertThat(getAnalysisResult().getTargetsToBuild()).isEmpty();
  }

  /**
   * Generating the same output from two targets is ok if we build them on successive builds
   * and invalidate the first target before we build the second target. This is a strictly weaker
   * test than if we didn't invalidate the first target, but since Skyframe can't pass then, this
   * test could be useful for it. Actually, since Skyframe makes multiple update calls, it manages
   * to unregister actions even when it shouldn't, and so this test can incorrectly pass. However,
   * {@code SkyframeExecutorTest#testNoActionConflictWithInvalidatedTarget} tests it more
   * rigorously.
   */
  @Test
  public void testNoActionConflictWithInvalidatedTarget() throws Exception {
    useConfiguration("--cpu=k8");
    scratch.file(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['foo.cc'])",
        "cc_binary(name='_objs/x/foo.o', srcs=['bar.cc'])");
    update("//conflict:x");
    ConfiguredTarget conflict = getConfiguredTarget("//conflict:x");
    Action oldAction = getGeneratingAction(getBinArtifact("_objs/x/foo.pic.o", conflict));
    assertThat(oldAction.getOwner().getLabel().toString()).isEqualTo("//conflict:x");
    scratch.overwriteFile(
        "conflict/BUILD",
        "cc_library(name='newx', srcs=['foo.cc'])", // Rename target.
        "cc_binary(name='_objs/x/foo.pic.o', srcs=['bar.cc'])");
    update(defaultFlags(), "//conflict:_objs/x/foo.pic.o");
    ConfiguredTarget objsConflict = getConfiguredTarget("//conflict:_objs/x/foo.pic.o");
    Action newAction = getGeneratingAction(getBinArtifact("_objs/x/foo.pic.o", objsConflict));
    assertThat(newAction.getOwner().getLabel().toString())
        .isEqualTo("//conflict:_objs/x/foo.pic.o");
  }

  /**
   * Generating the same output from multiple actions is causing an error.
   */
  @Test
  public void testActionConflictCausesError() throws Exception {
    if (getInternalTestExecutionMode() != InternalTestExecutionMode.NORMAL) {
      // TODO(b/67529176): conflicts not detected.
      return;
    }
    useConfiguration("--cpu=k8");
    scratch.file(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['foo.cc'])",
        "cc_binary(name='_objs/x/foo.pic.o', srcs=['bar.cc'])");
    reporter.removeHandler(failFastHandler); // expect errors
    update(defaultFlags().with(Flag.KEEP_GOING), "//conflict:x", "//conflict:_objs/x/foo.pic.o");
    assertContainsEvent("file 'conflict/_objs/x/foo.pic.o' " + CONFLICT_MSG);
  }

  @Test
  public void testNoActionConflictErrorAfterClearedAnalysis() throws Exception {
    if (getInternalTestExecutionMode() != InternalTestExecutionMode.NORMAL) {
      // TODO(b/67529176): conflicts not detected.
      return;
    }
    useConfiguration("--cpu=k8");
    scratch.file(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['foo.cc'])",
        "cc_binary(name='_objs/x/foo.pic.o', srcs=['bar.cc'])");
    reporter.removeHandler(failFastHandler); // expect errors
    update(defaultFlags().with(Flag.KEEP_GOING), "//conflict:x", "//conflict:_objs/x/foo.pic.o");
    // We want to force a "dropConfiguredTargetsNow" operation, which won't inform the
    // invalidation receiver about the dropped configured targets.
    skyframeExecutor.clearAnalysisCache(
        ImmutableList.<ConfiguredTarget>of(), ImmutableSet.<AspectValue>of());
    assertContainsEvent("file 'conflict/_objs/x/foo.pic.o' " + CONFLICT_MSG);
    eventCollector.clear();
    scratch.overwriteFile(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['baz.cc'])",
        "cc_binary(name='_objs/x/foo.pic.o', srcs=['bar.cc'])");
    update(defaultFlags().with(Flag.KEEP_GOING), "//conflict:x", "//conflict:_objs/x/foo.pic.o");
    assertNoEvents();
  }

  /**
   * For two conflicting actions whose primary inputs are different, no list diff detail should be
   * part of the output.
   */
  @Test
  public void testConflictingArtifactsErrorWithNoListDetail() throws Exception {
    if (getInternalTestExecutionMode() != InternalTestExecutionMode.NORMAL) {
      // TODO(b/67529176): conflicts not detected.
      return;
    }
    useConfiguration("--cpu=k8");
    scratch.file(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['foo.cc'])",
        "cc_binary(name='_objs/x/foo.pic.o', srcs=['bar.cc'])");
    reporter.removeHandler(failFastHandler); // expect errors
    update(defaultFlags().with(Flag.KEEP_GOING), "//conflict:x", "//conflict:_objs/x/foo.pic.o");

    assertContainsEvent("file 'conflict/_objs/x/foo.pic.o' " + CONFLICT_MSG);
    assertDoesNotContainEvent("MandatoryInputs");
    assertDoesNotContainEvent("Outputs");
  }

  /**
   * For two conflicted actions whose primary inputs are the same, list diff (max 5) should be part
   * of the output.
   */
  @Test
  public void testConflictingArtifactsWithListDetail() throws Exception {
    if (getInternalTestExecutionMode() != InternalTestExecutionMode.NORMAL) {
      // TODO(b/67529176): conflicts not detected.
      return;
    }
    useConfiguration("--cpu=k8");
    scratch.file(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['foo1.cc', 'foo2.cc', 'foo3.cc', 'foo4.cc', 'foo5.cc'"
            + ", 'foo6.cc'])",
        "genrule(name = 'foo', outs=['_objs/x/foo1.pic.o'], srcs=['foo1.cc', 'foo2.cc', "
            + "'foo3.cc', 'foo4.cc', 'foo5.cc', 'foo6.cc'], cmd='', output_to_bindir=1)");
    reporter.removeHandler(failFastHandler); // expect errors
    update(defaultFlags().with(Flag.KEEP_GOING), "//conflict:x", "//conflict:foo");

    Event event = assertContainsEvent("file 'conflict/_objs/x/foo1.pic.o' " + CONFLICT_MSG);
    assertContainsEvent("MandatoryInputs");
    assertContainsEvent("Outputs");

    // Validate that maximum of 5 artifacts in MandatoryInputs are part of output.
    Pattern pattern = Pattern.compile("\tconflict\\/foo[1-6].cc");
    Matcher matcher = pattern.matcher(event.getMessage());
    int matchCount = 0;
    while (matcher.find()) {
      matchCount++;
    }

    assertThat(matchCount).isEqualTo(5);
  }

  /**
   * The current action conflict detection code will only mark one of the targets as having an
   * error, and with multi-threaded analysis it is not deterministic which one that will be.
   */
  @Test
  public void testActionConflictMarksTargetInvalid() throws Exception {
    if (getInternalTestExecutionMode() != InternalTestExecutionMode.NORMAL) {
      // TODO(b/67529176): conflicts not detected.
      return;
    }
    useConfiguration("--cpu=k8");
    scratch.file(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['foo.cc'])",
        "cc_binary(name='_objs/x/foo.o', srcs=['bar.cc'])");
    reporter.removeHandler(failFastHandler); // expect errors
    int successfulAnalyses =
        update(defaultFlags().with(Flag.KEEP_GOING), "//conflict:x", "//conflict:_objs/x/foo.pic.o")
            .getTargetsToBuild()
            .size();
    assertThat(successfulAnalyses).isEqualTo(1);
  }

  /**
   *  BUILD file involved in BUILD-file cycle is changed
   */
  @Test
  public void testBuildFileInCycleChanged() throws Exception {
    if (getInternalTestExecutionMode() != InternalTestExecutionMode.NORMAL) {
      // TODO(b/67412276): cycles not properly handled.
      return;
    }
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'],",
        "          deps = ['//java/b'])");
    scratch.file("java/b/BUILD",
        "java_library(name = 'b',",
        "          srcs = ['B.java'],",
        "          deps = ['//java/c'])");
    scratch.file("java/c/BUILD",
        "java_library(name = 'c',",
        "          srcs = ['C.java'],",
        "          deps = ['//java/b'])");
    // expect error
    reporter.removeHandler(failFastHandler);
    update(defaultFlags().with(Flag.KEEP_GOING), "//java/a:A");
    ConfiguredTarget old = getConfiguredTarget("//java/a:A");
    // drop dependency on from b to c
    scratch.overwriteFile("java/b/BUILD",
        "java_library(name = 'b',",
        "             srcs = ['B.java'])");
    eventCollector.clear();
    reporter.addHandler(failFastHandler);
    update("//java/a:A");
    ConfiguredTarget current = getConfiguredTarget("//java/a:A");
    assertThat(current).isNotSameAs(old);
  }

  private void assertNoTargetsVisited() {
    Set<?> analyzedTargets = getSkyframeEvaluatedTargetKeys();
    assertWithMessage(analyzedTargets.toString()).that(analyzedTargets.size()).isEqualTo(0);
  }

  @Test
  public void testSecondRunAllCacheHits() throws Exception {
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'])");
    update("//java/a:A");
    update("//java/a:A");
    assertNoTargetsVisited();
  }

  @Test
  public void testDependencyAllCacheHits() throws Exception {
    scratch.file("java/a/BUILD",
        "java_library(name = 'x', srcs = ['A.java'], deps = ['y'])",
        "java_library(name = 'y', srcs = ['B.java'])");
    update("//java/a:x");
    Set<?> oldAnalyzedTargets = getSkyframeEvaluatedTargetKeys();
    assertThat(oldAnalyzedTargets.size()).isAtLeast(2); // could be greater due to implicit deps
    assertThat(countObjectsPartiallyMatchingRegex(oldAnalyzedTargets, "//java/a:x")).isEqualTo(1);
    assertThat(countObjectsPartiallyMatchingRegex(oldAnalyzedTargets, "//java/a:y")).isEqualTo(1);
    update("//java/a:y");
    assertNoTargetsVisited();
  }

  @Test
  public void testSupersetNotAllCacheHits() throws Exception {
    scratch.file("java/a/BUILD",
        // It's important that all targets are of the same rule class, otherwise the second update
        // call might analyze more than one extra target because of potential implicit dependencies.
        "java_library(name = 'x', srcs = ['A.java'], deps = ['y'])",
        "java_library(name = 'y', srcs = ['B.java'], deps = ['z'])",
        "java_library(name = 'z', srcs = ['C.java'])");
    update("//java/a:y");
    Set<?> oldAnalyzedTargets = getSkyframeEvaluatedTargetKeys();
    assertThat(oldAnalyzedTargets.size()).isAtLeast(3); // could be greater due to implicit deps
    assertThat(countObjectsPartiallyMatchingRegex(oldAnalyzedTargets, "//java/a:x")).isEqualTo(0);
    assertThat(countObjectsPartiallyMatchingRegex(oldAnalyzedTargets, "//java/a:y")).isEqualTo(1);
    update("//java/a:x");
    Set<?> newAnalyzedTargets = getSkyframeEvaluatedTargetKeys();
    assertThat(newAnalyzedTargets).isNotEmpty(); // could be greater due to implicit deps
    assertThat(countObjectsPartiallyMatchingRegex(newAnalyzedTargets, "//java/a:x")).isEqualTo(1);
    assertThat(countObjectsPartiallyMatchingRegex(newAnalyzedTargets, "//java/a:y")).isEqualTo(0);
  }

  @Test
  public void testExtraActions() throws Exception {
    scratch.file("java/com/google/a/BUILD", "java_library(name='a', srcs=['A.java'])");
    scratch.file("java/com/google/b/BUILD", "java_library(name='b', srcs=['B.java'])");
    scratch.file("extra/BUILD",
        "extra_action(name = 'extra',",
        "             out_templates = ['$(OWNER_LABEL_DIGEST)_$(ACTION_ID).tst'],",
        "             cmd = '')",
        "action_listener(name = 'listener',",
        "                mnemonics = ['Javac'],",
        "                extra_actions = [':extra'])");

    useConfiguration("--experimental_action_listener=//extra:listener");
    update("//java/com/google/a:a");
    update("//java/com/google/b:b");
  }

  @Test
  public void testExtraActionsCaching() throws Exception {
    scratch.file("java/a/BUILD", "java_library(name='a', srcs=['A.java'])");
    scratch.file("extra/BUILD",
        "extra_action(name = 'extra',",
        "             out_templates = ['$(OWNER_LABEL_DIGEST)_$(ACTION_ID).tst'],",
        "             cmd = 'echo $(EXTRA_ACTION_FILE)')",
        "action_listener(name = 'listener',",
        "                mnemonics = ['Javac'],",
        "                extra_actions = [':extra'])");
    useConfiguration("--experimental_action_listener=//extra:listener");

    update("//java/a:a");
    getConfiguredTarget("//java/a:a");

    scratch.overwriteFile("extra/BUILD",
        "extra_action(name = 'extra',",
        "             out_templates = ['$(OWNER_LABEL_DIGEST)_$(ACTION_ID).tst'],",
        "             cmd = 'echo $(BUG)')", // <-- change here
        "action_listener(name = 'listener',",
        "                mnemonics = ['Javac'],",
        "                extra_actions = [':extra'])");
    reporter.removeHandler(failFastHandler);
    try {
      update("//java/a:a");
      fail();
    } catch (ViewCreationFailedException e) {
      assertThat(e).hasMessageThat().contains("Analysis of target '//java/a:a' failed");
      assertContainsEvent("$(BUG) not defined");
    }
  }

  @Test
  public void testConfigurationCachingWithWarningReplay() throws Exception {
    useConfiguration("--test_sharding_strategy=experimental_heuristic");
    update();
    assertContainsEvent("Heuristic sharding is intended as a one-off experimentation tool");
    eventCollector.clear();
    update();
    assertContainsEvent("Heuristic sharding is intended as a one-off experimentation tool");
  }

  @Test
  public void testSkyframeCacheInvalidationBuildFileChange() throws Exception {
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'])");
    String aTarget = "//java/a:A";
    update(aTarget);
    ConfiguredTarget firstCT = getConfiguredTarget(aTarget);

    scratch.overwriteFile("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['B.java'])");

    update(aTarget);
    ConfiguredTarget updatedCT = getConfiguredTarget(aTarget);
    assertThat(updatedCT).isNotSameAs(firstCT);

    update(aTarget);
    ConfiguredTarget updated2CT = getConfiguredTarget(aTarget);
    assertThat(updated2CT).isSameAs(updatedCT);
  }

  @Test
  public void testSkyframeDifferentPackagesInvalidation() throws Exception {
    scratch.file("java/a/BUILD",
        "java_test(name = 'A',",
        "          srcs = ['A.java'])");

    scratch.file("java/b/BUILD",
        "java_test(name = 'B',",
        "          srcs = ['B.java'])");

    String aTarget = "//java/a:A";
    update(aTarget);
    ConfiguredTarget oldAConfTarget = getConfiguredTarget(aTarget);
    String bTarget = "//java/b:B";
    update(bTarget);
    ConfiguredTarget oldBConfTarget = getConfiguredTarget(bTarget);

    scratch.overwriteFile("java/b/BUILD",
        "java_test(name = 'B',",
        "          srcs = ['C.java'])");

    update(aTarget);
    // Check that 'A' was not invalidated because 'B' was modified and invalidated.
    ConfiguredTarget newAConfTarget = getConfiguredTarget(aTarget);
    ConfiguredTarget newBConfTarget = getConfiguredTarget(bTarget);

    assertThat(newAConfTarget).isSameAs(oldAConfTarget);
    assertThat(newBConfTarget).isNotSameAs(oldBConfTarget);
  }

  private int countObjectsPartiallyMatchingRegex(Iterable<? extends Object> elements,
      String toStringMatching) {
    toStringMatching = ".*" + toStringMatching + ".*";
    int result = 0;
    for (Object o : elements) {
      if (o.toString().matches(toStringMatching)) {
        ++result;
      }
    }
    return result;
  }

  @Test
  public void testGetSkyframeEvaluatedTargetKeysOmitsCachedTargets() throws Exception {
    scratch.file("java/a/BUILD",
        "java_library(name = 'x', srcs = ['A.java'], deps = ['z', 'w'])",
        "java_library(name = 'y', srcs = ['B.java'], deps = ['z', 'w'])",
        "java_library(name = 'z', srcs = ['C.java'])",
        "java_library(name = 'w', srcs = ['D.java'])");

    update("//java/a:x");
    Set<?> oldAnalyzedTargets = getSkyframeEvaluatedTargetKeys();
    assertThat(oldAnalyzedTargets.size()).isAtLeast(2); // could be greater due to implicit deps
    assertThat(countObjectsPartiallyMatchingRegex(oldAnalyzedTargets, "//java/a:x")).isEqualTo(1);
    assertThat(countObjectsPartiallyMatchingRegex(oldAnalyzedTargets, "//java/a:y")).isEqualTo(0);
    assertThat(countObjectsPartiallyMatchingRegex(oldAnalyzedTargets, "//java/a:z")).isEqualTo(1);
    assertThat(countObjectsPartiallyMatchingRegex(oldAnalyzedTargets, "//java/a:w")).isEqualTo(1);

    // Unless the build is not fully cached, we get notified about newly evaluated targets, as well
    // as cached top-level targets. For the two tests above to work correctly, we need to ensure
    // that getSkyframeEvaluatedTargetKeys() doesn't return these.
    update("//java/a:x", "//java/a:y", "//java/a:z");
    Set<?> newAnalyzedTargets = getSkyframeEvaluatedTargetKeys();
    assertThat(newAnalyzedTargets).hasSize(2);
    assertThat(countObjectsPartiallyMatchingRegex(newAnalyzedTargets, "//java/a:B.java"))
        .isEqualTo(1);
    assertThat(countObjectsPartiallyMatchingRegex(newAnalyzedTargets, "//java/a:y")).isEqualTo(1);
  }

  /** Test options class for testing diff-based analysis cache resetting. */
  public static final class DiffResetOptions extends FragmentOptions {
    public static final PatchTransition CLEAR_IRRELEVANT =
        (options) -> {
          BuildOptions cloned = options.clone();
          cloned.get(DiffResetOptions.class).probablyIrrelevantOption = "(cleared)";
          return cloned;
        };

    @Option(
        name = "probably_irrelevant",
        defaultValue = "(unset)",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.UNKNOWN},
        help = "This option is irrelevant to non-uses_irrelevant targets and is trimmed from them.")
    public String probablyIrrelevantOption;

    @Option(
        name = "definitely_relevant",
        defaultValue = "(unset)",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.UNKNOWN},
        help = "This option is not trimmed and is used by all targets.")
    public String definitelyRelevantOption;

    @Option(
        name = "host_relevant",
        defaultValue = "(unset)",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.UNKNOWN},
        help = "This option is not trimmed and is used by all host targets.")
    public String hostRelevantOption;

    @Override
    public DiffResetOptions getHost() {
      DiffResetOptions host = ((DiffResetOptions) super.getHost());
      host.definitelyRelevantOption = hostRelevantOption;
      return host;
    }
  }

  @SkylarkModule(name = "test_diff_fragment", doc = "fragment for testing differy fragments")
  private static final class DiffResetFragment extends BuildConfiguration.Fragment {}

  private static final class DiffResetFactory implements ConfigurationFragmentFactory {
    @Override
    public BuildConfiguration.Fragment create(BuildOptions options) {
      return new DiffResetFragment();
    }

    @Override
    public Class<? extends BuildConfiguration.Fragment> creates() {
      return DiffResetFragment.class;
    }

    @Override
    public ImmutableSet<Class<? extends FragmentOptions>> requiredOptions() {
      return ImmutableSet.of(DiffResetOptions.class);
    }
  }

  private void setupDiffResetTesting() throws Exception {
    OptionDefinition probablyIrrelevantOption =
        OptionsParser.getOptionDefinitions(DiffResetOptions.class)
            .stream()
            .filter(definition -> definition.getOptionName().equals("probably_irrelevant"))
            .collect(MoreCollectors.onlyElement());
    ImmutableSet<OptionDefinition> optionsThatCanChange = ImmutableSet.of(probablyIrrelevantOption);
    ConfiguredRuleClassProvider.Builder builder = new ConfiguredRuleClassProvider.Builder();
    TestRuleClassProvider.addStandardRules(builder);
    builder.addConfig(DiffResetOptions.class, new DiffResetFactory());
    builder.overrideShouldInvalidateCacheForDiffForTesting(
        (diff, newOptions) -> {
          for (OptionDefinition changed : diff.getFirst().keySet()) {
            if (!optionsThatCanChange.contains(changed)) {
              return true;
            }
          }
          return false;
        });
    builder.overrideTrimmingTransitionFactoryForTesting(
        (rule) -> {
          if (rule.getRuleClassObject().getName().equals("uses_irrelevant")) {
            return NoTransition.INSTANCE;
          }
          return DiffResetOptions.CLEAR_IRRELEVANT;
        });
    useRuleClassProvider(builder.build());
    scratch.file(
        "test/lib.bzl",
        "def _empty_impl(ctx):",
        "  pass",
        "normal_lib = rule(",
        "    implementation = _empty_impl,",
        "    fragments = ['test_diff_fragment'],",
        "    attrs = {",
        "        'deps': attr.label_list(),",
        "        'host_deps': attr.label_list(cfg='host'),",
        "    },",
        ")",
        "uses_irrelevant = rule(",
        "    implementation = _empty_impl,",
        "    fragments = ['test_diff_fragment'],",
        "    attrs = {",
        "        'deps': attr.label_list(),",
        "        'host_deps': attr.label_list(cfg='host'),",
        "    },",
        ")");
    update();
  }

  private void assertNumberOfAnalyzedConfigurationsOfTargets(
      Map<String, Integer> targetsWithCounts) {
    ImmutableMultiset<Label> actualSet =
        getSkyframeEvaluatedTargetKeys()
            .stream()
            .filter(key -> key instanceof ConfiguredTargetKey)
            .map(key -> ((ConfiguredTargetKey) key).getLabel())
            .collect(toImmutableMultiset());
    ImmutableMap<Label, Integer> expected =
        targetsWithCounts
            .entrySet()
            .stream()
            .collect(
                toImmutableMap(
                    entry -> Label.parseAbsoluteUnchecked(entry.getKey()),
                    entry -> entry.getValue()));
    ImmutableMap<Label, Integer> actual =
        expected
            .keySet()
            .stream()
            .collect(toImmutableMap(label -> label, label -> actualSet.count(label)));
    assertThat(actual).containsExactlyEntriesIn(expected);
  }

  @Test
  public void cacheNotClearedWhenOptionsStaySame() throws Exception {
    setupDiffResetTesting();
    scratch.file(
        "test/BUILD",
        "load(':lib.bzl', 'normal_lib', 'uses_irrelevant')",
        "uses_irrelevant(name='top', deps=[':shared'])",
        "normal_lib(name='shared')");
    useConfiguration("--definitely_relevant=Testing");
    update("//test:top");
    update("//test:top");
    // these targets were cached and did not need to be reanalyzed
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 0)
            .put("//test:shared", 0)
            .build());
  }

  @Test
  public void cacheNotClearedWhenOptionsStaySameWithMultiCpu() throws Exception {
    setupDiffResetTesting();
    scratch.file(
        "test/BUILD",
        "load(':lib.bzl', 'normal_lib', 'uses_irrelevant')",
        "uses_irrelevant(name='top', deps=[':shared'])",
        "normal_lib(name='shared')");
    useConfiguration("--experimental_multi_cpu=k8,ppc", "--definitely_relevant=Testing");
    update("//test:top");
    update("//test:top");
    // these targets were cached and did not need to be reanalyzed
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 0)
            .put("//test:shared", 0)
            .build());
  }

  @Test
  public void cacheClearedWhenNonAllowedOptionsChange() throws Exception {
    setupDiffResetTesting();
    scratch.file(
        "test/BUILD",
        "load(':lib.bzl', 'normal_lib', 'uses_irrelevant')",
        "uses_irrelevant(name='top', deps=[':shared'])",
        "normal_lib(name='shared')");
    useConfiguration("--definitely_relevant=Test 1");
    update("//test:top");
    useConfiguration("--definitely_relevant=Test 2");
    update("//test:top");
    useConfiguration("--definitely_relevant=Test 1");
    update("//test:top");
    // these targets needed to be reanalyzed even though we built them in this configuration
    // just a moment ago
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 1)
            .put("//test:shared", 1)
            .build());
  }

  @Test
  public void cacheClearedWhenNonAllowedHostOptionsChange() throws Exception {
    setupDiffResetTesting();
    scratch.file(
        "test/BUILD",
        "load(':lib.bzl', 'normal_lib', 'uses_irrelevant')",
        "uses_irrelevant(name='top', host_deps=[':shared'])",
        "normal_lib(name='shared')");
    useConfiguration("--host_relevant=Test 1");
    update("//test:top");
    useConfiguration("--host_relevant=Test 2");
    update("//test:top");
    useConfiguration("--host_relevant=Test 1");
    update("//test:top");
    // these targets needed to be reanalyzed even though we built them in this configuration
    // just a moment ago
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 1)
            .put("//test:shared", 1)
            .build());
  }

  @Test
  public void cacheClearedWhenMultiCpuChanges() throws Exception {
    setupDiffResetTesting();
    scratch.file(
        "test/BUILD",
        "load(':lib.bzl', 'normal_lib', 'uses_irrelevant')",
        "uses_irrelevant(name='top', deps=[':shared'])",
        "normal_lib(name='shared')");
    useConfiguration("--experimental_multi_cpu=k8,ppc");
    update("//test:top");
    useConfiguration("--experimental_multi_cpu=k8,armeabi-v7a");
    update("//test:top");
    // we needed to reanalyze these in both k8 and armeabi-v7a even though we did the k8 analysis
    // just a moment ago as part of the previous build
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 2)
            .put("//test:shared", 2)
            .build());
  }

  @Test
  public void cacheClearedWhenMultiCpuGetsBigger() throws Exception {
    setupDiffResetTesting();
    scratch.file(
        "test/BUILD",
        "load(':lib.bzl', 'normal_lib', 'uses_irrelevant')",
        "uses_irrelevant(name='top', deps=[':shared'])",
        "normal_lib(name='shared')");
    useConfiguration("--experimental_multi_cpu=k8,ppc");
    update("//test:top");
    useConfiguration("--experimental_multi_cpu=k8,ppc,armeabi-v7a");
    update("//test:top");
    // we needed to reanalyze these in all of {k8,ppc,armeabi-v7a} even though we did the k8 and ppc
    // analysis just a moment ago as part of the previous build
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 3)
            .put("//test:shared", 3)
            .build());
  }

  @Test
  public void cacheClearedWhenMultiCpuGetsSmaller() throws Exception {
    setupDiffResetTesting();
    scratch.file(
        "test/BUILD",
        "load(':lib.bzl', 'normal_lib', 'uses_irrelevant')",
        "uses_irrelevant(name='top', deps=[':shared'])",
        "normal_lib(name='shared')");
    useConfiguration("--experimental_multi_cpu=k8,ppc,armeabi-v7a");
    update("//test:top");
    useConfiguration("--experimental_multi_cpu=k8,ppc");
    update("//test:top");
    // we needed to reanalyze these in both k8 and ppc even though we did the k8 and ppc
    // analysis just a moment ago as part of the previous build
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 2)
            .put("//test:shared", 2)
            .build());
  }

  @Test
  public void cacheNotClearedWhenAllowedOptionsChange() throws Exception {
    setupDiffResetTesting();
    scratch.file(
        "test/BUILD",
        "load(':lib.bzl', 'normal_lib', 'uses_irrelevant')",
        "uses_irrelevant(name='top', deps=[':shared'])",
        "normal_lib(name='shared')");
    useConfiguration("--definitely_relevant=Testing", "--probably_irrelevant=Test 1");
    update("//test:top");
    useConfiguration("--definitely_relevant=Testing", "--probably_irrelevant=Test 2");
    update("//test:top");
    // the shared library got to reuse the cached value, while the entry point had to be rebuilt in
    // the new configuration
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 1)
            .put("//test:shared", 0)
            .build());
    useConfiguration("--definitely_relevant=Testing", "--probably_irrelevant=Test 1");
    update("//test:top");
    // now we're back to the old configuration with no cache clears, so no work needed to be done
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 0)
            .put("//test:shared", 0)
            .build());
  }

  @Test
  public void cacheNotClearedWhenAllowedOptionsChangeWithMultiCpu() throws Exception {
    setupDiffResetTesting();
    scratch.file(
        "test/BUILD",
        "load(':lib.bzl', 'normal_lib', 'uses_irrelevant')",
        "uses_irrelevant(name='top', deps=[':shared'])",
        "normal_lib(name='shared')");
    useConfiguration(
        "--experimental_multi_cpu=k8,ppc",
        "--definitely_relevant=Testing",
        "--probably_irrelevant=Test 1");
    update("//test:top");
    useConfiguration(
        "--experimental_multi_cpu=k8,ppc",
        "--definitely_relevant=Testing",
        "--probably_irrelevant=Test 2");
    update("//test:top");
    // the shared library got to reuse the cached value, while the entry point had to be rebuilt in
    // the new configurations
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 2)
            .put("//test:shared", 0)
            .build());
    useConfiguration(
        "--experimental_multi_cpu=k8,ppc",
        "--definitely_relevant=Testing",
        "--probably_irrelevant=Test 1");
    update("//test:top");
    // now we're back to the old configurations with no cache clears, so no work needed to be done
    assertNumberOfAnalyzedConfigurationsOfTargets(
        ImmutableMap.<String, Integer>builder()
            .put("//test:top", 0)
            .put("//test:shared", 0)
            .build());
  }
}
