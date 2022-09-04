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
package com.google.devtools.build.android.resources;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.InstructionAdapter;

/**
 * Represents a field and its initializer (where initialization is either part of the field
 * definition, or done via code in the static clinit function).
 */
public interface FieldInitializer {
  /**
   * Write the bytecode for the field definition.
   *
   * @return true if the initializer is deferred to clinit code.
   */
  boolean writeFieldDefinition(ClassWriter cw, int accessLevel, boolean isFinal);

  /**
   * Write the bytecode for the clinit portion of initializer.
   *
   * @return the number of stack slots needed for the code.
   */
  int writeCLInit(InstructionAdapter insts, String className);

  /**
   * Write the source code for the initializer to the given writer.
   * Unlike {@link #writeFieldDefinition}, this assumes non-final fields, since we don't use this
   * for final fields yet.
   */
  void writeInitSource(Writer writer) throws IOException;

  /** Tests if the field's name is in the provided set. */
  boolean nameIsIn(Set<String> fieldNames);
}
