// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.rules.python;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.lib.rules.python.PythonTestUtils.ensureDefaultIsPY2;

import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import org.junit.Before;
import org.junit.Test;

/** Tests that are common to {@code py_binary}, {@code py_test}, and {@code py_library}. */
public abstract class PyBaseConfiguredTargetTestBase extends BuildViewTestCase {

  private final String ruleName;

  protected PyBaseConfiguredTargetTestBase(String ruleName) {
    this.ruleName = ruleName;
  }

  @Before
  public final void setUpPython() throws Exception {
    analysisMock.pySupport().setup(mockToolsConfig);
  }

  /** Retrieves the Python version of a configured target. */
  protected PythonVersion getPythonVersion(ConfiguredTarget ct) {
    return getConfiguration(ct).getOptions().get(PythonOptions.class).getPythonVersion();
  }

  @Test
  public void badSrcsVersionValue() throws Exception {
    checkError("pkg", "foo",
        // error:
        "invalid value in 'srcs_version' attribute: "
            + "has to be one of 'PY2', 'PY3', 'PY2AND3', 'PY2ONLY' "
            + "or 'PY3ONLY' instead of 'doesnotexist'",
        // build file:
        ruleName + "(",
        "    name = 'foo',",
        "    srcs_version = 'doesnotexist',",
        "    srcs = ['foo.py'])");
  }

  @Test
  public void goodSrcsVersionValue() throws Exception {
    scratch.file(
        "pkg/BUILD",
        ruleName + "(",
        "    name = 'foo',",
        "    srcs_version = 'PY2',",
        "    srcs = ['foo.py'])");
    getConfiguredTarget("//pkg:foo");
    assertNoEvents();
  }

  @Test
  public void srcsVersionClashesWithForcePythonFlagUnderOldSemantics() throws Exception {
    // Under the old version semantics, we fail on any Python target the moment a conflict between
    // srcs_version and the configuration is detected. Under the new semantics, py_binary and
    // py_test care if there's a conflict but py_library does not. This test case checks the old
    // semantics; the new semantics are checked in PyLibraryConfiguredTargetTest and
    // PyExecutableConfiguredTargetTestBase. Note that under the new semantics py_binary and
    // py_library ignore the version flag, so those tests use the attribute to set the version
    // instead.
    useConfiguration("--experimental_allow_python_version_transitions=false", "--force_python=PY3");
    checkError("pkg", "foo",
        // error:
        "'//pkg:foo' can only be used with Python 2",
        // build file:
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = [':foo.py'],",
        "    srcs_version = 'PY2ONLY')");
  }

