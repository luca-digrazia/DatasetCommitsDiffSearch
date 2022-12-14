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
package com.google.devtools.build.skyframe;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.skyframe.GraphTester.CONCATENATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.testing.GcFinalization;
import com.google.devtools.build.lib.events.Reporter;
import com.google.devtools.build.lib.testutil.TestUtils;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.skyframe.GraphTester.StringValue;
import com.google.devtools.build.skyframe.InvalidatingNodeVisitor.DirtyingInvalidationState;
import com.google.devtools.build.skyframe.InvalidatingNodeVisitor.InvalidationState;
import com.google.devtools.build.skyframe.InvalidatingNodeVisitor.InvalidationType;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * Tests for {@link InvalidatingNodeVisitor}.
 */
@RunWith(Enclosed.class)
public class EagerInvalidatorTest {
  protected InMemoryGraph graph;
  protected GraphTester tester = new GraphTester();
  protected InvalidationState state = newInvalidationState();
  protected AtomicReference<InvalidatingNodeVisitor> visitor = new AtomicReference<>();
  protected DirtyKeyTrackerImpl dirtyKeyTracker;

  private IntVersion graphVersion = new IntVersion(0);

  // The following three methods should be abstract, but junit4 does not allow us to run inner
  // classes in an abstract outer class. Thus, we provide implementations. These methods will never
  // be run because only the inner classes, annotated with @RunWith, will actually be executed.
  EvaluationProgressReceiver.InvalidationState expectedState() {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unused") // Overridden by subclasses.
  void invalidate(DirtiableGraph graph, EvaluationProgressReceiver invalidationReceiver,
      SkyKey... keys) throws InterruptedException { throw new UnsupportedOperationException(); }

  boolean gcExpected() { throw new UnsupportedOperationException(); }

  private boolean isInvalidated(SkyKey key) {
    NodeEntry entry = graph.get(key);
    if (gcExpected()) {
      return entry == null;
    } else {
      return entry == null || entry.isDirty();
    }
  }

  private void assertChanged(SkyKey key) {
    NodeEntry entry = graph.get(key);
    if (gcExpected()) {
      assertNull(entry);
    } else {
      assertTrue(entry.isChanged());
    }
  }

  private void assertDirtyAndNotChanged(SkyKey key) {
    NodeEntry entry = graph.get(key);
    if (gcExpected()) {
      assertNull(entry);
    } else {
      assertTrue(entry.isDirty());
      assertFalse(entry.isChanged());
    }

  }

  protected InvalidationState newInvalidationState() {
    throw new UnsupportedOperationException("Sublcasses must override");
  }

  protected InvalidationType defaultInvalidationType() {
    throw new UnsupportedOperationException("Sublcasses must override");
  }

  // Convenience method for eval-ing a single value.
  protected SkyValue eval(boolean keepGoing, SkyKey key) throws InterruptedException {
    SkyKey[] keys = { key };
    return eval(keepGoing, keys).get(key);
  }

  protected <T extends SkyValue> EvaluationResult<T> eval(boolean keepGoing, SkyKey... keys)
    throws InterruptedException {
    Reporter reporter = new Reporter();
    ParallelEvaluator evaluator = new ParallelEvaluator(graph, graphVersion,
        ImmutableMap.of(GraphTester.NODE_TYPE, tester.createDelegatingFunction()),
        reporter, new MemoizingEvaluator.EmittedEventState(),
        InMemoryMemoizingEvaluator.DEFAULT_STORED_EVENT_FILTER, keepGoing, 200, null,
        new DirtyKeyTrackerImpl());
    graphVersion = graphVersion.next();
    return evaluator.eval(ImmutableList.copyOf(keys));
  }

  protected void invalidateWithoutError(@Nullable EvaluationProgressReceiver invalidationReceiver,
      SkyKey... keys) throws InterruptedException {
    invalidate(graph, invalidationReceiver, keys);
    assertTrue(state.isEmpty());
  }

  protected void set(String name, String value) {
    tester.set(name, new StringValue(value));
  }

  protected SkyKey skyKey(String name) {
    return GraphTester.toSkyKeys(name)[0];
  }

  protected void assertValueValue(String name, String expectedValue) throws InterruptedException {
    StringValue value = (StringValue) eval(false, skyKey(name));
    assertEquals(expectedValue, value.getValue());
  }

  @Before
  public void setUp() throws Exception {
    dirtyKeyTracker = new DirtyKeyTrackerImpl();
  }

  @Test
  public void receiverWorks() throws Exception {
    final Set<String> invalidated = Sets.newConcurrentHashSet();
    EvaluationProgressReceiver receiver = new EvaluationProgressReceiver() {
      @Override
      public void invalidated(SkyValue value, InvalidationState state) {
        Preconditions.checkState(state == expectedState());
        invalidated.add(((StringValue) value).getValue());
      }

      @Override
      public void enqueueing(SkyKey skyKey) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void evaluated(SkyKey skyKey, SkyValue value, EvaluationState state) {
        throw new UnsupportedOperationException();
      }
    };
    graph = new InMemoryGraph();
    set("a", "a");
    set("b", "b");
    tester.getOrCreate("ab").addDependency("a").addDependency("b")
        .setComputedValue(CONCATENATE);
    assertValueValue("ab", "ab");

    set("a", "c");
    invalidateWithoutError(receiver, skyKey("a"));
    assertThat(invalidated).containsExactly("a", "ab");
    assertValueValue("ab", "cb");
    set("b", "d");
    invalidateWithoutError(receiver, skyKey("b"));
    assertThat(invalidated).containsExactly("a", "ab", "b", "cb");
  }

  @Test
  public void receiverIsNotNotifiedAboutValuesInError() throws Exception {
    final Set<String> invalidated = Sets.newConcurrentHashSet();
    EvaluationProgressReceiver receiver = new EvaluationProgressReceiver() {
      @Override
      public void invalidated(SkyValue value, InvalidationState state) {
        Preconditions.checkState(state == expectedState());
        invalidated.add(((StringValue) value).getValue());
      }

      @Override
      public void enqueueing(SkyKey skyKey) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void evaluated(SkyKey skyKey, SkyValue value, EvaluationState state) {
        throw new UnsupportedOperationException();
      }
    };

    graph = new InMemoryGraph();
    set("a", "a");
    tester.getOrCreate("ab").addDependency("a").setHasError(true);
    eval(false, skyKey("ab"));

    invalidateWithoutError(receiver, skyKey("a"));
    assertThat(invalidated).containsExactly("a").inOrder();
  }

  @Test
  public void invalidateValuesNotInGraph() throws Exception {
    final Set<String> invalidated = Sets.newConcurrentHashSet();
    EvaluationProgressReceiver receiver = new EvaluationProgressReceiver() {
      @Override
      public void invalidated(SkyValue value, InvalidationState state) {
        Preconditions.checkState(state == InvalidationState.DIRTY);
        invalidated.add(((StringValue) value).getValue());
      }

      @Override
      public void enqueueing(SkyKey skyKey) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void evaluated(SkyKey skyKey, SkyValue value, EvaluationState state) {
        throw new UnsupportedOperationException();
      }
    };
    graph = new InMemoryGraph();
    invalidateWithoutError(receiver, skyKey("a"));
    assertThat(invalidated).isEmpty();
    set("a", "a");
    assertValueValue("a", "a");
    invalidateWithoutError(receiver, skyKey("b"));
    assertThat(invalidated).isEmpty();
  }

  @Test
  public void invalidatedValuesAreGCedAsExpected() throws Exception {
    SkyKey key = GraphTester.skyKey("a");
    HeavyValue heavyValue = new HeavyValue();
    WeakReference<HeavyValue> weakRef = new WeakReference<>(heavyValue);
    tester.set("a", heavyValue);

    graph = new InMemoryGraph();
    eval(false, key);
    invalidate(graph, null, key);

    tester = null;
    heavyValue = null;
    if (gcExpected()) {
      GcFinalization.awaitClear(weakRef);
    } else {
      // Not a reliable check, but better than nothing.
      System.gc();
      Thread.sleep(300);
      assertNotNull(weakRef.get());
    }
  }

  @Test
  public void reverseDepsConsistent() throws Exception {
    graph = new InMemoryGraph();
    set("a", "a");
    set("b", "b");
    set("c", "c");
    tester.getOrCreate("ab").addDependency("a").addDependency("b").setComputedValue(CONCATENATE);
    tester.getOrCreate("bc").addDependency("b").addDependency("c").setComputedValue(CONCATENATE);
    tester.getOrCreate("ab_c").addDependency("ab").addDependency("c")
        .setComputedValue(CONCATENATE);
    eval(false, skyKey("ab_c"), skyKey("bc"));

    assertThat(graph.get(skyKey("a")).getReverseDeps()).containsExactly(skyKey("ab"));
    assertThat(graph.get(skyKey("b")).getReverseDeps()).containsExactly(skyKey("ab"), skyKey("bc"));
    assertThat(graph.get(skyKey("c")).getReverseDeps()).containsExactly(skyKey("ab_c"),
        skyKey("bc"));

    invalidateWithoutError(null, skyKey("ab"));
    eval(false);

    // The graph values should be gone.
    assertTrue(isInvalidated(skyKey("ab")));
    assertTrue(isInvalidated(skyKey("abc")));

    // The reverse deps to ab and ab_c should have been removed.
    assertThat(graph.get(skyKey("a")).getReverseDeps()).isEmpty();
    assertThat(graph.get(skyKey("b")).getReverseDeps()).containsExactly(skyKey("bc"));
    assertThat(graph.get(skyKey("c")).getReverseDeps()).containsExactly(skyKey("bc"));
  }

  @Test
  public void interruptChild() throws Exception {
    graph = new InMemoryGraph();
    int numValues = 50; // More values than the invalidator has threads.
    final SkyKey[] family = new SkyKey[numValues];
    final SkyKey child = GraphTester.skyKey("child");
    final StringValue childValue = new StringValue("child");
    tester.set(child, childValue);
    family[0] = child;
    for (int i = 1; i < numValues; i++) {
      SkyKey member = skyKey(Integer.toString(i));
      tester.getOrCreate(member).addDependency(family[i - 1]).setComputedValue(CONCATENATE);
      family[i] = member;
    }
    SkyKey parent = GraphTester.skyKey("parent");
    tester.getOrCreate(parent).addDependency(family[numValues - 1]).setComputedValue(CONCATENATE);
    eval(/*keepGoing=*/false, parent);
    final Thread mainThread = Thread.currentThread();
    final AtomicReference<SkyValue> badValue = new AtomicReference<>();
    EvaluationProgressReceiver receiver = new EvaluationProgressReceiver() {
      @Override
      public void invalidated(SkyValue value, InvalidationState state) {
        if (value == childValue) {
          // Interrupt on the very first invalidate
          mainThread.interrupt();
        } else if (!childValue.equals(value)) {
          // All other invalidations should be of the same value.
          // Exceptions thrown here may be silently dropped, so keep track of errors ourselves.
          badValue.set(value);
        }
        try {
          assertTrue(visitor.get().awaitInterruptionForTestingOnly(2, TimeUnit.HOURS));
        } catch (InterruptedException e) {
          // We may well have thrown here because by the time we try to await, the main thread is
          // already interrupted.
        }
      }

      @Override
      public void enqueueing(SkyKey skyKey) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void evaluated(SkyKey skyKey, SkyValue value, EvaluationState state) {
        throw new UnsupportedOperationException();
      }
    };
    try {
      invalidateWithoutError(receiver, child);
      fail();
    } catch (InterruptedException e) {
      // Expected.
    }
    assertNull(badValue.get());
    assertFalse(state.isEmpty());
    final Set<SkyValue> invalidated = Sets.newConcurrentHashSet();
    assertFalse(isInvalidated(parent));
    SkyValue parentValue = graph.getValue(parent);
    assertNotNull(parentValue);
    receiver = new EvaluationProgressReceiver() {
      @Override
      public void invalidated(SkyValue value, InvalidationState state) {
        invalidated.add(value);
      }

      @Override
      public void enqueueing(SkyKey skyKey) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void evaluated(SkyKey skyKey, SkyValue value, EvaluationState state) {
        throw new UnsupportedOperationException();
      }
    };
    invalidateWithoutError(receiver);
    assertTrue(invalidated.contains(parentValue));
    assertThat(state.getInvalidationsForTesting()).isEmpty();

    // Regression test coverage:
    // "all pending values are marked changed on interrupt".
    assertTrue(isInvalidated(child));
    assertChanged(child);
    for (int i = 1; i < numValues; i++) {
      assertDirtyAndNotChanged(family[i]);
    }
    assertDirtyAndNotChanged(parent);
  }

  private SkyKey[] constructLargeGraph(int size) {
    Random random = new Random(TestUtils.getRandomSeed());
    SkyKey[] values = new SkyKey[size];
    for (int i = 0; i < size; i++) {
      String iString = Integer.toString(i);
      SkyKey iKey = GraphTester.toSkyKey(iString);
      set(iString, iString);
      for (int j = 0; j < i; j++) {
        if (random.nextInt(3) == 0) {
          tester.getOrCreate(iKey).addDependency(Integer.toString(j));
        }
      }
      values[i] = iKey;
    }
    return values;
  }

  /** Returns a subset of {@code nodes} that are still valid and so can be invalidated. */
  private Set<Pair<SkyKey, InvalidationType>> getValuesToInvalidate(SkyKey[] nodes) {
    Set<Pair<SkyKey, InvalidationType>> result = new HashSet<>();
    Random random = new Random(TestUtils.getRandomSeed());
    for (SkyKey node : nodes) {
      if (!isInvalidated(node)) {
        if (result.isEmpty() || random.nextInt(3) == 0) {
          // Add at least one node, if we can.
          result.add(Pair.of(node, defaultInvalidationType()));
        }
      }
    }
    return result;
  }

  @Test
  public void interruptThreadInReceiver() throws Exception {
    Random random = new Random(TestUtils.getRandomSeed());
    int graphSize = 1000;
    int tries = 5;
    graph = new InMemoryGraph();
    SkyKey[] values = constructLargeGraph(graphSize);
    eval(/*keepGoing=*/false, values);
    final Thread mainThread = Thread.currentThread();
    for (int run = 0; run < tries; run++) {
      Set<Pair<SkyKey, InvalidationType>> valuesToInvalidate = getValuesToInvalidate(values);
      // Find how many invalidations will actually be enqueued for invalidation in the first round,
      // so that we can interrupt before all of them are done.
      int validValuesToDo =
          Sets.difference(valuesToInvalidate, state.getInvalidationsForTesting()).size();
      for (Pair<SkyKey, InvalidationType> pair : state.getInvalidationsForTesting()) {
        if (!isInvalidated(pair.first)) {
          validValuesToDo++;
        }
      }
      int countDownStart = validValuesToDo > 0 ? random.nextInt(validValuesToDo) : 0;
      final CountDownLatch countDownToInterrupt = new CountDownLatch(countDownStart);
      final EvaluationProgressReceiver receiver = new EvaluationProgressReceiver() {
        @Override
        public void invalidated(SkyValue value, InvalidationState state) {
          countDownToInterrupt.countDown();
          if (countDownToInterrupt.getCount() == 0) {
            mainThread.interrupt();
            try {
              // Wait for the main thread to be interrupted uninterruptibly, because the main thread
              // is going to interrupt us, and we don't want to get into an interrupt fight. Only
              // if we get interrupted without the main thread also being interrupted will this
              // throw an InterruptedException.
              TrackingAwaiter.waitAndMaybeThrowInterrupt(
                  visitor.get().getInterruptionLatchForTestingOnly(),
                  "Main thread was not interrupted");
            } catch (InterruptedException e) {
              throw new IllegalStateException(e);
            }
          }
        }

        @Override
        public void enqueueing(SkyKey skyKey) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void evaluated(SkyKey skyKey, SkyValue value, EvaluationState state) {
          throw new UnsupportedOperationException();
        }
      };
      try {
        invalidate(graph, receiver,
            Sets.newHashSet(
                Iterables.transform(valuesToInvalidate,
                    Pair.<SkyKey, InvalidationType>firstFunction())).toArray(new SkyKey[0]));
        assertThat(state.getInvalidationsForTesting()).isEmpty();
      } catch (InterruptedException e) {
        // Expected.
      }
      if (state.isEmpty()) {
        // Ran out of values to invalidate.
        break;
      }
    }

    eval(/*keepGoing=*/false, values);
  }

  protected void setupInvalidatableGraph() throws Exception {
    graph = new InMemoryGraph();
    set("a", "a");
    set("b", "b");
    tester.getOrCreate("ab").addDependency("a").addDependency("b").setComputedValue(CONCATENATE);
    assertValueValue("ab", "ab");
    set("a", "c");
  }

  private static class HeavyValue implements SkyValue {
  }

  /**
   * Test suite for the deleting invalidator.
   */
  @RunWith(JUnit4.class)
  public static class DeletingInvalidatorTest extends EagerInvalidatorTest {
    @Override
    protected void invalidate(DirtiableGraph graph, EvaluationProgressReceiver invalidationReceiver,
        SkyKey... keys) throws InterruptedException {
      InvalidatingNodeVisitor invalidatingVisitor =
          EagerInvalidator.createVisitor(/*delete=*/true, graph, ImmutableList.copyOf(keys),
              invalidationReceiver, state, true, dirtyKeyTracker);
      if (invalidatingVisitor != null) {
        visitor.set(invalidatingVisitor);
        invalidatingVisitor.run();
      }
    }

    @Override
    EvaluationProgressReceiver.InvalidationState expectedState() {
      return EvaluationProgressReceiver.InvalidationState.DELETED;
    }

    @Override
    boolean gcExpected() {
      return true;
    }

    @Override
    protected InvalidationState newInvalidationState() {
      return new InvalidatingNodeVisitor.DeletingInvalidationState();
    }

    @Override
    protected InvalidationType defaultInvalidationType() {
      return InvalidationType.DELETED;
    }

    @Test
    public void dirtyKeyTrackerWorksWithDeletingInvalidator() throws Exception {
      setupInvalidatableGraph();
      TrackingInvalidationReceiver receiver = new TrackingInvalidationReceiver();

      // Dirty the node, and ensure that the tracker is aware of it:
      InvalidatingNodeVisitor dirtyingVisitor =
          EagerInvalidator.createVisitor(/*delete=*/false, graph, ImmutableList.of(skyKey("a")),
              receiver, new DirtyingInvalidationState(), true, dirtyKeyTracker);
      dirtyingVisitor.run();
      assertThat(dirtyKeyTracker.getDirtyKeys()).containsExactly(skyKey("a"), skyKey("ab"));

      // Delete the node, and ensure that the tracker is no longer tracking it:
      InvalidatingNodeVisitor deletingVisitor =
          EagerInvalidator.createVisitor(/*delete=*/true, graph, ImmutableList.of(skyKey("a")),
              receiver, state, true, dirtyKeyTracker);
      deletingVisitor.run();
      assertThat(dirtyKeyTracker.getDirtyKeys()).containsExactly(skyKey("ab"));
    }
  }

  /**
   * Test suite for the dirtying invalidator.
   */
  @RunWith(JUnit4.class)
  public static class DirtyingInvalidatorTest extends EagerInvalidatorTest {
    @Override
    protected void invalidate(DirtiableGraph graph, EvaluationProgressReceiver invalidationReceiver,
        SkyKey... keys) throws InterruptedException {
      InvalidatingNodeVisitor invalidatingVisitor =
          EagerInvalidator.createVisitor(/*delete=*/false, graph, ImmutableList.copyOf(keys),
              invalidationReceiver, state, true, dirtyKeyTracker);
      if (invalidatingVisitor != null) {
        visitor.set(invalidatingVisitor);
        invalidatingVisitor.run();
      }
    }

    @Override
    EvaluationProgressReceiver.InvalidationState expectedState() {
      return EvaluationProgressReceiver.InvalidationState.DIRTY;
    }

    @Override
    boolean gcExpected() {
      return false;
    }

    @Override
    protected InvalidationState newInvalidationState() {
      return new DirtyingInvalidationState();
    }

    @Override
    protected InvalidationType defaultInvalidationType() {
      return InvalidationType.CHANGED;
    }

    @Test
    public void dirtyKeyTrackerWorksWithDirtyingInvalidator() throws Exception {
      setupInvalidatableGraph();
      TrackingInvalidationReceiver receiver = new TrackingInvalidationReceiver();

      // Dirty the node, and ensure that the tracker is aware of it:
      invalidate(graph, receiver, skyKey("a"));
      assertThat(dirtyKeyTracker.getDirtyKeys()).hasSize(2);
    }
  }
}
