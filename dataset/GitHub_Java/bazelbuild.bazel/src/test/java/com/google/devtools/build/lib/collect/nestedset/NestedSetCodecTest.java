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
package com.google.devtools.build.lib.collect.nestedset;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.skyframe.serialization.AutoRegistry;
import com.google.devtools.build.lib.skyframe.serialization.ObjectCodecs;
import com.google.devtools.build.lib.skyframe.serialization.SerializationConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Tests for {@link NestedSet} serialization. */
@RunWith(JUnit4.class)
public class NestedSetCodecTest {
  @Before
  public void setUp() {
    SerializationConstants.shouldSerializeNestedSet = true;
  }

  @After
  public void tearDown() {
    SerializationConstants.shouldSerializeNestedSet = false;
  }

  @Test
  public void testAutoCodecedCodec() throws Exception {
    ObjectCodecs objectCodecs =
        new ObjectCodecs(
            AutoRegistry.get().getBuilder().setAllowDefaultCodec(true).build(), ImmutableMap.of());
    NestedSetCodecTestUtils.checkCodec(objectCodecs, false);
  }

  @Test
  public void testCodecWithInMemoryNestedSetStore() throws Exception {
    ObjectCodecs objectCodecs =
        new ObjectCodecs(
            AutoRegistry.get()
                .getBuilder()
                .setAllowDefaultCodec(true)
                .add(new NestedSetCodecWithStore<>(NestedSetStore.inMemory()))
                .build(),
            ImmutableMap.of());
    NestedSetCodecTestUtils.checkCodec(objectCodecs, true);
  }

  @Test
  public void testSingletonNestedSetSerializedWithoutStore() throws Exception {
    NestedSetStore mockNestedSetStore = Mockito.mock(NestedSetStore.class);
    Mockito.when(mockNestedSetStore.computeFingerprintAndStore(Mockito.any(), Mockito.any()))
        .thenThrow(new AssertionError("NestedSetStore should not have been used"));

    ObjectCodecs objectCodecs =
        new ObjectCodecs(
            AutoRegistry.get()
                .getBuilder()
                .setAllowDefaultCodec(true)
                .add(new NestedSetCodecWithStore<>(mockNestedSetStore))
                .build());
    NestedSet<String> singletonNestedSet = new NestedSet<>(Order.STABLE_ORDER, "a");
    objectCodecs.serialize(singletonNestedSet);
  }
}
