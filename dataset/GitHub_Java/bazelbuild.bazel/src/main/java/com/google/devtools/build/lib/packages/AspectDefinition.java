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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.util.BinaryPredicate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The definition of an aspect (see {@link com.google.devtools.build.lib.analysis.Aspect} for more
 * information.)
 *
 * <p>Contains enough information to build up the configured target graph except for the actual way
 * to build the Skyframe node (that is the territory of
 * {@link com.google.devtools.build.lib.view AspectFactory}). In particular:
 * <ul>
 *   <li>The condition that must be fulfilled for an aspect to be able to operate on a configured
 *       target
 *   <li>The (implicit or late-bound) attributes of the aspect that denote dependencies the aspect
 *       itself needs (e.g. runtime libraries for a new language for protocol buffers)
 *   <li>The aspects this aspect requires from its direct dependencies
 * </ul>
 *
 * <p>The way to build the Skyframe node is not here because this data needs to be accessible from
 * the {@code .packages} package and that one requires references to the {@code .view} package.
 */
@Immutable
public final class AspectDefinition {

  private final String name;
  private final ImmutableSet<Class<?>> requiredProviders;
  private final ImmutableMap<String, Attribute> attributes;
  private final ImmutableMultimap<String, Class<? extends AspectFactory<?, ?, ?>>> attributeAspects;

  private AspectDefinition(
      String name,
      ImmutableSet<Class<?>> requiredProviders,
      ImmutableMap<String, Attribute> attributes,
      ImmutableMultimap<String, Class<? extends AspectFactory<?, ?, ?>>> attributeAspects) {
    this.name = name;
    this.requiredProviders = requiredProviders;
    this.attributes = attributes;
    this.attributeAspects = attributeAspects;
  }

  public String getName() {
    return name;
  }

  /**
   * Returns the attributes of the aspect in the form of a String -&gt; {@link Attribute} map.
   *
   * <p>All attributes are either implicit or late-bound.
   */
  public ImmutableMap<String, Attribute> getAttributes() {
    return attributes;
  }

  /**
   * Returns the set of {@link com.google.devtools.build.lib.analysis.TransitiveInfoProvider} instances
   * that must be present on a configured target so that this aspect can be applied to it.
   *
   * <p>We cannot refer to that class here due to our dependency structure, so this returns a set
   * of unconstrained class objects.
   *
   * <p>If a configured target does not have a required provider, the aspect is silently not created
   * for it.
   */
  public ImmutableSet<Class<?>> getRequiredProviders() {
    return requiredProviders;
  }

  /**
   * Returns the attribute -&gt; set of required aspects map.
   *
   * <p>Note that the map actually contains {@link AspectFactory}
   * instances, except that we cannot reference that class here.
   */
  public ImmutableMultimap<String, Class<? extends AspectFactory<?, ?, ?>>> getAttributeAspects() {
    return attributeAspects;
  }

  /**
   * Returns the attribute -&gt; set of labels that are provided by aspects of attribute.
   */
  public static ImmutableMultimap<Attribute, Label> visitAspectsIfRequired(
      Target from, Attribute attribute, Target to) {
    ImmutableMultimap.Builder<Attribute, Label> labelBuilder = ImmutableMultimap.builder();
    // Aspect can be declared only for Rules.
    if (!(from instanceof Rule) || !(to instanceof Rule)) {
      return labelBuilder.build();
    }
    RuleClass ruleClass = ((Rule) to).getRuleClassObject();
    for (Class<? extends AspectFactory<?, ?, ?>> candidateClass : attribute.getAspects()) {
      AspectFactory<?, ?, ?> candidate = AspectFactory.Util.create(candidateClass);
      // Check if target satisfies condition for this aspect (has to provide all required
      // TransitiveInfoProviders)
      if (!ruleClass.getAdvertisedProviders().containsAll(
          candidate.getDefinition().getRequiredProviders())) {
        continue;
      }
      addAllAttributesOfAspect((Rule) from, labelBuilder, candidate.getDefinition(), Rule.ALL_DEPS);
    }
    return labelBuilder.build();
  }

  /**
   * Collects all attribute labels from the specified aspectDefinition.
   */
  public static void addAllAttributesOfAspect(Rule from,
      ImmutableMultimap.Builder<Attribute, Label> labelBuilder, AspectDefinition aspectDefinition,
      BinaryPredicate<Rule, Attribute> predicate) {
    ImmutableMap<String, Attribute> attributes = aspectDefinition.getAttributes();
    for (Attribute aspectAttribute : attributes.values()) {
      if (!predicate.apply(from, aspectAttribute)) {
        continue;
      }
      if (aspectAttribute.getType() == Type.LABEL) {
        Label label = Type.LABEL.cast(aspectAttribute.getDefaultValue(from));
        if (label != null) {
          labelBuilder.put(aspectAttribute, label);
        }
      } else if (aspectAttribute.getType() == Type.LABEL_LIST) {
        List<Label> labelList = Type.LABEL_LIST.cast(aspectAttribute.getDefaultValue(from));
        labelBuilder.putAll(aspectAttribute, labelList);
      }
    }
  }

  /**
   * Builder class for {@link AspectDefinition}.
   */
  public static final class Builder {
    private final String name;
    private final Map<String, Attribute> attributes = new LinkedHashMap<>();
    private final Set<Class<?>> requiredProviders = new LinkedHashSet<>();
    private final Multimap<String, Class<? extends AspectFactory<?, ?, ?>>> attributeAspects =
        LinkedHashMultimap.create();

    public Builder(String name) {
      this.name = name;
    }

    /**
     * Asserts that this aspect can only be evaluated for rules that supply the specified provider.
     */
    public Builder requireProvider(Class<?> requiredProvider) {
      this.requiredProviders.add(requiredProvider);
      return this;
    }

    /**
     * Tells that in order for this aspect to work, the given aspect must be computed for the
     * direct dependencies in the attribute with the specified name on the associated configured
     * target.
     *
     * <p>Note that {@code AspectFactory} instances are expected in the second argument, but we
     * cannot reference that interface here.
     */
    public Builder attributeAspect(
        String attribute, Class<? extends AspectFactory<?, ?, ?>> aspectFactory) {
      this.attributeAspects.put(
          Preconditions.checkNotNull(attribute), Preconditions.checkNotNull(aspectFactory));
      return this;
    }

    /**
     * Adds an attribute to the aspect.
     *
     * <p>Since aspects do not appear in BUILD files, the attribute must be either implicit
     * (not available in the BUILD file, starting with '$') or late-bound (determined after the
     * configuration is available, starting with ':')
     */
    public <TYPE> Builder add(Attribute.Builder<TYPE> attr) {
      Attribute attribute = attr.build();
      Preconditions.checkState(attribute.isImplicit() || attribute.isLateBound());
      Preconditions.checkState(!attributes.containsKey(attribute.getName()),
          "An attribute with the name '%s' already exists.", attribute.getName());
      attributes.put(attribute.getName(), attribute);
      return this;
    }

    /**
     * Builds the aspect definition.
     *
     * <p>The builder object is reusable afterwards.
     */
    public AspectDefinition build() {
      return new AspectDefinition(name, ImmutableSet.copyOf(requiredProviders),
          ImmutableMap.copyOf(attributes), ImmutableMultimap.copyOf(attributeAspects));
    }
  }
}
