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
package com.google.devtools.build.lib.skylark;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.skylark.util.SkylarkTestCase;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.vfs.ModifiedFileSet;
import com.google.devtools.build.lib.vfs.PathFragment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for string representations of Skylark objects. */
@RunWith(JUnit4.class)
public class SkylarkStringRepresentationsTest extends SkylarkTestCase {

  // Different ways to format objects, these suffixes are used in the `prepare_params` function
  private static final ImmutableList<String> SUFFIXES =
      ImmutableList.of("_str", "_repr", "_format", "_str_perc", "_repr_perc");

  private Object skylarkLoadingEval(String code) throws Exception {
    return skylarkLoadingEval(code, "");
  }

  /**
   * Evaluates {@code code} in the loading phase in a .bzl file
   *
   * @param code The code to execute
   * @param definition Additional code to define necessary variables
   */
  private Object skylarkLoadingEval(String code, String definition) throws Exception {
    scratch.overwriteFile("eval/BUILD", "load(':eval.bzl', 'eval')", "eval(name='eval')");
    scratch.overwriteFile(
        "eval/eval.bzl",
        definition,
        String.format("x = %s", code), // Should be placed here to execute during the loading phase
        "def _impl(ctx):",
        "  return struct(result = x)",
        "eval = rule(implementation = _impl)");
    skyframeExecutor.invalidateFilesUnderPathForTesting(
        reporter,
        new ModifiedFileSet.Builder()
            .modify(PathFragment.create("eval/BUILD"))
            .modify(PathFragment.create("eval/eval.bzl"))
            .build(),
        rootDirectory);

    ConfiguredTarget target = getConfiguredTarget("//eval");
    return target.get("result");
  }

  /**
   * Asserts that all 5 different ways to convert an object to a string of {@code expression}
   * ({@code str}, {@code repr}, {@code '%s'}, {@code '%r'}, {@code '{}'.format} return the correct
   * {@code representation}. Not applicable for objects that have different {@code str} and {@code
   * repr} representations.
   *
   * @param definition optional definition required to evaluate the {@code expression}
   * @param expression the expression to evaluate a string representation of
   * @param representation desired string representation
   */
  private void assertStringRepresentation(
      String definition, String expression, String representation) throws Exception {
    assertThat(skylarkLoadingEval(String.format("str(%s)", expression), definition))
        .isEqualTo(representation);
    assertThat(skylarkLoadingEval(String.format("repr(%s)", expression), definition))
        .isEqualTo(representation);
    assertThat(skylarkLoadingEval(String.format("'%%s' %% (%s,)", expression), definition))
        .isEqualTo(representation);
    assertThat(skylarkLoadingEval(String.format("'%%r' %% (%s,)", expression), definition))
        .isEqualTo(representation);
    assertThat(skylarkLoadingEval(String.format("'{}'.format(%s)", expression), definition))
        .isEqualTo(representation);
  }

  private void assertStringRepresentation(String expression, String representation)
      throws Exception {
    assertStringRepresentation("", expression, representation);
  }

