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

package com.google.devtools.build.lib.runtime;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.flags.InvocationPolicyEnforcer;
import com.google.devtools.build.lib.runtime.proto.InvocationPolicyOuterClass.InvocationPolicy;
import com.google.devtools.build.lib.runtime.proto.InvocationPolicyOuterClass.UseDefault;
import com.google.devtools.common.options.Converters;
import com.google.devtools.common.options.ExpansionFunction;
import com.google.devtools.common.options.IsolatedOptionsData;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;
import com.google.devtools.common.options.OptionsParsingException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the Incompatible Changes system (--incompatible_* flags). These go in their own suite
 * because the options parser doesn't know the business logic for incompatible changes.
 */
@RunWith(JUnit4.class)
public class AllIncompatibleChangesExpansionTest {

  /** Dummy comment (linter suppression) */
  public static class ExampleOptions extends OptionsBase {
    @Option(
      name = "all",
      defaultValue = "null",
      expansionFunction = AllIncompatibleChangesExpansion.class
    )
    public Void all;

    @Option(name = "X", defaultValue = "false")
    public boolean x;

    @Option(name = "Y", defaultValue = "true")
    public boolean y;

    @Option(
      name = "incompatible_A",
      category = "incompatible changes",
      defaultValue = "false",
      help = "Migrate to A"
    )
    public boolean incompatibleA;

    @Option(
      name = "incompatible_B",
      category = "incompatible changes",
      defaultValue = "false",
      help = "Migrate to B"
    )
    public boolean incompatibleB;
  }

  /** Dummy comment (linter suppression) */
  public static class ExampleExpansionOptions extends OptionsBase {
    @Option(
      name = "incompatible_expX",
      category = "incompatible changes",
      defaultValue = "null",
      expansion = {"--X"},
      help = "Start using X"
    )
    public Void incompatibleExpX;

    /** Dummy comment (linter suppression) */
    public static class YExpansion implements ExpansionFunction {
      @Override
      public ImmutableList<String> getExpansion(IsolatedOptionsData optionsData) {
        return ImmutableList.of("--noY");
      }
    }

    @Option(
      name = "incompatible_expY",
      category = "incompatible changes",
      defaultValue = "null",
      expansionFunction = YExpansion.class,
      help = "Stop using Y"
    )
    public Void incompatibleExpY;
  }

  @Test
  public void noChangesSelected() throws OptionsParsingException {
    OptionsParser parser = OptionsParser.newOptionsParser(ExampleOptions.class);
    parser.parse("");
    ExampleOptions opts = parser.getOptions(ExampleOptions.class);
    assertThat(opts.x).isFalse();
    assertThat(opts.y).isTrue();
    assertThat(opts.incompatibleA).isFalse();
    assertThat(opts.incompatibleB).isFalse();
  }

  @Test
  public void allChangesSelected() throws OptionsParsingException {
    OptionsParser parser = OptionsParser.newOptionsParser(ExampleOptions.class);
    parser.parse("--all");
    ExampleOptions opts = parser.getOptions(ExampleOptions.class);
    assertThat(opts.x).isFalse();
    assertThat(opts.y).isTrue();
    assertThat(opts.incompatibleA).isTrue();
    assertThat(opts.incompatibleB).isTrue();
  }

  @Test
  public void rightmostOverrides() throws OptionsParsingException {
    // Check that all-expansion behaves just like any other expansion flag:
    // the rightmost setting of any individual option wins.
    OptionsParser parser = OptionsParser.newOptionsParser(ExampleOptions.class);
    parser.parse("--noincompatible_A", "--all", "--noincompatible_B");
    ExampleOptions opts = parser.getOptions(ExampleOptions.class);
    assertThat(opts.incompatibleA).isTrue();
    assertThat(opts.incompatibleB).isFalse();
  }

  @Test
  public void expansionOptions() throws OptionsParsingException {
    // Check that all-expansion behaves just like any other expansion flag:
    // the rightmost setting of any individual option wins.
    OptionsParser parser =
        OptionsParser.newOptionsParser(ExampleOptions.class, ExampleExpansionOptions.class);
    parser.parse("--all");
    ExampleOptions opts = parser.getOptions(ExampleOptions.class);
    assertThat(opts.x).isTrue();
    assertThat(opts.y).isFalse();
    assertThat(opts.incompatibleA).isTrue();
    assertThat(opts.incompatibleB).isTrue();
  }

  @Test
  public void invocationPolicy() throws OptionsParsingException {
    // Check that all-expansion behaves just like any other expansion flag and can be filtered
    // by invocation policy.
    InvocationPolicy.Builder invocationPolicyBuilder = InvocationPolicy.newBuilder();
    invocationPolicyBuilder.addFlagPoliciesBuilder()
        .setFlagName("incompatible_A")
        .setUseDefault(UseDefault.getDefaultInstance())
        .build();
    InvocationPolicy policy = invocationPolicyBuilder.build();
    InvocationPolicyEnforcer enforcer = new InvocationPolicyEnforcer(policy);

    OptionsParser parser =
        OptionsParser.newOptionsParser(ExampleOptions.class);
    parser.parse("--all");
    enforcer.enforce(parser);

    ExampleOptions opts = parser.getOptions(ExampleOptions.class);
    assertThat(opts.x).isFalse();
    assertThat(opts.y).isTrue();
    assertThat(opts.incompatibleA).isFalse(); // A should have been removed from the expansion.
    assertThat(opts.incompatibleB).isTrue(); // B, without a policy, should have been left alone.
  }

