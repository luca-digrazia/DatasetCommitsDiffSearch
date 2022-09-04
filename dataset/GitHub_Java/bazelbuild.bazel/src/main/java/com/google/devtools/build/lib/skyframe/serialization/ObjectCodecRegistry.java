// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.skyframe.serialization;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.skyframe.serialization.Memoizer.MemoizingCodec;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Registry class for handling {@link ObjectCodec} mappings. Codecs are indexed by {@link String}
 * classifiers and assigned deterministic numeric identifiers for more compact on-the-wire
 * representation if desired.
 */
public class ObjectCodecRegistry {

  static Builder newBuilder() {
    return new Builder();
  }

  private final ImmutableMap<Class<?>, CodecDescriptor> classMappedCodecs;
  private final ImmutableList<CodecDescriptor> tagMappedCodecs;
  @Nullable
  private final CodecDescriptor defaultCodecDescriptor;

  private final int memoizingCodecsStartTag;
  private final ImmutableMap<Class<?>, MemoizingCodecDescriptor<?>> classMappedMemoizingCodecs;
  private final ImmutableList<MemoizingCodecDescriptor<?>> tagMappedMemoizingCodecs;

  private final IdentityHashMap<Object, Integer> constantsMap;
  private final ImmutableList<Object> constants;
  private final int constantsStartTag;

  private ObjectCodecRegistry(
      Map<Class<?>, CodecHolder> codecs,
      ImmutableSet<MemoizingCodec<?>> memoizingCodecs,
      ImmutableList<Object> constants,
      boolean allowDefaultCodec) {
    ImmutableMap.Builder<Class<?>, CodecDescriptor> codecMappingsBuilder = ImmutableMap.builder();
    int nextTag = 1; // 0 is reserved for null.
    for (Class<?> clazz :
        ImmutableList.sortedCopyOf(Comparator.comparing(Class::getName), codecs.keySet())) {
      codecMappingsBuilder.put(clazz, codecs.get(clazz).createDescriptor(nextTag));
      nextTag++;
    }
    this.classMappedCodecs = codecMappingsBuilder.build();

    this.defaultCodecDescriptor =
        allowDefaultCodec
            ? new TypedCodecDescriptor<>(nextTag++, new JavaSerializableCodec())
            : null;
    this.tagMappedCodecs = makeTagMappedCodecs(classMappedCodecs.values(), defaultCodecDescriptor);

    this.memoizingCodecsStartTag = nextTag;
    ImmutableMap.Builder<Class<?>, MemoizingCodecDescriptor<?>> memoizingCodecsBuilder =
        ImmutableMap.builderWithExpectedSize(memoizingCodecs.size());
    ImmutableList.Builder<MemoizingCodecDescriptor<?>> tagMappedMemoizingCodecsBuilder =
        ImmutableList.builderWithExpectedSize(memoizingCodecs.size());
    nextTag =
        processMemoizingCodecs(
            memoizingCodecs, nextTag, tagMappedMemoizingCodecsBuilder, memoizingCodecsBuilder);
    this.classMappedMemoizingCodecs = memoizingCodecsBuilder.build();
    this.tagMappedMemoizingCodecs = tagMappedMemoizingCodecsBuilder.build();

    constantsStartTag = nextTag;
    constantsMap = new IdentityHashMap<>();
    for (Object constant : constants) {
      constantsMap.put(constant, nextTag++);
    }
    this.constants = constants;
  }

  /**
   * Returns a {@link CodecDescriptor} for the given type.
   *
   * <p>Falls back to a codec for the nearest super type of type. Failing that, may fall back to the
   * registry's default codec.
   */
  public CodecDescriptor getCodecDescriptor(Class<?> type)
      throws SerializationException.NoCodecException {
    // TODO(blaze-team): consider caching this traversal.
    for (Class<?> nextType = type; nextType != null; nextType = nextType.getSuperclass()) {
      CodecDescriptor result = classMappedCodecs.get(nextType);
      if (result != null) {
        return result;
      }
    }
    if (defaultCodecDescriptor == null) {
      throw new SerializationException.NoCodecException(
          "No codec available for " + type + " and default fallback disabled");
    }
    return defaultCodecDescriptor;
  }

