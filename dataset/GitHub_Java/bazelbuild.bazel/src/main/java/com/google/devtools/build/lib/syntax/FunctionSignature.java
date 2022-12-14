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
import com.google.common.collect.ImmutableList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Function Signatures for BUILD language (same as Python)
 *
 * <p>Skylark's function signatures are just like Python3's.
 * A function may have 6 kinds of arguments:
 * positional mandatory, positional optional, positional rest (aka *star argument),
 * key-only mandatory, key-only optional, key rest (aka **star_star argument).
 * A caller may specify all arguments but the *star and **star_star arguments by name,
 * and thus all mandatory and optional arguments are named arguments.
 *
 * <p>To enable various optimizations in the argument processing routine,
 * we sort arguments according the following constraints, enabling corresponding optimizations:
 * <ol>
 * <li>the positional mandatories come just before the positional optionals,
 *   so they can be filled in one go.
 * <li>the optionals are grouped together, so we can iterate over them in one go.
 * <li>positionals come first, so it's easy to prepend extra positional arguments such as "self"
 *   to an argument list, and we optimize for the common case of no key-only mandatory parameters.
 *   key-only parameters are thus grouped together.
 *   positional mandatory and key-only mandatory parameters are separate,
 *   but there no loop over a contiguous chunk of them, anyway.
 * <li>the named are all grouped together, with star and star_star rest arguments coming last.
 * </ol>
 *
 * <p>Parameters are thus sorted in the following obvious order:
 * positional mandatory arguments (if any), positional optional arguments (if any),
 * key-only optional arguments (if any), key-only mandatory arguments (if any),
 * then star argument (if any), then star_star argument (if any).
 */
public abstract class FunctionSignature implements Serializable {

  /**
   * The shape of a FunctionSignature, without names
   */
  public abstract static class Shape implements Serializable {

    /** Create a function signature */
    public static Shape create(
        int mandatoryPositionals,
        int optionalPositionals,
        int mandatoryNamedOnly,
        int optionalNamedOnly,
        boolean starArg,
        boolean kwArg) {
      Preconditions.checkArgument(
          0 <= mandatoryPositionals && 0 <= optionalPositionals
          && 0 <= mandatoryNamedOnly && 0 <= optionalNamedOnly);
      return new AutoValueFunctionSignatureShape(
          mandatoryPositionals, optionalPositionals,
          mandatoryNamedOnly, optionalNamedOnly, starArg, kwArg);
    }

    // These abstract getters specify the actual argument count fields to be defined by AutoValue.
    /** number of mandatory positional arguments */
    public abstract int getMandatoryPositionals();

    /** number of optional positional arguments */
    public abstract int getOptionalPositionals();

    /** number of mandatory named-only arguments. */
    public abstract int getMandatoryNamedOnly();

    /** number of optional named-only arguments */
    public abstract int getOptionalNamedOnly();

    /** indicator for presence of a star argument for extra positional arguments */
    public abstract boolean hasStarArg();

    /** indicator for presence of a star-star argument for extra keyword arguments */
    public abstract boolean hasKwArg();


    // The are computed argument counts
    /** number of optional positional arguments. */
    public int getPositionals() {
      return getMandatoryPositionals() + getOptionalPositionals();
    }

    /** number of optional named-only arguments. */
    public int getNamedOnly() {
      return getMandatoryNamedOnly() + getOptionalNamedOnly();
    }

    /** number of optional arguments. */
    public int getOptionals() {
      return getOptionalPositionals() + getOptionalNamedOnly();
    }

    /** total number of arguments */
    public int getArguments() {
      return getPositionals() + getNamedOnly() + (hasStarArg() ? 1 : 0) + (hasKwArg() ? 1 : 0);
    }
  }

  /**
   * Signatures proper.
   *
   * <p>A signature is a Shape and an ImmutableList of argument variable names
   * NB: we assume these lists are short, so we may do linear scans.
   */
  public static FunctionSignature create(Shape shape, ImmutableList<String> names) {
    Preconditions.checkArgument(names.size() == shape.getArguments());
    return new AutoValueFunctionSignature(shape, names);
  }

  // Field definition (details filled in by AutoValue)
  /** The shape */
  public abstract Shape getShape();

  /** The names */
  public abstract ImmutableList<String> getNames();

  /** append a representation of this signature to a string buffer. */
  public StringBuffer toStringBuffer(StringBuffer sb) {
    return WithValues.<Object, SkylarkType>create(this).toStringBuffer(sb);
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    toStringBuffer(sb);
    return sb.toString();
  }


