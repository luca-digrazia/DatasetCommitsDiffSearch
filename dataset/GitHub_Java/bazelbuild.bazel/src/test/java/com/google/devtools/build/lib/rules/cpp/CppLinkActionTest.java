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

package com.google.devtools.build.lib.rules.cpp;

import static com.google.devtools.build.lib.actions.util.ActionsTestUtil.NULL_ACTION_OWNER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ResourceSet;
import com.google.devtools.build.lib.actions.Root;
import com.google.devtools.build.lib.actions.util.ActionsTestUtil;
import com.google.devtools.build.lib.analysis.AnalysisEnvironment;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.util.ActionTester;
import com.google.devtools.build.lib.analysis.util.ActionTester.ActionCombinationFactory;
import com.google.devtools.build.lib.analysis.util.AnalysisTestUtil;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.rules.cpp.CppLinkAction.Builder;
import com.google.devtools.build.lib.rules.cpp.Link.LinkStaticness;
import com.google.devtools.build.lib.rules.cpp.Link.LinkTargetType;
import com.google.devtools.build.lib.rules.cpp.LinkerInputs.LibraryToLink;
import com.google.devtools.build.lib.vfs.PathFragment;

/**
 * Tests for {@link CppLinkAction}.
 */
public class CppLinkActionTest extends BuildViewTestCase {
  private RuleContext createDummyRuleContext() throws Exception {
    return view.getRuleContextForTesting(reporter, scratchConfiguredTarget(
        "dummyRuleContext", "dummyRuleContext",
        // CppLinkAction creation requires a CcToolchainProvider.
        "cc_library(name = 'dummyRuleContext')"),
        new StubAnalysisEnvironment() {
          @Override
          public void registerAction(Action... action) {
            // No-op.
          }

          @Override
          public Artifact getDerivedArtifact(PathFragment rootRelativePath, Root root) {
            return CppLinkActionTest.this.getDerivedArtifact(
                rootRelativePath, root, ActionsTestUtil.NULL_ARTIFACT_OWNER);
          }
        }, masterConfig);
  }

  /**
   * This mainly checks that non-static links don't have identical keys. Many options are only
   * allowed on non-static links, and we test several of them here.
   */
  public void testComputeKeyNonStatic() throws Exception {
    final RuleContext ruleContext = createDummyRuleContext();
    final PathFragment outputPath = new PathFragment("dummyRuleContext/output/path.xyz");
    final Artifact outputFile = getBinArtifactWithNoOwner(outputPath.getPathString());
    final Artifact oFile = getSourceArtifact("cc/a.o");
    final Artifact oFile2 = getSourceArtifact("cc/a2.o");
    final Artifact interfaceSoBuilder = getBinArtifactWithNoOwner("foo/build_interface_so");
    ActionTester.runTest(
        128,
        new ActionCombinationFactory() {

          @Override
          public Action generate(int i) {
            CppLinkAction.Builder builder =
                new CppLinkAction.Builder(ruleContext, outputFile) {
                  @Override
                  protected Artifact getInterfaceSoBuilder() {
                    return interfaceSoBuilder;
                  }
                };
            builder.addCompilationInputs(
                (i & 1) == 0 ? ImmutableList.of(oFile) : ImmutableList.of(oFile2));
            builder.setLinkType(
                (i & 2) == 0 ? LinkTargetType.DYNAMIC_LIBRARY : LinkTargetType.EXECUTABLE);
            builder.setLinkStaticness(LinkStaticness.DYNAMIC);
            builder.setNativeDeps((i & 4) == 0);
            builder.setUseTestOnlyFlags((i & 8) == 0);
            builder.setWholeArchive((i & 16) == 0);
            builder.setFake((i & 32) == 0);
            builder.setRuntimeSolibDir((i & 64) == 0 ? null : new PathFragment("so1"));
            return builder.build();
          }
        });
  }

  /**
   * This mainly checks that static library links don't have identical keys, and it also compares
   * them with simple dynamic library links.
   */
  public void testComputeKeyStatic() throws Exception {
    final RuleContext ruleContext = createDummyRuleContext();
    final PathFragment outputPath = new PathFragment("dummyRuleContext/output/path.xyz");
    final Artifact outputFile = getBinArtifactWithNoOwner(outputPath.getPathString());
    final Artifact oFile = getSourceArtifact("cc/a.o");
    final Artifact oFile2 = getSourceArtifact("cc/a2.o");
    final Artifact interfaceSoBuilder = getBinArtifactWithNoOwner("foo/build_interface_so");
    ActionTester.runTest(
        4,
        new ActionCombinationFactory() {

          @Override
          public Action generate(int i) {
            CppLinkAction.Builder builder =
                new CppLinkAction.Builder(ruleContext, outputFile) {
                  @Override
                  protected Artifact getInterfaceSoBuilder() {
                    return interfaceSoBuilder;
                  }
                };
            builder.addCompilationInputs(
                (i & 1) == 0 ? ImmutableList.of(oFile) : ImmutableList.of(oFile2));
            builder.setLinkType(
                (i & 2) == 0 ? LinkTargetType.STATIC_LIBRARY : LinkTargetType.DYNAMIC_LIBRARY);
            return builder.build();
          }
        });
  }

