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
package com.google.devtools.build.remote.worker;

import build.bazel.remote.execution.v2.Digest;
import com.google.devtools.build.lib.remote.SimpleBlobStoreActionCache;
import com.google.devtools.build.lib.remote.disk.OnDiskBlobStore;
import com.google.devtools.build.lib.remote.options.RemoteOptions;
import com.google.devtools.build.lib.remote.util.DigestUtil;
import com.google.devtools.build.lib.vfs.Path;

/** A {@link SimpleBlobStoreActionCache} backed by an {@link OnDiskBlobStore}. */
class OnDiskBlobStoreActionCache extends SimpleBlobStoreActionCache {

  public OnDiskBlobStoreActionCache(RemoteOptions options, Path cacheDir, DigestUtil digestUtil) {
    super(options, new OnDiskBlobStore(cacheDir), digestUtil);
  }

  public boolean containsKey(Digest digest) {
    return ((OnDiskBlobStore) blobStore).contains(digest.getHash());
  }
}
