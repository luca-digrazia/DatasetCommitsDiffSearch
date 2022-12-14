// Copyright 2005 Google Inc. All Rights Reserved.
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
package com.google.devtools.build.lib.syntax;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.lib.testutil.MoreAsserts.assertContainsRegex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.google.devtools.build.lib.syntax.Label.SyntaxException;
import com.google.devtools.build.lib.testutil.TestUtils;
import com.google.devtools.build.lib.vfs.PathFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.regex.Pattern;

/**
 * Tests for {@link Label}.
 */
@RunWith(JUnit4.class)
public class LabelTest {

  private static final String BAD_PACKAGE_CHARS =
      "package names may contain only A-Z, a-z, 0-9, '/', '-' and '_'";

  private static final String TARGET_UPLEVEL =
      "target names may not contain up-level references '..'";

  @Test
  public void testAbsolute() throws Exception {
    {
      Label l = Label.parseAbsolute("//foo/bar:baz");
      assertEquals("foo/bar", l.getPackageName());
      assertEquals("baz", l.getName());
    }
    {
      Label l = Label.parseAbsolute("//foo/bar");
      assertEquals("foo/bar", l.getPackageName());
      assertEquals("bar", l.getName());
    }
  }

  private static String parseCommandLine(String label, String prefix) throws SyntaxException {
    return Label.parseCommandLineLabel(label, new PathFragment(prefix)).toString();
  }

  @Test
  public void testLabelResolution() throws Exception {
    assertEquals("//absolute:label", parseCommandLine("//absolute:label", ""));
    assertEquals("//absolute:label", parseCommandLine("//absolute:label", "absolute"));
    assertEquals("//absolute:label", parseCommandLine(":label", "absolute"));
    assertEquals("//absolute:label", parseCommandLine("label", "absolute"));
    assertEquals("//absolute:label", parseCommandLine("absolute:label", ""));
    assertEquals("//absolute/path:label", parseCommandLine("path:label", "absolute"));
    assertEquals("//absolute/path:label/path", parseCommandLine("path:label/path", "absolute"));
    assertEquals("//absolute:label/path", parseCommandLine("label/path", "absolute"));
  }

  @Test
  public void testLabelResolutionAbsolutePath() throws Exception {
    try {
      parseCommandLine("//absolute:label", "/absolute");
      fail();
    } catch (IllegalArgumentException e) {
      // Expected exception
    }
  }

  @Test
  public void testLabelResolutionBadSyntax() throws Exception {
    try {
      parseCommandLine("//absolute:A+bad%syntax", "");
      fail();
    } catch (SyntaxException e) {
      // Expected exception
    }
  }

  @Test
  public void testGetRelative() throws Exception {
    Label base = Label.parseAbsolute("//foo/bar:baz");
    {
      Label l = base.getRelative("//p1/p2:target");
      assertEquals("p1/p2", l.getPackageName());
      assertEquals("target", l.getName());
    }
    {
      Label l = base.getRelative(":quux");
      assertEquals("foo/bar", l.getPackageName());
      assertEquals("quux", l.getName());
    }
    try {
      base.getRelative("/p1/p2:target");
      fail();
    } catch (Label.SyntaxException e) {
      /* ok */
    }
    try {
      base.getRelative("quux:");
      fail();
    } catch (Label.SyntaxException e) {
      /* ok */
    }
    try {
      base.getRelative(":");
      fail();
    } catch (Label.SyntaxException e) {
      /* ok */
    }
    try {
      base.getRelative("::");
      fail();
    } catch (Label.SyntaxException e) {
      /* ok */
    }
  }

  @Test
  public void testFactory() throws Exception {
    Label l = Label.create("foo/bar", "quux");
    assertEquals("foo/bar", l.getPackageName());
    assertEquals("quux", l.getName());
  }

