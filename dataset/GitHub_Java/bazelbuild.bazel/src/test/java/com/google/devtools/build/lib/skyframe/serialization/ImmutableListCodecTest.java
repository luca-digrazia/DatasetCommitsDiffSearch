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

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.skyframe.serialization.strings.StringCodecs;
import com.google.devtools.build.lib.skyframe.serialization.testutils.AbstractObjectCodecTest;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ImmutableListCodec}. */
@RunWith(JUnit4.class)
public class ImmutableListCodecTest extends AbstractObjectCodecTest<ImmutableList<String>> {

  @SuppressWarnings("unchecked")
  public ImmutableListCodecTest() {
    super(
        new ImmutableListCodec<>(StringCodecs.simple()),
        ImmutableList.of(),
        ImmutableList.of("foo"),
        ImmutableList.of("bar", "baz"));
  }
}
