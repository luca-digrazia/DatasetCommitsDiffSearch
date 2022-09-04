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

package com.google.devtools.build.lib.rules.platform;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.IterableSubject;
import com.google.devtools.build.lib.analysis.platform.ConstraintSettingInfo;
import com.google.devtools.build.lib.analysis.platform.ConstraintValueInfo;
import com.google.devtools.build.lib.analysis.platform.DeclaredToolchainInfo;
import com.google.devtools.build.lib.analysis.platform.PlatformInfo;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.skyframe.RegisteredToolchainsValue;
import com.google.devtools.build.lib.skyframe.util.SkyframeExecutorTestUtils;
import com.google.devtools.build.lib.skylark.util.SkylarkTestCase;
import com.google.devtools.build.skyframe.EvaluationResult;
import com.google.devtools.build.skyframe.SkyKey;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;

/** Utility methods for setting up platform and toolchain related tests. */
public abstract class ToolchainTestCase extends SkylarkTestCase {

  public PlatformInfo linuxPlatform;
  public PlatformInfo macPlatform;

  public ConstraintSettingInfo setting;
  public ConstraintValueInfo linuxConstraint;
  public ConstraintValueInfo macConstraint;

  public Label testToolchainType;

  protected static IterableSubject assertToolchainLabels(
      RegisteredToolchainsValue registeredToolchainsValue) {
    assertThat(registeredToolchainsValue).isNotNull();
    ImmutableList<DeclaredToolchainInfo> declaredToolchains =
        registeredToolchainsValue.registeredToolchains();
    List<Label> labels = collectToolchainLabels(declaredToolchains);
    return assertThat(labels);
  }

  protected static List<Label> collectToolchainLabels(List<DeclaredToolchainInfo> toolchains) {
    return toolchains
        .stream()
        .map((toolchain -> toolchain.toolchainLabel()))
        .collect(Collectors.toList());
  }

  private static String formatConstraints(Collection<String> constraints) {
    return constraints.stream().map(c -> String.format("'%s'", c)).collect(joining(", "));
  }

  @Before
  public void createConstraints() throws Exception {
    scratch.file(
        "constraints/BUILD",
        "constraint_setting(name = 'os')",
        "constraint_value(name = 'linux',",
        "    constraint_setting = ':os')",
        "constraint_value(name = 'mac',",
        "    constraint_setting = ':os')");

    scratch.file(
        "platforms/BUILD",
        "platform(name = 'linux',",
        "    constraint_values = ['//constraints:linux'])",
        "platform(name = 'mac',",
        "    constraint_values = ['//constraints:mac'])");

    setting = ConstraintSettingInfo.create(makeLabel("//constraints:os"));
    linuxConstraint = ConstraintValueInfo.create(setting, makeLabel("//constraints:linux"));
    macConstraint = ConstraintValueInfo.create(setting, makeLabel("//constraints:mac"));

    linuxPlatform =
        PlatformInfo.builder()
            .setLabel(makeLabel("//platforms:linux"))
            .addConstraint(linuxConstraint)
            .build();
    macPlatform =
        PlatformInfo.builder()
            .setLabel(makeLabel("//platforms:mac"))
            .addConstraint(macConstraint)
            .build();
  }

  public void addToolchain(
      String packageName,
      String toolchainName,
      Collection<String> execConstraints,
      Collection<String> targetConstraints,
      String data)
      throws Exception {
    scratch.appendFile(
        packageName + "/BUILD",
        "load('//toolchain:toolchain_def.bzl', 'test_toolchain')",
        "toolchain(",
        "    name = '" + toolchainName + "',",
        "    toolchain_type = '//toolchain:test_toolchain',",
        "    exec_compatible_with = [" + formatConstraints(execConstraints) + "],",
        "    target_compatible_with = [" + formatConstraints(targetConstraints) + "],",
        "    toolchain = ':" + toolchainName + "_impl')",
        "test_toolchain(",
        "  name='" + toolchainName + "_impl',",
        "  data = '" + data + "')");
  }

  @Before
  public void createToolchains() throws Exception {
    rewriteWorkspace("register_toolchains('//toolchain:toolchain_1', '//toolchain:toolchain_2')");

    scratch.file(
        "toolchain/toolchain_def.bzl",
        "def _impl(ctx):",
        "  toolchain = platform_common.ToolchainInfo(",
        "      data = ctx.attr.data)",
        "  return [toolchain]",
        "test_toolchain = rule(",
        "    implementation = _impl,",
        "    attrs = {",
        "       'data': attr.string()})");

    scratch.file("toolchain/BUILD", "toolchain_type(name = 'test_toolchain')");
    addToolchain(
        "toolchain",
        "toolchain_1",
        ImmutableList.of("//constraints:linux"),
        ImmutableList.of("//constraints:mac"),
        "foo");
    addToolchain(
        "toolchain",
        "toolchain_2",
        ImmutableList.of("//constraints:mac"),
        ImmutableList.of("//constraints:linux"),
        "bar");

    testToolchainType = makeLabel("//toolchain:test_toolchain");
  }

  protected EvaluationResult<RegisteredToolchainsValue> requestToolchainsFromSkyframe(
      SkyKey toolchainsKey) throws InterruptedException {
    try {
      getSkyframeExecutor().getSkyframeBuildView().enableAnalysis(true);
      return SkyframeExecutorTestUtils.evaluate(
          getSkyframeExecutor(), toolchainsKey, /*keepGoing=*/ false, reporter);
    } finally {
      getSkyframeExecutor().getSkyframeBuildView().enableAnalysis(false);
    }
  }
}