  @Test
  public void testIdentities() throws Exception {

    Label l1 = Label.parseAbsolute("//foo/bar:baz");
    Label l2 = Label.parseAbsolute("//foo/bar:baz");
    Label l3 = Label.parseAbsolute("//foo/bar:quux");

    assertEquals(l1, l1);
    assertEquals(l1, l2);
    assertEquals(l2, l1);
    assertEquals(l1, l2);

    assertFalse(l3.equals(l1));
    assertFalse(l1.equals(l3));

    assertEquals(l1.hashCode(), l2.hashCode());
  }

  @Test
  public void testToString() throws Exception {
    {
      String s = "//foo/bar:baz";
      Label l = Label.parseAbsolute(s);
      assertEquals(s, l.toString());
    }
    {
      Label l = Label.parseAbsolute("//foo/bar");
      assertEquals("//foo/bar:bar", l.toString());
    }
  }

  @Test
  public void testDotDot() throws Exception {
    Label.parseAbsolute("//foo/bar:baz..gif");
  }

  /**
   * Asserts that creating a label throws a SyntaxException.
   * @param label the label to create.
   */
  private static void assertSyntaxError(String expectedError, String label) {
    try {
      Label.parseAbsolute(label);
      fail("Label '" + label + "' did not contain a syntax error");
    } catch (SyntaxException e) {
      assertContainsRegex(Pattern.quote(expectedError), e.getMessage());
    }
  }

  @Test
  public void testBadCharacters() throws Exception {
    assertSyntaxError("package names may contain only",
                      "//foo/bar baz");
    assertSyntaxError("target names may not contain ':'",
                      "//foo:bar:baz");
    assertSyntaxError("target names may not contain ':'",
                      "//foo:bar:");
    assertSyntaxError("target names may not contain ':'",
                      "//foo/bar::");
    assertSyntaxError("target names may not contain '&'",
                      "//foo:bar&");
    assertSyntaxError("target names may not contain '$'",
                      "//foo/bar:baz$a");
    assertSyntaxError("target names may not contain '('",
                      "//foo/bar:baz(foo)");
    assertSyntaxError("target names may not contain ')'",
                      "//foo/bar:bazfoo)");
  }

  @Test
  public void testUplevelReferences() throws Exception {
    assertSyntaxError(BAD_PACKAGE_CHARS,
                      "//foo/bar/..:baz");
    assertSyntaxError(BAD_PACKAGE_CHARS,
                      "//foo/../baz:baz");
    assertSyntaxError(BAD_PACKAGE_CHARS,
                      "//../bar/baz:baz");
    assertSyntaxError(BAD_PACKAGE_CHARS,
                      "//..:foo");
    assertSyntaxError(TARGET_UPLEVEL,
                      "//foo:bar/../baz");
    assertSyntaxError(TARGET_UPLEVEL,
                      "//foo:../bar/baz");
    assertSyntaxError(TARGET_UPLEVEL,
                      "//foo:bar/baz/..");
    assertSyntaxError(TARGET_UPLEVEL,
                      "//foo:..");
  }

  @Test
  public void testDotAsAPathSegment() throws Exception {
    assertSyntaxError("package names may contain only A-Z, a-z, 0-9, '/', '-' and '_'",
                      "//foo/bar/.:baz");
    assertSyntaxError(BAD_PACKAGE_CHARS,
                      "//foo/./baz:baz");
    assertSyntaxError(BAD_PACKAGE_CHARS,
                      "//./bar/baz:baz");
    assertSyntaxError(BAD_PACKAGE_CHARS,
                      "//.:foo");
    assertSyntaxError("target names may not contain '.' as a path segment",
                      "//foo:bar/./baz");
    assertSyntaxError("target names may not contain '.' as a path segment",
                      "//foo:./bar/baz");
    // TODO(bazel-team): enable when we have removed the "Workaround" in Label
    // that rewrites broken Labels by removing the trailing '.'
    //assertSyntaxError(TARGET_UPLEVEL,
    //                  "//foo:bar/baz/.");
    //assertSyntaxError(TARGET_UPLEVEL,
    //                  "//foo:.");
  }

