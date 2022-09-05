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
package com.google.devtools.build.lib.packages;

import static com.google.devtools.build.lib.packages.BuildType.DISTRIBUTIONS;
import static com.google.devtools.build.lib.packages.BuildType.FILESET_ENTRY_LIST;
import static com.google.devtools.build.lib.packages.BuildType.LABEL;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_DICT_UNARY;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;
import static com.google.devtools.build.lib.packages.BuildType.LICENSE;
import static com.google.devtools.build.lib.packages.BuildType.NODEP_LABEL;
import static com.google.devtools.build.lib.packages.BuildType.NODEP_LABEL_LIST;
import static com.google.devtools.build.lib.packages.BuildType.OUTPUT;
import static com.google.devtools.build.lib.packages.BuildType.OUTPUT_LIST;
import static com.google.devtools.build.lib.packages.BuildType.TRISTATE;
import static com.google.devtools.build.lib.syntax.Type.BOOLEAN;
import static com.google.devtools.build.lib.syntax.Type.INTEGER;
import static com.google.devtools.build.lib.syntax.Type.INTEGER_LIST;
import static com.google.devtools.build.lib.syntax.Type.STRING;
import static com.google.devtools.build.lib.syntax.Type.STRING_DICT;
import static com.google.devtools.build.lib.syntax.Type.STRING_DICT_UNARY;
import static com.google.devtools.build.lib.syntax.Type.STRING_LIST;
import static com.google.devtools.build.lib.syntax.Type.STRING_LIST_DICT;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.collect.CollectionUtils;
import com.google.devtools.build.lib.packages.Attribute.ComputationLimiter;
import com.google.devtools.build.lib.packages.BuildType.Selector;
import com.google.devtools.build.lib.packages.BuildType.SelectorList;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.util.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * {@link AttributeMap} implementation that provides the ability to retrieve <i>all possible</i>
 * values an attribute might take.
 */
public class AggregatingAttributeMapper extends AbstractAttributeMapper {

  @SuppressWarnings("unchecked")
  private static final ImmutableSet<Type<?>> scalarTypes =
      ImmutableSet.of(INTEGER, STRING, LABEL, NODEP_LABEL, OUTPUT, BOOLEAN, TRISTATE, LICENSE);

  private final Rule rule;

  private AggregatingAttributeMapper(Rule rule) {
    super(rule.getPackage(), rule.getRuleClassObject(), rule.getLabel(),
        rule.getAttributeContainer());
    this.rule = rule;
  }

  public static AggregatingAttributeMapper of(Rule rule) {
    return new AggregatingAttributeMapper(rule);
  }

  /**
   * Returns all of this rule's attributes that are non-configurable. These are unconditionally
   * available to computed defaults no matter what dependencies they've declared.
   */
  private List<String> getNonConfigurableAttributes() {
    return rule.getRuleClassObject().getNonConfigurableAttributes();
  }

  /**
   * Override that also visits the rule's configurable attribute keys (which are themselves labels).
   *
   * <p>Note that we directly parse the selectors rather than just calling {@link #visitAttribute}
   * to iterate over all possible values. That's because {@link #visitAttribute} can grow
   * exponentially with respect to the number of selects (e.g. if an attribute uses three selects
   * with three conditions each, it can take nine possible values). So we want to avoid that code
   * path whenever actual value iteration isn't specifically needed.
   */
  @Override
  protected void visitLabels(Attribute attribute, AcceptsLabelAttribute observer)
      throws InterruptedException {
    visitLabels(attribute, true, observer);
  }

