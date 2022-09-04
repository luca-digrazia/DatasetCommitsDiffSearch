// Copyright 2017 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.skyframe.serialization;

import com.google.devtools.build.lib.analysis.BlazeDirectories;
import com.google.devtools.build.lib.analysis.BlazeDirectoriesCodec;
import com.google.devtools.build.lib.analysis.ServerDirectories;

/** Tests for {@link BlazeDirectoriesCodec}. */
public class BlazeDirectoriesCodecTest extends AbstractObjectCodecTest<BlazeDirectories> {
  public BlazeDirectoriesCodecTest() {
    super(
        new BlazeDirectoriesCodec(new PathCodec(FsUtils.TEST_FILESYSTEM)),
        new BlazeDirectories(
            new ServerDirectories(
                FsUtils.TEST_FILESYSTEM.getPath("install_base"),
                FsUtils.TEST_FILESYSTEM.getPath("output_base")),
            FsUtils.TEST_FILESYSTEM.getPath("workspace"),
            "Blaze"),
        new BlazeDirectories(
            new ServerDirectories(
                FsUtils.TEST_FILESYSTEM.getPath("install_base"),
                FsUtils.TEST_FILESYSTEM.getPath("output_base"),
                "ab"),
            FsUtils.TEST_FILESYSTEM.getPath("workspace"),
            "Blaze"),
        new BlazeDirectories(
            new ServerDirectories(
                FsUtils.TEST_FILESYSTEM.getPath("install_base"),
                FsUtils.TEST_FILESYSTEM.getPath("output_base")),
            FsUtils.TEST_FILESYSTEM.getPath("workspace"),
            "Bazel"));
  }
}
