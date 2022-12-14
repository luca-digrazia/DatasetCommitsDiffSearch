// Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.android;

import com.android.resources.ResourceType;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.devtools.build.android.AndroidFrameworkAttrIdProvider.AttrLookupException;
import com.google.devtools.build.android.resources.FieldInitializer;
import com.google.devtools.build.android.resources.IntArrayFieldInitializer;
import com.google.devtools.build.android.resources.IntFieldInitializer;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Generates {@link FieldInitializer}s placeholder unique ids. The real ids will be assigned when
 * building the android_binary.
 */
class PlaceholderIdFieldInitializerBuilder {
  /**
   * Determine the TT portion of the resource ID (PPTTEEEE) that aapt would have assigned. This not
   * at all alphabetical. It depends on the order in which the types are processed, and whether or
   * not previous types are present (compact). See the code in aapt Resource.cpp:buildResources().
   * There are several seemingly arbitrary and different processing orders in the function, but the
   * ordering is determined specifically by the portion at: <a
   * href="https://android.googlesource.com/platform/frameworks/base.git/+/marshmallow-release/tools/aapt/Resource.cpp#1254">
   * Resource.cpp:buildResources() </a>
   *
   * <p>where it does:
   *
   * <pre>
   *   if (drawables != NULL) { ... }
   *   if (mipmaps != NULL) { ... }
   *   if (layouts != NULL) { ... }
   * </pre>
   *
   * Numbering starts at 1 instead of 0, and ResourceType.ATTR comes before the rest.
   * ResourceType.STYLEABLE doesn't actually need a resource ID, so that is skipped. We encode the
   * ordering in the following list.
   */
  private static final ImmutableList<ResourceType> AAPT_TYPE_ORDERING =
      ImmutableList.of(
          ResourceType.DRAWABLE,
          ResourceType.MIPMAP,
          ResourceType.LAYOUT,
          ResourceType.ANIM,
          ResourceType.ANIMATOR,
          ResourceType.TRANSITION,
          ResourceType.INTERPOLATOR,
          ResourceType.XML,
          ResourceType.RAW,
          // Begin VALUES portion
          // Technically, aapt just assigns according to declaration order in the source value.xml
          // files so it isn't really deterministic. However, the Gradle merger sorts the values.xml
          // file before invoking aapt, so assume that is also done.
          ResourceType.ARRAY,
          ResourceType.BOOL,
          ResourceType.COLOR,
          ResourceType.DIMEN,
          ResourceType.FRACTION,
          ResourceType.ID,
          ResourceType.INTEGER,
          ResourceType.PLURALS,
          ResourceType.STRING,
          ResourceType.STYLE,
          // End VALUES portion
          // Technically, file-based COLOR resources come next. If we care about complete
          // equivalence we should separate the file-based resources from value-based resources so
          // that we can number them the same way.
          ResourceType.MENU);

  private static final int APP_PACKAGE_MASK = 0x7f000000;
  private static final int ATTR_TYPE_ID = 1;
  private static final Logger logger =
      Logger.getLogger(PlaceholderIdFieldInitializerBuilder.class.getName());
  private static final String NORMALIZED_ANDROID_PREFIX = "android_";

  /**
   * Assign any public ids to the given idBuilder.
   *
   * @param nameToId where to store the final name -> id mappings
   * @param publicIds known public resources (can contain null values, if ID isn't reserved)
   * @param typeId the type slot for the current resource type.
   * @return the final set of assigned resource ids (includes those without apriori assignments).
   */
  private static Set<Integer> assignPublicIds(
      Map<String, Integer> nameToId, SortedMap<String, Optional<Integer>> publicIds, int typeId) {
    HashMap<Integer, String> assignedIds = new HashMap<>();
    int prevId = getInitialIdForTypeId(typeId);
    for (Map.Entry<String, Optional<Integer>> entry : publicIds.entrySet()) {
      Optional<Integer> id = entry.getValue();
      if (id.isPresent()) {
        prevId = id.get();
      } else {
        prevId = nextFreeId(prevId + 1, assignedIds.keySet());
      }
      String previousMapping = assignedIds.put(prevId, entry.getKey());
      if (previousMapping != null) {
        logger.warning(
            String.format(
                "Multiple entry names declared for public entry identifier 0x%x (%s and %s)",
                prevId, previousMapping, entry.getKey()));
      }
      nameToId.put(entry.getKey(), prevId);
    }
    return assignedIds.keySet();
  }

  private static int extractTypeId(int fullID) {
    return (fullID & 0x00FF0000) >> 16;
  }

  private static int getInitialIdForTypeId(int typeId) {
    return APP_PACKAGE_MASK | (typeId << 16);
  }

