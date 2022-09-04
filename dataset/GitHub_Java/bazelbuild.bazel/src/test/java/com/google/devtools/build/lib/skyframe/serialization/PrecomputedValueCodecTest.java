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

import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.skyframe.PrecomputedValue;
import com.google.devtools.build.lib.skyframe.PrecomputedValueCodec;
import com.google.devtools.build.lib.skyframe.serialization.testutils.AbstractObjectCodecTest;
import com.google.devtools.build.lib.vfs.PathFragment;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PrecomputedValueCodec}. */
@RunWith(JUnit4.class)
public class PrecomputedValueCodecTest extends AbstractObjectCodecTest<PrecomputedValue> {
  public PrecomputedValueCodecTest() {
    super(
        new PrecomputedValueCodec(
            () ->
                ObjectCodecs.newBuilder()
                    .asClassKeyedBuilder()
                    // Note no PathFragmentCodec.
                    .add(String.class, new FastStringCodec())
                    .add(Label.class, LabelCodec.INSTANCE)
                    .build()),
        new PrecomputedValue(PathFragment.create("java serializable 1")),
        new PrecomputedValue(PathFragment.create("java serializable 2")),
        new PrecomputedValue("first string"),
        new PrecomputedValue("second string"),
        new PrecomputedValue(Label.parseAbsoluteUnchecked("//foo:bar")),
        new PrecomputedValue(Label.parseAbsoluteUnchecked("//foo:baz")));
  }
}
