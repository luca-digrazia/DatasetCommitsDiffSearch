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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.util.SpellChecker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** A syntax-tree-walking evaluator. */
// TODO(adonovan): make this class the sole locus of tree-based evaluation logic.
// Make all its methods static, and thread StarlarkThread (soon: StarlarkThread) explicitly.
// The only actual state is the return value, which can be saved in the StarlarkThread's call frame.
// Move remaining Expression.eval logic here, and simplify.
final class Eval {

  private static final AtomicReference<Debugger> debugger = new AtomicReference<>();

  private final StarlarkThread thread;
  private final Debugger dbg;
  private Object result = Starlark.NONE;

  // ---- entry points ----

  static void setDebugger(Debugger dbg) {
    Debugger prev = debugger.getAndSet(dbg);
    if (prev != null) {
      prev.close();
    }
  }

  static void execFile(StarlarkThread thread, StarlarkFile file)
      throws EvalException, InterruptedException {
    checkInterrupt();
    for (Statement stmt : file.getStatements()) {
      execToplevelStatement(thread, stmt);
    }
  }

  static Object execStatements(StarlarkThread thread, List<Statement> statements)
      throws EvalException, InterruptedException {
    checkInterrupt();
    Eval eval = new Eval(thread);
    eval.execStatementsInternal(statements);
    return eval.result;
  }

  static void execToplevelStatement(StarlarkThread thread, Statement stmt)
      throws EvalException, InterruptedException {
    // Ignore the returned BREAK/CONTINUE/RETURN/PASS token:
    // the first three don't exist at top-level, and the last is a no-op.
    new Eval(thread).exec(stmt);

    // Hack for SkylarkImportLookupFunction's "export" semantics.
    if (thread.postAssignHook != null) {
      if (stmt instanceof AssignmentStatement) {
        AssignmentStatement assign = (AssignmentStatement) stmt;
        for (Identifier id : Identifier.boundIdentifiers(assign.getLHS())) {
          String name = id.getName();
          Object value = thread.moduleLookup(name);
          thread.postAssignHook.assign(name, value);
        }
      }
    }
  }

  private Eval(StarlarkThread thread) {
    this.thread = thread;
    this.dbg = debugger.get(); // capture value and use for lifetime of one Eval
  }

  private void execAssignment(AssignmentStatement node) throws EvalException, InterruptedException {
    if (node.isAugmented()) {
      execAugmentedAssignment(node);
    } else {
      Object rvalue = eval(thread, node.getRHS());
      assign(node.getLHS(), rvalue, thread, node.getLocation());
    }
  }

  private TokenKind execFor(ForStatement node) throws EvalException, InterruptedException {
    Object o = eval(thread, node.getCollection());
    Iterable<?> seq = Starlark.toIterable(o);
    EvalUtils.lock(o, node.getLocation());
    try {
      for (Object it : seq) {
        assign(node.getLHS(), it, thread, node.getLocation());

        switch (execStatementsInternal(node.getBlock())) {
          case PASS:
          case CONTINUE:
            // Stay in loop.
            checkInterrupt();
            continue;
          case BREAK:
            // Finish loop, execute next statement after loop.
            return TokenKind.PASS;
          case RETURN:
            // Finish loop, return from function.
            return TokenKind.RETURN;
          default:
            throw new IllegalStateException("unreachable");
        }
      }
    } finally {
      EvalUtils.unlock(o, node.getLocation());
    }
    return TokenKind.PASS;
  }

  private void execDef(DefStatement node) throws EvalException, InterruptedException {
    FunctionSignature sig = node.getSignature();

    // Evaluate default value expressions of optional parameters.
    // They may be discontinuous:
    // def f(a, b=1, *, c, d=2) has a defaults tuple of (1, 2).
    // TODO(adonovan): record the gaps (e.g. c) with a sentinel
    // to simplify Starlark.matchSignature.
    Tuple<Object> defaults = Tuple.empty();
    int ndefaults = node.getSignature().numOptionals();
    if (ndefaults > 0) {
      Object[] array = new Object[ndefaults];
      for (int i = sig.numMandatoryPositionals(), j = 0; i < sig.numParameters(); i++) {
        Expression expr = node.getParameters().get(i).getDefaultValue();
        if (expr != null) {
          array[j++] = eval(thread, expr);
        }
      }
      defaults = Tuple.wrap(array);
    }

    thread.updateAndExport(
        node.getIdentifier().getName(),
        new StarlarkFunction(
            node.getIdentifier().getName(),
            node.getIdentifier().getLocation(),
            sig,
            defaults,
            node.getStatements(),
            thread.getGlobals()));
  }

