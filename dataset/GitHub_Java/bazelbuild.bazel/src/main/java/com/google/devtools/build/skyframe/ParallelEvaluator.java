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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.NestedSetVisitor;
import com.google.devtools.build.lib.concurrent.AbstractQueueVisitor;
import com.google.devtools.build.lib.concurrent.ExecutorShutdownUtil;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadCompatible;
import com.google.devtools.build.lib.concurrent.ThrowableRecordingRunnableWrapper;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.events.StoredEventHandler;
import com.google.devtools.build.lib.profiler.Profiler;
import com.google.devtools.build.lib.profiler.ProfilerTask;
import com.google.devtools.build.lib.util.GroupedList.GroupedListHelper;
import com.google.devtools.build.skyframe.EvaluationProgressReceiver.EvaluationState;
import com.google.devtools.build.skyframe.NodeEntry.DependencyState;
import com.google.devtools.build.skyframe.Scheduler.SchedulerException;
import com.google.devtools.build.skyframe.SkyFunctionException.ReifiedSkyFunctionException;
import com.google.devtools.build.skyframe.ValueOrExceptionUtils.BottomException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

/**
 * Evaluates a set of given functions ({@code SkyFunction}s) with arguments ({@code SkyKey}s).
 * Cycles are not allowed and are detected during the traversal.
 *
 * <p>This class implements multi-threaded evaluation. This is a fairly complex process that has
 * strong consistency requirements between the {@link ProcessableGraph}, the nodes in the graph of
 * type {@link NodeEntry}, the work queue, and the set of in-flight nodes.
 *
 * <p>The basic invariants are:
 *
 * <p>A node can be in one of three states: ready, waiting, and done. A node is ready if and only
 * if all of its dependencies have been signaled. A node is done if it has a value. It is waiting
 * if not all of its dependencies have been signaled.
 *
 * <p>A node must be in the work queue if and only if it is ready. It is an error for a node to be
 * in the work queue twice at the same time.
 *
 * <p>A node is considered in-flight if it has been created, and is not done yet. In case of an
 * interrupt, the work queue is discarded, and the in-flight set is used to remove partially
 * computed values.
 *
 * <p>Each evaluation of the graph takes place at a "version," which is currently given by a
 * non-negative {@code long}. The version can also be thought of as an "mtime." Each node in the
 * graph has a version, which is the last version at which its value changed. This version data is
 * used to avoid unnecessary re-evaluation of values. If a node is re-evaluated and found to have
 * the same data as before, its version (mtime) remains the same. If all of a node's children's
 * have the same version as before, its re-evaluation can be skipped.
 *
 * <p>This class is not intended for direct use, and is only exposed as public for use in
 * evaluation implementations outside of this package.
 */
public final class ParallelEvaluator implements Evaluator {
  private final ProcessableGraph graph;
  private final Version graphVersion;

  private final Predicate<SkyKey> nodeEntryIsDone = new Predicate<SkyKey>() {
    @Override
    public boolean apply(SkyKey skyKey) {
      return isDoneForBuild(graph.get(skyKey));
    }
  };

  private final ImmutableMap<? extends SkyFunctionName, ? extends SkyFunction> skyFunctions;

  private final EventHandler reporter;
  private final NestedSetVisitor<TaggedEvents> replayingNestedSetEventVisitor;
  private final boolean keepGoing;
  private final int threadCount;
  @Nullable private final EvaluationProgressReceiver progressReceiver;
  private final DirtyKeyTracker dirtyKeyTracker;
  private final AtomicBoolean errorEncountered = new AtomicBoolean(false);

  private static final Interner<SkyKey> KEY_CANONICALIZER =  Interners.newWeakInterner();

  public ParallelEvaluator(ProcessableGraph graph, Version graphVersion,
                    ImmutableMap<? extends SkyFunctionName, ? extends SkyFunction> skyFunctions,
                    final EventHandler reporter,
                    MemoizingEvaluator.EmittedEventState emittedEventState,
                    boolean keepGoing, int threadCount,
                    @Nullable EvaluationProgressReceiver progressReceiver,
                    DirtyKeyTracker dirtyKeyTracker) {
    this.graph = graph;
    this.skyFunctions = skyFunctions;
    this.graphVersion = graphVersion;
    this.reporter = Preconditions.checkNotNull(reporter);
    this.keepGoing = keepGoing;
    this.threadCount = threadCount;
    this.progressReceiver = progressReceiver;
    this.dirtyKeyTracker = Preconditions.checkNotNull(dirtyKeyTracker);
    this.replayingNestedSetEventVisitor =
        new NestedSetVisitor<>(new NestedSetEventReceiver(reporter), emittedEventState);
  }

  /**
   * Receives the events from the NestedSet and delegates to the reporter.
   */
  private static class NestedSetEventReceiver implements NestedSetVisitor.Receiver<TaggedEvents> {

    private final EventHandler reporter;

    public NestedSetEventReceiver(EventHandler reporter) {
      this.reporter = reporter;
    }
    @Override
    public void accept(TaggedEvents events) {
      String tag = events.getTag();
      for (Event e : events.getEvents()) {
        reporter.handle(e.withTag(tag));
      }
    }
  }

  /**
   * A suitable {@link SkyFunction.Environment} implementation.
   */
  class SkyFunctionEnvironment implements SkyFunction.Environment {
    private boolean building = true;
    private boolean valuesMissing = false;
    private SkyKey depErrorKey = null;
    private final SkyKey skyKey;
    private SkyValue value = null;
    private ErrorInfo errorInfo = null;
    private final Map<SkyKey, ValueWithMetadata> bubbleErrorInfo;
    /** The set of values previously declared as dependencies. */
    private final Set<SkyKey> directDeps;

    /**
     * The grouped list of values requested during this build as dependencies. On a subsequent
     * build, if this value is dirty, all deps in the same dependency group can be checked in
     * parallel for changes. In other words, if dep1 and dep2 are in the same group, then dep1 will
     * be checked in parallel with dep2. See {@link #getValues} for more.
     */
    private final GroupedListHelper<SkyKey> newlyRequestedDeps = new GroupedListHelper<>();

    /**
     * The value visitor managing the thread pool. Used to enqueue parents when this value is
     * finished, and, during testing, to block until an exception is thrown if a value builder
     * requests that.
     */
    private final ValueVisitor visitor;

    /** The set of errors encountered while fetching children. */
    private final Collection<ErrorInfo> childErrorInfos = new LinkedHashSet<>();
    private final StoredEventHandler eventHandler = new StoredEventHandler() {
      @Override
      public void handle(Event e) {
        checkActive();
        switch (e.getKind()) {
          case INFO:
            throw new UnsupportedOperationException("Values should not display INFO messages: " +
                skyKey + " printed " + e.getLocation() + ": " + e.getMessage());
          case PROGRESS:
            reporter.handle(e);
            break;
          default:
            super.handle(e);
        }
      }
    };

    private SkyFunctionEnvironment(SkyKey skyKey, Set<SkyKey> directDeps, ValueVisitor visitor) {
      this(skyKey, directDeps, null, visitor);
    }

    private SkyFunctionEnvironment(SkyKey skyKey, Set<SkyKey> directDeps,
        @Nullable Map<SkyKey, ValueWithMetadata> bubbleErrorInfo, ValueVisitor visitor) {
      this.skyKey = skyKey;
      this.directDeps = Collections.unmodifiableSet(directDeps);
      this.bubbleErrorInfo = bubbleErrorInfo;
      this.childErrorInfos.addAll(childErrorInfos);
      this.visitor = visitor;
    }

    private void checkActive() {
      Preconditions.checkState(building, skyKey);
    }

    private NestedSet<TaggedEvents> buildEvents(boolean missingChildren) {
      // Aggregate the nested set of events from the direct deps, also adding the events from
      // building this value.
      NestedSetBuilder<TaggedEvents> eventBuilder = NestedSetBuilder.stableOrder();
      ImmutableList<Event> events = eventHandler.getEvents();
      if (!events.isEmpty()) {
        eventBuilder.add(new TaggedEvents(getTagFromKey(), events));
      }
      for (SkyKey dep : graph.get(skyKey).getTemporaryDirectDeps()) {
        ValueWithMetadata value = getValueMaybeFromError(dep, bubbleErrorInfo);
        if (value != null) {
          eventBuilder.addTransitive(value.getTransitiveEvents());
        } else {
          Preconditions.checkState(missingChildren, "", dep, skyKey);
        }
      }
      return eventBuilder.build();
    }

    /**
     * If this node has an error, that is, if errorInfo is non-null, do nothing. Otherwise, set
     * errorInfo to the union of the child errors that were recorded earlier by getValueOrException,
     * if there are any.
     */
    private void finalizeErrorInfo() {
      if (errorInfo == null && !childErrorInfos.isEmpty()) {
        errorInfo = new ErrorInfo(skyKey, childErrorInfos);
      }
    }

    private void setValue(SkyValue newValue) {
      Preconditions.checkState(errorInfo == null && bubbleErrorInfo == null,
          "%s %s %s %s", skyKey, newValue, errorInfo, bubbleErrorInfo);
      Preconditions.checkState(value == null, "%s %s %s", skyKey, value, newValue);
      value = newValue;
    }

