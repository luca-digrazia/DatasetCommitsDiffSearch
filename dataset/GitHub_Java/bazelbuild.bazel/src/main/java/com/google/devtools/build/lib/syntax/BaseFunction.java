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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.events.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * BaseFunction is a base class for functions that have a FunctionSignature and optional default
 * values. Its {@link #callImpl} method converts the positional and named arguments into an array of
 * values corresponding to the parameters of the FunctionSignature, then calls the subclass's {@link
 * #call} method with this array.
 */
// TODO(adonovan): express the processArguments functionality of this class as a standalone function
// that takes signature and defaultValues as explicit parameters, and do away with this class. There
// is no real need for a concept of "StarlarkCallable with Signature", though a few places in Bazel
// rely on this concept. For example, the Args.add_all(map_all=..., map_each=...) parameters have
// their signatures checked to discover errors eagerly.
// Only StarlarkFunction needs getDefaultValues().
public abstract class BaseFunction implements StarlarkCallable {

  private final FunctionSignature signature;

  /**
   * The default values of optional parameters. Both the list and its elements may be null. A null
   * list is equivalent to a list containing only null elements.
   */
  // TODO(adonovan): investigate why null elements are permitted. I would expect one non-null
  // element per optional parameter, without exception.
  @Nullable private final List<Object> defaultValues;

  /** Returns the signature of this function. */
  public FunctionSignature getSignature() {
    return signature;
  }

  /**
   * Returns the tuple of parameter default values of this function value. May be null and may
   * contain null elements.
   */
  @Nullable
  public List<Object> getDefaultValues() {
    return defaultValues;
  }

  /** Constructs a BaseFunction with a given signature and default values. */
  BaseFunction(FunctionSignature signature, @Nullable List<Object> defaultValues) {
    this.signature = Preconditions.checkNotNull(signature);
    this.defaultValues = defaultValues;
    if (defaultValues != null) {
      Preconditions.checkArgument(defaultValues.size() == signature.numOptionals());
    }
  }

  /** Constructs a BaseFunction with a given signature without default values. */
  protected BaseFunction(FunctionSignature signature) {
    this(signature, /*defaultValues=*/ null);
  }

  /**
   * Process the caller-provided arguments into an array suitable for the callee (this function).
   */
  private Object[] processArguments(
      List<Object> args,
      @Nullable Map<String, Object> kwargs,
      @Nullable Location loc,
      @Nullable StarlarkThread thread)
      throws EvalException {
    Object[] arguments = new Object[signature.numParameters()];

    ImmutableList<String> names = signature.getParameterNames();

    // Note that this variable will be adjusted down if there are extra positionals,
    // after these extra positionals are dumped into starParam.
    int numPositionalArgs = args.size();

    int numMandatoryPositionalParams = signature.numMandatoryPositionals();
    int numOptionalPositionalParams = signature.numOptionalPositionals();
    int numMandatoryNamedOnlyParams = signature.numMandatoryNamedOnly();
    int numOptionalNamedOnlyParams = signature.numOptionalNamedOnly();
    boolean hasVarargs = signature.hasVarargs();
    boolean hasKwargs = signature.hasKwargs();
    int numPositionalParams = numMandatoryPositionalParams + numOptionalPositionalParams;
    int numNamedOnlyParams = numMandatoryNamedOnlyParams + numOptionalNamedOnlyParams;
    int numNamedParams = numPositionalParams + numNamedOnlyParams;
    int kwargIndex = names.size() - 1; // only valid if hasKwargs

    // (1) handle positional arguments
    if (hasVarargs) {
      // Nota Bene: we collect extra positional arguments in a (tuple,) rather than a [list],
      // and this is actually the same as in Python.
      int starParamIndex = numNamedParams;
      if (numPositionalArgs > numPositionalParams) {
        arguments[starParamIndex] =
            Tuple.copyOf(args.subList(numPositionalParams, numPositionalArgs));
        numPositionalArgs = numPositionalParams; // clip numPositionalArgs
      } else {
        arguments[starParamIndex] = Tuple.empty();
      }
    } else if (numPositionalArgs > numPositionalParams) {
      throw new EvalException(loc,
          numPositionalParams > 0
          ? "too many (" + numPositionalArgs + ") positional arguments in call to " + this
          : this + " does not accept positional arguments, but got " + numPositionalArgs);
    }

    for (int i = 0; i < numPositionalArgs; i++) {
      arguments[i] = args.get(i);
    }

    // (2) handle keyword arguments
    if (kwargs == null || kwargs.isEmpty()) {
      // Easy case (2a): there are no keyword arguments.
      // All arguments were positional, so check we had enough to fill all mandatory positionals.
      if (numPositionalArgs < numMandatoryPositionalParams) {
        throw new EvalException(loc, String.format(
            "insufficient arguments received by %s (got %s, expected at least %s)",
            this, numPositionalArgs, numMandatoryPositionalParams));
      }
      // We had no named argument, so fail if there were mandatory named-only parameters
      if (numMandatoryNamedOnlyParams > 0) {
        throw new EvalException(loc, String.format(
            "missing mandatory keyword arguments in call to %s", this));
      }
      // Fill in defaults for missing optional parameters, that were conveniently grouped together,
      // thanks to the absence of mandatory named-only parameters as checked above.
      if (defaultValues != null) {
        int j = numPositionalArgs - numMandatoryPositionalParams;
        int endOptionalParams = numPositionalParams + numOptionalNamedOnlyParams;
        for (int i = numPositionalArgs; i < endOptionalParams; i++) {
          arguments[i] = defaultValues.get(j++);
        }
      }
      // If there's a kwarg, it's empty.
      if (hasKwargs) {
        arguments[kwargIndex] = Dict.of(thread.mutability());
      }
    } else if (hasKwargs && numNamedParams == 0) {
      // Easy case (2b): there are no named parameters, but there is a **kwargs.
      // Therefore all keyword arguments go directly to the kwarg.
      // Note that *args and **kwargs themselves don't count as named.
      // Also note that no named parameters means no mandatory parameters that weren't passed,
      // and no missing optional parameters for which to use a default. Thus, no loops.
      // NB: not 2a means kwarg isn't null
      arguments[kwargIndex] = Dict.copyOf(thread.mutability(), kwargs);
    } else {
      // Hard general case (2c): some keyword arguments may correspond to named parameters
      Dict<String, Object> kwArg = hasKwargs ? Dict.of(thread.mutability()) : Dict.empty();

      // For nicer stabler error messages, start by checking against
      // an argument being provided both as positional argument and as keyword argument.
      ArrayList<String> bothPosKey = new ArrayList<>();
      for (int i = 0; i < numPositionalArgs; i++) {
        String name = names.get(i);
        if (kwargs.containsKey(name)) {
          bothPosKey.add(name);
        }
      }
      if (!bothPosKey.isEmpty()) {
        throw new EvalException(loc,
            String.format("argument%s '%s' passed both by position and by name in call to %s",
                (bothPosKey.size() > 1 ? "s" : ""), Joiner.on("', '").join(bothPosKey), this));
      }

      // Accept the arguments that were passed.
      for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
        String keyword = entry.getKey();
        Object value = entry.getValue();
        int pos = names.indexOf(keyword); // the list should be short, so linear scan is OK.
        if (0 <= pos && pos < numNamedParams) {
          arguments[pos] = value;
        } else {
          if (!hasKwargs) {
            List<String> unexpected = Ordering.natural().sortedCopy(Sets.difference(
                kwargs.keySet(), ImmutableSet.copyOf(names.subList(0, numNamedParams))));
            throw new EvalException(loc, String.format("unexpected keyword%s '%s' in call to %s",
                    unexpected.size() > 1 ? "s" : "", Joiner.on("', '").join(unexpected), this));
          }
          if (kwArg.containsKey(keyword)) {
            throw new EvalException(loc, String.format(
                "%s got multiple values for keyword argument '%s'", this, keyword));
          }
          kwArg.put(keyword, value, loc);
        }
      }
      if (hasKwargs) {
        arguments[kwargIndex] = Dict.copyOf(thread.mutability(), kwArg);
      }

      // Check that all mandatory parameters were filled in general case 2c.
      // Note: it's possible that numPositionalArgs > numMandatoryPositionalParams but that's OK.
      for (int i = numPositionalArgs; i < numMandatoryPositionalParams; i++) {
        if (arguments[i] == null) {
          throw new EvalException(loc, String.format(
              "missing mandatory positional argument '%s' while calling %s",
              names.get(i), this));
        }
      }

      int endMandatoryNamedOnlyParams = numPositionalParams + numMandatoryNamedOnlyParams;
      for (int i = numPositionalParams; i < endMandatoryNamedOnlyParams; i++) {
        if (arguments[i] == null) {
          throw new EvalException(loc, String.format(
              "missing mandatory named-only argument '%s' while calling %s",
              names.get(i), this));
        }
      }

      // Get defaults for those parameters that weren't passed.
      if (defaultValues != null) {
        for (int i = Math.max(numPositionalArgs, numMandatoryPositionalParams);
             i < numPositionalParams; i++) {
          if (arguments[i] == null) {
            arguments[i] = defaultValues.get(i - numMandatoryPositionalParams);
          }
        }
        int numMandatoryParams = numMandatoryPositionalParams + numMandatoryNamedOnlyParams;
        for (int i = numMandatoryParams + numOptionalPositionalParams; i < numNamedParams; i++) {
          if (arguments[i] == null) {
            arguments[i] = defaultValues.get(i - numMandatoryParams);
          }
        }
      }
    } // End of general case 2c for argument passing.

    return arguments;
  }

  @Override
  public Object callImpl(
      StarlarkThread thread,
      @Nullable FuncallExpression call,
      List<Object> args,
      Map<String, Object> kwargs)
      throws EvalException, InterruptedException {
    Location loc = call == null ? Location.BUILTIN : call.getLocation();
    Object[] arguments = processArguments(args, kwargs, loc, thread);
    return call(arguments, call, thread);
  }

  /**
   * Inner call to a BaseFunction subclasses need to @Override this method.
   *
   * @param args an array of argument values sorted as per the signature.
   * @param ast the source code for the function if user-defined
   * @param thread the Starlark thread for the call
   * @throws InterruptedException may be thrown in the function implementations.
   * @deprecated override the {@code callImpl} method directly.
   */
  // TODO(adonovan): the only remaining users of the "inner" protocol are:
  // - StarlarkFunction.
  // - PackageFactory.newPackageFunction, which goes to heroic lengths to reinvent the wheel.
  // - SkylarkProvider, which can be optimized by using callImpl directly once
  //   we've implemented the "vector" calling convention described in Eval.
  @Deprecated
  protected Object call(Object[] args, @Nullable FuncallExpression ast, StarlarkThread thread)
      throws EvalException, InterruptedException {
    throw new EvalException(
        (ast == null) ? Location.BUILTIN : ast.getLocation(),
        String.format("function %s not implemented", getName()));
  }

  /**
   * Render this object in the form of an equivalent Python function signature.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getName());
    sb.append('(');
    signature.toStringBuilder(sb, this::printDefaultValue);
    sb.append(')');
    return sb.toString();
  }

  private String printDefaultValue(int i) {
    Object v = defaultValues != null ? defaultValues.get(i) : null;
    return v != null ? Starlark.repr(v) : null;
  }

  @Override
  public boolean isImmutable() {
    return true;
  }

  @Override
  public void repr(Printer printer) {
    printer.append("<function " + getName() + ">");
  }
}
