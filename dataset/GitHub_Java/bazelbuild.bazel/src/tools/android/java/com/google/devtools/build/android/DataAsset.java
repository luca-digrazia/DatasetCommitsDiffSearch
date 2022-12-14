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
package com.google.devtools.build.android;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Represents an Asset created from a binary file.
 */
public interface DataAsset {
  /**
   * Provides the RelativeAssetPath of the DataAsset.
   */
  DataKey dataKey();

  /**
   * Provides the Path to the file from which the DataResource was derived.
   */
  Path source();

  /**
   * Writes the asset to the given asset directory.
   * @param newAssetDirectory The new directory for this asset.
   * @throws IOException if there are issues with writing the asset.
   */
  void write(Path newAssetDirectory) throws IOException;

  /**
   * Compares one data resource to another.
   *
   * Not implementing Comparable as it would conflict with DataResource.
   */
  int compareTo(DataAsset other);
}
