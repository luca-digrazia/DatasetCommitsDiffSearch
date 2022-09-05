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

package com.google.devtools.build.lib.packages;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.LabelSyntaxException;
import com.google.devtools.build.lib.packages.License.DistributionType;
import com.google.devtools.build.lib.packages.License.LicenseParsingException;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SelectorValue;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.syntax.Type.ConversionException;
import com.google.devtools.build.lib.syntax.Type.DictType;
import com.google.devtools.build.lib.syntax.Type.ListType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Collection of data types that are specific to building things, i.e. not inherent to Skylark.
 */
public final class BuildType {

  /**
   * The type of a label. Labels are not actually a first-class datatype in
   * the build language, but they are so frequently used in the definitions of
   * attributes that it's worth treating them specially (and providing support
   * for resolution of relative-labels in the <code>convert()</code> method).
   */
  public static final Type<Label> LABEL = new LabelType();
  /**
   * The type of a dictionary of {@linkplain #LABEL labels}.
   */
  public static final DictType<String, Label> LABEL_DICT_UNARY = DictType.create(
      Type.STRING, LABEL);
  /**
   *  The type of a list of {@linkplain #LABEL labels}.
   */
  public static final ListType<Label> LABEL_LIST = ListType.create(LABEL);
  /**
   * The type of a dictionary of {@linkplain #LABEL_LIST label lists}.
   */
  // TODO(gregce): remove after abi_deps is removed.
  public static final DictType<String, List<Label>> LABEL_LIST_DICT =
      DictType.create(Type.STRING, LABEL_LIST);
  /**
   * This is a label type that does not cause dependencies. It is needed because
   * certain rules want to verify the type of a target referenced by one of their attributes, but
   * if there was a dependency edge there, it would be a circular dependency.
   */
  public static final Type<Label> NODEP_LABEL = new LabelType();
  /**
   *  The type of a list of {@linkplain #NODEP_LABEL labels} that do not cause
   *  dependencies.
   */
  public static final ListType<Label> NODEP_LABEL_LIST = ListType.create(NODEP_LABEL);
  /**
   * The type of a license. Like Label, licenses aren't first-class, but
   * they're important enough to justify early syntax error detection.
   */
  public static final Type<License> LICENSE = new LicenseType();
  /**
   * The type of a single distribution.  Only used internally, as a type
   * symbol, not a converter.
   */
  public static final Type<DistributionType> DISTRIBUTION = new Type<DistributionType>() {
    @Override
    public DistributionType cast(Object value) {
      return (DistributionType) value;
    }

    @Override
    public DistributionType convert(Object x, String what, Object context) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DistributionType getDefaultValue() {
      return null;
    }

    @Override
    public Collection<Object> flatten(Object value) {
      return NOT_COMPOSITE_TYPE;
    }

    @Override
    public String toString() {
      return "distribution";
    }
  };
  /**
   * The type of a set of distributions. Distributions are not a first-class type,
   * but they do warrant early syntax checking.
   */
  public static final Type<Set<DistributionType>> DISTRIBUTIONS = new Distributions();
  /**
   *  The type of an output file, treated as a {@link #LABEL}.
   */
  public static final Type<Label> OUTPUT = new OutputType();
  /**
   *  The type of a list of {@linkplain #OUTPUT outputs}.
   */
  public static final ListType<Label> OUTPUT_LIST = ListType.create(OUTPUT);
  /**
   * The type of a FilesetEntry attribute inside a Fileset.
   */
  public static final Type<FilesetEntry> FILESET_ENTRY = new FilesetEntryType();
  /**
   * The type of a list of {@linkplain #FILESET_ENTRY FilesetEntries}.
   */
  public static final ListType<FilesetEntry> FILESET_ENTRY_LIST = ListType.create(FILESET_ENTRY);
  /**
   * The type of a TriState with values: true (x>0), false (x==0), auto (x<0).
   */
  public static final Type<TriState> TRISTATE = new TriStateType();

  private BuildType() {
    // Do not instantiate
  }

