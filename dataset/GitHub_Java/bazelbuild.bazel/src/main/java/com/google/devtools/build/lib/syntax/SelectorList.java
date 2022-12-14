// Copyright 2015 Google Inc. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.events.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * An attribute value consisting of a concatenation of native types and selects, e.g:
 *
 * <pre>
 *   rule(
 *       name = 'myrule',
 *       deps =
 *           [':defaultdep']
 *           + select({
 *               'a': [':adep'],
 *               'b': [':bdep'],})
 *           + select({
 *               'c': [':cdep'],
 *               'd': [':ddep'],})
 *   )
 * </pre>
 */
public final class SelectorList {
  private final Class<?> type;
  private final List<Object> elements;

  private SelectorList(Class<?> type, List<Object> elements) {
    this.type = type;
    this.elements = elements;
  }

  /**
   * Returns an ordered list of the elements in this expression. Each element may be a
   * native type or a select.
   */
  public List<Object> getElements() {
    return elements;
  }

  /**
   * Returns the native type contained by this expression.
   */
  private Class<?> getType() {
    return type;
  }

  /**
   * Creates a "wrapper" list that consists of a single select.
   */
  public static SelectorList of(SelectorValue selector) {
    return new SelectorList(selector.getType(), ImmutableList.<Object>of(selector));
  }

  /**
   * Creates a list that concatenates two values, where each value may be either a native
   * type or a select over that type.
   *
   * @throws EvalException if the values don't have the same underlying type
   */
  public static SelectorList concat(Location location, Object value1, Object value2)
      throws EvalException {
    ImmutableList.Builder<Object> builder = ImmutableList.builder();
    Class<?> type1 = addValue(value1, builder);
    Class<?> type2 = addValue(value2, builder);
    if (!canConcatenate(type1, type2)) {
      throw new EvalException(location, "'+' operator applied to incompatible types");
    }
    return new SelectorList(type1, builder.build());
  }
  
  // TODO(bazel-team): match on the List interface, not the actual implementation. For now,
  // we verify this is the right class through test coverage.
  private static final Class<?> NATIVE_LIST_TYPE = ArrayList.class;

  private static Class<?> addValue(Object value, ImmutableList.Builder<Object> builder) {
    if (value instanceof SelectorList) {
      SelectorList selectorList = (SelectorList) value;
      builder.addAll(selectorList.getElements());
      return selectorList.getType();
    } else if (value instanceof SelectorValue) {
      builder.add(value);
      return ((SelectorValue) value).getType();
    } else if (value instanceof GlobList) {
      builder.add(((GlobList<?>) value).delegate());
      return NATIVE_LIST_TYPE;
    } else {
      builder.add(value);
      return value.getClass();
    }
  }

  private static boolean isListType(Class<?> type) {
    return type == NATIVE_LIST_TYPE || type.getSuperclass() == SkylarkList.class;
  }

  private static boolean canConcatenate(Class<?> type1, Class<?> type2) {
    if (type1 == type2) {
      return true;
    } else if (isListType(type1) && isListType(type2)) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return Joiner.on(" + ").join(elements);
  }
}