  private void visitLabels(
      final Attribute attribute, boolean includeSelectKeys, final AcceptsLabelAttribute observer)
      throws InterruptedException {
    Type<?> type = attribute.getType();
    SelectorList<?> selectorList = getSelectorList(attribute.getName(), type);
    if (selectorList == null) {
      if (getComputedDefault(attribute.getName(), attribute.getType()) != null) {
        // Computed defaults are a special pain: we have no choice but to iterate through their
        // (computed) values and look for labels.
        for (Object value : visitAttribute(attribute.getName(), attribute.getType())) {
          if (value != null) {
            type.visitLabels(new Type.LabelVisitor() {
              @Override
              public void visit(@Nullable Object label) throws InterruptedException {
                if (label != null) {
                  observer.acceptLabelAttribute(
                      getLabel().resolveRepositoryRelative((Label) label), attribute);
                }
              }
            }, value);
          }
        }
      } else {
        super.visitLabels(attribute, observer);
      }
    } else {
      for (Selector<?> selector : selectorList.getSelectors()) {
        for (Map.Entry<Label, ?> selectorEntry : selector.getEntries().entrySet()) {
          if (includeSelectKeys && !BuildType.Selector.isReservedLabel(selectorEntry.getKey())) {
            observer.acceptLabelAttribute(
                getLabel().resolveRepositoryRelative(selectorEntry.getKey()), attribute);
          }
          Object value = selector.isValueSet(selectorEntry.getKey())
              ? selectorEntry.getValue()
              : attribute.getDefaultValue(null);
          type.visitLabels(new Type.LabelVisitor() {
            @Override
            public void visit(@Nullable Object label) throws InterruptedException {
              if (label != null) {
                observer.acceptLabelAttribute(
                    getLabel().resolveRepositoryRelative((Label) label), attribute);
              }
            }
          }, value);
        }
      }
    }
  }

  /**
   * Returns all labels reachable via the given attribute. If a label is listed multiple times, each
   * instance appears in the returned list.
   *
   * @param includeSelectKeys whether to include config_setting keys for configurable attributes
   */
  public List<Label> getReachableLabels(String attributeName, boolean includeSelectKeys)
      throws InterruptedException {
    final ImmutableList.Builder<Label> builder = ImmutableList.builder();
    visitLabels(getAttributeDefinition(attributeName), includeSelectKeys,
        new AcceptsLabelAttribute() {
          @Override
          public void acceptLabelAttribute(Label label, Attribute attribute) {
            builder.add(label);
          }
        });
    return builder.build();
  }

  /**
   * Returns the labels that might appear multiple times in the same attribute value.
   */
  public Set<Label> checkForDuplicateLabels(Attribute attribute) {
    String attrName = attribute.getName();
    Type<?> attrType = attribute.getType();
    ImmutableSet.Builder<Label> duplicates = ImmutableSet.builder();

    SelectorList<?> selectorList = getSelectorList(attribute.getName(), attrType);
    if (selectorList == null || selectorList.getSelectors().size() == 1) {
      // Three possible scenarios:
      //  1) Plain old attribute (no selects). Without selects, visitAttribute runs efficiently.
      //  2) Computed default, possibly depending on other attributes using select. In this case,
      //     visitAttribute might be inefficient. But we have no choice but to iterate over all
      //     possible values (since we have to compute them), so we take the efficiency hit.
      //  3) "attr = select({...})". With just a single select, visitAttribute runs efficiently.
      for (Object value : visitAttribute(attrName, attrType)) {
        if (value != null) {
          // TODO(bazel-team): Calculate duplicates directly using attrType.visitLabels in order to
          // avoid intermediate collections here.
          duplicates.addAll(CollectionUtils.duplicatedElementsOf(extractLabels(attrType, value)));
        }
      }
    } else {
      // Multiple selects concatenated together. It's expensive to iterate over every possible
      // value, so instead collect all labels across all the selects and check for duplicates.
      // This is overly strict, since this counts duplicates across values. We can presumably
      // relax this if necessary, but doing so would incur the value iteration expense this
      // code path avoids.
      List<Label> combinedLabels = new LinkedList<>(); // Labels that appear across all selectors.
      for (Selector<?> selector : selectorList.getSelectors()) {
        // Labels within a single selector. It's okay for there to be duplicates as long as
        // they're in different selector paths (since only one path can actually get chosen).
        Set<Label> selectorLabels = new LinkedHashSet<>();
        for (Object selectorValue : selector.getEntries().values()) {
          List<Label> labelsInSelectorValue = extractLabels(attrType, selectorValue);
          // Duplicates within a single path are not okay.
          duplicates.addAll(CollectionUtils.duplicatedElementsOf(labelsInSelectorValue));
          Iterables.addAll(selectorLabels, labelsInSelectorValue);
        }
        combinedLabels.addAll(selectorLabels);
      }
      duplicates.addAll(CollectionUtils.duplicatedElementsOf(combinedLabels));
    }

    return duplicates.build();
  }