  /**
   * Returns whether the specified type is a label type or not.
   */
  public static boolean isLabelType(Type<?> type) {
    return type == LABEL || type == LABEL_LIST || type == LABEL_DICT_UNARY
        || type == NODEP_LABEL || type == NODEP_LABEL_LIST
        || type == LABEL_LIST_DICT || type == FILESET_ENTRY_LIST;
  }

  /**
   * Variation of {@link Type#convert} that supports selector expressions for configurable
   * attributes* (i.e. "{ config1: 'value1_of_orig_type', config2: 'value2_of_orig_type; }"). If x
   * is a selector expression, returns a {@link Selector} instance that contains key-mapped entries
   * of the native type. Else, returns the native type directly.
   *
   * <p>The caller is responsible for casting the returned value appropriately.
   */
  public static <T> Object selectableConvert(
      Type type, Object x, String what, @Nullable Label context)
      throws ConversionException {
    if (x instanceof com.google.devtools.build.lib.syntax.SelectorList) {
      return new SelectorList<T>(
          ((com.google.devtools.build.lib.syntax.SelectorList) x).getElements(),
          what, context, type);
    } else {
      return type.convert(x, what, context);
    }
  }

  private static class FilesetEntryType extends
      Type<FilesetEntry> {
    @Override
    public FilesetEntry cast(Object value) {
      return (FilesetEntry) value;
    }

    @Override
    public FilesetEntry convert(Object x, String what, Object context)
        throws ConversionException {
      if (!(x instanceof FilesetEntry)) {
        throw new ConversionException(this, x, what);
      }
      return (FilesetEntry) x;
    }

    @Override
    public String toString() {
      return "FilesetEntry";
    }

    @Override
    public FilesetEntry getDefaultValue() {
      return null;
    }

    @Override
    public Collection<? extends Object> flatten(Object value) {
      return cast(value).getLabels();
    }
  }

  private static class LabelType extends Type<Label> {
    @Override
    public Label cast(Object value) {
      return (Label) value;
    }

    @Override
    public Label getDefaultValue() {
      return null; // Labels have no default value
    }

    @Override
    public Collection<Label> flatten(Object value) {
      return ImmutableList.of(cast(value));
    }

    @Override
    public String toString() {
      return "label";
    }

    @Override
    public Label convert(Object x, String what, Object context)
        throws ConversionException {
      if (x instanceof Label) {
        return (Label) x;
      }
      try {
        return ((Label) context).getRelative(STRING.convert(x, what, context));
      } catch (LabelSyntaxException e) {
        throw new ConversionException("invalid label '" + x + "' in "
            + what + ": " + e.getMessage());
      }
    }
  }

  /**
   * Like Label, LicenseType is a derived type, which is declared specially
   * in order to allow syntax validation. It represents the licenses, as
   * described in {@ref License}.
   */
  public static class LicenseType extends Type<License> {
    @Override
    public License cast(Object value) {
      return (License) value;
    }

    @Override
    public License convert(Object x, String what, Object context) throws ConversionException {
      try {
        List<String> licenseStrings = STRING_LIST.convert(x, what);
        return License.parseLicense(licenseStrings);
      } catch (LicenseParsingException e) {
        throw new ConversionException(e.getMessage());
      }
    }

    @Override
    public License getDefaultValue() {
      return License.NO_LICENSE;
    }

    @Override
    public Collection<Object> flatten(Object value) {
      return NOT_COMPOSITE_TYPE;
    }

    @Override
    public String toString() {
      return "license";
    }
  }

  /**
   * Like Label, Distributions is a derived type, which is declared specially
   * in order to allow syntax validation. It represents the declared distributions
   * of a target, as described in {@ref License}.
   */
  private static class Distributions extends
      Type<Set<DistributionType>> {
    @SuppressWarnings("unchecked")
    @Override
    public Set<DistributionType> cast(Object value) {
      return (Set<DistributionType>) value;
    }

    @Override
    public Set<DistributionType> convert(Object x, String what, Object context)
        throws ConversionException {
      try {
        List<String> distribStrings = STRING_LIST.convert(x, what);
        return License.parseDistributions(distribStrings);
      } catch (LicenseParsingException e) {
        throw new ConversionException(e.getMessage());
      }
    }

    @Override
    public Set<DistributionType> getDefaultValue() {
      return Collections.emptySet();
    }

    @Override
    public Collection<Object> flatten(Object what) {
      return NOT_COMPOSITE_TYPE;
    }

    @Override
    public String toString() {
      return "distributions";
    }

    @Override
    public Type<DistributionType> getListElementType() {
      return DISTRIBUTION;
    }
  }

