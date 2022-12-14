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

/**
 * Some static constants for deciding serialization behavior.
 */
public class SerializationConstants {

  /**
   * If true, we attempt to to serialize ConfiguredTargetValue in testing.
   */
  public static final boolean VALIDATE_CONFIGURED_TARGET_VALUE =
      System.getenv("DONT_VALIDATE_CONFIGURED_TARGET_VALUE") == null;

  private static final boolean IN_TEST = System.getenv("TEST_TMPDIR") != null;
  private static final boolean CHECK_SERIALIZATION =
      System.getenv("DONT_SANITY_CHECK_SERIALIZATION") == null;

  private static final boolean TEST_NESTED_SET_SERIALIZATION =
      System.getenv("TEST_NESTED_SET_SERIALIZATION") != null;

   /**
   * If true, serialization should include NestedSet. Non-final so tests can opt-in to NestedSet
   * serialization.
   */
  public static boolean shouldSerializeNestedSet = TEST_NESTED_SET_SERIALIZATION || !IN_TEST;

  /**
   * Returns true if serialization should be validated on all Skyframe writes.
   */
  public static boolean shouldCheckSerializationBecauseInTest() {
    return IN_TEST && CHECK_SERIALIZATION;
  }
}
