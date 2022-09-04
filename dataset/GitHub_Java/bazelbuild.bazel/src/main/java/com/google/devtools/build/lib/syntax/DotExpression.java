// Copyright 2014 The Bazel Authors. All rights reserved.
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

import java.io.IOException;

/** Syntax node for a dot expression. e.g. obj.field, but not obj.method() */
public final class DotExpression extends Expression {

  private final Expression object;

  private final Identifier field;

  DotExpression(Expression object, Identifier field) {
    this.object = object;
    this.field = field;
  }

  public Expression getObject() {
    return object;
  }

  public Identifier getField() {
    return field;
  }

  @Override
  public void prettyPrint(Appendable buffer) throws IOException {
    object.prettyPrint(buffer);
    buffer.append('.');
    field.prettyPrint(buffer);
  }

  @Override
  public void accept(NodeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public Kind kind() {
    return Kind.DOT;
  }
}
