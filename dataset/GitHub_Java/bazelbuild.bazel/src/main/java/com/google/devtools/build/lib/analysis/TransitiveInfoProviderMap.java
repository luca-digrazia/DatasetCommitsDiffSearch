// Copyright 2014 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** Provides a mapping between a TransitiveInfoProvider class and an instance. */
@Immutable
public interface TransitiveInfoProviderMap {

  /** Returns the instance for the provided providerClass, or <tt>null</tt> if not present. */
  @Nullable
  <P extends TransitiveInfoProvider> P getProvider(Class<P> providerClass);

  ImmutableSet<Map.Entry<Class<? extends TransitiveInfoProvider>, TransitiveInfoProvider>>
      entrySet();

  ImmutableCollection<TransitiveInfoProvider> values();

  TransitiveInfoProviderMapBuilder toBuilder();
}
