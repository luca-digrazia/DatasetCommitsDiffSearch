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
package com.google.devtools.build.lib.syntax;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * An annotation to mark built-in keyword argument methods accessible from Skylark.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkylarkBuiltin {

  String name();

  String doc();

  Param[] mandatoryParams() default {};

  Param[] optionalParams() default {};

  boolean documented() default true;

  Class<?> objectType() default Object.class;

  Class<?> returnType() default Object.class;

  boolean onlyLoadingPhase() default false;

  /**
   * An annotation for parameters of Skylark built-in functions.
   */
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Param {

    String name();

    String doc();

    Class<?> type() default Object.class;

    Class<?> generic1() default Object.class;

    boolean callbackEnabled() default false;
  }
}
