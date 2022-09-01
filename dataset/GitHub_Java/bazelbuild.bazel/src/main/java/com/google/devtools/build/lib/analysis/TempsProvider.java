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

package com.google.devtools.build.lib.analysis;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;

import java.util.Collection;

/**
 * A {@link TransitiveInfoProvider} for rule classes that save extra files when
 * {@code --save_temps} is in effect.
 */
@Immutable
public final class TempsProvider implements TransitiveInfoProvider {

  private final ImmutableList<Artifact> temps;

  public TempsProvider(ImmutableList<Artifact> temps) {
    this.temps = temps;
  }

  /**
   * Return the extra artifacts to save when {@code --save_temps} is in effect.
   */
  public Collection<Artifact> getTemps() {
    return temps;
  }
}