  private static int nextFreeId(int nextSlot, Set<Integer> reservedSlots) {
    // Linear search for the next free slot. This assumes that reserved <public> ids are rare.
    // Otherwise we should use a NavigableSet or some other smarter data-structure.
    while (reservedSlots.contains(nextSlot)) {
      ++nextSlot;
    }
    return nextSlot;
  }

  private static String normalizeAttrName(String attrName) {
    // In addition to ".", attributes can have ":", e.g., for "android:textColor".
    return normalizeName(attrName).replace(':', '_');
  }

  private static String normalizeName(String resourceName) {
    return resourceName.replace('.', '_');
  }

  private final AndroidFrameworkAttrIdProvider androidIdProvider;

  private final Map<ResourceType, Set<String>> innerClasses = new EnumMap<>(ResourceType.class);

  private final Map<ResourceType, SortedMap<String, Optional<Integer>>> publicIds =
      new EnumMap<>(ResourceType.class);

  private final Map<String, Map<String, Boolean>> styleableAttrs = new HashMap<>();

  public PlaceholderIdFieldInitializerBuilder(AndroidFrameworkAttrIdProvider androidIdProvider) {
    this.androidIdProvider = androidIdProvider;
  }

  public void addPublicResource(ResourceType type, String name, Optional<Integer> value) {
    SortedMap<String, Optional<Integer>> publicMappings = publicIds.get(type);
    if (publicMappings == null) {
      publicMappings = new TreeMap<>();
      publicIds.put(type, publicMappings);
    }
    Optional<Integer> oldValue = publicMappings.put(name, value);
    // AAPT should issue an error, but do a bit of sanity checking here just in case.
    if (oldValue != null && !oldValue.equals(value)) {
      // Enforce a consistent ordering on the warning message.
      Integer lower = oldValue.orNull();
      Integer higher = value.orNull();
      if (Ordering.natural().compare(oldValue.orNull(), value.orNull()) > 0) {
        lower = higher;
        higher = oldValue.orNull();
      }
      logger.warning(
          String.format(
              "resource %s/%s has conflicting public identifiers (0x%x vs 0x%x)",
              type, name, lower, higher));
    }
  }

  public void addSimpleResource(ResourceType type, String name) {
    Set<String> fields = innerClasses.get(type);
    if (fields == null) {
      fields = new HashSet<>();
      innerClasses.put(type, fields);
    }
    fields.add(normalizeName(name));
  }

  public void addStyleableResource(FullyQualifiedName key, Map<FullyQualifiedName, Boolean> attrs) {
    ResourceType type = ResourceType.STYLEABLE;
    // The configuration can play a role in sorting, but that isn't modeled yet.
    String normalizedStyleableName = normalizeName(key.name());
    addSimpleResource(type, normalizedStyleableName);
    // We should have merged styleables, so there should only be one definition per configuration.
    // However, we don't combine across configurations, so there can be a pre-existing definition.
    Map<String, Boolean> normalizedAttrs = styleableAttrs.get(normalizedStyleableName);
    if (normalizedAttrs == null) {
      // We need to maintain the original order of the attrs.
      normalizedAttrs = new LinkedHashMap<>();
      styleableAttrs.put(normalizedStyleableName, normalizedAttrs);
    }
    for (Map.Entry<FullyQualifiedName, Boolean> attrEntry : attrs.entrySet()) {
      String normalizedAttrName = normalizeAttrName(attrEntry.getKey().name());
      normalizedAttrs.put(normalizedAttrName, attrEntry.getValue());
    }
  }