  /**
   * Creates a set of BUILD and .bzl files that gathers objects of many different types available in
   * Skylark and creates their string representations by calling `str` and `repr` on them. The
   * strings are available in the configured target for //test/skylark:check
   */
  private void generateFilesToTestStrings() throws Exception {
    // Generate string representations of different Skylark types. Objects are generated in
    // test/skylark/rules.bzl: the top-level objects dict contains objects
    // available during the loading phase, and _check_impl(ctx) returns objects that are available
    // during the analysis phase. prepare_params(objects) converts a list of objects to a list of
    // their string representations.

    scratch.file(
        "test/skylark/rules.bzl",
        "aspect_ctx_provider = provider()",
        "def prepare_params(objects):",
        "  params = {}",
        "  for k, v in objects.items():",
        "    params[k + '_str'] = str(v)",
        "    params[k + '_repr'] = repr(v)",
        "    params[k + '_format'] = '{}'.format(v)",
        "    params[k + '_str_perc'] = '%s' % (v,)",
        "    params[k + '_repr_perc'] = '%r' % (v,)",
        "  return params",
        "",
        "def _impl_aspect(target, ctx):",
        "  return [aspect_ctx_provider(ctx = ctx)]",
        "my_aspect = aspect(implementation = _impl_aspect)",
        "",
        "def _impl(ctx): pass",
        "dep = rule(implementation = _impl)",
        "",
        "def _genfile_impl(ctx):",
        "  ctx.file_action(output = ctx.outputs.my_output, content = 'foo')",
        "genfile = rule(",
        "  implementation = _genfile_impl,",
        "  outputs = {",
        "    'my_output': '%{name}.txt',",
        "  },",
        ")",
        "",
        "def _check_impl(ctx):",
        "  objects = {",
        "    'target': ctx.attr.deps[0],",
        "    'alias_target': ctx.attr.deps[1],",
        "    'aspect_target': ctx.attr.asp_deps[0],",
        "    'input_target': ctx.attr.srcs[0],",
        "    'output_target': ctx.attr.srcs[1],",
        "    'rule_ctx': ctx,",
        "    'aspect_ctx': ctx.attr.asp_deps[0][aspect_ctx_provider].ctx,",
        "  }",
        "  return struct(**prepare_params(objects))",
        "check = rule(",
        "  implementation = _check_impl,",
        "  attrs = {",
        "    'deps': attr.label_list(),",
        "    'asp_deps': attr.label_list(aspects = [my_aspect]),",
        "    'srcs': attr.label_list(allow_files = True),",
        "  },",
        ")");

    scratch.file(
        "test/skylark/BUILD",
        "load(':rules.bzl', 'check', 'dep', 'genfile')",
        "",
        "dep(name = 'foo')",
        "dep(name = 'bar')",
        "alias(name = 'foobar', actual = ':foo')",
        "genfile(name = 'output')",
        "check(",
        "  name = 'check',",
        "  deps = [':foo', ':foobar'],",
        "  asp_deps = [':bar'],",
        "  srcs = ['input.txt', 'output.txt'],",
        ")");
  }

  @Test
  public void testStringRepresentations_Strings() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=true");