    /**
     * Set this node to be in error. The node's value must not have already been set. However, all
     * dependencies of this node <i>must</i> already have been registered, since this method may
     * register a dependence on the error transience node, which should always be the last dep.
     */
    private void setError(ErrorInfo errorInfo) {
      Preconditions.checkState(value == null, "%s %s %s", skyKey, value, errorInfo);
      Preconditions.checkState(this.errorInfo == null,
          "%s %s %s", skyKey, this.errorInfo, errorInfo);

      if (errorInfo.isTransient()) {
        DependencyState triState =
            graph.get(ErrorTransienceValue.key()).addReverseDepAndCheckIfDone(skyKey);
        Preconditions.checkState(triState == DependencyState.DONE,
            "%s %s %s", skyKey, triState, errorInfo);

        final NodeEntry state = graph.get(skyKey);
        state.addTemporaryDirectDeps(
            GroupedListHelper.create(ImmutableList.of(ErrorTransienceValue.key())));
        state.signalDep();
      }

      this.errorInfo = Preconditions.checkNotNull(errorInfo, skyKey);
    }

    /** Get a child of the value being evaluated, for use by the value builder. */
    private ValueOrUntypedException getValueOrUntypedException(SkyKey depKey) {
      checkActive();
      depKey = KEY_CANONICALIZER.intern(depKey);  // Canonicalize SkyKeys to save memory.
      ValueWithMetadata value = getValueMaybeFromError(depKey, bubbleErrorInfo);
      if (value == null) {
        // If this entry is not yet done then (optionally) record the missing dependency and return
        // null.
        valuesMissing = true;
        if (bubbleErrorInfo != null) {
          // Values being built just for their errors don't get to request new children.
          return ValueOrExceptionUtils.ofNull();
        }
        Preconditions.checkState(!directDeps.contains(depKey), "%s %s %s", skyKey, depKey, value);
        addDep(depKey);
        valuesMissing = true;
        return ValueOrExceptionUtils.ofNull();
      }

      if (!directDeps.contains(depKey)) {
        // If this child is done, we will return it, but also record that it was newly requested so
        // that the dependency can be properly registered in the graph.
        addDep(depKey);
      }

      replayingNestedSetEventVisitor.visit(value.getTransitiveEvents());
      ErrorInfo errorInfo = value.getErrorInfo();

      if (errorInfo != null) {
        childErrorInfos.add(errorInfo);
      }

      if (value.getValue() != null && (keepGoing || errorInfo == null)) {
        // The caller is given the value of the value if there was no error computing the value, or
        // if this is a keepGoing build (in which case each value should get child values even if
        // there are also errors).
        return ValueOrExceptionUtils.ofValueUntyped(value.getValue());
      }

      // There was an error building the value, which we will either report by throwing an exception
      // or insulate the caller from by returning null.
      Preconditions.checkNotNull(errorInfo, "%s %s %s", skyKey, depKey, value);

      if (!keepGoing && errorInfo.getException() != null && bubbleErrorInfo == null) {
        // Child errors should not be propagated in noKeepGoing mode (except during error bubbling).
        // Instead we should fail fast.

        // We arbitrarily record the first child error.
        if (depErrorKey == null) {
          depErrorKey = depKey;
        }
        valuesMissing = true;
        return ValueOrExceptionUtils.ofNull();
      }

      if (bubbleErrorInfo != null) {
        // Set interrupted status, so that builder doesn't try anything fancy after this.
        Thread.currentThread().interrupt();
      }
      if (errorInfo.getException() != null) {
        // Give builder a chance to handle this exception.
        Exception e = errorInfo.getException();
        return ValueOrExceptionUtils.ofExn(e);
      }
      // In a cycle.
      Preconditions.checkState(!Iterables.isEmpty(errorInfo.getCycleInfo()), "%s %s %s %s", skyKey,
          depKey, errorInfo, value);
      valuesMissing = true;
      return ValueOrExceptionUtils.ofNull();
    }

    private <E extends Exception> ValueOrException<E> getValueOrException(SkyKey depKey,
        Class<E> exceptionClass) {
      return ValueOrExceptionUtils.downcovert(getValueOrException(depKey, exceptionClass,
          BottomException.class), exceptionClass);
    }

    private <E1 extends Exception, E2 extends Exception> ValueOrException2<E1, E2>
        getValueOrException(SkyKey depKey, Class<E1> exceptionClass1, Class<E2> exceptionClass2) {
      return ValueOrExceptionUtils.downconvert(getValueOrException(depKey, exceptionClass1,
          exceptionClass2, BottomException.class), exceptionClass1, exceptionClass2);
    }

    private <E1 extends Exception, E2 extends Exception, E3 extends Exception>
    ValueOrException3<E1, E2, E3> getValueOrException(SkyKey depKey, Class<E1> exceptionClass1,
            Class<E2> exceptionClass2, Class<E3> exceptionClass3) {
      return ValueOrExceptionUtils.downconvert(getValueOrException(depKey, exceptionClass1,
          exceptionClass2, exceptionClass3, BottomException.class), exceptionClass1,
          exceptionClass2, exceptionClass3);
    }

    private <E1 extends Exception, E2 extends Exception, E3 extends Exception,
        E4 extends Exception> ValueOrException4<E1, E2, E3, E4> getValueOrException(SkyKey depKey,
        Class<E1> exceptionClass1, Class<E2> exceptionClass2, Class<E3> exceptionClass3,
        Class<E4> exceptionClass4) {
      SkyFunctionException.validateExceptionType(exceptionClass1);
      SkyFunctionException.validateExceptionType(exceptionClass2);
      SkyFunctionException.validateExceptionType(exceptionClass3);
      SkyFunctionException.validateExceptionType(exceptionClass4);
      ValueOrUntypedException voe = getValueOrUntypedException(depKey);
      SkyValue value = voe.getValue();
      if (value != null) {
        return ValueOrExceptionUtils.ofValue(value);
      }
      Exception e = voe.getException();
      if (e != null) {
        if (exceptionClass1.isInstance(e)) {
          return ValueOrExceptionUtils.ofExn1(exceptionClass1.cast(e));
        }
        if (exceptionClass2.isInstance(e)) {
          return ValueOrExceptionUtils.ofExn2(exceptionClass2.cast(e));
        }
        if (exceptionClass3.isInstance(e)) {
          return ValueOrExceptionUtils.ofExn3(exceptionClass3.cast(e));
        }
        if (exceptionClass4.isInstance(e)) {
          return ValueOrExceptionUtils.ofExn4(exceptionClass4.cast(e));
        }
      }
      valuesMissing = true;
      return ValueOrExceptionUtils.ofNullValue();
    }

    @Override
    @Nullable
    public SkyValue getValue(SkyKey depKey) {
      try {
        return getValueOrThrow(depKey, BottomException.class);
      } catch (BottomException e) {
        throw new IllegalStateException("shouldn't reach here");
      }
    }

    @Override
    @Nullable
    public <E extends Exception> SkyValue getValueOrThrow(SkyKey depKey, Class<E> exceptionClass)
        throws E {
      return getValueOrException(depKey, exceptionClass).get();
    }

    @Override
    @Nullable
    public <E1 extends Exception, E2 extends Exception> SkyValue getValueOrThrow(SkyKey depKey,
        Class<E1> exceptionClass1, Class<E2> exceptionClass2) throws E1, E2 {
      return getValueOrException(depKey, exceptionClass1, exceptionClass2).get();
    }

    @Override
    @Nullable
    public <E1 extends Exception, E2 extends Exception,
        E3 extends Exception> SkyValue getValueOrThrow(SkyKey depKey, Class<E1> exceptionClass1,
        Class<E2> exceptionClass2, Class<E3> exceptionClass3) throws E1, E2, E3 {
      return getValueOrException(depKey, exceptionClass1, exceptionClass2, exceptionClass3).get();
    }

    @Override
    public <E1 extends Exception, E2 extends Exception, E3 extends Exception,
        E4 extends Exception> SkyValue getValueOrThrow(SkyKey depKey, Class<E1> exceptionClass1,
        Class<E2> exceptionClass2, Class<E3> exceptionClass3, Class<E4> exceptionClass4) throws E1,
        E2, E3, E4 {
      return getValueOrException(depKey, exceptionClass1, exceptionClass2, exceptionClass3,
          exceptionClass4).get();
    }

    @Override
    public Map<SkyKey, SkyValue> getValues(Iterable<SkyKey> depKeys) {
      return Maps.transformValues(getValuesOrThrow(depKeys, BottomException.class),
          GET_VALUE_FROM_VOE);
    }

    @Override
    public <E extends Exception> Map<SkyKey, ValueOrException<E>> getValuesOrThrow(
        Iterable<SkyKey> depKeys, Class<E> exceptionClass) {
      return Maps.transformValues(getValuesOrThrow(depKeys, exceptionClass, BottomException.class),
          makeSafeDowncastToVOEFunction(exceptionClass));
    }

    @Override
    public <E1 extends Exception,
        E2 extends Exception> Map<SkyKey, ValueOrException2<E1, E2>> getValuesOrThrow(
        Iterable<SkyKey> depKeys, Class<E1> exceptionClass1, Class<E2> exceptionClass2) {
      return Maps.transformValues(getValuesOrThrow(depKeys, exceptionClass1, exceptionClass2,
          BottomException.class), makeSafeDowncastToVOE2Function(exceptionClass1,
              exceptionClass2));
    }

    @Override
    public <E1 extends Exception, E2 extends Exception, E3 extends Exception> Map<SkyKey,
        ValueOrException3<E1, E2, E3>> getValuesOrThrow(Iterable<SkyKey> depKeys,
        Class<E1> exceptionClass1, Class<E2> exceptionClass2, Class<E3> exceptionClass3) {
      return Maps.transformValues(getValuesOrThrow(depKeys, exceptionClass1, exceptionClass2,
          exceptionClass3, BottomException.class), makeSafeDowncastToVOE3Function(exceptionClass1,
              exceptionClass2, exceptionClass3));
    }

