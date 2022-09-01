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

import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.packages.NoSuchThingException;

/**
 * Exception indicating that the required configured target is not in the
 * analysis cache.
 */
public class NoSuchConfiguredTargetException extends NoSuchThingException {
  public NoSuchConfiguredTargetException(Label label, BuildConfiguration configuration) {
    super("not in cache: " + label + " " + configuration);
  }
}
