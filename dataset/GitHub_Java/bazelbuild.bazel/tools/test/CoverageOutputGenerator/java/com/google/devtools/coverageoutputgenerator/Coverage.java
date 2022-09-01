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

package com.google.devtools.coverageoutputgenerator;

import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

class Coverage {
  private final TreeMap<String, SourceFileCoverage> sourceFiles;

  Coverage() {
    sourceFiles = new TreeMap<>();
  }

  void add(SourceFileCoverage input) {
    String sourceFilename = input.sourceFileName();
    if (sourceFiles.containsKey(sourceFilename)) {
      SourceFileCoverage old = sourceFiles.get(sourceFilename);
      sourceFiles.put(sourceFilename, SourceFileCoverage.merge(old, input));
    } else {
      sourceFiles.put(sourceFilename, input);
    }
  }

  static Coverage merge(Coverage c1, Coverage c2) {
    Coverage merged = new Coverage();
    for (SourceFileCoverage sourceFile : c1.getAllSourceFiles()) {
      merged.add(sourceFile);
    }
    for (SourceFileCoverage sourceFile : c2.getAllSourceFiles()) {
      merged.add(sourceFile);
    }
    return merged;
  }

  static Coverage filterOutMatchingSources(Coverage coverage, List<String> regexes)
      throws IllegalArgumentException {
    if (coverage == null || regexes == null) {
      throw new IllegalArgumentException("Coverage and regex should not be null.");
    }
    if (regexes.isEmpty()) {
      return coverage;
    }
    Coverage filteredCoverage = new Coverage();
    for (SourceFileCoverage source : coverage.getAllSourceFiles()) {
      if (!matchesAnyRegex(source.sourceFileName(), regexes)) {
        filteredCoverage.add(source);
      }
    }
    return filteredCoverage;
  }

  private static boolean matchesAnyRegex(String input, List<String> regexes) {
    for (String regex : regexes) {
      if (input.matches(regex)) {
        return true;
      }
    }
    return false;
  }

  boolean isEmpty() {
    return sourceFiles.isEmpty();
  }

  Collection<SourceFileCoverage> getAllSourceFiles() {
    return sourceFiles.values();
  }
}
