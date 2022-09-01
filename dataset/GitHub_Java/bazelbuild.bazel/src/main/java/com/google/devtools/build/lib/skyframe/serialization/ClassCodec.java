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

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;

/** Codec for {@link Class}. */
class ClassCodec implements ObjectCodec<Class<?>> {
  @SuppressWarnings("unchecked")
  @Override
  public Class<Class<?>> getEncodedClass() {
    return (Class<Class<?>>) (Object) Class.class;
  }

  @Override
  public void serialize(SerializationContext context, Class<?> obj, CodedOutputStream codedOut)
      throws SerializationException, IOException {
    context.serialize(obj.getName(), codedOut);
  }

  @Override
  public Class<?> deserialize(DeserializationContext context, CodedInputStream codedIn)
      throws SerializationException, IOException {
    String className = context.deserialize(codedIn);
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new SerializationException("Couldn't find class for " + className, e);
    }
  }
}
