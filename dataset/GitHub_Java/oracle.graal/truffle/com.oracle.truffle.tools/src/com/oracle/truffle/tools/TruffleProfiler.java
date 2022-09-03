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
package com.oracle.truffle.tools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.TruffleProfiler.Counter.TimeKind;

@Registration(id = TruffleProfiler.ID)
public class TruffleProfiler extends TruffleInstrument {

    public static final String ID = "truffle_profiler";

    private static final int MAX_CODE_LENGTH = 30;

    private Instrumenter instrumenter;

    private boolean isCollecting;

    private boolean isTiming;

    private String[] mimeTypes = parseMimeTypes(System.getProperty("truffle.profiling.includeMimeTypes"));

    @SuppressWarnings("rawtypes") private EventBinding binding;

    private Profiler profiler;  // API service to clients; null when disposed

    private final Map<SourceSection, Counter> counters = new HashMap<>();

    // TODO temporary solution until TruffleRuntime#getCallerFrame() is fast
    // I am aware that this is not thread safe. (CHumer)
    private Counter activeCounter;

    private boolean disposed;

    @Override
    protected void onCreate(final Env env) {
        this.instrumenter = env.getInstrumenter();

        assert profiler == null;
        this.profiler = new Profiler();
        env.registerService(profiler);

        /*
         * ensure even if the runtime is not disposed that instruments are disposed shouldn't
         * PolylgotEngine ensure that instruments are always disposed
         */
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                onDispose(env);
            }
        }));
    }

    @Override
    protected void onDispose(Env env) {
        if (!disposed) {
            counters.clear();
            if (binding != null) {
                binding.dispose();
                binding = null;
            }
            instrumenter = null;
            profiler = null;
            disposed = true;
        }
    }

    // Reconfigure what's being collected; does not affect collected data
    private void reset() {
        if (isCollecting) {
            if (binding != null) {
                binding.dispose();
            }
            final Builder filterBuilder = SourceSectionFilter.newBuilder();
            if (mimeTypes != null) {
                filterBuilder.mimeTypeIs(mimeTypes);
            }
            final SourceSectionFilter filter = filterBuilder.tagIs(StandardTags.RootTag.class).build();
            binding = instrumenter.attachFactory(filter, new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {
                    return createCountingNode(context);
                }
            });
        } else {
            if (binding != null) {
                binding.dispose();
                binding = null;
            }
        }
    }

    private ExecutionEventNode createCountingNode(EventContext context) {
        SourceSection sourceSection = context.getInstrumentedSourceSection();
        Counter counter = counters.get(sourceSection);
        if (counter == null) {
            counter = new Counter(sourceSection);
            counters.put(sourceSection, counter);
        }
        if (isTiming) {
            return TimedCounterNode.create(this, counter, context);
        } else {
            return new CounterNode(this, counter);
        }
    }

    private void printHistograms(PrintStream out) {
        final List<Counter> sortedCounters = new ArrayList<>(counters.values());

        boolean hasCompiled = false;
        for (Counter counter : sortedCounters) {
            if (counter.getInvocations(TimeKind.COMPILED) > 0) {
                hasCompiled = true;
            }
        }

        if (hasCompiled) {
            printHistogram(out, sortedCounters, TimeKind.INTERPRETED_AND_COMPILED);
            printHistogram(out, sortedCounters, TimeKind.INTERPRETED);
            printHistogram(out, sortedCounters, TimeKind.COMPILED);
        } else {
            printHistogram(out, sortedCounters, TimeKind.INTERPRETED);
        }

    }

    private void printHistogram(PrintStream out, List<Counter> sortedCounters, final TimeKind time) {
        Collections.sort(sortedCounters, new Comparator<Counter>() {
            @Override
            public int compare(Counter o1, Counter o2) {
                if (isTiming) {
                    return Long.compare(o2.getSelfTime(time), o1.getSelfTime(time));
                } else {
                    return Long.compare(o2.getInvocations(time), o1.getInvocations(time));
                }
            }
        });

        out.println("Truffle profiler histogram for mode " + time);
        out.println(String.format("%12s | %7s | %11s | %7s | %11s | %-30s | %s ", //
                        "Invoc", "Total", "PerInvoc", "SelfTime", "PerInvoc", "Source", "Code"));
        for (Counter counter : sortedCounters) {
            final long invocations = counter.getInvocations(time);
            if (invocations <= 0L) {
                continue;
            }
            double totalTimems = counter.getTotalTime(time) / 1000000.0d;
            double selfTimems = counter.getSelfTime(time) / 1000000.0d;
            out.println(String.format("%12d |%6.0fms |%10.3fms |%7.0fms |%10.3fms | %-30s | %s", //
                            invocations, totalTimems, totalTimems / invocations,  //
                            selfTimems, selfTimems / invocations, //
                            getShortDescription(counter.getSourceSection()), getShortSource(counter.getSourceSection())));
        }
        out.println();
    }

    private static Object getShortSource(SourceSection sourceSection) {
        if (sourceSection.getSource() == null) {
            return "<unknown>";
        }

        String code = sourceSection.getCode();
        if (code.length() > MAX_CODE_LENGTH) {
            code = code.substring(0, MAX_CODE_LENGTH);
        }

        return code.replaceAll("\\n", "\\\\n");
    }

    // custom version of SourceSection#getShortDescription
    private static String getShortDescription(SourceSection sourceSection) {
        if (sourceSection.getSource() == null) {
            return sourceSection.getIdentifier();
        }
        StringBuilder b = new StringBuilder();
        if (sourceSection.getIdentifier() != null) {
            b.append(sourceSection.getSource().getName());
        } else {
            b.append("<unknown>");
        }
        b.append(":line=");

        if (sourceSection.getStartLine() == sourceSection.getEndLine()) {
            b.append(sourceSection.getStartLine());

        } else {
            b.append(sourceSection.getStartLine()).append("-").append(sourceSection.getEndLine());
        }

        b.append(":chars=");
        if (sourceSection.getCharIndex() == sourceSection.getEndColumn()) {
            b.append(sourceSection.getCharIndex());
        } else {
            b.append(sourceSection.getCharIndex()).append("-").append(sourceSection.getEndColumn());
        }

        return b.toString();
    }

    private static String[] parseMimeTypes(String property) {
        if (property != null) {
            return property.split(";");
        } else {
            return null;
        }
    }

    private void setCollecting(boolean isCollecting) {
        if (this.isCollecting != isCollecting) {
            this.isCollecting = isCollecting;
            reset();
        }
    }

    private void setTiming(boolean isTiming) {
        if (this.isTiming != isTiming) {
            this.isTiming = isTiming;
            reset();
        }
    }

    private void setMimeTypes(String[] newTypes) {
        mimeTypes = newTypes != null && newTypes.length > 0 ? newTypes : null;
        reset();
    }

    private boolean hasData() {
        for (Counter counter : counters.values()) {
            if (counter.getInvocations(TimeKind.INTERPRETED_AND_COMPILED) > 0) {
                return true;
            }
        }
        return false;
    }

    private void clearData() {
        for (Counter counter : counters.values()) {
            counter.clear();
        }
    }

    private Map<SourceSection, Counter> getCounters() {
        return Collections.unmodifiableMap(counters);
    }

    private static class TimedCounterNode extends CounterNode {

        private final EventContext context;
        private final FrameSlot parentCounterSlot;
        private final FrameSlot timeStartedSlot;
        private final ConditionProfile parentNotNullProfile = ConditionProfile.createBinaryProfile();

        private static final Object KEY_TIME_STARTED = new Object();
        private static final Object KEY_PARENT_COUNTER = new Object();

        TimedCounterNode(TruffleProfiler truffleProfiler, Counter counter, EventContext context) {
            super(truffleProfiler, counter);
            this.context = context;
            FrameDescriptor frameDescriptor = context.getInstrumentedNode().getRootNode().getFrameDescriptor();
            this.timeStartedSlot = frameDescriptor.findOrAddFrameSlot(KEY_TIME_STARTED, "profiler:timeStarted", FrameSlotKind.Long);
            this.parentCounterSlot = frameDescriptor.findOrAddFrameSlot(KEY_PARENT_COUNTER, "profiler:parentCounter", FrameSlotKind.Object);
        }

        @Override
        protected void onDispose(VirtualFrame frame) {
            FrameDescriptor frameDescriptor = context.getInstrumentedNode().getRootNode().getFrameDescriptor();
            if (frameDescriptor.getIdentifiers().contains(KEY_TIME_STARTED)) {
                frameDescriptor.removeFrameSlot(timeStartedSlot);
            }
            if (frameDescriptor.getIdentifiers().contains(KEY_PARENT_COUNTER)) {
                frameDescriptor.removeFrameSlot(parentCounterSlot);
            }
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            frame.setLong(timeStartedSlot, System.nanoTime());
            super.onEnter(frame);
            frame.setObject(parentCounterSlot, truffleProfiler.activeCounter);
            truffleProfiler.activeCounter = counter;
            if (CompilerDirectives.inInterpreter()) {
                counter.compiled = false;
            } else {
                counter.compiled = true;
            }
        }

        @Override
        protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            onReturnValue(frame, null);
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {

            long startTime;
            Counter parentCounter;
            try {
                startTime = frame.getLong(timeStartedSlot);
                parentCounter = (Counter) frame.getObject(parentCounterSlot);
            } catch (FrameSlotTypeException e) {
                throw new AssertionError();
            }

            long timeNano = System.nanoTime() - startTime;
            if (CompilerDirectives.inInterpreter()) {
                counter.interpretedTotalTime += timeNano;
            } else {
                counter.compiledTotalTime += timeNano;
            }
            // the condition if parentCounter is usually only for a root method null
            // after that it is always set. So it makes sense to speculate.
            if (parentNotNullProfile.profile(parentCounter != null)) {
                // do not speculate on parentCounter.compiled condition very likely to invalidate
                if (parentCounter.compiled) {
                    parentCounter.compiledChildTime += timeNano;
                } else {
                    parentCounter.interpretedChildTime += timeNano;
                }
            }
            truffleProfiler.activeCounter = parentCounter;

        }

        /* Static factory required for lazy class loading */
        static CounterNode create(TruffleProfiler profiler, Counter counter, EventContext context) {
            return new TimedCounterNode(profiler, counter, context);
        }

    }

    private static class CounterNode extends ExecutionEventNode {

        protected final TruffleProfiler truffleProfiler;
        protected final Counter counter;

        CounterNode(TruffleProfiler truffleProfiler, Counter counter) {
            this.truffleProfiler = truffleProfiler;
            this.counter = counter;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                counter.interpretedInvocations++;
            } else {
                counter.compiledInvocations++;
            }
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }
    }

    /**
     * Access to Truffle profiling services.
     *
     * @since 0.15
     */
    public final class Profiler {

        private Profiler() {
        }

        /**
         * Controls whether profile data is being collected, {@code false} by default.
         * <p>
         * Any collected data remains available while collecting is turned off. Unless explicitly
         * {@linkplain #clearData() cleared}, previously collected data will be included when
         * collection resumes.
         *
         * @since 0.15
         */
        public void setCollecting(boolean collecting) {
            assert !disposed;
            TruffleProfiler.this.setCollecting(collecting);
        }

        /**
         * Is data currently being collected (default {@code false})?
         * <p>
         * If data is not being collected, any previously collected data remains. Unless explicitly
         * {@linkplain #clearData() cleared}, previously collected data will be included when
         * collection resumes.
         *
         * @since 0.15
         */
        public boolean isCollecting() {
            assert !disposed;
            return TruffleProfiler.this.isCollecting;
        }

        /**
         * Controls whether profile data being collected includes timing, {@code false} by default.
         * <p>
         * If data is not currently being {@linkplain #isCollecting() collected}, this setting
         * remains and takes effect when collection resumes.
         * <p>
         * While timing data is not being collected, any previously collected timing data remains.
         * Unless explicitly {@linkplain #clearData() cleared}, previously collected timing data
         * will be included in data collected when collection resumes.
         *
         * @since 0.15
         */
        public void setTiming(boolean timing) {
            assert !disposed;
            TruffleProfiler.this.setTiming(timing);
        }

        /**
         * Does data being {@linkplain #isCollecting() collected} include timing (default
         * {@code false})?
         *
         * @since 0.15
         */
        public boolean isTiming() {
            assert !disposed;
            return isTiming;
        }

        /**
         * Replaces the list of MIME types for which data is being collected, {@code null} for
         * <strong>ANY</strong>.
         *
         * @param newMimeTypes new list of MIME types, {@code null} or an empty list matches any
         *            MIME type.
         *
         * @since 0.15
         */
        public void setMimeTypes(String[] newMimeTypes) {
            assert !disposed;
            TruffleProfiler.this.setMimeTypes(newMimeTypes);
        }

        /**
         * Gets MIME types for which data is being {@linkplain #isCollecting() collected}.
         *
         * @return MIME types matching sources being profiled; {@code null} matches
         *         <strong>ANY</strong> MIME type.
         * @since 0.15
         */
        public String[] getMimeTypes() {
            assert !disposed;
            return mimeTypes == null ? null : Arrays.copyOf(mimeTypes, mimeTypes.length);
        }

        /**
         * Is any data currently collected?
         *
         * @since 0.15
         */
        public boolean hasData() {
            assert !disposed;
            return TruffleProfiler.this.hasData();
        }

        /**
         * Resets all collected data to zero.
         *
         * @since 0.15
         */
        public void clearData() {
            assert !disposed;
            TruffleProfiler.this.clearData();
        }

        /**
         * Gets an unmodifiable map of all counters.
         *
         * @since 0.15
         */
        public Map<SourceSection, Counter> getCounters() {
            assert !disposed;
            return TruffleProfiler.this.getCounters();
        }

        /**
         * Prints a simple, default textual summary of currently collected data, format subject to
         * change. Use {@linkplain #getCounters() counters} explicitly for reliable access.
         *
         * @since 0.15
         */
        public void printHistograms(PrintStream out) {
            assert !disposed;
            TruffleProfiler.this.printHistograms(out);
        }
    }

    /**
     * Access Truffle profiling data for a program element.
     *
     * @since 0.15
     */
    public static final class Counter {

        enum TimeKind {
            INTERPRETED_AND_COMPILED,
            INTERPRETED,
            COMPILED
        }

        private final SourceSection sourceSection;
        private long interpretedInvocations;
        private long interpretedChildTime;
        private long interpretedTotalTime;
        private long compiledInvocations;
        private long compiledTotalTime;
        private long compiledChildTime;

        /*
         * This is a rather hacky flag to find out if the parent was already compiled and so the
         * child time is accounted as interpretedChildTime or compiledChildTime (CHumer)
         */
        private boolean compiled;

        private Counter(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        private void clear() {
            interpretedInvocations = 0;
            interpretedChildTime = 0;
            interpretedTotalTime = 0;
            compiledInvocations = 0;
            compiledTotalTime = 0;
            compiledChildTime = 0;
        }

        /**
         * The program element being profiled.
         *
         * @since 0.15
         */
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        /**
         * Number of times the program element has been executed since the last time data was
         * {@linkplain #clear() cleared}.
         *
         * @since 0.15
         */
        public long getInvocations(TimeKind kind) {
            switch (kind) {
                case INTERPRETED_AND_COMPILED:
                    return interpretedInvocations + compiledInvocations;
                case COMPILED:
                    return compiledInvocations;
                case INTERPRETED:
                    return interpretedInvocations;
                default:
                    throw new AssertionError();
            }
        }

        /**
         * Total time taken executing the program element since the last time data was
         * {@linkplain #clear() cleared}.
         *
         * @since 0.15
         */
        public long getTotalTime(TimeKind kind) {
            switch (kind) {
                case INTERPRETED_AND_COMPILED:
                    return interpretedTotalTime + compiledTotalTime;
                case COMPILED:
                    return compiledTotalTime;
                case INTERPRETED:
                    return interpretedTotalTime;
                default:
                    throw new AssertionError();
            }
        }

        /**
         * Self time taken executing the program element since the last time data was
         * {@linkplain #clear() cleared}.
         *
         * @since 0.15
         */
        public long getSelfTime(TimeKind kind) {
            switch (kind) {
                case INTERPRETED_AND_COMPILED:
                    return interpretedTotalTime + compiledTotalTime - compiledChildTime - interpretedChildTime;
                case COMPILED:
                    return compiledTotalTime - compiledChildTime;
                case INTERPRETED:
                    return interpretedTotalTime - interpretedChildTime;
                default:
                    throw new AssertionError();
            }
        }
    }
}
