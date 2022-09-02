/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

final class SafepointStackSampler {

    private static final Set<Class<?>> VISITOR_TAGS = new HashSet<>(Arrays.asList(StandardTags.RootTag.class));
    private final int stackLimit;
    private final SourceSectionFilter sourceSectionFilter;
    private final ConcurrentLinkedQueue<StackVisitor> stackVisitorCache = new ConcurrentLinkedQueue<>();
    private final AtomicReference<SampleAction> cachedAction = new AtomicReference<>();
    private final Map<Thread, SyntheticFrame> syntheticFrames = new ConcurrentHashMap<>();
    private boolean overflowed;

    SafepointStackSampler(int stackLimit, SourceSectionFilter sourceSectionFilter) {
        this.stackLimit = stackLimit;
        this.sourceSectionFilter = sourceSectionFilter;
    }

    private StackVisitor fetchStackVisitor() {
        StackVisitor visitor = stackVisitorCache.poll();
        if (visitor == null) {
            visitor = new StackVisitor(stackLimit);
        }
        return visitor;
    }

    List<StackSample> sample(Env env, TruffleContext context) {
        if (context.isActive()) {
            throw new IllegalArgumentException("Cannot sample a context that is currently active on the current thread.");
        }
        if (context.isClosed()) {
            return Collections.emptyList();
        }
        SampleAction action = cachedAction.getAndSet(null);
        if (action == null) {
            action = new SampleAction();
        }
        long submitTime = System.nanoTime();
        Future<Void> future;
        try {
            future = env.submitThreadLocal(context, null, action);
        } catch (IllegalStateException e) {
            // context may be closed while submitting
            return Collections.emptyList();
        }

        try {
            future.get(100L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            return null;
        } catch (TimeoutException e) {
            future.cancel(false);
        }
        // we compute the time to find out how accurate this sample is.
        List<StackSample> perThreadSamples = new ArrayList<>();
        for (StackVisitor stackVisitor : action.getStacks()) {
            // time until the safepoint is executed from schedule
            long bias = stackVisitor.startTime - submitTime;
            long overhead = stackVisitor.endTime - stackVisitor.startTime;
            perThreadSamples.add(new StackSample(stackVisitor.thread, stackVisitor.createEntries(sourceSectionFilter),
                            bias, overhead, stackVisitor.overflowed));
            stackVisitor.resetAndReturn();
        }
        action.reset();
        cachedAction.set(action);

        for (SyntheticFrame syntheticFrame : syntheticFrames.values()) {
            perThreadSamples.add(syntheticFrame.stackSample());
        }
        return perThreadSamples;
    }

    boolean hasOverflowed() {
        return overflowed;
    }

    private void stackOverflowed(boolean visitorOverflowed) {
        this.overflowed |= visitorOverflowed;
    }

    public void pushSyntheticFrame(LanguageInfo language, String message) {
        Thread thread = Thread.currentThread();
        SyntheticFrame parent = null;
        if (syntheticFrames.containsKey(thread)) {
            parent = syntheticFrames.get(thread);
        }
        syntheticFrames.put(thread, new SyntheticFrame(parent, thread, language, message));
    }

    public void popSyntheticFrame() {
        Thread thread = Thread.currentThread();
        SyntheticFrame syntheticFrame = syntheticFrames.get(thread);
        if (syntheticFrame == null) {
            return;
        }
        if (syntheticFrame.parent != null) {
            syntheticFrames.put(thread, syntheticFrame.parent);
        } else {
            syntheticFrames.remove(thread);
        }
    }

    static final class StackSample {

        final Thread thread;
        final List<StackTraceEntry> stack;
        final long biasNs;
        final long durationNs;
        final boolean overflowed;

        StackSample(Thread thread, List<StackTraceEntry> stack, long biasNs, long durationNs, boolean overflowed) {
            this.thread = thread;
            this.stack = stack;
            this.biasNs = biasNs;
            this.durationNs = durationNs;
            this.overflowed = overflowed;
        }
    }

    private class StackVisitor implements FrameInstanceVisitor<FrameInstance> {

        private final CallTarget[] targets;
        private final byte[] states;
        private Thread thread;
        private int nextFrameIndex;
        private long startTime;
        private long endTime;
        private boolean overflowed;

        StackVisitor(int stackLimit) {
            assert stackLimit > 0;
            this.states = new byte[stackLimit];
            this.targets = new CallTarget[stackLimit];
        }

        private byte state(FrameInstance frameInstance) {
            switch (frameInstance.getCompilationTier()) {
                case 0:
                    return StackTraceEntry.STATE_INTERPRETED;
                case 1:
                    if (frameInstance.isCompilationRoot()) {
                        return StackTraceEntry.STATE_FIRST_TIER_COMPILATION_ROOT;
                    } else {
                        return StackTraceEntry.STATE_FIRST_TIER_COMPILED;
                    }
                default:
                    if (frameInstance.isCompilationRoot()) {
                        return StackTraceEntry.STATE_LAST_TIER_COMPILED;
                    } else {
                        return StackTraceEntry.STATE_LAST_TIER_COMPILATION_ROOT;
                    }
            }
        }

        public FrameInstance visitFrame(FrameInstance frameInstance) {
            states[nextFrameIndex] = state(frameInstance);
            targets[nextFrameIndex] = frameInstance.getCallTarget();
            nextFrameIndex++;
            if (nextFrameIndex >= targets.length) {
                // stop traversing
                overflowed = true;
                return frameInstance;
            }
            return null;
        }

        void beforeVisit(Node topOfStackNode) {
            thread = Thread.currentThread();
            assert nextFrameIndex == 0 : "not cleaned";
            startTime = System.nanoTime();
            targets[nextFrameIndex] = topOfStackNode.getRootNode().getCallTarget();
        }

        void resetAndReturn() {
            Arrays.fill(states, 0, nextFrameIndex, (byte) 0);
            Arrays.fill(targets, 0, nextFrameIndex, null);
            nextFrameIndex = 0;
            thread = null;
            overflowed = false;
            this.startTime = 0L;
            this.endTime = 0L;
            stackVisitorCache.add(this);
        }

        List<StackTraceEntry> createEntries(SourceSectionFilter filter) {
            return createEntries(filter, null);
        }

        @SuppressWarnings("unused")
        List<StackTraceEntry> createEntries(SourceSectionFilter filter, String synthetic) {
            List<StackTraceEntry> entries = new ArrayList<>(nextFrameIndex);
            if (synthetic != null) {
                entries.add(new StackTraceEntry(synthetic));
            }
            for (int i = 0; i < nextFrameIndex; i++) {
                CallTarget target = targets[i];
                RootNode root = ((RootCallTarget) target).getRootNode();
                SourceSection sourceSection = root.getSourceSection();
                if (sourceSection != null && filter.includes(root, sourceSection)) {
                    entries.add(new StackTraceEntry(VISITOR_TAGS, sourceSection, root, root, states[i]));
                }
            }
            return entries;
        }

        void afterVisit() {
            this.endTime = System.nanoTime();
        }
    }

    private class SampleAction extends ThreadLocalAction {

        final ConcurrentHashMap<Thread, StackVisitor> completed = new ConcurrentHashMap<>();
        private volatile boolean cancelled;

        protected SampleAction() {
            super(false, false);
        }

        @Override
        protected void perform(Access access) {
            if (cancelled) {
                // too late to do anything
                return;
            }
            StackVisitor visitor = fetchStackVisitor();
            visitor.beforeVisit(access.getLocation());
            Truffle.getRuntime().iterateFrames(visitor);
            stackOverflowed(visitor.overflowed);
            if (cancelled) {
                // did not complete on time
                visitor.resetAndReturn();
            } else {
                completed.put(access.getThread(), visitor);
            }
            visitor.afterVisit();
        }

        List<StackVisitor> getStacks() {
            cancelled = true;
            return new ArrayList<>(completed.values());
        }

        void reset() {
            cancelled = false;
            completed.clear();
        }
    }

    private class SyntheticFrame {
        final SyntheticFrame parent;
        final StackVisitor visitor;
        final Thread thread;
        final LanguageInfo language;
        final String message;
        StackSample stackSample;

        /**
         * Created on the interpreter thread, keep as fast as possible
         */
        public SyntheticFrame(SyntheticFrame parent, Thread thread, LanguageInfo language, String message) {
            this.parent = parent;
            this.thread = thread;
            this.language = language;
            this.message = message;
            this.visitor = fetchStackVisitor();
            Truffle.getRuntime().iterateFrames(visitor);
        }

        /**
         * Read on the sampling thread
         */
        private StackSample stackSample() {
            if (stackSample == null) {
                String languageMessage = language.getName() + ":" + message;
                stackSample = new StackSample(thread, visitor.createEntries(sourceSectionFilter, languageMessage), 0, 0, visitor.overflowed);
                visitor.resetAndReturn();
            }
            return stackSample;
        }
    }
}