  // There's no unit test to check that the expansion of --all is sorted. IsolatedOptionsData is not
  // exposed from OptionsParser, making it difficult to check, and it's not clear that exposing it
  // would be worth it.

  /**
   * Ensure that we get an {@link OptionsParser.ConstructionException} containing {@code message}
   * when the incompatible changes in the given {@link OptionsBase} subclass are validated.
   */
  // Because javadoc can't resolve inner classes.
  @SuppressWarnings("javadoc")
  private static void assertBadness(Class<? extends OptionsBase> optionsBaseClass, String message) {
    try {
      OptionsParser.newOptionsParser(ExampleOptions.class, optionsBaseClass);
      fail("Should have failed with message \"" + message + "\"");
    } catch (OptionsParser.ConstructionException e) {
      assertThat(e).hasMessageThat().contains(message);
    }
  }

  /** Dummy comment (linter suppression) */
  public static class BadNameOptions extends OptionsBase {
    @Option(
      name = "badname",
      category = "incompatible changes",
      defaultValue = "false",
      help = "nohelp"
    )
    public boolean bad;
  }

  @Test
  public void badName() {
    assertBadness(
        BadNameOptions.class,
        "Incompatible change option '--badname' must have name "
            + "starting with \"incompatible_\"");
  }

  /** Dummy comment (linter suppression) */
  public static class BadCategoryOptions extends OptionsBase {
    @Option(name = "incompatible_bad", category = "badcat", defaultValue = "false", help = "nohelp")
    public boolean bad;
  }

  @Test
  public void badCategory() {
    assertBadness(BadCategoryOptions.class, "must have category \"incompatible changes\"");
  }

  /** Dummy comment (linter suppression) */
  public static class BadTypeOptions extends OptionsBase {
    @Option(
      name = "incompatible_bad",
      category = "incompatible changes",
      defaultValue = "0",
      help = "nohelp"
    )
    public int bad;
  }

  @Test
  public void badType() {
    assertBadness(BadTypeOptions.class, "must have boolean type");
  }

  /** Dummy comment (linter suppression) */
  public static class BadHelpOptions extends OptionsBase {
    @Option(name = "incompatible_bad", category = "incompatible changes", defaultValue = "false")
    public boolean bad;
  }

  @Test
  public void badHelp() {
    assertBadness(BadHelpOptions.class, "must have a \"help\" string");
  }

  /** Dummy comment (linter suppression) */
  public static class BadAbbrevOptions extends OptionsBase {
    @Option(
      name = "incompatible_bad",
      category = "incompatible changes",
      defaultValue = "false",
      help = "nohelp",
      abbrev = 'x'
    )
    public boolean bad;
  }

  @Test
  public void badAbbrev() {
    assertBadness(BadAbbrevOptions.class, "must not use the abbrev field");
  }

  /** Dummy comment (linter suppression) */
  public static class BadValueHelpOptions extends OptionsBase {
    @Option(
      name = "incompatible_bad",
      category = "incompatible changes",
      defaultValue = "false",
      help = "nohelp",
      valueHelp = "x"
    )
    public boolean bad;
  }

  @Test
  public void badValueHelp() {
    assertBadness(BadValueHelpOptions.class, "must not use the valueHelp field");
  }

  /** Dummy comment (linter suppression) */
  public static class BadConverterOptions extends OptionsBase {
    @Option(
      name = "incompatible_bad",
      category = "incompatible changes",
      defaultValue = "false",
      help = "nohelp",
      converter = Converters.BooleanConverter.class
    )
    public boolean bad;
  }

  @Test
  public void badConverter() {
    assertBadness(BadConverterOptions.class, "must not use the converter field");
  }

  /** Dummy comment (linter suppression) */
  public static class BadAllowMultipleOptions extends OptionsBase {
    @Option(
      name = "incompatible_bad",
      category = "incompatible changes",
      defaultValue = "null",
      help = "nohelp",
      allowMultiple = true
    )
    public List<String> bad;
  }

  @Test
  public void badAllowMutliple() {
    assertBadness(BadAllowMultipleOptions.class, "must not use the allowMultiple field");
  }

  /** Dummy comment (linter suppression) */
  public static class BadImplicitRequirementsOptions extends OptionsBase {
    @Option(
      name = "incompatible_bad",
      category = "incompatible changes",
      defaultValue = "false",
      help = "nohelp",
      implicitRequirements = "--x"
    )
    public boolean bad;
  }

  @Test
  public void badImplicitRequirements() {
    assertBadness(
        BadImplicitRequirementsOptions.class, "must not use the implicitRequirements field");
  }

  /** Dummy comment (linter suppression) */
  public static class BadOldNameOptions extends OptionsBase {
    @Option(
      name = "incompatible_bad",
      category = "incompatible changes",
      defaultValue = "false",
      help = "nohelp",
      oldName = "x"
    )
    public boolean bad;
  }

  @Test
  public void badOldName() {
    assertBadness(BadOldNameOptions.class, "must not use the oldName field");
  }

  /** Dummy comment (linter suppression) */
  public static class BadWrapperOptionOptions extends OptionsBase {
    @Option(
      name = "incompatible_bad",
      category = "incompatible changes",
      defaultValue = "false",
      help = "nohelp",
      wrapperOption = true
    )
    public boolean bad;
  }

  @Test
  public void badWrapperOption() {
    assertBadness(BadWrapperOptionOptions.class, "must not use the wrapperOption field");
  }
}
