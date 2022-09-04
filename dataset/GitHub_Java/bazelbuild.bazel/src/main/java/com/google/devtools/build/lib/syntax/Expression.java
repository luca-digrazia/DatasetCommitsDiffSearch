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

/**
 * Base class for all expression nodes in the AST.
 */
public abstract class Expression extends ASTNode {

  /**
   * Returns the result of evaluating this build-language expression in the
   * specified environment. All BUILD language datatypes are mapped onto the
   * corresponding Java types as follows:
   *
   * <pre>
   *    int   -> Integer
   *    float -> Double          (currently not generated by the grammar)
   *    str   -> String
   *    [...] -> List&lt;Object>    (mutable)
   *    (...) -> List&lt;Object>    (immutable)
   *    {...} -> Map&lt;Object, Object>
   *    func  -> Function
   * </pre>
   *
   * @return the result of evaluting the expression: a Java object corresponding
   *         to a datatype in the BUILD language.
   * @throws EvalException if the expression could not be evaluated.
   * @throws InterruptedException may be thrown in a sub class.
   */
  public final Object eval(Environment env) throws EvalException, InterruptedException {
    try {
      return doEval(env);
    } catch (EvalException ex) {
      throw maybeTransformException(ex);
    }
  }

  /**
   * Evaluates the expression and returns the result.
   *
   * <p>This method is only invoked by the super class {@link Expression} when calling {@link
   * #eval(Environment)}.
   *
   * @throws EvalException if the expression could not be evaluated
   * @throws InterruptedException may be thrown in a sub class.
   */
  abstract Object doEval(Environment env) throws EvalException, InterruptedException;

  /**
   * Returns the inferred type of the result of the Expression.
   *
   * <p>Checks the semantics of the Expression using the {@link Environment} according to
   * the rules of the Skylark language, throws {@link EvalException} in case of a semantical error.
   *
   * @see Statement
   */
  abstract void validate(ValidationEnvironment env) throws EvalException;
}
