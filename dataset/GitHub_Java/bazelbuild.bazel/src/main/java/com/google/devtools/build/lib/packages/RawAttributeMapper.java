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
package com.google.devtools.build.lib.packages;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.syntax.Label;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

/**
 * {@link AttributeMap} implementation that returns raw attribute information as contained
 * within a {@link Rule}. In particular, configurable attributes of the form
 * { config1: "value1", config2: "value2" } are passed through without being resolved to a
 * final value.
 */
public class RawAttributeMapper extends AbstractAttributeMapper {
  RawAttributeMapper(Package pkg, RuleClass ruleClass, Label ruleLabel,
      AttributeContainer attributes) {
    super(pkg, ruleClass, ruleLabel, attributes);
  }

  public static RawAttributeMapper of(Rule rule) {
    return new RawAttributeMapper(rule.getPackage(), rule.getRuleClassObject(), rule.getLabel(),
        rule.getAttributeContainer());
  }

  @Override
  protected <T> Iterable<T> visitAttribute(String attributeName, Type<T> type) {
    T value = get(attributeName, type);
    return value == null ? ImmutableList.<T>of() : ImmutableList.of(value);
  }

  /**
   * Variation of {@link #get} that merges the values of configurable lists together (with
   * duplicates removed).
   *
   * <p>For example, given:
   * <pre>
   *   attr = select({
   *       ':condition1': [A, B, C],
   *       ':condition2': [C, D]
   *       }),
   * </pre>
   * this returns the value <code>[A, B, C, D]</code>.
   *
   * <p>If the attribute isn't configurable (e.g. <code>attr = [A, B]</code>), returns
   * its raw value.
   *
   * <p>Throws an {@link IllegalStateException} if the attribute isn't a list type.
   */
  @Nullable
  public <T> Collection<T> getMergedValues(String attributeName, Type<List<T>> type) {
    Preconditions.checkState(type instanceof Type.ListType);
    if (!isConfigurable(attributeName, type)) {
      return get(attributeName, type);
    }

    Type.Selector<List<T>> selector = getSelector(attributeName, type);
    ImmutableSet.Builder<T> mergedValues = ImmutableSet.builder();
    for (List<T> configuredList : selector.getEntries().values()) {
      mergedValues.addAll(configuredList);
    }
    return mergedValues.build();
  }

  /**
   * Returns true if the given attribute is configurable for this rule instance, false
   * otherwise.
   */
  public <T> boolean isConfigurable(String attributeName, Type<T> type) {
    return getSelector(attributeName, type) != null;
  }

  /**
   * If the attribute is configurable for this rule instance, returns its configuration
   * keys. Else returns an empty list.
   */
  public <T> Iterable<Label> getConfigurabilityKeys(String attributeName, Type<T> type) {
    Type.Selector<T> selector = getSelector(attributeName, type);
    return selector == null ? ImmutableList.<Label>of() : selector.getEntries().keySet();
  }
}