  /**
   * Returns a list of the possible values of the specified attribute in the specified rule.
   *
   * <p>If the attribute's value is a simple value, then this returns a singleton list of that
   * value.
   *
   * <p>If the attribute's value is an expression containing one or many {@code select(...)}
   * expressions, then this returns a list of all values that expression may evaluate to.
   *
   * <p>If the attribute does not have an explicit value for this rule, and the rule provides a
   * computed default, the computed default function is evaluated given the rule's other attribute
   * values as inputs and the output is returned in a singleton list.
   *
   * <p>If the attribute does not have an explicit value for this rule, and the rule provides a
   * computed default, and the computed default function depends on other attributes whose values
   * contain {@code select(...)} expressions, then the computed default function is evaluated for
   * every possible combination of input values, and the list of outputs is returned.
   */
  public Iterable<Object> getPossibleAttributeValues(Rule rule, Attribute attr) {
    // Values may be null, so use normal collections rather than immutable collections.
    // This special case for the visibility attribute is needed because its value is replaced
    // with an empty list during package loading if it is public or private in order not to visit
    // the package called 'visibility'.
    if (attr.getName().equals("visibility")) {
      List<Object> result = new ArrayList<>(1);
      result.add(rule.getVisibility().getDeclaredLabels());
      return result;
    }
    return Lists.<Object>newArrayList(visitAttribute(attr.getName(), attr.getType()));
  }

  /**
   * Coerces the list {@param possibleValues} of values of type {@param attrType} to a single
   * value of that type, in the following way:
   *
   * <p>If the list contains a single value, return that value.
   *
   * <p>If the list contains zero or multiple values and the type is a scalar type, return {@code
   * null}.
   *
   * <p>If the list contains zero or multiple values and the type is a collection or map type,
   * merge the collections/maps in the list and return the merged collection/map.
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public static Object flattenAttributeValues(Type<?> attrType, Iterable<Object> possibleValues) {
    // If there is only one possible value, return it.
    if (Iterables.size(possibleValues) == 1) {
      return Iterables.getOnlyElement(possibleValues);
    }

    // Otherwise, there are multiple possible values. To conform to the message shape expected by
    // query output's clients, we must transform the list of possible values. This transformation
    // will be lossy, but this is the best we can do.

    // If the attribute's type is not a collection type, return null. Query output's clients do
    // not support list values for scalar attributes.
    if (scalarTypes.contains(attrType)) {
      return null;
    }

    // If the attribute's type is a collection type, merge the list of collections into a single
    // collection. This is a sensible solution for query output's clients, which are happy to get
    // the union of possible values.
    if (attrType == STRING_LIST
        || attrType == LABEL_LIST
        || attrType == NODEP_LABEL_LIST
        || attrType == OUTPUT_LIST
        || attrType == DISTRIBUTIONS
        || attrType == INTEGER_LIST
        || attrType == FILESET_ENTRY_LIST) {
      Builder<Object> builder = ImmutableList.builder();
      for (Object possibleValue : possibleValues) {
        Collection<Object> collection = (Collection<Object>) possibleValue;
        for (Object o : collection) {
          builder.add(o);
        }
      }
      return builder.build();
    }

    // Same for maps as for collections.
    if (attrType == STRING_DICT
        || attrType == STRING_DICT_UNARY
        || attrType == STRING_LIST_DICT
        || attrType == LABEL_DICT_UNARY) {
      Map<Object, Object> mergedDict = new HashMap<>();
      for (Object possibleValue : possibleValues) {
        Map<Object, Object> stringDict = (Map<Object, Object>) possibleValue;
        for (Entry<Object, Object> entry : stringDict.entrySet()) {
          mergedDict.put(entry.getKey(), entry.getValue());
        }
      }
      return mergedDict;
    }

    throw new AssertionError("Unknown type: " + attrType);
  }

  /**
   * Returns a list of all possible values an attribute can take for this rule.
   *
   * <p>Note that when an attribute uses multiple selects, or is a {@link Attribute.ComputedDefault}
   * that depends on configurable attributes, it can potentially take on many values. So be cautious
   * about unnecessarily relying on this method.
   */
  public <T> Iterable<T> visitAttribute(String attributeName, Type<T> type) {
    // If this attribute value is configurable, visit all possible values.
    SelectorList<T> selectorList = getSelectorList(attributeName, type);
    if (selectorList != null) {
      ImmutableList.Builder<T> builder = ImmutableList.builder();
      visitConfigurableAttribute(selectorList.getSelectors(), new BoundSelectorPaths(), type,
          null, builder);
      return builder.build();
    }

    // If this attribute is a computed default, feed it all possible value combinations of
    // its declared dependencies and return all computed results. For example, if this default
    // uses attributes x and y, x can configurably be x1 or x2, and y can configurably be y1
    // or y1, then compute default values for the (x1,y1), (x1,y2), (x2,y1), and (x2,y2) cases.
    Attribute.ComputedDefault computedDefault = getComputedDefault(attributeName, type);
    if (computedDefault != null) {
      return computedDefault.getPossibleValues(type, rule);
    }

    // For any other attribute, just return its direct value.
    T value = get(attributeName, type);
    return value == null ? ImmutableList.<T>of() : ImmutableList.of(value);
  }

