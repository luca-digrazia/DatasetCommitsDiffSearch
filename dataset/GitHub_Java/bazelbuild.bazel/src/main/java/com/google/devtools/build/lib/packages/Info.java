// Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import com.google.devtools.build.lib.syntax.ClassObject;
import com.google.devtools.build.lib.syntax.Concatable;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.Printer;
import com.google.devtools.build.lib.syntax.SkylarkType;
import com.google.devtools.build.lib.util.Preconditions;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Represents information provided by a {@link Provider}. */
@SkylarkModule(
  name = "struct",
  category = SkylarkModuleCategory.BUILTIN,
  doc =
      "A special language element to support structs (i.e. simple value objects). "
          + "See the global <a href=\"globals.html#struct\">struct</a> function "
          + "for more details."
)
public class Info implements ClassObject, SkylarkValue, Concatable, Serializable {
  private final Provider provider;
  private final ImmutableMap<String, Object> values;
  private final Location creationLoc;
  private final String errorMessage;

  /** Creates an empty struct with a given location. */
  public Info(Provider provider, Location location) {
    this.provider = provider;
    this.values = ImmutableMap.of();
    this.creationLoc = location;
    this.errorMessage = provider.getErrorMessageFormatForInstances();
  }

  /** Creates a built-in struct (i.e. without creation loc). */
  public Info(Provider provider, Map<String, Object> values) {
    this.provider = provider;
    this.values = copyValues(values);
    this.creationLoc = null;
    this.errorMessage = provider.getErrorMessageFormatForInstances();
  }

  /**
   * Creates a built-in struct (i.e. without creation loc).
   *
   * <p>Allows to supply a specific error message. Only used in
   * {@link com.google.devtools.build.lib.packages.NativeProvider.StructConstructor#create(Map,
   * String)} If you need to override an error message, preferred way is to create a specific {@link
   * NativeProvider}.
   */
  Info(Provider provider, Map<String, Object> values, String errorMessage) {
    this.provider = provider;
    this.values = copyValues(values);
    this.creationLoc = null;
    this.errorMessage = Preconditions.checkNotNull(errorMessage);
  }

  public Info(Provider provider, Map<String, Object> values, Location creationLoc) {
    this.provider = provider;
    this.values = copyValues(values);
    this.creationLoc = Preconditions.checkNotNull(creationLoc);
    this.errorMessage = provider.getErrorMessageFormatForInstances();
  }

  // Ensure that values are all acceptable to Skylark before to stuff them in a ClassObject
  private ImmutableMap<String, Object> copyValues(Map<String, Object> values) {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    for (Map.Entry<String, Object> e : values.entrySet()) {
      builder.put(
          Attribute.getSkylarkName(e.getKey()), SkylarkType.convertToSkylark(e.getValue(), null));
    }
    return builder.build();
  }

  @Override
  public Object getValue(String name) {
    return values.get(name);
  }

  public boolean hasKey(String name) {
    return values.containsKey(name);
  }

  /** Returns a value and try to cast it into specified type */
  public <TYPE> TYPE getValue(String key, Class<TYPE> type) throws EvalException {
    Object obj = values.get(key);
    if (obj == null) {
      return null;
    }
    SkylarkType.checkType(obj, type, key);
    return type.cast(obj);
  }

  @Override
  public ImmutableCollection<String> getKeys() {
    return values.keySet();
  }

  public Location getCreationLoc() {
    return Preconditions.checkNotNull(creationLoc, "This struct was not created in a Skylark code");
  }

  @Override
  public Concatter getConcatter() {
    return StructConcatter.INSTANCE;
  }

  public Provider getProvider() {
    return provider;
  }

  @Nullable
  public Location getCreationLocOrNull() {
    return creationLoc;
  }

  private static class StructConcatter implements Concatter {
    private static final StructConcatter INSTANCE = new StructConcatter();

    private StructConcatter() {}

    @Override
    public Info concat(Concatable left, Concatable right, Location loc) throws EvalException {
      Info lval = (Info) left;
      Info rval = (Info) right;
      if (!lval.provider.equals(rval.provider)) {
        throw new EvalException(
            loc,
            String.format(
                "Cannot concat %s with %s",
                lval.provider.getPrintableName(), rval.provider.getPrintableName()));
      }
      SetView<String> commonFields = Sets.intersection(lval.values.keySet(), rval.values.keySet());
      if (!commonFields.isEmpty()) {
        throw new EvalException(
            loc,
            "Cannot concat structs with common field(s): " + Joiner.on(",").join(commonFields));
      }
      return new Info(
          lval.provider,
          ImmutableMap.<String, Object>builder().putAll(lval.values).putAll(rval.values).build(),
          loc);
    }
  }

  @Override
  public String errorMessage(String name) {
    String suffix =
        "Available attributes: "
            + Joiner.on(", ").join(Ordering.natural().sortedCopy(values.keySet()));
    return String.format(errorMessage, name) + "\n" + suffix;
  }

  @Override
  public boolean isImmutable() {
    // If the provider is not yet exported the hash code of the object is subject to change
    if (!provider.isExported()) {
      return false;
    }
    for (Object item : values.values()) {
      if (!EvalUtils.isImmutable(item)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object otherObject) {
    if (!(otherObject instanceof Info)) {
      return false;
    }
    Info other = (Info) otherObject;
    if (this == other) {
      return true;
    }
    if (!this.provider.equals(other.provider)) {
      return false;
    }
    // Compare objects' keys and values
    if (!this.getKeys().equals(other.getKeys())) {
      return false;
    }
    for (String key : getKeys()) {
      if (!this.getValue(key).equals(other.getValue(key))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    List<String> keys = new ArrayList<>(getKeys());
    Collections.sort(keys);
    List<Object> objectsToHash = new ArrayList<>();
    objectsToHash.add(provider);
    for (String key : keys) {
      objectsToHash.add(key);
      objectsToHash.add(getValue(key));
    }
    return Objects.hashCode(objectsToHash.toArray());
  }

  /**
   * Convert the object to string using Skylark syntax. The output tries to be reversible (but there
   * is no guarantee, it depends on the actual values).
   */
  @Override
  public void repr(SkylarkPrinter printer) {
    boolean first = true;
    printer.append("struct(");
    // Sort by key to ensure deterministic output.
    for (String key : Ordering.natural().sortedCopy(values.keySet())) {
      if (!first) {
        printer.append(", ");
      }
      first = false;
      printer.append(key);
      printer.append(" = ");
      printer.repr(values.get(key));
    }
    printer.append(")");
  }

  @Override
  public void reprLegacy(SkylarkPrinter printer) {
    boolean first = true;
    printer.append(provider.getPrintableName());
    printer.append("(");
    // Sort by key to ensure deterministic output.
    for (String key : Ordering.natural().sortedCopy(values.keySet())) {
      if (!first) {
        printer.append(", ");
      }
      first = false;
      printer.append(key);
      printer.append(" = ");
      printer.repr(values.get(key));
    }
    printer.append(")");
  }

  @Override
  public String toString() {
    return Printer.repr(this);
  }
}
