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
package com.google.devtools.build.lib.profiler.output;

import com.google.common.base.Joiner;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ListMultimap;
import com.google.devtools.build.lib.profiler.ProfileInfo.Task;
import com.google.devtools.build.lib.profiler.statistics.SkylarkStatistics;
import com.google.devtools.build.lib.profiler.statistics.TasksStatistics;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

/**
 * Formats {@link SkylarkStatistics} as a HTML tables and histogram charts.
 */
public final class SkylarkHtml extends HtmlPrinter {

  /**
   * How many characters from the end of the location of a Skylark function to display.
   */
  private static final int NUM_LOCATION_CHARS_UNABBREVIATED = 40;

  private final SkylarkStatistics stats;

  public SkylarkHtml(PrintStream out, SkylarkStatistics stats) {
    super(out);
    this.stats = stats;
  }

  /**
   * Prints all CSS definitions and JavaScript code. May be a large amount of output.
   */
  void printHtmlHead() {
    lnOpen("style", "type", "text/css", "<!--");
    lnPrint("div.skylark-histogram {");
    lnPrint("  width: 95%; margin: 0 auto; display: none;");
    lnPrint("}");
    lnPrint("div.skylark-chart {");
    lnPrint("  width: 100%; height: 200px; margin: 0 auto 2em;");
    lnPrint("}");
    lnPrint("div.skylark-table {");
    lnPrint("  width: 95%; margin: 0 auto;");
    lnPrint("}");
    lnPrint("-->");
    close(); // style

    lnElement("script", "type", "text/javascript", "src", "https://www.google.com/jsapi");
    lnOpen("script", "type", "text/javascript");
    lnPrint("google.load(\"visualization\", \"1.1\", {packages:[\"corechart\",\"table\"]});");
    lnPrint("google.setOnLoadCallback(drawVisualization);");

    String dataVar = "data";
    String tableVar = dataVar + "Table";
    lnPrintf("var %s = {};\n", dataVar);
    lnPrintf("var %s = {};\n", tableVar);
    lnPrint("var histogramData;");

    lnPrint("function drawVisualization() {");
    down();
    printStatsJs(
        stats.getUserFunctionStats(), "user", dataVar, tableVar, stats.getUserTotalNanos());
    printStatsJs(
        stats.getBuiltinFunctionStats(),
        "builtin",
        dataVar,
        tableVar,
        stats.getBuiltinTotalNanos());

    printHistogramData();

    lnPrint("document.querySelector('#user-close').onclick = function() {");
    lnPrint("  document.querySelector('#user-histogram').style.display = 'none';");
    lnPrint("};");
    lnPrint("document.querySelector('#builtin-close').onclick = function() {");
    lnPrint("  document.querySelector('#builtin-histogram').style.display = 'none';");
    lnPrint("};");
    up();
    lnPrint("};");

    lnPrint("var options = {");
    down();
    lnPrint("isStacked: true,");
    lnPrint("legend: { position: 'none' },");
    lnPrint("hAxis: { },");
    lnPrint("histogram: { lastBucketPercentile: 5 },");
    lnPrint("vAxis: { title: '# calls', viewWindowMode: 'pretty', gridlines: { count: -1 } }");
    up();
    lnPrint("};");

    lnPrint("function selectHandler(category) {");
    down();
    lnPrint("return function() {");
    down();
    printf("var selection = %s[category].getSelection();", tableVar);
    lnPrint("if (selection.length < 1) return;");
    lnPrint("var item = selection[0];");
    lnPrintf("var loc = %s[category].getValue(item.row, 0);", dataVar);
    lnPrintf("var func = %s[category].getValue(item.row, 1);", dataVar);
    lnPrint("var key = loc + '#' + func;");
    lnPrint("var histData = histogramData[category][key];");
    lnPrint("var fnOptions = JSON.parse(JSON.stringify(options));");
    lnPrint("fnOptions.title = loc + ' - ' + func;");
    lnPrint("var chartDiv = document.getElementById(category+'-chart');");
    lnPrint("var chart = new google.visualization.Histogram(chartDiv);");
    lnPrint("var histogramDiv = document.getElementById(category+'-histogram');");
    lnPrint("histogramDiv.style.display = 'block';");
    lnPrint("chart.draw(histData, fnOptions);");
    up();
    lnPrint("}");
    up();
    lnPrint("};");
    lnClose(); // script
  }

