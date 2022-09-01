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
package com.google.devtools.build.lib.testutil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation class which we use to attach a little meta data to test
 * classes. For now, we use this to attach a {@link Suite}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface TestSpec {

  /**
   * The size of the specified test, in terms of its resource consumption and
   * execution time.
   */
  Suite size() default Suite.SMALL_TESTS;

  /**
   * The name of the suite to which this test belongs.  Useful for creating
   * test suites organised by function.
   */
  String suite() default "";

  /**
   * If the test will pass consistently without outside changes.
   * This should be fixed as soon as possible.
   */
  boolean flaky() default false;
}
