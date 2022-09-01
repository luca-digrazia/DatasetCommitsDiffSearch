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

package com.google.devtools.build.lib.packages.util;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.util.ResourceFileLoader;
import java.io.IOException;

/**
 * Mocks out Android dependencies for testing.
 */
public class BazelMockAndroidSupport {

  public static void setupNdk(MockToolsConfig config) throws IOException {
    new Crosstool(config, "android/crosstool")
        .setCrosstoolFile(
            /*version=*/ "mock_version",
            ResourceFileLoader.loadResource(
                BazelMockAndroidSupport.class, "MOCK_ANDROID_CROSSTOOL"))
        .setSupportedArchs(ImmutableList.of("x86", "armeabi-v7a"))
        .setSupportsHeaderParsing(false)
        .write();
  }
}
