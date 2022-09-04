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

package com.google.devtools.skylark.skylint;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.syntax.Expression;
import com.google.devtools.build.lib.syntax.ExpressionStatement;
import com.google.devtools.build.lib.syntax.Statement;
import com.google.devtools.build.lib.syntax.StringLiteral;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Utilities to extract and parse docstrings. */
public final class DocstringUtils {
  private DocstringUtils() {}

  /** Takes a function body and returns the docstring literal, if present. */
  @Nullable
  static StringLiteral extractDocstring(List<Statement> statements) {
    if (statements.isEmpty()) {
      return null;
    }
    Statement statement = statements.get(0);
    if (statement instanceof ExpressionStatement) {
      Expression expr = ((ExpressionStatement) statement).getExpression();
      if (expr instanceof StringLiteral) {
        return (StringLiteral) expr;
      }
    }
    return null;
  }

  /** Parses a docstring from a string literal and appends any new errors to the given list. */
  static DocstringInfo parseDocstring(StringLiteral docstring, List<DocstringParseError> errors) {
    int indentation = docstring.getLocation().getStartLineAndColumn().getColumn() - 1;
    return parseDocstring(docstring.getValue(), indentation, errors);
  }

  /**
   * Parses a docstring.
   *
   * <p>The format of the docstring is as follows
   *
   * <pre>{@code
   * """One-line summary: must be followed and may be preceded by a blank line.
   *
   * Optional additional description like this.
   *
   * If it's a function docstring and the function has more than one argument, the docstring has
   * to document these parameters as follows:
   *
   * Args:
   *   parameter1: description of the first parameter. Each parameter line
   *     should be indented by one, preferably two, spaces (as here).
   *   parameter2: description of the second
   *     parameter that spans two lines. Each additional line should have a
   *     hanging indentation of at least one, preferably two, additional spaces (as here).
   *   another_parameter (unused, mutable): a parameter may be followed
   *     by additional attributes in parentheses
   *
   * Returns:
   *   Description of the return value.
   *   Should be indented by at least one, preferably two spaces (as here)
   *   Can span multiple lines.
   * """
   * }</pre>
   *
   * @param docstring a docstring of the format described above
   * @param indentation the indentation level (number of spaces) of the docstring
   * @param parseErrors a list to which parsing error messages are written
   * @return the parsed docstring information
   */
  static DocstringInfo parseDocstring(
      String docstring, int indentation, List<DocstringParseError> parseErrors) {
    DocstringParser parser = new DocstringParser(docstring, indentation);
    DocstringInfo result = parser.parse();
    parseErrors.addAll(parser.errors);
    return result;
  }

  static class DocstringInfo {
    /** The one-line summary at the start of the docstring. */
    final String summary;
    /** Documentation of function parameters from the 'Args:' section. */
    final List<ParameterDoc> parameters;
    /** Documentation of the return value from the 'Returns:' section, or empty if there is none. */
    final String returns;
    /** Deprecation warning from the 'Deprecated:' section, or empty if there is none. */
    final String deprecated;
    /** Rest of the docstring that is not part of any of the special sections above. */
    final String longDescription;

    public DocstringInfo(
        String summary,
        List<ParameterDoc> parameters,
        String returns,
        String deprecated,
        String longDescription) {
      this.summary = summary;
      this.parameters = ImmutableList.copyOf(parameters);
      this.returns = returns;
      this.deprecated = deprecated;
      this.longDescription = longDescription;
    }

    public boolean isSingleLineDocstring() {
      return longDescription.isEmpty() && parameters.isEmpty() && returns.isEmpty();
    }
  }

  static class ParameterDoc {
    final String parameterName;
    final List<String> attributes; // e.g. a type annotation, "unused", "mutable"
    final String description;

    public ParameterDoc(String parameterName, List<String> attributes, String description) {
      this.parameterName = parameterName;
      this.attributes = ImmutableList.copyOf(attributes);
      this.description = description;
    }
  }