    @Override
    public <E1 extends Exception, E2 extends Exception, E3 extends Exception,
        E4 extends Exception> Map<SkyKey, ValueOrException4<E1, E2, E3, E4>> getValuesOrThrow(
        Iterable<SkyKey> depKeys, Class<E1> exceptionClass1, Class<E2> exceptionClass2,
        Class<E3> exceptionClass3, Class<E4> exceptionClass4) {
      Map<SkyKey, ValueOrException4<E1, E2, E3, E4>> result = new HashMap<>();
      newlyRequestedDeps.startGroup();
      for (SkyKey depKey : depKeys) {
        if (result.containsKey(depKey)) {
          continue;
        }
        result.put(depKey, getValueOrException(depKey, exceptionClass1, exceptionClass2,
            exceptionClass3, exceptionClass4));
      }
      newlyRequestedDeps.endGroup();
      return Collections.unmodifiableMap(result);
    }

    private void addDep(SkyKey key) {
      if (!newlyRequestedDeps.contains(key)) {
        // dep may have been requested already this evaluation. If not, add it.
        newlyRequestedDeps.add(key);
      }
    }

    @Override
    public boolean valuesMissing() {
      return valuesMissing;
    }

    /**
     * If {@code !keepGoing} and there is at least one dep in error, returns a dep in error.
     * Otherwise returns {@code null}.
     */
    @Nullable
    private SkyKey getDepErrorKey() {
      return depErrorKey;
    }

    @Override
    public EventHandler getListener() {
      checkActive();
      return eventHandler;
    }

    private void doneBuilding() {
      building = false;
    }

    /**
     * Apply the change to the graph (mostly) atomically and signal all nodes that are waiting for
     * this node to complete. Adding nodes and signaling is not atomic, but may need to be changed
     * for interruptibility.
     *
     * <p>Parents are only enqueued if {@code enqueueParents} holds. Parents should be enqueued
     * unless (1) this node is being built after the main evaluation has aborted, or (2) this node
     * is being built with --nokeep_going, and so we are about to shut down the main evaluation
     * anyway.
     *
     * <p>The node entry is informed if the node's value and error are definitive via the flag
     * {@code completeValue}.
     */
    void commit(boolean enqueueParents) {
      NodeEntry primaryEntry = Preconditions.checkNotNull(graph.get(skyKey), skyKey);
      // Construct the definitive error info, if there is one.
      finalizeErrorInfo();

      // We have the following implications:
      // errorInfo == null => value != null => enqueueParents.
      // All these implications are strict:
      // (1) errorInfo != null && value != null happens for values with recoverable errors.
      // (2) value == null && enqueueParents happens for values that are found to have errors
      // during a --keep_going build.

      NestedSet<TaggedEvents> events = buildEvents(/*missingChildren=*/false);
      Version valueVersion;
      SkyValue valueWithMetadata;
      if (value == null) {
        Preconditions.checkNotNull(errorInfo, "%s %s", skyKey, primaryEntry);
        valueWithMetadata = ValueWithMetadata.error(errorInfo, events);
      } else {
        // We must be enqueueing parents if we have a value.
        Preconditions.checkState(enqueueParents, "%s %s", skyKey, primaryEntry);
        valueWithMetadata = ValueWithMetadata.normal(value, errorInfo, events);
      }
      // If this entry is dirty, setValue may not actually change it, if it determines that
      // the data being written now is the same as the data already present in the entry.
      // We could consider using max(childVersions) here instead of graphVersion. When full
      // versioning is implemented, this would allow evaluation at a version between
      // max(childVersions) and graphVersion to re-use this result.
      Set<SkyKey> reverseDeps = primaryEntry.setValue(valueWithMetadata, graphVersion);
      // Note that if this update didn't actually change the value entry, this version may not
      // be the graph version.
      valueVersion = primaryEntry.getVersion();
      Preconditions.checkState(valueVersion.atMost(graphVersion),
          "%s should be at most %s in the version partial ordering",
          valueVersion, graphVersion);
      if (progressReceiver != null) {
        // Tell the receiver that this value was built. If valueVersion.equals(graphVersion), it
        // was evaluated this run, and so was changed. Otherwise, it is less than graphVersion,
        // by the Preconditions check above, and was not actually changed this run -- when it was
        // written above, its version stayed below this update's version, so its value remains the
        // same as before.
        progressReceiver.evaluated(skyKey, value,
            valueVersion.equals(graphVersion) ? EvaluationState.BUILT : EvaluationState.CLEAN);
      }
      signalValuesAndEnqueueIfReady(enqueueParents ? visitor : null, reverseDeps, valueVersion);

      visitor.notifyDone(skyKey);
      replayingNestedSetEventVisitor.visit(events);
    }

    @Nullable
    private String getTagFromKey() {
      return skyFunctions.get(skyKey.functionName()).extractTag(skyKey);
    }

    /**
     * Gets the latch that is counted down when an exception is thrown in {@code
     * AbstractQueueVisitor}. For use in tests to check if an exception actually was thrown. Calling
     * {@code AbstractQueueVisitor#awaitExceptionForTestingOnly} can throw a spurious {@link
     * InterruptedException} because {@link CountDownLatch#await} checks the interrupted bit before
     * returning, even if the latch is already at 0. See bug "testTwoErrors is flaky".
     */
    CountDownLatch getExceptionLatchForTesting() {
      return visitor.getExceptionLatchForTestingOnly();
    }

    @Override
    public boolean inErrorBubblingForTesting() {
      return bubbleErrorInfo != null;
    }
  }

  private static final Function<ValueOrException<BottomException>, SkyValue> GET_VALUE_FROM_VOE =
      new Function<ValueOrException<BottomException>, SkyValue>() {
    @Override
    public SkyValue apply(ValueOrException<BottomException> voe) {
      return ValueOrExceptionUtils.downcovert(voe);
    }
  };

  private static <E extends Exception>
      Function<ValueOrException2<E, BottomException>, ValueOrException<E>>
      makeSafeDowncastToVOEFunction(final Class<E> exceptionClass) {
    return new Function<ValueOrException2<E, BottomException>, ValueOrException<E>>() {
      @Override
      public ValueOrException<E> apply(ValueOrException2<E, BottomException> voe) {
        return ValueOrExceptionUtils.downcovert(voe, exceptionClass);
      }
    };
  }

  private static <E1 extends Exception, E2 extends Exception>
      Function<ValueOrException3<E1, E2, BottomException>, ValueOrException2<E1, E2>>
      makeSafeDowncastToVOE2Function(final Class<E1> exceptionClass1,
      final Class<E2> exceptionClass2) {
    return new Function<ValueOrException3<E1, E2, BottomException>,
        ValueOrException2<E1, E2>>() {
      @Override
      public ValueOrException2<E1, E2> apply(ValueOrException3<E1, E2, BottomException> voe) {
        return ValueOrExceptionUtils.downconvert(voe, exceptionClass1, exceptionClass2);
      }
    };
  }

  private static <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      Function<ValueOrException4<E1, E2, E3, BottomException>, ValueOrException3<E1, E2, E3>>
      makeSafeDowncastToVOE3Function(final Class<E1> exceptionClass1,
          final Class<E2> exceptionClass2, final Class<E3> exceptionClass3) {
    return new Function<ValueOrException4<E1, E2, E3, BottomException>,
        ValueOrException3<E1, E2, E3>>() {
      @Override
      public ValueOrException3<E1, E2, E3> apply(ValueOrException4<E1, E2, E3,
          BottomException> voe) {
        return ValueOrExceptionUtils.downconvert(voe, exceptionClass1, exceptionClass2,
            exceptionClass3);
      }
    };
  }

  private class ValueVisitor extends AbstractQueueVisitor {
    private AtomicBoolean preventNewEvaluations = new AtomicBoolean(false);
    private final Set<SkyKey> inflightNodes = Sets.newConcurrentHashSet();

    private ValueVisitor(int threadCount) {
      super(/*concurrent*/true,
          threadCount,
          threadCount,
          1, TimeUnit.SECONDS,
          /*failFastOnException*/true,
          /*failFastOnInterrupt*/true,
          "skyframe-evaluator");
    }

    @Override
    protected boolean isCriticalError(Throwable e) {
      return e instanceof RuntimeException;
    }

    protected void waitForCompletion() throws InterruptedException {
      work(/*failFastOnInterrupt=*/true);
    }

    public void enqueueEvaluation(final SkyKey key) {
      // We unconditionally add the key to the set of in-flight nodes because even if evaluation is
      // never scheduled we still want to remove the previously created NodeEntry from the graph.
      // Otherwise we would leave the graph in a weird state (wasteful garbage in the best case and
      // inconsistent in the worst case).
      boolean newlyEnqueued = inflightNodes.add(key);
      // All nodes enqueued for evaluation will be either verified clean, re-evaluated, or cleaned
      // up after being in-flight when an error happens in nokeep_going mode or in the event of an
      // interrupt. In any of these cases, they won't be dirty anymore.
      if (newlyEnqueued) {
        dirtyKeyTracker.notDirty(key);
      }
      if (preventNewEvaluations.get()) {
        return;
      }
      if (newlyEnqueued && progressReceiver != null) {
        progressReceiver.enqueueing(key);
      }
      enqueue(new Evaluate(this, key));
    }

    public void preventNewEvaluations() {
      preventNewEvaluations.set(true);
    }

    public void notifyDone(SkyKey key) {
      inflightNodes.remove(key);
    }

    private boolean isInflight(SkyKey key) {
      return inflightNodes.contains(key);
    }
  }

  /**
   * An action that evaluates a value.
   */
  private class Evaluate implements Runnable {
    private final ValueVisitor visitor;
    /** The name of the value to be evaluated. */
    private final SkyKey skyKey;

