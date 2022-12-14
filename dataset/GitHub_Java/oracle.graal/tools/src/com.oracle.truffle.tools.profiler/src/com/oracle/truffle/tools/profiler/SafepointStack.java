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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Custom more efficient stack representations for profilers.
 *
 * @since 0.30
 */
final class SafepointStack {

    private final int stackLimit;
    private final SourceSectionFilter sourceSectionFilter;

    private final ConcurrentLinkedQueue<StackVisitor> stackVisitorCache = new ConcurrentLinkedQueue<>();

    SafepointStack(int stackLimit, SourceSectionFilter sourceSectionFilter) {
        this.stackLimit = stackLimit;
        this.sourceSectionFilter = SourceSectionFilter.ANY;
    }

    private StackVisitor fetchStackVisitor() {
        StackVisitor visitor = stackVisitorCache.poll();
        if (visitor == null) {
            visitor = new StackVisitor(stackLimit);
        }
        return visitor;
    }

    private void returnStackVisitor(StackVisitor visitor) {
        visitor.reset();
        stackVisitorCache.add(visitor);
    }

    static class StackVisitor implements FrameInstanceVisitor<FrameInstance> {

        private Thread thread;

        private final CallTarget[] targets;
        private final byte[] states;
        private int nextFrameIndex;
        private long startTime;
        private long endTime;
        private boolean overflowed;

        StackVisitor(int stackLimit) {
            assert stackLimit > 0;
            this.states = new byte[stackLimit];
            this.targets = new CallTarget[stackLimit];
        }

        public FrameInstance visitFrame(FrameInstance frameInstance) {
            byte state;
            int tier = frameInstance.getCompilationTier();
            switch (tier) {
                case 0:
                    state = StackTraceEntry.STATE_INTERPRETED;
                    break;
                case 1:
                    if (frameInstance.isCompilationRoot()) {
                        state = StackTraceEntry.STATE_FIRST_TIER_COMPILATION_ROOT;
                    } else {
                        state = StackTraceEntry.STATE_FIRST_TIER_COMPILED;
                    }
                    break;
                default:
                    if (frameInstance.isCompilationRoot()) {
                        state = StackTraceEntry.STATE_LAST_TIER_COMPILED;
                    } else {
                        state = StackTraceEntry.STATE_LAST_TIER_COMPILATION_ROOT;
                    }
                    break;
            }
            states[nextFrameIndex] = state;
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

        void reset() {
            Arrays.fill(states, 0, nextFrameIndex, (byte) 0);
            Arrays.fill(targets, 0, nextFrameIndex, null);
            nextFrameIndex = 0;
            thread = null;
            overflowed = false;
            this.startTime = 0L;
            this.endTime = 0L;
        }

        private static final Set<Class<?>> TAGS = new HashSet<>(Arrays.asList(StandardTags.RootTag.class));

        List<StackTraceEntry> createEntries(SourceSectionFilter filter) {
            List<StackTraceEntry> entries = new ArrayList<>(nextFrameIndex);
            for (int i = 0; i < nextFrameIndex; i++) {
                CallTarget target = targets[i];
                RootNode root = ((RootCallTarget) target).getRootNode();
                SourceSection sourceSection = root.getSourceSection();
                if (sourceSection != null && filter.includes(root, sourceSection)) {
                    entries.add(new StackTraceEntry(TAGS, sourceSection, root, root, states[i]));
                }
            }
            return entries;
        }

        void afterVisit() {
            this.endTime = System.nanoTime();
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
            if (cancelled) {
                // did not complete on time
                returnStackVisitor(visitor);
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

    private final AtomicReference<SampleAction> cachedAction = new AtomicReference<>();

    List<StackSample> sample(Env env, TruffleContext context) {
        if (context.isActive()) {
            throw new IllegalArgumentException("Cannot sample a context that is currently active on the current thread.");
        }
        SampleAction action = cachedAction.getAndSet(null);
        if (action == null) {
            action = new SampleAction();
        }

        if (context.isClosed()) {
            return Collections.emptyList();
        }
        long time = System.nanoTime();
        Future<Void> future;
        try {
            future = env.submitThreadLocal(context, null, action);
        } catch (IllegalStateException e) {
            // context may be closed while submitting
            return Collections.emptyList();
        }

        List<StackVisitor> stacks;
        try {
            future.get(100L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            return null;
        } catch (TimeoutException e) {
            future.cancel(false);
        }
        // we compute the time to find out how accurate this sample is.
        stacks = action.getStacks();
        List<StackSample> threads = new ArrayList<>();
        for (StackVisitor stackVisitor : stacks) {
            // time until the safepoint is executed from schedule
            long bias = stackVisitor.startTime - time;
            long overhead = stackVisitor.endTime - stackVisitor.startTime;

            threads.add(new StackSample(stackVisitor.thread, stackVisitor.createEntries(sourceSectionFilter),
                            bias, overhead, stackVisitor.overflowed));
            returnStackVisitor(stackVisitor);
        }
        action.reset();
        cachedAction.set(action);

        return threads;
    }
}
