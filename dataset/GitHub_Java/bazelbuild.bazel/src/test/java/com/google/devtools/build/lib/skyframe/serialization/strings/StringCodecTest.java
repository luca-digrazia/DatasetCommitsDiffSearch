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

package com.google.devtools.build.lib.skyframe.serialization.strings;

import com.google.devtools.build.lib.skyframe.serialization.testutils.ObjectCodecTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Basic tests for {@link StringCodec}. */
@RunWith(JUnit4.class)
public class StringCodecTest {

  @Test
  public void testCodec() throws Exception {
    ObjectCodecTester.newBuilder(new StringCodec())
        .addSubjects("usually precomputed and supports weird unicodes: （╯°□°）╯︵┻━┻ ")
        .buildAndRunTests();
  }
}