  @Nullable
  public <T> MemoizingCodecDescriptor<? super T> getMemoizingCodecDescriptor(Class<T> type) {
    for (Class<?> nextType = type; nextType != null; nextType = nextType.getSuperclass()) {
      MemoizingCodecDescriptor<?> result = classMappedMemoizingCodecs.get(nextType);
      if (result != null) {
        @SuppressWarnings("unchecked")
        MemoizingCodecDescriptor<? super T> castResult =
            (MemoizingCodecDescriptor<? super T>) result;
        return castResult;
      }
    }
    return null;
  }

  @Nullable
  Object maybeGetConstantByTag(int tag) {
    return tag < constantsStartTag || tag - constantsStartTag >= constants.size()
        ? null
        : constants.get(tag - constantsStartTag);
  }

  @Nullable
  Integer maybeGetTagForConstant(Object object) {
    return constantsMap.get(object);
  }

  /** Returns the {@link CodecDescriptor} associated with the supplied tag. */
  public CodecDescriptor getCodecDescriptorByTag(int tag)
      throws SerializationException.NoCodecException {
    int tagOffset = tag - 1;
    if (tagOffset < 0 || tagOffset > tagMappedCodecs.size()) {
      throw new SerializationException.NoCodecException("No codec available for tag " + tag);
    }

    CodecDescriptor result = tagMappedCodecs.get(tagOffset);
    if (result != null) {
      return result;
    } else {
      throw new SerializationException.NoCodecException("No codec available for tag " + tag);
    }
  }

  @Nullable
  public MemoizingCodec<?> maybeGetMemoizingCodecByTag(int tag) {
    int tagOffset = tag - memoizingCodecsStartTag;
    if (tagOffset < 0 || tagOffset > tagMappedMemoizingCodecs.size()) {
      return null;
    }
    return tagMappedMemoizingCodecs.get(tagOffset).getMemoizingCodec();
  }

  /**
   * Creates a builder using the current contents of this registry.
   *
   * <p>This is much more efficient than scanning multiple times.
   */
  @VisibleForTesting
  public Builder getBuilder() {
    Builder builder = newBuilder();
    builder.setAllowDefaultCodec(defaultCodecDescriptor != null);
    for (Map.Entry<Class<?>, CodecDescriptor> entry : classMappedCodecs.entrySet()) {
      // Cast here is safe because the original #add in the Builder was type-checked.
      builder.add(entry.getKey(), getObjectCodec(entry.getValue()));
    }

    for (MemoizingCodecDescriptor<?> descriptor : tagMappedMemoizingCodecs) {
      builder.addMemoizing(descriptor.getMemoizingCodec());
    }

    for (Object constant : constants) {
      builder.addConstant(constant);
    }
    return builder;
  }

  @SuppressWarnings("unchecked")
  private static ObjectCodec<Object> getObjectCodec(CodecDescriptor descriptor) {
    return (ObjectCodec<Object>) descriptor.getCodec();
  }

  /** Describes encoding logic. */
  interface CodecDescriptor {
    void serialize(SerializationContext context, Object obj, CodedOutputStream codedOut)
        throws IOException, SerializationException;

    Object deserialize(DeserializationContext context, CodedInputStream codedIn)
        throws IOException, SerializationException;

    /**
     * Unique identifier identifying the associated codec.
     *
     * <p>Intended to be used as a compact on-the-wire representation of an encoded object's type.
     *
     * <p>Returns a value ≥ 1.
     *
     * <p>0 is a special tag representing null while negative numbers are reserved for
     * backreferences.
     */
    int getTag();

    /** Returns the underlying codec. */
    ObjectCodec<?> getCodec();
  }

  static class MemoizingCodecDescriptor<T> {
    private final int tag;
    private final MemoizingCodec<T> memoizingCodec;

    MemoizingCodecDescriptor(int tag, MemoizingCodec<T> memoizingCodec) {
      this.tag = tag;
      this.memoizingCodec = memoizingCodec;
    }

    int getTag() {
      return tag;
    }

    MemoizingCodec<T> getMemoizingCodec() {
      return memoizingCodec;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("tag", tag)
          .add("codec", memoizingCodec)
          .toString();
    }
  }

  private static class TypedCodecDescriptor<T> implements CodecDescriptor {
    private final int tag;
    private final ObjectCodec<T> codec;

