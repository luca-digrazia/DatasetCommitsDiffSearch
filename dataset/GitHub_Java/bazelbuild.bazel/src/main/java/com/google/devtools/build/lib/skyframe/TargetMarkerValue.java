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
package com.google.devtools.build.lib.skyframe;

import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

/**
 * Value represents visited target in the Skyframe graph after error checking.
 */
@Immutable
@ThreadSafe
public final class TargetMarkerValue implements SkyValue {

  static final TargetMarkerValue TARGET_MARKER_INSTANCE = new TargetMarkerValue();

  private TargetMarkerValue() {
  }

  @ThreadSafe
  public static SkyKey key(Label label) {
    return new SkyKey(SkyFunctions.TARGET_MARKER, label);
  }
}
