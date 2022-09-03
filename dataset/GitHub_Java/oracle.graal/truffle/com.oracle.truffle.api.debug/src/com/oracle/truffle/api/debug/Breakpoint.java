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
import java.net.URI;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.DebuggerSession.SteppingLocation;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * A request that guest language program execution be suspended at specified
 * {@linkplain BreakpointLocation locations} on behalf of a debugging client
 * {@linkplain DebuggerSession session}.
 * <p>
 * <h4>Breakpoint lifetime</h4>
 * <p>
 * <ul>
 * <li>A client's {@link DebuggerSession} uses a {@link Builder} to create a new breakpoint,
 * choosing among multiple ways to specify the intended location. Examples include a specified
 * {@link #newBuilder(Source) source}, a specified {@link #newBuilder(URI) URI}, line ranges, or an
 * exact {@link #newBuilder(SourceSection) SourceSection}, with more options likely to be added in
 * the future.</li>
 *
 * <li>A breakpoint can have no effect until it is {@linkplain DebuggerSession installed} by a
 * session, which may be done only once.</li>
 *
 * <li>A breakpoint that is both installed and {@linkplain Breakpoint#isEnabled() enabled} (true by
 * default) will suspend any guest language execution thread that arrives at a matching AST
 * location. The breakpoint (synchronously) {@linkplain SuspendedCallback calls back} to the
 * responsible session on the execution thread.</li>
 *
 * <li>A breakpoint may be enabled or disabled any number of times.</li>
 *
 * <li>A breakpoint that is no longer needed may be {@linkplain #dispose() disposed}. A disposed
 * breakpoint:
 * <ul>
 * <li>is disabled</li>
 * <li>is not installed in any session</li>
 * <li>can have no effect on program execution, and</li>
 * <li>may not be used again.</li>
 * </ul>
 * </li>
 *
 * <li>A session being {@linkplain DebuggerSession#close() closed} disposes all installed
 * breakpoints.</li>
 * </ul>
 * </p>
 * <p>
 * Example usage: {@link com.oracle.truffle.api.debug.BreakpointSnippets#example()}
 *
 * @since 0.9
 */
@SuppressWarnings("javadoc")
public final class Breakpoint {

    /**
     * A general model of the states occupied by a {@link Breakpoint} during its lifetime.
     *
     * @since 0.9
     * @deprecated use {@link Breakpoint#isEnabled()}, {@link Breakpoint#isDisposed()} or
     *             {@link Breakpoint#isResolved()} instead.
     */
    @Deprecated
    public enum State {

        /**
         * No matching source locations have been identified, but it is enables so that the
         * breakpoint will become active when any matching source locations appear.
         */
        ENABLED_UNRESOLVED("Enabled/Unresolved"),

        /**
         * No matching source locations have been identified, and it is disabled. The breakpoint
         * will become associated with any matching source locations that appear, but will not
         * become active until explicitly enabled.
         */
        DISABLED_UNRESOLVED("Disabled/Unresolved"),

        /**
         * Matching source locations have been identified and the breakpoint is active at them.
         */
        ENABLED("Enabled"),

        /**
         * Matching source locations have been identified, but he breakpoint is disabled. It will
         * not be active until explicitly enabled.
         */
        DISABLED("Disabled"),

        /**
         * The breakpoint is permanently inactive.
         */
        DISPOSED("Disposed");

        private final String name;

        State(String name) {
            this.name = name;
        }

        /** @since 0.9 */
        public String getName() {
            return name;
        }

        /** @since 0.9 */
        @Override
        public String toString() {
            return name;
        }
    }

    static final Comparator<Breakpoint> COMPARATOR = new Comparator<Breakpoint>() {

        public int compare(Breakpoint o1, Breakpoint o2) {
            return o1.locationKey.compareTo(o2.locationKey);
        }

    };

    private static final Breakpoint BUILDER_INSTANCE = new Breakpoint();

    private final SourceSectionFilter filter;
    private final BreakpointLocation locationKey;
    private final boolean oneShot;

    private volatile DebuggerSession session;

    private volatile boolean enabled;
    private volatile boolean resolved;
    private volatile int ignoreCount;
    private volatile boolean disposed;
    private volatile String condition;

    /* We use long instead of int in the implementation to avoid not hitting again on overflows. */
    private final AtomicLong hitCount = new AtomicLong();
    private volatile SourceSection resolvedSourceSection;
    private volatile Assumption conditionUnchanged;

    private EventBinding<? extends ExecutionEventNodeFactory> breakpointBinding;
    private EventBinding<?> sourceBinding;

    Breakpoint(BreakpointLocation key, SourceSectionFilter filter, boolean oneShot) {
        this.locationKey = key;
        this.filter = filter;
        this.oneShot = oneShot;
        this.enabled = true;
    }

    private Breakpoint() {
        this.locationKey = null;
        this.filter = null;
        this.oneShot = false;
    }

    /**
     * Returns <code>true</code> if this breakpoint can no longer affect execution.
     *
     * @since 0.17
     */
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Returns whether this breakpoint is currently allowed to suspend execution.
     * <p>
     * New breakpoints are enabled by default. Disabled breakpoints remain installed and may be
     * enabled/disabled arbitrarily.
     *
     * @since 0.9
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Controls whether this breakpoint is allowed to suspend execution; enabled by default.
     *
     * @param enabled <code>true</code> to activate the breakpoint, <code>false</code> to deactivate
     *            it so that it will not suspend the execution.
     * @since 0.9
     */
    public synchronized void setEnabled(boolean enabled) {
        if (disposed) {
            // cannot enable disposed breakpoints
            return;
        }
        if (this.enabled != enabled) {
            if (session != null) {
                if (enabled) {
                    install();
                } else {
                    uninstall();
                }
            }
            this.enabled = enabled;
        }
    }

    /**
     * Returns <code>true</code> if the breakpoint location has been resolved. A breakpoint location
     * will be resolved if at least one source that contains the breakpoint location is loaded.
     *
     * @since 0.17
     */
    public boolean isResolved() {
        return resolved;
    }

    /**
     * Sets a boolean condition expression for this breakpoint. Breakpoints are by default
     * unconditional. If a condition is set then the breakpoint is hit if the condition returned
     * <code>true</code>. If a breakpoint should be unconditional then set the expression to
     * <code>null</code>. Please note that if a breakpoint condition fails to execute for any reason
     * then the condition is considered to return always <code>true</code>. Errors in parsing or
     * executing the condition expression can be retrieved using
     * {@link SuspendedEvent#getBreakpointConditionException(Breakpoint)}.
     *
     * @param expression if non{@code -null}, a boolean expression, expressed in the guest language,
     *            to be evaluated in the lexical context at the breakpoint location.
     * @throws IOException never actually thrown
     * @see SuspendedEvent#getBreakpointConditionException(Breakpoint)
     * @since 0.9
     */
    public synchronized void setCondition(String expression) throws IOException {
        this.condition = expression;
        Assumption assumption = conditionUnchanged;
        if (assumption != null) {
            this.conditionUnchanged = null;
            assumption.invalidate();
        }
    }

    /*
     * CHumer Deprecation Note: Deprecated because of wrong return type.
     */
    /**
     * Returns the resolved object that is executed for debugger conditions. If the breakpoint is
     * {@link #isResolved() unresolved} and the debugger was created based on an {@link URI} then
     * the condition might not yet have been resolved.
     *
     * @since 0.9
     * @deprecated in 0.16, unreliable implementation. no replacement
     */
    @Deprecated
    public synchronized Source getCondition() {
        if (condition != null) {
            Source source = null;
            if (isResolved()) {
                source = resolvedSourceSection.getSource();
            } else if (locationKey.getKey() instanceof Source) {
                source = (Source) locationKey.getKey();
            }
            if (source != null) {
                return Source.newBuilder(condition).mimeType(source.getMimeType()).name("Condition for breakpoint " + toString()).build();
            }
        }
        return null;
    }

    /**
     * Prevents this breakpoint from having any further effect on execution.
     *
     * @since 0.9
     */
    public synchronized void dispose() {
        if (!disposed) {
            setEnabled(false);
            if (sourceBinding != null) {
                sourceBinding.dispose();
                sourceBinding = null;
            }
            if (session != null) {
                session.disposeBreakpoint(this);
            }
            disposed = true;
        }
    }

    /*
     * Deprecation Note: State was redundant to setEnabled and isEnabled. So I it is better to go
     * with isEnabled(), isDisposed(), isResolved()
     */
    /**
     * Gets current state of the breakpoint.
     *
     * @since 0.9
     * @deprecated use {@link Breakpoint#isEnabled()}, {@link Breakpoint#isDisposed()} or
     *             {@link Breakpoint#isResolved()} instead.
     */
    @Deprecated
    public State getState() {
        if (isDisposed()) {
            return State.DISPOSED;
        }
        if (isEnabled()) {
            if (isResolved()) {
                return State.ENABLED;
            } else {
                return State.ENABLED_UNRESOLVED;
            }
        } else {
            if (isResolved()) {
                return State.DISABLED;
            } else {
                return State.DISABLED_UNRESOLVED;
            }
        }
    }

    /**
     * Does this breakpoint disable itself after first activation?
     *
     * @since 0.9
     */
    public boolean isOneShot() {
        return oneShot;
    }

    /**
     * Gets the number of hits left to be ignored before this breakpoint will suspend execution.
     *
     * @since 0.9
     */
    public int getIgnoreCount() {
        return ignoreCount;
    }

    /**
     * Change the threshold for when this breakpoint should start causing a break.
     * <p>
     * When both an ignore count and a {@linkplain #setCondition(String) condition} are specified,
     * the condition is evaluated first: if {@code false} it is not considered to be a hit. In other
     * words, the ignore count is for successful conditions only.
     *
     * @since 0.9
     */
    public void setIgnoreCount(int ignoreCount) {
        this.ignoreCount = ignoreCount;
    }

    /**
     * Gets the number of times this breakpoint has suspended execution.
     * <p>
     * If the breakpoint has a condition that evaluates to {@code false}, it does not count as a
     * hit.
     *
     * @since 0.9
     */
    public int getHitCount() {
        return (int) hitCount.get();
    }

    /**
     * Gets a human-sensible description of this breakpoint's location.
     */
    String getShortDescription() {
        return "Breakpoint@" + locationKey.toString();
    }

    /**
     * Returns a description of the current location this breakpoint is installed at.
     *
     * @since 0.9
     */
    public String getLocationDescription() {
        return locationKey.toString();
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.9
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    }

    DebuggerNode lookupNode(EventContext context) {
        if (!isEnabled()) {
            return null;
        } else {
            EventBinding<? extends ExecutionEventNodeFactory> binding = breakpointBinding;
            if (binding != null) {
                return (DebuggerNode) context.lookupExecutionEventNode(binding);
            }
            return null;
        }
    }

    synchronized Assumption getConditionUnchanged() {
        if (conditionUnchanged == null) {
            conditionUnchanged = Truffle.getRuntime().createAssumption("Breakpoint condition unchanged.");
        }
        return conditionUnchanged;
    }

    BreakpointLocation getLocationKey() {
        return locationKey;
    }

    DebuggerSession getSession() {
        return session;
    }

    synchronized void install(DebuggerSession d) {
        if (this.session != null) {
            throw new IllegalStateException("Breakpoint is already installed.");
        }
        this.session = d;
        if (enabled) {
            install();
        }
    }

    private void install() {
        Thread.holdsLock(this);
        sourceBinding = session.getDebugger().getInstrumenter().attachLoadSourceSectionListener(filter, new LoadSourceSectionListener() {
            public void onLoad(LoadSourceSectionEvent event) {
                resolveBreakpoint(event.getSourceSection());
            }
        }, true);
        breakpointBinding = session.getDebugger().getInstrumenter().attachFactory(filter, new BreakpointNodeFactory());
    }

    private synchronized void resolveBreakpoint(SourceSection sourceSection) {
        if (disposed) {
            // cannot resolve disposed breakpoint
            return;
        }
        if (!isResolved()) {
            resolvedSourceSection = sourceSection;
            if (sourceBinding != null) {
                sourceBinding.dispose();
                sourceBinding = null;
            }
            resolved = true;
        }
    }

    private void uninstall() {
        Thread.holdsLock(this);
        if (breakpointBinding != null) {
            breakpointBinding.dispose();
            breakpointBinding = null;
        }
    }

    /**
     * Returns <code>true</code> if it should appear in the breakpoints list.
     *
     * @throws BreakpointConditionFailure
     */
    boolean notifyIndirectHit(DebuggerNode source, DebuggerNode node, Frame frame) throws BreakpointConditionFailure {
        if (!isEnabled()) {
            return false;
        }
        assert node.getBreakpoint() == this;

        if (source != node) {
            if (!((BreakpointNode) node).shouldBreak(frame)) {
                return false;
            }
        } else {
            // don't do the assert here, the breakpoint condition might have side effects.
            // assert ((BreakpointNode) node).shouldBreak(frame);
        }

        if (this.hitCount.incrementAndGet() <= ignoreCount) {
            // breakpoint hit was ignored
            return false;
        }

        if (isOneShot()) {
            setEnabled(false);
        }
        return true;
    }

    @TruffleBoundary
    private void doBreak(DebuggerNode source, MaterializedFrame frame, BreakpointConditionFailure failure) {
        if (!isEnabled()) {
            // make sure we do not cause break events if we got disabled already
            // the instrumentation framework will make sure that this is not happening if the
            // binding was disposed.
            return;
        }
        session.notifyCallback(source, frame, null, failure);
    }

    /**
     * Creates a new breakpoint builder based on a URI location.
     *
     * @param sourceUri the uri to install the breakpoint at
     * @since 0.17
     */
    public static Builder newBuilder(URI sourceUri) {
        return BUILDER_INSTANCE.new Builder(sourceUri);
    }

    /**
     * Creates a new breakpoint builder based on a source.
     *
     * @param source the source to install the breakpoint at
     * @since 0.17
     */
    public static Builder newBuilder(Source source) {
        return BUILDER_INSTANCE.new Builder(source);
    }

    /**
     * Creates a new breakpoint builder based on a source section.
     *
     * @param sourceSection the source section to install the breakpoint at
     * @since 0.17
     */
    public static Builder newBuilder(SourceSection sourceSection) {
        return BUILDER_INSTANCE.new Builder(sourceSection);
    }

    /**
     * Builder implementation for a new {@link Breakpoint breakpoint}.
     *
     * @see Breakpoint#newBuilder(Source)
     * @see Breakpoint#newBuilder(URI)
     * @see Breakpoint#newBuilder(SourceSection)
     * @since 0.17
     */
    public final class Builder {

        private final Object key;

        private int line = -1;
        private int ignoreCount;
        private boolean oneShot;
        private SourceSection sourceSection;

        private Builder(Object key) {
            Objects.requireNonNull(key);
            this.key = key;
        }

        private Builder(SourceSection key) {
            this(key.getSource());
            Objects.requireNonNull(key);
            sourceSection = key;
        }

        /**
         * Configures the line this breakpoint should be installed at. Cannot be used together with
         * {@link Breakpoint#newBuilder(SourceSection)}. The given line number must be > 0 otherwise
         * an {@link IllegalArgumentException} is thrown. Can only be invoked once per builder.
         *
         * @param line the line where the breakpoint should be installed
         * @since 0.17
         */
        public Builder lineIs(@SuppressWarnings("hiding") int line) {
            if (line <= 0) {
                throw new IllegalArgumentException("Line argument must be > 0.");
            }
            if (this.line != -1) {
                throw new IllegalStateException("LineIs can only be called once per breakpoint builder.");
            }
            if (sourceSection != null) {
                throw new IllegalArgumentException("LineIs cannot be used with source section based breakpoint. ");
            }
            this.line = line;
            return this;
        }

        /**
         * Configures the number of times a breakpoint is ignored until it is hit. The ignore count
         * can also be reconfigured after the breakpoint was created with
         * {@link Breakpoint#setIgnoreCount(int)}.
         *
         * @see Breakpoint#setIgnoreCount(int)
         * @since 0.17
         */
        public Builder ignoreCount(@SuppressWarnings("hiding") int ignoreCount) {
            if (ignoreCount < 0) {
                throw new IllegalArgumentException("IgnoreCount argument must be >= 0.");
            }
            this.ignoreCount = ignoreCount;
            return this;
        }

        /**
         * Installing a one shot will disable the breakpoint after hitting it once. If the
         * breakpoint is enabled again {@link Breakpoint#setEnabled(boolean) enabled} after hitting
         * it once it is again hit once and disabled again.
         *
         * @since 0.17
         */
        public Builder oneShot() {
            this.oneShot = true;
            return this;
        }

        /**
         * Returns a new breakpoint instance given the current builder configuration.
         *
         * @since 0.17
         */
        public Breakpoint build() {
            SourceSectionFilter f = buildFilter();
            BreakpointLocation location = new BreakpointLocation(key, line, -1);
            Breakpoint breakpoint = new Breakpoint(location, f, oneShot);
            breakpoint.setIgnoreCount(ignoreCount);
            return breakpoint;
        }

        private SourceSectionFilter buildFilter() {
            SourceSectionFilter.Builder f = SourceSectionFilter.newBuilder();
            if (key instanceof URI) {
                final URI sourceUri = (URI) key;
                f.sourceIs(new SourcePredicate() {
                    public boolean test(Source s) {
                        URI uri = s.getURI();
                        if (uri == null) {
                            return false;
                        }
                        return uri.equals(sourceUri);
                    }

                    @Override
                    public String toString() {
                        return "URI equals " + sourceUri;
                    }
                });
            } else {
                assert key instanceof Source;
                f.sourceIs((Source) key);
            }
            if (line != -1) {
                f.lineStartsIn(IndexRange.byLength(line, 1));
            }
            if (sourceSection != null) {
                f.sourceSectionEquals(sourceSection);
            }
            f.tagIs(StatementTag.class);
            return f.build();
        }
    }

    private class BreakpointNodeFactory implements ExecutionEventNodeFactory {

        public ExecutionEventNode create(EventContext context) {
            if (!isResolved()) {
                resolveBreakpoint(context.getInstrumentedSourceSection());
            }
            return new BreakpointNode(Breakpoint.this, context);
        }

    }

    private static class BreakpointNode extends DebuggerNode {

        private final Breakpoint breakpoint;
        private final BranchProfile breakBranch = BranchProfile.create();

        @Child private ConditionalBreakNode breakCondition;

        BreakpointNode(Breakpoint breakpoint, EventContext context) {
            super(context);
            this.breakpoint = breakpoint;
            if (breakpoint.condition != null) {
                this.breakCondition = new ConditionalBreakNode(context, breakpoint);
            }
        }

        @Override
        SteppingLocation getSteppingLocation() {
            return SteppingLocation.BEFORE_STATEMENT;
        }

        @Override
        Breakpoint getBreakpoint() {
            return breakpoint;
        }

        @Override
        EventBinding<?> getBinding() {
            return breakpoint.breakpointBinding;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            BreakpointConditionFailure conditionError = null;
            try {
                if (!shouldBreak(frame)) {
                    return;
                }
            } catch (BreakpointConditionFailure e) {
                conditionError = e;
            }
            breakBranch.enter();
            breakpoint.doBreak(this, frame.materialize(), conditionError);
        }

        boolean shouldBreak(Frame frame) throws BreakpointConditionFailure {
            if (breakCondition != null) {
                try {
                    return breakCondition.shouldBreak(frame);
                } catch (Throwable e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new BreakpointConditionFailure(breakpoint, e);
                    // fallthrough to true
                }
            }
            return true;
        }

    }

    static final class BreakpointConditionFailure extends SlowPathException {

        private static final long serialVersionUID = 1L;

        private final Breakpoint breakpoint;

        BreakpointConditionFailure(Breakpoint breakpoint, Throwable cause) {
            super(cause);
            this.breakpoint = breakpoint;
        }

        public Breakpoint getBreakpoint() {
            return breakpoint;
        }

        public Throwable getConditionFailure() {
            return getCause();
        }

    }

    private static class ConditionalBreakNode extends Node {

        private final EventContext context;
        private final Breakpoint breakpoint;
        @Child private DirectCallNode conditionCallNode;
        @CompilationFinal private Assumption conditionUnchanged;

        ConditionalBreakNode(EventContext context, Breakpoint breakpoint) {
            this.context = context;
            this.breakpoint = breakpoint;
            this.conditionUnchanged = breakpoint.getConditionUnchanged();
        }

        boolean shouldBreak(Frame frame) {
            if (conditionCallNode == null || !conditionUnchanged.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initializeConditional();
            }
            Object result;
            // TODO we need to change the call signature fo DirectCallNode to support Frame instead
            // of just VirtualFrame.
            if (frame instanceof VirtualFrame) {
                result = conditionCallNode.call((VirtualFrame) frame, new Object[0]);
            } else {
                result = conditionCallNode.getCallTarget().call();
            }
            if (!(result instanceof Boolean)) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalArgumentException("Unsupported return type " + result + " in condition.");
            }
            return (Boolean) result;
        }

        private void initializeConditional() {
            Node instrumentedNode = context.getInstrumentedNode();
            final RootNode rootNode = instrumentedNode.getRootNode();
            if (rootNode == null) {
                throw new IllegalStateException("Probe was disconnected from the AST.");
            }

            Source conditionSource;
            synchronized (breakpoint) {
                conditionSource = Source.newBuilder(breakpoint.condition).mimeType(context.getInstrumentedSourceSection().getSource().getMimeType()).name(
                                "breakpoint condition").build();
                if (conditionSource == null) {
                    throw new IllegalStateException("Condition is not resolved " + rootNode);
                }
                conditionUnchanged = breakpoint.getConditionUnchanged();
            }

            final CallTarget callTarget = Debugger.ACCESSOR.parse(conditionSource, instrumentedNode, new String[0]);
            conditionCallNode = insert(Truffle.getRuntime().createDirectCallNode(callTarget));
        }
    }

}

class BreakpointSnippets {

    public void example() {
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        SuspendedCallback suspendedCallback = new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
            }
        };
        Source someCode = Source.newBuilder("").mimeType("").name("").build();

        // @formatter:off
        // BEGIN: BreakpointSnippets.example
        try (DebuggerSession session = Debugger.find(engine).
                        startSession(suspendedCallback)) {

            // install breakpoint in someCode at line 3.
            session.install(Breakpoint.newBuilder(someCode).
                            lineIs(3).build());

            // install breakpoint for a URI at line 3
            session.install(Breakpoint.newBuilder(someCode.getURI()).
                            lineIs(3).build());

        }
        // END: BreakpointSnippets.example
        // @formatter:on

    }
}
