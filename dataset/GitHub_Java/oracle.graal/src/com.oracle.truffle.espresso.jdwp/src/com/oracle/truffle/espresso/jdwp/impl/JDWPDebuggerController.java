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
import com.oracle.truffle.api.TruffleLanguage;
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
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.jdwp.api.BreakpointInfo;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.JDWPOptions;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.VMEventListeners;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;


public class JDWPDebuggerController {

    private static final StepConfig STEP_CONFIG = StepConfig.newBuilder().suspendAnchors(SourceElement.ROOT, SuspendAnchor.AFTER).build();

    private JDWPOptions options;
    private DebuggerSession debuggerSession;
    private final JDWPInstrument instrument;
    private TruffleLanguage.Env languageEnv;
    private Map<Object, Object> suspendLocks = new HashMap<>();
    private Map<Object, SuspendedInfo> suspendedInfos = new HashMap<>();
    private Map<Object, Integer> commandRequestIds = new HashMap<>();
    private final Map<Object, ThreadJob> threadJobs = new HashMap<>();
    private HashMap<Object, FieldBreakpointEvent> fieldBreakpointExpected = new HashMap<>();

    private Ids<Object> ids;

    // justification for this being a map is that lookups only happen when at a breakpoint
    private Map<Breakpoint, BreakpointInfo> breakpointInfos = new HashMap<>();
    private JDWPContext context;

    public JDWPDebuggerController(JDWPInstrument instrument) {
        this.instrument = instrument;
    }

    public void initialize(TruffleLanguage.Env languageEnv, JDWPOptions jdwpOptions, JDWPContext context, boolean reconnect) {
        this.options = jdwpOptions;
        this.languageEnv = languageEnv;
        this.context = context;
        this.ids = context.getIds();

        // setup the debugger session object early to make sure instrumentable nodes are materialized
        Debugger debugger = languageEnv.lookup(languageEnv.getInstruments().get("debugger"), Debugger.class);
        debuggerSession = debugger.startSession(new SuspendedCallbackImpl(), SourceElement.ROOT, SourceElement.STATEMENT);
        debuggerSession.setSteppingFilter(SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true).build());
        debuggerSession.suspendNextExecution();

