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

package com.google.devtools.build.skydoc.rendering;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.skydoc.rendering.proto.StardocOutputProtos.AspectInfo;
import com.google.devtools.build.skydoc.rendering.proto.StardocOutputProtos.AttributeInfo;
import com.google.devtools.build.skydoc.rendering.proto.StardocOutputProtos.AttributeType;
import com.google.devtools.build.skydoc.rendering.proto.StardocOutputProtos.FunctionParamInfo;
import com.google.devtools.build.skydoc.rendering.proto.StardocOutputProtos.ProviderInfo;
import com.google.devtools.build.skydoc.rendering.proto.StardocOutputProtos.ProviderNameGroup;
import com.google.devtools.build.skydoc.rendering.proto.StardocOutputProtos.RuleInfo;
import com.google.devtools.build.skydoc.rendering.proto.StardocOutputProtos.StarlarkFunctionInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains a number of utility methods for markdown rendering.
 */
public final class MarkdownUtil {
  private static final int MAX_LINE_LENGTH = 100;

  /**
   * Return a string that formats the input string so it is displayable in a markdown table cell.
   * This performs the following operations:
   *
   * <ul>
   *   <li>Trims the string of leading/trailing whitespace.
   *   <li>Transforms the string using {@link #htmlEscape}.
   *   <li>Transforms multline code (```) tags into preformatted code HTML tags.
   *   <li>Transforms single-tick code (`) tags into code HTML tags.
   *   <li>Transforms newline characters into line break HTML tags.
   * </ul>
   */
  public String markdownCellFormat(String docString) {
    String resultString = htmlEscape(docString.trim());

    resultString = replaceWithTag(resultString, "```", "<pre><code>", "</code></pre>");
    resultString = replaceWithTag(resultString, "`", "<code>", "</code>");

    return resultString.replace("\n", "<br>");
  }

  private static String replaceWithTag(
      String wholeString, String stringToReplace, String openTag, String closeTag) {
    String remainingString = wholeString;
    StringBuilder resultString = new StringBuilder();

    boolean openTagNext = true;
    int index = remainingString.indexOf(stringToReplace);
    while (index > -1) {
      resultString.append(remainingString, 0, index);
      resultString.append(openTagNext ? openTag : closeTag);
      openTagNext = !openTagNext;
      remainingString = remainingString.substring(index + stringToReplace.length());
      index = remainingString.indexOf(stringToReplace);
    }
    resultString.append(remainingString);
    return resultString.toString();
  }

