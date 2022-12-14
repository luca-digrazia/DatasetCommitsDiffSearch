// Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.skyframe;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.skyframe.StarlarkImportLookupValue.SkylarkImportLookupKey;
import com.google.devtools.build.lib.skyframe.serialization.testutils.FsUtils;
import com.google.devtools.build.lib.skyframe.serialization.testutils.SerializationTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SkylarkImportLookupKey_AutoCodec}. */
@RunWith(JUnit4.class)
public final class SkylarkImportLookupKeyCodecTest {

  @Test
  public void testCodec() throws Exception {
    SerializationTester serializationTester =
        new SerializationTester(
            SkylarkImportLookupKey.create(
                Label.parseAbsolute("//foo/bar:baz", ImmutableMap.of()), false, -1, null),
            SkylarkImportLookupKey.create(
                Label.parseAbsolute("//foo/bar:baz", ImmutableMap.of()), true, -1, null),
            SkylarkImportLookupKey.create(
                Label.parseAbsolute("//foo/bar:baz", ImmutableMap.of()), true, 8, null),
            SkylarkImportLookupKey.create(
                Label.parseAbsolute("//foo/bar:baz", ImmutableMap.of()),
                true,
                4,
                FsUtils.TEST_ROOTED_PATH));
    FsUtils.addDependencies(serializationTester);
    serializationTester.runTests();
  }
}