  private static class DocstringParser {
    private final String docstring;
    /** Start offset of the current line. */
    private int startOfLineOffset = 0;
    /** End offset of the current line. */
    private int endOfLineOffset = -1;
    /** Current line number within the docstring. */
    private int lineNumber = 0;
    /**
     * The indentation of the doctring literal in the source file.
     *
     * <p>Every line except the first one must be indented by at least that many spaces.
     */
    private int baselineIndentation = 0;
    /** Whether there was a blank line before the current line. */
    private boolean blankLineBefore = false;
    /** The complete current line in the docstring, including all indentation. */
    private String originalLine = "";
    /**
     * The current line in the docstring with the baseline indentation removed.
     *
     * <p>If the indentation of {@link #originalLine} is less than the expected {@link
     * #baselineIndentation}, only the existing indentation is stripped; none of the remaining
     * characters are cut off.
     */
    private String line = "";
    /** Errors that occurred so far. */
    private final List<DocstringParseError> errors = new ArrayList<>();

    DocstringParser(String docstring, int indentation) {
      this.docstring = docstring;
      nextLine();
      // the indentation is only relevant for the following lines, not the first one:
      this.baselineIndentation = indentation;
    }

    /**
     * Move on to the next line and update the parser's internal state accordingly.
     *
     * @return whether there are lines remaining to be parsed
     */
    boolean nextLine() {
      if (startOfLineOffset >= docstring.length()) {
        return false;
      }
      blankLineBefore = line.isEmpty();
      lineNumber++;
      startOfLineOffset = endOfLineOffset + 1;
      if (startOfLineOffset >= docstring.length()) {
        line = "";
        return false;
      }
      endOfLineOffset = docstring.indexOf('\n', startOfLineOffset);
      if (endOfLineOffset < 0) {
        endOfLineOffset = docstring.length();
      }
      originalLine = docstring.substring(startOfLineOffset, endOfLineOffset);
      line = originalLine;
      int indentation = getIndentation(line);
      if (!line.isEmpty()) {
        if (indentation < baselineIndentation) {
          error(
              "line indented too little (here: "
                  + indentation
                  + " spaces; expected: "
                  + baselineIndentation
                  + " spaces)");
          startOfLineOffset += indentation;
        } else {
          startOfLineOffset += baselineIndentation;
        }
      }
      line = docstring.substring(startOfLineOffset, endOfLineOffset);
      return true;
    }

    private boolean eof() {
      return startOfLineOffset >= docstring.length();
    }

    private static int getIndentation(String line) {
      int index = 0;
      while (index < line.length() && line.charAt(index) == ' ') {
        index++;
      }
      return index;
    }

    void error(String message) {
      errors.add(new DocstringParseError(message, lineNumber, originalLine));
    }

    DocstringInfo parse() {
      String summary = line;
      String nonStandardDeprecation = checkForNonStandardDeprecation(line);
      if (!nextLine()) {
        return new DocstringInfo(summary, Collections.emptyList(), "", nonStandardDeprecation, "");
      }
      if (!line.isEmpty()) {
        error("the one-line summary should be followed by a blank line");
      } else {
        nextLine();
      }
      List<String> longDescriptionLines = new ArrayList<>();
      List<ParameterDoc> params = new ArrayList<>();
      String returns = "";
      String deprecated = "";
      while (!eof()) {
        switch (line) {
          case "Args:":
            if (!blankLineBefore) {
              error("section should be preceded by a blank line");
            }
            if (!params.isEmpty()) {
              error("parameters were already documented above");
            }
            if (!returns.isEmpty()) {
              error("parameters should be documented before the return value");
            }
            params.addAll(parseParameters());
            break;
          case "Returns:":
            if (!blankLineBefore) {
              error("section should be preceded by a blank line");
            }
            if (!returns.isEmpty()) {
              error("return value was already documented above");
            }
            returns = parseSectionAfterHeading();
            break;
          case "Deprecated:":
            if (!blankLineBefore) {
              error("section should be preceded by a blank line");
            }
            if (!deprecated.isEmpty()) {
              error("deprecation message was already documented above");
            }
            deprecated = parseSectionAfterHeading();
            break;
          default:
            if (deprecated.isEmpty() && nonStandardDeprecation.isEmpty()) {
              nonStandardDeprecation = checkForNonStandardDeprecation(line);
            }
            longDescriptionLines.add(line);
            nextLine();
        }
      }
      if (deprecated.isEmpty()) {
        deprecated = nonStandardDeprecation;
      }
      return new DocstringInfo(
          summary, params, returns, deprecated, String.join("\n", longDescriptionLines));
    }