  private Map<String, Integer> assignAttrIds(int attrTypeId) {
    // Attrs are special, since they can be defined within a declare-styleable. Those are sorted
    // after top-level definitions.
    if (!innerClasses.containsKey(ResourceType.ATTR)) {
      return ImmutableMap.of();
    }
    Map<String, Integer> attrToId =
        Maps.newHashMapWithExpectedSize(innerClasses.get(ResourceType.ATTR).size());
    // After assigning public IDs, we count up monotonically, so we don't need to track additional
    // assignedIds to avoid collisions (use an ImmutableSet to ensure we don't add more).
    Set<Integer> assignedIds = ImmutableSet.of();
    if (publicIds.containsKey(ResourceType.ATTR)) {
      assignedIds = assignPublicIds(attrToId, publicIds.get(ResourceType.ATTR), attrTypeId);
    }
    Set<String> inlineAttrs = new HashSet<>();
    Set<String> styleablesWithInlineAttrs = new TreeSet<>();
    for (Map.Entry<String, Map<String, Boolean>> styleableAttrEntry : styleableAttrs.entrySet()) {
      Map<String, Boolean> attrs = styleableAttrEntry.getValue();
      for (Map.Entry<String, Boolean> attrEntry : attrs.entrySet()) {
        if (attrEntry.getValue()) {
          inlineAttrs.add(attrEntry.getKey());
          styleablesWithInlineAttrs.add(styleableAttrEntry.getKey());
        }
      }
    }
    int nextId = nextFreeId(getInitialIdForTypeId(attrTypeId), assignedIds);
    // Technically, aapt assigns based on declaration order, but the merge should have sorted
    // the non-inline attributes, so assigning by sorted order is the same.
    ImmutableList<String> sortedAttrs =
        Ordering.natural().immutableSortedCopy(innerClasses.get(ResourceType.ATTR));
    for (String attr : sortedAttrs) {
      if (!inlineAttrs.contains(attr) && !attrToId.containsKey(attr)) {
        attrToId.put(attr, nextId);
        nextId = nextFreeId(nextId + 1, assignedIds);
      }
    }
    for (String styleable : styleablesWithInlineAttrs) {
      Map<String, Boolean> attrs = styleableAttrs.get(styleable);
      for (Map.Entry<String, Boolean> attrEntry : attrs.entrySet()) {
        if (attrEntry.getValue() && !attrToId.containsKey(attrEntry.getKey())) {
          attrToId.put(attrEntry.getKey(), nextId);
          nextId = nextFreeId(nextId + 1, assignedIds);
        }
      }
    }
    return attrToId;
  }

  private Map<ResourceType, Integer> assignTypeIdsForPublic() {
    Map<ResourceType, Integer> allocatedTypeIds = new EnumMap<>(ResourceType.class);
    if (publicIds.isEmpty()) {
      return allocatedTypeIds;
    }
    // Keep track of the reverse mapping from Int -> Type for validation.
    Map<Integer, ResourceType> assignedIds = new HashMap<>();
    for (Map.Entry<ResourceType, SortedMap<String, Optional<Integer>>> publicTypeEntry :
        publicIds.entrySet()) {
      ResourceType currentType = publicTypeEntry.getKey();
      Integer reservedTypeSlot = null;
      String previousResource = null;
      for (Map.Entry<String, Optional<Integer>> publicEntry :
          publicTypeEntry.getValue().entrySet()) {
        Optional<Integer> reservedId = publicEntry.getValue();
        if (!reservedId.isPresent()) {
          continue;
        }
        Integer typePortion = extractTypeId(reservedId.get());
        if (reservedTypeSlot == null) {
          reservedTypeSlot = typePortion;
          previousResource = publicEntry.getKey();
        } else {
          if (!reservedTypeSlot.equals(typePortion)) {
            logger.warning(
                String.format(
                    "%s has conflicting type codes for its public identifiers (%s=%s vs %s=%s)",
                    currentType.getName(),
                    previousResource,
                    reservedTypeSlot,
                    publicEntry.getKey(),
                    typePortion));
          }
        }
      }
      if (currentType == ResourceType.ATTR
          && reservedTypeSlot != null
          && !reservedTypeSlot.equals(ATTR_TYPE_ID)) {
        logger.warning(
            String.format(
                "Cannot force ATTR to have type code other than 0x%02x (got 0x%02x from %s)",
                ATTR_TYPE_ID, reservedTypeSlot, previousResource));
      }
      allocatedTypeIds.put(currentType, reservedTypeSlot);
      ResourceType alreadyAssigned = assignedIds.put(reservedTypeSlot, currentType);
      if (alreadyAssigned != null) {
        logger.warning(
            String.format(
                "Multiple type names declared for public type identifier 0x%x (%s vs %s)",
                reservedTypeSlot, alreadyAssigned, currentType));
      }
    }
    return allocatedTypeIds;
  }

  public Map<ResourceType, List<FieldInitializer>> build() throws AttrLookupException {
    Map<ResourceType, List<FieldInitializer>> initializers = new EnumMap<>(ResourceType.class);
    Map<ResourceType, Integer> typeIdMap = chooseTypeIds();
    Map<String, Integer> attrAssignments = assignAttrIds(typeIdMap.get(ResourceType.ATTR));
    for (Map.Entry<ResourceType, Set<String>> fieldEntries : innerClasses.entrySet()) {
      ResourceType type = fieldEntries.getKey();
      ImmutableList<String> sortedFields =
          Ordering.natural().immutableSortedCopy(fieldEntries.getValue());
      List<FieldInitializer> fields;
      if (type == ResourceType.STYLEABLE) {
        fields = getStyleableInitializers(attrAssignments, sortedFields);
      } else if (type == ResourceType.ATTR) {
        fields = getAttrInitializers(attrAssignments, sortedFields);
      } else {
        int typeId = typeIdMap.get(type);
        fields = getResourceInitializers(type, typeId, sortedFields);
      }
      // The maximum number of Java fields is 2^16.
      // See the JVM reference "4.11. Limitations of the Java Virtual Machine."
      Preconditions.checkArgument(fields.size() < (1 << 16));
      initializers.put(type, fields);
    }
    return initializers;
  }

