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

package com.google.devtools.lcovmerger;

import static com.google.devtools.lcovmerger.LcovConstants.BA_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.BRDA_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.BRF_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.BRH_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.DA_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.END_OF_RECORD_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.FNDA_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.FNF_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.FNH_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.FN_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.LCOV_DELIMITER;
import static com.google.devtools.lcovmerger.LcovConstants.LF_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.LH_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.SF_MARKER;
import static com.google.devtools.lcovmerger.LcovConstants.TAKEN;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A parser for the lcov tracefile format used by geninfo. See
 * <a href="http://ltp.sourceforge.net/coverage/lcov/geninfo.1.php"> lcov documentation</a>
 */
class LcovParser {

  private static final Logger logger = Logger.getLogger(LcovParser.class.getName());
  private SourceFileCoverage currentSourceFileCoverage;
  private final InputStream tracefileStream;

  private LcovParser(InputStream tracefileStream) {
    this.tracefileStream = tracefileStream;
  }

  /**
   * Returns a list of the source files found in the given tracefile.
   */
  public static List<SourceFileCoverage> parse(InputStream tracefileStream) throws IOException {
    LcovParser lcovParser = new LcovParser(tracefileStream);
    return lcovParser.parse();
  }

  /**
   * Reads the tracefile line by line and creates a SourceFileCoverage object
   * for each section of the file between a SF:<source file> line and an
   * end_of_record line.
   *
   * @return a list of each source file path found in the tracefile
   */
  private List<SourceFileCoverage> parse() throws IOException {
    List<SourceFileCoverage> allSourceFiles = new ArrayList<>();
    try (BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(tracefileStream, UTF_8))) {
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        parseLine(line, allSourceFiles);
      }
      bufferedReader.close();
    }
    return allSourceFiles;
  }

  /**
   * Merges {@code currentSourceFileCoverage} into {@code allSourceFilesCoverageData} and resets
   * {@code currentSourceFileCoverage} to null.
   */
  private void reset(List<SourceFileCoverage> allSourceFiles) {
    allSourceFiles.add(currentSourceFileCoverage);
    currentSourceFileCoverage = null;
  }

  /*
   * Reads the line and redirects the parsing to the corresponding {@code parseXLine} method. Every
   * {@code parseXLine} methods fills in data to {@code currentSourceFileCoverage} accordingly.
   */
  private boolean parseLine(String line, List<SourceFileCoverage> allSourceFiles) {
    if (line.startsWith(SF_MARKER)) {
      return parseSFLine(line);
    }
    // currentSourceFileCoverage should be null only before calling an SF line, otherwise
    // the object should have been created in parseSFLine. If currentSourceFileCoverage is null
    // here it means the parser arrived in an invalid state.
    if (currentSourceFileCoverage == null) {
      return false;
    }
    if (line.startsWith(FN_MARKER)) {
      return parseFNLine(line);
    }
    if (line.startsWith(FNDA_MARKER)) {
      return parseFNDALine(line);
    }
    if (line.startsWith(FNF_MARKER)) {
      return parseFNFLine(line);
    }
    if (line.startsWith(FNH_MARKER)) {
      return parseFNHLine(line);
    }
    if (line.startsWith(BRDA_MARKER)) {
      return parseBRDALine(line);
    }
    if (line.startsWith(BA_MARKER)) {
      return parseBALine(line);
    }
    if (line.startsWith(BRF_MARKER)) {
      return parseBRFLine(line);
    }
    if (line.startsWith(BRH_MARKER)) {
      return parseBRHLine(line);
    }
    if (line.startsWith(DA_MARKER)) {
      return parseDALine(line);
    }
    if (line.startsWith(LH_MARKER)) {
      return parseLHLine(line);
    }
    if (line.startsWith(LF_MARKER)) {
      return parseLFLine(line);
    }
    if (line.equals(END_OF_RECORD_MARKER)) {
      reset(allSourceFiles);
      return true;
    }
    logger.log(Level.WARNING, "Tracefile includes invalid line: " + line);
    return false;
  }

  // SF:<path to source file name>
  private boolean parseSFLine(String line) {
    if (currentSourceFileCoverage != null) {
      logger.log(Level.WARNING, "Tracefile doesn't have SF:<source file> line before" + line);
      return false;
    }
    String sourcefile = line.substring(SF_MARKER.length());
    if (sourcefile.isEmpty()) {
      logger.log(Level.WARNING, "Tracefile doesn't contain source file name on line: " + line);
      return false;
    }
    currentSourceFileCoverage = new SourceFileCoverage(sourcefile);
    return true;
  }

  // FN:<line number of function start>,<function name>
  private boolean parseFNLine(String line) {
    String lineContent = line.substring(FN_MARKER.length());
    String[] funcData = lineContent.split(LCOV_DELIMITER, -1);
    if (funcData.length != 2 || funcData[0].isEmpty() || funcData[1].isEmpty()) {
      logger.log(Level.WARNING, "Tracefile contains invalid FN line " + line);
      return false;
    }
    try {
      int lineNrFunctionStart = Integer.parseInt(funcData[0]);
      String functionName = funcData[1];
      currentSourceFileCoverage.addLineNumber(functionName, lineNrFunctionStart);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING, "Tracefile contains invalid line number on FN line " + line);
      return false;
    }
    return true;
  }

  // FNDA:<execution count>,<function name>
  private boolean parseFNDALine(String line) {
    String lineContent = line.substring(FNDA_MARKER.length());
    String[] funcData = lineContent.split(LCOV_DELIMITER, -1);
    if (funcData.length != 2 || funcData[0].isEmpty() || funcData[1].isEmpty()) {
      logger.log(Level.WARNING, "Tracefile contains invalid FNDA line " + line);
      return false;
    }
    try {
      int executionCount = Integer.parseInt(funcData[0]);
      String functionName = funcData[1];
      currentSourceFileCoverage.addFunctionExecution(functionName, executionCount);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING, "Tracefile contains invalid execution count on FN line " + line);
      return false;
    }
    return true;
  }

  // FNF:<number of functions found>
  private boolean parseFNFLine(String line) {
    String lineContent = line.substring(FNF_MARKER.length());
    if (lineContent.isEmpty()) {
      logger.log(Level.WARNING, "Tracefile contains invalid FNF line " + line);
      return false;
    }
    try {
      int nrFunctionsFound = Integer.parseInt(lineContent);
      currentSourceFileCoverage.nrFunctionsFound(nrFunctionsFound);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING,
          "Tracefile contains invalid number of functions on FNF line " + line);
      return false;
    }
    return true;
  }

  // FNH:<number of function hit>
  private boolean parseFNHLine(String line) {
    String lineContent = line.substring(FNH_MARKER.length());
    if (lineContent.isEmpty()) {
      logger.log(Level.WARNING, "Tracefile contains invalid FNH line " + line);
      return false;
    }
    try {
      int nrFunctionsHit = Integer.parseInt(lineContent);
      currentSourceFileCoverage.nrFunctionsHit(nrFunctionsHit);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING,
          "Tracefile contains invalid number of functions hit on FNH line " + line);
      return false;
    }
    return true;
  }

  // BA:<line number>,<taken>
  private boolean parseBALine(String line) {
    String lineContent = line.substring(BA_MARKER.length());
    String[] lineData = lineContent.split(LCOV_DELIMITER, -1);
    if (lineData.length != 2) {
      logger.log(Level.WARNING, "Tracefile contains invalid BRDA line " + line);
      return false;
    }
    for (String data : lineData) {
      if (data.isEmpty()) {
        logger.log(Level.WARNING, "Tracefile contains invalid BRDA line " + line);
        return false;
      }
    }
    try {
      int lineNumber = Integer.parseInt(lineData[0]);
      int taken = Integer.parseInt(lineData[1]);

      boolean wasExecuted = false;
      if (taken == 1 || taken == 2) {
        wasExecuted = true;
      }
      BranchCoverage branchCoverage =
          BranchCoverage.create(lineNumber, -1, -1, wasExecuted, taken);

      currentSourceFileCoverage.addBranch(lineNumber, branchCoverage);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING, "Tracefile contains an invalid number BA line " + line);
      return false;
    }
    return true;
  }

  // BRDA:<line number>,<block number>,<branch number>,<taken>
  private boolean parseBRDALine(String line) {
    String lineContent = line.substring(BRDA_MARKER.length());
    String[] lineData = lineContent.split(LCOV_DELIMITER, -1);
    if (lineData.length != 4) {
      logger.log(Level.WARNING, "Tracefile contains invalid BRDA line " + line);
      return false;
    }
    for (String data : lineData) {
      if (data.isEmpty()) {
        logger.log(Level.WARNING, "Tracefile contains invalid BRDA line " + line);
        return false;
      }
    }
    try {
      int lineNumber = Integer.parseInt(lineData[0]);
      int blockNumber = Integer.parseInt(lineData[1]);
      int branchNumber = Integer.parseInt(lineData[2]);
      String taken = lineData[3];

      boolean wasExecuted = false;
      int executionCount = 0;
      if (taken.equals(TAKEN)) {
        executionCount = Integer.parseInt(taken);
        wasExecuted = true;
      }
      BranchCoverage branchCoverage =
          BranchCoverage.create(lineNumber, blockNumber, branchNumber, wasExecuted, executionCount);

      currentSourceFileCoverage.addBranch(lineNumber, branchCoverage);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING, "Tracefile contains an invalid number BRDA line " + line);
      return false;
    }
    return true;
  }

  // BRF:<number of branches found>
  private boolean parseBRFLine(String line) {
    String lineContent = line.substring(BRF_MARKER.length());
    if (lineContent.isEmpty()) {
      logger.log(Level.WARNING, "Tracefile contains invalid BRF line " + line);
      return false;
    }
    try {
      int nrBranchesFound = Integer.parseInt(lineContent);
      currentSourceFileCoverage.nrBranchesFound(nrBranchesFound);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING,
          "Tracefile contains invalid number of branches in BRDA line " + line);
      return false;
    }
    return true;
  }

  // BRH:<number of branches hit>
  private boolean parseBRHLine(String line) {
    String lineContent = line.substring(BRH_MARKER.length());
    if (lineContent.isEmpty()) {
      logger.log(Level.WARNING, "Tracefile contains invalid BRH line " + line);
      return false;
    }
    try {
      int nrBranchesHit = Integer.parseInt(lineContent);
      currentSourceFileCoverage.nrBranchesHit(nrBranchesHit);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING,
          "Tracefile contains invalid number of branches hit in BRH line " + line);
      return false;
    }
    return true;
  }

  // DA:<line number>,<execution count>,[,<checksum>]
  private boolean parseDALine(String line) {
    String lineContent = line.substring(DA_MARKER.length());
    String[] lineData = lineContent.split(LCOV_DELIMITER, -1);
    if (lineData.length != 2 && lineData.length != 3) {
      logger.log(Level.WARNING, "Tracefile contains invalid DA line " + line);
      return false;
    }
    for (String data : lineData) {
      if (data.isEmpty()) {
        logger.log(Level.WARNING, "Tracefile contains invalid DA line " + line);
        return false;
      }
    }
    try {
      int lineNumber = Integer.parseInt(lineData[0]);
      int executionCount = Integer.parseInt(lineData[1]);
      String checkSum = null;
      if (lineData.length == 3) {
        checkSum = lineData[2];
      }
      LineCoverage lineCoverage =
          LineCoverage.create(lineNumber, executionCount, checkSum);
      currentSourceFileCoverage.addLine(
          lineNumber, lineCoverage);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING, "Tracefile contains an invalid number on DA line " + line);
      return false;
    }
    return true;
  }

  // LH:<nr of lines with non-zero exec count>
  private boolean parseLHLine(String line) {
    String lineContent = line.substring(LH_MARKER.length());
    if (lineContent.isEmpty()) {
      logger.log(Level.WARNING, "Tracefile contains invalid LHL line " + line);
      return false;
    }
    try {
      int nrLines = Integer.parseInt(lineContent);
      currentSourceFileCoverage.nrOfLinesWithNonZeroExecution(nrLines);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING, "Tracefile contains an invalid number on LHL line " + line);
      return false;
    }
    return true;
  }

  // LF:<number of instrumented lines>
  private boolean parseLFLine(String line) {
    String lineContent = line.substring(LF_MARKER.length());
    if (lineContent.isEmpty()) {
      logger.log(Level.WARNING, "Tracefile contains invalid LF line " + line);
      return false;
    }
    try {
      int nrLines = Integer.parseInt(lineContent);
      currentSourceFileCoverage.nrOfInstrumentedLines(nrLines);
    } catch (NumberFormatException e) {
      logger.log(Level.WARNING, "Tracefile contains an invalid number on LF line " + line);
      return false;
    }
    return true;
  }
}

