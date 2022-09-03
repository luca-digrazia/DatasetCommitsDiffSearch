/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.DebuggerSession.SteppingLocation;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Access for {@link Debugger} clients to the state of a guest language execution thread that has
 * been suspended, for example by a {@link Breakpoint} or stepping action.
 * <p>
 * <h4>Event lifetime</h4>
 * <ul>
 * <li>A {@link DebuggerSession} {@link Instrumenter instruments} guest language code in order to
 * implement {@linkplain Breakpoint breakpoints}, stepping actions, or other debugging actions on
 * behalf of the session's {@linkplain Debugger debugger} client.</li>
 *
 * <li>A session may choose to suspend a guest language execution thread when it receives
 * (synchronous) notification on the execution thread that it has reached an AST location
 * instrumented by the session.</li>
 *
 * <li>The session passes a new {@link SuspendedEvent} to the debugger client (synchronously) in a
 * {@linkplain SuspendedCallback#onSuspend(SuspendedEvent) callback} on the guest language execution
 * thread.</li>
 *
 * <li>Clients may access certain event state only on the execution thread where the event was
 * created and notification received; access from other threads can throws
 * {@link IllegalStateException}. Please see the javadoc of the individual method for details.</li>
 *
 * <li>A suspended thread resumes guest language execution after the client callback returns and the
 * thread unwinds back to instrumentation code in the AST.</li>
 *
 * <li>All event methods throw {@link IllegalStateException} after the suspended thread resumes
 * guest language execution.</li>
 * </ul>
 * </p>
 * <h4>Access to execution state</h4>
 * <p>
 * <ul>
 * <li>Method {@link #getStackFrames()} describes the suspended thread's location in guest language
 * code. This information becomes unusable beyond the lifetime of the event and must not be stored.
 * </li>
 *
 * <li>Method {@link #getReturnValue()} describes a local result when the thread is suspended just
 * {@link #isHaltedBefore() after} a frame.</li>
 * </ul>
 * </p>
 * <h4>Next debugging action</h4>
 * <p>
 * Clients use the following methods to request the debugging action that will take effect when the
 * event's thread resumes guest language execution.
 * <ul>
 * <li>{@link #prepareStepInto(int)}</li>
 * <li>{@link #prepareStepOut()}</li>
 * <li>{@link #prepareStepOver(int)}</li>
 * <li>{@link #prepareKill()}</li>
 * <li>{@link #prepareContinue()}</li>
 * </ul>
 * If no debugging action is requested then {@link #prepareContinue() continue} is assumed. A
 * request for more than one action throws an {@link IllegalStateException}.
 * </p>
 *
 * @since 0.9
 */

public final class SuspendedEvent {

    private final SourceSection sourceSection;
    private final SteppingLocation location;

    private final Thread thread;

    private DebuggerSession session;
    private EventContext context;
    private MaterializedFrame materializedFrame;
    private List<Breakpoint> breakpoints;
    private Object returnValue;

    private volatile boolean disposed;
    private SteppingStrategy nextStrategy;

    private Map<Breakpoint, Throwable> conditionFailures;
    private DebugStackFrameIterable cachedFrames;

    SuspendedEvent(DebuggerSession session, Thread thread, EventContext context, MaterializedFrame frame, SteppingLocation location, Object returnValue,
                    List<Breakpoint> breakpoints, Map<Breakpoint, Throwable> conditionFailures) {
        this.session = session;
        this.context = context;
        this.location = location;
        this.materializedFrame = frame;
        this.returnValue = returnValue;
        this.conditionFailures = conditionFailures;
        this.breakpoints = breakpoints == null ? Collections.<Breakpoint> emptyList() : Collections.<Breakpoint> unmodifiableList(breakpoints);
        this.thread = thread;
        this.sourceSection = context.getInstrumentedSourceSection();
    }

    boolean isDisposed() {
        return disposed;
    }

    void clearLeakingReferences() {
        this.disposed = true;

        // cleanup data for potential memory leaks
        this.returnValue = null;
        this.breakpoints = null;
        this.materializedFrame = null;
        this.cachedFrames = null;
        this.session = null;
        this.context = null;
    }

    void verifyValidState(boolean allowDifferentThread) {
        if (disposed) {
            throw new IllegalStateException("Not in a suspended state.");
        }
        if (!allowDifferentThread && Thread.currentThread() != thread) {
            throw new IllegalStateException("Illegal thread access.");
        }
    }

    SteppingStrategy getNextStrategy() {
        SteppingStrategy strategy = nextStrategy;
        if (strategy == null) {
            return SteppingStrategy.createContinue();
        }
        return strategy;
    }

    private void setNextStrategy(SteppingStrategy nextStrategy) {
        verifyValidState(true);
        this.nextStrategy = nextStrategy;
    }

    /**
     * Returns the debugger session this suspended event was created for.
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @since 0.17
     */
    public DebuggerSession getSession() {
        verifyValidState(true);
        return session;
    }

    Thread getThread() {
        return thread;
    }

    EventContext getContext() {
        return context;
    }

    SteppingLocation getLocation() {
        return location;
    }

    /**
     * Debugger associated with the just suspended execution. This debugger remains valid after the
     * event is processed, it is possible and suggested to keep a reference to it and use it any
     * time later when evaluating sources in the {@link com.oracle.truffle.api.vm.PolyglotEngine}.
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @return instance of debugger associated with the just suspended execution and any subsequent
     *         ones in the same {@link com.oracle.truffle.api.vm.PolyglotEngine}.
     * @since 0.9
     * @deprecated use {@link #getSession()}.{@link DebuggerSession#getDebugger() getDebugger()}
     *             instead.
     */
    @Deprecated
    public Debugger getDebugger() {
        verifyValidState(true);
        return session.getDebugger();
    }

    /**
     * The Guest Language AST node where execution is suspended. Depending on the value of
     * {@link #isHaltedBefore()}, the node is either:
     * <ul>
     * <li>{@code true}: the next (non-instrumentation) node to be executed, or</li>
     * <li>{@code false}: the most recent (non-instrumentation) node that has been executed.</li>
     * </ul>
     *
     * @see ExecutionEventListener
     * @since 0.9
     * @deprecated use {@link #getSourceSection()} instead. Direct access to {@link Node} is not
     *             possible anymore.
     */
    @Deprecated
    public Node getNode() {
        return context.getInstrumentedNode();
    }

    /**
     * Returns the guest language source section of the AST node before/after the execution is
     * suspended. Returns <code>null</code> if no source section information is available.
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @since 0.17
     */
    public SourceSection getSourceSection() {
        verifyValidState(true);
        return sourceSection;
    }

    /**
     * Returns <code>true</code> if the execution is suspended before executing a guest language
     * source location. Returns <code>false</code> if it was suspended after.
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @since 0.14
     */
    public boolean isHaltedBefore() {
        verifyValidState(true);
        return location == SteppingLocation.BEFORE_STATEMENT;
    }

    /**
     * Returns the return value of the currently executed source location. Returns <code>null</code>
     * if the execution is suspended {@link #isHaltedBefore() before} a guest language location. The
     * returned value is <code>null</code> if an exception occurred during execution of the
     * instrumented statement. The debug value remains valid event if the current execution was
     * suspend.
     * <p>
     * This method is not allowed to be invoked from other threads than the execution thread.
     *
     * @since 0.17
     */
    public DebugValue getReturnValue() {
        return getTopStackFrame().wrapHeapValue(returnValue);
    }

    // TODO CHumer: we also want to provide access to guest language errors. The API for that is not
    // yet ready.

    /**
     * @since 0.9
     * @deprecated use {@link #getTopStackFrame()} to access local variables instead
     */
    @Deprecated
    public MaterializedFrame getFrame() {
        return materializedFrame;
    }

    MaterializedFrame getMaterializedFrame() {
        return materializedFrame;
    }

    /*
     * CHumer deprecation note: Its better to provide more structured information about errors if
     * possible. I realized that the only errors that would make sense in the warnings log would be
     * errors about breakpoint conditions. So I decided to add explicitly accessible breakpoint
     * condition failures.
     */
    /**
     * @since 0.9
     * @deprecated use {@link #getBreakpointConditionException(Breakpoint)} to receive the last
     *             breakpoint condition exception.
     */
    @Deprecated
    public List<String> getRecentWarnings() {
        verifyValidState(false);
        List<String> list = new ArrayList<>();
        for (Breakpoint breakpoint : getBreakpoints()) {
            Throwable failure = getBreakpointConditionException(breakpoint);
            if (failure != null) {
                list.add(String.format("Exception in %s:  %s", breakpoint.toString(), failure.toString()));
            }
        }
        return Collections.emptyList();
    }

    /**
     * Returns the cause of failure, if any, during evaluation of a breakpoint's
     * {@linkplain Breakpoint#setCondition(String) condition}.
     *
     *
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @param breakpoint a breakpoint associated with this event
     * @return the cause of condition failure
     *
     * @since 0.17
     */
    public Throwable getBreakpointConditionException(Breakpoint breakpoint) {
        verifyValidState(true);
        if (conditionFailures == null) {
            return null;
        }
        return conditionFailures.get(breakpoint);
    }

    /**
     * Returns the {@link Breakpoint breakpoints} that individually would cause the "hit" where
     * execution is suspended.
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @return an unmodifiable list of breakpoints
     *
     * @since 0.17
     */
    public List<Breakpoint> getBreakpoints() {
        verifyValidState(true);
        return breakpoints;
    }

    /**
     * @return list of stack frames
     * @since 0.9
     * @deprecated use {@link #getStackFrames()} instead
     */
    @Deprecated
    @CompilerDirectives.TruffleBoundary
    public List<FrameInstance> getStack() {
        verifyValidState(false);
        final List<FrameInstance> frameInstances = new ArrayList<>();
        // Map the Truffle stack for this execution, ignore nested executions
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
            @Override
            public FrameInstance visitFrame(FrameInstance frameInstance) {
                if (isEvalRootStackFrame(SuspendedEvent.this, frameInstance)) {
                    return frameInstance;
                }
                frameInstances.add(frameInstance);
                return null;
            }
        });
        return frameInstances;
    }

    /**
     * Returns the topmost stack frame returned by {@link #getStackFrames()}.
     * <p>
     * This method is not allowed to be invoked from other threads than the execution thread.
     *
     * @see #getStackFrames()
     * @since 0.17
     */
    public DebugStackFrame getTopStackFrame() {
        // there must be always a top stack frame.
        return getStackFrames().iterator().next();
    }

    /**
     * Returns a list of guest language stack frame objects that indicate the current guest language
     * location. There is always at least one, the topmost, stack frame available. The returned
     * stack frames are usable only during {@link SuspendedCallback#onSuspend(SuspendedEvent)
     * suspend} and should not be stored permanently.
     *
     * <p>
     * This method is not allowed to be invoked from other threads than the execution thread.
     *
     * @since 0.17
     */
    public Iterable<DebugStackFrame> getStackFrames() {
        verifyValidState(false);
        if (cachedFrames == null) {
            cachedFrames = new DebugStackFrameIterable();
        }
        return cachedFrames;
    }

    private static boolean isEvalRootStackFrame(SuspendedEvent event, FrameInstance instance) {
        CallTarget target = instance.getCallTarget();
        RootNode root = null;
        if (target instanceof RootCallTarget) {
            root = ((RootCallTarget) target).getRootNode();
        }
        if (root != null && event.getSession().getDebugger().getEnv().isEngineRoot(root)) {
            return true;
        }
        return false;
    }

    /**
     * Prepare to execute in Continue mode when guest language program execution resumes. In this
     * mode execution will continue until either:
     * <ul>
     * <li>execution arrives at a node to which an enabled breakpoint is attached,
     * <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ul>
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @since 0.9
     */
    public void prepareContinue() {
        setNextStrategy(SteppingStrategy.createContinue());
    }

    /**
     * Prepare to execute in <strong>StepInto</strong> mode when guest language program execution
     * resumes. In this mode:
     * <ul>
     * <li>Execution, when resumed, continues until either:
     * <ol>
     * <li>execution arrives at at the <em>nth</em> node (specified by {@code stepCount}) with the
     * tag {@link StatementTag}, <strong>or</strong></li>
     * <li>execution arrives at a {@link Breakpoint}, <strong>or</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </li>
     * <li>The mode persists only until either:
     * <ol>
     * <li>program execution resumes and then halts, at which time the mode reverts to
     * {@linkplain #prepareContinue() Continue}, <strong>or</strong></li>
     * <li>execution completes, at which time the mode reverts to {@linkplain #prepareContinue()
     * Continue}, <strong>or</strong></li>
     * <li>another mode is chosen.</li>
     * </ol>
     * </li>
     * <li>A breakpoint set at a location where execution would halt is treated specially to avoid a
     * "double halt" during stepping:
     * <ul>
     * <li>execution halts only <em>once</em> at the location;</li>
     * <li>the halt counts as a breakpoint {@link Breakpoint#getHitCount() hit};</li>
     * <li>the mode reverts to {@linkplain #prepareContinue() Continue}, as if there were no
     * breakpoint; and</li>
     * <li>this special treatment applies only for breakpoints created <strong>before</strong> the
     * mode is set.</li>
     * </ul>
     * </ul>
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @param stepCount the number of times to perform StepInto before halting
     * @throws IllegalArgumentException if {@code stepCount <= 0}
     * @since 0.9
     */
    public void prepareStepInto(int stepCount) {
        if (stepCount <= 0) {
            throw new IllegalArgumentException("stepCount must be > 0");
        }
        setNextStrategy(SteppingStrategy.createStepInto(stepCount));
    }

    /**
     * Prepare to execute in <strong>StepOut</strong> mode when guest language program execution
     * resumes. In this mode:
     * <ul>
     * <li>Execution, when resumed, continues until either:
     * <ol>
     * <li>execution arrives at the nearest enclosing call site on the stack, <strong>or</strong>
     * </li>
     * <li>execution arrives at a {@link Breakpoint}, <strong>or</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </li>
     * <li>The mode persists only until either:
     * <ol>
     * <li>program execution resumes and then halts, at which time the mode reverts to
     * {@linkplain #prepareContinue() Continue}, <strong>or</strong></li>
     * <li>execution completes, at which time the mode reverts to {@linkplain #prepareContinue()
     * Continue}, <strong>or</strong></li>
     * <li>another mode is chosen.</li>
     * </ol>
     * </li>
     * </ul>
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @since 0.9
     */
    public void prepareStepOut() {
        setNextStrategy(SteppingStrategy.createStepOut());
    }

    /**
     * Prepare to execute in StepOver mode when guest language program execution resumes. In this
     * mode:
     * <ul>
     * <li>Execution, when resumed, continues until either:
     * <ol>
     * <li>execution arrives at at the <em>nth</em> node (specified by {@code stepCount}) with the
     * tag {@link StatementTag}, ignoring nodes nested in function/method calls, <strong>or</strong>
     * </li>
     * <li>execution arrives at a {@link Breakpoint}, <strong>or</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </li>
     * <li>The mode persists only until either:
     * <ol>
     * <li>program execution resumes and then halts, at which time the mode reverts to
     * {@linkplain #prepareContinue() Continue}, <strong>or</strong></li>
     * <li>execution completes, at which time the mode reverts to {@linkplain #prepareContinue()
     * Continue}, <strong>or</strong></li>
     * <li>another mode is chosen.</li>
     * </ol>
     * </li>
     * <li>A breakpoint set at a location where execution would halt is treated specially to avoid a
     * "double halt" during stepping:
     * <ul>
     * <li>execution halts only <em>once</em> at the location;</li>
     * <li>the halt counts as a breakpoint {@link Breakpoint#getHitCount() hit};</li>
     * <li>the mode reverts to {@linkplain #prepareContinue() Continue}, as if there were no
     * breakpoint; and</li>
     * <li>this special treatment applies only for breakpoints created <strong>before</strong> the
     * mode is set.</li>
     * </ul>
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @param stepCount the number of times to perform StepOver before halting
     * @throws IllegalArgumentException if {@code stepCount <= 0}
     * @since 0.9
     */
    public void prepareStepOver(int stepCount) {
        if (stepCount <= 0) {
            throw new IllegalArgumentException("stepCount must be > 0");
        }
        setNextStrategy(SteppingStrategy.createStepOver(stepCount));
    }

    /**
     * Prepare to terminate the suspended execution represented by this event. One use-case for this
     * method is to shield an execution of an unknown code with a timeout:
     *
     * {@link com.oracle.truffle.tck.ExecWithTimeOut#tckSnippets}
     *
     * <p>
     * This method is allowed to be invoked from other threads than the execution thread.
     *
     * @since 0.12
     */
    public void prepareKill() {
        setNextStrategy(SteppingStrategy.createKill());
    }

    /**
     * Evaluates given code snippet in the context of currently suspended execution.
     *
     * @param code the snippet to evaluate
     * @param frameInstance the frame in which to evaluate the code; {@code null} means the current
     *            frame at the halted location.
     * @return the computed value
     * @throws IllegalStateException if the frame is not part of current execution stack
     * @throws IOException in case an evaluation goes wrong
     * @throws KillException if the evaluation is killed by the debugger
     * @since 0.9
     * @deprecated use {@link DebugStackFrame#eval(String)} instead
     */
    @Deprecated
    public Object eval(String code, FrameInstance frameInstance) throws IOException {
        verifyValidState(false);
        return DebuggerSession.evalInContext(this, code, frameInstance);
    }

    /**
     * Generates a (potentially language-specific) description of an execution value in a part of
     * the current execution context, for example the value stored in a frame slot. The description
     * is intended to be useful to a guest language programmer.
     *
     * @param value an object presumed to represent a <em>value</em> managed by the language of the
     *            AST where execution is halted.
     * @param frameInstance the frame in which to evaluate the code;
     *
     * @return a user-oriented description of a possibly language-specific value
     * @throws IllegalArgumentException if the frame is not part of current execution stack
     * @since 0.15
     * @deprecated use {@link DebugValue#as(Class) DebugValue.as(String.class)} instead.
     */
    @Deprecated
    public String toString(Object value, FrameInstance frameInstance) {
        verifyValidState(false);
        RootNode rootNode = null;
        if (frameInstance.getCallTarget() instanceof RootCallTarget) {
            rootNode = ((RootCallTarget) frameInstance.getCallTarget()).getRootNode();
        }
        if (rootNode == null) {
            // Unknown language
            return value.toString();
        }
        return session.getDebugger().getEnv().toString(rootNode, value);
    }

    /**
     * @since 0.17
     */
    @Override
    public String toString() {
        return "Suspended at " + getSourceSection() + " for thread " + getThread();
    }

    private final class DebugStackFrameIterable implements Iterable<DebugStackFrame> {

        private DebugStackFrame topStackFrame;
        private List<DebugStackFrame> otherFrames;

        private DebugStackFrame getTopStackFrame() {
            if (topStackFrame == null) {
                topStackFrame = new DebugStackFrame(SuspendedEvent.this, null);
            }
            return topStackFrame;
        }

        private List<DebugStackFrame> getOtherFrames() {
            if (otherFrames == null) {
                final List<DebugStackFrame> frameInstances = new ArrayList<>();
                Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
                    private boolean first = true;

                    @Override
                    public FrameInstance visitFrame(FrameInstance frameInstance) {
                        if (isEvalRootStackFrame(SuspendedEvent.this, frameInstance)) {
                            // we stop at eval root stack frames
                            return frameInstance;
                        }
                        if (first) {
                            first = false;
                            return null;
                        }
                        frameInstances.add(new DebugStackFrame(SuspendedEvent.this, frameInstance));
                        return null;
                    }
                });
                otherFrames = frameInstances;
            }
            return otherFrames;
        }

        public Iterator<DebugStackFrame> iterator() {
            return new Iterator<DebugStackFrame>() {

                private int index;
                private Iterator<DebugStackFrame> otherIterator;

                public boolean hasNext() {
                    verifyValidState(false);
                    if (index == 0) {
                        return true;
                    } else {
                        return getOtherStackFrames().hasNext();
                    }
                }

                public DebugStackFrame next() {
                    verifyValidState(false);
                    if (index == 0) {
                        index++;
                        return getTopStackFrame();
                    } else {
                        return getOtherStackFrames().next();
                    }
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                private Iterator<DebugStackFrame> getOtherStackFrames() {
                    if (otherIterator == null) {
                        otherIterator = getOtherFrames().iterator();
                    }
                    return otherIterator;
                }

            };
        }

    }

}