  private void printHistogramData() {
    lnPrint("histogramData = {");
    down();
    printHistogramData(stats.getBuiltinFunctionTasks(), "builtin");
    printHistogramData(stats.getUserFunctionTasks(), "user");
    up();
    lnPrint("}");
  }

  private void printHistogramData(ListMultimap<String, Task> tasks, String category) {
    lnPrintf("'%s': {", category);
    down();
    for (String function : tasks.keySet()) {
      lnPrintf("'%s': google.visualization.arrayToDataTable(", function);
      lnPrint("[['duration']");
      for (Task task : tasks.get(function)) {
        printf(",[%f]", task.durationNanos / 1000000.);
      }
      lnPrint("], false),");
    }
    up();
    lnPrint("},");
  }

  private void printStatsJs(
      List<TasksStatistics> statsList,
      String category,
      String dataVar,
      String tableVar,
      long totalNanos) {
    String tmpVar = category + dataVar;
    lnPrintf("var statsDiv = document.getElementById('%s_function_stats');", category);
    if (statsList.isEmpty()) {
      lnPrint(
          "statsDiv.innerHTML = '<i>No relevant function calls to display. Some minor"
              + " builtin functions may have been ignored because their names could not be used"
              + " as variables in JavaScript.</i>'");
    } else {
      lnPrintf("var %s = new google.visualization.DataTable();", tmpVar);
      lnPrintf("%s.addColumn('string', 'Location');", tmpVar);
      lnPrintf("%s.addColumn('string', 'Function');", tmpVar);
      lnPrintf("%s.addColumn('number', 'count');", tmpVar);
      lnPrintf("%s.addColumn('number', 'min (ms)');", tmpVar);
      lnPrintf("%s.addColumn('number', 'mean (ms)');", tmpVar);
      lnPrintf("%s.addColumn('number', 'median (ms)');", tmpVar);
      lnPrintf("%s.addColumn('number', 'max (ms)');", tmpVar);
      lnPrintf("%s.addColumn('number', 'std dev (ms)');", tmpVar);
      lnPrintf("%s.addColumn('number', 'mean self (ms)');", tmpVar);
      lnPrintf("%s.addColumn('number', 'self (ms)');", tmpVar);
      lnPrintf("%s.addColumn('number', 'self (%%)');", tmpVar);
      lnPrintf("%s.addColumn('number', 'total (ms)');", tmpVar);
      lnPrintf("%s.addColumn('number', 'relative (%%)');", tmpVar);
      lnPrintf("%s.addRows([", tmpVar);
      down();
      for (TasksStatistics stats : statsList) {
        double relativeTotal = (double) stats.totalNanos / totalNanos;
        double relativeSelf = (double) stats.selfNanos / stats.totalNanos;
        String[] split = stats.name.split("#");
        String location = split[0];
        String name = split[1];
        lnPrintf("[{v:'%s', f:'%s'}, ", location, abbreviatePath(location));
        printf("'%s', ", name);
        printf("%d, ", stats.count);
        printf("%.3f, ", stats.minimumMillis());
        printf("%.3f, ", stats.meanMillis());
        printf("%.3f, ", stats.medianMillis());
        printf("%.3f, ", stats.maximumMillis());
        printf("%.3f, ", stats.standardDeviationMillis);
        printf("%.3f, ", stats.selfMeanMillis());
        printf("%.3f, ", stats.selfMillis());
        printf("{v:%.4f, f:'%.3f %%'}, ", relativeSelf, relativeSelf * 100);
        printf("%.3f, ", stats.totalMillis());
        printf("{v:%.4f, f:'%.3f %%'}],", relativeTotal, relativeTotal * 100);
      }
      lnPrint("]);");
      up();
      lnPrintf("%s.%s = %s;", dataVar, category, tmpVar);
      lnPrintf("%s.%s = new google.visualization.Table(statsDiv);", tableVar, category);
      lnPrintf(
          "google.visualization.events.addListener(%s.%s, 'select', selectHandler('%s'));",
          tableVar,
          category,
          category);
      lnPrintf(
          "%s.%s.draw(%s.%s, {showRowNumber: true, width: '100%%', height: '100%%'});",
          tableVar,
          category,
          dataVar,
          category);
    }
  }

