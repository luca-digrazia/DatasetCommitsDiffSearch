// Copyright 2015 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.generatedprojecttest.util;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Utility class for providing static predicates for rules, to help filter the rules.
 */
public class RuleSetUtils {

  /**
   * Predicate for checking if a rule is hidden.
   */
  public static final Predicate<String> HIDDEN_RULE = new Predicate<String>() {
    @Override
    public boolean apply(final String input) {
      try {
        RuleClassType.INVISIBLE.checkName(input);
        return true;
      } catch (IllegalArgumentException e) {
        return input.equals("testing_dummy_rule");
      }
    }
  };

  /**
   * Predicate for checking if a rule has any mandatory attributes.
   */
  public static final Predicate<RuleClass> MANDATORY_ATTRIBUTES = new Predicate<RuleClass>() {
    @Override
    public boolean apply(final RuleClass input) {
      List<Attribute> li = new ArrayList<>(input.getAttributes());
      return Iterables.any(li, MANDATORY);
    }
  };

  /**
   * Predicate for checking if a rule allows an empty srcs attribute.
   */
  public static final Predicate<RuleClass> EMPTY_SOURCES_ALLOWED = new Predicate<RuleClass>() {
    @Override
    public boolean apply(final RuleClass input) {
      return !input.getAttributeByName("srcs").isNonEmpty();
    }
  };

  /**
   * Predicate for checking that the rule can have a deps attribute, and does not have any
   * other mandatory attributes besides deps.
   */
  public static final Predicate<RuleClass> DEPS_ONLY_ALLOWED = new Predicate<RuleClass>() {
    @Override
    public boolean apply(final RuleClass input) {
      List<Attribute> li = new ArrayList<>(input.getAttributes());
      // TODO(bazel-team): after the API migration we shouldn't check srcs separately
      boolean emptySrcsAllowed = input.hasAttr("srcs", BuildType.LABEL_LIST)
          ? !input.getAttributeByName("srcs").isNonEmpty() : true;
      if (!(emptySrcsAllowed && Iterables.any(li, DEPS))) {
        return false;
      }

      Iterator<Attribute> it = li.iterator();
      boolean mandatoryAttributesBesidesDeps =
          Iterables.any(Lists.newArrayList(Iterators.filter(it, MANDATORY)), Predicates.not(DEPS));
      return !mandatoryAttributesBesidesDeps;
    }
  };

  /**
   * Predicate for checking if a RuleClass has certain attributes
   */
  public static class HasAttributes implements Predicate<RuleClass> {

    private static enum Operator {
      ANY, ALL
    }

    private final List<Pair<String, Type<?>>> attributes;
    private final Operator operator;

    public HasAttributes(Collection<Pair<String, Type<?>>> attributes, Operator operator) {
      this.attributes = ImmutableList.copyOf(attributes);
      this.operator = operator;
    }

    @Override
    public boolean apply(final RuleClass input) {
      switch (operator) {
        case ANY:
          for (Pair<String, Type<?>> attribute : attributes) {
            if (input.hasAttr(attribute.first, attribute.second)) {
              return true;
            }
          }
          return false;
        case ALL:
          for (Pair<String, Type<?>> attribute : attributes) {
            if (!input.hasAttr(attribute.first, attribute.second)) {
              return false;
            }
          }
          return true;
      }
      return false;
    }
  }

  public static final Predicate<RuleClass> hasAnyAttributes(
      Collection<Pair<String, Type<?>>> attributes) {
    return new HasAttributes(attributes, HasAttributes.Operator.ANY);
  }

  public static final Predicate<RuleClass> hasAllAttributes(
      Collection<Pair<String, Type<?>>> attributes) {
    return new HasAttributes(attributes, HasAttributes.Operator.ALL);
  }

  /**
   * Predicate for checking if an attribute is mandatory.
   */
  private static final Predicate<Attribute> MANDATORY = new Predicate<Attribute>() {
    @Override
    public boolean apply(final Attribute input) {
      return input.isMandatory();
    }
  };

  /**
   * Predicate for checking if an attribute is the "deps" attribute.
   */
  private static final Predicate<Attribute> DEPS = new Predicate<Attribute>() {
    @Override
    public boolean apply(final Attribute input) {
      return input.getName().equals("deps");
    }
  };

  /**
   * Predicate for checking if all the strings in a list of strings are different.
   */
  public static final Predicate<List<String>> ELEMENTS_ALL_DIFFERENT =
      new Predicate<List<String>>() {
    @Override
    public boolean apply(final List<String> input) {
      Set<String> inputAsSet = new HashSet<>(input);
      return input.size() == inputAsSet.size();
    }
  };

  /**
   * Predicate for checking if a rule class is not in excluded.
   */
  public static Predicate<String> notContainsAnyOf(final ImmutableSet<String> excluded) {
    return Predicates.not(Predicates.in(excluded));
  }
}