  /**
   * FunctionSignature.WithValues: also specifies a List of default values and types.
   *
   * <p>The lists can be null, which is an optimized path for specifying all null values.
   *
   * <p>Note that if some values can be null (for BuiltinFunction, not for UserDefinedFunction),
   * you should use an ArrayList; otherwise, we recommend an ImmutableList.
   *
   * <p>V is the class of defaultValues and T is the class of types.
   * When parsing a function definition at compile-time, they are &lt;Expression, Expression&gt;;
   * when processing a @SkylarkBuiltin annotation at build-time, &lt;Object, SkylarkType&gt;.
   */
  public abstract static class WithValues<V, T> implements Serializable {

    // The fields
    /** The underlying signature with parameter shape and names */
    public abstract FunctionSignature getSignature();

    /** The default values (if any) as a List of one per optional parameter.
     * We might have preferred ImmutableList, but we care about
     * supporting null's for some BuiltinFunction's, and we don't spit on speed.
     */
    @Nullable public abstract List<V> getDefaultValues();

    /** The parameter types (if specified) as a List of one per parameter, including * and **.
     * We might have preferred ImmutableList, but we care about supporting null's
     * so we can take shortcut for untyped values.
     */
    @Nullable public abstract List<T> getTypes();

    /**
     * Create a signature with (default and type) values.
     * If you supply mutable List's, we trust that you won't modify them afterwards.
     */
    public static <V, T> WithValues<V, T> create(FunctionSignature signature,
        @Nullable List<V> defaultValues, @Nullable List<T> types) {
      Shape shape = signature.getShape();
      Preconditions.checkArgument(defaultValues == null
          || defaultValues.size() == shape.getOptionals());
      Preconditions.checkArgument(types == null
          || types.size() == shape.getArguments());
      return new AutoValueFunctionSignatureWithValues<V, T>(signature, defaultValues, types);
    }

    public static <V, T> WithValues<V, T> create(FunctionSignature signature,
        @Nullable List<V> defaultValues) {
      return create(signature, defaultValues, null);
    }

    public static <V, T> WithValues<V, T> create(FunctionSignature signature,
        @Nullable V[] defaultValues) {
      return create(signature, Arrays.asList(defaultValues), null);
    }

    public static <V, T> WithValues<V, T> create(FunctionSignature signature) {
      return create(signature, null, null);
    }

    /**
     * Parse a list of Parameter into a FunctionSignature.
     *
     * <p>To be used both by the Parser and by the SkylarkBuiltin annotation processor.
     */
    public static <V, T> WithValues<V, T> of(Iterable<Parameter<V, T>> parameters)
        throws SignatureException {
      int mandatoryPositionals = 0;
      int optionalPositionals = 0;
      int mandatoryNamedOnly = 0;
      int optionalNamedOnly = 0;
      boolean hasStarStar = false;
      boolean hasStar = false;
      @Nullable String star = null;
      @Nullable String starStar = null;
      @Nullable T starType = null;
      @Nullable T starStarType = null;
      ArrayList<String> params = new ArrayList<>();
      ArrayList<V> defaults = new ArrayList<>();
      ArrayList<T> types = new ArrayList<>();
      // mandatory named-only parameters are kept aside to be spliced after the optional ones.
      ArrayList<String> mandatoryNamedOnlyParams = new ArrayList<>();
      ArrayList<T> mandatoryNamedOnlyTypes = new ArrayList<>();
      boolean defaultRequired = false; // true after mandatory positionals and before star.
      Set<String> paramNameSet = new HashSet<>(); // set of names, to avoid duplicates

      for (Parameter<V, T> param : parameters) {
        if (hasStarStar) {
          throw new SignatureException("illegal parameter after star-star parameter", param);
        }
        @Nullable String name = param.getName();
        @Nullable T type = param.getType();
        if (param.hasName()) {
          if (paramNameSet.contains(name)) {
            throw new SignatureException("duplicate parameter name in function definition", param);
          }
          paramNameSet.add(name);
        }
        if (param.isStarStar()) {
          hasStarStar = true;
          starStar = name;
          starStarType = type;
        } else if (param.isStar()) {
          if (hasStar) {
            throw new SignatureException(
                "duplicate star parameter in function definition", param);
          }
          hasStar = true;
          defaultRequired = false;
          if (param.hasName()) {
            star = name;
            starType = type;
          }
        } else if (hasStar && param.isMandatory()) {
          // mandatory named-only, added contiguously at the end, to simplify calls
          mandatoryNamedOnlyParams.add(name);
          mandatoryNamedOnlyTypes.add(type);
          mandatoryNamedOnly++;
        } else {
          params.add(name);
          types.add(type);
          if (param.isMandatory()) {
            if (defaultRequired) {
              throw new SignatureException(
                  "a mandatory positional parameter must not follow an optional parameter",
                  param);
            }
            mandatoryPositionals++;
          } else { // At this point, it's an optional parameter
            defaults.add(param.getDefaultValue());
            if (hasStar) {
              // named-only optional
              optionalNamedOnly++;
            } else {
              optionalPositionals++;
              defaultRequired = true;
            }
          }
        }
      }
      params.addAll(mandatoryNamedOnlyParams);
      types.addAll(mandatoryNamedOnlyTypes);
      if (star != null) {
        params.add(star);
        types.add(starType);
      }
      if (starStar != null) {
        params.add(starStar);
        types.add(starStarType);
      }
      return WithValues.<V, T>create(
          FunctionSignature.create(
              Shape.create(
                  mandatoryPositionals, optionalPositionals,
                  mandatoryNamedOnly, optionalNamedOnly,
                  star != null, starStar != null),
              ImmutableList.<String>copyOf(params)),
          FunctionSignature.<V>valueListOrNull(defaults),
          FunctionSignature.<T>valueListOrNull(types));
    }

