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
package com.google.devtools.build.lib.rules.android;

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;

/**
 * Provider of transitively available ZIPs of native libs that should be directly copied into the
 * APK.
 */
@Immutable
public final class NativeLibsZipsProvider implements TransitiveInfoProvider {
  private final NestedSet<Artifact> aarNativeLibs;

  public NativeLibsZipsProvider(NestedSet<Artifact> aarNativeLibs) {
    this.aarNativeLibs = aarNativeLibs;
  }

  public NestedSet<Artifact> getAarNativeLibs() {
    return aarNativeLibs;
  }
}
