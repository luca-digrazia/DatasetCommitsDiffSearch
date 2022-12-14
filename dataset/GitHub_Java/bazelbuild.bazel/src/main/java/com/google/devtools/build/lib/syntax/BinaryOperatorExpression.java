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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.syntax.ClassObject.SkylarkClassObject;

import java.util.Collection;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;

/**
 * Syntax node for a binary operator expression.
 */
public final class BinaryOperatorExpression extends Expression {

  private final Expression lhs;

  private final Expression rhs;

  private final Operator operator;

  public BinaryOperatorExpression(Operator operator,
                                  Expression lhs,
                                  Expression rhs) {
    this.lhs = lhs;
    this.rhs = rhs;
    this.operator = operator;
  }

  public Expression getLhs() {
    return lhs;
  }

  public Expression getRhs() {
    return rhs;
  }

  /**
   * Returns the operator kind for this binary operation.
   */
  public Operator getOperator() {
    return operator;
  }

  @Override
  public String toString() {
    return lhs + " " + operator + " " + rhs;
  }

  @SuppressWarnings("unchecked")
  private int compare(Object lval, Object rval) throws EvalException {
    if (!(lval instanceof Comparable)) {
      throw new EvalException(getLocation(), lval + " is not comparable");
    }
    try {
      return ((Comparable<Object>) lval).compareTo(rval);
    } catch (ClassCastException e) {
      throw new EvalException(getLocation(), "Cannot compare " + EvalUtils.getDatatypeName(lval)
          + " with " + EvalUtils.getDatatypeName(rval));
    }
  }