    /**
     * Append a representation of this signature to a string buffer.
     */
    public StringBuffer toStringBuffer(final StringBuffer sb) {
      FunctionSignature signature = getSignature();
      Shape shape = signature.getShape();
      final ImmutableList<String> names = signature.getNames();
      @Nullable final List<V> defaultValues = getDefaultValues();
      @Nullable final List<T> types = getTypes();

      int mandatoryPositionals = shape.getMandatoryPositionals();
      int optionalPositionals = shape.getOptionalPositionals();
      int mandatoryNamedOnly = shape.getMandatoryNamedOnly();
      int optionalNamedOnly = shape.getOptionalNamedOnly();
      boolean starArg = shape.hasStarArg();
      boolean kwArg = shape.hasKwArg();
      int positionals = mandatoryPositionals + optionalPositionals;
      int namedOnly = mandatoryNamedOnly + optionalNamedOnly;
      int named = positionals + namedOnly;
      int args = named + (starArg ? 1 : 0) + (kwArg ? 1 : 0);
      int endOptionals = positionals + optionalNamedOnly;
      boolean hasStar = starArg || (namedOnly > 0);
      int iStarArg = named;
      int iKwArg = args - 1;

      class Show {
        private boolean isMore = false;
        private int j = 0;

        public void comma() {
          if (isMore) { sb.append(", "); }
          isMore = true;
        }
        public void type(int i) {
          if (types != null && types.get(i) != null) {
            sb.append(" : ").append(types.get(i).toString());
          }
        }
        public void mandatory(int i) {
          comma();
          sb.append(names.get(i));
          type(i);
        }
        public void optional(int i) {
          mandatory(i);
          sb.append(" = ").append((defaultValues == null)
              ? "null" : String.valueOf(defaultValues.get(j++)));
        }
      };
      Show show = new Show();

      int i = 0;
      for (; i < mandatoryPositionals; i++) {
        show.mandatory(i);
      }
      for (; i < positionals; i++) {
        show.optional(i);
      }
      if (hasStar) {
        show.comma();
        sb.append("*");
        if (starArg) {
          sb.append(names.get(iStarArg));
        }
      }
      for (; i < endOptionals; i++) {
        show.optional(i);
      }
      for (; i < named; i++) {
        show.mandatory(i);
      }
      if (kwArg) {
        show.comma();
        sb.append("**");
        sb.append(names.get(iKwArg));
      }

      return sb;
    }

    @Override
    public String toString() {
      StringBuffer sb = new StringBuffer();
      toStringBuffer(sb);
      return sb.toString();
    }
  }

  /** The given List, or null if all the list elements are null. */
  @Nullable public static <E> List<E> valueListOrNull(List<E> list) {
    if (list == null) {
      return null;
    }
    for (E value : list) {
      if (value != null) {
        return list;
      }
    }
    return null;
  }

  /**
   * Constructs a function signature (with names) from signature description and names.
   * This method covers the general case.
   * The number of optional named-only parameters is deduced from the other arguments.
   *
   * @param numMandatoryPositionals an int for the number of mandatory positional parameters
   * @param numOptionalPositionals an int for the number of optional positional parameters
   * @param numMandatoryNamedOnly an int for the number of mandatory named-only parameters
   * @param starArg a boolean for whether there is a starred parameter
   * @param kwArg a boolean for whether there is a star-starred parameter
   * @param names an Array of String for the parameter names
   * @return a FunctionSignature
   */
  public static FunctionSignature of(int numMandatoryPositionals, int numOptionalPositionals,
      int numMandatoryNamedOnly, boolean starArg, boolean kwArg, String... names) {
    return create(Shape.create(
        numMandatoryPositionals,
        numOptionalPositionals,
        numMandatoryNamedOnly,
        names.length - (kwArg ? 1 : 0) - (starArg ? 1 : 0)
            - numMandatoryPositionals - numOptionalPositionals - numMandatoryNamedOnly,
        starArg, kwArg),
        ImmutableList.<String>copyOf(names));
  }

