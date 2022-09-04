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

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import java.util.Collection;

/**
 * An Android rule that exposes this enables <a
 * href="https://developer.android.com/topic/libraries/data-binding/index.html">data binding</a> on
 * its resource processing and Java compilation.
 */
public final class UsesDataBindingProvider implements TransitiveInfoProvider {
  private final ImmutableList<Artifact> metadataOutputs;

  public UsesDataBindingProvider(Collection<Artifact> metadataOutputs) {
    this.metadataOutputs = ImmutableList.copyOf(metadataOutputs);
  }

  /**
   * Returns the metadata outputs from this rule's annotation processing that describe how it
   * applies data binding. See {@link DataBinding#getMetadataOutputs} for details.
   */
  public ImmutableList<Artifact> getMetadataOutputs() {
    return metadataOutputs;
  }
}
