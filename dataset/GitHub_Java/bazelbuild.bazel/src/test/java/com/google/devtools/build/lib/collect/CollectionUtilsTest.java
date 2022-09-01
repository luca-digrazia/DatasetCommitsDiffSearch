// Copyright 2014 Google Inc. All rights reserved.
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
package com.google.devtools.build.lib.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link CollectionUtils}.
 */

@RunWith(JUnit4.class)
public class CollectionUtilsTest {

  @Test
  public void testDuplicatedElementsOf() {
    assertDups(ImmutableList.<Integer>of(), ImmutableSet.<Integer>of());
    assertDups(ImmutableList.of(0), ImmutableSet.<Integer>of());
    assertDups(ImmutableList.of(0, 0, 0), ImmutableSet.of(0));
    assertDups(ImmutableList.of(1, 2, 3, 1, 2, 3), ImmutableSet.of(1, 2, 3));
    assertDups(ImmutableList.of(1, 2, 3, 1, 2, 3, 4), ImmutableSet.of(1, 2, 3));
    assertDups(ImmutableList.of(1, 2, 3, 4), ImmutableSet.<Integer>of());
  }

  private static void assertDups(List<Integer> collection, Set<Integer> dups) {
    assertEquals(dups, CollectionUtils.duplicatedElementsOf(collection));
  }

  @Test
  public void testIsImmutable() throws Exception {
    assertTrue(CollectionUtils.isImmutable(ImmutableList.of(1, 2, 3)));
    assertTrue(CollectionUtils.isImmutable(ImmutableSet.of(1, 2, 3)));

    NestedSet<Integer> ns = NestedSetBuilder.<Integer>compileOrder()
        .add(1).add(2).add(3).build();
    assertTrue(CollectionUtils.isImmutable(ns));

    NestedSet<Integer> ns2 = NestedSetBuilder.<Integer>linkOrder().add(1).add(2).add(3).build();
    assertTrue(CollectionUtils.isImmutable(ns2));

    IterablesChain<Integer> chain = IterablesChain.<Integer>builder().addElement(1).build();

    assertTrue(CollectionUtils.isImmutable(chain));

    assertFalse(CollectionUtils.isImmutable(Lists.newArrayList()));
    assertFalse(CollectionUtils.isImmutable(Lists.newLinkedList()));
    assertFalse(CollectionUtils.isImmutable(Sets.newHashSet()));
    assertFalse(CollectionUtils.isImmutable(Sets.newLinkedHashSet()));

    // The result of Iterables.concat() actually is immutable, but we have no way of checking if
    // a given Iterable comes from concat().
    assertFalse(CollectionUtils.isImmutable(Iterables.concat(ns, ns2)));

    // We can override the check by using the ImmutableIterable wrapper.
    assertTrue(CollectionUtils.isImmutable(
        ImmutableIterable.from(Iterables.concat(ns, ns2))));
  }

  @Test
  public void testCheckImmutable() throws Exception {
    CollectionUtils.checkImmutable(ImmutableList.of(1, 2, 3));
    CollectionUtils.checkImmutable(ImmutableSet.of(1, 2, 3));

    try {
      CollectionUtils.checkImmutable(Lists.newArrayList(1, 2, 3));
    } catch (IllegalStateException e) {
      return;
    }
    fail();
  }

  @Test
  public void testMakeImmutable() throws Exception {
    Iterable<Integer> immutableList = ImmutableList.of(1, 2, 3);
    assertSame(immutableList, CollectionUtils.makeImmutable(immutableList));

    Iterable<Integer> mutableList = Lists.newArrayList(1, 2, 3);
    Iterable<Integer> converted = CollectionUtils.makeImmutable(mutableList);
    assertNotSame(mutableList, converted);
    assertEquals(mutableList, ImmutableList.copyOf(converted));
  }

  private static enum Small { ALPHA, BRAVO }
  private static enum Large {
    L0, L1, L2, L3, L4, L5, L6, L7, L8, L9,
    L10, L11, L12, L13, L14, L15, L16, L17, L18, L19,
    L20, L21, L22, L23, L24, L25, L26, L27, L28, L29,
    L30, L31,
  }

  private static enum TooLarge {
    T0, T1, T2, T3, T4, T5, T6, T7, T8, T9,
    T10, T11, T12, T13, T14, T15, T16, T17, T18, T19,
    T20, T21, T22, T23, T24, T25, T26, T27, T28, T29,
    T30, T31, T32,
  }

  private static enum Medium {
    ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT,
  }

  private <T extends Enum<T>> void assertAllDifferent(Class<T> clazz) throws Exception {
    Set<EnumSet<T>> allSets = new HashSet<>();

    int maxBits = 1 << clazz.getEnumConstants().length;
    for (int i = 0; i < maxBits; i++) {
      EnumSet<T> set = CollectionUtils.fromBits(i, clazz);
      int back = CollectionUtils.toBits(set);
      assertEquals(back, i);  // Assert that a roundtrip is idempotent
      allSets.add(set);
    }

    assertEquals(maxBits, allSets.size());  // Assert that every decoded value is different
  }

  @Test
  public void testEnumBitfields() throws Exception {
    assertEquals(0, CollectionUtils.<Small>toBits());
    assertEquals(EnumSet.noneOf(Small.class), CollectionUtils.fromBits(0, Small.class));
    assertEquals(3, CollectionUtils.toBits(Small.ALPHA, Small.BRAVO));
    assertEquals(10, CollectionUtils.toBits(Medium.TWO, Medium.FOUR));
    assertEquals(EnumSet.of(Medium.SEVEN, Medium.EIGHT),
        CollectionUtils.fromBits(192, Medium.class));

    assertAllDifferent(Small.class);
    assertAllDifferent(Medium.class);
    assertAllDifferent(Large.class);

    try {
      CollectionUtils.toBits(TooLarge.T32);
      fail();
    } catch (IllegalArgumentException e) {
      // good
    }

    try {
      CollectionUtils.fromBits(0, TooLarge.class);
      fail();
    } catch (IllegalArgumentException e) {
      // good
    }
  }
}