  public void testCommandLineSplitting() throws Exception {
    RuleContext ruleContext = createDummyRuleContext();
    Artifact output = getDerivedArtifact(
        new PathFragment("output/path.xyz"), getTargetConfiguration().getBinDirectory(),
        ActionsTestUtil.NULL_ARTIFACT_OWNER);
    final Artifact outputIfso = getDerivedArtifact(
        new PathFragment("output/path.ifso"), getTargetConfiguration().getBinDirectory(),
        ActionsTestUtil.NULL_ARTIFACT_OWNER);
    CppLinkAction.Builder builder = new CppLinkAction.Builder(ruleContext, output);
    builder.setLinkType(LinkTargetType.STATIC_LIBRARY);
    assertTrue(builder.canSplitCommandLine());

    builder.setLinkType(LinkTargetType.DYNAMIC_LIBRARY);
    assertTrue(builder.canSplitCommandLine());

    builder.setInterfaceOutput(outputIfso);
    assertFalse(builder.canSplitCommandLine());

    builder.setInterfaceOutput(null);
    builder.setLinkType(LinkTargetType.INTERFACE_DYNAMIC_LIBRARY);
    assertFalse(builder.canSplitCommandLine());
  }

  /**
   * Links a small target.
   * Checks that resource estimates are above the minimum and scale correctly.
   */
  public void testSmallLocalLinkResourceEstimate() throws Exception {
    assertLinkSizeAccuracy(3);
  }

  /**
   * Fake links a large target.
   * Checks that resource estimates are above the minimum and scale correctly.
   * The actual link action is irrelevant; we are just checking the estimate.
   */
  public void testLargeLocalLinkResourceEstimate() throws Exception {
    assertLinkSizeAccuracy(7000);
  }

  private void assertLinkSizeAccuracy(int inputs) throws Exception {
    ImmutableList.Builder<Artifact> objects = ImmutableList.builder();
    for (int i = 0; i < inputs; i++) {
      objects.add(getOutputArtifact("object" + i + ".o"));
    }

    CppLinkAction linkAction = createLinkBuilder(
        Link.LinkTargetType.EXECUTABLE, "binary2", objects.build(),
        ImmutableList.<LibraryToLink>of())
        .setFake(true)
        .build();

    // Ensure that minima are enforced.
    ResourceSet resources = ((CppLinkAction) linkAction).estimateResourceConsumptionLocal();
    assertTrue(resources.getMemoryMb() >= CppLinkAction.MIN_STATIC_LINK_RESOURCES.getMemoryMb());
    assertTrue(resources.getCpuUsage() >= CppLinkAction.MIN_STATIC_LINK_RESOURCES.getCpuUsage());
    assertTrue(resources.getIoUsage() >= CppLinkAction.MIN_STATIC_LINK_RESOURCES.getIoUsage());

    final int linkSize = Iterables.size(linkAction.getLinkCommandLine().getLinkerInputs());
    ResourceSet scaledSet = ResourceSet.createWithRamCpuIo(
        CppLinkAction.LINK_RESOURCES_PER_INPUT.getMemoryMb() * linkSize,
        CppLinkAction.LINK_RESOURCES_PER_INPUT.getCpuUsage() * linkSize,
        CppLinkAction.LINK_RESOURCES_PER_INPUT.getIoUsage() * linkSize
    );

    // Ensure that anything above the minimum is properly scaled.
    assertTrue(resources.getMemoryMb() == CppLinkAction.MIN_STATIC_LINK_RESOURCES.getMemoryMb()
      || resources.getMemoryMb() == scaledSet.getMemoryMb());
    assertTrue(resources.getCpuUsage() == CppLinkAction.MIN_STATIC_LINK_RESOURCES.getCpuUsage()
      || resources.getCpuUsage() == scaledSet.getCpuUsage());
    assertTrue(resources.getIoUsage() == CppLinkAction.MIN_STATIC_LINK_RESOURCES.getIoUsage()
      || resources.getIoUsage() == scaledSet.getIoUsage());
  }
  private Builder createLinkBuilder(Link.LinkTargetType type, String outputPath,
      Iterable<Artifact> nonLibraryInputs, ImmutableList<LibraryToLink> libraryInputs) {
    return createLinkBuilder(type, outputPath, nonLibraryInputs, libraryInputs,
        AnalysisTestUtil.STUB_ANALYSIS_ENVIRONMENT);
  }

  private Builder createLinkBuilder(Link.LinkTargetType type, String outputPath,
      Iterable<Artifact> nonLibraryInputs, ImmutableList<LibraryToLink> libraryInputs,
      AnalysisEnvironment analysisEnv) {
    Builder builder = CppLinkAction.Builder.createTestBuilder(
        NULL_ACTION_OWNER,
        analysisEnv,
        new Artifact(new PathFragment(outputPath), getTargetConfiguration().getBinDirectory()),
        getTargetConfiguration())
        .addNonLibraryInputs(nonLibraryInputs)
        .addLibraries(NestedSetBuilder.wrap(Order.LINK_ORDER, libraryInputs))
        .setLinkType(type)
        .setCrosstoolInputs(NestedSetBuilder.<Artifact>emptySet(Order.STABLE_ORDER))
        .setLinkStaticness(type.isStaticLibraryLink()
            ? LinkStaticness.FULLY_STATIC
            : LinkStaticness.MOSTLY_STATIC);
    return builder;
  }

  public Artifact getOutputArtifact(String relpath) {
    return new Artifact(
        getTargetConfiguration().getBinDirectory().getPath().getRelative(relpath),
        getTargetConfiguration().getBinDirectory(),
        getTargetConfiguration().getBinFragment().getRelative(relpath));
  }
}