  @Test
  public void testTrailingDotSegment() throws Exception {
    assertEquals(Label.parseAbsolute("//foo:dir/."), Label.parseAbsolute("//foo:dir"));
  }

  @Test
  public void testSomeOtherBadLabels() throws Exception {
    assertSyntaxError("package names may not end with '/'",
                      "//foo/:bar");
    assertSyntaxError("empty package name", "//:foo");
    assertSyntaxError("package names may not start with '/'", "///p:foo");
    assertSyntaxError("package names may not contain '//' path separators",
                      "//a//b:foo");
  }

  @Test
  public void testSomeGoodLabels() throws Exception {
    Label.parseAbsolute("//foo:..bar");
    Label.parseAbsolute("//Foo:..bar");
    Label.parseAbsolute("//-Foo:..bar");
    Label.parseAbsolute("//00:..bar");
    Label.parseAbsolute("//package:foo+bar");
    Label.parseAbsolute("//package:foo_bar");
    Label.parseAbsolute("//package:foo=bar");
    Label.parseAbsolute("//package:foo-bar");
    Label.parseAbsolute("//package:foo.bar");
    Label.parseAbsolute("//package:foo@bar");
    Label.parseAbsolute("//package:foo~bar");
  }

  /**
   * Regression test: we previously expanded the set of characters which are considered label chars
   * to include "@" (see test above). An unexpected side-effect is that "@D" in genrule(cmd) was
   * considered to be a valid relative label! The fix is to forbid "@x" in package names.
   */
  @Test
  public void testAtVersionIsIllegal() throws Exception {
    assertSyntaxError(BAD_PACKAGE_CHARS, "//foo/bar@123:baz");
  }

  @Test
  public void testDoubleSlashPathSeparator() throws Exception {
    assertSyntaxError("package names may not contain '//' path separators",
                      "//foo//bar:baz");
    assertSyntaxError("target names may not contain '//' path separator",
                      "//foo:bar//baz");
  }

  @Test
  public void testNonPrintableCharacters() throws Exception {
    assertSyntaxError(
      "target names may not contain non-printable characters: '\\x02'",
      "//foo:..\002bar");
  }

  /** Make sure that control characters - such as CR - are escaped on output. */
  @Test
  public void testInvalidLineEndings() throws Exception {
    assertSyntaxError("invalid target name '..bar\\r': "
        + "target names may not end with carriage returns", "//foo:..bar\r");
  }

  @Test
  public void testEmptyName() throws Exception {
    assertSyntaxError("invalid target name '': empty target name", "//foo/bar:");
  }

  @Test
  public void testSerializationSimple() throws Exception {
    checkSerialization("//a", 91);
  }

  @Test
  public void testSerializationNested() throws Exception {
    checkSerialization("//foo/bar:baz", 99);
  }

  @Test
  public void testSerializationWithoutTargetName() throws Exception {
    checkSerialization("//foo/bar", 99);
  }

  private void checkSerialization(String labelString, int expectedSize) throws Exception {
    Label a = Label.parseAbsolute(labelString);
    byte[] sa = TestUtils.serializeObject(a);
    assertEquals(expectedSize, sa.length);

    Label a2 = (Label) TestUtils.deserializeObject(sa);
    assertEquals(a, a2);
  }

  @Test
  public void testRepoLabel() throws Exception {
    Label label = Label.parseRepositoryLabel("@foo//bar/baz:bat/boo");
    assertEquals("@foo//bar/baz:bat/boo", label.toString());
  }

  @Test
  public void testNoRepo() throws Exception {
    Label label = Label.parseRepositoryLabel("//bar/baz:bat/boo");
    assertEquals("//bar/baz:bat/boo", label.toString());
  }

  @Test
  public void testInvalidRepo() throws Exception {
    try {
      Label.parseRepositoryLabel("foo//bar/baz:bat/boo");
      fail();
    } catch (SyntaxException e) {
      assertThat(e).hasMessage("invalid repository name 'foo': workspace name must start with '@'");
    }
  }
}
