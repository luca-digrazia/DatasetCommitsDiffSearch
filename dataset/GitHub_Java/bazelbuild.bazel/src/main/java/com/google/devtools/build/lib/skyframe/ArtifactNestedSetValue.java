// Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.skyframe;

import com.google.devtools.build.lib.actions.ActionExecutionException;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;
import com.google.devtools.build.skyframe.ValueOrException2;
import java.io.IOException;
import java.util.Map;

/**
 * Represent a "promise" that the Artifacts under a NestedSet is evaluated by Skyframe and the
 * ValueOrException is available in {@link ArtifactNestedSetFunction#artifactToSkyValueMap}
 */
@Immutable
@ThreadSafe
public class ArtifactNestedSetValue implements SkyValue {

  private static ArtifactNestedSetValue singleton = null;

  static ArtifactNestedSetValue createOrGetInstance() {
    if (singleton == null) {
      singleton = new ArtifactNestedSetValue();
    }
    return singleton;
  }

  Map<SkyKey, ValueOrException2<IOException, ActionExecutionException>> getArtifactToSkyValueMap() {
    return ArtifactNestedSetFunction.getInstance().getArtifactSkyKeyToValueOrException();
  }
}
