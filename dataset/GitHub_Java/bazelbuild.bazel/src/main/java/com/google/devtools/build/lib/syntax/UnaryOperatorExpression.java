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
package com.google.devtools.build.lib.syntax;

import java.io.IOException;

/** A UnaryOperatorExpression represents a unary operator expression, 'op x'. */
public final class UnaryOperatorExpression extends Expression {

  private final TokenKind op; // NOT, TILDE, MINUS or PLUS
  private final Expression x;

  UnaryOperatorExpression(TokenKind op, Expression x) {
    this.op = op;
    this.x = x;
  }

  /** Returns the operator. */
  public TokenKind getOperator() {
    return op;
  }

  /** Returns the operand. */
  public Expression getX() {
    return x;
  }

  @Override
  public void prettyPrint(Appendable buffer) throws IOException {
    // TODO(bazel-team): retain parentheses in the syntax tree so we needn't
    // conservatively emit them here.
    buffer.append(op == TokenKind.NOT ? "not " : op.toString());
    buffer.append('(');
    x.prettyPrint(buffer);
    buffer.append(')');
  }

  @Override
  public String toString() {
    // Note that this omits the parentheses for brevity, but is not correct in general due to
    // operator precedence rules. For example, "(not False) in mylist" prints as
    // "not False in mylist", which evaluates to opposite results in the case that mylist is empty.
    // TODO(adonovan): record parentheses explicitly in syntax tree.
    return (op == TokenKind.NOT ? "not " : op.toString()) + x;
  }

  @Override
  public void accept(NodeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public Kind kind() {
    return Kind.UNARY_OPERATOR;
  }
}