  private TokenKind execIf(IfStatement node) throws EvalException, InterruptedException {
    boolean cond = Starlark.truth(eval(thread, node.getCondition()));
    if (cond) {
      return execStatementsInternal(node.getThenBlock());
    } else if (node.getElseBlock() != null) {
      return execStatementsInternal(node.getElseBlock());
    }
    return TokenKind.PASS;
  }

  private void execLoad(LoadStatement node) throws EvalException {
    for (LoadStatement.Binding binding : node.getBindings()) {
      Identifier orig = binding.getOriginalName();

      // TODO(adonovan): make this a static check.
      if (orig.isPrivate() && !node.mayLoadInternalSymbols()) {
        throw new EvalException(
            orig.getLocation(),
            "symbol '" + orig.getName() + "' is private and cannot be imported.");
      }

      // Load module.
      String moduleName = node.getImport().getValue();
      StarlarkThread.Extension module = thread.getExtension(moduleName);
      if (module == null) {
        throw new EvalException(
            node.getImport().getLocation(),
            String.format(
                "file '%s' was not correctly loaded. "
                    + "Make sure the 'load' statement appears in the global scope in your file",
                moduleName));
      }

      // Extract symbol.
      Object value = module.getBindings().get(orig.getName());
      if (value == null) {
        throw new EvalException(
            orig.getLocation(),
            String.format(
                "file '%s' does not contain symbol '%s'%s",
                moduleName,
                orig.getName(),
                SpellChecker.didYouMean(orig.getName(), module.getBindings().keySet())));
      }

      // Define module-local variable.
      thread.update(binding.getLocalName().getName(), value);
    }
  }

  private TokenKind execReturn(ReturnStatement node) throws EvalException, InterruptedException {
    Expression ret = node.getReturnExpression();
    if (ret != null) {
      this.result = eval(thread, ret);
    }
    return TokenKind.RETURN;
  }

  private TokenKind exec(Statement st) throws EvalException, InterruptedException {
    if (dbg != null) {
      dbg.before(thread, st.getLocation());
    }

    try {
      return execDispatch(st);
    } catch (EvalException ex) {
      throw maybeTransformException(st, ex);
    }
  }

  private TokenKind execDispatch(Statement st) throws EvalException, InterruptedException {
    switch (st.kind()) {
      case ASSIGNMENT:
        execAssignment((AssignmentStatement) st);
        return TokenKind.PASS;
      case EXPRESSION:
        eval(thread, ((ExpressionStatement) st).getExpression());
        return TokenKind.PASS;
      case FLOW:
        return ((FlowStatement) st).getKind();
      case FOR:
        return execFor((ForStatement) st);
      case DEF:
        execDef((DefStatement) st);
        return TokenKind.PASS;
      case IF:
        return execIf((IfStatement) st);
      case LOAD:
        execLoad((LoadStatement) st);
        return TokenKind.PASS;
      case RETURN:
        return execReturn((ReturnStatement) st);
    }
    throw new IllegalArgumentException("unexpected statement: " + st.kind());
  }

  private TokenKind execStatementsInternal(List<Statement> statements)
      throws EvalException, InterruptedException {
    // Hot code path, good chance of short lists which don't justify the iterator overhead.
    for (int i = 0; i < statements.size(); i++) {
      TokenKind flow = exec(statements.get(i));
      if (flow != TokenKind.PASS) {
        return flow;
      }
    }
    return TokenKind.PASS;
  }

  /**
   * Updates the environment bindings, and possibly mutates objects, so as to assign the given value
   * to the given expression. The expression must be valid for an {@code LValue}.
   */
  private static void assign(Expression expr, Object value, StarlarkThread thread, Location loc)
      throws EvalException, InterruptedException {
    if (expr instanceof Identifier) {
      assignIdentifier((Identifier) expr, value, thread);
    } else if (expr instanceof IndexExpression) {
      Object object = eval(thread, ((IndexExpression) expr).getObject());
      Object key = eval(thread, ((IndexExpression) expr).getKey());
      assignItem(object, key, value, loc);
    } else if (expr instanceof ListExpression) {
      ListExpression list = (ListExpression) expr;
      assignList(list.getElements(), value, thread, loc);
    } else {
      // Not possible for validated ASTs.
      throw new EvalException(loc, "cannot assign to '" + expr + "'");
    }
  }

