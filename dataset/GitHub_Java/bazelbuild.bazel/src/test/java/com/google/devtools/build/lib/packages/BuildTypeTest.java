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
package com.google.devtools.build.lib.packages;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.lib.testutil.MoreAsserts.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.LabelSyntaxException;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.BuildType.Selector;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.Printer;
import com.google.devtools.build.lib.syntax.SelectorList;
import com.google.devtools.build.lib.syntax.SelectorValue;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.syntax.Type.ConversionException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test of type-conversions for build-specific types.
 */
@RunWith(JUnit4.class)
public class BuildTypeTest {
  private Label currentRule;

  @Before
  public final void setCurrentRule() throws Exception  {
    this.currentRule = Label.parseAbsolute("//quux:baz");
  }

  @Test
  public void testLabelKeyedStringDictConvertsToMapFromLabelToString() throws Exception {
    Map<Object, String> input = new ImmutableMap.Builder<Object, String>()
        .put("//absolute:label", "absolute value")
        .put(":relative", "theory of relativity")
        .put("nocolon", "colonial times")
        .put("//current/package:explicit", "explicit content")
        .put(Label.parseAbsolute("//i/was/already/a/label"), "and that's okay")
        .build();
    Label context = Label.parseAbsolute("//current/package:this");

    Map<Label, String> expected = new ImmutableMap.Builder<Label, String>()
        .put(Label.parseAbsolute("//absolute:label"), "absolute value")
        .put(Label.parseAbsolute("//current/package:relative"), "theory of relativity")
        .put(Label.parseAbsolute("//current/package:nocolon"), "colonial times")
        .put(Label.parseAbsolute("//current/package:explicit"), "explicit content")
        .put(Label.parseAbsolute("//i/was/already/a/label"), "and that's okay")
        .build();

    assertThat(BuildType.LABEL_KEYED_STRING_DICT.convert(input, null, context))
        .containsExactlyEntriesIn(expected);
  }

  @Test
  public void testLabelKeyedStringDictConvertingStringShouldFail() throws Exception {
    try {
      BuildType.LABEL_KEYED_STRING_DICT.convert("//actually/a:label", null, currentRule);
      fail("Expected a conversion exception to be thrown.");
    } catch (ConversionException expected) {
      assertThat(expected)
          .hasMessage(
              "expected value of type 'dict(label, string)', "
                  + "but got \"//actually/a:label\" (string)");
    }
  }

  @Test
  public void testLabelKeyedStringDictConvertingListShouldFail() throws Exception {
    try {
      BuildType.LABEL_KEYED_STRING_DICT.convert(
          ImmutableList.of("//actually/a:label"), null, currentRule);
      fail("Expected a conversion exception to be thrown.");
    } catch (ConversionException expected) {
      assertThat(expected)
          .hasMessage(
              "expected value of type 'dict(label, string)', "
                  + "but got [\"//actually/a:label\"] (List)");
    }
  }

  @Test
  public void testLabelKeyedStringDictConvertingMapWithNonStringKeyShouldFail() throws Exception {
    try {
      BuildType.LABEL_KEYED_STRING_DICT.convert(ImmutableMap.of(1, "OK"), null, currentRule);
      fail("Expected a conversion exception to be thrown.");
    } catch (ConversionException expected) {
      assertThat(expected)
          .hasMessage("expected value of type 'string' for dict key element, but got 1 (int)");
    }
  }

  @Test
  public void testLabelKeyedStringDictConvertingMapWithNonStringValueShouldFail() throws Exception {
    try {
      BuildType.LABEL_KEYED_STRING_DICT.convert(
          ImmutableMap.of("//actually/a:label", 3), null, currentRule);
      fail("Expected a conversion exception to be thrown.");
    } catch (ConversionException expected) {
      assertThat(expected)
          .hasMessage("expected value of type 'string' for dict value element, but got 3 (int)");
    }
  }

  @Test
  public void testLabelKeyedStringDictConvertingMapWithInvalidLabelKeyShouldFail()
      throws Exception {
    try {
      BuildType.LABEL_KEYED_STRING_DICT.convert(
          ImmutableMap.of("//uplevel/references/are:../../forbidden", "OK"), null, currentRule);
      fail("Expected a conversion exception to be thrown.");
    } catch (ConversionException expected) {
      assertThat(expected)
          .hasMessage(
              "invalid label '//uplevel/references/are:../../forbidden' in "
                  + "dict key element: invalid target name '../../forbidden': "
                  + "target names may not contain up-level references '..'");
    }
  }

