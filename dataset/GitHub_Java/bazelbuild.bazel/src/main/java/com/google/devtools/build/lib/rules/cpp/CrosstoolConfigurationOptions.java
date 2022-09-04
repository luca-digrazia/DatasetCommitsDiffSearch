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

package com.google.devtools.build.lib.rules.cpp;

import javax.annotation.Nullable;

/**
 * A container object which provides crosstool configuration options to the build.
 */
public interface CrosstoolConfigurationOptions {
  /** Returns the CPU associated with this crosstool configuration. */
  String getCpu();

  /** Returns the compiler associated with this crosstool configuration. */
  @Nullable
  String getCompiler();

  /** Returns the libc version associated with this crosstool configuration. */
  @Nullable
  String getLibc();
}
