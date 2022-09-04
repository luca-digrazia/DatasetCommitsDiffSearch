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
import javax.annotation.Nullable;

/**
 * Syntax node for a parameter in a function definition.
 *
 * <p>Parameters may be of four forms, as in {@code def f(a, b=c, *args, **kwargs)}. They are
 * represented by the subclasses Mandatory, Optional, Star, and StarStar.
 *
 * <p>See FunctionSignature for how a valid list of Parameters is organized as a signature, e.g. def
 * foo(mandatory, optional = e1, *args, mandatorynamedonly, optionalnamedonly = e2, **kw): ...
 *
 * <p>V is the class of a defaultValue (Expression at compile-time, Object at runtime), T is the
 * class of a type (Expression at compile-time, SkylarkType at runtime).
 */
public abstract class Parameter<V, T> extends Node {

  @Nullable protected final Identifier identifier;
  @Nullable protected final T type;

  private Parameter(@Nullable Identifier identifier, @Nullable T type) {
    this.identifier = identifier;
    this.type = type;
  }

  @Nullable
  public String getName() {
    return identifier != null ? identifier.getName() : null;
  }

  @Nullable
  public Identifier getIdentifier() {
    return identifier;
  }

  public boolean hasName() {
    return true;
  }

  @Nullable
  public T getType() {
    return type;
  }

  @Nullable
  public V getDefaultValue() {
    return null;
  }

  @Override
  public final void prettyPrint(Appendable buffer, int indentLevel) throws IOException {
    prettyPrint(buffer);
  }

  @Override
  public abstract void prettyPrint(Appendable buffer) throws IOException;

  /**
   * Syntax node for a mandatory parameter, {@code f(id)}. It may be positional or keyword-only
   * depending on its position.
   */
  public static final class Mandatory<V, T> extends Parameter<V, T> {

    Mandatory(Identifier identifier) {
      this(identifier, null);
    }

    Mandatory(Identifier identifier, @Nullable T type) {
      super(identifier, type);
    }

    @Override
    public void prettyPrint(Appendable buffer) throws IOException {
      buffer.append(getName());
    }
  }

  /**
   * Syntax node for an optional parameter, {@code f(id=expr).}. It may be positional or
   * keyword-only depending on its position.
   */
  public static final class Optional<V, T> extends Parameter<V, T> {

    public final V defaultValue;

    Optional(Identifier identifier, @Nullable V defaultValue) {
      this(identifier, null, defaultValue);
    }

    Optional(Identifier identifier, @Nullable T type, @Nullable V defaultValue) {
      super(identifier, type);
      this.defaultValue = defaultValue;
    }

    @Override
    @Nullable
    public V getDefaultValue() {
      return defaultValue;
    }

    @Override
    public void prettyPrint(Appendable buffer) throws IOException {
      buffer.append(getName());
      buffer.append('=');
      // This should only ever be used on a parameter representing static information, i.e. with V
      // and T instantiated as Expression.
      ((Expression) defaultValue).prettyPrint(buffer);
    }

    // Keep this as a separate method so that it can be used regardless of what V and T are
    // parameterized with.
    @Override
    public String toString() {
      return getName() + "=" + defaultValue;
    }
  }

  /** Syntax node for a star parameter, {@code f(*identifier)} or or {@code f(..., *, ...)}. */
  public static final class Star<V, T> extends Parameter<V, T> {

    Star(@Nullable Identifier identifier, @Nullable T type) {
      super(identifier, type);
    }

    Star(@Nullable Identifier identifier) {
      this(identifier, null);
    }

    @Override
    public boolean hasName() {
      return getName() != null;
    }

    @Override
    public void prettyPrint(Appendable buffer) throws IOException {
      buffer.append('*');
      if (getName() != null) {
        buffer.append(getName());
      }
    }
  }

  /** Syntax node for a parameter of the form {@code f(**identifier)}. */
  public static final class StarStar<V, T> extends Parameter<V, T> {

    StarStar(Identifier identifier, @Nullable T type) {
      super(identifier, type);
    }

    StarStar(Identifier identifier) {
      this(identifier, null);
    }

    @Override
    public void prettyPrint(Appendable buffer) throws IOException {
      buffer.append("**");
      buffer.append(getName());
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void accept(NodeVisitor visitor) {
    visitor.visit((Parameter<Expression, Expression>) this);
  }
}
