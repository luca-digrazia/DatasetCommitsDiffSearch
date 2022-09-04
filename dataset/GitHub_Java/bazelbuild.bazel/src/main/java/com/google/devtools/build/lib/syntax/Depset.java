// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.syntax;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSet.NestedSetDepthException;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * A Depset is a Starlark value that wraps a {@link NestedSet}.
 *
 * <p>A NestedSet has a type parameter that describes, at compile time, the elements of the set. By
 * contrast, a Depset has a value, {@link #getContentType}, that describes the elements during
 * execution. This type symbol permits the element type of a Depset value to be queried, after the
 * type parameter has been erased, without visiting each element of the often-vast data structure.
 *
 * <p>The content type of a non-empty {@code Depset} is determined by its first element. All
 * elements must have the same type. An empty depset has type {@code SkylarkType.TOP}, and may be
 * combined with any other depset.
 */
// TODO(adonovan): move to lib.packages, as this is a Bazelism.
@SkylarkModule(
    name = "depset",
    category = SkylarkModuleCategory.BUILTIN,
    doc =
        "<p>A specialized data structure that supports efficient merge operations and has a"
            + " defined traversal order. Commonly used for accumulating data from transitive"
            + " dependencies in rules and aspects. For more information see <a"
            + " href=\"../depsets.md\">here</a>."
            + " <p>The elements of a depset must be hashable and all of the same type (as"
            + " defined by the built-in type(x) function), but depsets are not simply"
            + " hash sets and do not support fast membership tests."
            + " If you need a general set datatype, you can"
            + " simulate one using a dictionary where all keys map to <code>True</code>."
            + "<p>Depsets are immutable. They should be created using their <a"
            + " href=\"globals.html#depset\">constructor function</a> and merged or augmented with"
            + " other depsets via the <code>transitive</code> argument. There are other deprecated"
            + " methods (<code>|</code> and <code>+</code> operators, <code>union</code> method)"
            + " that will eventually go away."
            + "<p>The <code>order</code> parameter determines the"
            + " kind of traversal that is done to convert the depset to an iterable. There are"
            + " four possible values:"
            + "<ul><li><code>\"default\"</code> (formerly"
            + " <code>\"stable\"</code>): Order is unspecified (but"
            + " deterministic).</li>"
            + "<li><code>\"postorder\"</code> (formerly"
            + " <code>\"compile\"</code>): A left-to-right post-ordering. Precisely, this"
            + " recursively traverses all children leftmost-first, then the direct elements"
            + " leftmost-first.</li>"
            + "<li><code>\"preorder\"</code> (formerly"
            + " <code>\"naive_link\"</code>): A left-to-right pre-ordering. Precisely, this"
            + " traverses the direct elements leftmost-first, then recursively traverses the"
            + " children leftmost-first.</li>"
            + "<li><code>\"topological\"</code> (formerly"
            + " <code>\"link\"</code>): A topological ordering from the root down to the leaves."
            + " There is no left-to-right guarantee.</li>"
            + "</ul>"
            + "<p>Two depsets may only be merged if"
            + " either both depsets have the same order, or one of them has"
            + " <code>\"default\"</code> order. In the latter case the resulting depset's order"
            + " will be the same as the other's order."
            + "<p>Depsets may contain duplicate values but"
            + " these will be suppressed when iterating (using <code>to_list()</code>). Duplicates"
            + " may interfere with the ordering semantics.")
@Immutable
@AutoCodec
public final class Depset implements SkylarkValue {
  private final SkylarkType contentType;
  private final NestedSet<?> set;
  @Nullable private final ImmutableList<Object> items;
  @Nullable private final ImmutableList<NestedSet<?>> transitiveItems;

  @AutoCodec.VisibleForSerialization
  Depset(
      SkylarkType contentType,
      NestedSet<?> set,
      ImmutableList<Object> items,
      ImmutableList<NestedSet<?>> transitiveItems) {
    this.contentType = Preconditions.checkNotNull(contentType, "type cannot be null");
    this.set = set;
    this.items = items;
    this.transitiveItems = transitiveItems;
  }

  private static Depset create(
      Order order, SkylarkType contentType, Object item, @Nullable Depset left)
      throws EvalException {
    ImmutableList.Builder<Object> itemsBuilder = ImmutableList.builder();
    ImmutableList.Builder<NestedSet<?>> transitiveItemsBuilder = ImmutableList.builder();
    if (left != null) {
      if (left.items == null) { // SkylarkSet created from native NestedSet
        transitiveItemsBuilder.add(left.set);
      } else { // Preserving the left-to-right addition order.
        itemsBuilder.addAll(left.items);
        transitiveItemsBuilder.addAll(left.transitiveItems);
      }
    }
    // Adding the item
    if (item instanceof Depset) {
      Depset nestedSet = (Depset) item;
      if (!nestedSet.isEmpty()) {
        contentType = checkType(contentType, nestedSet.contentType);
        transitiveItemsBuilder.add(nestedSet.set);
      }
    } else if (item instanceof Sequence) {
      for (Object x : (Sequence) item) {
        EvalUtils.checkHashable(x);
        SkylarkType xt = SkylarkType.of(x);
        contentType = checkType(contentType, xt);
        itemsBuilder.add(x);
      }
    } else {
      throw new EvalException(
          null,
          String.format(
              "cannot union value of type '%s' to a depset", EvalUtils.getDataTypeName(item)));
    }
    ImmutableList<Object> items = itemsBuilder.build();
    ImmutableList<NestedSet<?>> transitiveItems = transitiveItemsBuilder.build();
    // Initializing the real nested set
    NestedSetBuilder<Object> builder = new NestedSetBuilder<>(order);
    builder.addAll(items);
    try {
      for (NestedSet<?> nestedSet : transitiveItems) {
        builder.addTransitive(nestedSet);
      }
    } catch (IllegalArgumentException e) {
      // Order mismatch between item and builder.
      throw new EvalException(null, e.getMessage());
    }
    return new Depset(contentType, builder.build(), items, transitiveItems);
  }

  // implementation of deprecated depset(x) constructor
  static Depset legacyOf(Order order, Object items) throws EvalException {
    // TODO(adonovan): rethink this API. TOP is a pessimistic type for item, and it's wrong
    // (should be BOTTOM) if items is an empty Depset or Sequence.
    return create(order, SkylarkType.TOP, items, null);
  }

  // implementation of deprecated depset+x, depset.union(x), depset|x
  static Depset unionOf(Depset left, Object right) throws EvalException {
    return create(left.set.getOrder(), left.contentType, right, left);
  }

  /**
   * Returns a Depset that wraps the specified NestedSet.
   *
   * <p>This operation is type-safe only if the specified element type is appropriate for every
   * element of the set.
   */
  // TODO(adonovan): enforce that we never construct a Depset with a StarlarkType
  // that represents a non-Skylark type (e.g. NestedSet<PathFragment>).
  // One way to do that is to disallow constructing StarlarkTypes for classes
  // that would fail Starlark.valid; however remains the problem that
  // Object.class means "any Starlark value" but in fact allows any Java value.
  public static <T> Depset of(SkylarkType contentType, NestedSet<T> set) {
    return new Depset(contentType, set, null, null);
  }

  /**
   * Returns a Depset that wraps the specified NestedSet.
   *
   * <p>This operation is type-safe only if the specified element type is appropriate for every
   * element of the set.
   */
  public static <T> Depset of(Class<T> contentType, NestedSet<T> set) {
    return new Depset(SkylarkType.of(contentType), set, null, null);
  }

  /**
   * Checks that an item type is allowed in a given set type, and returns the type of a new depset
   * with that item inserted.
   */
  private static SkylarkType checkType(SkylarkType depsetType, SkylarkType itemType)
      throws EvalException {
    // An initially empty depset takes its type from the first element added.
    // Otherwise, the types of the item and depset must match exactly.
    //
    // TODO(adonovan): why is the empty depset TOP, not BOTTOM?
    // T ^ TOP == TOP, whereas T ^ BOTTOM == T.
    // This can't be changed without breaking callers of getContentType who
    // expect to see TOP. Maybe this is minor, but it at least would require
    // changes to EvalUtils#getDataTypeName so that it
    // can continue to print "depset of Objects" instead of "depset of EmptyTypes".
    // Better yet, break the behavior and change it to "empty depset".
    if (depsetType == SkylarkType.TOP || depsetType.equals(itemType)) {
      return itemType;
    }
    throw new EvalException(
        null,
        String.format("cannot add an item of type '%s' to a depset of '%s'", itemType, depsetType));
  }

  /**
   * Throws an {@link TypeException} if this nested set does not have elements of the given type.
   */
  private void checkHasContentType(Class<?> type) throws TypeException {
    // Empty sets should be SkylarkType.TOP anyway.
    if (!set.isEmpty() && !contentType.canBeCastTo(type)) {
      throw new TypeException();
    }
  }

  /**
   * Returns the embedded {@link NestedSet}, while asserting that its elements all have the given
   * type. Note that the type itself cannot be a parameterized type, as the type check is shallow.
   *
   * <p>If you do not specifically need the {@code NestedSet} and you are going to flatten it
   * anyway, prefer {@link #toCollection} to make your intent clear.
   *
   * @param type a {@link Class} representing the expected type of the contents
   * @return the {@code NestedSet}, with the appropriate generic type
   * @throws TypeException if the type does not accurately describe all elements
   */
  // The precondition ensures generic type safety, and sets are immutable.
  @SuppressWarnings("unchecked")
  public <T> NestedSet<T> getSet(Class<T> type) throws TypeException {
    // TODO(adonovan): eliminate this function and toCollection in favor of ones
    // that accept a SkylarkType augmented with a type parameter so that it acts
    // like a "reified generic": whereas a Class symbol can express only the
    // top-level value tag, a SkylarkType could express an entire type such as
    // Set<List<String+Integer>>, and act as a "witness" or existential type to
    // unlock the untyped nested set. For example:
    //
    //  public <T> NestedSet<T> getSet(SkylarkType<NestedSet<T>> witness) throws TypeException {
    //     if (this.type.matches(witness)) {
    //         return witness.convert(this);
    //     }
    //     throw TypeException;
    // }

    checkHasContentType(type);
    return (NestedSet<T>) set;
  }

  /**
   * Returns the embedded {@link NestedSet} without asserting the type of its elements. To validate
   * the type of elements in the set, call {@link #getSet(Class)} instead.
   */
  public NestedSet<?> getSet() {
    return set;
  }

  /**
   * Returns the contents of the set as a {@link Collection}, asserting that the set type is
   * compatible with {@code T}.
   *
   * @param type a {@link Class} representing the expected type of the contents
   * @throws TypeException if the type does not accurately describe all elements
   */
  // The precondition ensures generic type safety.
  @SuppressWarnings("unchecked")
  public <T> Collection<T> toCollection(Class<T> type) throws TypeException {
    checkHasContentType(type);
    return (Collection<T>) toCollection();
  }

  /** Returns the contents of the set as a {@link Collection}. */
  public Collection<?> toCollection() {
    return set.toList();
  }

  /**
   * Returns the embedded {@link NestedSet} of this object while asserting that its elements have
   * the given type.
   *
   * <p>This convenience method should be invoked only by methods which are called from Starlark to
   * validate the parameters of the method, as the exception thrown is specific to param validation.
   *
   * @param expectedType a class representing the expected type of the contents
   * @param fieldName the name of the field being validated, used to construct a descriptive error
   *     message if validation fails
   * @return the {@code NestedSet}, with the appropriate generic type
   * @throws EvalException if the type does not accurately describe the elements of the set
   */
  public <T> NestedSet<T> getSetFromParam(Class<T> expectedType, String fieldName)
      throws EvalException {
    try {
      return getSet(expectedType);
    } catch (TypeException exception) {
      throw new EvalException(
          null,
          String.format(
              "for parameter '%s', got a depset of '%s', expected a depset of '%s'",
              fieldName, getContentType(), EvalUtils.getDataTypeNameFromClass(expectedType)),
          exception);
    }
  }

  /**
   * Identical to {@link #getSetFromParam(Class, String)}, except that it handles a <b>noneable</b>
   * depset parameter.
   *
   * <p>If the parameter's value is None, returns an empty nested set.
   *
   * @throws EvalException if the parameter is neither None nor a Depset, or if it is a Depset of an
   *     unexpected type
   */
  // TODO(b/140932420): Better noneable handling should prevent instanceof checking.
  public static <T> NestedSet<T> getSetFromNoneableParam(
      Object depsetOrNone, Class<T> expectedType, String fieldName) throws EvalException {
    if (depsetOrNone == Starlark.NONE) {
      return NestedSetBuilder.<T>emptySet(Order.STABLE_ORDER);
    }
    if (depsetOrNone instanceof Depset) {
      Depset depset = (Depset) depsetOrNone;
      return depset.getSetFromParam(expectedType, fieldName);
    } else {
      throw new EvalException(
          String.format(
              "expected a depset of '%s' but got '%s' for parameter '%s'",
              EvalUtils.getDataTypeNameFromClass(expectedType), depsetOrNone, fieldName));
    }
  }

  public boolean isEmpty() {
    return set.isEmpty();
  }

  @Override
  public boolean truth() {
    return !set.isEmpty();
  }

  public SkylarkType getContentType() {
    return contentType;
  }

  @Override
  public String toString() {
    return Printer.repr(this);
  }

  public Order getOrder() {
    return set.getOrder();
  }

  @Override
  public boolean isImmutable() {
    return true;
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append("depset(");
    printer.printList(set, "[", ", ", "]", null);
    Order order = getOrder();
    if (order != Order.STABLE_ORDER) {
      printer.append(", order = ");
      printer.repr(order.getSkylarkName());
    }
    printer.append(")");
  }

  @SkylarkCallable(
      name = "union",
      doc =
          "<i>(Deprecated)</i> Returns a new <a href=\"depset.html\">depset</a> that is the merge "
              + "of the given depset and <code>new_elements</code>. Use the "
              + "<code>transitive</code> constructor argument instead.",
      parameters = {
        @Param(name = "new_elements", type = Object.class, doc = "The elements to be added.")
      },
      useStarlarkThread = true)
  public Depset union(Object newElements, StarlarkThread thread) throws EvalException {
    if (thread.getSemantics().incompatibleDepsetUnion()) {
      throw new EvalException(
          null,
          "depset method `.union` has been removed. See "
              + "https://docs.bazel.build/versions/master/skylark/depsets.html for "
              + "recommendations. Use --incompatible_depset_union=false "
              + "to temporarily disable this check.");
    }
    // newElements' type is Object because of the polymorphism on unioning two
    // Depsets versus a set and another kind of iterable.
    // Can't use EvalUtils#toIterable since that would discard this information.
    return Depset.unionOf(this, newElements);
  }

  @SkylarkCallable(
      name = "to_list",
      doc =
          "Returns a list of the elements, without duplicates, in the depset's traversal order. "
              + "Note that order is unspecified (but deterministic) for elements that were added "
              + "more than once to the depset. Order is also unspecified for <code>\"default\""
              + "</code>-ordered depsets, and for elements of child depsets whose order differs "
              + "from that of the parent depset. The list is a copy; modifying it has no effect "
              + "on the depset and vice versa.",
      useStarlarkThread = true)
  public StarlarkList<Object> toList(StarlarkThread thread) throws EvalException {
    try {
      return StarlarkList.copyOf(thread.mutability(), this.toCollection());
    } catch (NestedSetDepthException exception) {
      throw new EvalException(
          null,
          "depset exceeded maximum depth "
              + exception.getDepthLimit()
              + ". This was only discovered when attempting to flatten the depset for to_list(), "
              + "as the size of depsets is unknown until flattening. "
              + "See https://github.com/bazelbuild/bazel/issues/9180 for details and possible "
              + "solutions.");
    }
  }

  /** Create a {@link Builder} with specified order. */
  public static Builder builder(Order order) {
    return new Builder(order);
  }

  /**
   * Builder for {@link Depset}.
   *
   * <p>Use this to construct typesafe Skylark nested sets (depsets). Encapsulates content type
   * checking logic.
   */
  public static final class Builder {

    private final Order order;
    private final NestedSetBuilder<Object> builder;

    private SkylarkType contentType = SkylarkType.TOP;

    private Builder(Order order) {
      this.order = order;
      this.builder = new NestedSetBuilder<>(order);
    }

    /** Adds a direct element, checking that its type is equal to the elements already added. */
    public Builder addDirect(Object x) throws EvalException {
      // In case of problems, see b/144992997 or github.com/bazelbuild/bazel/issues/10289.
      EvalUtils.checkHashable(x);

      SkylarkType xt = SkylarkType.of(x);
      this.contentType = checkType(contentType, xt);
      builder.add(x);
      return this;
    }

    /** Adds a transitive set, checking that its type is equal to the elements already added. */
    public Builder addTransitive(Depset transitive) throws EvalException {
      if (transitive.isEmpty()) {
        return this;
      }

      this.contentType = checkType(contentType, transitive.getContentType());

      if (!order.isCompatible(transitive.getOrder())) {
        throw new EvalException(
            null,
            String.format(
                "Order '%s' is incompatible with order '%s'",
                order.getSkylarkName(), transitive.getOrder().getSkylarkName()));
      }
      builder.addTransitive(transitive.getSet());
      return this;
    }

    public Depset build() {
      return new Depset(contentType, builder.build(), null, null);
    }
  }

  /** An exception thrown when validation fails on the type of elements of a nested set. */
  public static class TypeException extends Exception {}
}
