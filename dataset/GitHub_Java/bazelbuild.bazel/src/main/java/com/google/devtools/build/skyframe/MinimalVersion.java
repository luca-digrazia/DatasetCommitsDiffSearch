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
package com.google.devtools.build.skyframe;

/**
 * A Version "less than" all other versions, other than itself. Only used as initializer in node
 * entry.
 */
final class MinimalVersion implements Version {
  static final MinimalVersion INSTANCE = new MinimalVersion();

  private MinimalVersion() {}

  @Override
  public boolean atMost(Version other) {
    return true;
  }
}