  private static class OutputType extends Type<Label> {
    @Override
    public Label cast(Object value) {
      return (Label) value;
    }

    @Override
    public Label getDefaultValue() {
      return null;
    }

    @Override
    public Collection<Label> flatten(Object value) {
      return ImmutableList.of(cast(value));
    }

    @Override
    public String toString() {
      return "output";
    }

    @Override
    public Label convert(Object x, String what, Object context)
        throws ConversionException {

      String value;
      try {
        value = STRING.convert(x, what, context);
      } catch (ConversionException e) {
        throw new ConversionException(this, x, what);
      }
      try {
        // Enforce value is relative to the context.
        Label currentRule = (Label) context;
        Label result = currentRule.getRelative(value);
        if (!result.getPackageIdentifier().equals(currentRule.getPackageIdentifier())) {
          throw new ConversionException("label '" + value + "' is not in the current package");
        }
        return result;
      } catch (LabelSyntaxException e) {
        throw new ConversionException(
            "illegal output file name '" + value + "' in rule " + context + ": "
            + e.getMessage());
      }
    }
  }

  /**
   * Holds an ordered collection of {@link Selector}s. This is used to support
   * {@code attr = rawValue + select(...) + select(...) + ..."} syntax. For consistency's
   * sake, raw values are stored as selects with only a default condition.
   */
  public static final class SelectorList<T> {
    private final Type<T> originalType;
    private final List<Selector<T>> elements;

    @VisibleForTesting
    SelectorList(List<Object> x, String what, @Nullable Label context,
        Type<T> originalType) throws ConversionException {
      if (x.size() > 1 && originalType.concat(ImmutableList.<T>of()) == null) {
        throw new ConversionException(
            String.format("type '%s' doesn't support select concatenation", originalType));
      }

      ImmutableList.Builder<Selector<T>> builder = ImmutableList.builder();
      for (Object elem : x) {
        if (elem instanceof SelectorValue) {
          builder.add(new Selector<T>(((SelectorValue) elem).getDictionary(), what,
              context, originalType));
        } else {
          T directValue = originalType.convert(elem, what, context);
          builder.add(new Selector<T>(ImmutableMap.of(Selector.DEFAULT_CONDITION_KEY, directValue),
              what, context, originalType));
        }
      }
      this.originalType = originalType;
      this.elements = builder.build();
    }

    SelectorList(List<Selector<T>> elements, Type<T> originalType) {
      this.elements = ImmutableList.copyOf(elements);
      this.originalType = originalType;
    }

    /**
     * Returns a syntactically order-preserved list of all values and selectors for this attribute.
     */
    public List<Selector<T>> getSelectors() {
      return elements;
    }

    /**
     * Returns the native Type for this attribute (i.e. what this would be if it wasn't a
     * selector list).
     */
    public Type<T> getOriginalType() {
      return originalType;
    }

    /**
     * Returns the labels of all configurability keys across all selects in this expression.
     */
    public Set<Label> getKeyLabels() {
      ImmutableSet.Builder<Label> keys = ImmutableSet.builder();
      for (Selector<T> selector : getSelectors()) {
         for (Label label : selector.getEntries().keySet()) {
           if (!Selector.isReservedLabel(label)) {
             keys.add(label);
           }
         }
      }
      return keys.build();
    }
  }

  /**
   * Special Type that represents a selector expression for configurable attributes. Holds a
   * mapping of {@code <Label, T>} entries, where keys are configurability patterns and values are
   * objects of the attribute's native Type.
   */
  public static final class Selector<T> {
    /** Value to use when none of an attribute's selection criteria match. */
    @VisibleForTesting
    public static final String DEFAULT_CONDITION_KEY = "//conditions:default";

    public static final Label DEFAULT_CONDITION_LABEL =
        Label.parseAbsoluteUnchecked(DEFAULT_CONDITION_KEY);