    private String checkForNonStandardDeprecation(String line) {
      if (line.toLowerCase().startsWith("deprecated:") || line.contains("DEPRECATED")) {
        error("use a 'Deprecated:' section for deprecations, similar to a 'Returns:' section");
        return line;
      }
      return "";
    }

    private static final Pattern paramLineMatcher =
        Pattern.compile(
            "\\s*(?<name>[*\\w]+)( \\(\\s*(?<attributes>.*)\\s*\\))?: (?<description>.*)");

    private static final Pattern attributesSeparator = Pattern.compile("\\s*,\\s*");

    private List<ParameterDoc> parseParameters() {
      nextLine();
      List<ParameterDoc> params = new ArrayList<>();
      int expectedParamLineIndentation = -1;
      while (!eof()) {
        if (line.isEmpty()) {
          nextLine();
          continue;
        }
        int actualIndentation = getIndentation(line);
        if (actualIndentation == 0) {
          if (!blankLineBefore) {
            error("end of 'Args' section without blank line");
          }
          break;
        }
        String trimmedLine;
        if (expectedParamLineIndentation == -1) {
          expectedParamLineIndentation = actualIndentation;
        }
        if (expectedParamLineIndentation != actualIndentation) {
          error(
              "inconsistent indentation of parameter lines (before: "
                  + expectedParamLineIndentation
                  + "; here: "
                  + actualIndentation
                  + " spaces)");
        }
        trimmedLine = line.substring(actualIndentation);
        Matcher matcher = paramLineMatcher.matcher(trimmedLine);
        if (!matcher.matches()) {
          error("invalid parameter documentation");
          nextLine();
          continue;
        }
        String parameterName = Preconditions.checkNotNull(matcher.group("name"));
        String attributesString = matcher.group("attributes");
        StringBuilder description = new StringBuilder(matcher.group("description"));
        List<String> attributes =
            attributesString == null
                ? Collections.emptyList()
                : Arrays.asList(attributesSeparator.split(attributesString));
        parseContinuedParamDescription(actualIndentation, description);
        params.add(new ParameterDoc(parameterName, attributes, description.toString().trim()));
      }
      return params;
    }

    /** Parses additional lines that can come after "param: foo" in an 'Args' section. */
    private void parseContinuedParamDescription(
        int baselineIndentation, StringBuilder description) {
      while (nextLine()) {
        if (line.isEmpty()) {
          description.append('\n');
          continue;
        }
        if (getIndentation(line) <= baselineIndentation) {
          break;
        }
        String trimmedLine = line.substring(baselineIndentation);
        description.append('\n');
        description.append(trimmedLine);
      }
    }

    private String parseSectionAfterHeading() {
      nextLine();
      StringBuilder returns = new StringBuilder();
      boolean firstLine = true;
      while (!eof()) {
        String trimmedLine;
        if (line.isEmpty()) {
          trimmedLine = line;
        } else if (getIndentation(line) == 0) {
          if (!blankLineBefore) {
            error("end of section without blank line");
          }
          break;
        } else {
          if (getIndentation(line) < 2) {
            error(
                "text in a section has to be indented by two spaces"
                    + " (relative to the left margin of the docstring)");
            trimmedLine = line.substring(getIndentation(line));
          } else {
            trimmedLine = line.substring(2);
          }
        }
        if (!firstLine) {
          returns.append('\n');
        }
        returns.append(trimmedLine);
        nextLine();
        firstLine = false;
      }
      return returns.toString().trim();
    }
  }

  static class DocstringParseError {
    final String message;
    final int lineNumber;
    final String line;

    public DocstringParseError(String message, int lineNumber, String line) {
      this.message = message;
      this.lineNumber = lineNumber;
      this.line = line;
    }

    @Override
    public String toString() {
      return lineNumber + ": " + message;
    }
  }
}
