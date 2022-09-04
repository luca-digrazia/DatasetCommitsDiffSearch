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

package com.google.devtools.build.lib.skyframe;

import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/** The Skyframe function that generates values for variables of the client environment. */
public final class ClientEnvironmentFunction implements SkyFunction {

  private final AtomicReference<Map<String, String>> clientEnv;

  ClientEnvironmentFunction(AtomicReference<Map<String, String>> clientEnv) {
    this.clientEnv = clientEnv;
  }

  @Nullable
  @Override
  public String extractTag(SkyKey skyKey) {
    return null;
  }

  @Nullable
  @Override
  public SkyValue compute(SkyKey key, Environment env) throws InterruptedException {
    return new ClientEnvironmentValue(clientEnv.get().get((String) key.argument()));
  }
}