    private TypedCodecDescriptor(int tag, ObjectCodec<T> codec) {
      this.tag = tag;
      this.codec = codec;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(SerializationContext context, Object obj, CodedOutputStream codedOut)
        throws IOException, SerializationException {
      codec.serialize(context, (T) obj, codedOut);
    }

    @Override
    public T deserialize(DeserializationContext context, CodedInputStream codedIn)
        throws IOException, SerializationException {
      return codec.deserialize(context, codedIn);
    }

    @Override
    public int getTag() {
      return tag;
    }

    @Override
    public ObjectCodec<T> getCodec() {
      return codec;
    }
  }

  private interface CodecHolder {
    CodecDescriptor createDescriptor(int tag);
  }

  private static class TypedCodecHolder<T> implements CodecHolder {
    private final ObjectCodec<T> codec;

    private TypedCodecHolder(ObjectCodec<T> codec) {
      this.codec = codec;
    }

    @Override
    public CodecDescriptor createDescriptor(int tag) {
      return new TypedCodecDescriptor<T>(tag, codec);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("codec", codec).toString();
    }
  }

  /** Builder for {@link ObjectCodecRegistry}. */
  public static class Builder {
    private final ImmutableMap.Builder<Class<?>, CodecHolder> codecsBuilder =
        ImmutableMap.builder();
    private final ImmutableSet.Builder<MemoizingCodec<?>> memoizingCodecBuilder =
        ImmutableSet.builder();
    private final ImmutableList.Builder<Object> constantsBuilder = ImmutableList.builder();
    private boolean allowDefaultCodec = true;

    public <T> Builder add(Class<? extends T> type, ObjectCodec<T> codec) {
      codecsBuilder.put(type, new TypedCodecHolder<>(codec));
      return this;
    }

    public Builder addMemoizing(MemoizingCodec<?> memoizingCodec) {
      memoizingCodecBuilder.add(memoizingCodec);
      return this;
    }

    /**
     * Set whether or not we allow fallback to java serialization when no matching codec is found.
     */
    public Builder setAllowDefaultCodec(boolean allowDefaultCodec) {
      this.allowDefaultCodec = allowDefaultCodec;
      return this;
    }

    public Builder addConstant(Object object) {
      constantsBuilder.add(object);
      return this;
    }

    public ObjectCodecRegistry build() {
      return new ObjectCodecRegistry(
          codecsBuilder.build(),
          memoizingCodecBuilder.build(),
          constantsBuilder.build(),
          allowDefaultCodec);
    }
  }

  private static ImmutableList<CodecDescriptor> makeTagMappedCodecs(
      Collection<CodecDescriptor> codecs, @Nullable CodecDescriptor defaultCodecDescriptor) {
    CodecDescriptor[] codecTable =
        new CodecDescriptor[codecs.size() + (defaultCodecDescriptor != null ? 1 : 0)];
    for (CodecDescriptor codecDescriptor : codecs) {
      codecTable[codecDescriptor.getTag() - 1] = codecDescriptor;
    }

    if (defaultCodecDescriptor != null) {
      codecTable[defaultCodecDescriptor.getTag() - 1] = defaultCodecDescriptor;
    }
    return ImmutableList.copyOf(codecTable);
  }

  private static int processMemoizingCodecs(
      Iterable<? extends MemoizingCodec<?>> memoizingCodecs,
      int nextTag,
      ImmutableList.Builder<MemoizingCodecDescriptor<?>> tagMappedMemoizingCodecsBuilder,
      ImmutableMap.Builder<Class<?>, MemoizingCodecDescriptor<?>> memoizingCodecsBuilder) {
    for (MemoizingCodec<?> memoizingCodec :
        ImmutableList.sortedCopyOf(
            Comparator.comparing(o -> o.getEncodedClass().getName()), memoizingCodecs)) {
      MemoizingCodecDescriptor<?> codecDescriptor =
          new MemoizingCodecDescriptor<>(nextTag++, memoizingCodec);
      tagMappedMemoizingCodecsBuilder.add(codecDescriptor);
      memoizingCodecsBuilder.put(memoizingCodec.getEncodedClass(), codecDescriptor);
      for (Class<?> otherClass : memoizingCodec.additionalEncodedSubclasses()) {
        memoizingCodecsBuilder.put(otherClass, codecDescriptor);
      }
    }
    return nextTag;
  }
}