  @Test
  public void testLabelKeyedStringDictConvertingMapWithMultipleEquivalentKeysShouldFail()
      throws Exception {
    Label context = Label.parseAbsolute("//current/package:this");
    Map<String, String> input = new ImmutableMap.Builder<String, String>()
        .put(":reference", "value1")
        .put("//current/package:reference", "value2")
        .build();
    try {
      BuildType.LABEL_KEYED_STRING_DICT.convert(input, null, context);
      fail("Expected a conversion exception to be thrown.");
    } catch (ConversionException expected) {
      assertThat(expected)
          .hasMessage(
              "duplicate labels: //current/package:reference "
                  + "(as [\":reference\", \"//current/package:reference\"])");
    }
  }

  @Test
  public void testLabelKeyedStringDictConvertingMapWithMultipleSetsOfEquivalentKeysShouldFail()
      throws Exception {
    Label context = Label.parseAbsolute("//current/rule:sibling");
    Map<String, String> input = new ImmutableMap.Builder<String, String>()
        .put(":rule", "first set")
        .put("//current/rule:rule", "also first set")
        .put("//other/package:package", "interrupting rule")
        .put("//other/package", "interrupting rule's friend")
        .put("//current/rule", "part of first set but non-contiguous in iteration order")
        .put("//not/involved/in/any:collisions", "same value")
        .put("//also/not/involved/in/any:collisions", "same value")
        .build();
    try {
      BuildType.LABEL_KEYED_STRING_DICT.convert(input, null, context);
      fail("Expected a conversion exception to be thrown.");
    } catch (ConversionException expected) {
      assertThat(expected)
          .hasMessage(
              "duplicate labels: //current/rule:rule "
                  + "(as [\":rule\", \"//current/rule:rule\", \"//current/rule\"]), "
                  + "//other/package:package "
                  + "(as [\"//other/package:package\", \"//other/package\"])");
    }
  }

  @Test
  public void testLabelKeyedStringDictErrorConvertingMapWithMultipleEquivalentKeysIncludesContext()
      throws Exception {
    Label context = Label.parseAbsolute("//current/package:this");
    Map<String, String> input = new ImmutableMap.Builder<String, String>()
        .put(":reference", "value1")
        .put("//current/package:reference", "value2")
        .build();
    try {
      BuildType.LABEL_KEYED_STRING_DICT.convert(input, "flag map", context);
      fail("Expected a conversion exception to be thrown.");
    } catch (ConversionException expected) {
      assertThat(expected)
          .hasMessage(
              "duplicate labels in flag map: //current/package:reference "
                  + "(as [\":reference\", \"//current/package:reference\"])");
    }
  }