  /**
   * Return a string that escapes angle brackets for HTML.
   *
   * <p>For example: 'Information with <brackets>.' becomes 'Information with &lt;brackets&gt;'.
   */
  public String htmlEscape(String docString) {
    return docString.replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * Return a string representing the rule summary for the given rule with the given name.
   *
   * For example: 'my_rule(foo, bar)'.
   * The summary will contain hyperlinks for each attribute.
   */
  @SuppressWarnings("unused") // Used by markdown template.
  public String ruleSummary(String ruleName, RuleInfo ruleInfo) {
    List<String> attributeNames =
        ruleInfo.getAttributeList().stream()
            .map(attr -> attr.getName())
            .collect(Collectors.toList());
    return summary(ruleName, attributeNames);
  }

  /**
   * Return a string representing the summary for the given provider with the given name.
   *
   * For example: 'MyInfo(foo, bar)'.
   * The summary will contain hyperlinks for each field.
   */
  @SuppressWarnings("unused") // Used by markdown template.
  public String providerSummary(String providerName, ProviderInfo providerInfo) {
    List<String> fieldNames =
        providerInfo.getFieldInfoList().stream()
            .map(field -> field.getName())
            .collect(Collectors.toList());
    return summary(providerName, fieldNames);
  }

  /**
   * Return a string representing the aspect summary for the given aspect with the given name.
   *
   * <p>For example: 'my_aspect(foo, bar)'. The summary will contain hyperlinks for each attribute.
   */
  @SuppressWarnings("unused") // Used by markdown template.
  public String aspectSummary(String aspectName, AspectInfo aspectInfo) {
    List<String> attributeNames =
        aspectInfo.getAttributeList().stream()
            .map(attr -> attr.getName())
            .collect(Collectors.toList());
    return summary(aspectName, attributeNames);
  }

  /**
   * Return a string representing the summary for the given user-defined function.
   *
   * <p>For example: 'my_func(foo, bar)'. The summary will contain hyperlinks for each parameter.
   */
  @SuppressWarnings("unused") // Used by markdown template.
  public String funcSummary(StarlarkFunctionInfo funcInfo) {
    List<String> paramNames =
        funcInfo.getParameterList().stream()
            .map(param -> param.getName())
            .collect(Collectors.toList());
    return summary(funcInfo.getFunctionName(), paramNames);
  }

  private static String summary(String functionName, List<String> paramNames) {
    List<List<String>> paramLines = wrap(functionName, paramNames, MAX_LINE_LENGTH);
    List<String> paramLinksLines = new ArrayList<>();
    for (List<String> params : paramLines) {
      String paramLinksLine =
          params.stream()
              .map(param -> String.format("<a href=\"#%s-%s\">%s</a>", functionName, param, param))
              .collect(Collectors.joining(", "));
      paramLinksLines.add(paramLinksLine);
    }
    String paramList =
        Joiner.on(String.format(",\n%s", Strings.repeat(" ", functionName.length() + 1)))
            .join(paramLinksLines);
    return String.format("%s(%s)", functionName, paramList);
  }

  /**
   * Wraps the given function parameter names to be able to construct a function summary that stays
   * within the provided line length limit.
   *
   * @param functionName the function name.
   * @param paramNames the function parameter names.
   * @param maxLineLength the maximal line length.
   * @return the lines with the wrapped parameter names.
   */
  private static List<List<String>> wrap(
      String functionName, List<String> paramNames, int maxLineLength) {
    List<List<String>> paramLines = new ArrayList<>();
    ImmutableList.Builder<String> linesBuilder = new ImmutableList.Builder<>();
    int leading = functionName.length();
    int length = leading;
    int punctuation = 2; // cater for left parenthesis/space before and comma after parameter
    for (String paramName : paramNames) {
      length += paramName.length() + punctuation;
      if (length > maxLineLength) {
        paramLines.add(linesBuilder.build());
        length = leading + paramName.length();
        linesBuilder = new ImmutableList.Builder<>();
      }
      linesBuilder.add(paramName);
    }
    paramLines.add(linesBuilder.build());
    return paramLines;
  }

  /**
   * Returns a string describing the given attribute's type. The description consists of a hyperlink
   * if there is a relevant hyperlink to Bazel documentation available.
   */
  public String attributeTypeString(AttributeInfo attrInfo) {
    String typeLink;
    switch (attrInfo.getType()) {
      case LABEL:
      case LABEL_LIST:
      case OUTPUT:
        typeLink = "https://bazel.build/docs/build-ref.html#labels";
        break;
      case NAME:
        typeLink = "https://bazel.build/docs/build-ref.html#name";
        break;
      case STRING_DICT:
      case STRING_LIST_DICT:
      case LABEL_STRING_DICT:
        typeLink = "https://bazel.build/docs/skylark/lib/dict.html";
        break;
      default:
        typeLink = null;
        break;
    }
    if (typeLink == null) {
      return attributeTypeDescription(attrInfo.getType());
    } else {
      return String.format(
          "<a href=\"%s\">%s</a>", typeLink, attributeTypeDescription(attrInfo.getType()));
    }
  }

  public String mandatoryString(AttributeInfo attrInfo) {
    return attrInfo.getMandatory() ? "required" : "optional";
  }

  /**
   * Returns "required" if providing a value for this parameter is mandatory. Otherwise, returns
   * "optional".
   */
  public String mandatoryString(FunctionParamInfo paramInfo) {
    return paramInfo.getMandatory() ? "required" : "optional";
  }

  /**
   * Return a string explaining what providers an attribute requires. Adds hyperlinks to providers.
   */
  public String attributeProviders(AttributeInfo attributeInfo) {
    List<ProviderNameGroup> providerNames = attributeInfo.getProviderNameGroupList();
    List<String> finalProviderNames = new ArrayList<>();
    for (ProviderNameGroup providerNameList : providerNames) {
      List<String> providers = providerNameList.getProviderNameList();
      finalProviderNames.add(String.format(Joiner.on(", ").join(providers)));
    }
    return String.format(Joiner.on("; or ").join(finalProviderNames));
  }

  private static String attributeTypeDescription(AttributeType attributeType) {
    switch (attributeType) {
      case NAME:
        return "Name";
      case INT:
        return "Integer";
      case LABEL:
        return "Label";
      case STRING:
        return "String";
      case STRING_LIST:
        return "List of strings";
      case INT_LIST:
        return "List of integers";
      case LABEL_LIST:
        return "List of labels";
      case BOOLEAN:
        return "Boolean";
      case LABEL_STRING_DICT:
        return "Dictionary: Label -> String";
      case STRING_DICT:
        return "Dictionary: String -> String";
      case STRING_LIST_DICT:
        return "Dictionary: String -> List of strings";
      case OUTPUT:
        return "Label";
      case OUTPUT_LIST:
        return "List of labels";
      case UNKNOWN:
      case UNRECOGNIZED:
        throw new IllegalArgumentException("Unhandled type " + attributeType);
    }
    throw new IllegalArgumentException("Unhandled type " + attributeType);
  }
}
