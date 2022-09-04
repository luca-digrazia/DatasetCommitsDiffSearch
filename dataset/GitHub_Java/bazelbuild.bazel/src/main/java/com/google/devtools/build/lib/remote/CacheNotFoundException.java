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

package com.google.devtools.build.lib.remote;

import com.google.devtools.remoteexecution.v1test.Digest;
import java.io.IOException;

/**
 * An exception to indicate cache misses.
 * TODO(olaola): have a class of checked RemoteCacheExceptions.
 */
public final class CacheNotFoundException extends IOException {
  private final Digest missingDigest;

  CacheNotFoundException(Digest missingDigest) {
    super("Missing digest: " + missingDigest);
    this.missingDigest = missingDigest;
  }

  public Digest getMissingDigest() {
    return missingDigest;
  }
}