  /** Binds a variable to the given value in the environment. */
  private static void assignIdentifier(Identifier ident, Object value, StarlarkThread thread)
      throws EvalException {
    thread.updateAndExport(ident.getName(), value);
  }

  /**
   * Adds or changes an object-key-value relationship for a list or dict.
   *
   * <p>For a list, the key is an in-range index. For a dict, it is a hashable value.
   *
   * @throws EvalException if the object is not a list or dict
   */
  @SuppressWarnings("unchecked")
  private static void assignItem(Object object, Object key, Object value, Location loc)
      throws EvalException {
    if (object instanceof Dict) {
      Dict<Object, Object> dict = (Dict<Object, Object>) object;
      dict.put(key, value, loc);
    } else if (object instanceof StarlarkList) {
      StarlarkList<Object> list = (StarlarkList<Object>) object;
      int index = EvalUtils.getSequenceIndex(key, list.size(), loc);
      list.set(index, value, loc);
    } else {
      throw new EvalException(
          loc,
          "can only assign an element in a dictionary or a list, not in a '"
              + EvalUtils.getDataTypeName(object)
              + "'");
    }
  }

  /**
   * Recursively assigns an iterable value to a sequence of assignable expressions.
   *
   * @throws EvalException if the list literal has length 0, or if the value is not an iterable of
   *     matching length
   */
  private static void assignList(
      List<Expression> lhs, Object x, StarlarkThread thread, Location loc)
      throws EvalException, InterruptedException {
    // TODO(adonovan): lock/unlock rhs during iteration so that
    // assignments fail when the left side aliases the right,
    // which is a tricky case in Python assignment semantics.
    int nrhs = Starlark.len(x);
    if (nrhs < 0) {
      throw new EvalException(loc, "type '" + EvalUtils.getDataTypeName(x) + "' is not iterable");
    }
    Iterable<?> rhs = Starlark.toIterable(x); // fails if x is a string
    int len = lhs.size();
    if (len == 0) {
      throw new EvalException(
          loc, "lists or tuples on the left-hand side of assignments must have at least one item");
    }
    if (len != nrhs) {
      throw new EvalException(
          loc,
          String.format(
              "assignment length mismatch: left-hand side has length %d, but right-hand side"
                  + " evaluates to value of length %d",
              len, nrhs));
    }
    int i = 0;
    for (Object item : rhs) {
      assign(lhs.get(i), item, thread, loc);
      i++;
    }
  }

  private void execAugmentedAssignment(AssignmentStatement stmt)
      throws EvalException, InterruptedException {
    Expression lhs = stmt.getLHS();
    TokenKind op = stmt.getOperator();
    Expression rhs = stmt.getRHS();
    Location loc = stmt.getLocation();

    if (lhs instanceof Identifier) {
      Object x = eval(thread, lhs);
      Object y = eval(thread, rhs);
      Object z = inplaceBinaryOp(op, x, y, thread, loc);
      assignIdentifier((Identifier) lhs, z, thread);
    } else if (lhs instanceof IndexExpression) {
      // object[index] op= y
      // The object and key should be evaluated only once, so we don't use lhs.eval().
      IndexExpression index = (IndexExpression) lhs;
      Object object = eval(thread, index.getObject());
      Object key = eval(thread, index.getKey());
      Object x = EvalUtils.index(object, key, thread, loc);
      // Evaluate rhs after lhs.
      Object y = eval(thread, rhs);
      Object z = inplaceBinaryOp(op, x, y, thread, loc);
      assignItem(object, key, z, loc);
    } else if (lhs instanceof ListExpression) {
      throw new EvalException(loc, "cannot perform augmented assignment on a list literal");
    } else {
      // Not possible for validated ASTs.
      throw new EvalException(loc, "cannot perform augmented assignment on '" + lhs + "'");
    }
  }

  private static Object inplaceBinaryOp(
      TokenKind op, Object x, Object y, StarlarkThread thread, Location location)
      throws EvalException, InterruptedException {
    // list += iterable  behaves like  list.extend(iterable)
    // TODO(b/141263526): following Python, allow list+=iterable (but not list+iterable).
    if (op == TokenKind.PLUS && x instanceof StarlarkList && y instanceof StarlarkList) {
      StarlarkList<?> list = (StarlarkList) x;
      list.extend(y);
      return list;
    }
    return EvalUtils.binaryOp(op, x, y, thread, location);
  }

  // ---- expressions ----