  /**
   * Determines all possible values a configurable attribute can take. Do not call this method
   * unless really necessary (see TODO comment inside).
   *
   * @param selectors the selectors that make up this attribute assignment (in order)
   * @param boundSelectorPaths paths that have already been chosen from previous selectors in an
   *     earlier recursive call of this method. For example, given
   *     <pre>cmd = select({':a': 'w', ':b': 'x'}) + select({':a': 'y', ':b': 'z'})</pre>
   *     the only possible values for <code>cmd</code> are <code>"wy"</code> and <code>"xz"</code>.
   *     This is because the selects have the same conditions, so whatever matches the first also
   *     matches the second. Note that this doesn't work for selects with overlapping but
   *     <i>different</i> key sets. That's because of key specialization (see
   *     {@link com.google.devtools.build.lib.analysis.ConfiguredAttributeMapper} - if the
   *     second select also included a condition <code>':c'</code> that includes both the flags
   *     in <code>':a'</code> and <code>':b'</code>, <code>':c'</code> would be chosen over
   *     them both.
   * @param type the type of this attribute
   * @param currentValueSoFar the partial value produced so far from earlier calls to this method
   * @param valuesBuilder output container for full values this attribute can take
   */
  private <T> void visitConfigurableAttribute(List<Selector<T>> selectors,
      BoundSelectorPaths boundSelectorPaths, Type<T> type, T currentValueSoFar,
      ImmutableList.Builder<T> valuesBuilder) {
    // TODO(bazel-team): minimize or eliminate uses of this interface. It necessarily grows
    // exponentially with the number of selects in the attribute. Is that always necessary?
    // For example, dependency resolution just needs to know every possible label an attribute
    // might reference, but it doesn't need to know the exact combination of labels that make
    // up a value. This may be even less important for non-label values (e.g. strings), which
    // have no impact on the dependency structure.

    if (selectors.isEmpty()) {
      if (currentValueSoFar != null) {
        // Null values arise when a None is used as the value of a Selector for a type without a
        // default value.
        // TODO(gregce): visitAttribute should probably convey that an unset attribute is possible.
        // Therefore we need to actually handle null values here.
        valuesBuilder.add(currentValueSoFar);
      }
    } else {
      Selector<T> firstSelector = selectors.get(0);
      List<Selector<T>> remainingSelectors = selectors.subList(1, selectors.size());

      Map<Label, T> firstSelectorEntries = firstSelector.getEntries();
      Label boundKey = boundSelectorPaths.getChosenKey(firstSelectorEntries.keySet());
      if (boundKey != null) {
        // If we've already followed some path from a previous selector with the same exact
        // conditions as this one, we only need to visit that path (since the same key will
        // match both selectors).
        T boundValue = firstSelectorEntries.get(boundKey);
        visitConfigurableAttribute(remainingSelectors, boundSelectorPaths, type,
                    currentValueSoFar == null
                        ? boundValue
                        : type.concat(ImmutableList.of(currentValueSoFar, boundValue)),
                    valuesBuilder);
      } else {
        // Otherwise, we need to iterate over all possible paths.
        for (Map.Entry<Label, T> selectorBranch : firstSelectorEntries.entrySet()) {
          // Bind this particular path for later selectors using the same conditions.
          boundSelectorPaths.bind(firstSelectorEntries.keySet(), selectorBranch.getKey());
          visitConfigurableAttribute(remainingSelectors, boundSelectorPaths, type,
              currentValueSoFar == null
                  ? selectorBranch.getValue()
                  : type.concat(ImmutableList.of(currentValueSoFar, selectorBranch.getValue())),
              valuesBuilder);
          // Unbind the path (so when we pop back up the recursive stack we can rebind it to new
          // values if we visit this selector again).
          boundSelectorPaths.unbind(firstSelectorEntries.keySet());
        }
      }
    }
  }