  @Override
  Object eval(Environment env) throws EvalException, InterruptedException {
    Object lval = lhs.eval(env);

    // Short-circuit operators
    if (operator == Operator.AND) {
      if (EvalUtils.toBoolean(lval)) {
        return rhs.eval(env);
      } else {
        return lval;
      }
    }

    if (operator == Operator.OR) {
      if (EvalUtils.toBoolean(lval)) {
        return lval;
      } else {
        return rhs.eval(env);
      }
    }

    Object rval = rhs.eval(env);

    switch (operator) {
      case PLUS: {
        // int + int
        if (lval instanceof Integer && rval instanceof Integer) {
          return ((Integer) lval).intValue() + ((Integer) rval).intValue();
        }

        // string + string
        if (lval instanceof String && rval instanceof String) {
          return (String) lval + (String) rval;
        }

        // list + list, tuple + tuple (list + tuple, tuple + list => error)
        if (lval instanceof List<?> && rval instanceof List<?>) {
          List<?> llist = (List<?>) lval;
          List<?> rlist = (List<?>) rval;
          if (EvalUtils.isImmutable(llist) != EvalUtils.isImmutable(rlist)) {
            throw new EvalException(getLocation(), "can only concatenate "
                + EvalUtils.getDatatypeName(rlist) + " (not \""
                + EvalUtils.getDatatypeName(llist) + "\") to "
                + EvalUtils.getDatatypeName(rlist));
          }
          if (llist instanceof GlobList<?> || rlist instanceof GlobList<?>) {
            return GlobList.concat(llist, rlist);
          } else {
            List<Object> result = Lists.newArrayListWithCapacity(llist.size() + rlist.size());
            result.addAll(llist);
            result.addAll(rlist);
            return EvalUtils.makeSequence(result, EvalUtils.isImmutable(llist));
          }
        }

        if (lval instanceof SkylarkList && rval instanceof SkylarkList) {
          return SkylarkList.concat((SkylarkList) lval, (SkylarkList) rval, getLocation());
        }

        if (env.isSkylarkEnabled() && lval instanceof Map<?, ?> && rval instanceof Map<?, ?>) {
          Map<?, ?> ldict = (Map<?, ?>) lval;
          Map<?, ?> rdict = (Map<?, ?>) rval;
          Map<Object, Object> result = Maps.newHashMapWithExpectedSize(ldict.size() + rdict.size());
          result.putAll(ldict);
          result.putAll(rdict);
          return result;
        }

        if (env.isSkylarkEnabled()
            && lval instanceof SkylarkClassObject && rval instanceof SkylarkClassObject) {
          return SkylarkClassObject.concat(
              (SkylarkClassObject) lval, (SkylarkClassObject) rval, getLocation());
        }

        if (env.isSkylarkEnabled() && lval instanceof SkylarkNestedSet) {
          return new SkylarkNestedSet((SkylarkNestedSet) lval, rval, getLocation());
        }
        break;
      }

      case MINUS: {
        if (lval instanceof Integer && rval instanceof Integer) {
          return ((Integer) lval).intValue() - ((Integer) rval).intValue();
        }
        break;
      }

      case MULT: {
        // int * int
        if (lval instanceof Integer && rval instanceof Integer) {
          return ((Integer) lval).intValue() * ((Integer) rval).intValue();
        }

        // string * int
        if (lval instanceof String && rval instanceof Integer) {
          return Strings.repeat((String) lval, ((Integer) rval).intValue());
        }

        // int * string
        if (lval instanceof Integer && rval instanceof String) {
          return Strings.repeat((String) rval, ((Integer) lval).intValue());
        }
        break;
      }

      case PERCENT: {
        // int % int
        if (lval instanceof Integer && rval instanceof Integer) {
          return ((Integer) lval).intValue() % ((Integer) rval).intValue();
        }

        // string % tuple, string % dict, string % anything-else
        if (lval instanceof String) {
          try {
            String pattern = (String) lval;
            if (rval instanceof List<?>) {
              List<?> rlist = (List<?>) rval;
              if (EvalUtils.isTuple(rlist)) {
                return EvalUtils.formatString(pattern, rlist);
              }
              /* string % list: fall thru */
            }
            if (rval instanceof SkylarkList) {
              SkylarkList rlist = (SkylarkList) rval;
              if (rlist.isTuple()) {
                return EvalUtils.formatString(pattern, rlist.toList());
              }
            }

            return EvalUtils.formatString(pattern,
                                          Collections.singletonList(rval));
          } catch (IllegalFormatException e) {
            throw new EvalException(getLocation(), e.getMessage());
          }
        }
        break;
      }

      case EQUALS_EQUALS: {
        return lval.equals(rval);
      }

      case NOT_EQUALS: {
        return !lval.equals(rval);
      }

      case LESS: {
        return compare(lval, rval) < 0;
      }

      case LESS_EQUALS: {
        return compare(lval, rval) <= 0;
      }

      case GREATER: {
        return compare(lval, rval) > 0;
      }

      case GREATER_EQUALS: {
        return compare(lval, rval) >= 0;
      }

      case IN: {
        if (rval instanceof SkylarkList) {
          for (Object obj : (SkylarkList) rval) {
            if (obj.equals(lval)) {
              return true;
            }
          }
          return false;
        } else if (rval instanceof Collection<?>) {
          return ((Collection<?>) rval).contains(lval);
        } else if (rval instanceof Map<?, ?>) {
          return ((Map<?, ?>) rval).containsKey(lval);
        } else if (rval instanceof String) {
          if (lval instanceof String) {
            return ((String) rval).contains((String) lval);
          } else {
            throw new EvalException(getLocation(),
                "in operator only works on strings if the left operand is also a string");
          }
        } else {
          throw new EvalException(getLocation(),
              "in operator only works on lists, tuples, dictionaries and strings");
        }
      }

      default: {
        throw new AssertionError("Unsupported binary operator: " + operator);
      }
    } // endswitch

    throw new EvalException(getLocation(),
        "unsupported operand types for '" + operator + "': '"
        + EvalUtils.getDatatypeName(lval) + "' and '"
        + EvalUtils.getDatatypeName(rval) + "'");
  }