    private Evaluate(ValueVisitor visitor, SkyKey skyKey) {
      this.visitor = visitor;
      this.skyKey = skyKey;
    }

    private void enqueueChild(SkyKey skyKey, NodeEntry entry, SkyKey child) {
      Preconditions.checkState(!entry.isDone(), "%s %s", skyKey, entry);
      Preconditions.checkState(!ErrorTransienceValue.key().equals(child),
          "%s cannot request ErrorTransienceValue as a dep: %s", skyKey, entry);

      NodeEntry depEntry = graph.createIfAbsent(child);
      switch (depEntry.addReverseDepAndCheckIfDone(skyKey)) {
        case DONE :
          if (entry.signalDep(depEntry.getVersion())) {
            // This can only happen if there are no more children to be added.
            visitor.enqueueEvaluation(skyKey);
          }
          break;
        case ADDED_DEP :
          break;
        case NEEDS_SCHEDULING :
          visitor.enqueueEvaluation(child);
          break;
      }
    }

    /**
     * Returns true if this depGroup consists of the error transience value and the error transience
     * value is newer than the entry, meaning that the entry must be re-evaluated.
     */
    private boolean invalidatedByErrorTransience(Collection<SkyKey> depGroup, NodeEntry entry) {
      return depGroup.size() == 1
          && depGroup.contains(ErrorTransienceValue.key())
          && !graph.get(ErrorTransienceValue.key()).getVersion().atMost(entry.getVersion());
    }

    @Override
    public void run() {
      NodeEntry state = graph.get(skyKey);
      Preconditions.checkNotNull(state, "%s %s", skyKey, state);
      Preconditions.checkState(state.isReady(), "%s %s", skyKey, state);

      if (state.isDirty()) {
        switch (state.getDirtyState()) {
          case CHECK_DEPENDENCIES:
            // Evaluating a dirty node for the first time, and checking its children to see if any
            // of them have changed. Note that there must be dirty children for this to happen.

            // Check the children group by group -- we don't want to evaluate a value that is no
            // longer needed because an earlier dependency changed. For example, //foo:foo depends
            // on target //bar:bar and is built. Then foo/BUILD is modified to remove the dependence
            // on bar, and bar/BUILD is deleted. Reloading //bar:bar would incorrectly throw an
            // exception. To avoid this, we must reload foo/BUILD first, at which point we will
            // discover that it has changed, and re-evaluate target //foo:foo from scratch.
            // On the other hand, when an action requests all of its inputs, we can safely check all
            // of them in parallel on a subsequent build. So we allow checking an entire group in
            // parallel here, if the node builder requested a group last build.
            Collection<SkyKey> directDepsToCheck = state.getNextDirtyDirectDeps();

            if (invalidatedByErrorTransience(directDepsToCheck, state)) {
              // If this dep is the ErrorTransienceValue and the ErrorTransienceValue has been
              // updated then we need to force a rebuild. We would like to just signal the entry as
              // usual, but we can't, because then the ErrorTransienceValue would remain as a dep,
              // which would be incorrect if, for instance, the value re-evaluated to a non-error.
              state.forceRebuild();
              break; // Fall through to re-evaluation.
            } else {
              // If this isn't the error transience value, it is safe to add these deps back to the
              // node -- even if one of them has changed, the contract of pruning is that the node
              // will request these deps again when it rebuilds. We must add these deps before
              // enqueuing them, so that the node knows that it depends on them.
              state.addTemporaryDirectDeps(GroupedListHelper.create(directDepsToCheck));
            }

            for (SkyKey directDep : directDepsToCheck) {
              enqueueChild(skyKey, state, directDep);
            }
            return;
          case VERIFIED_CLEAN:
            // No child has a changed value. This node can be marked done and its parents signaled
            // without any re-evaluation.
            visitor.notifyDone(skyKey);
            Set<SkyKey> reverseDeps = state.markClean();
            SkyValue value = state.getValue();
            if (progressReceiver != null) {
              // Tell the receiver that the value was not actually changed this run.
              progressReceiver.evaluated(skyKey, value, EvaluationState.CLEAN);
            }
            signalValuesAndEnqueueIfReady(visitor, reverseDeps, state.getVersion());
            return;
          case REBUILDING:
            // Nothing to be done if we are already rebuilding.
        }
      }

      // TODO(bazel-team): Once deps are requested in a deterministic order within a group, or the
      // framework is resilient to rearranging group order, change this so that
      // SkyFunctionEnvironment "follows along" as the node builder runs, iterating through the
      // direct deps that were requested on a previous run. This would allow us to avoid the
      // conversion of the direct deps into a set.
      Set<SkyKey> directDeps = state.getTemporaryDirectDeps();
      Preconditions.checkState(!directDeps.contains(ErrorTransienceValue.key()),
          "%s cannot have a dep on ErrorTransienceValue during building: %s", skyKey, state);
      // Get the corresponding SkyFunction and call it on this value.
      SkyFunctionEnvironment env = new SkyFunctionEnvironment(skyKey, directDeps, visitor);
      SkyFunctionName functionName = skyKey.functionName();
      SkyFunction factory = skyFunctions.get(functionName);
      Preconditions.checkState(factory != null, "%s %s", functionName, state);

      SkyValue value = null;
      Profiler.instance().startTask(ProfilerTask.SKYFUNCTION, skyKey);
      try {
        // TODO(bazel-team): count how many of these calls returns null vs. non-null
        value = factory.compute(skyKey, env);
      } catch (final SkyFunctionException builderException) {
        ReifiedSkyFunctionException reifiedBuilderException =
            new ReifiedSkyFunctionException(builderException, skyKey);
        // Propagated transitive errors are treated the same as missing deps.
        if (reifiedBuilderException.getRootCauseSkyKey().equals(skyKey)) {
          boolean shouldFailFast = !keepGoing || builderException.isCatastrophic();
          if (shouldFailFast) {
            // After we commit this error to the graph but before the eval call completes with the
            // error there is a race-like opportunity for the error to be used, either by an
            // in-flight computation or by a future computation.
            if (errorEncountered.compareAndSet(false, true)) {
              // This is the first error encountered.
              visitor.preventNewEvaluations();
            } else {
              // This is not the first error encountered, so we ignore it so that we can terminate
              // with the first error.
              return;
            }
          }

          registerNewlyDiscoveredDepsForDoneEntry(skyKey, state, env);
          ErrorInfo errorInfo = new ErrorInfo(reifiedBuilderException);
          env.setError(errorInfo);
          env.commit(/*enqueueParents=*/keepGoing);
          if (!shouldFailFast) {
            return;
          }
          throw SchedulerException.ofError(errorInfo, skyKey);
        }
      } catch (InterruptedException ie) {
        // InterruptedException cannot be thrown by Runnable.run, so we must wrap it.
        // Interrupts can be caught by both the Evaluator and the AbstractQueueVisitor.
        // The former will unwrap the IE and propagate it as is; the latter will throw a new IE.
        throw SchedulerException.ofInterruption(ie, skyKey);
      } catch (RuntimeException re) {
        // Programmer error (most likely NPE or a failed precondition in a SkyFunction). Output
        // some context together with the exception.
        String msg = prepareCrashMessage(skyKey, state.getInProgressReverseDeps());
        throw new RuntimeException(msg, re);
      } finally {
        env.doneBuilding();
        Profiler.instance().completeTask(ProfilerTask.SKYFUNCTION);
      }

      GroupedListHelper<SkyKey> newDirectDeps = env.newlyRequestedDeps;

      if (value != null) {
        Preconditions.checkState(!env.valuesMissing,
            "%s -> %s, ValueEntry: %s", skyKey, newDirectDeps, state);
        env.setValue(value);
        registerNewlyDiscoveredDepsForDoneEntry(skyKey, state, env);
        env.commit(/*enqueueParents=*/true);
        return;
      }

      if (!newDirectDeps.isEmpty() && env.getDepErrorKey() != null) {
        Preconditions.checkState(!keepGoing);
        // We encountered a child error in noKeepGoing mode, so we want to fail fast. But we first
        // need to add the edge between the current node and the child error it requested so that
        // error bubbling can occur. Note that this edge will subsequently be removed during graph
        // cleaning (since the current node will never be committed to the graph).
        SkyKey childErrorKey = env.getDepErrorKey();
        NodeEntry childErrorEntry = Preconditions.checkNotNull(graph.get(childErrorKey),
            "skyKey: %s, state: %s childErrorKey: %s", skyKey, state, childErrorKey);
        if (!state.getTemporaryDirectDeps().contains(childErrorKey)) {
          // This means the cached error was freshly requested (e.g. the parent has never been
          // built before).
          Preconditions.checkState(newDirectDeps.contains(childErrorKey), "%s %s %s", state,
              childErrorKey, newDirectDeps);
          state.addTemporaryDirectDeps(GroupedListHelper.create(ImmutableList.of(childErrorKey)));
          DependencyState childErrorState = childErrorEntry.addReverseDepAndCheckIfDone(skyKey);
          Preconditions.checkState(childErrorState == DependencyState.DONE,
              "skyKey: %s, state: %s childErrorKey: %s", skyKey, state, childErrorKey,
              childErrorEntry);
        } else {
          // This means the cached error was previously requested, and was then subsequently (after
          // a restart) requested along with another sibling dep. This can happen on an incremental
          // eval call when the parent is dirty and the child error is in a separate dependency
          // group from the sibling dep.
          Preconditions.checkState(!newDirectDeps.contains(childErrorKey), "%s %s %s", state,
              childErrorKey, newDirectDeps);
          Preconditions.checkState(childErrorEntry.isDone(),
              "skyKey: %s, state: %s childErrorKey: %s", skyKey, state, childErrorKey,
              childErrorEntry);
        }
        ErrorInfo childErrorInfo = Preconditions.checkNotNull(childErrorEntry.getErrorInfo());
        throw SchedulerException.ofError(childErrorInfo, childErrorKey);
      }

      // TODO(bazel-team): This code is not safe to interrupt, because we would lose the state in
      // newDirectDeps.

      // TODO(bazel-team): An ill-behaved SkyFunction can throw us into an infinite loop where we
      // add more dependencies on every run. [skyframe-core]

      // Add all new keys to the set of known deps.
      state.addTemporaryDirectDeps(newDirectDeps);

      // If there were no newly requested dependencies, at least one of them was in error or there
      // is a bug in the SkyFunction implementation. The environment has collected its errors, so we
      // just order it to be built.
      if (newDirectDeps.isEmpty()) {
        // TODO(bazel-team): This means a bug in the SkyFunction. What to do?
        Preconditions.checkState(!env.childErrorInfos.isEmpty(), "%s %s", skyKey, state);
        env.commit(/*enqueueParents=*/keepGoing);
        if (!keepGoing) {
          throw SchedulerException.ofError(state.getErrorInfo(), skyKey);
        }
        return;
      }

      for (SkyKey newDirectDep : newDirectDeps) {
        enqueueChild(skyKey, state, newDirectDep);
      }
      // It is critical that there is no code below this point.
    }