  /**
   * Helper class for {@link #visitConfigurableAttribute}. See that method's comments for more
   * details.
   */
  private static class BoundSelectorPaths {
    private final Map<Set<Label>, Label> bindings = new HashMap<>();

    /**
     * Binds the given config key set to the specified path. There should be no previous binding
     * for this key set.
     */
    public void bind(Set<Label> allKeys, Label chosenKey) {
      Preconditions.checkState(allKeys.contains(chosenKey));
      Verify.verify(bindings.put(allKeys, chosenKey) == null);
    }

    /**
     * Unbinds the given config key set.
     */
    public void unbind(Set<Label> allKeys) {
      Verify.verifyNotNull(bindings.remove(allKeys));
    }

    /**
     * Returns the key this config key set is bound to or null if no binding.
     */
    public Label getChosenKey(Set<Label> allKeys) {
      return bindings.get(allKeys);
    }
  }

  /**
   * Given a list of attributes, creates an {attrName -> attrValue} map for every possible
   * combination of those attributes' values and returns a list of all the maps.
   *
   * <p>For example, given attributes x and y, which respectively have possible values x1, x2 and
   * y1, y2, this returns:
   *
   * <pre>
   *   [
   *    {x: x1, y: y1},
   *    {x: x1, y: y2},
   *    {x: x2, y: y1},
   *    {x: x2, y: y2}
   *   ]
   * </pre>
   *
   * <p>The work done by this method may be limited by providing a {@link ComputationLimiter} that
   * throws if too much work is attempted.
   */
  <TException extends Exception> List<Map<String, Object>> visitAttributes(
      List<String> attributes, ComputationLimiter<TException> limiter) throws TException {
    List<Map<String, Object>> depMaps = new LinkedList<>();
    AtomicInteger combinationsSoFar = new AtomicInteger(0);
    visitAttributesInner(
        attributes, depMaps, ImmutableMap.<String, Object>of(), combinationsSoFar, limiter);
    return depMaps;
  }

  /**
   * A recursive function used in the implementation of {@link #visitAttributes}.
   *
   * @param attributes a list of attributes that are not yet assigned values in the {@code
   *     currentMap} parameter.
   * @param mappings a mutable list of {attrName --> attrValue} maps collected so far. This method
   *     will add newly discovered maps to the list.
   * @param currentMap a (possibly non-empty) map holding {attrName --> attrValue} assignments for
   *     attributes not in the {@code attributes} list.
   * @param combinationsSoFar a counter for all previously processed combinations of possible
   *     values.
   * @param limiter a strategy to limit the work done by invocations of this method.
   */
  private <TException extends Exception> void visitAttributesInner(
      List<String> attributes,
      List<Map<String, Object>> mappings,
      Map<String, Object> currentMap,
      AtomicInteger combinationsSoFar,
      ComputationLimiter<TException> limiter)
      throws TException {
    if (attributes.isEmpty()) {
      // Because this method uses exponential time/space on the number of inputs, we may limit
      // the total number of method calls.
      limiter.onComputationCount(combinationsSoFar.incrementAndGet());
      // Recursive base case: store whatever's already been populated in currentMap.
      mappings.add(currentMap);
      return;
    }

    // Take the first attribute in the dependency list and iterate over all its values. For each
    // value x, copy currentMap with the additional entry { firstAttrName: x }, then feed
    // this recursively into a subcall over all remaining dependencies. This recursively
    // continues until we run out of values.
    String firstAttribute = attributes.get(0);
    Iterable<?> firstAttributePossibleValues =
        visitAttribute(firstAttribute, getAttributeType(firstAttribute));
    for (Object value : firstAttributePossibleValues) {
      Map<String, Object> newMap = new HashMap<>();
      newMap.putAll(currentMap);
      newMap.put(firstAttribute, value);
      visitAttributesInner(
          attributes.subList(1, attributes.size()), mappings, newMap, combinationsSoFar, limiter);
    }
  }

