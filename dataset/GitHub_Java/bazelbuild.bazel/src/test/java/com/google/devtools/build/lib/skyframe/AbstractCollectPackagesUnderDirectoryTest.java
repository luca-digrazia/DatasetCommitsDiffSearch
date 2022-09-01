// Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import com.google.devtools.build.lib.actions.ActionKeyContext;
import com.google.devtools.build.lib.analysis.BlazeDirectories;
import com.google.devtools.build.lib.analysis.ConfiguredRuleClassProvider;
import com.google.devtools.build.lib.analysis.ServerDirectories;
import com.google.devtools.build.lib.clock.BlazeClock;
import com.google.devtools.build.lib.cmdline.RepositoryName;
import com.google.devtools.build.lib.events.EventCollector;
import com.google.devtools.build.lib.events.Reporter;
import com.google.devtools.build.lib.packages.BuildFileName;
import com.google.devtools.build.lib.packages.StarlarkSemanticsOptions;
import com.google.devtools.build.lib.pkgcache.PackageCacheOptions;
import com.google.devtools.build.lib.pkgcache.PathPackageLocator;
import com.google.devtools.build.lib.rules.repository.RepositoryDelegatorFunction;
import com.google.devtools.build.lib.testutil.MoreAsserts;
import com.google.devtools.build.lib.testutil.Scratch;
import com.google.devtools.build.lib.testutil.TestConstants;
import com.google.devtools.build.lib.util.AbruptExitException;
import com.google.devtools.build.lib.util.io.TimestampGranularityMonitor;
import com.google.devtools.build.lib.vfs.FileSystem;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.vfs.Root;
import com.google.devtools.build.lib.vfs.RootedPath;
import com.google.devtools.build.lib.vfs.inmemoryfs.InMemoryFileSystem;
import com.google.devtools.build.skyframe.BuildDriver;
import com.google.devtools.build.skyframe.EvaluationContext;
import com.google.devtools.build.skyframe.EvaluationResult;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionName;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.common.options.Options;
import com.google.devtools.common.options.OptionsProvider;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Abstract base class for testing an implementation of a {@link SkyFunction} for {@link
 * SkyFunctions#COLLECT_PACKAGES_UNDER_DIRECTORY}.
 */
public abstract class AbstractCollectPackagesUnderDirectoryTest {
  protected FileSystem fileSystem;
  protected Root root;
  protected Path workingDir;
  private Scratch scratch;
  protected BlazeDirectories directories;
  private EventCollector eventCollector;
  private Reporter reporter;
  protected ConfiguredRuleClassProvider ruleClassProvider;
  private BuildDriver buildDriver;

  @Before
  public void setUp() throws IOException {
    fileSystem = new InMemoryFileSystem();
    workingDir = fileSystem.getPath(getWorkspacePathString());
    workingDir.createDirectoryAndParents();
    root = Root.fromPath(workingDir);
    scratch = new Scratch(workingDir);
    directories =
        new BlazeDirectories(
            new ServerDirectories(
                fileSystem.getPath("/install"),
                fileSystem.getPath("/output"),
                fileSystem.getPath("/user_root")),
            workingDir,
            /*defaultSystemJavabase=*/ null,
            /*productName=*/ "DummyProductNameForUnitTests");
    eventCollector = new EventCollector();
    reporter = new Reporter(new EventBus());
    reporter.addHandler(eventCollector);
  }

  protected abstract String getWorkspacePathString();

  protected abstract List<BuildFileName> getBuildFileNamesByPriority();

  protected abstract ImmutableMap<SkyFunctionName, SkyFunction> getExtraSkyFunctions();

  protected abstract SkyframeExecutorFactory makeSkyframeExecutorFactory();

  @Test
  public void noPackageErrors() throws Exception {
    initBuildDriver();

    scratch.file("BUILD");
    scratch.dir("a1/b1/c1");
    scratch.file("a1/b1/c1/BUILD");
    scratch.dir("a1/b1/c2");
    scratch.dir("a1/b2/c1");
    scratch.dir("a1/b2/c2");
    scratch.dir("a2/b1/c1");
    scratch.file("a2/b1/c1/BUILD");
    scratch.dir("a2/b1/c2");
    scratch.dir("a2/b2/c1");
    scratch.dir("a2/b2/c2");
    scratch.file("a2/b2/c2/BUILD");

    {
      CollectPackagesUnderDirectoryValue collectPackagesUnderDirectoryValue =
          getCollectPackagesUnderDirectoryValue("");
      assertThat(collectPackagesUnderDirectoryValue.isDirectoryPackage()).isTrue();
      assertThat(
              collectPackagesUnderDirectoryValue
                  .getSubdirectoryTransitivelyContainsPackagesOrErrors())
          .containsExactly(
              rootedPath("tools"), Boolean.TRUE,
              rootedPath("a1"), Boolean.TRUE,
              rootedPath("a2"), Boolean.TRUE);
    }

    {
      CollectPackagesUnderDirectoryValue collectPackagesUnderDirectoryValue =
          getCollectPackagesUnderDirectoryValue("a1");
      assertThat(collectPackagesUnderDirectoryValue.isDirectoryPackage()).isFalse();
      assertThat(
              collectPackagesUnderDirectoryValue
                  .getSubdirectoryTransitivelyContainsPackagesOrErrors())
          .containsExactly(rootedPath("a1/b1"), Boolean.TRUE, rootedPath("a1/b2"), Boolean.FALSE);
    }

    {
      CollectPackagesUnderDirectoryValue collectPackagesUnderDirectoryValue =
          getCollectPackagesUnderDirectoryValue("a2/b1");
      assertThat(collectPackagesUnderDirectoryValue.isDirectoryPackage()).isFalse();
      assertThat(
              collectPackagesUnderDirectoryValue
                  .getSubdirectoryTransitivelyContainsPackagesOrErrors())
          .containsExactly(
              rootedPath("a2/b1/c1"), Boolean.TRUE, rootedPath("a2/b1/c2"), Boolean.FALSE);
    }
  }

  @Test
  public void packageErrors() throws Exception {
    initBuildDriver();

    scratch.dir("a1/b1");
    scratch.file("a1/b1/BUILD", "xxx");
    scratch.dir("a1/b2");
    scratch.dir("a2/b1");
    scratch.dir("a2/b2");
    scratch.file("a2/b2/BUILD", "yyy");

    CollectPackagesUnderDirectoryValue collectPackagesUnderDirectoryValue =
        getCollectPackagesUnderDirectoryValue("");
    assertThat(collectPackagesUnderDirectoryValue.isDirectoryPackage()).isFalse();
    assertThat(
            collectPackagesUnderDirectoryValue
                .getSubdirectoryTransitivelyContainsPackagesOrErrors())
        .containsExactly(
            rootedPath("tools"), Boolean.TRUE,
            rootedPath("a1"), Boolean.TRUE,
            rootedPath("a2"), Boolean.TRUE);
    MoreAsserts.assertContainsEvent(eventCollector, "Loading package: a1/b1");
    MoreAsserts.assertContainsEvent(eventCollector, "a1/b1/BUILD:1:1: name 'xxx' is not defined");
    MoreAsserts.assertContainsEvent(eventCollector, "Loading package: a2/b2");
    MoreAsserts.assertContainsEvent(eventCollector, "a2/b2/BUILD:1:1: name 'yyy' is not defined");
  }

  @Test
  public void symlinks() throws Exception {
    initBuildDriver();

    Path a1DirPath = scratch.dir("a1");
    scratch.dir("a1/b1/c1");
    Path a1CircularPath = scratch.resolve("a1/circular");
    FileSystemUtils.ensureSymbolicLink(a1CircularPath, a1CircularPath);
    scratch.file("a1/b1/c1/BUILD");
    FileSystemUtils.ensureSymbolicLink(scratch.resolve("a2"), a1DirPath);
    scratch.dir("a3");
    scratch.file(
        "a3/DONT_FOLLOW_SYMLINKS_WHEN_TRAVERSING_THIS_DIRECTORY_VIA_A_RECURSIVE_TARGET_PATTERN");
    FileSystemUtils.ensureSymbolicLink(scratch.resolve("a3/dirlink"), a1DirPath);

    CollectPackagesUnderDirectoryValue collectPackagesUnderDirectoryValue =
        getCollectPackagesUnderDirectoryValue("");
    assertThat(collectPackagesUnderDirectoryValue.isDirectoryPackage()).isFalse();
    assertThat(
            collectPackagesUnderDirectoryValue
                .getSubdirectoryTransitivelyContainsPackagesOrErrors())
        .containsExactly(
            rootedPath("tools"), Boolean.TRUE,
            rootedPath("a1"), Boolean.TRUE,
            rootedPath("a2"), Boolean.TRUE,
            rootedPath("a3"), Boolean.FALSE);
    MoreAsserts.assertContainsEvent(eventCollector, "Loading package: a1/b1/c1");
    MoreAsserts.assertContainsEvent(eventCollector, "Loading package: a2/b1/c1");
    MoreAsserts.assertDoesNotContainEvent(eventCollector, "Loading package: a3/b1/c1");
    MoreAsserts.assertContainsEvent(
        eventCollector,
        "Failed to get information about path, for a1/circular, skipping: Symlink cycle");
    MoreAsserts.assertContainsEvent(
        eventCollector,
        "Failed to get information about path, for a2/circular, skipping: Symlink cycle");
  }

  @Test
  public void excludedPaths() throws Exception {
    initBuildDriver();

    scratch.dir("a1/b1/c1");
    scratch.file("a1/b1/c1/BUILD");
    scratch.dir("a1/b1/c2");
    scratch.file("a1/b1/c2/BUILD");
    scratch.dir("a1/b2/c1");
    scratch.file("a1/b2/c1/BUILD");
    scratch.dir("a1/b2/c2");
    scratch.file("a1/b2/c2/BUILD");
    scratch.dir("a2/b1/c1");
    scratch.file("a2/b1/c1/BUILD");
    scratch.dir("a2/b1/c2");
    scratch.file("a2/b1/c2/BUILD");
    scratch.dir("a2/b2/c1");
    scratch.file("a2/b2/c1/BUILD");
    scratch.dir("a2/b2/c2");
    scratch.file("a2/b2/c2/BUILD");

    CollectPackagesUnderDirectoryValue collectPackagesUnderDirectoryValue =
        getCollectPackagesUnderDirectoryValue(
            "",
            /*excludedPaths=*/ ImmutableSet.of(
                PathFragment.create("a1"),
                PathFragment.create("a2/b1"),
                PathFragment.create("a2/b2/c2")));
    assertThat(collectPackagesUnderDirectoryValue.isDirectoryPackage()).isFalse();
    // There is not supposed to be a map entry for excluded subdirectories.
    assertThat(
            collectPackagesUnderDirectoryValue
                .getSubdirectoryTransitivelyContainsPackagesOrErrors())
        .containsExactly(
            rootedPath("tools"), Boolean.TRUE,
            rootedPath("a2"), Boolean.TRUE);
    MoreAsserts.assertDoesNotContainEvent(eventCollector, "Loading package: a1/b1/c1");
    MoreAsserts.assertDoesNotContainEvent(eventCollector, "Loading package: a1/b1/c2");
    MoreAsserts.assertDoesNotContainEvent(eventCollector, "Loading package: a1/b2/c1");
    MoreAsserts.assertDoesNotContainEvent(eventCollector, "Loading package: a1/b1/c2");
    MoreAsserts.assertDoesNotContainEvent(eventCollector, "Loading package: a2/b1/c1");
    MoreAsserts.assertDoesNotContainEvent(eventCollector, "Loading package: a2/b1/c2");
    MoreAsserts.assertContainsEvent(eventCollector, "Loading package: a2/b2/c1");
    MoreAsserts.assertDoesNotContainEvent(eventCollector, "Loading package: a2/b2/c2");
  }

  private void initBuildDriver() throws AbruptExitException, InterruptedException, IOException {
    PathPackageLocator pathPackageLocator =
        PathPackageLocator.createWithoutExistenceCheck(
            directories.getOutputBase(), ImmutableList.of(root), getBuildFileNamesByPriority());
    PackageCacheOptions packageCacheOptions = Options.getDefaults(PackageCacheOptions.class);
    packageCacheOptions.packagePath = ImmutableList.of(getWorkspacePathString());
    scratch.file("tools/BUILD");
    scratch.file("tools/empty_prelude.bzl");
    ruleClassProvider =
        new ConfiguredRuleClassProvider.Builder()
            .setRunfilesPrefix("workspace")
            .setPrelude("//tools:empty_prelude.bzl")
            .build();
    SkyframeExecutor skyframeExecutor =
        makeSkyframeExecutorFactory()
            .create(
                TestConstants.PACKAGE_FACTORY_BUILDER_FACTORY_FOR_TESTING
                    .builder(directories)
                    .build(ruleClassProvider, fileSystem),
                fileSystem,
                directories,
                new ActionKeyContext(),
                /*workspaceStatusActionFactory=*/ null,
                ruleClassProvider.getBuildInfoFactories(),
                /*diffAwarenessFactories=*/ ImmutableList.of(),
                getExtraSkyFunctions(),
                /*customDirtinessCheckers=*/ ImmutableList.of(),
                /*managedDirectoriesKnowledge=*/ null);
    skyframeExecutor.injectExtraPrecomputedValues(
        ImmutableList.of(
            PrecomputedValue.injected(
                RepositoryDelegatorFunction.RESOLVED_FILE_INSTEAD_OF_WORKSPACE, Optional.absent()),
            PrecomputedValue.injected(
                RepositoryDelegatorFunction.REPOSITORY_OVERRIDES, ImmutableMap.of()),
            PrecomputedValue.injected(
                RepositoryDelegatorFunction.DEPENDENCY_FOR_UNCONDITIONAL_FETCHING,
                RepositoryDelegatorFunction.DONT_FETCH_UNCONDITIONALLY)));
    skyframeExecutor.sync(
        reporter,
        packageCacheOptions,
        pathPackageLocator,
        Options.getDefaults(StarlarkSemanticsOptions.class),
        UUID.randomUUID(),
        /*clientEnv=*/ ImmutableMap.of(),
        new TimestampGranularityMonitor(BlazeClock.instance()),
        OptionsProvider.EMPTY);
    buildDriver = skyframeExecutor.getDriver();
  }

  private CollectPackagesUnderDirectoryValue getCollectPackagesUnderDirectoryValue(String directory)
      throws InterruptedException {
    return getCollectPackagesUnderDirectoryValue(directory, /*excludedPaths=*/ ImmutableSet.of());
  }

  private CollectPackagesUnderDirectoryValue getCollectPackagesUnderDirectoryValue(
      String directory, ImmutableSet<PathFragment> excludedPaths) throws InterruptedException {
    SkyKey key =
        CollectPackagesUnderDirectoryValue.key(
            RepositoryName.MAIN, rootedPath(directory), excludedPaths);
    return evaluate(key).get(key);
  }

  private RootedPath rootedPath(String relativePath) {
    return RootedPath.toRootedPath(root, PathFragment.create(relativePath));
  }

  private EvaluationResult<CollectPackagesUnderDirectoryValue> evaluate(SkyKey key)
      throws InterruptedException {
    EvaluationContext evaluationContext =
        EvaluationContext.newBuilder()
            .setKeepGoing(true)
            .setNumThreads(1)
            .setEventHander(new Reporter(new EventBus(), reporter))
            .build();
    return buildDriver.evaluate(ImmutableList.of(key), evaluationContext);
  }
}