  /**
   * Returns the result of evaluating this build-language expression in the specified environment.
   * All BUILD language datatypes are mapped onto the corresponding Java types as follows:
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
   * @return the result of evaluting the expression: a Java object corresponding to a datatype in
   *     the BUILD language.
   * @throws EvalException if the expression could not be evaluated.
   * @throws InterruptedException may be thrown in a sub class.
   */
  static Object eval(StarlarkThread thread, Expression expr)
      throws EvalException, InterruptedException {
    // TODO(adonovan): don't push and pop all the time. We should only need the stack of function
    // call frames, and we should recycle them.
    // TODO(adonovan): put the StarlarkThread (Starlark thread) into the Java thread-local store
    // once only, in push, and undo this in pop.
    try {
      if (Callstack.enabled) {
        Callstack.push(expr);
      }
      try {
        return doEval(thread, expr);
      } catch (EvalException ex) {
        throw maybeTransformException(expr, ex);
      }
    } finally {
      if (Callstack.enabled) {
        Callstack.pop();
      }
    }
  }

  private static Object doEval(StarlarkThread thread, Expression expr)
      throws EvalException, InterruptedException {
    switch (expr.kind()) {
      case BINARY_OPERATOR:
        {
          BinaryOperatorExpression binop = (BinaryOperatorExpression) expr;
          Object x = eval(thread, binop.getX());
          // AND and OR require short-circuit evaluation.
          switch (binop.getOperator()) {
            case AND:
              return Starlark.truth(x) ? eval(thread, binop.getY()) : x;
            case OR:
              return Starlark.truth(x) ? x : eval(thread, binop.getY());
            default:
              Object y = eval(thread, binop.getY());
              return EvalUtils.binaryOp(binop.getOperator(), x, y, thread, binop.getLocation());
          }
        }

      case COMPREHENSION:
        return evalComprehension(thread, (Comprehension) expr);

      case CONDITIONAL:
        {
          ConditionalExpression cond = (ConditionalExpression) expr;
          Object v = eval(thread, cond.getCondition());
          return eval(thread, Starlark.truth(v) ? cond.getThenCase() : cond.getElseCase());
        }

      case DICT_EXPR:
        {
          DictExpression dictexpr = (DictExpression) expr;
          Dict<Object, Object> dict = Dict.of(thread.mutability());
          Location loc = dictexpr.getLocation();
          for (DictExpression.Entry entry : dictexpr.getEntries()) {
            Object k = eval(thread, entry.getKey());
            Object v = eval(thread, entry.getValue());
            int before = dict.size();
            dict.put(k, v, loc);
            if (dict.size() == before) {
              throw new EvalException(
                  loc, "Duplicated key " + Starlark.repr(k) + " when creating dictionary");
            }
          }
          return dict;
        }

      case DOT:
        {
          DotExpression dot = (DotExpression) expr;
          Object object = eval(thread, dot.getObject());
          String name = dot.getField().getName();
          Object result = EvalUtils.getAttr(thread, dot.getLocation(), object, name);
          if (result == null) {
            throw EvalUtils.getMissingAttrException(object, name, thread.getSemantics());
          }
          return result;
        }

      case CALL:
        {
          checkInterrupt();

          CallExpression call = (CallExpression) expr;
          Object fn = eval(thread, call.getFunction());

          // StarStar and Star args are guaranteed to be last, if they occur.
          ImmutableList<Argument> arguments = call.getArguments();
          int n = arguments.size();
          Argument.StarStar starstar = null;
          if (n > 0 && arguments.get(n - 1) instanceof Argument.StarStar) {
            starstar = (Argument.StarStar) arguments.get(n - 1);
            n--;
          }
          Argument.Star star = null;
          if (n > 0 && arguments.get(n - 1) instanceof Argument.Star) {
            star = (Argument.Star) arguments.get(n - 1);
            n--;
          }
          // Inv: n = |positional| + |named|

          // Allocate assuming no *args/**kwargs.
          int npos = call.getNumPositionalArguments();
          int i;

          // f(expr) -- positional args
          Object[] positional = npos == 0 ? EMPTY : new Object[npos];
          for (i = 0; i < npos; i++) {
            Argument arg = arguments.get(i);
            Object value = eval(thread, arg.getValue());
            positional[i] = value;
          }

          // f(id=expr) -- named args
          Object[] named = n == npos ? EMPTY : new Object[2 * (n - npos)];
          for (int j = 0; i < n; i++) {
            Argument.Keyword arg = (Argument.Keyword) arguments.get(i);
            Object value = eval(thread, arg.getValue());
            named[j++] = arg.getName();
            named[j++] = value;
          }

          // f(*args) -- varargs
          if (star != null) {
            Object value = eval(thread, star.getValue());
            if (!(value instanceof StarlarkIterable)) {
              throw new EvalException(
                  call.getLocation(),
                  "argument after * must be an iterable, not " + EvalUtils.getDataTypeName(value));
            }
            // TODO(adonovan): opt: if value.size is known, preallocate (and skip if empty).
            ArrayList<Object> list = new ArrayList<>();
            Collections.addAll(list, positional);
            Iterables.addAll(list, ((Iterable<?>) value));
            positional = list.toArray();
          }

          // f(**kwargs)
          if (starstar != null) {
            Object value = eval(thread, starstar.getValue());
            if (!(value instanceof Dict)) {
              throw new EvalException(
                  call.getLocation(),
                  "argument after ** must be a dict, not " + EvalUtils.getDataTypeName(value));
            }
            Dict<?, ?> kwargs = (Dict<?, ?>) value;
            int j = named.length;
            named = Arrays.copyOf(named, j + 2 * kwargs.size());
            for (Map.Entry<?, ?> e : kwargs.entrySet()) {
              if (!(e.getKey() instanceof String)) {
                throw new EvalException(
                    call.getLocation(),
                    "keywords must be strings, not " + EvalUtils.getDataTypeName(e.getKey()));
              }
              named[j++] = e.getKey();
              named[j++] = e.getValue();
            }
          }

          return Starlark.fastcall(thread, fn, call.getLocation(), positional, named);
        }

      case IDENTIFIER:
        {
          Identifier id = (Identifier) expr;
          String name = id.getName();
          if (id.getScope() == null) {
            // Legacy behavior, to be removed.
            Object result = thread.lookup(name);
            if (result == null) {
              String error =
                  ValidationEnvironment.createInvalidIdentifierException(
                      id.getName(), thread.getVariableNames());
              throw new EvalException(id.getLocation(), error);
            }
            return result;
          }

          Object result;
          switch (id.getScope()) {
            case Local:
              result = thread.localLookup(name);
              break;
            case Module:
              result = thread.moduleLookup(name);
              break;
            case Universe:
              result = thread.universeLookup(name);
              break;
            default:
              throw new IllegalStateException(id.getScope().toString());
          }
          if (result == null) {
            // Since Scope was set, we know that the variable is defined in the scope.
            // However, the assignment was not yet executed.
            String error = ValidationEnvironment.getErrorForObsoleteThreadLocalVars(id.getName());
            if (error == null) {
              error =
                  id.getScope().getQualifier()
                      + " variable '"
                      + name
                      + "' is referenced before assignment.";
            }
            throw new EvalException(id.getLocation(), error);
          }
          return result;
        }

      case INDEX:
        {
          IndexExpression index = (IndexExpression) expr;
          Object object = eval(thread, index.getObject());
          Object key = eval(thread, index.getKey());
          return EvalUtils.index(object, key, thread, index.getLocation());
        }

      case INTEGER_LITERAL:
        return ((IntegerLiteral) expr).getValue();

      case LIST_EXPR:
        {
          ListExpression list = (ListExpression) expr;
          int n = list.getElements().size();
          Object[] array = new Object[n];
          for (int i = 0; i < n; i++) {
            array[i] = eval(thread, list.getElements().get(i));
          }
          return list.isTuple() ? Tuple.wrap(array) : StarlarkList.wrap(thread.mutability(), array);
        }

      case SLICE:
        {
          SliceExpression slice = (SliceExpression) expr;
          Object object = eval(thread, slice.getObject());
          Object start = slice.getStart() == null ? Starlark.NONE : eval(thread, slice.getStart());
          Object end = slice.getEnd() == null ? Starlark.NONE : eval(thread, slice.getEnd());
          Object step = slice.getStep() == null ? Starlark.NONE : eval(thread, slice.getStep());
          Location loc = slice.getLocation();

          // TODO(adonovan): move the rest into a public EvalUtils.slice() operator.

          if (object instanceof Sequence) {
            return ((Sequence<?>) object).getSlice(start, end, step, loc, thread.mutability());
          }

          if (object instanceof String) {
            String string = (String) object;
            List<Integer> indices =
                EvalUtils.getSliceIndices(start, end, step, string.length(), loc);
            // TODO(adonovan): opt: optimize for common case, step=1.
            char[] result = new char[indices.size()];
            char[] original = string.toCharArray();
            int resultIndex = 0;
            for (int originalIndex : indices) {
              result[resultIndex] = original[originalIndex];
              ++resultIndex;
            }
            return new String(result);
          }

          throw new EvalException(
              loc,
              String.format(
                  "type '%s' has no operator [:](%s, %s, %s)",
                  EvalUtils.getDataTypeName(object),
                  EvalUtils.getDataTypeName(start),
                  EvalUtils.getDataTypeName(end),
                  EvalUtils.getDataTypeName(step)));
        }

      case STRING_LITERAL:
        return ((StringLiteral) expr).getValue();

      case UNARY_OPERATOR:
        {
          UnaryOperatorExpression unop = (UnaryOperatorExpression) expr;
          Object x = eval(thread, unop.getX());
          return EvalUtils.unaryOp(unop.getOperator(), x, unop.getLocation());
        }
    }
    throw new IllegalArgumentException("unexpected expression: " + expr.kind());
  }