    private String prepareCrashMessage(SkyKey skyKey, Iterable<SkyKey> reverseDeps) {
      StringBuilder reverseDepDump = new StringBuilder();
      for (SkyKey key : reverseDeps) {
        if (reverseDepDump.length() > MAX_REVERSEDEP_DUMP_LENGTH) {
          reverseDepDump.append(", ...");
          break;
        }
        if (reverseDepDump.length() > 0) {
          reverseDepDump.append(", ");
        }
        reverseDepDump.append("'");
        reverseDepDump.append(key.toString());
        reverseDepDump.append("'");
      }

      return String.format(
          "Unrecoverable error while evaluating node '%s' (requested by nodes %s)",
          skyKey, reverseDepDump);
    }

    private static final int MAX_REVERSEDEP_DUMP_LENGTH = 1000;
  }

  /**
   * Signals all parents that this node is finished. If visitor is not null, also enqueues any
   * parents that are ready. If visitor is null, indicating that we are building this node after
   * the main build aborted, then skip any parents that are already done (that can happen with
   * cycles).
   */
  private void signalValuesAndEnqueueIfReady(@Nullable ValueVisitor visitor, Iterable<SkyKey> keys,
      Version version) {
    if (visitor != null) {
      for (SkyKey key : keys) {
        if (graph.get(key).signalDep(version)) {
          visitor.enqueueEvaluation(key);
        }
      }
    } else {
      for (SkyKey key : keys) {
        NodeEntry entry = Preconditions.checkNotNull(graph.get(key), key);
        if (!entry.isDone()) {
          // In cycles, we can have parents that are already done.
          entry.signalDep(version);
        }
      }
    }
  }

  /**
   * If child is not done, removes key from child's reverse deps. Returns whether child should be
   * removed from key's entry's direct deps.
   */
  private boolean removeIncompleteChild(SkyKey key, SkyKey child) {
    NodeEntry childEntry = graph.get(child);
    if (!isDoneForBuild(childEntry)) {
      childEntry.removeReverseDep(key);
      return true;
    }
    return false;
  }

  /**
   * Add any additional deps that were registered during the run of a builder that finished by
   * creating a node or throwing an error. Builders may throw errors even if all their deps were
   * not provided -- we trust that a SkyFunction may be know it should throw an error even if not
   * all of its requested deps are done. However, that means we're assuming the SkyFunction would
   * throw that same error if all of its requested deps were done. Unfortunately, there is no way to
   * enforce that condition.
   */
  private void registerNewlyDiscoveredDepsForDoneEntry(SkyKey skyKey, NodeEntry entry,
      SkyFunctionEnvironment env) {
    Set<SkyKey> unfinishedDeps = new HashSet<>();
    Iterables.addAll(unfinishedDeps,
        Iterables.filter(env.newlyRequestedDeps, Predicates.not(nodeEntryIsDone)));
    env.newlyRequestedDeps.remove(unfinishedDeps);
    entry.addTemporaryDirectDeps(env.newlyRequestedDeps);
    for (SkyKey newDep : env.newlyRequestedDeps) {
      NodeEntry depEntry = graph.get(newDep);
      DependencyState triState = depEntry.addReverseDepAndCheckIfDone(skyKey);
      Preconditions.checkState(DependencyState.DONE == triState,
          "new dep %s was not already done for %s. ValueEntry: %s. DepValueEntry: %s",
          newDep, skyKey, entry, depEntry);
      entry.signalDep();
    }
    Preconditions.checkState(entry.isReady(), "%s %s %s", skyKey, entry, env.newlyRequestedDeps);
  }

  private void informProgressReceiverThatValueIsDone(SkyKey key) {
    if (progressReceiver != null) {
      NodeEntry entry = graph.get(key);
      Preconditions.checkState(entry.isDone(), entry);
      SkyValue value = entry.getValue();
      Version valueVersion = entry.getVersion();
      Preconditions.checkState(valueVersion.atMost(graphVersion),
          "%s should be at most %s in the version partial ordering", valueVersion, graphVersion);
      // For most nodes we do not inform the progress receiver if they were already done when we
      // retrieve them, but top-level nodes are presumably of more interest.
      // If valueVersion is not equal to graphVersion, it must be less than it (by the
      // Preconditions check above), and so the node is clean.
      progressReceiver.evaluated(key, value, valueVersion.equals(graphVersion)
          ? EvaluationState.BUILT
          : EvaluationState.CLEAN);
    }
  }

  @Override
  @ThreadCompatible
  public <T extends SkyValue> EvaluationResult<T> eval(Iterable<SkyKey> skyKeys)
      throws InterruptedException {
    ImmutableSet<SkyKey> skyKeySet = ImmutableSet.copyOf(skyKeys);

    // Optimization: if all required node values are already present in the cache, return them
    // directly without launching the heavy machinery, spawning threads, etc.
    // Inform progressReceiver that these nodes are done to be consistent with the main code path.
    if (Iterables.all(skyKeySet, nodeEntryIsDone)) {
      for (SkyKey skyKey : skyKeySet) {
        informProgressReceiverThatValueIsDone(skyKey);
      }
      // Note that the 'catastrophe' parameter doesn't really matter here (it's only used for
      // sanity checking).
      return constructResult(null, skyKeySet, null, /*catastrophe=*/false);
    }

    if (!keepGoing) {
      Set<SkyKey> cachedErrorKeys = new HashSet<>();
      for (SkyKey skyKey : skyKeySet) {
        NodeEntry entry = graph.get(skyKey);
        if (entry == null) {
          continue;
        }
        if (entry.isDone() && entry.getErrorInfo() != null) {
          informProgressReceiverThatValueIsDone(skyKey);
          cachedErrorKeys.add(skyKey);
        }
      }

      // Errors, even cached ones, should halt evaluations not in keepGoing mode.
      if (!cachedErrorKeys.isEmpty()) {
        // Note that the 'catastrophe' parameter doesn't really matter here (it's only used for
        // sanity checking).
        return constructResult(null, cachedErrorKeys, null, /*catastrophe=*/false);
      }
    }

    // We delay this check until we know that some kind of evaluation is necessary, since !keepGoing
    // and !keepsEdges are incompatible only in the case of a failed evaluation -- there is no
    // need to be overly harsh to callers who are just trying to retrieve a cached result.
    Preconditions.checkState(keepGoing || !(graph instanceof InMemoryGraph)
        || ((InMemoryGraph) graph).keepsEdges(),
        "nokeep_going evaluations are not allowed if graph edges are not kept: %s", skyKeys);

    Profiler.instance().startTask(ProfilerTask.SKYFRAME_EVAL, skyKeySet);
    try {
      return eval(skyKeySet, new ValueVisitor(threadCount));
    } finally {
      Profiler.instance().completeTask(ProfilerTask.SKYFRAME_EVAL);
    }
  }

  @ThreadCompatible
  private <T extends SkyValue> EvaluationResult<T> eval(ImmutableSet<SkyKey> skyKeys,
      ValueVisitor visitor) throws InterruptedException {
    // We unconditionally add the ErrorTransienceValue here, to ensure that it will be created, and
    // in the graph, by the time that it is needed. Creating it on demand in a parallel context sets
    // up a race condition, because there is no way to atomically create a node and set its value.
    NodeEntry errorTransienceEntry = graph.createIfAbsent(ErrorTransienceValue.key());
    DependencyState triState = errorTransienceEntry.addReverseDepAndCheckIfDone(null);
    Preconditions.checkState(triState != DependencyState.ADDED_DEP,
        "%s %s", errorTransienceEntry, triState);
    if (triState != DependencyState.DONE) {
      errorTransienceEntry.setValue(new ErrorTransienceValue(), graphVersion);
      // The error transience entry is always invalidated by the RecordingDifferencer.
      // Now that the entry's value is set, it is no longer dirty.
      dirtyKeyTracker.notDirty(ErrorTransienceValue.key());

      Preconditions.checkState(
          errorTransienceEntry.addReverseDepAndCheckIfDone(null) != DependencyState.ADDED_DEP,
          errorTransienceEntry);
    }
    for (SkyKey skyKey : skyKeys) {
      NodeEntry entry = graph.createIfAbsent(skyKey);
      // This must be equivalent to the code in enqueueChild above, in order to be thread-safe.
      switch (entry.addReverseDepAndCheckIfDone(null)) {
        case NEEDS_SCHEDULING:
          visitor.enqueueEvaluation(skyKey);
          break;
        case DONE:
          informProgressReceiverThatValueIsDone(skyKey);
          break;
        case ADDED_DEP:
          break;
        default:
          throw new IllegalStateException(entry + " for " + skyKey + " in unknown state");
      }
    }
    try {
      return waitForCompletionAndConstructResult(visitor, skyKeys);
    } finally {
      // TODO(bazel-team): In nokeep_going mode or in case of an interrupt, we need to remove
      // partial values from the graph. Find a better way to handle those cases.
      clean(visitor.inflightNodes);
    }
  }

