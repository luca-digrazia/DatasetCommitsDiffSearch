/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.StepConfig;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.JDWPOptions;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public final class DebuggerController implements ContextsListener {

    private static final StepConfig STEP_CONFIG = StepConfig.newBuilder().suspendAnchors(SourceElement.ROOT, SuspendAnchor.AFTER).build();

    public static final String DEBUG_STACK_FRAME_FIND_CURRENT_ROOT = "findCurrentRoot";

    // justification for all of the hash maps is that lookups only happen when at a breakpoint
    private final Map<Object, SimpleLock> suspendLocks = new HashMap<>();
    private final Map<Object, SuspendedInfo> suspendedInfos = new HashMap<>();
    private final Map<Object, SteppingInfo> commandRequestIds = new HashMap<>();
    private final Map<Object, ThreadJob> threadJobs = new HashMap<>();
    private final Map<Object, FieldBreakpointEvent> fieldBreakpointExpected = new HashMap<>();
    private final Map<Object, MethodBreakpointEvent> methodBreakpointExpected = new HashMap<>();
    private final Map<Breakpoint, BreakpointInfo> breakpointInfos = new HashMap<>();

    private JDWPOptions options;
    private DebuggerSession debuggerSession;
    private final JDWPInstrument instrument;
    private Ids<Object> ids;
    private JDWPContext context;
    private final VirtualMachine vm;
    private Debugger debugger;
    private final GCPrevention gcPrevention;
    private final ThreadSuspension threadSuspension;
    private final EventFilters eventFilters;
    private VMEventListener eventListener;
    private TruffleContext truffleContext;
    private Object previous;

    public DebuggerController(JDWPInstrument instrument) {
        this.instrument = instrument;
        this.vm = new VirtualMachineImpl();
        this.gcPrevention = new GCPrevention();
        this.threadSuspension = new ThreadSuspension();
        this.eventFilters = new EventFilters();
    }

    public void initialize(Debugger debug, JDWPOptions jdwpOptions, JDWPContext jdwpContext) {
        this.debugger = debug;
        this.options = jdwpOptions;
        this.context = jdwpContext;
        this.ids = jdwpContext.getIds();
        this.eventListener = new VMEventListenerImpl(this);

        // setup the debug session object early to make sure instrumentable nodes are materialized
        debuggerSession = debug.startSession(new SuspendedCallbackImpl(), SourceElement.ROOT, SourceElement.STATEMENT);
        debuggerSession.setSteppingFilter(SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true).build());

        instrument.init(jdwpContext);
    }

    public void reInitialize() {
        initialize(debugger, options, context);
    }

    public JDWPContext getContext() {
        return context;
    }

    public JDWPInstrument getInstrument() {
        return instrument;
    }

    public SuspendedInfo getSuspendedInfo(Object thread) {
        return suspendedInfos.get(thread);
    }

    public boolean shouldWaitForAttach() {
        return options.suspend;
    }

    public int getListeningPort() {
        return Integer.parseInt(options.address);
    }

    public String getTransport() {
        return options.transport;
    }

    public void setCommandRequestId(Object thread, int commandRequestId, byte suspendPolicy) {
        JDWPLogger.log("Adding step command request in thread %s with ID %s", JDWPLogger.LogLevel.STEPPING, getThreadName(thread), commandRequestId);
        commandRequestIds.put(thread, new SteppingInfo(commandRequestId, suspendPolicy));
    }

    /**
     * Installs a line breakpoint within a given method.
     * 
     * @param command the command that represents the breakpoint
     */
    public void submitLineBreakpoint(DebuggerCommand command) {
        SourceLocation location = command.getSourceLocation();
        try {
            Breakpoint bp = Breakpoint.newBuilder(location.getSource()).lineIs(location.getLineNumber()).build();
            bp.setEnabled(true);
            int ignoreCount = command.getRequestFilter().getIgnoreCount();
            if (ignoreCount > 0) {
                bp.setIgnoreCount(ignoreCount);
            }
            mapBreakpoint(bp, command.getBreakpointInfo());
            debuggerSession.install(bp);
            JDWPLogger.log("Breakpoint submitted at %s", JDWPLogger.LogLevel.STEPPING, bp.getLocationDescription());

        } catch (NoSuchSourceLineException ex) {
            // perhaps the debugger's view on the source is out of sync, in which case
            // the bytecode and source does not match.
            JDWPLogger.log("Failed submitting breakpoint at non-existing location: %s", JDWPLogger.LogLevel.ALL, location);
        }
    }

    public void submitMethodEntryBreakpoint(DebuggerCommand debuggerCommand) {
        // method entry breakpoints are limited per class, so we must
        // install a first line breakpoint into each method in the class
        KlassRef[] klasses = debuggerCommand.getRequestFilter().getKlassRefPatterns();
        List<Breakpoint> breakpoints = new ArrayList<>();
        for (KlassRef klass : klasses) {
            for (MethodRef method : klass.getDeclaredMethods()) {
                int line = method.getFirstLine();
                Breakpoint bp;
                if (line != -1) {
                    bp = Breakpoint.newBuilder(method.getSource()).lineIs(line).build();
                } else {
                    bp = Breakpoint.newBuilder(method.getSource().createUnavailableSection()).build();
                }
                breakpoints.add(bp);
            }
        }
        BreakpointInfo breakpointInfo = debuggerCommand.getBreakpointInfo();
        for (Breakpoint breakpoint : breakpoints) {
            mapBreakpoint(breakpoint, breakpointInfo);
            debuggerSession.install(breakpoint);
        }
    }

    public void submitExceptionBreakpoint(DebuggerCommand command) {
        Breakpoint bp = Breakpoint.newExceptionBuilder(command.getBreakpointInfo().isCaught(), command.getBreakpointInfo().isUnCaught()).build();
        bp.setEnabled(true);
        int ignoreCount = command.getBreakpointInfo().getFilter().getIgnoreCount();
        if (ignoreCount > 0) {
            bp.setIgnoreCount(ignoreCount);
        }
        mapBreakpoint(bp, command.getBreakpointInfo());
        debuggerSession.install(bp);
        JDWPLogger.log("exception breakpoint submitted", JDWPLogger.LogLevel.STEPPING);
    }

    @CompilerDirectives.TruffleBoundary
    private void mapBreakpoint(Breakpoint bp, BreakpointInfo info) {
        breakpointInfos.put(bp, info);
        info.addBreakpoint(bp);
    }

    public void clearBreakpoints() {
        for (Breakpoint breakpoint : debuggerSession.getBreakpoints()) {
            breakpoint.dispose();
        }
    }

    public void stepOver(RequestFilter filter) {
        Object thread = filter.getStepInfo().getGuestThread();
        JDWPLogger.log("STEP_OVER for thread: %s", JDWPLogger.LogLevel.STEPPING, getThreadName(thread));

        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null && !(susp instanceof UnknownSuspendedInfo)) {
            // check if we're at the last line in a method
            // if so, we need to STEP_OUT to reach the caller
            // location
            CallFrame currentFrame = susp.getStackFrames()[0];
            MethodRef method = (MethodRef) ids.fromId((int) currentFrame.getMethodId());
            if (method.isLastLine(currentFrame.getCodeIndex())) {
                doStepOut(susp);
            } else {
                susp.getEvent().prepareStepOver(STEP_CONFIG);
            }
            susp.recordStep(DebuggerCommand.Kind.STEP_OVER);
        } else {
            JDWPLogger.log("NOT STEPPING OVER for thread: %s", JDWPLogger.LogLevel.STEPPING, getThreadName(thread));
        }
    }

    public void stepInto(RequestFilter filter) {
        Object thread = filter.getStepInfo().getGuestThread();
        JDWPLogger.log("STEP_INTO for thread: %s", JDWPLogger.LogLevel.STEPPING, getThreadName(thread));

        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null && !(susp instanceof UnknownSuspendedInfo)) {
            susp.getEvent().prepareStepInto(STEP_CONFIG);
            susp.recordStep(DebuggerCommand.Kind.STEP_INTO);
        } else {
            JDWPLogger.log("not STEPPING INTO for thread: %s", JDWPLogger.LogLevel.STEPPING, getThreadName(thread));
        }
    }

    public void stepOut(RequestFilter filter) {
        Object thread = filter.getStepInfo().getGuestThread();
        JDWPLogger.log("STEP_OUT for thread: %s", JDWPLogger.LogLevel.STEPPING, getThreadName(thread));

        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null && !(susp instanceof UnknownSuspendedInfo)) {
            doStepOut(susp);
        } else {
            JDWPLogger.log("not STEPPING OUT for thread: %s", JDWPLogger.LogLevel.STEPPING, getThreadName(thread));
        }
    }

    private void doStepOut(SuspendedInfo susp) {
        // There are two different cases for step out, 1) step out and land
        // on the same line, enabling step into next method on line, and 2)
        // step over the callee line when there are no further method calls
        // on the line. Ask the guest language which one to use
        RootNode callerRoot = susp.getCallerRootNode();

        // since the below method uses stack frame iteration it must be called
        // on the correct thread
        ThreadJob job = new ThreadJob(susp.getThread(), new Callable<Object>() {
            @Override
            public Object call() {
                return context.moreMethodCallsOnLine(callerRoot);
            }
        });
        postJobForThread(job);
        boolean hasMoreMethodCallsOnLine = (boolean) job.getResult().getResult();
        if (hasMoreMethodCallsOnLine) {
            susp.getEvent().prepareStepOut(STEP_CONFIG);
        } else {
            susp.getEvent().prepareStepOut(STEP_CONFIG).prepareStepOver(STEP_CONFIG);
        }
        susp.recordStep(DebuggerCommand.Kind.STEP_OUT);
    }

    public void resume(Object thread, boolean sessionClosed) {
        JDWPLogger.log("Called resume thread: %s with suspension count: %d", JDWPLogger.LogLevel.THREAD, getThreadName(thread), threadSuspension.getSuspensionCount(thread));

        if (threadSuspension.getSuspensionCount(thread) == 0) {
            // already running, so nothing to do
            return;
        }

        threadSuspension.resumeThread(thread);
        int suspensionCount = threadSuspension.getSuspensionCount(thread);

        if (suspensionCount == 0) {
            // only resume when suspension count reaches 0

            if (!isStepping(thread)) {
                if (!sessionClosed) {
                    try {
                        JDWPLogger.log("calling underlying resume method for thread: %s", JDWPLogger.LogLevel.THREAD, getThreadName(thread));
                        debuggerSession.resume(getContext().asHostThread(thread));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to resume thread: " + getThreadName(thread), e);
                    }
                }

                // OK, this is a pure resume call, so clear suspended info
                JDWPLogger.log("pure resume call, clearing suspended info on: %s", JDWPLogger.LogLevel.THREAD, getThreadName(thread));

                suspendedInfos.put(thread, null);
            }

            SimpleLock lock = getSuspendLock(thread);
            synchronized (lock) {
                JDWPLogger.log("Waiking up thread: %s", JDWPLogger.LogLevel.THREAD, getThreadName(thread));
                lock.release();
                lock.notifyAll();
                threadSuspension.removeHardSuspendedThread(thread);
            }
        } else {
            JDWPLogger.log("Not resuming thread: %s with suspension count: %d", JDWPLogger.LogLevel.THREAD, getThreadName(thread), threadSuspension.getSuspensionCount(thread));
        }
    }

    private String getThreadName(Object thread) {
        return getContext().getThreadName(thread);
    }

    private boolean isStepping(Object thread) {
        return commandRequestIds.get(thread) != null;
    }

    public void resumeAll(boolean sessionClosed) {
        JDWPLogger.log("Called resumeAll:", JDWPLogger.LogLevel.THREAD);

        for (Object thread : getContext().getAllGuestThreads()) {
            resume(thread, sessionClosed);
        }
    }

    public void suspend(Object thread) {
        JDWPLogger.log("suspend called for thread: %s with suspension count %d", JDWPLogger.LogLevel.THREAD, getThreadName(thread), threadSuspension.getSuspensionCount(thread));

        if (threadSuspension.getSuspensionCount(thread) > 0) {
            // already suspended, so only increase the suspension count
            threadSuspension.suspendThread(thread);
            return;
        }

        try {
            JDWPLogger.log("State: %s", JDWPLogger.LogLevel.THREAD, getContext().asHostThread(thread).getState());
            JDWPLogger.log("calling underlying suspend method for thread: %s", JDWPLogger.LogLevel.THREAD, getThreadName(thread));
            debuggerSession.suspend(getContext().asHostThread(thread));

            boolean suspended = threadSuspension.getSuspensionCount(thread) != 0;
            JDWPLogger.log("suspend success: %b", JDWPLogger.LogLevel.THREAD, suspended);

            // quite often the Debug API will not call back the onSuspend method in time,
            // even if the thread is executing. If the thread is blocked or waiting we still need
            // to suspend it, thus we manage this with a hard suspend mechanism
            threadSuspension.addHardSuspendedThread(thread);
            suspendedInfos.put(thread, new UnknownSuspendedInfo(thread, getContext()));
        } catch (Exception e) {
            JDWPLogger.log("not able to suspend thread: %s", JDWPLogger.LogLevel.THREAD, getThreadName(thread));
        }
    }

    /**
     * Immediately suspend the current guest thread at its current location. Typically, this method
     * is used in response to class prepare events.
     * 
     * @param eventThread the guest thread which must correspond to the current host thread
     * @param suspendPolicy the policy for which threads to suspend
     * @param callBack a callback that is to be run when threads have been marked as suspended
     */
    public void immediateSuspend(Object eventThread, byte suspendPolicy, Callable<Void> callBack) {
        assert eventThread == context.asGuestThread(Thread.currentThread());

        switch (suspendPolicy) {
            case SuspendStrategy.ALL:
                // suspend all but the current thread
                // at next execution point
                for (Object thread : getContext().getAllGuestThreads()) {
                    if (context.asGuestThread(Thread.currentThread()) != thread) {
                        suspend(thread);
                    }
                }
                // immediately suspend the event thread
                suspend(null, eventThread, SuspendStrategy.EVENT_THREAD, Collections.singletonList(callBack), null, false);
                break;
            case SuspendStrategy.EVENT_THREAD:
                // immediately suspend the event thread
                suspend(null, eventThread, SuspendStrategy.EVENT_THREAD, Collections.singletonList(callBack), null, false);
                break;
        }
    }

    public void suspendAll() {
        JDWPLogger.log("Called suspendAll", JDWPLogger.LogLevel.THREAD);

        for (Object thread : getContext().getAllGuestThreads()) {
            suspend(thread);
        }
    }

    private SimpleLock getSuspendLock(Object thread) {
        SimpleLock lock = suspendLocks.get(thread);
        if (lock == null) {
            lock = new SimpleLock();
            suspendLocks.put(thread, lock);
        }
        return lock;
    }

    public void disposeDebugger(boolean prepareReconnect) {
        // Creating a new thread, because the reset method
        // will interrupt all active jdwp threads, which might
        // include the current one if we received a DISPOSE command.
        new Thread(new Runnable() {
            @Override
            public void run() {
                eventListener.vmDied();
                instrument.reset(prepareReconnect);
            }
        }).start();
    }

    public void endSession() {
        debuggerSession.close();
    }

    public JDWPOptions getOptions() {
        return options;
    }

    public void prepareFieldBreakpoint(FieldBreakpointEvent event) {
        fieldBreakpointExpected.put(Thread.currentThread(), event);
    }

    public void prepareMethodBreakpoint(MethodBreakpointEvent event) {
        methodBreakpointExpected.put(Thread.currentThread(), event);
    }

    public VirtualMachine getVirtualMachine() {
        return vm;
    }

    public GCPrevention getGCPrevention() {
        return gcPrevention;
    }

    public ThreadSuspension getThreadSuspension() {
        return threadSuspension;
    }

    public EventFilters getEventFilters() {
        return eventFilters;
    }

    public VMEventListener getEventListener() {
        return eventListener;
    }

    public boolean enterTruffleContext() {
        if (previous == null && truffleContext != null) {
            previous = truffleContext.enter();
            return true;
        }
        return false;
    }

    public void leaveTruffleContext() {
        if (truffleContext != null) {
            truffleContext.leave(previous);
            previous = null;
        }
    }

    @Override
    public void onLanguageContextInitialized(TruffleContext con, @SuppressWarnings("unused") LanguageInfo language) {
        truffleContext = con;
    }

    public void suspend(CallFrame currentFrame, Object thread, byte suspendPolicy, List<Callable<Void>> jobs, SteppingInfo steppingInfo, boolean breakpointHit) {
        JDWPLogger.log("suspending from callback in thread: %s", JDWPLogger.LogLevel.THREAD, getThreadName(thread));

        // before sending any events to debugger, make sure to mark
        // the thread lock as locked, in case a resume command happens
        // shortly thereafter, with the risk of a race (lost notify)
        getSuspendLock(thread).acquire();

        switch (suspendPolicy) {
            case SuspendStrategy.NONE:
                runJobs(jobs);
                break;
            case SuspendStrategy.EVENT_THREAD:
                JDWPLogger.log("Suspend EVENT_THREAD", JDWPLogger.LogLevel.THREAD);

                threadSuspension.suspendThread(thread);
                runJobs(jobs);
                suspendEventThread(currentFrame, thread, steppingInfo, breakpointHit);
                break;
            case SuspendStrategy.ALL:
                JDWPLogger.log("Suspend ALL", JDWPLogger.LogLevel.THREAD);

                Thread suspendThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // suspend other threads
                        for (Object activeThread : getContext().getAllGuestThreads()) {
                            if (activeThread != thread) {
                                JDWPLogger.log("Request thread suspend for other thread: %s", JDWPLogger.LogLevel.THREAD, getThreadName(activeThread));

                                DebuggerController.this.suspend(activeThread);
                            }
                        }
                        // send any breakpoint events here, since now all threads that are
                        // expected to be suspended
                        // have increased suspension count
                        runJobs(jobs);
                    }
                });
                threadSuspension.suspendThread(thread);
                suspendThread.start();
                suspendEventThread(currentFrame, thread, steppingInfo, breakpointHit);
                break;
        }
    }

    private static void runJobs(List<Callable<Void>> jobs) {
        for (Callable<Void> job : jobs) {
            try {
                job.call();
            } catch (Exception e) {
                throw new RuntimeException("failed to send event to debugger", e);
            }
        }
    }

    private void suspendEventThread(CallFrame currentFrame, Object thread, SteppingInfo info, boolean breakpointHit) {
        JDWPLogger.log("Suspending event thread: %s with new suspension count: %d", JDWPLogger.LogLevel.THREAD, getThreadName(thread), threadSuspension.getSuspensionCount(thread));

        // if during stepping, send a step completed event back to the debugger
        if (info != null && !breakpointHit) {
            eventListener.stepCompleted(info.getRequestId(), info.getSuspendPolicy(), thread, currentFrame);
        }

        // no reason to hold a hard suspension status, since now
        // we have the actual suspension status and suspended information
        threadSuspension.removeHardSuspendedThread(thread);

        lockThread(thread);
    }

    private void lockThread(Object thread) {
        SimpleLock lock = getSuspendLock(thread);

        synchronized (lock) {
            try {
                // in case a thread job is already posted on this thread
                checkThreadJobsAndRun(thread);
                while (lock.isLocked()) {
                    JDWPLogger.log("lock.wait() for thread: %s", JDWPLogger.LogLevel.THREAD, getThreadName(thread));
                    lock.wait();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("not able to suspend thread: " + getThreadName(thread), e);
            }
        }

        checkThreadJobsAndRun(thread);

        JDWPLogger.log("lock wakeup for thread: %s", JDWPLogger.LogLevel.THREAD, getThreadName(thread));
    }

    private void checkThreadJobsAndRun(Object thread) {
        if (threadJobs.containsKey(thread)) {
            // re-acquire the thread lock after completing
            // the job, to avoid the thread resuming.
            getSuspendLock(thread).acquire();
            // a thread job was posted on this thread
            // only wake up to perform the job a go back to sleep
            ThreadJob job = threadJobs.remove(thread);
            byte suspensionStrategy = job.getSuspensionStrategy();

            if (suspensionStrategy == SuspendStrategy.ALL) {
                Object[] allThreads = context.getAllGuestThreads();
                // resume all threads during invocation og method to avoid potential deadlocks
                for (Object activeThread : allThreads) {
                    if (activeThread != thread) {
                        resume(activeThread, false);
                    }
                }
                // perform the job on this thread
                job.runJob();
                // suspend all other threads after the invocation
                for (Object activeThread : allThreads) {
                    if (activeThread != thread) {
                        suspend(thread);
                    }
                }
            } else {
                job.runJob();
            }

            lockThread(thread);
        }
    }

    public void postJobForThread(ThreadJob job) {
        threadJobs.put(job.getThread(), job);
        SimpleLock lock = getSuspendLock(job.getThread());
        synchronized (lock) {
            lock.release();
            lock.notifyAll();
        }
    }

    private class SuspendedCallbackImpl implements SuspendedCallback {

        public static final String DEBUG_VALUE_GET = "get";
        public static final String DEBUG_EXCEPTION_GET_RAW_EXCEPTION = "getRawException";

        @Override
        public void onSuspend(SuspendedEvent event) {
            Object currentThread = getContext().asGuestThread(Thread.currentThread());
            JDWPLogger.log("Suspended at: %s in thread: %s", JDWPLogger.LogLevel.STEPPING, event.getSourceSection().toString(), getThreadName(currentThread));
            SteppingInfo steppingInfo = commandRequestIds.remove(currentThread);

            if (steppingInfo != null) {
                // get the top frame for chekcing instance filters
                CallFrame[] callFrames = createCallFrames(ids.getIdAsLong(currentThread), event.getStackFrames(), 1);
                if (checkExclusionFilters(steppingInfo, event, currentThread, callFrames[0])) {
                    JDWPLogger.log("not suspending here: %s", JDWPLogger.LogLevel.STEPPING, event.getSourceSection());
                    // continue stepping until completed
                    commandRequestIds.put(currentThread, steppingInfo);
                    return;
                }
            }

            CallFrame[] callFrames = createCallFrames(ids.getIdAsLong(currentThread), event.getStackFrames(), -1);

            RootNode callerRootNode = null;
            int i = 0;
            for (DebugStackFrame stackFrame : event.getStackFrames()) {
                if (i == 1) {
                    callerRootNode = findCurrentRoot(stackFrame);
                }
                i++;
            }

            SuspendedInfo suspendedInfo = new SuspendedInfo(event, callFrames, currentThread, callerRootNode);
            suspendedInfos.put(currentThread, suspendedInfo);

            byte suspendPolicy = SuspendStrategy.EVENT_THREAD;

            // collect any events that need to be sent to the debugger once we're done here
            List<Callable<Void>> jobs = new ArrayList<>();

            boolean hit = false;
            for (Breakpoint bp : event.getBreakpoints()) {

                BreakpointInfo info = breakpointInfos.get(bp);
                suspendPolicy = info.getSuspendPolicy();

                if (info.isLineBreakpoint()) {
                    hit = true;
                    // check if breakpoint request limited to a specific thread
                    Object thread = info.getThread();
                    if (thread == null || thread == currentThread) {
                        jobs.add(new Callable<Void>() {
                            @Override
                            public Void call() {
                                eventListener.breakpointHit(info, callFrames[0], currentThread);
                                return null;
                            }
                        });
                    }
                } else if (info.isExceptionBreakpoint()) {
                    // get the specific exception type if any
                    KlassRef klass = info.getKlass(); // null means no filtering
                    Throwable exception = getRawException(event.getException());
                    if (exception == null) {
                        JDWPLogger.log("Unable to retrieve raw exception for %s", JDWPLogger.LogLevel.ALL, event.getException());
                        // failed to get the raw exception, so don't suspend here.
                        return;
                    }

                    Object guestException = getContext().getGuestException(exception);
                    JDWPLogger.log("checking exception breakpoint for exception: %s", JDWPLogger.LogLevel.STEPPING, exception);
                    // TODO(Gregersen) - rewrite this when instanceof implementation in Truffle is
                    // completed
                    // See /browse/GR-10371
                    // Currently, the Truffle Debug API doesn't filter on
                    // type, so we end up here having to check also, the
                    // ignore count set on the breakpoint will not work
                    // properly due to this.
                    // we need to do a real type check here, since subclasses
                    // of the specified exception should also hit.
                    if (klass == null) {
                        // always hit when broad exception filter is used
                        hit = true;
                    } else if (klass == null || getContext().isInstanceOf(guestException, klass)) {
                        JDWPLogger.log("Exception type matched the klass type: %s", JDWPLogger.LogLevel.STEPPING, klass.getNameAsString());
                        // check filters if we should not suspend
                        Pattern[] positivePatterns = info.getFilter().getIncludePatterns();
                        // verify include patterns
                        if (positivePatterns == null || positivePatterns.length == 0 || matchLocation(positivePatterns, callFrames[0])) {
                            // verify exclude patterns
                            Pattern[] negativePatterns = info.getFilter().getExcludePatterns();
                            if (negativePatterns == null || negativePatterns.length == 0 || !matchLocation(negativePatterns, callFrames[0])) {
                                hit = true;
                            }
                        }
                    }
                    if (hit) {
                        JDWPLogger.log("Breakpoint hit in thread: %s", JDWPLogger.LogLevel.STEPPING, getThreadName(currentThread));

                        jobs.add(new Callable<Void>() {
                            @Override
                            public Void call() {
                                eventListener.exceptionThrown(info, currentThread, guestException, callFrames);
                                return null;
                            }
                        });
                    } else {
                        // don't suspend here
                        suspendedInfos.put(currentThread, null);
                        return;
                    }
                }
            }

            // check if suspended for a field breakpoint
            FieldBreakpointEvent fieldEvent = fieldBreakpointExpected.remove(Thread.currentThread());
            if (fieldEvent != null) {
                FieldBreakpointInfo info = fieldEvent.getInfo();
                if (info.isAccessBreakpoint()) {
                    jobs.add(new Callable<Void>() {
                        @Override
                        public Void call() {
                            eventListener.fieldAccessBreakpointHit(fieldEvent, currentThread, callFrames[0]);
                            return null;
                        }
                    });
                } else if (info.isModificationBreakpoint()) {
                    jobs.add(new Callable<Void>() {
                        @Override
                        public Void call() {
                            eventListener.fieldModificationBreakpointHit(fieldEvent, currentThread, callFrames[0]);
                            return null;
                        }
                    });
                }
            }
            // check if suspended for a method breakpoint
            MethodBreakpointEvent methodEvent = methodBreakpointExpected.remove(Thread.currentThread());
            if (methodEvent != null) {
                jobs.add(new Callable<Void>() {
                    @Override
                    public Void call() {
                        eventListener.methodBreakpointHit(methodEvent, currentThread, callFrames[0]);
                        return null;
                    }
                });
            }

            // now, suspend the current thread until resumed by e.g. a debugger command
            suspend(callFrames[0], currentThread, suspendPolicy, jobs, steppingInfo, hit);
        }

        private boolean matchLocation(Pattern[] patterns, CallFrame callFrame) {
            KlassRef klass = (KlassRef) ids.fromId((int) callFrame.getClassId());

            for (Pattern pattern : patterns) {
                JDWPLogger.log("Matching klass: %s against pattern: %s", JDWPLogger.LogLevel.STEPPING, klass.getNameAsString(), pattern.pattern());
                if (pattern.pattern().matches(klass.getNameAsString().replace('/', '.'))) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkExclusionFilters(SteppingInfo info, SuspendedEvent event, Object thread, CallFrame frame) {
            if (info != null) {
                RequestFilter requestFilter = eventFilters.getRequestFilter(info.getRequestId());

                if (requestFilter != null && requestFilter.getStepInfo() != null) {
                    // we're currently stepping, so check if suspension point
                    // matches any exclusion filters
                    if (requestFilter.getThisFilterId() != 0) {
                        Object filterObject = context.getIds().fromId((int) requestFilter.getThisFilterId());
                        Object thisObject = frame.getThisValue();
                        if (filterObject != thisObject) {
                            continueStepping(event, thread);
                            return true;
                        }
                    }

                    KlassRef klass = (KlassRef) context.getIds().fromId((int) frame.getClassId());

                    if (klass != null && requestFilter.isKlassExcluded(klass)) {
                        // should not suspend here then, tell the event to keep going
                        continueStepping(event, thread);
                        return true;
                    }
                }
            }
            return false;
        }

        private void continueStepping(SuspendedEvent event, Object thread) {
            SuspendedInfo susp = suspendedInfos.get(thread);
            switch (susp.getStepKind()) {
                case STEP_INTO:
                    // stepping into unwanted code which was filtered
                    // so step out and try step into again
                    event.prepareStepOut(STEP_CONFIG).prepareStepInto(STEP_CONFIG);
                    break;
                case STEP_OVER:
                    event.prepareStepOver(STEP_CONFIG);
                    break;
                case STEP_OUT:
                    doStepOut(susp);
                    break;
                default:
                    break;
            }
        }

        private CallFrame[] createCallFrames(long threadId, Iterable<DebugStackFrame> stackFrames, int frameLimit) {
            LinkedList<CallFrame> list = new LinkedList<>();
            int frameCount = 0;

            for (DebugStackFrame frame : stackFrames) {
                // byte type tag, long classId, long methodId, long codeIndex

                if (frame.getSourceSection() == null) {
                    continue;
                }

                long klassId;
                long methodId;
                byte typeTag;
                long codeIndex;

                RootNode root = findCurrentRoot(frame);
                if (root == null) {
                    // since we can't lookup the root node, we have to
                    // construct a jdwp-like location from the frame
                    // TODO(Gregersen) - add generic polyglot jdwp frame representation
                    continue;
                } else {
                    MethodRef method = getContext().getMethodFromRootNode(root);
                    KlassRef klass = method.getDeclaringKlass();

                    klassId = ids.getIdAsLong(klass);
                    methodId = ids.getIdAsLong(method);
                    typeTag = TypeTag.getKind(klass);
                    // try to get the precise code index directly
                    codeIndex = context.getCurrentBCI(root);

                    if (codeIndex == -1) {
                        // could not obtain precise bci from the root node
                        // try to get an encapsulated source section from the frame
                        SourceSection sourceSection = root.getEncapsulatingSourceSection();
                        if (sourceSection.hasLines()) {
                            if (sourceSection.getStartLine() != sourceSection.getEndLine()) {
                                JDWPLogger.log("Not able to get a precise encapsulated source section", JDWPLogger.LogLevel.ALL);
                            }
                            codeIndex = method.getBCIFromLine(sourceSection.getStartLine());
                        } else {
                            // no lines! Fall back to bci 0 then
                            codeIndex = 0;
                        }
                    }
                }

                DebugScope scope = frame.getScope();

                Object thisValue = null;
                ArrayList<Object> realVariables = new ArrayList<>();

                if (scope != null) {
                    Iterator<DebugValue> variables = scope.getDeclaredValues().iterator();
                    while (variables.hasNext()) {
                        DebugValue var = variables.next();
                        if ("this".equals(var.getName())) {
                            // get the real object reference and register it with Id
                            thisValue = getRealValue(var);
                        } else {
                            // add to variables list
                            Object realValue = getRealValue(var);
                            realVariables.add(realValue);
                        }
                    }
                }
                list.addLast(new CallFrame(threadId, typeTag, klassId, methodId, codeIndex, thisValue, realVariables.toArray(new Object[realVariables.size()])));
                frameCount++;
                if (frameLimit != -1 && frameCount >= frameLimit) {
                    return list.toArray(new CallFrame[list.size()]);
                }
            }
            return list.toArray(new CallFrame[list.size()]);
        }

        private Object getRealValue(DebugValue value) {
            // TODO(Gregersen) - hacked in with reflection currently
            // tracked by /browse/GR-19813
            // awaiting a proper API for this
            try {
                java.lang.reflect.Method getMethod = DebugValue.class.getDeclaredMethod(DEBUG_VALUE_GET);
                getMethod.setAccessible(true);
                return getMethod.invoke(value);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                JDWPLogger.log("Failed to reflectively get real value for %s", JDWPLogger.LogLevel.ALL, value);
                // use a static object to signal that the value could not be retrieved
                // callers will send appropriate jdwp error codes when discovered
                return JDWP.INVALID_VALUE;
            }
        }

        private Throwable getRawException(DebugException exception) {
            // TODO(Gregersen) - hacked in with reflection currently
            // tracked by /browse/GR-19815
            try {
                Method method = DebugException.class.getDeclaredMethod(DEBUG_EXCEPTION_GET_RAW_EXCEPTION);
                method.setAccessible(true);
                return (Throwable) method.invoke(exception);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                JDWPLogger.log("Failed to reflectively get raw exception for %s", JDWPLogger.LogLevel.ALL, exception);
                // null signals that we're unable to retrieve the raw exception instance
                return null;
            }
        }
    }

    private static RootNode findCurrentRoot(DebugStackFrame frame) {
        // TODO(Gregersen) - hacked in with reflection currently
        // tracked by /browse/GR-19814
        // for now just use reflection to get the current root
        try {
            java.lang.reflect.Method getRoot = DebugStackFrame.class.getDeclaredMethod(DEBUG_STACK_FRAME_FIND_CURRENT_ROOT);
            getRoot.setAccessible(true);
            return (RootNode) getRoot.invoke(frame);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            JDWPLogger.log("Failed to reflectively find current root for %s", JDWPLogger.LogLevel.ALL, frame);
            // null signals that we're unable to retrieve the current root instance
            return null;
        }
    }

    @Override
    public void onContextCreated(@SuppressWarnings("unused") TruffleContext con) {

    }

    @Override
    public void onLanguageContextCreated(@SuppressWarnings("unused") TruffleContext con, @SuppressWarnings("unused") LanguageInfo language) {

    }

    @Override
    public void onLanguageContextFinalized(@SuppressWarnings("unused") TruffleContext con, @SuppressWarnings("unused") LanguageInfo language) {

    }

    @Override
    public void onLanguageContextDisposed(@SuppressWarnings("unused") TruffleContext con, @SuppressWarnings("unused") LanguageInfo language) {

    }

    @Override
    public void onContextClosed(@SuppressWarnings("unused") TruffleContext con) {

    }

}
