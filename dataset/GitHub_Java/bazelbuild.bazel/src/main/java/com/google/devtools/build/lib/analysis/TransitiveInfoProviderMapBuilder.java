// Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.util.Preconditions;
import java.util.Arrays;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;

/** A builder for {@link TransitiveInfoProviderMap}. */
public class TransitiveInfoProviderMapBuilder {

  // TODO(arielb): share the instance with the outerclass and copy on write instead?
  private final LinkedHashMap<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider>
      providers = new LinkedHashMap();

  /**
   * Returns <tt>true</tt> if a {@link TransitiveInfoProvider} has been added for the class
   * provided.
   */
  public boolean contains(Class<? extends TransitiveInfoProvider> providerClass) {
    return providers.containsKey(providerClass);
  }

  public <T extends TransitiveInfoProvider> TransitiveInfoProviderMapBuilder put(
      Class<? extends T> providerClass, T provider) {
    Preconditions.checkNotNull(providerClass);
    Preconditions.checkNotNull(provider);
    // TODO(arielb): throw an exception if the providerClass is already present?
    // This is enforced by aspects but RuleConfiguredTarget presents violations
    // particularly around LicensesProvider
    providers.put(providerClass, provider);
    return this;
  }

  public TransitiveInfoProviderMapBuilder add(TransitiveInfoProvider provider) {
    return put(TransitiveInfoProviderEffectiveClassHelper.get(provider), provider);
  }

  public TransitiveInfoProviderMapBuilder add(TransitiveInfoProvider... providers) {
    return addAll(Arrays.asList(providers));
  }

  public TransitiveInfoProviderMapBuilder addAll(TransitiveInfoProviderMap providers) {
    for (int i = 0; i < providers.getProviderCount(); ++i) {
      add(providers.getProviderAt(i));
    }
    return this;
  }

  public TransitiveInfoProviderMapBuilder addAll(Iterable<TransitiveInfoProvider> providers) {
    for (TransitiveInfoProvider provider : providers) {
      add(provider);
    }
    return this;
  }

  @Nullable
  public <P extends TransitiveInfoProvider> P getProvider(Class<P> providerClass) {
    return (P) providers.get(providerClass);
  }

  public TransitiveInfoProviderMap build() {
    return new TransitiveInfoProviderMapOffsetBased(ImmutableMap.copyOf(providers));
  }
}
