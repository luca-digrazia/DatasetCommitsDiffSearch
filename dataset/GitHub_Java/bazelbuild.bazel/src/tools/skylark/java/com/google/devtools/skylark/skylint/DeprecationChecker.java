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

import com.google.devtools.build.lib.syntax.BuildFileAST;
import com.google.devtools.build.lib.syntax.FunctionDefStatement;
import com.google.devtools.build.lib.syntax.Identifier;
import com.google.devtools.build.lib.syntax.LoadStatement;
import com.google.devtools.build.lib.syntax.StringLiteral;
import com.google.devtools.skylark.skylint.DocstringUtils.DocstringInfo;
import com.google.devtools.skylark.skylint.Environment.NameInfo;
import com.google.devtools.skylark.skylint.Environment.NameInfo.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** Checks for usage of deprecated symbols. */
public class DeprecationChecker extends AstVisitorWithNameResolution {
  private static final String DEPRECATED_SYMBOL_CATEGORY = "deprecated-symbol";

  private final List<Issue> issues = new ArrayList<>();
  /** Maps a global function name to its deprecation warning, if any. */
  private final Map<String, String> symbolToDeprecationWarning = new HashMap<>();

  public static List<Issue> check(BuildFileAST ast) {
    DeprecationChecker checker = new DeprecationChecker();
    checker.visit(ast);
    return checker.issues;
  }

  @Override
  public void visit(BuildFileAST ast) {
    Map<String, StringLiteral> docstrings = DocstringUtils.collectDocstringLiterals(ast);
    for (Entry<String, StringLiteral> entry : docstrings.entrySet()) {
      String symbol = entry.getKey();
      StringLiteral docstring = entry.getValue();
      DocstringInfo info = DocstringUtils.parseDocstring(docstring, new ArrayList<>());
      if (!info.deprecated.isEmpty()) {
        symbolToDeprecationWarning.put(symbol, info.deprecated);
      }
    }
    super.visit(ast);
  }

  @Override
  public void visit(FunctionDefStatement node) {
    // Don't issue deprecation warnings inside of deprecated functions:
    if (!symbolToDeprecationWarning.containsKey(node.getIdentifier().getName())) {
      super.visit(node);
    }
  }

  @Override
  public void visit(LoadStatement stmt) {
    super.visit(stmt);
    // TODO(skylark-team): analyze dependencies for deprecations.
  }

  @Override
  void use(Identifier ident) {
    NameInfo info = env.resolveName(ident.getName());
    if (info != null
        && symbolToDeprecationWarning.containsKey(info.name)
        && info.kind != Kind.LOCAL) {
      String deprecationMessage = symbolToDeprecationWarning.get(info.name);
      issues.add(
          Issue.create(
              DEPRECATED_SYMBOL_CATEGORY,
              "usage of '" + info.name + "' is deprecated: " + deprecationMessage,
              ident.getLocation()));
    }
  }
}