  private Map<ResourceType, Integer> chooseTypeIds() {
    // Go through public entries. Those may have forced certain type assignments, so take those
    // into account first.
    Map<ResourceType, Integer> allocatedTypeIds = assignTypeIdsForPublic();
    Set<Integer> reservedTypeSlots = ImmutableSet.copyOf(allocatedTypeIds.values());
    // ATTR always takes up slot #1, even if it isn't present.
    allocatedTypeIds.put(ResourceType.ATTR, ATTR_TYPE_ID);
    // The rest are packed after that.
    int nextTypeId = nextFreeId(ATTR_TYPE_ID + 1, reservedTypeSlots);
    for (ResourceType t : AAPT_TYPE_ORDERING) {
      if (innerClasses.containsKey(t) && !allocatedTypeIds.containsKey(t)) {
        allocatedTypeIds.put(t, nextTypeId);
        nextTypeId = nextFreeId(nextTypeId + 1, reservedTypeSlots);
      }
    }
    // Sanity check that everything has been assigned, except STYLEABLE. There shouldn't be
    // anything of type PUBLIC either (since that isn't a real resource).
    // We will need to update the list if there is a new resource type.
    for (ResourceType t : innerClasses.keySet()) {
      Preconditions.checkState(
          t == ResourceType.STYLEABLE || allocatedTypeIds.containsKey(t),
          "Resource type %s is not allocated a type ID",
          t);
    }
    return allocatedTypeIds;
  }

  private List<FieldInitializer> getAttrInitializers(
      Map<String, Integer> attrAssignments, Collection<String> sortedFields) {
    ImmutableList.Builder<FieldInitializer> initList = ImmutableList.builder();
    for (String field : sortedFields) {
      int attrId = attrAssignments.get(field);
      initList.add(new IntFieldInitializer(field, attrId));
    }
    return initList.build();
  }

  private List<FieldInitializer> getResourceInitializers(
      ResourceType type, int typeId, Collection<String> sortedFields) {
    ImmutableList.Builder<FieldInitializer> initList = ImmutableList.builder();
    Map<String, Integer> publicNameToId = new HashMap<>();
    Set<Integer> assignedIds = ImmutableSet.of();
    if (publicIds.containsKey(type)) {
      assignedIds = assignPublicIds(publicNameToId, publicIds.get(type), typeId);
    }
    int resourceIds = nextFreeId(getInitialIdForTypeId(typeId), assignedIds);
    for (String field : sortedFields) {
      Integer fieldValue = publicNameToId.get(field);
      if (fieldValue == null) {
        fieldValue = resourceIds;
        resourceIds = nextFreeId(resourceIds + 1, assignedIds);
      }
      initList.add(new IntFieldInitializer(field, fieldValue));
    }
    return initList.build();
  }

  private List<FieldInitializer> getStyleableInitializers(
      Map<String, Integer> attrAssignments, Collection<String> styleableFields)
      throws AttrLookupException {
    ImmutableList.Builder<FieldInitializer> initList = ImmutableList.builder();
    for (String field : styleableFields) {
      Set<String> attrs = styleableAttrs.get(field).keySet();
      ImmutableMap.Builder<String, Integer> arrayInitValues = ImmutableMap.builder();
      for (String attr : attrs) {
        Integer attrId = attrAssignments.get(attr);
        if (attrId == null) {
          // It should be a framework resource, otherwise we don't know about the resource.
          if (!attr.startsWith(NORMALIZED_ANDROID_PREFIX)) {
            throw new AttrLookupException("App attribute not found: " + attr);
          }
          String attrWithoutPrefix = attr.substring(NORMALIZED_ANDROID_PREFIX.length());
          attrId = androidIdProvider.getAttrId(attrWithoutPrefix);
        }
        arrayInitValues.put(attr, attrId);
      }
      // The styleable array should be sorted by ID value.
      // Make sure that if we have android: framework attributes, their IDs are listed first.
      ImmutableMap<String, Integer> arrayInitMap =
          arrayInitValues.orderEntriesByValue(Ordering.<Integer>natural()).build();
      initList.add(new IntArrayFieldInitializer(field, arrayInitMap.values()));
      int index = 0;
      for (String attr : arrayInitMap.keySet()) {
        initList.add(new IntFieldInitializer(field + "_" + attr, index));
        ++index;
      }
    }
    return initList.build();
  }
}