    assertThat(skylarkLoadingEval("str('foo')")).isEqualTo("foo");
    assertThat(skylarkLoadingEval("'%s' % 'foo'")).isEqualTo("foo");
    assertThat(skylarkLoadingEval("'{}'.format('foo')")).isEqualTo("foo");
    assertThat(skylarkLoadingEval("repr('foo')")).isEqualTo("\"foo\"");
    assertThat(skylarkLoadingEval("'%r' % 'foo'")).isEqualTo("\"foo\"");
  }

  @Test
  public void testStringRepresentations_Labels() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=true");

    assertThat(skylarkLoadingEval("str(Label('//foo:bar'))")).isEqualTo("//foo:bar");
    assertThat(skylarkLoadingEval("'%s' % Label('//foo:bar')")).isEqualTo("//foo:bar");
    assertThat(skylarkLoadingEval("'{}'.format(Label('//foo:bar'))")).isEqualTo("//foo:bar");
    assertThat(skylarkLoadingEval("repr(Label('//foo:bar'))")).isEqualTo("Label(\"//foo:bar\")");
    assertThat(skylarkLoadingEval("'%r' % Label('//foo:bar')")).isEqualTo("Label(\"//foo:bar\")");

    assertThat(skylarkLoadingEval("'{}'.format([Label('//foo:bar')])")).isEqualTo("[Label(\"//foo:bar\")]");
  }

  @Test
  public void testStringRepresentations_Primitives() throws Exception {
    // Strings are tested in a separate test case as they have different str and repr values.
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=true");

    assertStringRepresentation("1543", "1543");
    assertStringRepresentation("True", "True");
    assertStringRepresentation("False", "False");
  }

  @Test
  public void testStringRepresentations_Containers() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=true");

    assertStringRepresentation("['a', 'b']", "[\"a\", \"b\"]");
    assertStringRepresentation("('a', 'b')", "(\"a\", \"b\")");
    assertStringRepresentation("{'a': 'b', 'c': 'd'}", "{\"a\": \"b\", \"c\": \"d\"}");
    assertStringRepresentation("struct(d = 4, c = 3)", "struct(c = 3, d = 4)");
  }


  @Test
  public void testStringRepresentations_Functions() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=true");

    assertStringRepresentation("all", "<built-in function all>");
    assertStringRepresentation("def f(): pass", "f", "<function f from //eval:eval.bzl>");
  }

  @Test
  public void testStringRepresentations_Rules() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=true");

    assertStringRepresentation("native.cc_library", "<built-in rule cc_library>");
    assertStringRepresentation("rule(implementation=str)", "<rule>");
  }

  @Test
  public void testLegacyStringRepresentations_Labels() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=false");

    assertThat(skylarkLoadingEval("str(Label('//foo:bar'))")).isEqualTo("//foo:bar");
    assertThat(skylarkLoadingEval("'%s' % Label('//foo:bar')")).isEqualTo("//foo:bar");
    assertThat(skylarkLoadingEval("'{}'.format(Label('//foo:bar'))")).isEqualTo("//foo:bar");
    assertThat(skylarkLoadingEval("repr(Label('//foo:bar'))")).isEqualTo("\"//foo:bar\"");
    assertThat(skylarkLoadingEval("'%r' % Label('//foo:bar')")).isEqualTo("\"//foo:bar\"");

    // Also test that str representations (as opposed to repr) also use legacy formatting
    // They are equivalent for labels, but not equivalent for lists of labels, because
    // containers always render their items with repr
    assertThat(skylarkLoadingEval("str([Label('//foo:bar')])")).isEqualTo("[\"//foo:bar\"]");
    assertThat(skylarkLoadingEval("'{}'.format([Label('//foo:bar')])")).isEqualTo("[\"//foo:bar\"]");
    assertThat(skylarkLoadingEval("'%s' % [Label('//foo:bar')]")).isEqualTo("[\"//foo:bar\"]");
  }

  @Test
  public void testLegacyStringRepresentations_Functions() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=false");

    assertStringRepresentation("all", "<function all>");
    assertStringRepresentation("def f(): pass", "f", "<function f>");
  }

  @Test
  public void testLegacyStringRepresentations_Rules() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=false");

    assertStringRepresentation("native.cc_library", "<function cc_library>");
    assertStringRepresentation("rule(implementation=str)", "<function rule>");
  }

  @Test
  public void testLegacyStringRepresentations_Aspects() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=false");

    assertStringRepresentation("aspect(implementation=str)", "Aspect:<function str>");
  }

  @Test
  public void testLegacyStringRepresentations_Providers() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=false");

    assertStringRepresentation("provider()", "<function <no name>>");
    assertStringRepresentation("p = provider()", "p(b = 2, a = 1)", "p(a = 1, b = 2)");
  }

  @Test
  public void testLegacyStringRepresentations_Select() throws Exception {
    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=false");

    assertStringRepresentation(
        "select({'//foo': ['//bar']}) + select({'//foo2': ['//bar2']})",
        "selector({\"//foo\": [\"//bar\"]}) + selector({\"//foo2\": [\"//bar2\"]})");
  }

  @Test
  public void testLegacyStringRepresentations_Targets() throws Exception {
    // alias targets in skylark used to leak their memory addresses in string representations,
    // we don't try to preserve this behaviour as it's harmful.
    // An example of their legacy representation:
    // "<com.google.devtools.build.lib.rules.AliasConfiguredTarget@12da9140>"

    setSkylarkSemanticsOptions("--incompatible_descriptive_string_representations=false");

    generateFilesToTestStrings();
    ConfiguredTarget target = getConfiguredTarget("//test/skylark:check");


    ImmutableList<Pair<String, String>> parameters = ImmutableList.of(
        new Pair<>("rule_ctx", "//test/skylark:check"),
        new Pair<>("aspect_ctx", "//test/skylark:bar"),
        new Pair<>("input_target", "InputFileConfiguredTarget(//test/skylark:input.txt)"));
    for (String suffix : SUFFIXES) {
      for (Pair<String, String > pair : parameters) {
        assertThat(target.get(pair.getFirst() + suffix)).isEqualTo(pair.getSecond());
      }
    }

    // Legacy representation of several types of objects may contain nondeterministic chunks
    parameters = ImmutableList.of(
        new Pair<>("target", "ConfiguredTarget\\(//test/skylark:foo, [0-9a-f]+\\)"),
        new Pair<>("aspect_target", "ConfiguredTarget\\(//test/skylark:bar, [0-9a-f]+\\)"),
        new Pair<>("output_target", "ConfiguredTarget\\(//test/skylark:output.txt, [0-9a-f]+\\)"));
    for (String suffix : SUFFIXES) {
      for (Pair<String, String > pair : parameters) {
        assertThat((String) target.get(pair.getFirst() + suffix)).matches(pair.getSecond());
      }
    }
  }
}