  @Override
  public void accept(SyntaxTreeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  SkylarkType validate(ValidationEnvironment env) throws EvalException {
    SkylarkType ltype = lhs.validate(env);
    SkylarkType rtype = rhs.validate(env);
    String lname = EvalUtils.getDataTypeNameFromClass(ltype.getType());
    String rname = EvalUtils.getDataTypeNameFromClass(rtype.getType());

    switch (operator) {
      case AND: {
        return ltype.infer(rtype, "and operator", rhs.getLocation(), lhs.getLocation());
      }

      case OR: {
        return ltype.infer(rtype, "or operator", rhs.getLocation(), lhs.getLocation());
      }

      case PLUS: {
        // int + int
        if (ltype == SkylarkType.INT && rtype == SkylarkType.INT) {
          return SkylarkType.INT;
        }

        // string + string
        if (ltype == SkylarkType.STRING && rtype == SkylarkType.STRING) {
          return SkylarkType.STRING;
        }

        // list + list
        if (ltype.isList() && rtype.isList()) {
          return ltype.infer(rtype, "list concatenation", rhs.getLocation(), lhs.getLocation());
        }

        // dict + dict
        if (ltype.isDict() && rtype.isDict()) {
          return ltype.infer(rtype, "dict concatenation", rhs.getLocation(), lhs.getLocation());
        }

        // struct + struct
        if (ltype.isStruct() && rtype.isStruct()) {
          return SkylarkType.of(ClassObject.class);
        }

        if (ltype.isNset()) {
          if (rtype.isNset()) {
            return ltype.infer(rtype, "nested set", rhs.getLocation(), lhs.getLocation());
          } else if (rtype.isList()) {
            return ltype.infer(SkylarkType.of(SkylarkNestedSet.class, rtype.getGenericType1()),
                "nested set", rhs.getLocation(), lhs.getLocation());
          }
          if (rtype != SkylarkType.UNKNOWN) {
            throw new EvalException(getLocation(), String.format("can only concatenate nested sets "
                + "with other nested sets or list of items, not '" + rname + "'"));
          }
        }

        break;
      }

      case MULT: {
        // int * int
        if (ltype == SkylarkType.INT && rtype == SkylarkType.INT) {
          return SkylarkType.INT;
        }

        // string * int
        if (ltype == SkylarkType.STRING && rtype == SkylarkType.INT) {
          return SkylarkType.STRING;
        }

        // int * string
        if (ltype == SkylarkType.INT && rtype == SkylarkType.STRING) {
          return SkylarkType.STRING;
        }
        break;
      }

      case MINUS: {
        if (ltype == SkylarkType.INT && rtype == SkylarkType.INT) {
          return SkylarkType.INT;
        }
        break;
      }

      case PERCENT: {
        // int % int
        if (ltype == SkylarkType.INT && rtype == SkylarkType.INT) {
          return SkylarkType.INT;
        }

        // string % tuple, string % dict, string % anything-else
        if (ltype == SkylarkType.STRING) {
          return SkylarkType.STRING;
        }
        break;
      }

      case EQUALS_EQUALS:
      case NOT_EQUALS:
      case LESS:
      case LESS_EQUALS:
      case GREATER:
      case GREATER_EQUALS: {
        if (ltype != SkylarkType.UNKNOWN && !(Comparable.class.isAssignableFrom(ltype.getType()))) {
          throw new EvalException(getLocation(), lname + " is not comparable");
        }
        ltype.infer(rtype, "comparison", lhs.getLocation(), rhs.getLocation());
        return SkylarkType.BOOL;
      }

      case IN: {
        if (rtype.isList()
            || rtype.isSet()
            || rtype.isDict()
            || rtype == SkylarkType.STRING) {
          return SkylarkType.BOOL;
        } else {
          if (rtype != SkylarkType.UNKNOWN) {
            throw new EvalException(getLocation(), String.format("operand 'in' only works on "
                + "strings, dictionaries, lists, sets or tuples, not on a(n) %s",
                EvalUtils.getDataTypeNameFromClass(rtype.getType())));
          }
        }
      }
    } // endswitch

    if (ltype != SkylarkType.UNKNOWN && rtype != SkylarkType.UNKNOWN) {
      throw new EvalException(getLocation(),
          "unsupported operand types for '" + operator + "': '" + lname + "' and '" + rname + "'");
    }
    return SkylarkType.UNKNOWN;
  }
}
