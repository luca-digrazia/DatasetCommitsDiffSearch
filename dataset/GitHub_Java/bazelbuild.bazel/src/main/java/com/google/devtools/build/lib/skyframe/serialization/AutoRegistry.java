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

import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import java.io.IOException;

/**
 * A lazy, automatically populated registry.
 *
 * <p>Must not be accessed by any {@link CodecRegisterer} or {@link ObjectCodec} constructors or
 * static initializers.
 */
public class AutoRegistry {

  private static final Supplier<ObjectCodecRegistry> SUPPLIER =
      Suppliers.memoize(AutoRegistry::create);

  /* Common ancestor of common.google.devtools.build and com.google.devtools.common.options,
   * where Tristate lives. */
  private static final String PACKAGE_PREFIX = "com.google.devtools";

  /** Classes outside {@link AutoRegistry#PACKAGE_PREFIX} that need to be serialized. */
  private static final ImmutableList<String> EXTERNAL_CLASS_NAMES_TO_REGISTER =
      ImmutableList.of("java.io.FileNotFoundException", "java.io.IOException");

  private static final ImmutableList<Object> CONSTANTS_TO_REGISTER =
      ImmutableList.of(
          Predicates.alwaysTrue(),
          Predicates.alwaysFalse(),
          Predicates.isNull(),
          Predicates.notNull());

  public static ObjectCodecRegistry get() {
    return SUPPLIER.get();
  }

  private static ObjectCodecRegistry create() {
    try {
      ObjectCodecRegistry.Builder registry = CodecScanner.initializeCodecRegistry(PACKAGE_PREFIX);
      for (String className : EXTERNAL_CLASS_NAMES_TO_REGISTER) {
        registry.addClassName(className);
      }
      for (Object constant : CONSTANTS_TO_REGISTER) {
        registry.addConstant(constant);
      }
      return registry.build();
    } catch (IOException | ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