  private void clean(Set<SkyKey> inflightNodes) throws InterruptedException {
    boolean alreadyInterrupted = Thread.interrupted();
    // This parallel computation is fully cpu-bound, so we use a thread for each processor.
    ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactoryBuilder().setNameFormat("ParallelEvaluator#clean %d").build());
    ThrowableRecordingRunnableWrapper wrapper =
        new ThrowableRecordingRunnableWrapper("ParallelEvaluator#clean");
    for (final SkyKey key : inflightNodes) {
      final NodeEntry entry = graph.get(key);
      if (entry.isDone()) {
        // Entry may be done in case of a RuntimeException or other programming bug. Do nothing,
        // since (a) we're about to crash anyway, and (b) getTemporaryDirectDeps cannot be called
        // on a done node, so the call below would crash, which would mask the actual exception
        // that caused this state.
        continue;
      }
      executor.execute(wrapper.wrap(new Runnable() {
        @Override
        public void run() {
          cleanInflightNode(key, entry);
        }
      }));
    }
    // We uninterruptibly wait for all nodes to be cleaned because we want to make sure the graph
    // is left in a good state.
    //
    // TODO(bazel-team): Come up with a better design for graph cleaning such that we can respond
    // to interrupts in constant time.
    boolean newlyInterrupted = ExecutorShutdownUtil.uninterruptibleShutdown(executor);
    Throwables.propagateIfPossible(wrapper.getFirstThrownError());
    if (newlyInterrupted || alreadyInterrupted) {
      throw new InterruptedException();
    }
  }

  private void cleanInflightNode(SkyKey key, NodeEntry entry) {
    Set<SkyKey> temporaryDeps = entry.getTemporaryDirectDeps();
    graph.remove(key);
    for (SkyKey dep : temporaryDeps) {
      NodeEntry nodeEntry = graph.get(dep);
      // The direct dep might have already been cleaned from the graph.
      if (nodeEntry != null) {
        // Only bother removing the reverse dep on done nodes since other in-flight nodes will be
        // cleaned too.
        if (nodeEntry.isDone()) {
          nodeEntry.removeReverseDep(key);
        }
      }
    }
  }

  private <T extends SkyValue> EvaluationResult<T> waitForCompletionAndConstructResult(
      ValueVisitor visitor, Iterable<SkyKey> skyKeys) throws InterruptedException {
    Map<SkyKey, ValueWithMetadata> bubbleErrorInfo = null;
    boolean catastrophe = false;
    try {
      visitor.waitForCompletion();
    } catch (final SchedulerException e) {
      Throwables.propagateIfPossible(e.getCause(), InterruptedException.class);
      if (Thread.interrupted()) {
        // As per the contract of AbstractQueueVisitor#work, if an unchecked exception is thrown and
        // the build is interrupted, the thrown exception is what will be rethrown. Since the user
        // presumably wanted to interrupt the build, we ignore the thrown SchedulerException (which
        // doesn't indicate a programming bug) and throw an InterruptedException.
        throw new InterruptedException();
      }

      SkyKey errorKey = Preconditions.checkNotNull(e.getFailedValue(), e);
      // ErrorInfo could only be null if SchedulerException wrapped an InterruptedException, but
      // that should have been propagated.
      ErrorInfo errorInfo = Preconditions.checkNotNull(e.getErrorInfo(), errorKey);
      catastrophe = errorInfo.isCatastrophic();
      if (!catastrophe || !keepGoing) {
        bubbleErrorInfo = bubbleErrorUp(errorInfo, errorKey, skyKeys, visitor);
      } else {
        // Bubbling the error up requires that graph edges are present for done nodes. This is not
        // always the case in a keepGoing evaluation, since it is assumed that done nodes do not
        // need to be traversed. In this case, we hope the caller is tolerant of a possibly empty
        // result, and return prematurely.
        bubbleErrorInfo = ImmutableMap.of(errorKey, graph.get(errorKey).getValueWithMetadata());
      }
    }

    // Successful evaluation, either because keepGoing or because we actually did succeed.
    // TODO(bazel-team): Maybe report root causes during the build for lower latency.
    return constructResult(visitor, skyKeys, bubbleErrorInfo, catastrophe);
  }

  /**
   * Walk up graph to find a top-level node (without parents) that wanted this failure. Store
   * the failed nodes along the way in a map, with ErrorInfos that are appropriate for that layer.
   * Example:
   *                      foo   bar
   *                        \   /
   *           unrequested   baz
   *                     \    |
   *                      failed-node
   * User requests foo, bar. When failed-node fails, we look at its parents. unrequested is not
   * in-flight, so we replace failed-node by baz and repeat. We look at baz's parents. foo is
   * in-flight, so we replace baz by foo. Since foo is a top-level node and doesn't have parents,
   * we then break, since we know a top-level node, foo, that depended on the failed node.
   *
   * There's the potential for a weird "track jump" here in the case:
   *                        foo
   *                       / \
   *                   fail1 fail2
   * If fail1 and fail2 fail simultaneously, fail2 may start propagating up in the loop below.
   * However, foo requests fail1 first, and then throws an exception based on that. This is not
   * incorrect, but may be unexpected.
   *
   * <p>Returns a map of errors that have been constructed during the bubbling up, so that the
   * appropriate error can be returned to the caller, even though that error was not written to the
   * graph. If a cycle is detected during the bubbling, this method aborts and returns null so that
   * the normal cycle detection can handle the cycle.
   *
   * <p>Note that we are not propagating error to the first top-level node but to the highest one,
   * because during this process we can add useful information about error from other nodes.
   */
  private Map<SkyKey, ValueWithMetadata> bubbleErrorUp(final ErrorInfo leafFailure,
      SkyKey errorKey, Iterable<SkyKey> skyKeys, ValueVisitor visitor) {
    Set<SkyKey> rootValues = ImmutableSet.copyOf(skyKeys);
    ErrorInfo error = leafFailure;
    Map<SkyKey, ValueWithMetadata> bubbleErrorInfo = new HashMap<>();
    boolean externalInterrupt = false;
    while (true) {
      NodeEntry errorEntry = graph.get(errorKey);
      Iterable<SkyKey> reverseDeps = errorEntry.isDone()
          ? errorEntry.getReverseDeps()
          : errorEntry.getInProgressReverseDeps();
      // We should break from loop only when node doesn't have any parents.
      if (Iterables.isEmpty(reverseDeps)) {
        Preconditions.checkState(rootValues.contains(errorKey),
            "Current key %s has to be a top-level key: %s", errorKey, rootValues);
        break;
      }
      SkyKey parent = null;
      NodeEntry parentEntry = null;
      for (SkyKey bubbleParent : reverseDeps) {
        if (bubbleErrorInfo.containsKey(bubbleParent)) {
          // We are in a cycle. Don't try to bubble anything up -- cycle detection will kick in.
          return null;
        }
        NodeEntry bubbleParentEntry = Preconditions.checkNotNull(graph.get(bubbleParent),
            "parent %s of %s not in graph", bubbleParent, errorKey);
        // Might be the parent that requested the error.
        if (bubbleParentEntry.isDone()) {
          // This parent is cached from a previous evaluate call. We shouldn't bubble up to it
          // since any error message produced won't be meaningful to this evaluate call.
          // The child error must also be cached from a previous build.
          Preconditions.checkState(errorEntry.isDone(), "%s %s", errorEntry, bubbleParentEntry);
          Version parentVersion = bubbleParentEntry.getVersion();
          Version childVersion = errorEntry.getVersion();
          Preconditions.checkState(childVersion.atMost(graphVersion)
              && !childVersion.equals(graphVersion),
              "child entry is not older than the current graph version, but had a done parent. "
              + "child: %s childEntry: %s, childVersion: %s"
              + "bubbleParent: %s bubbleParentEntry: %s, parentVersion: %s, graphVersion: %s",
              errorKey, errorEntry, childVersion,
              bubbleParent, bubbleParentEntry, parentVersion, graphVersion);
          Preconditions.checkState(parentVersion.atMost(graphVersion)
              && !parentVersion.equals(graphVersion),
              "parent entry is not older than the current graph version. "
              + "child: %s childEntry: %s, childVersion: %s"
              + "bubbleParent: %s bubbleParentEntry: %s, parentVersion: %s, graphVersion: %s",
              errorKey, errorEntry, childVersion,
              bubbleParent, bubbleParentEntry, parentVersion, graphVersion);
          continue;
        }
        // Arbitrarily pick the first in-flight parent.
        Preconditions.checkState(visitor.isInflight(bubbleParent),
            "errorKey: %s, errorEntry: %s, bubbleParent: %s, bubbleParentEntry: %s", errorKey,
            errorEntry, bubbleParent, bubbleParentEntry);
        parent = bubbleParent;
        parentEntry = bubbleParentEntry;
        break;
      }
      Preconditions.checkNotNull(parent, "", errorKey, bubbleErrorInfo);
      errorKey = parent;
      SkyFunction factory = skyFunctions.get(parent.functionName());
      if (parentEntry.isDirty()) {
        switch (parentEntry.getDirtyState()) {
          case CHECK_DEPENDENCIES:
            // If this value's child was bubbled up to, it did not signal this value, and so we must
            // manually make it ready to build.
            parentEntry.signalDep();
            // Fall through to REBUILDING, since state is now REBUILDING.
          case REBUILDING:
            // Nothing to be done.
            break;
          default:
            throw new AssertionError(parent + " not in valid dirty state: " + parentEntry);
        }
      }
      SkyFunctionEnvironment env =
          new SkyFunctionEnvironment(parent, parentEntry.getTemporaryDirectDeps(),
              bubbleErrorInfo, visitor);
      externalInterrupt = externalInterrupt || Thread.currentThread().isInterrupted();
      try {
        // This build is only to check if the parent node can give us a better error. We don't
        // care about a return value.
        factory.compute(parent, env);
      } catch (SkyFunctionException builderException) {
        ReifiedSkyFunctionException reifiedBuilderException =
            new ReifiedSkyFunctionException(builderException, parent);
        if (reifiedBuilderException.getRootCauseSkyKey().equals(parent)) {
          error = new ErrorInfo(reifiedBuilderException);
          bubbleErrorInfo.put(errorKey,
              ValueWithMetadata.error(new ErrorInfo(errorKey, ImmutableSet.of(error)),
                  env.buildEvents(/*missingChildren=*/true)));
          continue;
        }
      } catch (InterruptedException interruptedException) {
        // Do nothing.
        // This throw happens if the builder requested the failed node, and then checked the
        // interrupted state later -- getValueOrThrow sets the interrupted bit after the failed
        // value is requested, to prevent the builder from doing too much work.
      } finally {
        // Clear interrupted status. We're not listening to interrupts here.
        Thread.interrupted();
      }
      // Builder didn't throw an exception, so just propagate this one up.
      bubbleErrorInfo.put(errorKey,
          ValueWithMetadata.error(new ErrorInfo(errorKey, ImmutableSet.of(error)),
              env.buildEvents(/*missingChildren=*/true)));
    }

    // Reset the interrupt bit if there was an interrupt from outside this evaluator interrupt.
    // Note that there are internal interrupts set in the node builder environment if an error
    // bubbling node calls getValueOrThrow() on a node in error.
    if (externalInterrupt) {
      Thread.currentThread().interrupt();
    }
    return bubbleErrorInfo;
  }

  /**
   * Constructs an {@link EvaluationResult} from the {@link #graph}.  Looks for cycles if there
   * are unfinished nodes but no error was already found through bubbling up
   * (as indicated by {@code bubbleErrorInfo} being null).
   *
   * <p>{@code visitor} may be null, but only in the case where all graph entries corresponding to
   * {@code skyKeys} are known to be in the DONE state ({@code entry.isDone()} returns true).
   */
  private <T extends SkyValue> EvaluationResult<T> constructResult(
      @Nullable ValueVisitor visitor, Iterable<SkyKey> skyKeys,
      Map<SkyKey, ValueWithMetadata> bubbleErrorInfo, boolean catastrophe) {
    Preconditions.checkState(!keepGoing || catastrophe || bubbleErrorInfo == null,
        "", skyKeys, bubbleErrorInfo);
    EvaluationResult.Builder<T> result = EvaluationResult.builder();
    List<SkyKey> cycleRoots = new ArrayList<>();
    boolean hasError = false;
    for (SkyKey skyKey : skyKeys) {
      ValueWithMetadata valueWithMetadata = getValueMaybeFromError(skyKey, bubbleErrorInfo);
      // Cycle checking: if there is a cycle, evaluation cannot progress, therefore,
      // the final values will not be in DONE state when the work runs out.
      if (valueWithMetadata == null) {
        // Don't look for cycles if the build failed for a known reason.
        if (bubbleErrorInfo == null) {
          cycleRoots.add(skyKey);
        }
        hasError = true;
        continue;
      }
      SkyValue value = valueWithMetadata.getValue();
      // TODO(bazel-team): Verify that message replay is fast and works in failure
      // modes [skyframe-core]
      // Note that replaying events here is only necessary on null builds, because otherwise we
      // would have already printed the transitive messages after building these values.
      replayingNestedSetEventVisitor.visit(valueWithMetadata.getTransitiveEvents());
      ErrorInfo errorInfo = valueWithMetadata.getErrorInfo();
      Preconditions.checkState(value != null || errorInfo != null, skyKey);
      hasError = hasError || (errorInfo != null);
      if (!keepGoing && errorInfo != null) {
        // value will be null here unless the value was already built on a prior keepGoing build.
        result.addError(skyKey, errorInfo);
        continue;
      }
      if (value == null) {
        // Note that we must be in the keepGoing case. Only make this value an error if it doesn't
        // have a value. The error shouldn't matter to the caller since the value succeeded after a
        // fashion.
        result.addError(skyKey, errorInfo);
      } else {
        result.addResult(skyKey, value);
      }
    }
    if (!cycleRoots.isEmpty()) {
      Preconditions.checkState(visitor != null, skyKeys);
      checkForCycles(cycleRoots, result, visitor, keepGoing);
    }
    Preconditions.checkState(bubbleErrorInfo == null || hasError,
        "If an error bubbled up, some top-level node must be in error", bubbleErrorInfo, skyKeys);
    result.setHasError(hasError);
    return result.build();
  }

  private <T extends SkyValue> void checkForCycles(
      Iterable<SkyKey> badRoots, EvaluationResult.Builder<T> result, final ValueVisitor visitor,
      boolean keepGoing) {
    for (SkyKey root : badRoots) {
      ErrorInfo errorInfo = checkForCycles(root, visitor, keepGoing);
      if (errorInfo == null) {
        // This node just wasn't finished when evaluation aborted -- there were no cycles below it.
        Preconditions.checkState(!keepGoing, "", root, badRoots);
        continue;
      }
      Preconditions.checkState(!Iterables.isEmpty(errorInfo.getCycleInfo()),
          "%s was not evaluated, but was not part of a cycle", root);
      result.addError(root, errorInfo);
      if (!keepGoing) {
        return;
      }
    }
  }

  /**
   * Marker value that we push onto a stack before we push a node's children on. When the marker
   * value is popped, we know that all the children are finished. We would use null instead, but
   * ArrayDeque does not permit null elements.
   */
  private static final SkyKey CHILDREN_FINISHED =
      new SkyKey(new SkyFunctionName("MARKER", false), "MARKER");

  /** The max number of cycles we will report to the user for a given root, to avoid OOMing. */
  private static final int MAX_CYCLES = 20;

  /**
   * The algorithm for this cycle detector is as follows. We visit the graph depth-first, keeping
   * track of the path we are currently on. We skip any DONE nodes (they are transitively
   * error-free). If we come to a node already on the path, we immediately construct a cycle. If
   * we are in the noKeepGoing case, we return ErrorInfo with that cycle to the caller. Otherwise,
   * we continue. Once all of a node's children are done, we construct an error value for it, based
   * on those children. Finally, when the original root's node is constructed, we return its
   * ErrorInfo.
   */
  private ErrorInfo checkForCycles(SkyKey root, ValueVisitor visitor, boolean keepGoing) {
    // The number of cycles found. Do not keep on searching for more cycles after this many were
    // found.
    int cyclesFound = 0;
    // The path through the graph currently being visited.
    List<SkyKey> graphPath = new ArrayList<>();
    // Set of nodes on the path, to avoid expensive searches through the path for cycles.
    Set<SkyKey> pathSet = new HashSet<>();

    // Maintain a stack explicitly instead of recursion to avoid stack overflows
    // on extreme graphs (with long dependency chains).
    Deque<SkyKey> toVisit = new ArrayDeque<>();

    toVisit.push(root);

    // The procedure for this check is as follows: we visit a node, push it onto the graph stack,
    // push a marker value onto the toVisit stack, and then push all of its children onto the
    // toVisit stack. Thus, when the marker node comes to the top of the toVisit stack, we have
    // visited the downward transitive closure of the value. At that point, all of its children must
    // be finished, and so we can build the definitive error info for the node, popping it off the
    // graph stack.
    while (!toVisit.isEmpty()) {
      SkyKey key = toVisit.pop();
      NodeEntry entry = graph.get(key);

      if (key == CHILDREN_FINISHED) {
        // A marker node means we are done with all children of a node. Since all nodes have
        // errors, we must have found errors in the children when that happens.
        key = graphPath.remove(graphPath.size() - 1);
        entry = graph.get(key);
        pathSet.remove(key);
        // Skip this node if it was first/last node of a cycle, and so has already been processed.
        if (entry.isDone()) {
          continue;
        }
        if (!keepGoing) {
          // in the --nokeep_going mode, we would have already returned if we'd found a cycle below
          // this node. The fact that we haven't means that there were no cycles below this node
          // -- it just hadn't finished evaluating. So skip it.
          continue;
        }
        if (cyclesFound < MAX_CYCLES) {
          // Value must be ready, because all of its children have finished, so we can build its
          // error.
          Preconditions.checkState(entry.isReady(), "%s not ready. ValueEntry: %s", key, entry);
        } else if (!entry.isReady()) {
          removeIncompleteChildrenForCycle(key, entry, entry.getTemporaryDirectDeps());
        }
        Set<SkyKey> directDeps = entry.getTemporaryDirectDeps();
        // Find out which children have errors. Similar logic to that in Evaluate#run().
        List<ErrorInfo> errorDeps = getChildrenErrorsForCycle(directDeps);
        Preconditions.checkState(!errorDeps.isEmpty(),
            "Value %s was not successfully evaluated, but had no child errors. ValueEntry: %s", key,
            entry);
        SkyFunctionEnvironment env = new SkyFunctionEnvironment(key, directDeps, visitor);
        env.setError(new ErrorInfo(key, errorDeps));
        env.commit(/*enqueueParents=*/false);
      }

      // Nothing to be done for this node if it already has an entry.
      if (entry.isDone()) {
        continue;
      }
      if (cyclesFound == MAX_CYCLES) {
        // Do not keep on searching for cycles indefinitely, to avoid excessive runtime/OOMs.
        continue;
      }

      if (pathSet.contains(key)) {
        int cycleStart = graphPath.indexOf(key);
        // Found a cycle!
        cyclesFound++;
        Iterable<SkyKey> cycle = graphPath.subList(cycleStart, graphPath.size());
        // Put this node into a consistent state for building if it is dirty.
        if (entry.isDirty() && entry.getDirtyState() == NodeEntry.DirtyState.CHECK_DEPENDENCIES) {
          // In the check deps state, entry has exactly one child not done yet. Note that this node
          // must be part of the path to the cycle we have found (since done nodes cannot be in
          // cycles, and this is the only missing one). Thus, it will not be removed below in
          // removeDescendantsOfCycleValue, so it is safe here to signal that it is done.
          entry.signalDep();
        }
        if (keepGoing) {
          // Any children of this node that we haven't already visited are not worth visiting,
          // since this node is about to be done. Thus, the only child worth visiting is the one in
          // this cycle, the cycleChild (which may == key if this cycle is a self-edge).
          SkyKey cycleChild = selectCycleChild(key, graphPath, cycleStart);
          removeDescendantsOfCycleValue(key, entry, cycleChild, toVisit,
                  graphPath.size() - cycleStart);
          ValueWithMetadata dummyValue = ValueWithMetadata.wrapWithMetadata(new SkyValue() {});


          SkyFunctionEnvironment env =
              new SkyFunctionEnvironment(key, entry.getTemporaryDirectDeps(),
                  ImmutableMap.of(cycleChild, dummyValue), visitor);

          // Construct error info for this node. Get errors from children, which are all done
          // except possibly for the cycleChild.
          List<ErrorInfo> allErrors =
              getChildrenErrors(entry.getTemporaryDirectDeps(), /*unfinishedChild=*/cycleChild);
          CycleInfo cycleInfo = new CycleInfo(cycle);
          // Add in this cycle.
          allErrors.add(new ErrorInfo(cycleInfo));
          env.setError(new ErrorInfo(key, allErrors));
          env.commit(/*enqueueParents=*/false);
          continue;
        } else {
          // We need to return right away in the noKeepGoing case, so construct the cycle (with the
          // path) and return.
          Preconditions.checkState(graphPath.get(0).equals(root),
              "%s not reached from %s. ValueEntry: %s", key, root, entry);
          return new ErrorInfo(new CycleInfo(graphPath.subList(0, cycleStart), cycle));
        }
      }

      // This node is not yet known to be in a cycle. So process its children.
      Iterable<? extends SkyKey> children = graph.get(key).getTemporaryDirectDeps();
      if (Iterables.isEmpty(children)) {
        continue;
      }

      // This marker flag will tell us when all this node's children have been processed.
      toVisit.push(CHILDREN_FINISHED);
      // This node is now part of the path through the graph.
      graphPath.add(key);
      pathSet.add(key);
      for (SkyKey nextValue : children) {
        toVisit.push(nextValue);
      }
    }
    return keepGoing ? getAndCheckDone(root).getErrorInfo() : null;
  }

  /**
   * Returns the child of this node that is in the cycle that was just found. If the cycle is a
   * self-edge, returns the node itself.
   */
  private static SkyKey selectCycleChild(SkyKey key, List<SkyKey> graphPath, int cycleStart) {
    return cycleStart + 1 == graphPath.size() ? key : graphPath.get(cycleStart + 1);
  }

  /**
   * Get all the errors of child nodes. There must be at least one cycle amongst them.
   *
   * @param children child nodes to query for errors.
   * @return List of ErrorInfos from all children that had errors.
   */
  private List<ErrorInfo> getChildrenErrorsForCycle(Iterable<SkyKey> children) {
    List<ErrorInfo> allErrors = new ArrayList<>();
    boolean foundCycle = false;
    for (SkyKey child : children) {
      ErrorInfo errorInfo = getAndCheckDone(child).getErrorInfo();
      if (errorInfo != null) {
        foundCycle |= !Iterables.isEmpty(errorInfo.getCycleInfo());
        allErrors.add(errorInfo);
      }
    }
    Preconditions.checkState(foundCycle, "", children, allErrors);
    return allErrors;
  }

  /**
   * Get all the errors of child nodes.
   *
   * @param children child nodes to query for errors.
   * @param unfinishedChild child which is allowed to not be done.
   * @return List of ErrorInfos from all children that had errors.
   */
  private List<ErrorInfo> getChildrenErrors(Iterable<SkyKey> children, SkyKey unfinishedChild) {
    List<ErrorInfo> allErrors = new ArrayList<>();
    for (SkyKey child : children) {
      ErrorInfo errorInfo = getErrorMaybe(child, /*allowUnfinished=*/child.equals(unfinishedChild));
      if (errorInfo != null) {
        allErrors.add(errorInfo);
      }
    }
    return allErrors;
  }

  @Nullable
  private ErrorInfo getErrorMaybe(SkyKey key, boolean allowUnfinished) {
    if (!allowUnfinished) {
      return getAndCheckDone(key).getErrorInfo();
    }
    NodeEntry entry = Preconditions.checkNotNull(graph.get(key), key);
    return entry.isDone() ? entry.getErrorInfo() : null;
  }

  /**
   * Removes direct children of key from toVisit and from the entry itself, and makes the entry
   * ready if necessary. We must do this because it would not make sense to try to build the
   * children after building the entry. It would violate the invariant that a parent can only be
   * built after its children are built; See bug "Precondition error while evaluating a Skyframe
   * graph with a cycle".
   *
   * @param key SkyKey of node in a cycle.
   * @param entry NodeEntry of node in a cycle.
   * @param cycleChild direct child of key in the cycle, or key itself if the cycle is a self-edge.
   * @param toVisit list of remaining nodes to visit by the cycle-checker.
   * @param cycleLength the length of the cycle found.
   */
  private void removeDescendantsOfCycleValue(SkyKey key, NodeEntry entry,
      @Nullable SkyKey cycleChild, Iterable<SkyKey> toVisit, int cycleLength) {
    Set<SkyKey> unvisitedDeps = new HashSet<>(entry.getTemporaryDirectDeps());
    unvisitedDeps.remove(cycleChild);
    // Remove any children from this node that are not part of the cycle we just found. They are
    // irrelevant to the node as it stands, and if they are deleted from the graph because they are
    // not built by the end of cycle-checking, we would have dangling references.
    removeIncompleteChildrenForCycle(key, entry, unvisitedDeps);
    if (!entry.isReady()) {
      // The entry has at most one undone dep now, its cycleChild. Signal to make entry ready. Note
      // that the entry can conceivably be ready if its cycleChild already found a different cycle
      // and was built.
      entry.signalDep();
    }
    Preconditions.checkState(entry.isReady(), "%s %s %s", key, cycleChild, entry);
    Iterator<SkyKey> it = toVisit.iterator();
    while (it.hasNext()) {
      SkyKey descendant = it.next();
      if (descendant == CHILDREN_FINISHED) {
        // Marker value, delineating the end of a group of children that were enqueued.
        cycleLength--;
        if (cycleLength == 0) {
          // We have seen #cycleLength-1 marker values, and have arrived at the one for this value,
          // so we are done.
          return;
        }
        continue; // Don't remove marker values.
      }
      if (cycleLength == 1) {
        // Remove the direct children remaining to visit of the cycle node.
        Preconditions.checkState(unvisitedDeps.contains(descendant),
            "%s %s %s %s %s", key, descendant, cycleChild, unvisitedDeps, entry);
        it.remove();
      }
    }
    throw new IllegalStateException("There were not " + cycleLength + " groups of children in "
        + toVisit + " when trying to remove children of " + key + " other than " + cycleChild);
  }

  private void removeIncompleteChildrenForCycle(SkyKey key, NodeEntry entry,
      Iterable<SkyKey> children) {
    Set<SkyKey> unfinishedDeps = new HashSet<>();
    for (SkyKey child : children) {
      if (removeIncompleteChild(key, child)) {
        unfinishedDeps.add(child);
      }
    }
    entry.removeUnfinishedDeps(unfinishedDeps);
  }

  private NodeEntry getAndCheckDone(SkyKey key) {
    NodeEntry entry = graph.get(key);
    Preconditions.checkNotNull(entry, key);
    Preconditions.checkState(entry.isDone(), "%s %s", key, entry);
    return entry;
  }

  private ValueWithMetadata getValueMaybeFromError(SkyKey key,
      @Nullable Map<SkyKey, ValueWithMetadata> bubbleErrorInfo) {
    SkyValue value = bubbleErrorInfo == null ? null : bubbleErrorInfo.get(key);
    NodeEntry entry = graph.get(key);
    if (value != null) {
      Preconditions.checkNotNull(entry,
          "Value cannot have error before evaluation started", key, value);
      return ValueWithMetadata.wrapWithMetadata(value);
    }
    return isDoneForBuild(entry) ? entry.getValueWithMetadata() : null;
  }

  /**
   * Return true if the entry does not need to be re-evaluated this build. The entry will need to
   * be re-evaluated if it is not done, but also if it was not completely evaluated last build and
   * this build is keepGoing.
   */
  private boolean isDoneForBuild(@Nullable NodeEntry entry) {
    return entry != null && entry.isDone();
  }
}