        if (!reconnect) {
            instrument.init(context);
        }
    }

    public void reInitialize() {
        initialize(languageEnv, options, context, true);
    }

    public JDWPContext getContext() {
        return instrument.getContext();
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

    public void setCommandRequestId(Object thread, int commandRequestId) {
        commandRequestIds.put(thread, commandRequestId);
    }

    /**
     * Installs a line breakpoint within a given method.
     * @param command the command that represents the
     * breakpoint
     */
    public void submitLineBreakpoint(DebuggerCommand command) {
        SourceLocation location = command.getSourceLocation();
        try {
            Breakpoint bp = Breakpoint.newBuilder(location.getSource()).lineIs(location.getLineNumber()).build();
            bp.setEnabled(true);
            int ignoreCount = command.getBreakpointInfo().getFilter().getIgnoreCount();
            if (ignoreCount > 0) {
                bp.setIgnoreCount(ignoreCount);
            }
            mapBrekpoint(bp, command.getBreakpointInfo());
            debuggerSession.install(bp);
            JDWPLogger.log("Breakpoint submitted at " + bp.getLocationDescription(), JDWPLogger.LogLevel.STEPPING);

        } catch (NoSuchSourceLineException ex) {
            // perhaps the debugger's view on the source is out of sync, in which case
            // the bytecode and source does not match.
        }
    }

    public void submitExceptionBreakpoint(DebuggerCommand command) {
        Breakpoint bp = Breakpoint.newExceptionBuilder(command.getBreakpointInfo().isCaught(), command.getBreakpointInfo().isUnCaught()).build();
        bp.setEnabled(true);
        int ignoreCount = command.getBreakpointInfo().getFilter().getIgnoreCount();
        if (ignoreCount > 0) {
            bp.setIgnoreCount(ignoreCount);
        }
        mapBrekpoint(bp, command.getBreakpointInfo());
        debuggerSession.install(bp);
        JDWPLogger.log("exception breakpoint submitted", JDWPLogger.LogLevel.STEPPING);
    }

    @CompilerDirectives.TruffleBoundary
    private void mapBrekpoint(Breakpoint bp, BreakpointInfo info) {
        breakpointInfos.put(bp, info);
        info.setBreakpoint(bp);
    }

    public void stepOver(Object thread) {
        JDWPLogger.log("STEP_OVER for thread: " + getThreadName(thread), JDWPLogger.LogLevel.STEPPING);;

        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null && !(susp instanceof UnknownSuspendedInfo)) {
            // check if we're at the last line in a method
            // if so, we need to STEP_OUT to reach the caller
            // location
            JDWPCallFrame currentFrame = susp.getStackFrames()[0];
            MethodRef method = (MethodRef) ids.fromId((int) currentFrame.getMethodId());
            if (method.isLastLine(currentFrame.getCodeIndex())) {
                susp.getEvent().prepareStepOut(STEP_CONFIG);// .prepareStepOver(STEP_CONFIG);
            } else {
                susp.getEvent().prepareStepOver(STEP_CONFIG);
            }
            susp.recordStep(DebuggerCommand.Kind.STEP_OVER);
        } else {
            JDWPLogger.log("NOT STEPPING OVER for thread: " + getThreadName(thread), JDWPLogger.LogLevel.STEPPING);
        }
    }

    public void stepInto(Object thread) {
        JDWPLogger.log("STEP_INTO for thread: " + getThreadName(thread), JDWPLogger.LogLevel.STEPPING);

        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null && !(susp instanceof UnknownSuspendedInfo)) {
            susp.getEvent().prepareStepInto(STEP_CONFIG);
            susp.recordStep(DebuggerCommand.Kind.STEP_INTO);
        } else {
            JDWPLogger.log("not STEPPING INTO for thread: " + getThreadName(thread), JDWPLogger.LogLevel.STEPPING);
        }
    }

    public void stepOut(Object thread) {
        JDWPLogger.log("STEP_OUT for thread: " + getThreadName(thread), JDWPLogger.LogLevel.STEPPING);

        SuspendedInfo susp = suspendedInfos.get(thread);
        if (susp != null && !(susp instanceof UnknownSuspendedInfo)) {
            susp.getEvent().prepareStepOut(STEP_CONFIG);
            susp.recordStep(DebuggerCommand.Kind.STEP_OUT);
        } else {
            JDWPLogger.log("not STEPPING OUT for thread: " + getThreadName(thread), JDWPLogger.LogLevel.STEPPING);
        }
    }

    public void resume(Object thread, boolean sessionClosed) {
        JDWPLogger.log("Called resume thread: " + getThreadName(thread) + " with suspension count: " + ThreadSuspension.getSuspensionCount(thread), JDWPLogger.LogLevel.THREAD);

        if (ThreadSuspension.getSuspensionCount(thread) == 0) {
            // already running, so nothing to do
            return;
        }

        ThreadSuspension.resumeThread(thread);
        int suspensionCount = ThreadSuspension.getSuspensionCount(thread);

        if (suspensionCount == 0) {
            // only resume when suspension count reaches 0

            if (!isStepping(thread)) {
                if (!sessionClosed) {
                    try {
                        JDWPLogger.log("calling underlying resume method for thread: " + getThreadName(thread), JDWPLogger.LogLevel.THREAD);
                        debuggerSession.resume(getContext().getGuest2HostThread(thread));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to resume thread: " + getThreadName(thread), e);
                    }
                }

                // OK, this is a pure resume call, so clear suspended info
                JDWPLogger.log("pure resume call, clearing suspended info on: " + getThreadName(thread), JDWPLogger.LogLevel.THREAD);

                suspendedInfos.put(thread, null);
            }

            Object lock = getSuspendLock(thread);
            synchronized (lock) {
                JDWPLogger.log("Waiking up thread: " + getThreadName(thread), JDWPLogger.LogLevel.THREAD);

                lock.notifyAll();
                ThreadSuspension.removeHardSuspendedThread(thread);
            }
        } else {
            JDWPLogger.log("Not resuming thread: " + getThreadName(thread) + " with suspension count: " + ThreadSuspension.getSuspensionCount(thread), JDWPLogger.LogLevel.THREAD);
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
        JDWPLogger.log("suspend called for thread: " + getThreadName(thread) + " with suspension count " + ThreadSuspension.getSuspensionCount(thread), JDWPLogger.LogLevel.THREAD);

        if (ThreadSuspension.getSuspensionCount(thread) > 0) {
            // already suspended
            return;
        }

        try {
            JDWPLogger.log("State: " + getContext().getGuest2HostThread(thread).getState(), JDWPLogger.LogLevel.THREAD);
            JDWPLogger.log("calling underlying suspend method for thread: " + getThreadName(thread), JDWPLogger.LogLevel.THREAD);
            debuggerSession.suspend(getContext().getGuest2HostThread(thread));

            boolean suspended = ThreadSuspension.getSuspensionCount(thread) != 0;
            JDWPLogger.log("suspend success: " + suspended, JDWPLogger.LogLevel.THREAD);

            // quite often the Debug API will not call back the onSuspend method in time,
            // even if the thread is executing. If the thread is blocked or waiting we still need
            // to suspend it, thus we manage this with a hard suspend mechanism
            ThreadSuspension.addHardSuspendedThread(thread);
            suspendedInfos.put(thread, new UnknownSuspendedInfo(thread, getContext()));
        } catch (Exception e) {
            System.err.println("not able to suspend thread: " + getThreadName(thread));
        }
    }

    public void suspendAll() {
        JDWPLogger.log("Called suspendAll", JDWPLogger.LogLevel.THREAD);

        for (Object thread : getContext().getAllGuestThreads()) {
            suspend(thread);
        }
    }

    private Object getSuspendLock(Object thread) {
        Object lock = suspendLocks.get(thread);
        if (lock == null) {
            lock = new Object();
            suspendLocks.put(thread, lock);
        }
        return lock;
    }

    public void disposeDebugger(boolean prepareForReconnect) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                instrument.reset(true);
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

    private class SuspendedCallbackImpl implements SuspendedCallback {

        public static final String DEBUG_VALUE_GET = "get";
        public static final String DEBUG_STACK_FRAME_FIND_CURRENT_ROOT = "findCurrentRoot";
        public static final String DEBUG_EXCEPTION_GET_RAW_EXCEPTION = "getRawException";

        @CompilerDirectives.CompilationFinal
        private boolean firstSuspensionCalled;

        @Override
        public void onSuspend(SuspendedEvent event) {
            if (!firstSuspensionCalled) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                firstSuspensionCalled = true;
                return;
            }

            Object currentThread = getContext().getHost2GuestThread(Thread.currentThread());
            JDWPLogger.log("Suspended at: " + event.getSourceSection().toString() + " in thread: " + getThreadName(currentThread), JDWPLogger.LogLevel.STEPPING);

            if (commandRequestIds.get(currentThread) != null) {
                if (checkExclusionFilters(event, currentThread)) {
                    JDWPLogger.log("not suspending here: " + event.getSourceSection(), JDWPLogger.LogLevel.STEPPING);
                    return;
                }
            }

            JDWPCallFrame[] callFrames = createCallFrames(ids.getIdAsLong(currentThread), event.getStackFrames());
            SuspendedInfo suspendedInfo = new SuspendedInfo(event, callFrames, currentThread);
            suspendedInfos.put(currentThread, suspendedInfo);

            byte suspendPolicy = SuspendStrategy.EVENT_THREAD;

            // collect any events that need to be sent to the debugger once we're done here
            List<Callable<Void>> jobs = new ArrayList<>();

            boolean hit = false;
            for (Breakpoint bp : event.getBreakpoints()) {
                //System.out.println("BP at suspension point: " + bp.getLocationDescription());

                BreakpointInfo info = breakpointInfos.get(bp);
                suspendPolicy = info.getSuspendPolicy();

                if (info.isLineBreakpoint()) {
                    // check if breakpoint request limited to a specific thread
                    Object thread = info.getThread();
                    if (thread == null || thread == currentThread) {
                        jobs.add(new Callable<Void>() {
                            @Override
                            public Void call() {
                                VMEventListeners.getDefault().breakpointHit(info, currentThread);
                                return null;
                            }
                        });
                    }
                } else if (info.isExceptionBreakpoint()) {
                    // get the specific exception type if any
                    KlassRef klass = info.getKlass();
                    Throwable exception = getRawException(event.getException());
                    Object guestException = getContext().getGuestException(exception);
                    JDWPLogger.log("checking exception breakpoint for exception: " + exception, JDWPLogger.LogLevel.STEPPING);
                    // TODO(Gregersen) - rewrite this when instanceof implementation in Truffle is completed
                    // Currently, the Truffle Debug API doesn't filter on type, so we end up here having to check
                    // also, the ignore count set on the breakpoint will not work properly due to this.
                    // we need to do a real type check here, since subclasses of the specified exception
                    // should also hit
                    if (klass == null || getContext().isInstanceOf(guestException, klass)) {
                        JDWPLogger.log("Exception type matched the klass type: " + klass.getNameAsString(), JDWPLogger.LogLevel.STEPPING);
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
                        JDWPLogger.log("Breakpoint hit in thread: " + getThreadName(currentThread), JDWPLogger.LogLevel.STEPPING);

                        jobs.add(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                VMEventListeners.getDefault().exceptionThrown(info, currentThread, guestException, callFrames[0]);
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
                        public Void call() throws Exception {
                            VMEventListeners.getDefault().fieldAccessBreakpointHit(fieldEvent, currentThread, callFrames[0]);
                            return null;
                        }
                    });
                } else if (info.isModificationBreakpoint()) {
                    jobs.add(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            VMEventListeners.getDefault().fieldModificationBreakpointHit(fieldEvent, currentThread, callFrames[0]);
                            return null;
                        }
                    });
                }
            }

            // now, suspend the current thread until resumed by e.g. a debugger command
            suspend(callFrames[0], currentThread, suspendPolicy, jobs);
        }

        private boolean matchLocation(Pattern[] patterns, JDWPCallFrame callFrame) {
            KlassRef klass = (KlassRef) ids.fromId((int) callFrame.getClassId());

            for (Pattern pattern : patterns) {
                JDWPLogger.log("Matching klass: " + klass.getNameAsString() + " against pattern: " + pattern.pattern(), JDWPLogger.LogLevel.STEPPING);
                if (pattern.pattern().matches(klass.getNameAsString().replace('/', '.')))
                    return true;
            }
            return false;
        }

        private boolean checkExclusionFilters(SuspendedEvent event, Object thread) {
            Integer id = commandRequestIds.get(thread);

            if (id != null) {
                RequestFilter requestFilter = EventFilters.getDefault().getRequestFilter(id);

                if (requestFilter != null && requestFilter.isStepping()) {
                    // we're currently stepping, so check if suspension point
                    // matches any exclusion filters

                    DebugStackFrame topFrame = event.getTopStackFrame();

                    if (topFrame.getSourceSection() != null) {
                        RootNode root = findCurrentRoot(topFrame);

                        KlassRef klass = getContext().getKlassFromRootNode(root);

                        if (klass != null && requestFilter.isKlassExcluded(klass)) {
                            // should not suspend here then, tell the event to keep going
                            continueStepping(event, thread);
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private void continueStepping(SuspendedEvent event, Object thread) {
            switch (suspendedInfos.get(thread).getStepKind()) {
                case STEP_INTO:
                    // stepping into unwanted code which was filtered
                    // so step out and try step into again
                    event.prepareStepOut(STEP_CONFIG).prepareStepInto(STEP_CONFIG);
                    break;
                case STEP_OVER:
                    event.prepareStepOver(STEP_CONFIG);
                    break;
                case STEP_OUT:
                    event.prepareStepOut(STEP_CONFIG);
                    break;
                default:
                    break;
            }
        }

        private JDWPCallFrame[] createCallFrames(long threadId, Iterable<DebugStackFrame> stackFrames) {
            LinkedList<JDWPCallFrame> list = new LinkedList<>();
            for (DebugStackFrame frame : stackFrames) {
                // byte type tag, long classId, long methodId, long codeIndex

                if (frame.getSourceSection() == null) {
                    continue;
                }

                RootNode root = findCurrentRoot(frame);
                KlassRef klass = getContext().getKlassFromRootNode(root);

                if (klass != null) {
                    MethodRef method = getContext().getMethodFromRootNode(root);

                    long klassId = ids.getIdAsLong(klass);
                    long methodId = ids.getIdAsLong(method);
                    byte typeTag = TypeTag.getKind(klass);
                    int line = frame.getSourceSection().getStartLine();

                    long codeIndex = method.getBCIFromLine(line);

                    DebugScope scope = frame.getScope();

                    Object thisValue = null;
                    ArrayList<Object> realVariables = new ArrayList<>();

                    if (scope != null ) {
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
                    //System.out.println("collected frame info for method: " + klass.getNameAsString() + "." + method.getNameAsString() + "(" + line + ") : BCI(" + codeIndex + ")") ;
                    list.addLast(new JDWPCallFrame(threadId, typeTag, klassId, methodId, codeIndex, thisValue, realVariables.toArray(new Object[realVariables.size()])));

                } else {
                    throw new RuntimeException("stack walking not implemented for root node type! " + root);
                }
            }
            return list.toArray(new JDWPCallFrame[list.size()]);
        }

        private Object getRealValue(DebugValue value) {
            // TODO(Gregersen) - hacked in with reflection currently
            // awaiting a proper API for this
            try {
                java.lang.reflect.Method getMethod = DebugValue.class.getDeclaredMethod(DEBUG_VALUE_GET);
                getMethod.setAccessible(true);
                return getMethod.invoke(value);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private RootNode findCurrentRoot(DebugStackFrame frame) {
            // TODO(Gregersen) - hacked in with reflection currently
            // for now just use reflection to get the current root
            try {
                java.lang.reflect.Method getRoot = DebugStackFrame.class.getDeclaredMethod(DEBUG_STACK_FRAME_FIND_CURRENT_ROOT);
                getRoot.setAccessible(true);
                return (RootNode) getRoot.invoke(frame);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private Throwable getRawException(DebugException exception) {
            // TODO(Gregersen) - hacked in with reflection currently
            try {
                Method method = DebugException.class.getDeclaredMethod(DEBUG_EXCEPTION_GET_RAW_EXCEPTION);
                method.setAccessible(true);
                return (Throwable) method.invoke(exception);
            } catch (Exception e) {
                e.printStackTrace();
                return exception;
            }
        }

        private void suspend(JDWPCallFrame currentFrame, Object thread, byte suspendPolicy, List<Callable<Void>> jobs) {
            JDWPLogger.log("suspending from callback in thread: " + getThreadName(thread), JDWPLogger.LogLevel.THREAD);

            switch(suspendPolicy) {
                case SuspendStrategy.NONE:
                    runJobs(jobs);
                    break;
                case SuspendStrategy.EVENT_THREAD:
                    JDWPLogger.log("Suspend EVENT_THREAD", JDWPLogger.LogLevel.THREAD);

                    ThreadSuspension.suspendThread(thread);
                    runJobs(jobs);
                    suspendEventThread(currentFrame, thread);
                    break;
                case SuspendStrategy.ALL:
                    JDWPLogger.log("Suspend ALL", JDWPLogger.LogLevel.THREAD);

                    Thread suspendThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // suspend other threads
                            for (Object activeThread : getContext().getAllGuestThreads()) {
                                if (activeThread != thread) {
                                    JDWPLogger.log("Request thread suspend for other thread: " + getThreadName(activeThread), JDWPLogger.LogLevel.THREAD);

                                    JDWPDebuggerController.this.suspend(activeThread);
                                }
                            }
                            // send any breakpoint events here, since now all threads that are expected to be suspended
                            // have increased suspension count
                            runJobs(jobs);
                        }
                    });
                    ThreadSuspension.suspendThread(thread);
                    suspendThread.start();
                    suspendEventThread(currentFrame, thread);
                    break;
            }
        }

        private void runJobs(List<Callable<Void>> jobs) {
            for (Callable<Void> job : jobs) {
                try {
                    job.call();
                } catch (Exception e) {
                    throw new RuntimeException("failed to send event to debugger", e);
                }
            }
        }

        private void suspendEventThread(JDWPCallFrame currentFrame, Object thread) {

            JDWPLogger.log("Suspending event thread: " + getThreadName(thread) + " with new suspension count: " + ThreadSuspension.getSuspensionCount(thread), JDWPLogger.LogLevel.THREAD);

            // if during stepping, send a step completed event back to the debugger
            Integer id = commandRequestIds.get(thread);
            if (id != null) {
                VMEventListeners.getDefault().stepCompleted(id, currentFrame);
            }
            // reset
            commandRequestIds.put(thread, null);


            JDWPLogger.log("lock.wait() for thread: " + getThreadName(thread), JDWPLogger.LogLevel.THREAD);

            // no reason to hold a hard suspension status, since now
            // we have the actual suspension status and suspended information
            ThreadSuspension.removeHardSuspendedThread(thread);

            lockThread(thread);

            JDWPLogger.log("lock wakeup for thread: " + getThreadName(thread), JDWPLogger.LogLevel.THREAD);
        }
    }

    private void lockThread(Object thread) {
        Object lock = getSuspendLock(thread);

        synchronized (lock) {
            try {
                // in case a thread job is already posted on this thread
                checkThreadJobsAndRun(thread);
                lock.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException("not able to suspend thread: " + getThreadName(thread), e);
            }
        }

        checkThreadJobsAndRun(thread);

        JDWPLogger.log("lock wakeup for thread: " + getThreadName(thread), JDWPLogger.LogLevel.THREAD);
    }

    private void checkThreadJobsAndRun(Object thread) {
        if (threadJobs.containsKey(thread)) {
            // a thread job was posted on this thread
            // only wake up to perform the job a go back to sleep
            ThreadJob job = threadJobs.remove(thread);
            job.runJob();
            lockThread(thread);
        }
    }

    public void postJobForThread(ThreadJob job) {
        threadJobs.put(job.getThread(), job);
        Object lock = getSuspendLock(job.getThread());
        synchronized (lock) {
            lock.notifyAll();
        }
    }
}
