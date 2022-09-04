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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.collect.compacthashset.CompactHashSet;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkInterfaceUtils;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkList.Tuple;
import com.google.devtools.build.lib.syntax.StarlarkSemantics.FlagIdentifier;
import com.google.devtools.build.lib.util.Pair;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Helper functions for implementing function calls. */
// TODO(adonovan): make this class private. Logically it is part of EvalUtils, and the public
// methods should move there, though some parts might better exposed as a group related to annotated
// methods. For ease of review, we'll do that in a follow-up change.
public final class CallUtils {

  private CallUtils() {} // uninstantiable

  private static CacheValue getCacheValue(Class<?> cls, StarlarkSemantics semantics) {
    if (cls == String.class) {
      cls = StringModule.class;
    }
    try {
      return cache.get(Pair.of(cls, semantics));
    } catch (ExecutionException ex) {
      throw new IllegalStateException("cache error", ex);
    }
  }

  // Information derived from a SkylarkCallable-annotated class and a StarlarkSemantics.
  private static class CacheValue {
    @Nullable MethodDescriptor selfCall;
    ImmutableMap<String, MethodDescriptor> fields; // sorted by Java method name
    ImmutableMap<String, MethodDescriptor> methods; // sorted by Java method name
  }

  // A cache of information derived from a SkylarkCallable-annotated class and a StarlarkSemantics.
  private static final LoadingCache<Pair<Class<?>, StarlarkSemantics>, CacheValue> cache =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<Pair<Class<?>, StarlarkSemantics>, CacheValue>() {
                @Override
                public CacheValue load(Pair<Class<?>, StarlarkSemantics> key) throws Exception {
                  Class<?> cls = key.first;
                  StarlarkSemantics semantics = key.second;

                  MethodDescriptor selfCall = null;
                  ImmutableMap.Builder<String, MethodDescriptor> methods = ImmutableMap.builder();
                  Map<String, MethodDescriptor> fields = new HashMap<>();

                  // Sort methods by Java name, for determinism.
                  Method[] classMethods = cls.getMethods();
                  Arrays.sort(classMethods, Comparator.comparing(Method::getName));
                  for (Method method : classMethods) {
                    // Synthetic methods lead to false multiple matches
                    if (method.isSynthetic()) {
                      continue;
                    }

                    // annotated?
                    SkylarkCallable callable = SkylarkInterfaceUtils.getSkylarkCallable(method);
                    if (callable == null) {
                      continue;
                    }

                    // enabled by semantics?
                    if (!semantics.isFeatureEnabledBasedOnTogglingFlags(
                        callable.enableOnlyWithFlag(), callable.disableWithFlag())) {
                      continue;
                    }

                    MethodDescriptor descriptor = MethodDescriptor.of(method, callable, semantics);

                    // self-call method?
                    if (callable.selfCall()) {
                      if (selfCall != null) {
                        throw new IllegalArgumentException(
                            String.format(
                                "Class %s has two selfCall methods defined", cls.getName()));
                      }
                      selfCall = descriptor;
                      continue;
                    }

                    // regular method
                    methods.put(callable.name(), descriptor);

                    // field method?
                    if (descriptor.isStructField()
                        && fields.put(callable.name(), descriptor) != null) {
                      // TODO(b/72113542): Validate with annotation processor instead of at runtime.
                      throw new IllegalArgumentException(
                          String.format(
                              "Class %s declares two structField methods named %s",
                              cls.getName(), callable.name()));
                    }
                  }

                  CacheValue value = new CacheValue();
                  value.selfCall = selfCall;
                  value.methods = methods.build();
                  value.fields = ImmutableMap.copyOf(fields);
                  return value;
                }
              });

  /**
   * Returns a map of methods and corresponding SkylarkCallable annotations of the methods of the
   * objClass class reachable from Skylark. Elements are sorted by Java method name (which is not
   * necessarily the same as Starlark attribute name).
   */
  public static ImmutableMap<Method, SkylarkCallable> collectSkylarkMethodsWithAnnotation(
      Class<?> objClass) {
    ImmutableMap.Builder<Method, SkylarkCallable> result = ImmutableMap.builder();
    for (MethodDescriptor desc :
        getCacheValue(objClass, StarlarkSemantics.DEFAULT_SEMANTICS).methods.values()) {
      result.put(desc.getMethod(), desc.getAnnotation());
    }
    return result.build();
  }

  /**
   * Returns the Skylark callable Method of objClass with structField=true and the given name.
   *
   * @deprecated use {@link #getStructField(StarlarkSemantics, Class, String)} instead
   */
  @Deprecated
  public static MethodDescriptor getStructField(Class<?> objClass, String methodName) {
    return getStructField(StarlarkSemantics.DEFAULT_SEMANTICS, objClass, methodName);
  }

  /** Returns the Skylark callable Method of objClass with structField=true and the given name. */
  public static MethodDescriptor getStructField(
      StarlarkSemantics semantics, Class<?> objClass, String name) {
    return getCacheValue(objClass, semantics).fields.get(name);
  }

  /**
   * Returns the list of names of Skylark callable Methods of objClass with structField=true.
   *
   * @deprecated use {@link #getStructFieldNames(StarlarkSemantics, Class)} instead
   */
  @Deprecated
  public static Set<String> getStructFieldNames(Class<?> objClass) {
    return getStructFieldNames(StarlarkSemantics.DEFAULT_SEMANTICS, objClass);
  }

  /** Returns the list of names of Skylark callable Methods of objClass with structField=true. */
  public static Set<String> getStructFieldNames(StarlarkSemantics semantics, Class<?> objClass) {
    return getCacheValue(objClass, semantics).fields.keySet();
  }

  /**
   * Returns the list of Skylark callable Methods of objClass with the given name.
   *
   * @deprecated use {@link #getMethods(StarlarkSemantics, Class, String)} instead
   */
  @Deprecated
  public static MethodDescriptor getMethod(Class<?> objClass, String methodName) {
    return getMethod(StarlarkSemantics.DEFAULT_SEMANTICS, objClass, methodName);
  }

  /** Returns the list of Skylark callable Methods of objClass with the given name. */
  public static MethodDescriptor getMethod(
      StarlarkSemantics semantics, Class<?> objClass, String methodName) {
    return getCacheValue(objClass, semantics).methods.get(methodName);
  }

  /**
   * Returns a set of the Skylark name of all Skylark callable methods for object of type {@code
   * objClass}.
   *
   * @deprecated use {@link #getMethodNames(StarlarkSemantics, Class)} instead
   */
  @Deprecated
  public static Set<String> getMethodNames(Class<?> objClass) {
    return getMethodNames(StarlarkSemantics.DEFAULT_SEMANTICS, objClass);
  }

  /**
   * Returns a set of the Skylark name of all Skylark callable methods for object of type {@code
   * objClass}.
   */
  public static Set<String> getMethodNames(StarlarkSemantics semantics, Class<?> objClass) {
    return getCacheValue(objClass, semantics).methods.keySet();
  }

  /**
   * Returns a {@link MethodDescriptor} object representing a function which calls the selfCall java
   * method of the given object (the {@link SkylarkCallable} method with {@link
   * SkylarkCallable#selfCall()} set to true). Returns null if no such method exists.
   */
  @Nullable
  public static MethodDescriptor getSelfCallMethodDescriptor(
      StarlarkSemantics semantics, Class<?> objClass) {
    return getCacheValue(objClass, semantics).selfCall;
  }

  /**
   * Returns a {@link BuiltinCallable} representing a {@link SkylarkCallable}-annotated instance
   * method of a given object with the given Java method name.
   */
  // TODO(adonovan): replace with EvalUtils.getAttr, once the latter doesn't require
  // a Thread and Location.
  public static BuiltinCallable getBuiltinCallable(Object obj, String methodName) {
    Class<?> objClass = obj.getClass();
    MethodDescriptor methodDescriptor = getMethod(objClass, methodName);
    if (methodDescriptor == null) {
      throw new IllegalStateException(String.format(
          "Expected a method named '%s' in %s, but found none",
          methodName, objClass));
    }
    return new BuiltinCallable(obj, methodName);
  }

  /**
   * Invokes the given structField=true method and returns the result.
   *
   * <p>The given method must <b>not</b> require extra-interpreter parameters, such as {@link
   * StarlarkThread}. This method throws {@link IllegalArgumentException} for violations.
   *
   * @param methodDescriptor the descriptor of the method to invoke
   * @param fieldName the name of the struct field
   * @param obj the object on which to invoke the method
   * @return the method return value
   * @throws EvalException if there was an issue evaluating the method
   */
  public static Object invokeStructField(
      MethodDescriptor methodDescriptor, String fieldName, Object obj)
      throws EvalException, InterruptedException {
    Preconditions.checkArgument(
        methodDescriptor.isStructField(), "Can only be invoked on structField callables");
    Preconditions.checkArgument(
        !methodDescriptor.isUseStarlarkThread()
            || !methodDescriptor.isUseStarlarkSemantics()
            || !methodDescriptor.isUseLocation(),
        "Cannot be invoked on structField callables with extra interpreter params");
    return methodDescriptor.call(obj, new Object[0], Location.BUILTIN, null);
  }

  /**
   * Converts Starlark-defined arguments to an array of argument {@link Object}s that may be passed
   * to a given callable-from-Starlark Java method.
   *
   * @param method a descriptor for a java method callable from Starlark
   * @param objClass the class of the java object on which to invoke this method
   * @param args a list of positional Starlark arguments
   * @param kwargs a map of keyword Starlark arguments; keys are the used keyword, and values are
   *     their corresponding values in the method call
   * @param thread the Starlark thread for the call
   * @return the array of arguments which may be passed to {@link MethodDescriptor#call}
   * @throws EvalException if the given set of arguments are invalid for the given method. For
   *     example, if any arguments are of unexpected type, or not all mandatory parameters are
   *     specified by the user
   */
  static Object[] convertStarlarkArgumentsToJavaMethodArguments(
      StarlarkThread thread,
      FuncallExpression call,
      MethodDescriptor method,
      Class<?> objClass,
      List<Object> args,
      Map<String, Object> kwargs)
      throws EvalException {
    Preconditions.checkArgument(!method.isStructField(),
        "struct field methods should be handled by DotExpression separately");

    ImmutableList<ParamDescriptor> parameters = method.getParameters();
    // *args, **kwargs, location, ast, thread, skylark semantics
    final int extraArgsCount = 6;
    List<Object> builder = new ArrayList<>(parameters.size() + extraArgsCount);
    boolean acceptsExtraArgs = method.isAcceptsExtraArgs();
    boolean acceptsExtraKwargs = method.isAcceptsExtraKwargs();

    int argIndex = 0;

    // Process parameters specified in callable.parameters()
    // Many methods don't have any kwargs, so don't allocate a new hash set in that case.
    Set<String> keys =
        kwargs.isEmpty() ? ImmutableSet.of() : CompactHashSet.create(kwargs.keySet());
    // Positional parameters are always enumerated before non-positional parameters,
    // And default-valued positional parameters are always enumerated after other positional
    // parameters. These invariants are validated by the SkylarkCallable annotation processor.
    // Index is used deliberately, since usage of iterators adds a significant overhead
    for (int i = 0; i < parameters.size(); ++i) {
      ParamDescriptor param = parameters.get(i);
      SkylarkType type = param.getSkylarkType();
      Object value;

      if (param.isDisabledInCurrentSemantics()) {
        value =
            SkylarkSignatureProcessor.getDefaultValue(param.getName(), param.getValueOverride());
        builder.add(value);
        continue;
      }

      if (argIndex < args.size() && param.isPositional()) { // Positional args and params remain.
        value = args.get(argIndex);
        if (!type.contains(value)) {
          throw argumentMismatchException(
              call,
              String.format(
                  "expected value of type '%s' for parameter '%s'", type, param.getName()),
              method,
              objClass);
        }
        if (param.isNamed() && keys.contains(param.getName())) {
          throw argumentMismatchException(
              call,
              String.format("got multiple values for keyword argument '%s'", param.getName()),
              method,
              objClass);
        }
        argIndex++;
      } else { // No more positional arguments, or no more positional parameters.
        if (param.isNamed() && !keys.isEmpty() && keys.remove(param.getName())) {
          // Param specified by keyword argument.
          value = kwargs.get(param.getName());
          if (!type.contains(value)) {
            throw argumentMismatchException(
                call,
                String.format(
                    "expected value of type '%s' for parameter '%s'", type, param.getName()),
                method,
                objClass);
          }
        } else { // Param not specified by user. Use default value.
          if (param.getDefaultValue().isEmpty()) {
            throw unspecifiedParameterException(call, param, method, objClass, kwargs);
          }
          value =
              SkylarkSignatureProcessor.getDefaultValue(param.getName(), param.getDefaultValue());
        }
      }
      if (!param.isNoneable() && value instanceof NoneType) {
        throw argumentMismatchException(
            call,
            String.format("parameter '%s' cannot be None", param.getName()),
            method,
            objClass);
      }
      builder.add(value);
    }

    ImmutableList<Object> extraArgs = ImmutableList.of();
    if (argIndex < args.size()) {
      if (acceptsExtraArgs) {
        ImmutableList.Builder<Object> extraArgsBuilder =
            ImmutableList.builderWithExpectedSize(args.size() - argIndex);
        for (; argIndex < args.size(); argIndex++) {
          extraArgsBuilder.add(args.get(argIndex));
        }
        extraArgs = extraArgsBuilder.build();
      } else {
        throw argumentMismatchException(
            call,
            String.format(
                "expected no more than %s positional arguments, but got %s", argIndex, args.size()),
            method,
            objClass);
      }
    }
    ImmutableMap<String, Object> extraKwargs = ImmutableMap.of();
    if (!keys.isEmpty()) {
      if (acceptsExtraKwargs) {
        ImmutableMap.Builder<String, Object> extraKwargsBuilder =
            ImmutableMap.builderWithExpectedSize(keys.size());
        for (String key : keys) {
          extraKwargsBuilder.put(key, kwargs.get(key));
        }
        extraKwargs = extraKwargsBuilder.build();
      } else {
        throw unexpectedKeywordArgumentException(call, keys, method, objClass, thread);
      }
    }

    // Then add any skylark-interpreter arguments (for example kwargs or the StarlarkThread).
    if (acceptsExtraArgs) {
      builder.add(Tuple.copyOf(extraArgs));
    }
    if (acceptsExtraKwargs) {
      builder.add(SkylarkDict.copyOf(thread, extraKwargs));
    }
    appendExtraInterpreterArgs(builder, method, call, call.getLocation(), thread);

    return builder.toArray();
  }

  private static EvalException unspecifiedParameterException(
      FuncallExpression call,
      ParamDescriptor param,
      MethodDescriptor method,
      Class<?> objClass,
      Map<String, Object> kwargs) {
    if (kwargs.containsKey(param.getName())) {
      return argumentMismatchException(
          call,
          String.format("parameter '%s' may not be specified by name", param.getName()),
          method,
          objClass);
    } else {
      return argumentMismatchException(
          call,
          String.format("parameter '%s' has no default value", param.getName()),
          method,
          objClass);
    }
  }

  private static EvalException unexpectedKeywordArgumentException(
      FuncallExpression call,
      Set<String> unexpectedKeywords,
      MethodDescriptor method,
      Class<?> objClass,
      StarlarkThread thread) {
    // Check if any of the unexpected keywords are for parameters which are disabled by the
    // current semantic flags. Throwing an error with information about the misconfigured
    // semantic flag is likely far more helpful.
    for (ParamDescriptor param : method.getParameters()) {
      if (param.isDisabledInCurrentSemantics() && unexpectedKeywords.contains(param.getName())) {
        FlagIdentifier flagIdentifier = param.getFlagResponsibleForDisable();
        // If the flag is True, it must be a deprecation flag. Otherwise it's an experimental flag.
        if (thread.getSemantics().flagValue(flagIdentifier)) {
          return new EvalException(
              call.getLocation(),
              String.format(
                  "parameter '%s' is deprecated and will be removed soon. It may be "
                      + "temporarily re-enabled by setting --%s=false",
                  param.getName(), flagIdentifier.getFlagName()));
        } else {
          return new EvalException(
              call.getLocation(),
              String.format(
                  "parameter '%s' is experimental and thus unavailable with the current "
                      + "flags. It may be enabled by setting --%s",
                  param.getName(), flagIdentifier.getFlagName()));
        }
      }
    }

    return argumentMismatchException(
        call,
        String.format(
            "unexpected keyword%s %s",
            unexpectedKeywords.size() > 1 ? "s" : "",
            Joiner.on(", ").join(Iterables.transform(unexpectedKeywords, s -> "'" + s + "'"))),
        method,
        objClass);
  }

  private static EvalException argumentMismatchException(
      FuncallExpression call,
      String errorDescription,
      MethodDescriptor methodDescriptor,
      Class<?> objClass) {
    if (methodDescriptor.isSelfCall() || SkylarkInterfaceUtils.hasSkylarkGlobalLibrary(objClass)) {
      return new EvalException(
          call.getLocation(),
          String.format(
              "%s, for call to function %s",
              errorDescription, formatMethod(objClass, methodDescriptor)));
    } else {
      return new EvalException(
          call.getLocation(),
          String.format(
              "%s, for call to method %s of '%s'",
              errorDescription,
              formatMethod(objClass, methodDescriptor),
              EvalUtils.getDataTypeNameFromClass(objClass)));
    }
  }

  private static EvalException missingMethodException(
      FuncallExpression call, Class<?> objClass, String methodName) {
    return new EvalException(
        call.getLocation(),
        String.format(
            "type '%s' has no method %s()",
            EvalUtils.getDataTypeNameFromClass(objClass), methodName));
  }

  /**
   * Returns the extra interpreter arguments for the given {@link SkylarkCallable}, to be added at
   * the end of the argument list for the callable.
   *
   * <p>This method accepts null {@code ast} only if {@code callable.useAst()} is false. It is up to
   * the caller to validate this invariant.
   */
  static List<Object> extraInterpreterArgs(
      MethodDescriptor method,
      @Nullable FuncallExpression ast,
      Location loc,
      StarlarkThread thread) {
    List<Object> builder = new ArrayList<>();
    appendExtraInterpreterArgs(builder, method, ast, loc, thread);
    return ImmutableList.copyOf(builder);
  }

  /**
   * Same as {@link #extraInterpreterArgs(MethodDescriptor, FuncallExpression, Location,
   * StarlarkThread)} but appends args to a passed {@code builder} to avoid unnecessary allocations
   * of intermediate instances.
   *
   * @see #extraInterpreterArgs(MethodDescriptor, FuncallExpression, Location, StarlarkThread)
   */
  private static void appendExtraInterpreterArgs(
      List<Object> builder,
      MethodDescriptor method,
      @Nullable FuncallExpression ast,
      Location loc,
      StarlarkThread thread) {
    if (method.isUseLocation()) {
      builder.add(loc);
    }
    if (method.isUseAst()) {
      if (ast == null) {
        throw new IllegalArgumentException("Callable expects to receive ast: " + method.getName());
      }
      builder.add(ast);
    }
    if (method.isUseStarlarkThread()) {
      builder.add(thread);
    }
    if (method.isUseStarlarkSemantics()) {
      builder.add(thread.getSemantics());
    }
  }

  private static String formatMethod(Class<?> objClass, MethodDescriptor methodDescriptor) {
    ImmutableList.Builder<String> argTokens = ImmutableList.builder();
    // Skip first parameter ('self') for StringModule, as its a special case.
    Iterable<ParamDescriptor> parameters =
        objClass == StringModule.class
            ? Iterables.skip(methodDescriptor.getParameters(), 1)
            : methodDescriptor.getParameters();

    for (ParamDescriptor paramDescriptor : parameters) {
      if (!paramDescriptor.isDisabledInCurrentSemantics()) {
        if (paramDescriptor.getDefaultValue().isEmpty()) {
          argTokens.add(paramDescriptor.getName());
        } else {
          argTokens.add(paramDescriptor.getName() + " = " + paramDescriptor.getDefaultValue());
        }
      }
    }
    if (methodDescriptor.isAcceptsExtraArgs()) {
      argTokens.add("*args");
    }
    if (methodDescriptor.isAcceptsExtraKwargs()) {
      argTokens.add("**kwargs");
    }
    return methodDescriptor.getName() + "(" + Joiner.on(", ").join(argTokens.build()) + ")";
  }

  @VisibleForTesting
  public static boolean isNamespace(Class<?> classObject) {
    return classObject.isAnnotationPresent(SkylarkModule.class)
        && classObject.getAnnotation(SkylarkModule.class).namespace();
  }

  /** Invoke object.method() and return the result. */
  static Object callMethod(
      StarlarkThread thread,
      FuncallExpression call,
      Object object,
      ArrayList<Object> posargs,
      Map<String, Object> kwargs,
      String methodName,
      Location dotLocation)
      throws EvalException, InterruptedException {
    // Case 1: Object is a String. String is an unusual special case.
    if (object instanceof String) {
      return callStringMethod(thread, call, (String) object, methodName, posargs, kwargs);
    }

    // Case 2: Object is a Java object with a matching @SkylarkCallable method.
    // This is an optimization. For 'foo.bar()' where 'foo' is a java object with a callable
    // java method 'bar()', this avoids evaluating 'foo.bar' in isolation (which would require
    // creating a throwaway function-like object).
    MethodDescriptor methodDescriptor =
        CallUtils.getMethod(thread.getSemantics(), object.getClass(), methodName);
    if (methodDescriptor != null && !methodDescriptor.isStructField()) {
      Object[] javaArguments =
          convertStarlarkArgumentsToJavaMethodArguments(
              thread, call, methodDescriptor, object.getClass(), posargs, kwargs);
      return methodDescriptor.call(object, javaArguments, call.getLocation(), thread);
    }

    // Case 3: Object is a function registered with the BuiltinRegistry.
    // TODO(cparsons): The runtime builtin registry is deprecated and only used by non-Bazel users
    // of the Starlark interpreter. Remove its use.
    BaseFunction legacyRuntimeFunction =
        Runtime.getBuiltinRegistry().getFunction(object.getClass(), methodName);
    if (legacyRuntimeFunction != null) {
      return callLegacyBuiltinRegistryFunction(
          call, legacyRuntimeFunction, object, posargs, kwargs, thread);
    }

    // Case 4: All other cases. Evaluate "foo.bar" as a dot expression, then try to invoke it
    // as a callable.
    Object functionObject = EvalUtils.getAttr(thread, dotLocation, object, methodName);
    if (functionObject == null) {
      throw missingMethodException(call, object.getClass(), methodName);
    } else {
      return call(thread, call, functionObject, posargs, kwargs);
    }
  }

  private static Object callLegacyBuiltinRegistryFunction(
      FuncallExpression call,
      BaseFunction legacyRuntimeFunction,
      Object object,
      ArrayList<Object> posargs,
      Map<String, Object> kwargs,
      StarlarkThread thread)
      throws EvalException, InterruptedException {
    if (!isNamespace(object.getClass())) {
      posargs.add(0, object);
    }
    return legacyRuntimeFunction.call(posargs, kwargs, call, thread);
  }

  private static Object callStringMethod(
      StarlarkThread thread,
      FuncallExpression call,
      String objValue,
      String methodName,
      ArrayList<Object> posargs,
      Map<String, Object> kwargs)
      throws InterruptedException, EvalException {
    // String is a special case, since it can't be subclassed. Methods on strings defer
    // to StringModule, and thus need to include the actual string as a 'self' parameter.
    posargs.add(0, objValue);

    MethodDescriptor method = getMethod(thread.getSemantics(), StringModule.class, methodName);
    if (method == null) {
      throw missingMethodException(call, StringModule.class, methodName);
    }

    Object[] javaArguments =
        convertStarlarkArgumentsToJavaMethodArguments(
            thread, call, method, StringModule.class, posargs, kwargs);
    return method.call(StringModule.INSTANCE, javaArguments, call.getLocation(), thread);
  }

  static Object call(
      StarlarkThread thread,
      FuncallExpression call,
      Object fn,
      ArrayList<Object> posargs,
      Map<String, Object> kwargs)
      throws EvalException, InterruptedException {

    if (fn instanceof StarlarkCallable) {
      StarlarkCallable callable = (StarlarkCallable) fn;
      return callable.call(posargs, ImmutableMap.copyOf(kwargs), call, thread);
    }

    MethodDescriptor selfCall = getSelfCallMethodDescriptor(thread.getSemantics(), fn.getClass());
    if (selfCall != null) {
      Object[] javaArguments =
          convertStarlarkArgumentsToJavaMethodArguments(
              thread, call, selfCall, fn.getClass(), posargs, kwargs);
      return selfCall.call(fn, javaArguments, call.getLocation(), thread);
    }

    throw new EvalException(
        call.getLocation(), "'" + EvalUtils.getDataTypeName(fn) + "' object is not callable");
  }

}