  @Test
  public void testLabelKeyedStringDictCollectLabels() throws Exception {
    Map<Label, String> input = new ImmutableMap.Builder<Label, String>()
        .put(Label.parseAbsolute("//absolute:label"), "absolute value")
        .put(Label.parseAbsolute("//current/package:relative"), "theory of relativity")
        .put(Label.parseAbsolute("//current/package:nocolon"), "colonial times")
        .put(Label.parseAbsolute("//current/package:explicit"), "explicit content")
        .put(Label.parseAbsolute("//i/was/already/a/label"), "and that's okay")
        .build();

    ImmutableList<Label> expected =
        ImmutableList.of(
            Label.parseAbsolute("//absolute:label"),
            Label.parseAbsolute("//current/package:relative"),
            Label.parseAbsolute("//current/package:nocolon"),
            Label.parseAbsolute("//current/package:explicit"),
            Label.parseAbsolute("//i/was/already/a/label"));

    assertThat(collectLabels(BuildType.LABEL_KEYED_STRING_DICT, input))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testFilesetEntry() throws Exception {
    Label srcDir = Label.create("foo", "src");
    Label entryLabel = Label.create("foo", "entry");
    FilesetEntry input =
        new FilesetEntry(
            /* srcLabel */ srcDir,
            /* files */ ImmutableList.of(entryLabel),
            /* excludes */ null,
            /* destDir */ null,
            /* symlinkBehavior */ null,
            /* stripPrefix */ null);
    assertThat(BuildType.FILESET_ENTRY.convert(input, null, currentRule)).isEqualTo(input);
    assertThat(collectLabels(BuildType.FILESET_ENTRY, input)).containsExactly(entryLabel);
  }

  @Test
  public void testFilesetEntryList() throws Exception {
    Label srcDir = Label.create("foo", "src");
    Label entry1Label = Label.create("foo", "entry1");
    Label entry2Label = Label.create("foo", "entry");
    List<FilesetEntry> input = ImmutableList.of(
        new FilesetEntry(
            /* srcLabel */ srcDir,
            /* files */ ImmutableList.of(entry1Label),
            /* excludes */ null,
            /* destDir */ null,
            /* symlinkBehavior */ null,
            /* stripPrefix */ null),
        new FilesetEntry(
            /* srcLabel */ srcDir,
            /* files */ ImmutableList.of(entry2Label),
            /* excludes */ null,
            /* destDir */ null,
            /* symlinkBehavior */ null,
            /* stripPrefix */ null));
    assertThat(BuildType.FILESET_ENTRY_LIST.convert(input, null, currentRule)).isEqualTo(input);
    assertThat(collectLabels(BuildType.FILESET_ENTRY_LIST, input)).containsExactly(
        entry1Label, entry2Label);
  }

  /**
   * Tests basic {@link Selector} functionality.
   */
  @Test
  public void testSelector() throws Exception {
    ImmutableMap<String, String> input = ImmutableMap.of(
        "//conditions:a", "//a:a",
        "//conditions:b", "//b:b",
        Selector.DEFAULT_CONDITION_KEY, "//d:d");
    Selector<Label> selector = new Selector<>(input, null, currentRule, BuildType.LABEL);
    assertThat(selector.getOriginalType()).isEqualTo(BuildType.LABEL);

    Map<Label, Label> expectedMap = ImmutableMap.of(
        Label.parseAbsolute("//conditions:a"), Label.create("@//a", "a"),
        Label.parseAbsolute("//conditions:b"), Label.create("@//b", "b"),
        Label.parseAbsolute(BuildType.Selector.DEFAULT_CONDITION_KEY), Label.create("@//d", "d"));
    assertThat(selector.getEntries().entrySet()).containsExactlyElementsIn(expectedMap.entrySet());
  }

  /**
   * Tests that creating a {@link Selector} over a mismatching native type triggers an
   * exception.
   */
  @Test
  public void testSelectorWrongType() throws Exception {
    ImmutableMap<String, String> input = ImmutableMap.of(
        "//conditions:a", "not a/../label", "//conditions:b", "also not a/../label",
        BuildType.Selector.DEFAULT_CONDITION_KEY, "whatever");
    try {
      new Selector<>(input, null, currentRule, BuildType.LABEL);
      fail("Expected Selector instantiation to fail since the input isn't a selection of labels");
    } catch (ConversionException e) {
      assertThat(e).hasMessageThat().contains("invalid label 'not a/../label'");
    }
  }

  /**
   * Tests that non-label selector keys trigger an exception.
   */
  @Test
  public void testSelectorKeyIsNotALabel() throws Exception {
    ImmutableMap<String, String> input = ImmutableMap.of(
        "not a/../label", "//a:a",
        BuildType.Selector.DEFAULT_CONDITION_KEY, "whatever");
    try {
      new Selector<>(input, null, currentRule, BuildType.LABEL);
      fail("Expected Selector instantiation to fail since the key isn't a label");
    } catch (ConversionException e) {
      assertThat(e).hasMessageThat().contains("invalid label 'not a/../label'");
    }
  }

  /**
   * Tests that {@link Selector} correctly references its default value.
   */
  @Test
  public void testSelectorDefault() throws Exception {
    ImmutableMap<String, String> input = ImmutableMap.of(
        "//conditions:a", "//a:a",
        "//conditions:b", "//b:b",
        BuildType.Selector.DEFAULT_CONDITION_KEY, "//d:d");
    assertThat(new Selector<>(input, null, currentRule, BuildType.LABEL).getDefault())
        .isEqualTo(Label.create("@//d", "d"));
  }

  @Test
  public void testSelectorList() throws Exception {
    Object selector1 = new SelectorValue(ImmutableMap.of("//conditions:a",
        ImmutableList.of("//a:a"), "//conditions:b", ImmutableList.of("//b:b")), "");
    Object selector2 = new SelectorValue(ImmutableMap.of("//conditions:c",
        ImmutableList.of("//c:c"), "//conditions:d", ImmutableList.of("//d:d")), "");
    BuildType.SelectorList<List<Label>> selectorList = new BuildType.SelectorList<>(
        ImmutableList.of(selector1, selector2), null, currentRule, BuildType.LABEL_LIST);

    assertThat(selectorList.getOriginalType()).isEqualTo(BuildType.LABEL_LIST);
    assertThat(selectorList.getKeyLabels())
        .containsExactly(
            Label.parseAbsolute("//conditions:a"),
            Label.parseAbsolute("//conditions:b"),
            Label.parseAbsolute("//conditions:c"),
            Label.parseAbsolute("//conditions:d"));

    List<Selector<List<Label>>> selectors = selectorList.getSelectors();
    assertThat(selectors.get(0).getEntries().entrySet())
        .containsExactlyElementsIn(
            ImmutableMap.of(Label.parseAbsolute("//conditions:a"),
            ImmutableList.of(Label.create("@//a", "a")), Label.parseAbsolute("//conditions:b"),
            ImmutableList.of(Label.create("@//b", "b")))
                .entrySet());
    assertThat(selectors.get(1).getEntries().entrySet())
        .containsExactlyElementsIn(
            ImmutableMap.of(
                Label.parseAbsolute("//conditions:c"), ImmutableList.of(Label.create("@//c", "c")),
                Label.parseAbsolute("//conditions:d"), ImmutableList.of(Label.create("@//d", "d")))
                .entrySet());
  }

  @Test
  public void testSelectorListMixedTypes() throws Exception {
    Object selector1 =
        new SelectorValue(ImmutableMap.of("//conditions:a", ImmutableList.of("//a:a")), "");
    Object selector2 =
        new SelectorValue(ImmutableMap.of("//conditions:b", "//b:b"), "");
    try {
      new BuildType.SelectorList<>(ImmutableList.of(selector1, selector2), null, currentRule,
          BuildType.LABEL_LIST);
      fail("Expected SelectorList initialization to fail on mixed element types");
    } catch (ConversionException e) {
      assertThat(e).hasMessageThat().contains("expected value of type 'list(label)'");
    }
  }

  @Test
  public void testSelectorList_concatenate_selectorList() throws Exception {
    SelectorList selectorList =
        SelectorList.of(
            new SelectorValue(ImmutableMap.of("//conditions:a", ImmutableList.of("//a:a")), ""));
    List<String> list = ImmutableList.of("//a:a", "//b:b");

    // Creating a SelectorList from a SelectorList and a list should work properly.
    SelectorList result = SelectorList.of(Location.BUILTIN, selectorList, list);
    assertThat(result).isNotNull();
    assertThat(result.getType()).isAssignableTo(List.class);
  }

  @Test
  public void testSelectorList_concatenate_selectorValue() throws Exception {
    SelectorValue selectorValue =
        new SelectorValue(ImmutableMap.of("//conditions:a", ImmutableList.of("//a:a")), "");
    List<String> list = ImmutableList.of("//a:a", "//b:b");

    // Creating a SelectorList from a SelectorValue and a list should work properly.
    SelectorList result = SelectorList.of(Location.BUILTIN, selectorValue, list);
    assertThat(result).isNotNull();
    assertThat(result.getType()).isAssignableTo(List.class);
  }

  @Test
  public void testSelectorList_concatenate_differentListTypes() throws Exception {
    List<String> list = ImmutableList.of("//a:a", "//b:b");
    List<String> arrayList = new ArrayList<>();
    arrayList.add("//a:a");

    // Creating a SelectorList from two lists of different types should work properly.
    SelectorList result = SelectorList.of(Location.BUILTIN, list, arrayList);
    assertThat(result).isNotNull();
    assertThat(result.getType()).isAssignableTo(List.class);
  }

  @Test
  public void testSelectorList_concatenate_invalidType() throws Exception {
    List<String> list = ImmutableList.of("//a:a", "//b:b");

    // Creating a SelectorList from a list and a non-list should fail.
    assertThrows(EvalException.class, () -> SelectorList.of(Location.BUILTIN, list, "A string"));
  }

  /**
   * Tests that {@link BuildType#selectableConvert} returns either the native type or a selector
   * on that type, in accordance with the provided input.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testSelectableConvert() throws Exception {
    Object nativeInput = Arrays.asList("//a:a1", "//a:a2");
    Object selectableInput =
        SelectorList.of(new SelectorValue(ImmutableMap.of(
            "//conditions:a", nativeInput,
            BuildType.Selector.DEFAULT_CONDITION_KEY, nativeInput), ""));
    List<Label> expectedLabels =
        ImmutableList.of(Label.create("@//a", "a1"), Label.create("@//a", "a2"));

    // Conversion to direct type:
    Object converted = BuildType
        .selectableConvert(BuildType.LABEL_LIST, nativeInput, null, currentRule);
    assertThat(converted instanceof List<?>).isTrue();
    assertThat((List<Label>) converted).containsExactlyElementsIn(expectedLabels);

    // Conversion to selectable type:
    converted = BuildType
        .selectableConvert(BuildType.LABEL_LIST, selectableInput, null, currentRule);
    BuildType.SelectorList<?> selectorList = (BuildType.SelectorList<?>) converted;
    assertThat(((Selector<Label>) selectorList.getSelectors().get(0)).getEntries().entrySet())
        .containsExactlyElementsIn(
            ImmutableMap.of(
                    Label.parseAbsolute("//conditions:a"),
                    expectedLabels,
                    Label.parseAbsolute(BuildType.Selector.DEFAULT_CONDITION_KEY),
                    expectedLabels)
                .entrySet());
  }

  /**
   * Tests that {@link com.google.devtools.build.lib.syntax.Type#convert} fails on selector inputs.
   */
  @Test
  public void testConvertDoesNotAcceptSelectables() throws Exception {
    Object selectableInput = SelectorList.of(
        new SelectorValue(
            ImmutableMap.of("//conditions:a", Arrays.asList("//a:a1", "//a:a2")), ""));
    try {
      BuildType.LABEL_LIST.convert(selectableInput, null, currentRule);
      fail("Expected conversion to fail on a selectable input");
    } catch (ConversionException e) {
      assertThat(e).hasMessageThat().contains("expected value of type 'list(label)'");
    }
  }

  /**
   * Tests for "reserved" key labels (i.e. not intended to map to actual targets).
   */
  @Test
  public void testReservedKeyLabels() throws Exception {
    assertThat(BuildType.Selector.isReservedLabel(Label.parseAbsolute("//condition:a"))).isFalse();
    assertThat(
            BuildType.Selector.isReservedLabel(
                Label.parseAbsolute(BuildType.Selector.DEFAULT_CONDITION_KEY)))
        .isTrue();
  }

  @Test
  public void testUnconditionalSelects() throws Exception {
    assertThat(
            new Selector<>(
                    ImmutableMap.of("//conditions:a", "//a:a"), null, currentRule, BuildType.LABEL)
                .isUnconditional())
        .isFalse();
    assertThat(
            new Selector<>(
                    ImmutableMap.of(
                        "//conditions:a",
                        "//a:a",
                        BuildType.Selector.DEFAULT_CONDITION_KEY,
                        "//b:b"),
                    null,
                    currentRule,
                    BuildType.LABEL)
                .isUnconditional())
        .isFalse();
    assertThat(
            new Selector<>(
                    ImmutableMap.of(BuildType.Selector.DEFAULT_CONDITION_KEY, "//b:b"),
                    null,
                    currentRule,
                    BuildType.LABEL)
                .isUnconditional())
        .isTrue();
  }

  private static FilesetEntry makeFilesetEntry() {
    try {
      return new FilesetEntry(
          /* srcLabel */ Label.parseAbsolute("//foo:bar"),
          /* files */ ImmutableList.<Label>of(),
          /* excludes */ ImmutableSet.of("xyz"),
          /* destDir */ null,
          /* symlinkBehavior */ null,
          /* stripPrefix */ null);
    } catch (LabelSyntaxException e) {
      throw new RuntimeException("Bad label: ", e);
    }
  }

  private String createExpectedFilesetEntryString(
      FilesetEntry.SymlinkBehavior symlinkBehavior, char quotationMark) {
    return String.format(
        "FilesetEntry(srcdir = %1$c//x:x%1$c,"
        + " files = [%1$c//x:x%1$c],"
        + " excludes = [],"
        + " destdir = %1$c%1$c,"
        + " strip_prefix = %1$c.%1$c,"
        + " symlinks = %1$c%2$s%1$c)",
        quotationMark, symlinkBehavior.toString().toLowerCase());
  }

  private String createExpectedFilesetEntryString(char quotationMark) {
    return createExpectedFilesetEntryString(FilesetEntry.SymlinkBehavior.COPY, quotationMark);
  }

  private FilesetEntry createTestFilesetEntry(
      FilesetEntry.SymlinkBehavior symlinkBehavior)
      throws LabelSyntaxException {
    Label label = Label.parseAbsolute("//x");
    return new FilesetEntry(
        /* srcLabel */ label,
        /* files */ Arrays.asList(label),
        /* excludes */ null,
        /* destDir */ null,
        /* symlinkBehavior */ symlinkBehavior,
        /* stripPrefix */ null);
  }

  private FilesetEntry createTestFilesetEntry() throws LabelSyntaxException {
    return createTestFilesetEntry(FilesetEntry.SymlinkBehavior.COPY);
  }

  @Test
  public void testRegressionCrashInPrettyPrintValue() throws Exception {
    // Would cause crash in code such as this:
    //  Fileset(name='x', entries=[], out=[FilesetEntry(files=['a'])])
    // While formatting the "expected x, got y" message for the 'out'
    // attribute, prettyPrintValue(FilesetEntry) would be recursively called
    // with a List<Label> even though this isn't a valid datatype in the
    // interpreter.
    // Fileset isn't part of bazel, even though FilesetEntry is.
    assertThat(Printer.repr(createTestFilesetEntry()))
        .isEqualTo(createExpectedFilesetEntryString('"'));
  }

  @Test
  public void testFilesetEntrySymlinkAttr() throws Exception {
    FilesetEntry entryDereference =
      createTestFilesetEntry(FilesetEntry.SymlinkBehavior.DEREFERENCE);

    assertThat(Printer.repr(entryDereference))
        .isEqualTo(createExpectedFilesetEntryString(FilesetEntry.SymlinkBehavior.DEREFERENCE, '"'));
  }

  private FilesetEntry createStripPrefixFilesetEntry(String stripPrefix)  throws Exception {
    Label label = Label.parseAbsolute("//x");
    return new FilesetEntry(
        /* srcLabel */ label,
        /* files */ Arrays.asList(label),
        /* excludes */ null,
        /* destDir */ null,
        /* symlinkBehavior */ FilesetEntry.SymlinkBehavior.DEREFERENCE,
        /* stripPrefix */ stripPrefix);
  }

  @Test
  public void testFilesetEntryStripPrefixAttr() throws Exception {
    FilesetEntry withoutStripPrefix = createStripPrefixFilesetEntry(".");
    FilesetEntry withStripPrefix = createStripPrefixFilesetEntry("orange");

    String prettyWithout = Printer.repr(withoutStripPrefix);
    String prettyWith = Printer.repr(withStripPrefix);

    assertThat(prettyWithout).contains("strip_prefix = \".\"");
    assertThat(prettyWith).contains("strip_prefix = \"orange\"");
  }

  @Test
  public void testPrintFilesetEntry() throws Exception {
    assertThat(
        Printer.repr(
            new FilesetEntry(
                /* srcLabel */ Label.parseAbsolute("//foo:BUILD"),
                /* files */ ImmutableList.of(Label.parseAbsolute("//foo:bar")),
                /* excludes */ ImmutableSet.of("baz"),
                /* destDir */ "qux",
                /* symlinkBehavior */ FilesetEntry.SymlinkBehavior.DEREFERENCE,
                /* stripPrefix */ "blah")))
        .isEqualTo(
            Joiner.on(" ").join(
                ImmutableList.of(
                    "FilesetEntry(srcdir = \"//foo:BUILD\",",
                    "files = [\"//foo:bar\"],",
                    "excludes = [\"baz\"],",
                    "destdir = \"qux\",",
                    "strip_prefix = \"blah\",",
                    "symlinks = \"dereference\")")));
  }

  @Test
  public void testFilesetTypeDefinition() throws Exception {
    assertThat(EvalUtils.getDataTypeName(makeFilesetEntry())).isEqualTo("FilesetEntry");
    assertThat(EvalUtils.isImmutable(makeFilesetEntry())).isFalse();
  }

  private static ImmutableList<Label> collectLabels(Type<?> type, Object value)
      throws InterruptedException {
    final ImmutableList.Builder<Label> result = ImmutableList.builder();
    type.visitLabels(
        new Type.LabelVisitor<Object>() {
          @SuppressWarnings("unchecked")
          @Override
          public void visit(Label label, Object dummy) throws InterruptedException {
            result.add(label);
          }
        },
        value,
        /*context=*/ null);
    return result.build();
  }
}