    private final Type<T> originalType;
    private final Map<Label, T> map; // Can hold null values.
    private final Set<Label> conditionsWithDefaultValues;
    private final boolean hasDefaultCondition;

    @VisibleForTesting
    Selector(ImmutableMap<?, ?> x, String what, @Nullable Label context, Type<T> originalType)
        throws ConversionException {
      this.originalType = originalType;
      Map<Label, T> result = new LinkedHashMap<>();
      ImmutableSet.Builder<Label> defaultValuesBuilder = ImmutableSet.builder();
      boolean foundDefaultCondition = false;
      for (Entry<?, ?> entry : x.entrySet()) {
        Label key = LABEL.convert(entry.getKey(), what, context);
        if (key.equals(DEFAULT_CONDITION_LABEL)) {
          foundDefaultCondition = true;
        }
        if (entry.getValue() == Runtime.NONE) {
          // { "//condition": None } is the same as not setting the value.
          result.put(key, originalType.getDefaultValue());
          defaultValuesBuilder.add(key);
        } else {
          result.put(key, originalType.convert(entry.getValue(), what, context));
        }
      }
      map = Collections.unmodifiableMap(result);
      conditionsWithDefaultValues = defaultValuesBuilder.build();
      hasDefaultCondition = foundDefaultCondition;
    }

    /**
     * Returns the selector's (configurability pattern --gt; matching values) map.
     *
     * <p>Entries in this map retain the order of the entries in the map provided to the {@link
     * #Selector} constructor.
     */
    public Map<Label, T> getEntries() {
      return map;
    }

    /**
     * Returns the value to use when none of the attribute's selection keys match.
     */
    public T getDefault() {
      return map.get(DEFAULT_CONDITION_LABEL);
    }

    /**
     * Returns whether or not this selector has a default condition.
     */
    public boolean hasDefault() {
      return hasDefaultCondition;
    }

    /**
     * Returns the native Type for this attribute (i.e. what this would be if it wasn't a
     * selector expression).
     */
    public Type<T> getOriginalType() {
      return originalType;
    }

    /**
     * Returns true if this selector has the structure: {"//conditions:default": ...}. That means
     * all values are always chosen.
     */
    public boolean isUnconditional() {
      return map.size() == 1 && hasDefaultCondition;
    }

    /**
     * Returns true if an explicit value is set for the given condition, vs. { "//condition": None }
     * which means revert to the default.
     */
    public boolean isValueSet(Label condition) {
      return !conditionsWithDefaultValues.contains(condition);
    }

    /**
     * Returns true for labels that are "reserved selector key words" and not intended to
     * map to actual targets.
     */
    public static boolean isReservedLabel(Label label) {
      return DEFAULT_CONDITION_LABEL.equals(label);
    }
  }

  /**
   * Tristate values are needed for cases where user intent matters.
   *
   * <p>Tristate values are not explicitly interchangeable with booleans and are
   * handled explicitly as TriStates. Prefer Booleans with default values where
   * possible.  The main use case for TriState values is when a Rule's behavior
   * must interact with a Flag value in a complicated way.</p>
   */
  private static class TriStateType extends Type<TriState> {
    @Override
    public TriState cast(Object value) {
      return (TriState) value;
    }

    @Override
    public TriState getDefaultValue() {
      return TriState.AUTO;
    }

    @Override
    public Collection<Object> flatten(Object value) {
      return NOT_COMPOSITE_TYPE;
    }

    @Override
    public String toString() {
      return "tristate";
    }

    // Like BooleanType, this must handle integers as well.
    @Override
    public TriState convert(Object x, String what, Object context)
        throws ConversionException {
      if (x instanceof TriState) {
        return (TriState) x;
      }
      if (x instanceof Boolean) {
        return ((Boolean) x) ? TriState.YES : TriState.NO;
      }
      Integer xAsInteger = INTEGER.convert(x, what, context);
      if (xAsInteger == -1) {
        return TriState.AUTO;
      } else if (xAsInteger == 1) {
        return TriState.YES;
      } else if (xAsInteger == 0) {
        return TriState.NO;
      }
      throw new ConversionException(this, x, "TriState values is not one of [-1, 0, 1]");
    }
  }
}