  /**
   * Prints two sections for histograms and tables of statistics for user-defined and built-in
   * Skylark functions.
   */
  void printHtmlBody() {
    lnPrint("<a name='skylark_stats'/>");
    lnElement("h3", "Skylark Statistics");
    lnElement("h4", "User-Defined function execution time");
    lnOpen("div", "class", "skylark-histogram", "id", "user-histogram");
    lnElement("div", "class", "skylark-chart", "id", "user-chart");
    lnElement("button", "id", "user-close", "Hide histogram");
    lnClose(); // div user-histogram
    lnElement("div", "class", "skylark-table", "id", "user_function_stats");

    lnElement("h4", "Builtin function execution time");
    lnOpen("div", "class", "skylark-histogram", "id", "builtin-histogram");
    lnElement("div", "class", "skylark-chart", "id", "builtin-chart");
    lnElement("button", "id", "builtin-close", "Hide histogram");
    lnClose(); // div builtin-histogram
    lnElement("div", "class", "skylark-table", "id", "builtin_function_stats");
  }

  /**
   * Computes a string keeping the structure of the input but reducing the amount of characters on
   * elements at the front if necessary.
   *
   * <p>Reduces the length of function location strings by keeping at least the last element fully
   * intact and at most {@link #NUM_LOCATION_CHARS_UNABBREVIATED} from other
   * elements from the end. Elements before are abbreviated with their first two characters.
   *
   * <p>Example:
   * "//source/tree/with/very/descriptive/and/long/hierarchy/of/directories/longfilename.bzl:42"
   * becomes: "//so/tr/wi/ve/de/an/lo/hierarch/of/directories/longfilename.bzl:42"
   *
   * <p>There is no fixed length to the result as the last element is kept and the location may
   * have many elements.
   *
   * @param location Either a sequence of path elements separated by
   *     {@link StandardSystemProperty#FILE_SEPARATOR} and preceded by some root element
   *     (e.g. "/", "C:\") or path elements separated by "." and having no root element.
   */
  private String abbreviatePath(String location) {
    String[] elements;
    int lowestAbbreviateIndex;
    String root;
    String separator = StandardSystemProperty.FILE_SEPARATOR.value();
    if (location.contains(separator)) {
      elements = location.split(separator);
      // must take care to preserve file system roots (e.g. "/", "C:\"), keep separate
      lowestAbbreviateIndex = 1;
      root = location.substring(0, location.indexOf(separator) + 1);
    } else {
      // must be java class name for a builtin function
      elements = location.split("\\.");
      lowestAbbreviateIndex = 0;
      root = "";
      separator = ".";
    }

    String last = elements[elements.length - 1];
    int remaining = NUM_LOCATION_CHARS_UNABBREVIATED - last.length();
    // start from the next to last element of the location and add until "remaining" many
    // chars added, abbreviate rest with first 2 characters
    for (int index = elements.length - 2; index >= lowestAbbreviateIndex; index--) {
      String element = elements[index];
      if (remaining > 0) {
        int length = Math.min(remaining, element.length());
        element = element.substring(0, length);
        remaining -= length;
      } else {
        element = element.substring(0, Math.min(2, element.length()));
      }
      elements[index] = element;
    }
    return root + Joiner.on(separator).join(Arrays.asList(elements).subList(1, elements.length));
  }
}


