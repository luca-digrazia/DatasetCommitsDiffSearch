// Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.buildeventstream;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.buildeventstream.BuildEvent.LocalFile;
import com.google.devtools.build.lib.buildeventstream.PathConverter.FileUriPathConverter;
import com.google.devtools.build.lib.vfs.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** An uploader that simply turns paths into local file URIs. */
public class LocalFilesArtifactUploader implements BuildEventArtifactUploader {
  private static final FileUriPathConverter FILE_URI_PATH_CONVERTER = new FileUriPathConverter();
  private final ConcurrentHashMap<Path, Boolean> fileIsDirectory = new ConcurrentHashMap<>();

  @Override
  public ListenableFuture<PathConverter> upload(Map<Path, LocalFile> files) {
    return Futures.immediateFuture(new PathConverterImpl(files.keySet()));
  }

  @Override
  public void shutdown() {
    // Intentionally left empty
  }

  private class PathConverterImpl implements PathConverter {
    private final Set<Path> paths;

    private PathConverterImpl(Set<Path> paths) {
      this.paths = paths;
    }

    @Nullable
    @Override
    public String apply(Path path) {
      if (!paths.contains(path)) {
        // We should throw here, the file wasn't declared in BuildEvent#referencedLocalFiles
        return null;
      }
      if (fileIsDirectory.computeIfAbsent(path, Path::isDirectory)) {
        return null;
      }
      return FILE_URI_PATH_CONVERTER.apply(path);
    }
  }
}
