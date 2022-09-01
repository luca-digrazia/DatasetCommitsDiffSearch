// Copyright 2014 Google Inc. All rights reserved.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.vfs.PathFragment;

import java.util.List;

/**
 * Syntax node for an import statement.
 */
public final class LoadStatement extends Statement {

  private final ImmutableList<Ident> symbols;
  private final PathFragment importPath;

  /**
   * Constructs an import statement.
   */
  LoadStatement(String path, List<Ident> symbols) {
    this.symbols = ImmutableList.copyOf(symbols);
    this.importPath = new PathFragment(path + ".bzl");
  }

  public ImmutableList<Ident> getSymbols() {
    return symbols;
  }

  public PathFragment getImportPath() {
    return importPath;
  }

  @Override
  public String toString() {
    return String.format("load(\"%s\", %s)", importPath, Joiner.on(", ").join(symbols));
  }

  @Override
  void exec(Environment env) throws EvalException, InterruptedException {
    for (Ident i : symbols) {
      try {
        if (i.getName().startsWith("_")) {
          throw new EvalException(getLocation(), "symbol '" + i + "' is private and cannot "
              + "be imported");
        }
        env.importSymbol(getImportPath(), i.getName());
      } catch (Environment.NoSuchVariableException | Environment.LoadFailedException e) {
        throw new EvalException(getLocation(), e.getMessage());
      }
    }
  }

  @Override
  public void accept(SyntaxTreeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  void validate(ValidationEnvironment env) throws EvalException {
    // TODO(bazel-team): implement semantical check.
    for (Ident symbol : symbols) {
      env.update(symbol.getName(), SkylarkType.UNKNOWN, getLocation());
    }
  }
}
