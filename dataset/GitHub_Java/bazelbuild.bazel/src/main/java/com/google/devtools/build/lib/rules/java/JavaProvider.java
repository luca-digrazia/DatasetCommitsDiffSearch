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
package com.google.devtools.build.lib.rules.java;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.SkylarkClassObject;
import com.google.devtools.build.lib.packages.SkylarkClassObjectConstructor;

/** A Skylark declared provider that encapsulates all providers that are needed by Java rules. */
@Immutable
public final class JavaProvider extends SkylarkClassObject implements TransitiveInfoProvider {

  public static final SkylarkClassObjectConstructor JAVA_PROVIDER =
      SkylarkClassObjectConstructor.createNative("java_common.provider");

  private final JavaCompilationArgsProvider javaCompilationArgsProvider;

  public JavaProvider(JavaCompilationArgsProvider javaCompilationArgsProvider) {
    super(JAVA_PROVIDER, ImmutableMap.<String, Object>of());
    this.javaCompilationArgsProvider = javaCompilationArgsProvider;
  }

  public JavaCompilationArgsProvider getJavaCompilationArgsProvider() {
    return javaCompilationArgsProvider;
  }
}
