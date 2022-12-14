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

import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.events.Location;

import java.io.Serializable;

/**
 * Class representing an LValue.
 * It appears in assignment, for loop and comprehensions, e.g.
 *    lvalue = 2
 *    [for lvalue in exp]
 *    for lvalue in exp: pass
 * An LValue can be a simple variable or something more complex like a tuple.
 */
public class LValue implements Serializable {
  // Currently, expr can only be an Ident, but we plan to support more.
  private final Expression expr;

  public LValue(Expression expr) {
    this.expr = expr;
  }

  public Expression getExpression() {
    return expr;
  }

  /**
   * Assign a value to an LValue and update the environment.
   */
  public void assign(Environment env, Location loc, Expression rvalue)
      throws EvalException, InterruptedException {
    if (!(expr instanceof Ident)) {
      throw new EvalException(loc,
          "can only assign to variables, not to '" + expr + "'");
    }

    Ident ident = (Ident) expr;
    Object result = rvalue.eval(env);
    Preconditions.checkNotNull(result, "result of %s is null", rvalue);

    if (env.isSkylarkEnabled()) {
      // The variable may have been referenced successfully if a global variable
      // with the same name exists. In this case an Exception needs to be thrown.
      SkylarkEnvironment skylarkEnv = (SkylarkEnvironment) env;
      if (skylarkEnv.hasBeenReadGlobalVariable(ident.getName())) {
        throw new EvalException(loc, "Variable '" + ident.getName()
            + "' is referenced before assignment."
            + "The variable is defined in the global scope.");
      }
      Class<?> variableType = skylarkEnv.getVariableType(ident.getName());
      Class<?> resultType = EvalUtils.getSkylarkType(result.getClass());
      if (variableType != null && !variableType.equals(resultType)
          && !resultType.equals(Environment.NoneType.class)
          && !variableType.equals(Environment.NoneType.class)) {
        throw new EvalException(loc, String.format("Incompatible variable types, "
            + "trying to assign %s (type of %s) to variable %s which is already %s",
            EvalUtils.prettyPrintValue(result),
            EvalUtils.getDataTypeName(result),
            ident.getName(),
            EvalUtils.getDataTypeNameFromClass(variableType)));
      }
    }
    env.update(ident.getName(), result);
  }

  void validate(ValidationEnvironment env, Location loc, SkylarkType rvalueType)
      throws EvalException {
    // TODO(bazel-team): Implement other validations.
    if (expr instanceof Ident) {
      Ident ident = (Ident) expr;
      env.update(ident.getName(), rvalueType, loc);
      return;
    }
    throw new EvalException(loc,
        "can only assign to variables, not to '" + expr + "'");
  }

  @Override
  public String toString() {
    return expr.toString();
  }
}