  private static Object evalComprehension(StarlarkThread thread, Comprehension comp)
      throws EvalException, InterruptedException {
    final Dict<Object, Object> dict = comp.isDict() ? Dict.of(thread.mutability()) : null;
    final ArrayList<Object> list = comp.isDict() ? null : new ArrayList<>();

    // Save values of all variables bound in a 'for' clause
    // so we can restore them later.
    // TODO(adonovan) throw all this away when we implement flat environments.
    List<Object> saved = new ArrayList<>(); // alternating keys and values
    for (Comprehension.Clause clause : comp.getClauses()) {
      if (clause instanceof Comprehension.For) {
        for (Identifier ident :
            Identifier.boundIdentifiers(((Comprehension.For) clause).getVars())) {
          String name = ident.getName();
          Object value = thread.localLookup(ident.getName());
          saved.add(name);
          saved.add(value);
        }
      }
    }

    // The Lambda class serves as a recursive lambda closure.
    class Lambda {
      // execClauses(index) recursively executes the clauses starting at index,
      // and finally evaluates the body and adds its value to the result.
      void execClauses(int index) throws EvalException, InterruptedException {
        checkInterrupt();

        // recursive case: one or more clauses
        if (index < comp.getClauses().size()) {
          Comprehension.Clause clause = comp.getClauses().get(index);
          if (clause instanceof Comprehension.For) {
            Comprehension.For forClause = (Comprehension.For) clause;

            Object iterable = eval(thread, forClause.getIterable());
            Location loc = comp.getLocation();
            Iterable<?> listValue = Starlark.toIterable(iterable);
            EvalUtils.lock(iterable, loc);
            try {
              for (Object elem : listValue) {
                assign(forClause.getVars(), elem, thread, loc);
                execClauses(index + 1);
              }
            } finally {
              EvalUtils.unlock(iterable, loc);
            }

          } else {
            Comprehension.If ifClause = (Comprehension.If) clause;
            if (Starlark.truth(eval(thread, ifClause.getCondition()))) {
              execClauses(index + 1);
            }
          }
          return;
        }

        // base case: evaluate body and add to result.
        if (dict != null) {
          DictExpression.Entry body = (DictExpression.Entry) comp.getBody();
          Object k = eval(thread, body.getKey());
          EvalUtils.checkHashable(k);
          Object v = eval(thread, body.getValue());
          dict.put(k, v, comp.getLocation());
        } else {
          list.add(eval(thread, ((Expression) comp.getBody())));
        }
      }
    }
    new Lambda().execClauses(0);

    // Restore outer scope variables.
    // This loop implicitly undefines comprehension variables.
    for (int i = 0; i != saved.size(); ) {
      String name = (String) saved.get(i++);
      Object value = saved.get(i++);
      thread.updateInternal(name, value);
    }

    return comp.isDict() ? dict : StarlarkList.copyOf(thread.mutability(), list);
  }

  private static final Object[] EMPTY = {};

  /** Returns an exception which should be thrown instead of the original one. */
  private static EvalException maybeTransformException(Node node, EvalException original) {
    // If there is already a non-empty stack trace, we only add this node iff it describes a
    // new scope (e.g. CallExpression).
    if (original instanceof EvalExceptionWithStackTrace) {
      EvalExceptionWithStackTrace real = (EvalExceptionWithStackTrace) original;
      if (node instanceof CallExpression) {
        real.registerNode(node);
      }
      return real;
    }

    if (original.canBeAddedToStackTrace()) {
      return new EvalExceptionWithStackTrace(original, node);
    } else {
      return original;
    }
  }

  private static void checkInterrupt() throws InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
  }
}
