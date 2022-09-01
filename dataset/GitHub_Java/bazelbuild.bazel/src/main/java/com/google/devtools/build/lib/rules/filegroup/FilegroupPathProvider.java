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

package com.google.devtools.build.lib.rules.filegroup;

import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.skyframe.serialization.ObjectCodec;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.vfs.PathFragment;

/** A transitive info provider for dependent targets to query {@code path} attributes. */
@Immutable
@AutoCodec
public final class FilegroupPathProvider implements TransitiveInfoProvider {
  public static final ObjectCodec<FilegroupPathProvider> CODEC =
      new FilegroupPathProvider_AutoCodec();

  private final PathFragment pathFragment;

  @AutoCodec.Instantiator
  public FilegroupPathProvider(PathFragment pathFragment) {
    this.pathFragment = pathFragment;
  }

  /**
   * Returns the value of the {@code path} attribute or the empty fragment if it is not present.
   */
  public PathFragment getFilegroupPath() {
    return pathFragment;
  }
}
