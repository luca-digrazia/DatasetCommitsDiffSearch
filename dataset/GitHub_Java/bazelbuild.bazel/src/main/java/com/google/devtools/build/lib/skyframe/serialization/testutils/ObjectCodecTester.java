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

package com.google.devtools.build.lib.skyframe.serialization.testutils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.skyframe.serialization.ObjectCodec;
import com.google.devtools.build.lib.skyframe.serialization.SerializationException;
import com.google.protobuf.CodedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Utility for testing {@link ObjectCodec} instances. */
public class ObjectCodecTester<T> {

  /** Interface for testing successful deserialization of an object. */
  @FunctionalInterface
  public interface VerificationFunction<T> {
    /**
     * Verify whether or not the original object was sufficiently serialized/deserialized. Typically
     * this will be some sort of assertion.
     *
     * @throws Exception on verification failure
     */
    void verifyDeserialized(T original, T deserialized) throws Exception;
  }

  /**
   * Create an {@link ObjectCodecTester.Builder} for the supplied instance. See
   * {@link ObjectCodecTester.Builder} for details.
   */
  public static <T> ObjectCodecTester.Builder<T> newBuilder(ObjectCodec<T> toTest) {
    return new ObjectCodecTester.Builder<>(toTest);
  }

  private final ObjectCodec<T> underTest;
  private final ImmutableList<T> subjects;
  private final boolean skipBadDataTest;
  private final VerificationFunction<T> verificationFunction;

  private ObjectCodecTester(
      ObjectCodec<T> underTest,
      ImmutableList<T> subjects,
      boolean skipBadDataTest,
      VerificationFunction<T> verificationFunction) {
    this.underTest = underTest;
    Preconditions.checkState(!subjects.isEmpty(), "No subjects provided");
    this.subjects = subjects;
    this.skipBadDataTest = skipBadDataTest;
    this.verificationFunction = verificationFunction;
  }

  private void runTests() throws Exception {
    testSerializeDeserialize();
    testStableSerialization();
    if (!skipBadDataTest) {
      testDeserializeJunkData();
    }
  }

  /** Runs serialization/deserialization tests. */
  void testSerializeDeserialize() throws Exception {
    for (T subject : subjects) {
      byte[] serialized = toBytes(subject);
      T deserialized = fromBytes(serialized);
      verificationFunction.verifyDeserialized(subject, deserialized);
    }
  }

  /** Runs serialized bytes stability tests. */
  void testStableSerialization() throws Exception {
    for (T subject : subjects) {
      byte[] serialized = toBytes(subject);
      T deserialized = fromBytes(serialized);
      byte[] reserialized = toBytes(deserialized);
      assertThat(reserialized).isEqualTo(serialized);
    }
  }

  /** Runs junk-data recognition tests. */
  void testDeserializeJunkData() {
    try {
      underTest.deserialize(CodedInputStream.newInstance("junk".getBytes(StandardCharsets.UTF_8)));
      fail("Expected exception");
    } catch (SerializationException | IOException e) {
      // Expected.
    }
  }

  private T fromBytes(byte[] bytes) throws SerializationException, IOException {
    return TestUtils.fromBytes(underTest, bytes);
  }

  private byte[] toBytes(T subject) throws IOException, SerializationException {
    return TestUtils.toBytes(underTest, subject);
  }

  /** Builder for {@link ObjectCodecTester}. */
  public static class Builder<T> {
    private final ObjectCodec<T> underTest;
    private final ImmutableList.Builder<T> subjectsBuilder = ImmutableList.builder();
    private boolean skipBadDataTest = false;
    private VerificationFunction<T> verificationFunction =
        (original, deserialized) -> assertThat(deserialized).isEqualTo(original);

    private Builder(ObjectCodec<T> underTest) {
      this.underTest = underTest;
    }

    /** Add subjects to be tested for serialization/deserialization. */
    @SafeVarargs
    public final Builder<T> addSubjects(@SuppressWarnings("unchecked") T... subjects) {
      return addSubjects(ImmutableList.copyOf(subjects));
    }

    /** Add subjects to be tested for serialization/deserialization. */
    public Builder<T> addSubjects(ImmutableList<T> subjects) {
      subjectsBuilder.addAll(subjects);
      return this;
    }

    /**
     * Skip tests that check for the ability to detect bad data. This may be useful for simpler
     * codecs which don't do any error verification.
     */
    public Builder<T> skipBadDataTest() {
      this.skipBadDataTest = true;
      return this;
    }

    /**
     * Sets {@link ObjectCodecTester.VerificationFunction} for verifying deserialization. Default
     * is simple equality assertion, a custom version may be provided for more, or less, detailed
     * checks.
     */
    public Builder<T> verificationFunction(VerificationFunction<T> verificationFunction) {
      this.verificationFunction = Preconditions.checkNotNull(verificationFunction);
      return this;
    }

    /** Captures the state of this builder and run all associated tests. */
    public void buildAndRunTests() throws Exception {
      build().runTests();
    }

    /**
     * Creates a new {@link ObjectCodecTester} from this builder. Exposed to allow running tests
     * individually.
     */
    ObjectCodecTester<T> build() {
      return new ObjectCodecTester<>(
          underTest,
          subjectsBuilder.build(),
          skipBadDataTest,
          verificationFunction);
    }
  }
}