  @Test
  public void versionIs2IfUnspecified() throws Exception {
    ensureDefaultIsPY2();
    scratch.file(
        "pkg/BUILD", //
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'])");
    assertThat(getPythonVersion(getConfiguredTarget("//pkg:foo"))).isEqualTo(PythonVersion.PY2);
  }

  @Test
  public void versionIs3IfForcedByFlagUnderOldSemantics() throws Exception {
    // Under the old version semantics, --force_python takes precedence over the rule's own
    // default_python_version attribute, so this test case applies equally well to py_library,
    // py_binary, and py_test. Under the new semantics the rule attribute takes precedence, so this
    // would only make sense for py_library; see PyLibraryConfiguredTargetTest for the analogous
    // test.
    ensureDefaultIsPY2();
    useConfiguration("--experimental_allow_python_version_transitions=false", "--force_python=PY3");
    scratch.file(
        "pkg/BUILD", //
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'])");
    assertThat(getPythonVersion(getConfiguredTarget("//pkg:foo"))).isEqualTo(PythonVersion.PY3);
  }

  @Test
  public void packageNameCannotHaveHyphen() throws Exception {
    checkError("pkg-hyphenated", "foo",
        // error:
        "paths to Python packages may not contain '-'",
        // build file:
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'])");
  }

  @Test
  public void srcsPackageNameCannotHaveHyphen() throws Exception {
    scratch.file(
        "pkg-hyphenated/BUILD", //
        "exports_files(['bar.py'])");
    checkError("otherpkg", "foo",
        // error:
        "paths to Python packages may not contain '-'",
        // build file:
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py', '//pkg-hyphenated:bar.py'])");
  }

  @Test
  public void producesBothModernAndLegacyProviders_WithoutIncompatibleFlag() throws Exception {
    useConfiguration("--experimental_disallow_legacy_py_provider=false");
    scratch.file(
        "pkg/BUILD", //
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'])");
    ConfiguredTarget target = getConfiguredTarget("//pkg:foo");
    assertThat(target.get(PyInfo.PROVIDER)).isNotNull();
    assertThat(target.get(PyStructUtils.PROVIDER_NAME)).isNotNull();
  }

  @Test
  public void producesOnlyModernProvider_WithIncompatibleFlag() throws Exception {
    useConfiguration("--experimental_disallow_legacy_py_provider=true");
    scratch.file(
        "pkg/BUILD", //
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'])");
    ConfiguredTarget target = getConfiguredTarget("//pkg:foo");
    assertThat(target.get(PyInfo.PROVIDER)).isNotNull();
    assertThat(target.get(PyStructUtils.PROVIDER_NAME)).isNull();
  }

  @Test
  public void consumesLegacyProvider_WithoutIncompatibleFlag() throws Exception {
    useConfiguration("--experimental_disallow_legacy_py_provider=false");
    scratch.file(
        "pkg/rules.bzl",
        "def _myrule_impl(ctx):",
        "    return struct(py=struct(transitive_sources=depset([])))",
        "myrule = rule(",
        "    implementation = _myrule_impl,",
        ")");
    scratch.file(
        "pkg/BUILD",
        "load(':rules.bzl', 'myrule')",
        "myrule(",
        "    name = 'dep',",
        ")",
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'],",
        "    deps = [':dep'],",
        ")");
    ConfiguredTarget target = getConfiguredTarget("//pkg:foo");
    assertThat(target).isNotNull();
    assertNoEvents();
  }

  @Test
  public void rejectsLegacyProvider_WithIncompatibleFlag() throws Exception {
    useConfiguration("--experimental_disallow_legacy_py_provider=true");
    scratch.file(
        "pkg/rules.bzl",
        "def _myrule_impl(ctx):",
        "    return struct(py=struct(transitive_sources=depset([])))",
        "myrule = rule(",
        "    implementation = _myrule_impl,",
        ")");
    checkError(
        "pkg",
        "foo",
        // error:
        "In dep '//pkg:dep': The legacy 'py' provider is disallowed.",
        // build file:
        "load(':rules.bzl', 'myrule')",
        "myrule(",
        "    name = 'dep',",
        ")",
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'],",
        "    deps = [':dep'],",
        ")");
  }

  @Test
  public void consumesModernProvider() throws Exception {
    scratch.file(
        "pkg/rules.bzl",
        "def _myrule_impl(ctx):",
        "    return [PyInfo(transitive_sources=depset([]))]",
        "myrule = rule(",
        "    implementation = _myrule_impl,",
        ")");
    scratch.file(
        "pkg/BUILD",
        "load(':rules.bzl', 'myrule')",
        "myrule(",
        "    name = 'dep',",
        ")",
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'],",
        "    deps = [':dep'],",
        ")");
    ConfiguredTarget target = getConfiguredTarget("//pkg:foo");
    assertThat(target).isNotNull();
    assertNoEvents();
  }

  @Test
  public void requiresProvider() throws Exception {
    scratch.file(
        "pkg/rules.bzl",
        "def _myrule_impl(ctx):",
        "    return []",
        "myrule = rule(",
        "    implementation = _myrule_impl,",
        ")");
    checkError(
        "pkg",
        "foo",
        // error:
        "'//pkg:dep' does not have mandatory providers",
        // build file:
        "load(':rules.bzl', 'myrule')",
        "myrule(",
        "    name = 'dep',",
        ")",
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'],",
        "    deps = [':dep'],",
        ")");
  }
}