  /**
   * Constructs a function signature from mandatory positional argument names.
   *
   * @param names an Array of String for the positional parameter names
   * @return a FunctionSignature
   */
  public static FunctionSignature of(String... names) {
    return of(names.length, 0, 0, false, false, names);
  }

  /**
   * Constructs a function signature from positional argument names.
   *
   * @param numMandatory an int for the number of mandatory positional parameters
   * @param names an Array of String for the positional parameter names
   * @return a FunctionSignature
   */
  public static FunctionSignature of(int numMandatory, String... names) {
    return of(numMandatory, names.length - numMandatory, 0, false, false, names);
  }

  /**
   * Constructs a function signature from mandatory named-only argument names.
   *
   * @param names an Array of String for the mandatory named-only parameter names
   * @return a FunctionSignature
   */
  public static FunctionSignature namedOnly(String... names) {
    return of(0, 0, names.length, false, false, names);
  }

  /**
   * Constructs a function signature from named-only argument names.
   *
   * @param numMandatory an int for the number of mandatory named-only parameters
   * @param names an Array of String for the named-only parameter names
   * @return a FunctionSignature
   */
  public static FunctionSignature namedOnly(int numMandatory, String... names) {
    return of(0, 0, numMandatory, false, false, names);
  }

  /** Invalid signature from Parser or from SkylarkBuiltin annotations */
  protected static class SignatureException extends Exception {
    @Nullable private final Parameter<?, ?> parameter;

    /** SignatureException from a message and a Parameter */
    public SignatureException(String message, @Nullable Parameter<?, ?> parameter) {
      super(message);
      this.parameter = parameter;
    }

    /** what parameter caused the exception, if identified? */
    @Nullable public Parameter<?, ?> getParameter() {
      return parameter;
    }
  }

  /** A ready-made signature to allow only keyword arguments and put them in a kwarg parameter */
  public static final FunctionSignature KWARGS =
      FunctionSignature.of(0, 0, 0, false, true, "kwargs");


  // Minimal boilerplate to get things running in absence of AutoValue
  // TODO(bazel-team): actually migrate to AutoValue when possible,
  // which importantly for future plans will define .equals() and .hashValue() (also toString())
  private static class AutoValueFunctionSignatureShape extends Shape {
    private int mandatoryPositionals;
    private int optionalPositionals;
    private int mandatoryNamedOnly;
    private int optionalNamedOnly;
    private boolean starArg;
    private boolean kwArg;

    @Override public int getMandatoryPositionals() { return mandatoryPositionals; }
    @Override public int getOptionalPositionals() { return optionalPositionals; }
    @Override public int getMandatoryNamedOnly() { return mandatoryNamedOnly; }
    @Override public int getOptionalNamedOnly() { return optionalNamedOnly; }
    @Override public boolean hasStarArg() { return starArg; }
    @Override public boolean hasKwArg() { return kwArg; }

    public AutoValueFunctionSignatureShape(
        int mandatoryPositionals, int optionalPositionals,
        int mandatoryNamedOnly, int optionalNamedOnly, boolean starArg, boolean kwArg) {
      this.mandatoryPositionals = mandatoryPositionals;
      this.optionalPositionals = optionalPositionals;
      this.mandatoryNamedOnly = mandatoryNamedOnly;
      this.optionalNamedOnly = optionalNamedOnly;
      this.starArg = starArg;
      this.kwArg = kwArg;
    }
  }

  private static class AutoValueFunctionSignature extends FunctionSignature  {
    private Shape shape;
    private ImmutableList<String> names;

    @Override public Shape getShape() { return shape; }
    @Override public ImmutableList<String> getNames() { return names; }

    public AutoValueFunctionSignature(Shape shape, ImmutableList<String> names) {
      this.shape = shape;
      this.names = names;
    }
  }

  private static class AutoValueFunctionSignatureWithValues<V, T> extends WithValues<V, T> {
    private FunctionSignature signature;
    private List<V> defaultValues;
    private List<T> types;

    @Override public FunctionSignature getSignature() { return signature; }
    @Override public List<V> getDefaultValues() { return defaultValues; }
    @Override public List<T> getTypes() { return types; }

    public AutoValueFunctionSignatureWithValues(
        FunctionSignature signature, List<V> defaultValues, List<T> types) {
      this.signature = signature;
      this.defaultValues = defaultValues;
      this.types = types;
    }
  }
}
