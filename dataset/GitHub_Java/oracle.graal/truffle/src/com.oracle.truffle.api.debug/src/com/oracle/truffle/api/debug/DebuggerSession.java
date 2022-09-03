/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.debug;

import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.Breakpoint.BreakpointConditionFailure;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * Client access to {@link PolyglotEngine} {@linkplain Debugger debugging services}.
 *
 * <h4>Session lifetime</h4>
 * <p>
 * <ul>
 * <li>A {@link PolyglotEngine} debugging client
 * {@linkplain Debugger#startSession(SuspendedCallback) requests} a new {@linkplain DebuggerSession
 * session} from the {@linkplain Debugger#find(PolyglotEngine) engine's Debugger}.</li>
 *
 * <li>A client uses a session to request suspension of guest language execution threads, for
 * example by setting breakpoints or stepping.</li>
 *
 * <li>When a session suspends a guest language execution thread, it passes its client a new
 * {@link SuspendedEvent} via synchronous {@linkplain SuspendedCallback callback} on the execution
 * thread.</li>
 *
 * <li>A suspended guest language execution thread resumes language execution only after the client
 * callback returns.</li>
 *
 * <li>Sessions that are no longer needed should be {@linkplain #close() closed}; a closed session
 * has no further affect on engine execution.</li>
 * </ul>
 * </p>
 *
 * <h4>Debugging requests</h4>
 * <p>
 * Session clients can manage guest language execution in several ways:
 * <ul>
 * <li>{@linkplain #install(Breakpoint) Install} a newly created {@link Breakpoint}.</li>
 *
 * <li>{@linkplain #suspendNextExecution() Request} suspension of the next execution on the first
 * thread that is encountered.</li>
 *
 * <li>Request a stepping action (e.g. {@linkplain SuspendedEvent#prepareStepInto(int) step into},
 * {@linkplain SuspendedEvent#prepareStepOver(int) step over},
 * {@linkplain SuspendedEvent#prepareKill() kill}) on a suspended execution thread, to take effect
 * after the client callback returns.</li>
 * </ul>
 * </p>
 *
 * <h4>Event merging</h4>
 * <p>
 * A session may suspend a guest language execution thread in response to more than one request from
 * its client. For example:
 * <ul>
 * <li>A stepping action may land where a breakpoint is installed.</li>
 * <li>Multiple installed breakpoints may apply to a particular location.</li>
 * </ul>
 * In such cases the client receives a single <em>merged</em> event. A call to
 * {@linkplain SuspendedEvent#getBreakpoints()} lists all breakpoints (possibly none) that apply to
 * the suspended event's location.</li>
 * </p>
 *
 * <h4>Multiple sessions</h4>
 * <p>
 * There can be multiple sessions associated with a single engine, which are independent of one
 * another in the following ways:
 * <ul>
 * <li>Breakpoints created by a session are not visible to clients of other sessions.</li>
 *
 * <li>A client receives no notification when guest language execution threads are suspended by
 * sessions other than its own.</li>
 *
 * <li>Events are <em>not merged</em> across sessions. For example, when a guest language execution
 * thread hits a location where two sessions have installed breakpoints, each session notifies its
 * client with a new {@link SuspendedEvent} instance.</li>
 * </ul>
 * Because all sessions can control engine execution, some interactions are inherently possible. For
 * example:
 * <ul>
 * <li>A session's client can {@linkplain SuspendedEvent#prepareKill() kill} an execution at just
 * about any time.</li>
 * <li>A session's client can <em>starve</em> execution by not returning from the synchronous
 * {@linkplain SuspendedCallback callback} on the guest language execution thread.</li>
 * </ul>
 * </p>
 * <p>
 * Usage example: {@link DebuggerSessionSnippets#example}
 *
 * @since 0.17
 */
/*
 * Javadoc for package-protected APIs:
 *
 * <li>{@link #suspend(Thread)} suspends the next or current execution on a particular thread.</li>
 * <li>{@link #suspendAll()} suspends the next or current execution on all threads.</li>
 */
public final class DebuggerSession implements Closeable {

    private static final AtomicInteger SESSIONS = new AtomicInteger(0);

    enum SteppingLocation {
        AFTER_CALL,
        AFTER_STATEMENT,
        BEFORE_STATEMENT
    }

    private final Debugger debugger;
    private final SuspendedCallback callback;
    private final List<Breakpoint> breakpoints = Collections.synchronizedList(new ArrayList<>());

    private EventBinding<? extends ExecutionEventNodeFactory> callBinding;
    private EventBinding<? extends ExecutionEventNodeFactory> statementBinding;
    private EventBinding<? extends ExecutionEventNodeFactory> rootBinding;

    private final ConcurrentHashMap<Thread, SuspendedEvent> currentSuspendedEventMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Thread, SteppingStrategy> strategyMap = new ConcurrentHashMap<>();
    private volatile boolean suspendNext;
    private volatile boolean suspendAll;
    private final StableBoolean stepping = new StableBoolean(false);
    private final StableBoolean ignoreLanguageContextInitialization = new StableBoolean(false);
    private boolean includeInternal = false;
    private Predicate<Source> sourceFilter;
    private final StableBoolean breakpointsActive = new StableBoolean(true);
    private final DebuggerExecutionLifecycle executionLifecycle;
    final ThreadLocal<ThreadSuspension> threadSuspensions = new ThreadLocal<>();

    private final int sessionId;

    private volatile boolean closed;

    DebuggerSession(Debugger debugger, SuspendedCallback callback) {
        this.sessionId = SESSIONS.incrementAndGet();
        this.debugger = debugger;
        this.callback = callback;
        if (Debugger.TRACE) {
            trace("open with callback %s", callback);
        }
        addBindings(includeInternal, sourceFilter);
        executionLifecycle = new DebuggerExecutionLifecycle(debugger);
    }

    private void trace(String msg, Object... parameters) {
        Debugger.trace(this + ": " + msg, parameters);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.17
     */
    @Override
    public String toString() {
        return String.format("Session[id=%s]", sessionId);
    }

    /**
     * Returns the {@link Debugger debugger} instance that this session is associated with. Can be
     * used also after the session has already been closed.
     *
     * @since 0.17
     */
    public Debugger getDebugger() {
        return debugger;
    }

    /**
     * Returns a language top scope. The top scopes have global validity and unlike
     * {@link DebugStackFrame#getScope()} have no relation to the suspended location.
     *
     * @since 0.30
     */
    public DebugScope getTopScope(String languageId) {
        LanguageInfo info = debugger.getEnv().getLanguages().get(languageId);
        if (info == null) {
            return null;
        }
        Iterable<Scope> scopes = debugger.getEnv().findTopScopes(languageId);
        Iterator<Scope> it = scopes.iterator();
        if (!it.hasNext()) {
            return null;
        }
        return new DebugScope(it.next(), it, debugger, info);
    }

    /**
     * Returns a polyglot scope - symbols explicitly exported by languages.
     *
     * @since 0.30
     */
    public Map<String, ? extends DebugValue> getExportedSymbols() {
        return new AbstractMap<String, DebugValue>() {
            @Override
            public Set<Map.Entry<String, DebugValue>> entrySet() {
                Set<Map.Entry<String, DebugValue>> entries = new LinkedHashSet<>();
                for (Map.Entry<String, ? extends Object> symbol : debugger.getEnv().getExportedSymbols().entrySet()) {
                    DebugValue value = new DebugValue.HeapValue(debugger, symbol.getKey(), symbol.getValue());
                    entries.add(new SimpleImmutableEntry<>(symbol.getKey(), value));
                }
                return Collections.unmodifiableSet(entries);
            }

            @Override
            public DebugValue get(Object key) {
                if (!(key instanceof String)) {
                    return null;
                }
                String name = (String) key;
                Object value = debugger.getEnv().getExportedSymbols().get(name);
                if (value == null) {
                    return null;
                }
                return new DebugValue.HeapValue(debugger, name, value);
            }
        };
    }

    /**
     * Set a stepping suspension filter. Prepared steps skip code that match this filter.
     *
     * @since 0.26
     */
    public void setSteppingFilter(SuspensionFilter steppingFilter) {
        this.ignoreLanguageContextInitialization.set(steppingFilter.isIgnoreLanguageContextInitialization());
        synchronized (this) {
            boolean oldIncludeInternal = this.includeInternal;
            this.includeInternal = steppingFilter.isInternalIncluded();
            Predicate<Source> oldSourceFilter = this.sourceFilter;
            this.sourceFilter = steppingFilter.getSourcePredicate();
            if (oldIncludeInternal != this.includeInternal || oldSourceFilter != this.sourceFilter) {
                removeBindings();
                addBindings(this.includeInternal, this.sourceFilter);
            }
        }
    }

    /**
     * Suspends the next execution on the first thread that is encountered. After the first thread
     * was suspended no further executions are suspended unless {@link #suspendNextExecution()} is
     * called again. If multiple threads are executing at the same time then there are no guarantees
     * on which thread is going to be suspended. Will throw an {@link IllegalStateException} if the
     * session is already closed.
     *
     * @since 0.17
     */
    public synchronized void suspendNextExecution() {
        if (Debugger.TRACE) {
            trace("suspend next execution");
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        suspendNext = true;
        updateStepping();
    }

    /**
     * Suspends the current or the next execution of a given thread. Will throw an
     * {@link IllegalStateException} if the session is already closed.
     */
    // TODO make part of public API as soon as PolyglotEngine is thread-safe
    void suspend(Thread t) {
        if (Debugger.TRACE) {
            trace("suspend thread %s ", t);
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        setSteppingStrategy(t, SteppingStrategy.createAlwaysHalt(), true);
    }

    /**
     * Suspends the current or the next execution on all threads. All new executing threads will
     * start suspended until {@link #resumeAll()} is called or the session is closed. Will throw an
     * {@link IllegalStateException} if the session is already closed.
     */
    // TODO make part of public API as soon as PolyglotEngine is thread-safe
    synchronized void suspendAll() {
        if (Debugger.TRACE) {
            trace("suspend all threads");
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        suspendAll = true;
        // iterating concurrent hashmap should be save
        for (Thread t : strategyMap.keySet()) {
            SteppingStrategy s = strategyMap.get(t);
            assert s != null;
            if (s.isDone() || s.isConsumed()) {
                setSteppingStrategy(t, SteppingStrategy.createAlwaysHalt(), false);
            }
        }
        updateStepping();
    }

    /**
     * Resumes all suspended executions that have not yet been notified.
     *
     * @since 0.17
     */
    public synchronized void resumeAll() {
        if (Debugger.TRACE) {
            trace("resume all threads");
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        clearStrategies();
    }

    /**
     * Resumes the execution on a given thread if it has not been suspended yet.
     *
     * @param t the thread to resume
     */
    // TODO make part of public API as soon as PolyglotEngine is thread-safe
    synchronized void resume(Thread t) {
        if (Debugger.TRACE) {
            trace("resume threads", t);
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        setSteppingStrategy(t, SteppingStrategy.createContinue(), true);
    }

    private synchronized void setSteppingStrategy(Thread thread, SteppingStrategy strategy, boolean updateStepping) {
        if (closed) {
            return;
        }
        assert strategy != null;
        SteppingStrategy oldStrategy = this.strategyMap.put(thread, strategy);
        if (oldStrategy != strategy) {
            if (Debugger.TRACE) {
                trace("set stepping for thread: %s with strategy: %s", thread, strategy);
            }
            if (updateStepping) {
                updateStepping();
            }
        }
    }

    private synchronized void clearStrategies() {
        suspendAll = false;
        suspendNext = false;
        strategyMap.clear();
        updateStepping();
    }

    private SteppingStrategy getSteppingStrategy(Thread value) {
        return strategyMap.get(value);
    }

    private void updateStepping() {
        assert Thread.holdsLock(this);

        boolean needsStepping = suspendNext || suspendAll;
        if (!needsStepping) {
            // iterating concurrent hashmap should be save
            for (Thread t : strategyMap.keySet()) {
                SteppingStrategy s = strategyMap.get(t);
                assert s != null;
                if (!s.isDone()) {
                    needsStepping = true;
                    break;
                }
            }
        }

        stepping.set(needsStepping);
    }

    @TruffleBoundary
    void setThreadSuspendEnabled(boolean enabled) {
        if (!enabled) {
            // temporarily disable suspensions in the given thread
            threadSuspensions.set(ThreadSuspension.DISABLED);
        } else {
            threadSuspensions.remove();
        }
    }

    private void addBindings(boolean includeInternalCode, Predicate<Source> sFilter) {
        if (statementBinding == null) {
            // The order of registered instrumentations matters.
            // It's important to instrument root nodes first to intercept stack changes,
            // then instrument statements, and
            // call bindings need to be called after statements.
            this.rootBinding = createBinding(RootTag.class, includeInternalCode, sFilter, new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {
                    return new RootSteppingDepthNode();
                }
            });
            this.statementBinding = createBinding(StatementTag.class, includeInternalCode, sFilter, new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {
                    return new StatementSteppingNode(context);
                }
            });
            this.callBinding = createBinding(CallTag.class, includeInternalCode, sFilter, new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {
                    return new CallSteppingNode(context);
                }
            });
        }
    }

    private EventBinding<? extends ExecutionEventNodeFactory> createBinding(Class<?> tag, boolean includeInternalCode, Predicate<Source> sFilter, ExecutionEventNodeFactory factory) {
        Builder builder = SourceSectionFilter.newBuilder().tagIs(tag);
        builder.includeInternal(includeInternalCode);
        if (sFilter != null) {
            builder.sourceIs(new SourceSectionFilter.SourcePredicate() {
                @Override
                public boolean test(Source source) {
                    return sFilter.test(source);
                }
            });
        }
        return debugger.getInstrumenter().attachExecutionEventFactory(builder.build(), factory);
    }

    private void removeBindings() {
        assert Thread.holdsLock(this);
        if (statementBinding != null) {
            callBinding.dispose();
            statementBinding.dispose();
            callBinding = null;
            rootBinding.dispose();
            rootBinding = null;
            statementBinding = null;
            if (Debugger.TRACE) {
                trace("disabled stepping");
            }
        }
    }

    /**
     * Closes the current debugging session and disposes all installed breakpoints.
     *
     * @since 0.17
     */
    public synchronized void close() {
        if (Debugger.TRACE) {
            trace("close session");
        }
        if (closed) {
            throw new IllegalStateException("session already closed");
        }

        clearStrategies();
        removeBindings();
        for (Breakpoint breakpoint : this.breakpoints) {
            breakpoint.sessionClosed(this);
        }
        currentSuspendedEventMap.clear();
        debugger.disposedSession(this);
        closed = true;
    }

    /**
     * Returns all breakpoints {@link #install(com.oracle.truffle.api.debug.Breakpoint) installed}
     * in this session, in the install order. The returned list contains a current snapshot of
     * breakpoints, those that were {@link Breakpoint#dispose() disposed}, or
     * {@link Debugger#install(com.oracle.truffle.api.debug.Breakpoint) installed on Debugger} are
     * not included.
     *
     * @since 0.17
     * @see Debugger#getBreakpoints()
     */
    public List<Breakpoint> getBreakpoints() {
        if (closed) {
            throw new IllegalStateException("session already closed");
        }

        List<Breakpoint> b;
        synchronized (this.breakpoints) {
            // need to synchronize manually breakpoints are iterated which is not
            // synchronized by default.
            b = new ArrayList<>(this.breakpoints);
        }
        return Collections.unmodifiableList(b);
    }

    /**
     * Set whether breakpoints are active in this session. This has no effect on breakpoints
     * enabled/disabled state. Breakpoints need to be active to actually break the execution. The
     * breakpoints are active by default.
     *
     * @param active <code>true</code> to make all breakpoints active, <code>false</code> to make
     *            all breakpoints inactive.
     * @since 0.24
     */
    public void setBreakpointsActive(boolean active) {
        breakpointsActive.set(active);
    }

    /**
     * Test whether breakpoints are active in this session. Breakpoints do not break execution when
     * not active.
     *
     * @since 0.24
     */
    public boolean isBreakpointsActive() {
        return breakpointsActive.get();
    }

    /**
     * Adds a new breakpoint to this session and makes it capable of suspending execution.
     * <p>
     * The breakpoint suspends execution by making a {@link SuspendedCallback callback} to this
     * session, together with an event description that includes
     * {@linkplain SuspendedEvent#getBreakpoints() which breakpoint(s)} were hit.
     *
     * @param breakpoint a new breakpoint
     * @return the installed breakpoint
     * @throws IllegalStateException if the session has been closed
     *
     * @since 0.17
     */
    public synchronized Breakpoint install(Breakpoint breakpoint) {
        install(breakpoint, false);
        return breakpoint;
    }

    synchronized void install(Breakpoint breakpoint, boolean global) {
        if (closed) {
            if (!global) {
                throw new IllegalStateException("Debugger session is already closed. Cannot install new breakpoints.");
            } else {
                return;
            }
        }
        if (!breakpoint.install(this, !global)) {
            return;
        }
        if (!global) { // Do not keep global breakpoints in the list
            this.breakpoints.add(breakpoint);
        }
        if (Debugger.TRACE) {
            trace("installed session breakpoint %s", breakpoint);
        }
    }

    synchronized void disposeBreakpoint(Breakpoint breakpoint) {
        breakpoints.remove(breakpoint);
        if (Debugger.TRACE) {
            trace("disposed session breakpoint %s", breakpoint);
        }
    }

    /**
     * Set a {@link DebugContextsListener listener} to be notified about changes in contexts in
     * guest language application. One listener can be set at a time, call with <code>null</code> to
     * remove the current listener.
     *
     * @param listener a listener to receive the context events, or <code>null</code> to reset it
     * @param includeActiveContexts whether or not this listener should be notified for present
     *            active contexts
     * @since 0.30
     */
    public void setContextsListener(DebugContextsListener listener, boolean includeActiveContexts) {
        executionLifecycle.setContextsListener(listener, includeActiveContexts);
    }

    /**
     * Set a {@link DebugThreadsListener listener} to be notified about changes in threads in guest
     * language application. One listener can be set at a time, call with <code>null</code> to
     * remove the current listener.
     *
     * @param listener a listener to receive the context events
     * @param includeInitializedThreads whether or not this listener should be notified for present
     *            initialized threads
     * @since 0.30
     */
    public void setThreadsListener(DebugThreadsListener listener, boolean includeInitializedThreads) {
        executionLifecycle.setThreadsListener(listener, includeInitializedThreads);
    }

    @TruffleBoundary
    void notifyCallback(DebuggerNode source, MaterializedFrame frame, Object returnValue, BreakpointConditionFailure conditionFailure) {
        ThreadSuspension suspensionDisabled = threadSuspensions.get();
        if (suspensionDisabled != null && !suspensionDisabled.enabled) {
            return;
        }
        // SuspensionFilter:
        if (source.isStepNode()) {
            if (ignoreLanguageContextInitialization.get() && !source.getContext().isLanguageContextInitialized()) {
                return;
            }
        }
        Thread currentThread = Thread.currentThread();
        SuspendedEvent event = currentSuspendedEventMap.get(currentThread);
        if (event != null) {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: recursive from source:%s context:%s location:%s", source, source.getContext(), source.getSteppingLocation());
            }
            // avoid recursive suspensions in non legacy mode.
            return;
        }

        if (source.consumeIsDuplicate()) {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: duplicate from source:%s context:%s location:%s", source, source.getContext(), source.getSteppingLocation());
            }
            return;
        }

        // only the first DebuggerNode for a source location and thread will reach here.

        // mark all other nodes at this source location as duplicates
        List<DebuggerNode> nodes = collectDebuggerNodes(source);
        for (DebuggerNode node : nodes) {
            if (node == source) {
                // for the current one we won't call isDuplicate
                continue;
            }
            node.markAsDuplicate();
        }

        SteppingStrategy s = getSteppingStrategy(currentThread);
        if (suspendNext) {
            synchronized (this) {
                // double checked locking to avoid more than one suspension
                if (suspendNext) {
                    s = SteppingStrategy.createAlwaysHalt();
                    setSteppingStrategy(currentThread, s, true);
                    suspendNext = false;
                }
            }
        }

        if (s == null) {
            // a new Thread just appeared
            s = notifyNewThread(currentThread);
        }

        Map<Breakpoint, Throwable> breakpointFailures = null;
        if (conditionFailure != null) {
            breakpointFailures = new HashMap<>();
            breakpointFailures.put(conditionFailure.getBreakpoint(), conditionFailure.getConditionFailure());
        }

        List<Breakpoint> breaks = null;
        for (DebuggerNode node : nodes) {
            Breakpoint breakpoint = node.getBreakpoint();
            if (breakpoint == null || !isBreakpointsActive()) {
                continue; // not a breakpoint node
            }
            boolean hit = true;
            BreakpointConditionFailure failure = null;
            try {
                hit = breakpoint.notifyIndirectHit(source, node, frame);
            } catch (BreakpointConditionFailure e) {
                failure = e;
            }
            if (hit) {
                if (breaks == null) {
                    breaks = new ArrayList<>();
                }
                breaks.add(breakpoint.isGlobal() ? breakpoint.getROWrapper() : breakpoint);
            }
            if (failure != null) {
                if (breakpointFailures == null) {
                    breakpointFailures = new HashMap<>();
                }
                Breakpoint fb = failure.getBreakpoint();
                if (fb.isGlobal()) {
                    fb = fb.getROWrapper();
                }
                breakpointFailures.put(fb, failure.getConditionFailure());
            }
        }

        boolean hitStepping = s.step(this, source.getContext(), source.getSteppingLocation());
        boolean hitBreakpoint = breaks != null && !breaks.isEmpty();
        if (hitStepping || hitBreakpoint) {
            s.consume();
            doSuspend(SuspendedContext.create(source.getContext()), source.getSteppingLocation(), frame, returnValue, breaks, breakpointFailures);
        } else {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: strategy(%s) from source:%s context:%s location:%s", s, source, source.getContext(), source.getSteppingLocation());
            }
        }
        if (s.isKill()) {   // ComposedStrategy can become kill
            throw new KillException();
        }
    }

    private static void clearFrame(MaterializedFrame frame) {
        FrameDescriptor descriptor = frame.getFrameDescriptor();
        Object value = descriptor.getDefaultValue();
        for (FrameSlot slot : descriptor.getSlots()) {
            frame.setObject(slot, value);
        }
    }

    private void notifyUnwindCallback(MaterializedFrame frame) {
        Thread currentThread = Thread.currentThread();
        SteppingStrategy s = getSteppingStrategy(currentThread);
        // We must have an active stepping strategy on this thread when unwind finished
        assert s != null;
        assert s.isUnwind();
        assert s.step(this, null, null);
        s.consume();
        clearFrame(frame); // Clear the frame that is to be re-entered
        // Fake the caller context
        class Caller {
            final Node node;
            final MaterializedFrame frame;

            Caller(FrameInstance frameInstance) {
                this.node = frameInstance.getCallNode();
                this.frame = frameInstance.getFrame(FrameAccess.MATERIALIZE).materialize();
            }
        }
        Caller caller = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Caller>() {
            private int depth = 0;

            @Override
            public Caller visitFrame(FrameInstance frameInstance) {
                // we stop at eval root stack frames
                if (!SuspendedEvent.isEvalRootStackFrame(DebuggerSession.this, frameInstance) && (depth++ == 0)) {
                    return null;
                }
                return new Caller(frameInstance);
            }
        });
        SuspendedContext context = SuspendedContext.create(caller.node, ((SteppingStrategy.Unwind) s).unwind);
        doSuspend(context, SteppingLocation.AFTER_CALL, caller.frame, null, Collections.emptyList(), Collections.emptyMap());
    }

    private void doSuspend(SuspendedContext context, SteppingLocation steppingLocation, MaterializedFrame frame, Object returnValue, List<Breakpoint> breaks,
                    Map<Breakpoint, Throwable> conditionFailures) {
        CompilerAsserts.neverPartOfCompilation();
        Thread currentThread = Thread.currentThread();

        SuspendedEvent suspendedEvent;
        try {
            suspendedEvent = new SuspendedEvent(this, currentThread, context, frame, steppingLocation, returnValue, breaks, conditionFailures);
            currentSuspendedEventMap.put(currentThread, suspendedEvent);
            try {
                callback.onSuspend(suspendedEvent);
            } finally {
                currentSuspendedEventMap.remove(currentThread);
                /*
                 * In case the debug client did not behave and did store the suspended event.
                 */
                suspendedEvent.clearLeakingReferences();
            }
        } catch (Throwable t) {
            // let the instrumentation handle this
            throw t;
        }

        if (closed) {
            // session got closed in the meantime
            return;
        }

        SteppingStrategy strategy = suspendedEvent.getNextStrategy();
        if (!strategy.isKill()) {
            // suspend(...) has been called during SuspendedEvent notification. this is only
            // possible in non-legacy mode.
            SteppingStrategy currentStrategy = getSteppingStrategy(currentThread);
            if (currentStrategy != null && !currentStrategy.isConsumed()) {
                strategy = currentStrategy;
            }
        }
        strategy.initialize();

        if (Debugger.TRACE) {
            trace("end suspend with strategy %s at %s location %s", strategy, context, steppingLocation);
        }

        setSteppingStrategy(currentThread, strategy, true);
        if (strategy.isKill()) {
            throw new KillException();
        } else if (strategy.isUnwind()) {
            ThreadDeath unwind = context.createUnwind(null, rootBinding);
            ((SteppingStrategy.Unwind) strategy).unwind = unwind;
            throw unwind;
        }
    }

    private List<DebuggerNode> collectDebuggerNodes(DebuggerNode source) {
        List<DebuggerNode> nodes = new ArrayList<>();
        SuspendAnchor suspendAnchor = (source.getSteppingLocation() == SteppingLocation.BEFORE_STATEMENT) ? SuspendAnchor.BEFORE : SuspendAnchor.AFTER;
        EventContext context = source.getContext();
        synchronized (breakpoints) {
            for (Breakpoint b : breakpoints) {
                if (suspendAnchor == b.getSuspendAnchor()) {
                    DebuggerNode node = b.lookupNode(context);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            }
        }
        synchronized (debugger) {
            for (Breakpoint b : debugger.getRawBreakpoints()) {
                if (suspendAnchor == b.getSuspendAnchor()) {
                    DebuggerNode node = b.lookupNode(context);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            }
        }
        if (suspendAnchor == SuspendAnchor.BEFORE) {
            if (stepping.get()) {
                EventBinding<? extends ExecutionEventNodeFactory> localStatementBinding = statementBinding;
                if (localStatementBinding != null) {
                    DebuggerNode node = (DebuggerNode) context.lookupExecutionEventNode(localStatementBinding);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            }
            DebuggerNode node = debugger.alwaysHaltBreakpoint.lookupNode(context);
            if (node != null) {
                nodes.add(node);
            }
        } else {
            assert source.getSteppingLocation() == SteppingLocation.AFTER_CALL ||
                            source.getSteppingLocation() == SteppingLocation.AFTER_STATEMENT;
            // there is only one binding that can lead to an after event
            if (stepping.get()) {
                EventBinding<? extends ExecutionEventNodeFactory> localCallBinding = callBinding;
                if (localCallBinding != null) {
                    DebuggerNode node = (DebuggerNode) context.lookupExecutionEventNode(localCallBinding);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            }
        }
        return nodes;
    }

    private synchronized SteppingStrategy notifyNewThread(Thread currentThread) {
        SteppingStrategy s = getSteppingStrategy(currentThread);
        // double checked locking
        if (s == null) {
            if (suspendAll) {
                // all suspended
                s = SteppingStrategy.createAlwaysHalt();
            } else {
                // not suspended continue execution for this thread
                s = SteppingStrategy.createContinue();
            }
            setSteppingStrategy(currentThread, s, true);
        }
        assert s != null;
        return s;

    }

    /**
     * Evaluates a snippet of code in a halted execution context. Assumes frame is part of the
     * current execution stack, behavior is undefined if not.
     *
     * @param ev event notification where execution is halted
     * @param code text of the code to be executed
     * @param frameInstance frame where execution is halted
     * @return
     * @throws IOException
     */
    static Object evalInContext(SuspendedEvent ev, String code, FrameInstance frameInstance) throws IOException {
        try {
            Node node;
            MaterializedFrame frame;
            if (frameInstance == null) {
                node = ev.getContext().getInstrumentedNode();
                frame = ev.getMaterializedFrame();
            } else {
                node = frameInstance.getCallNode();
                frame = frameInstance.getFrame(FrameAccess.MATERIALIZE).materialize();
            }
            return Debugger.ACCESSOR.evalInContext(node, frame, code);
        } catch (KillException kex) {
            throw new IOException("Evaluation was killed.", kex);
        }
    }

    static final class ThreadSuspension {

        static final ThreadSuspension ENABLED = new ThreadSuspension(true);
        static final ThreadSuspension DISABLED = new ThreadSuspension(false);

        boolean enabled;

        ThreadSuspension(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private final class StatementSteppingNode extends DebuggerNode {

        StatementSteppingNode(EventContext context) {
            super(context);
        }

        @Override
        EventBinding<?> getBinding() {
            return statementBinding;
        }

        @Override
        boolean isStepNode() {
            return true;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (stepping.get()) {
                doEnter(frame.materialize());
            }
        }

        @TruffleBoundary
        private void doEnter(MaterializedFrame frame) {
            SteppingStrategy steppingStrategy;
            if (suspendNext || suspendAll || (steppingStrategy = getSteppingStrategy(Thread.currentThread())) != null && steppingStrategy.isActive()) {
                notifyCallback(this, frame, null, null);
            }
        }

        @Override
        SteppingLocation getSteppingLocation() {
            return SteppingLocation.BEFORE_STATEMENT;
        }
    }

    private final class CallSteppingNode extends DebuggerNode {

        CallSteppingNode(EventContext context) {
            super(context);
        }

        @Override
        EventBinding<?> getBinding() {
            return callBinding;
        }

        @Override
        boolean isStepNode() {
            return true;
        }

        @Override
        public void onReturnValue(VirtualFrame frame, Object result) {
            if (stepping.get()) {
                doReturn(frame.materialize(), result);
            }
        }

        @Override
        public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            if (stepping.get()) {
                doReturn(frame.materialize(), null);
            }
        }

        @TruffleBoundary
        private void doReturn(MaterializedFrame frame, Object result) {
            SteppingStrategy steppingStrategy = strategyMap.get(Thread.currentThread());
            if (steppingStrategy != null && steppingStrategy.isStopAfterCall()) {
                notifyCallback(this, frame, result, null);
            }
        }

        @Override
        SteppingLocation getSteppingLocation() {
            return SteppingLocation.AFTER_CALL;
        }

    }

    private final class RootSteppingDepthNode extends ExecutionEventNode {

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (stepping.get()) {
                doEnter();
            }
        }

        @Override
        public void onReturnValue(VirtualFrame frame, Object result) {
            if (stepping.get()) {
                doReturn();
            }
        }

        @Override
        public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            if (stepping.get()) {
                doReturn();
            }
        }

        @Override
        protected Object onUnwind(VirtualFrame frame, Object info) {
            if (stepping.get()) {
                return doUnwind(frame.materialize());
            } else {
                return null;
            }
        }

        @TruffleBoundary
        private void doEnter() {
            SteppingStrategy steppingStrategy = strategyMap.get(Thread.currentThread());
            if (steppingStrategy != null) {
                steppingStrategy.notifyCallEntry();
            }
        }

        @TruffleBoundary
        private void doReturn() {
            SteppingStrategy steppingStrategy = strategyMap.get(Thread.currentThread());
            if (steppingStrategy != null) {
                steppingStrategy.notifyCallExit();
            }
        }

        @TruffleBoundary
        private Object doUnwind(MaterializedFrame frame) {
            SteppingStrategy steppingStrategy = strategyMap.get(Thread.currentThread());
            if (steppingStrategy != null) {
                Object info = steppingStrategy.notifyOnUnwind();
                if (info == ProbeNode.UNWIND_ACTION_REENTER) {
                    notifyUnwindCallback(frame);
                }
                return info;
            } else {
                return null;
            }
        }
    }

    /**
     * Helper class that uses an assumption to switch between stepping mode and non-stepping mode
     * efficiently.
     */
    private static final class StableBoolean {

        @CompilationFinal private volatile Assumption unchanged;
        @CompilationFinal private volatile boolean value;

        StableBoolean(boolean initialValue) {
            this.value = initialValue;
            this.unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
        }

        boolean get() {
            if (unchanged.isValid()) {
                return value;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return value;
            }
        }

        void set(boolean value) {
            if (this.value != value) {
                this.value = value;
                Assumption old = this.unchanged;
                unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
                old.invalidate();
            }
        }

    }
}

class DebuggerSessionSnippets {

    public void example() {
        // @formatter:off
        // BEGIN: DebuggerSessionSnippets#example
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();

        try (DebuggerSession session = Debugger.find(engine).
                        startSession(new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                // step into the next event
                event.prepareStepInto(1);
            }
        })) {
            Source someCode = Source.newBuilder("...").
                            mimeType("...").
                            name("example").build();

            // install line breakpoint
            session.install(Breakpoint.newBuilder(someCode).lineIs(3).build());

            // should print suspended at for each debugger step.
            engine.eval(someCode);
        }

        // END: DebuggerSessionSnippets#example
        // @formatter:on
    }
}
