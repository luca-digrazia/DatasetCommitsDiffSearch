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
package com.google.devtools.build.lib.skylark;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.devtools.build.lib.analysis.BuildView.AnalysisResult;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.ViewCreationFailedException;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;

import javax.annotation.Nullable;

/**
 * Tests for Skylark aspects
 */
public class SkylarkAspectsTest extends BuildViewTestCase {
  public void testAspect() throws Exception {
    scratch.file(
        "test/aspect.bzl",
        "def _impl(target, ctx):",
        "   print('This aspect does nothing')",
        "   return struct()",
        "MyAspect = aspect(implementation=_impl)");
    scratch.file("test/BUILD", "java_library(name = 'xxx',)");

    AnalysisResult analysisResult =
        update(
            ImmutableList.of("//test:xxx"),
            ImmutableList.<String>of("test/aspect.bzl%MyAspect"),
            false,
            LOADING_PHASE_THREADS,
            true,
            new EventBus());
    assertThat(
            transform(
                analysisResult.getTargetsToBuild(),
                new Function<ConfiguredTarget, String>() {
                  @Nullable
                  @Override
                  public String apply(ConfiguredTarget configuredTarget) {
                    return configuredTarget.getLabel().toString();
                  }
                }))
        .containsExactly("//test:xxx");
  }

  public void testAspectFailingExecution() throws Exception {
    scratch.file(
        "test/aspect.bzl",
        "def _impl(target, ctx):",
        "   return 1/0",
        "",
        "MyAspect = aspect(implementation=_impl)");
    scratch.file("test/BUILD", "java_library(name = 'xxx',)");

    reporter.removeHandler(failFastHandler);
    try {
      update(
          ImmutableList.of("//test:xxx"),
          ImmutableList.of("test/aspect.bzl%MyAspect"),
          false,
          LOADING_PHASE_THREADS,
          true,
          new EventBus());
    } catch (ViewCreationFailedException e) {
      // expect to fail.
    }
    assertContainsEvent(
        "ERROR /workspace/test/BUILD:1:1: in java_library rule //test:xxx: \n"
            + "Traceback (most recent call last):\n"
            + "\tFile \"/workspace/test/BUILD\", line 1\n"
            + "\t\tMyAspect(...)\n"
            + "\tFile \"/workspace/test/aspect.bzl\", line 2, in _impl\n"
            + "\t\t1 / 0\n"
            + "integer division by zero");
  }

  public void testAspectFailingReturnsNotAStruct() throws Exception {
    scratch.file(
        "test/aspect.bzl",
        "def _impl(target, ctx):",
        "   return 0",
        "",
        "MyAspect = aspect(implementation=_impl)");
    scratch.file("test/BUILD", "java_library(name = 'xxx',)");

    reporter.removeHandler(failFastHandler);
    try {
      update(
          ImmutableList.of("//test:xxx"),
          ImmutableList.of("test/aspect.bzl%MyAspect"),
          false,
          LOADING_PHASE_THREADS,
          true,
          new EventBus());
    } catch (ViewCreationFailedException e) {
      // expect to fail.
    }
    assertContainsEvent("Aspect implementation doesn't return a struct");
  }

  public void testAspectFailingReturnsUnsafeObject() throws Exception {
    scratch.file(
        "test/aspect.bzl",
        "def foo():",
        "   return 0",
        "def _impl(target, ctx):",
        "   return struct(x = foo)",
        "",
        "MyAspect = aspect(implementation=_impl)");
    scratch.file("test/BUILD", "java_library(name = 'xxx',)");

    reporter.removeHandler(failFastHandler);
    try {
      update(
          ImmutableList.of("//test:xxx"),
          ImmutableList.of("test/aspect.bzl%MyAspect"),
          false,
          LOADING_PHASE_THREADS,
          true,
          new EventBus());
    } catch (ViewCreationFailedException e) {
      // expect to fail.
    }
    assertContainsEvent(
        "ERROR /workspace/test/BUILD:1:1: in java_library rule //test:xxx: \n"
        + "\n"
        + "\n"
        + "/workspace/test/aspect.bzl:4:11: Value of provider 'x' is of an illegal type: function");
  }

  public void testAspectFailingOrphanArtifacts() throws Exception {
    scratch.file(
        "test/aspect.bzl",
        "def _impl(target, ctx):",
        "  ctx.new_file('missing_in_action.txt')",
        "  return struct()",
        "",
        "MyAspect = aspect(implementation=_impl)");
    scratch.file("test/BUILD", "java_library(name = 'xxx',)");

    reporter.removeHandler(failFastHandler);
    try {
      update(
          ImmutableList.of("//test:xxx"),
          ImmutableList.of("test/aspect.bzl%MyAspect"),
          false,
          LOADING_PHASE_THREADS,
          true,
          new EventBus());
    } catch (ViewCreationFailedException e) {
      // expect to fail.
    }
    assertContainsEvent(
        "ERROR /workspace/test/BUILD:1:1: in java_library rule //test:xxx: \n"
            + "\n"
            + "\n"
            + "The following files have no generating action:\n"
            + "test/missing_in_action.txt\n");
  }
}