  /**
   * Returns an {@link AttributeMap} that delegates to {@code AggregatingAttributeMapper.this}
   * except for {@link #get} calls for attributes that are configurable. In that case, the {@link
   * AttributeMap} looks up an attribute's value in {@code directMap}. Any attempt to {@link #get} a
   * configurable attribute that's not in {@code directMap} causes an {@link
   * IllegalArgumentException} to be thrown.
   */
  AttributeMap createMapBackedAttributeMap(final Map<String, Object> directMap) {
    final AggregatingAttributeMapper owner = AggregatingAttributeMapper.this;
    return new AttributeMap() {

      @Override
      public <T> T get(String attributeName, Type<T> type) {
        owner.checkType(attributeName, type);
        if (getNonConfigurableAttributes().contains(attributeName)) {
          return owner.get(attributeName, type);
        }
        if (!directMap.containsKey(attributeName)) {
          throw new IllegalArgumentException(
              "attribute \""
                  + attributeName
                  + "\" isn't available in this computed default context");
        }
        return type.cast(directMap.get(attributeName));
      }

      @Override
      public <T> boolean isConfigurable(String attributeName, Type<T> type) {
        return owner.isConfigurable(attributeName, type);
      }

      @Override
      public String getName() {
        return owner.getName();
      }

      @Override
      public Label getLabel() {
        return owner.getLabel();
      }

      @Override
      public Iterable<String> getAttributeNames() {
        return ImmutableList.<String>builder()
            .addAll(directMap.keySet())
            .addAll(getNonConfigurableAttributes())
            .build();
      }

      @Override
      public void visitLabels(AcceptsLabelAttribute observer) throws InterruptedException {
        owner.visitLabels(observer);
      }

      @Override
      public String getPackageDefaultHdrsCheck() {
        return owner.getPackageDefaultHdrsCheck();
      }

      @Override
      public Boolean getPackageDefaultTestOnly() {
        return owner.getPackageDefaultTestOnly();
      }

      @Override
      public String getPackageDefaultDeprecation() {
        return owner.getPackageDefaultDeprecation();
      }

      @Override
      public ImmutableList<String> getPackageDefaultCopts() {
        return owner.getPackageDefaultCopts();
      }

      @Nullable
      @Override
      public Type<?> getAttributeType(String attrName) {
        return owner.getAttributeType(attrName);
      }

      @Nullable
      @Override
      public Attribute getAttributeDefinition(String attrName) {
        return owner.getAttributeDefinition(attrName);
      }

      @Override
      public boolean isAttributeValueExplicitlySpecified(String attributeName) {
        return owner.isAttributeValueExplicitlySpecified(attributeName);
      }

      @Override
      public boolean has(String attrName, Type<?> type) {
        return owner.has(attrName, type);
      }
    };
  }

  private static ImmutableList<Label> extractLabels(Type<?> type, Object value) {
    try {
      final ImmutableList.Builder<Label> result = ImmutableList.builder();
      type.visitLabels(new Type.LabelVisitor() {
        @Override
        public void visit(@Nullable Object object) {
          if (object != null) {
            result.add((Label) object);
          }
        }
      }, value);
      return result.build();
    } catch (InterruptedException e) {
      throw new IllegalStateException("Unexpected InterruptedException", e);
    }
  }
}
