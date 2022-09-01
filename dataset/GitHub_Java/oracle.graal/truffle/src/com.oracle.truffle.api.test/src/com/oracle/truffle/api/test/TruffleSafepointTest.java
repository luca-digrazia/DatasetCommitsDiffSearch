/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test;

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class TruffleSafepointTest {

    private static final Method SUBMIT_INTERNAL = ReflectionUtils.requireDeclaredMethod(TruffleLanguage.Env.class, "submitThreadLocalInternal", null);

    private static final int[] THREAD_CONFIGS = new int[]{1, 4, 16};
    private static final int[] ITERATION_CONFIGS = new int[]{1, 8, 32};
    private static ExecutorService service;
    private static final AtomicBoolean CANCELLED = new AtomicBoolean();

    private static final int TIMEOUT_SECONDS = 60;
    private static final boolean VERBOSE = false;
    /*
     * Rerun all thread configurations asynchronously. This flag is intended to be used for
     * debugging failures in this class.
     */
    private static final boolean RERUN_THREAD_CONFIG_ASYNC = true;

    @Rule public TestName name = new TestName();

    @BeforeClass
    public static void beforeClass() {
        service = Executors.newFixedThreadPool(Integer.MAX_VALUE);
        CANCELLED.set(false);
    }

    @AfterClass
    public static void afterClass() throws InterruptedException {
        if (service != null) {
            CANCELLED.set(true);
            service.shutdown();
            if (!service.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw failTimeout(null);
            }
        }
    }

    private long testStarted;

    @Before
    public void before() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });
        if (VERBOSE) {
            System.out.println();
            System.out.print(name.getMethodName() + ":");
            testStarted = System.currentTimeMillis();
        }
    }

    @After
    public void after() {
        ProxyLanguage.setDelegate(new ProxyLanguage());
        if (VERBOSE) {
            System.out.println((System.currentTimeMillis() - testStarted) + "ms");
        }
    }

    @Test
    public void testNullArgs() {
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            TruffleSafepoint.poll(node);
            return false;
        })) {
            assertFails(() -> setup.env.submitThreadLocal(new Thread[]{null}, new ThreadLocalAction(true, true) {
                @Override
                protected void perform(Access access) {
                }
            }), NullPointerException.class);
            assertFails(() -> setup.env.submitThreadLocal(null, null), NullPointerException.class);
        }
    }

    @Test
    public void testSynchronousRecursiveError() throws InterruptedException, AssertionError, ExecutionException {
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            TruffleSafepoint.poll(node);
            return false;
        })) {
            Future<Boolean> future = setup.futures.get(0);
            AtomicBoolean error = new AtomicBoolean();
            setup.env.submitThreadLocal(null, new ThreadLocalAction(true, true) {
                @Override
                protected void perform(Access outer) {
                    ThreadLocalAction action = new ThreadLocalAction(true, true) {
                        @Override
                        protected void perform(Access inner) {
                        }
                    };
                    try {
                        setup.env.submitThreadLocal(null, action);
                        fail();
                    } catch (IllegalStateException e) {
                        /*
                         * Synchronous inner safe point scheduling is disallowed as this is prone
                         * for deadlocks.
                         */
                        assertTrue(e.getMessage(), e.getMessage().startsWith("Recursive synchronous thread local action detected."));
                        error.set(true);
                    }
                }
            });
            setup.stopped.set(true);
            future.get();
            assertTrue(isStopped(error));
        }
    }

    @Test
    public void testSynchronous() {
        forEachConfig((threads, events) -> {
            TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.poll(node);
                return false;
            });

            ActionCollector[] collectors = new ActionCollector[events];
            AtomicInteger eventCounter = new AtomicInteger();
            for (int i = 0; i < collectors.length; i++) {
                collectors[i] = new ActionCollector(setup, eventCounter, true, true);
            }

            for (int i = 0; i < events; i++) {
                setup.env.submitThreadLocal(null, collectors[i]);
            }

            setup.stopAndAwait();

            for (int i = 0; i < collectors.length; i++) {
                ActionCollector runnable = collectors[i];

                // verify that events were happening in the right order#
                assertEquals(threads, runnable.ids.size());

                for (int concurrentId : runnable.ids) {
                    int priorEvents = threads * i;
                    int doneBy = priorEvents + threads;

                    assertTrue(concurrentId + ">=" + priorEvents, concurrentId >= priorEvents);
                    assertTrue(concurrentId + "<=" + doneBy, concurrentId <= doneBy);
                }

                assertEquals(threads, runnable.actions.size());

                // verify that every thread is seen exactly once
                Set<Thread> seenThreads = new HashSet<>();
                for (Thread t : runnable.actions) {
                    if (seenThreads.contains(t)) {
                        throw new AssertionError("Did not expect to see thread twice.");
                    }
                    seenThreads.add(t);
                }
            }
        });
    }

    @Test
    public void testAsynchronous() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false);

                for (int i = 0; i < events; i++) {
                    setup.env.submitThreadLocal(null, runnable);
                }
                setup.stopAndAwait();
                assertActionsAnyOrder(threads, events, runnable);
            }
        });
    }

    private static void assertActionsAnyOrder(int threads, int events, ActionCollector runnable) {
        assertEquals(threads * events, runnable.ids.size());
        for (int concurrentId : runnable.ids) {
            int priorEvents = 0;
            int doneBy = events * threads;

            assertTrue(concurrentId + ">=" + priorEvents, concurrentId >= priorEvents);
            assertTrue(concurrentId + "<=" + doneBy, concurrentId <= doneBy);
        }

        assertEquals(threads * events, runnable.actions.size());

        // verify that every thread is seen events time
        Map<Thread, Integer> seenThreads = new HashMap<>();
        for (Thread t : runnable.actions) {
            seenThreads.compute(t, (k, p) -> {
                if (p == null) {
                    return 1;
                } else {
                    return p + 1;
                }
            });
        }
        for (Entry<Thread, Integer> counters : seenThreads.entrySet()) {
            assertEquals(counters.getKey().toString(), (int) counters.getValue(), events);
        }
    }

    @Test
    public void testSideEffecting() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean(false);
            AtomicBoolean allowSideEffects = new AtomicBoolean(true);

            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint config = TruffleSafepoint.getCurrent();
                boolean prev = config.setAllowSideEffects(false);
                try {
                    while (true) {
                        TruffleSafepoint.poll(node);
                        if (isStopped(stopped)) {
                            return true;
                        }
                    }
                } finally {
                    config.setAllowSideEffects(prev);
                }
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false);
                for (int i = 0; i < events; i++) {
                    setup.env.submitThreadLocal(null, runnable);
                }
                assertEquals(0, eventCounter.get());

                allowSideEffects.set(false);
                stopped.set(true);
                setup.stopAndAwait();
                assertActionsAnyOrder(threads, events, runnable);
            }
        });
    }

    @TruffleBoundary
    private static boolean isStopped(AtomicBoolean stopped) {
        return stopped.get();
    }

    @Test
    public void testStackTrace() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.poll(node);
                return false;
            })) {

                AtomicInteger eventCounter = new AtomicInteger();
                List<List<List<TruffleStackTraceElement>>> lists = new ArrayList<>();
                for (int i = 0; i < events; i++) {
                    List<List<TruffleStackTraceElement>> stackTraces = new ArrayList<>();
                    setup.env.submitThreadLocal(null,
                                    new ThreadLocalAction(true, true) {
                                        @Override
                                        protected void perform(Access access) {
                                            RuntimeException e = new RuntimeException();
                                            TruffleStackTrace.fillIn(e);
                                            synchronized (stackTraces) {
                                                stackTraces.add(TruffleStackTrace.getStackTrace(e));
                                            }
                                        }
                                    });
                    lists.add(stackTraces);
                }
                assertEquals(0, eventCounter.get());
                setup.stopAndAwait();

                for (List<List<TruffleStackTraceElement>> eventList : lists) {
                    assertEquals(threads, eventList.size());
                    for (List<TruffleStackTraceElement> list : eventList) {
                        assertEquals(1, list.size());
                        for (TruffleStackTraceElement element : list) {
                            assertSame(setup.target, element.getTarget());
                        }
                    }
                }
            }
        });
    }

    @Test
    public void testException() {
        forEachConfig((threads, events) -> {

            AtomicReference<CountDownLatch> latchRef = new AtomicReference<>(null);
            List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.poll(node);
                return false;
            }, (e) -> {
                exceptions.add(e);
                latchRef.get().countDown();
            })) {

                for (int i = 0; i < events; i++) {
                    CountDownLatch latch = new CountDownLatch(threads);
                    latchRef.set(latch);

                    String testId = "test " + i + " of " + events;
                    AtomicInteger counter = new AtomicInteger();
                    Future<?> await = setup.env.submitThreadLocal(null,
                                    new ThreadLocalAction(true, false) {

                                        @Override
                                        protected void perform(Access access) {
                                            counter.incrementAndGet();
                                            throw new RuntimeException(testId);
                                        }
                                    });
                    // wait until all tasks on all threads are done
                    waitOrFail(await);

                    // make sure we thrown the exception for all events
                    assertEquals(threads, counter.get());

                    try {
                        // wait until all exceptions are reported
                        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw failTimeout(null);
                        }
                    } catch (InterruptedException e) {
                        fail();
                    }

                    assertEquals(threads, exceptions.size());
                    for (Throwable t : exceptions) {
                        assertTrue(t instanceof RuntimeException);
                        assertEquals(testId, t.getMessage());
                    }
                    exceptions.clear();
                }
            }
        });
    }

    private static final Node INVALID_NODE = new Node() {
    };

    /*
     * This test case is inspired by the ruby use case of disabling side-effects and block to wake
     * up to allow side-effecting effects a forced location.
     */
    @Test
    public void testBlockedAndSafepoints() {
        forEachConfig((threads, events) -> {
            Semaphore semaphore = new Semaphore(threads);
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
                lockCooperativelySafepoint(semaphore, node, safepoint);
                return false;
            })) {
                try {
                    AtomicInteger eventCounter = new AtomicInteger();
                    List<Future<?>> threadLocals = new ArrayList<>();

                    for (int i = 0; i < events; i++) {
                        threadLocals.add(setup.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                            @Override
                            protected void perform(Access access) {
                                assertNotSame(INVALID_NODE, access.getLocation());
                                assertSame(setup.target.getRootNode(), access.getLocation());
                                eventCounter.incrementAndGet();
                            }
                        }));

                        // after half of the events we let them run into the semaphore
                        // this encourages race conditions between setting blocked and unlocking
                        if (i == Math.floorDiv(events, 2)) {
                            for (int j = 0; j < threads; j++) {
                                acquire(semaphore);
                            }
                        }
                    }

                    // wait for all events to complete so we can reliably assert the events
                    for (Future<?> f : threadLocals) {
                        waitOrFail(f);
                    }

                    assertEquals(events * threads, eventCounter.get());

                } finally {
                    // let the threads complete in an orderly fashing
                    // events can happen in any order
                    semaphore.release(threads);
                }
            }
        });
    }

    @TruffleBoundary
    private static void lockCooperativelySafepoint(Semaphore semaphore, Node node, TruffleSafepoint safepoint) {
        boolean prevEffects = safepoint.setAllowSideEffects(false);
        Interrupter prevBlocked = safepoint.setBlocked(Interrupter.THREAD_INTERRUPT);
        try {
            while (true) {
                // No side-effecting events should happen here
                TruffleSafepoint.pollHere(INVALID_NODE);

                try {
                    // allow side-effects trigger interruptions again
                    boolean prevInner = safepoint.setAllowSideEffects(true);
                    try {
                        semaphore.acquire();
                        break;
                    } finally {
                        safepoint.setAllowSideEffects(prevInner);
                    }
                } catch (InterruptedException e) {
                    boolean condDisabled = safepoint.setAllowSideEffects(true);
                    try {
                        // All side-effecting events are forced to happen here.
                        TruffleSafepoint.pollHere(node);
                    } finally {
                        safepoint.setAllowSideEffects(condDisabled);
                    }
                    continue;
                }
            }
            releaseSemaphore(semaphore);
        } finally {
            safepoint.setAllowSideEffects(prevEffects);
            safepoint.setBlocked(prevBlocked);
        }
    }

    @Test
    public void testBlocked() {
        forEachConfig((threads, events) -> {
            Semaphore semaphore = new Semaphore(threads);
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.setBlockedInterruptible(node, Semaphore::acquire, semaphore);
                releaseSemaphore(semaphore);
                assert !Thread.interrupted() : "invalid trailing interrupted state";
                return false;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false);
                List<Future<?>> threadLocals = new ArrayList<>();

                for (int i = 0; i < events; i++) {
                    threadLocals.add(setup.env.submitThreadLocal(null, runnable));

                    if (i == Math.floorDiv(events, 2)) {
                        // after half of the events we let them run into the semaphore
                        // this encourages contention conditions between setting blocked and
                        // unlocking
                        for (int j = 0; j < threads; j++) {
                            acquire(semaphore);
                        }
                    }
                }

                // wait for all events to complete so we can reliably assert the events
                for (Future<?> f : threadLocals) {
                    waitOrFail(f);
                }

                // events can happen in any order
                assertActionsAnyOrder(threads, events, runnable);
                semaphore.release(threads);
            }
        });
    }

    @TruffleBoundary
    private static void releaseSemaphore(Semaphore semaphore) {
        semaphore.release();
    }

    /*
     * Non public version that can conifgure whether to enter.
     */
    private static Future<?> submitThreadLocalInternal(Env env, Thread[] threads, ThreadLocalAction action, boolean needEnter) {
        try {
            return (Future<?>) SUBMIT_INTERNAL.invoke(env, threads, action, needEnter);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }

    /*
     * First submit a synchronous safepoint and then reschedule an event until the context was left
     * for the last time on a thread. This simulates how we do cancellation and/or interrupt in a
     * polyglot context. Note that this test cannot be used via public API and is designed as
     * whitebox test for context cancellation.
     */
    @Test
    public void testContextAlive() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.poll(node);
                return false;
            })) {

                Map<Thread, List<AtomicBoolean>> noLongerAlive = Collections.synchronizedMap(new HashMap<>());
                try {
                    List<Future<?>> threadLocals = new ArrayList<>();
                    for (int i = 0; i < events; i++) {
                        ThreadLocal<AtomicBoolean> localBoolean = ThreadLocal.withInitial(() -> {
                            AtomicBoolean b = new AtomicBoolean();
                            noLongerAlive.computeIfAbsent(Thread.currentThread(), (k) -> new ArrayList<>()).add(b);
                            return b;
                        });

                        threadLocals.add(submitThreadLocalInternal(setup.env, null, new ThreadLocalAction(true, true) {
                            @Override
                            protected void perform(Access access) {
                                assertFalse(localBoolean.get().get());
                                if (setup.env.getContext().isActive()) {
                                    final Thread[] currentThread = new Thread[]{access.getThread()};
                                    submitThreadLocalInternal(setup.env, currentThread, new ThreadLocalAction(true, false) {
                                        @Override
                                        protected void perform(Access innerAccess) {
                                            assertSame(currentThread[0], innerAccess.getThread());
                                            // context is no longer alive only once when we leave
                                            // the
                                            // context
                                            assertFalse(localBoolean.get().get());
                                            if (setup.env.getContext().isActive()) {
                                                submitThreadLocalInternal(setup.env, currentThread, this, false);
                                            } else {
                                                localBoolean.get().set(true);
                                            }
                                        }
                                    }, false);
                                } else {
                                    localBoolean.get().set(true);
                                }
                            }
                        }, false));

                    }
                    // wait for all events to complete so we can reliably assert the events
                    for (Future<?> f : threadLocals) {
                        waitOrFail(f);
                    }

                } finally {
                    // let the threads complete in an orderly fashion
                    setup.stopAndAwait();
                }
                assertEquals(threads, noLongerAlive.size());
                for (List<AtomicBoolean> eventAlive : noLongerAlive.values()) {
                    assertEquals(events, eventAlive.size());
                    for (AtomicBoolean event : eventAlive) {
                        assertTrue(event.get());
                    }
                }
            }
        });
    }

    private static Semaphore[] createSemaphores(int count, int permits) {
        Semaphore[] semaphores = new Semaphore[count];
        for (int i = 0; i < semaphores.length; i++) {
            semaphores[i] = new Semaphore(permits);
        }
        return semaphores;
    }

    private static AtomicBoolean[] createBooleans(int count) {
        AtomicBoolean[] b = new AtomicBoolean[count];
        for (int i = 0; i < b.length; i++) {
            b[i] = new AtomicBoolean();
        }
        return b;
    }

    @Test
    public void testRecursiveBlockingProcessing() throws InterruptedException {
        /*
         * Specfies the number of recursive blocking actions.
         */
        int[] tests = new int[]{1, 2, 3, 7, 16, 128, 256};
        for (int blockingActions : tests) {
            Semaphore waitForSafepoint = new Semaphore(0);
            Semaphore[] leaveBlocked = createSemaphores(blockingActions, 0);

            try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
                acquire(waitForSafepoint);
                TruffleSafepoint.poll(node);
                return false;
            })) {

                AtomicBoolean performed = new AtomicBoolean();
                AtomicBoolean[] inBlockingAction = createBooleans(blockingActions);
                Semaphore[] awaitBlocked = createSemaphores(blockingActions, 0);
                Future<?>[] blockingFutures = new Future<?>[blockingActions];

                for (int i = 0; i < blockingActions; i++) {
                    final int actionIndex = i;
                    blockingFutures[actionIndex] = setup.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                        @Override
                        protected void perform(Access access) {
                            if (actionIndex > 0) {
                                assertTrue(inBlockingAction[actionIndex - 1].get());
                            }
                            inBlockingAction[actionIndex].set(true);
                            try {
                                awaitBlocked[actionIndex].release();
                                TruffleSafepoint.setBlockedInterruptible(access.getLocation(), (e) -> {
                                    leaveBlocked[actionIndex].acquire();
                                }, null);
                            } finally {
                                inBlockingAction[actionIndex].set(false);
                            }
                        }
                    });
                }

                Future<?> f = setup.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                    @Override
                    protected void perform(Access innerAccess) {
                        assertTrue(inBlockingAction[blockingActions - 1].get());
                        if (performed.get()) {
                            throw new AssertionError("already performed");
                        }
                        performed.set(true);
                    }
                });

                // start processing safepoints now
                waitForSafepoint.release(Integer.MAX_VALUE);

                for (int actionIndex = 0; actionIndex < blockingActions; actionIndex++) {
                    awaitBlocked[actionIndex].acquire();
                }

                for (int actionIndex = 0; actionIndex < blockingActions; actionIndex++) {
                    leaveBlocked[actionIndex].release();
                }
                waitOrFail(f);
                assertTrue(performed.get());

            }
        }
    }

    @TruffleBoundary
    private static void acquire(Semaphore waitForSafepoint) {
        waitForSafepoint.acquireUninterruptibly();
    }

    /*
     * All events are cancelled or performed as soon as the context is closed, otherwise the context
     * close throws an error.
     */
    @Test
    public void testContextCancellation() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicBoolean closed = new AtomicBoolean();
                List<Future<?>> threadLocals = new ArrayList<>();

                Semaphore awaitClosing = new Semaphore(0);
                Future<?> closing = service.submit(() -> {
                    awaitClosing.release();
                    setup.context.close(true);
                    closed.set(true);
                });

                for (int i = 0; i < events; i++) {
                    threadLocals.add(setup.env.submitThreadLocal(null, new ThreadLocalAction(false, false) {
                        @Override
                        protected void perform(Access innerAccess) {
                            if (closed.get()) {
                                fail("no event should ever be called if context is closed");
                            }
                        }
                    }));
                }
                acquire(awaitClosing);

                // we need to allow threads to complete before we cancel
                // this is necessary because we still use instrumentation for cancel and we are
                // not
                // using instrumented nodes here.
                setup.stopped.set(true);
                waitOrFail(closing);
            }
        });
    }

    /*
     * Test that future cancel actually cancels the event.
     */
    @Test
    public void testEventCancellation() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.poll(node);
                return false;
            })) {
                List<Future<Void>> futures = new ArrayList<>();
                for (int i = 0; i < events; i++) {
                    Future<Void> f = (setup.env.submitThreadLocal(null, new ThreadLocalAction(false, false) {
                        @Override
                        protected void perform(Access innerAccess) {
                        }
                    }));
                    if (f.cancel(false)) {
                        assertTrue(f.isDone());
                        assertTrue(f.isCancelled());
                    }
                    futures.add(f);
                }
                for (Future<Void> future : futures) {
                    waitOrFail(future);
                }
            }
        });
    }

    @TruffleBoundary
    private static void contextSafepoint() {
        Context.getCurrent().safepoint();
    }

    @Test
    public void testContextSafepoint() {
        forEachConfig((threads, events) -> {
            AtomicBoolean stopped = new AtomicBoolean();

            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                while (!isStopped(stopped)) {
                    contextSafepoint();
                }
                return true;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector collector = new ActionCollector(setup, eventCounter, true, false);

                // host safepoints don't have a location
                collector.verifyLocation = false;

                for (int i = 0; i < events; i++) {
                    setup.env.submitThreadLocal(null, collector);
                }
                stopped.set(true);
                setup.stopAndAwait();
                // events can happen in any order
                assertActionsAnyOrder(threads, events, collector);
            }
        });
    }

    @Test
    public void testSafepointALot() {
        safepointALot = true;
        try {
            testAsynchronous();
        } finally {
            safepointALot = false;
        }
    }

    @Test
    public void testNonSideEffectInvalidErrorThrown() throws InterruptedException {
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            TruffleSafepoint.poll(node);
            return false;
        })) {
            setup.env.submitThreadLocal(null, new ThreadLocalAction(false, false) {
                @Override
                protected void perform(Access outer) {
                    throw new GuestException();
                }
            });
            setup.stopped.set(true);
            setup.futures.get(0).get();
            fail();
        } catch (ExecutionException e) {
            assertTrue(e.getCause().toString(), e.getCause() instanceof AssertionError);
            assertEquals("Throwing Truffle exception is disallowed in non-side-effecting thread local actions.", e.getCause().getMessage());
        }
    }

    /*
     * This needs to be higher than graal.MaximumEscapeAnalysisArrayLength. However in this truffle
     * test the value is not easily accessible.
     */
    static int nonConstantValue = 1024;

    @Test
    public void testBigAllocationInLoop() {
        final int loopCount = 1024;
        Object[] values = new Object[loopCount];
        CountDownLatch await = new CountDownLatch(2);
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            countDownAndAwait(await);
            for (int i = 0; i < loopCount; i++) {
                // perform an escaping allocation
                values[i] = new Object[nonConstantValue];
                TruffleSafepoint.poll(node);
            }
            return false;
        })) {
            SafepointCounter counter = new SafepointCounter(setup);
            setup.env.submitThreadLocal(null, counter);

            countDownAndAwait(await);

            // now the loop runs and we should get at least one safepoint invocation for each loop
            // invocation. otherwise something is wrong with safepoint elimination

            setup.stopAndAwait();
            int count = counter.counter.get();
            assertTrue(String.valueOf(count), count >= loopCount);
        }
    }

    @Test
    public void testSimpleAllocationInLoop() {
        final int loopCount = 1024;
        Object[] values = new Object[loopCount];
        CountDownLatch await = new CountDownLatch(2);
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            countDownAndAwait(await);
            for (int i = 0; i < loopCount; i++) {
                // perform an escaping allocation
                values[i] = new Object();
                TruffleSafepoint.poll(node);
            }
            return false;
        })) {
            SafepointCounter counter = new SafepointCounter(setup);
            setup.env.submitThreadLocal(null, counter);

            countDownAndAwait(await);

            // now the loop runs and we should get at least one safepoint invocation for each loop
            // invocation. otherwise something is wrong with safepoint elimination

            setup.stopAndAwait();
            int count = counter.counter.get();
            assertTrue(String.valueOf(count), count >= loopCount);
        }
    }

    @Test
    public void testCountedSumLoop() {
        final int loopCount = 1024;
        int[] values = new int[loopCount];
        CountDownLatch await = new CountDownLatch(2);
        try (TestSetup setup = setupSafepointLoop(1, (s, node) -> {
            countDownAndAwait(await);
            int sum = 0;
            for (int i = 0; i < loopCount; i++) {
                sum += values[i];
            }
            // escape sum value
            values[0] = sum;
            return false;
        })) {
            SafepointCounter counter = new SafepointCounter(setup);
            setup.env.submitThreadLocal(null, counter);

            countDownAndAwait(await);

            setup.stopAndAwait();
            int count = counter.counter.get();

            // for a sum loop the number of notifications should be below 1024
            // otherwise truffle loop safepoint elimination did not work.
            assertTrue(String.valueOf(count), count < loopCount);
        }
    }

    @TruffleBoundary
    private static void countDownAndAwait(CountDownLatch await) {
        await.countDown();
        try {
            await.await();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    static class CallInLoopNode extends Node implements NodeCallable {

        @Child IndirectCallNode indirectCall = IndirectCallNode.create();

        static final int LOOP_COUNT = 1024;

        CallTarget target;
        Semaphore awaitSubmit = new Semaphore(0);
        Object[] values = new Object[LOOP_COUNT];

        public boolean call(TestSetup setup, TestRootNode node) {
            initialize();
            for (int i = 0; i < LOOP_COUNT; i++) {
                // perform an escaping allocation
                values[i] = indirectCall.call(target);
            }
            return false;
        }

        @TruffleBoundary
        private void initialize() {
            target = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
            try {
                awaitSubmit.acquire();
            } catch (InterruptedException e) {
                // not expected to interrupt
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void testTruffleCallInLoop() {
        CallInLoopNode callInLoop = new CallInLoopNode();
        try (TestSetup setup = setupSafepointLoop(1, callInLoop)) {
            SafepointCounter counter = new SafepointCounter(setup);
            setup.env.submitThreadLocal(null, counter);

            callInLoop.awaitSubmit.release();

            setup.stopAndAwait();
            int count = counter.counter.get();

            // if there is an indirect truffle call in the loop
            // we can omit the safepoint. if that safepoint would
            // therefore we expect a safepoint count around 1024 but definitely less than 1024 * 2
            assertTrue(String.valueOf(count), count < CallInLoopNode.LOOP_COUNT * 2);
        }
    }

    @SuppressWarnings("serial")
    static class GuestException extends AbstractTruffleException {

    }

    @Test
    public void testSubmitAsInstrument() {
        forEachConfig((threads, events) -> {
            try (TestSetup setup = setupSafepointLoop(threads, (s, node) -> {
                TruffleSafepoint.poll(node);
                return false;
            })) {
                AtomicInteger eventCounter = new AtomicInteger();
                ActionCollector runnable = new ActionCollector(setup, eventCounter, true, false);
                for (int i = 0; i < events; i++) {
                    setup.instrumentEnv.submitThreadLocal(setup.env.getContext(), null, runnable);
                }
                setup.stopAndAwait();
                assertActionsAnyOrder(threads, events, runnable);
            }
        });
    }

    @FunctionalInterface
    interface TestRunner {

        void run(int threads, int events);

    }

    void forEachConfig(TestRunner run) {
        // synchronous execution of all configs
        for (int threadConfig = 0; threadConfig < THREAD_CONFIGS.length; threadConfig++) {
            int threads = THREAD_CONFIGS[threadConfig];
            for (int iterationConfig = 0; iterationConfig < ITERATION_CONFIGS.length; iterationConfig++) {
                int events = ITERATION_CONFIGS[iterationConfig];
                if (VERBOSE) {
                    System.out.println("[" + threads + ", " + events + "]");
                }
                try {
                    run.run(threads, events);
                } catch (Throwable e) {
                    throw new AssertionError("Test config threads " + threads + " events " + events + " failed.", e);
                }
            }
        }

        // asynchronous execution of all configs
        if (RERUN_THREAD_CONFIG_ASYNC) {
            List<Future<?>> futures = new ArrayList<>();
            for (int threadConfig = 0; threadConfig < THREAD_CONFIGS.length; threadConfig++) {
                int threads = THREAD_CONFIGS[threadConfig];
                for (int iterationConfig = 0; iterationConfig < ITERATION_CONFIGS.length; iterationConfig++) {
                    int events = ITERATION_CONFIGS[iterationConfig];
                    try {
                        if (futures.size() >= 64) {
                            for (Future<?> future : futures) {
                                waitOrFail(future);
                            }
                            futures.clear();
                        }
                        futures.add(service.submit(() -> run.run(threads, events)));
                    } catch (AssertionError e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            for (Future<?> future : futures) {
                waitOrFail(future);
            }
        }
    }

    private static void waitOrFail(Future<?> future) throws AssertionError {
        try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } catch (ExecutionException e) {
            throw new AssertionError(e.getCause());
        } catch (TimeoutException e) {
            throw failTimeout(e);
        }
    }

    private static RuntimeException failTimeout(TimeoutException e) throws AssertionError {
        System.out.println("Timeout detected. Printing all threads: ");
        for (Entry<Thread, StackTraceElement[]> elements : Thread.getAllStackTraces().entrySet()) {
            Exception ex = new Exception(elements.getKey().toString());
            ex.setStackTrace(elements.setValue(elements.getValue()));
            ex.printStackTrace();
        }
        throw new AssertionError("Timed out waiting for threads", e);
    }

    private TestSetup setupSafepointLoop(int threads, NodeCallable callable) {
        return setupSafepointLoop(threads, callable, null);
    }

    static class TestRootNode extends RootNode {

        final AtomicBoolean stopped;
        final TestSetup setup;
        final CountDownLatch latch;
        @CompilationFinal NodeCallable callable;

        TestRootNode(TruffleLanguage<?> language, AtomicBoolean stopped, TestSetup setup, CountDownLatch latch, NodeCallable callable) {
            super(language);
            this.stopped = stopped;
            this.setup = setup;
            this.latch = latch;
            if (callable instanceof Node) {
                this.callable = (NodeCallable) insert((Node) callable);
            } else {
                this.callable = callable;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object execute(VirtualFrame frame) {
            waitForLatch(latch);
            do {
                Boolean result = callable.call(setup, this);
                if (result) {
                    return result;
                }
                // we want to call at least once
            } while (!stopped.get());
            return null;
        }

        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        public String getName() {
            return "org.graalvm.TestRoot";
        }
    }

    @SuppressWarnings("unchecked")
    private TestSetup setupSafepointLoop(int threads, NodeCallable callable, Consumer<Throwable> exHandler) {
        Context c = createTestContext();
        TestSetup setup = null;
        try {
            c.enter();
            c.initialize(ProxyLanguage.ID);
            ProxyLanguage proxyLanguage = ProxyLanguage.getCurrentLanguage();
            Env env = ProxyLanguage.getCurrentContext().getEnv();
            TruffleInstrument.Env instrument = c.getEngine().getInstruments().get(ProxyInstrument.ID).lookup(ProxyInstrument.Initialize.class).getEnv();
            c.leave();
            CountDownLatch latch = new CountDownLatch(threads);
            Object targetEnter = env.getContext().enter(null);
            AtomicBoolean stopped = new AtomicBoolean();

            TestSetup finalSetup = setup = new TestSetup(c, env, instrument, stopped);
            setup.root = new TestRootNode(proxyLanguage, stopped, setup, latch, callable);
            setup.target = Truffle.getRuntime().createCallTarget(setup.root);
            env.getContext().leave(null, targetEnter);
            setup.futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                setup.futures.add(service.submit(() -> {
                    Object prev = env.getContext().enter(finalSetup.target.getRootNode());
                    try {
                        do {
                            try {
                                return (Boolean) finalSetup.target.call(latch);
                            } catch (Throwable t) {
                                if (exHandler != null) {
                                    exHandler.accept(t);
                                } else {
                                    throw t;
                                }
                            }
                        } while (!stopped.get());
                        return true;
                    } finally {
                        env.getContext().leave(finalSetup.target.getRootNode(), prev);
                    }
                }));
            }
            try {
                if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    for (Future<Boolean> future : setup.futures) {
                        if (future.isDone()) {
                            try {
                                future.get();
                            } catch (ExecutionException e) {
                                throw new AssertionError(e.getCause());
                            }
                        }
                    }
                    throw new AssertionError();
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            return setup;
        } catch (Throwable t) {
            if (setup != null && setup.futures != null) {
                setup.close();
            } else {
                c.close();
            }
            throw t;
        }
    }

    @FunctionalInterface
    interface NodeCallable extends NodeInterface {

        boolean call(TestSetup setup, TestRootNode node);

    }

    static class TestSetup implements AutoCloseable {

        final Context context;
        final Env env;
        final TruffleInstrument.Env instrumentEnv;
        List<Future<Boolean>> futures;
        @CompilationFinal RootCallTarget target;
        @CompilationFinal TestRootNode root;
        final AtomicBoolean stopped;

        TestSetup(Context context, Env env, TruffleInstrument.Env instrumentEnv, AtomicBoolean stopped) {
            this.context = context;
            this.env = env;
            this.instrumentEnv = instrumentEnv;
            this.stopped = stopped;
        }

        void stopAndAwait() {
            stopped.set(true);
            awaitFutures(futures);
        }

        @Override
        public void close() {
            stopAndAwait();
            context.close();
        }
    }

    static boolean isGraalRuntime() {
        return Truffle.getRuntime().getName().contains("Graal");
    }

    protected Context createTestContext() {
        Context.Builder b = Context.newBuilder();
        b.allowExperimentalOptions(true);
        if (isGraalRuntime()) {
            b.option("engine.CompileImmediately", "true");
            b.option("engine.BackgroundCompilation", "false");
        }
        if (safepointALot) {
            b.option("engine.SafepointALot", "true");
            b.logHandler(new ByteArrayOutputStream()); // discard output of safepoint a lot
        }
        return b.build();
    }

    private boolean safepointALot = false;

    @TruffleBoundary
    private static void waitForLatch(CountDownLatch latch) throws AssertionError {
        latch.countDown();
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
    }

    private static void awaitFutures(List<Future<Boolean>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new AssertionError(e.getCause());
            } catch (InterruptedException e) {
                throw new AssertionError(2);
            } catch (TimeoutException e) {
                throw failTimeout(e);
            }
        }
    }

    @SuppressWarnings("serial")
    static class SafepointPerformed extends RuntimeException {
    }

    static class SafepointCounter extends ThreadLocalAction {

        private final TestSetup setup;

        final AtomicInteger counter = new AtomicInteger();

        protected SafepointCounter(TestSetup setup) {
            super(false, false);
            this.setup = setup;
        }

        @Override
        protected void perform(Access access) {
            counter.incrementAndGet();

            // resubmit
            setup.env.submitThreadLocal(new Thread[]{Thread.currentThread()}, this);
        }
    }

    static class ActionCollector extends ThreadLocalAction {
        final List<Thread> actions = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> ids = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger counter;
        final TestSetup setup;

        boolean verifyLocation = true;

        ActionCollector(TestSetup setup, boolean sync) {
            this(setup, new AtomicInteger(), false, sync);
        }

        ActionCollector(TestSetup setup, AtomicInteger counter, boolean sideEffect, boolean sync) {
            super(sideEffect, sync);
            this.setup = setup;
            this.counter = counter;
        }

        @Override
        protected void perform(Access access) {
            assertSame(Thread.currentThread(), access.getThread());
            if (verifyLocation) {
                assertSame(setup.target.getRootNode(), access.getLocation());
            } else {
                assertNotNull(access.getLocation());
            }
            actions.add(access.getThread());
            ids.add(counter.incrementAndGet());
        }

        @Override
        public String toString() {
            return "ActionCollector@" + Integer.toHexString(hashCode());
        }
    }
}
