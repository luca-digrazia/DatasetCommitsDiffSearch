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
package com.oracle.truffle.tools.profiler.impl;

import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.ProfilerNode;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class CPUSamplerCLI extends ProfilerCLI {

    enum Output {
        HISTOGRAM,
        CALLTREE
    }

    enum Mode {
        COMPILED,
        ROOTS,
        STATEMENTS
    }

    static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Output",
            Output.HISTOGRAM,
            new Function<String, Output>() {
                @Override
                public Output apply(String s) {
                    try {
                        return Output.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Output can be: histogram or calltree");
                    }
                }
            });

    static final OptionType<Mode> CLI_MODE_TYPE = new OptionType<>("Mode",
            Mode.COMPILED,
            new Function<String, Mode>() {
                @Override
                public Mode apply(String s) {
                    try {
                        return Mode.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Mode can be: compiled, roots or statements.");
                    }
                }
            });

    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    static final OptionKey<Mode> MODE = new OptionKey<>(Mode.COMPILED, CLI_MODE_TYPE);
    static final OptionKey<Long> SAMPLE_PERIOD = new OptionKey<>(1L);
    static final OptionKey<Long> DELAY_PERIOD = new OptionKey<>(0L);
    static final OptionKey<Integer> STACK_LIMIT = new OptionKey<>(10000);
    static final OptionKey<Output> OUTPUT = new OptionKey<>(Output.HISTOGRAM, CLI_OUTPUT_TYPE);

    static final OptionKey<Object[]> FILTER_ROOT = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);
    static final OptionKey<Object[]> FILTER_FILE = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);
    static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>("");

    static final OptionKey<Boolean> SAMPLE_INTERNAL = new OptionKey<>(false);

    static void handleOutput(TruffleInstrument.Env env, CPUSampler sampler, OptionDescriptors descriptors) {
        PrintStream out = new PrintStream(env.out());
        if (sampler.hasStackOverflowed()) {
            out.println("-------------------------------------------------------------------------------- ");
            out.println("ERROR: Shadow stack has overflowed its capacity of " + env.getOptions().get(STACK_LIMIT) + " during execution!");
            out.println("The gathered data is incomplete and incorrect!");
            String name = "";
            Iterator<OptionDescriptor> iterator = descriptors.iterator();
            while (iterator.hasNext()) {
                OptionDescriptor descriptor = iterator.next();
                if (descriptor.getKey().equals(STACK_LIMIT)) {
                    name = descriptor.getName();
                    break;
                }
            }
            assert !name.equals("");
            out.println("Use --" + name + "=<" + STACK_LIMIT.getType().getName() + "> to set stack capacity.");
            out.println("-------------------------------------------------------------------------------- ");
            return;
        }
        switch (env.getOptions().get(OUTPUT)) {
            case HISTOGRAM:
                printSamplingHistogram(out, sampler);
                break;
            case CALLTREE:
                printSamplingCallTree(out, sampler);
                break;
        }
    }

    private static void printSamplingHistogram(PrintStream out, CPUSampler sampler) {

        final Map<SourceLocation, List<ProfilerNode<CPUSampler.Payload>>> histogram = sampler.computeHistogram();

        List<List<ProfilerNode<CPUSampler.Payload>>> lines = new ArrayList<>(histogram.values());
        Collections.sort(lines, new Comparator<List<ProfilerNode<CPUSampler.Payload>>>() {
            @Override
            public int compare(List<ProfilerNode<CPUSampler.Payload>> o1, List<ProfilerNode<CPUSampler.Payload>> o2) {
                long sum1 = 0;
                for (ProfilerNode<CPUSampler.Payload> tree : o1) {
                    sum1 += tree.getPayload().getSelfHitCount();
                }

                long sum2 = 0;
                for (ProfilerNode<CPUSampler.Payload> tree : o2) {
                    sum2 += tree.getPayload().getSelfHitCount();
                }
                return Long.compare(sum2, sum1);
            }
        });

        int maxLength = 10;
        for (List<ProfilerNode<CPUSampler.Payload>> line : lines) {
            maxLength = Math.max(computeRootNameMaxLength(line.get(0)), maxLength);
        }

        String title = String.format(" %-" + maxLength + "s |      Total Time     |  Opt %% ||       Self Time     |  Opt %% | Location             ", "Name");
        long samples = sampler.getTotalSamples();
        String sep = repeat("-", title.length());
        out.println(sep);
        out.println(String.format("Sampling Histogram. Recorded %s samples with period %dms", samples, sampler.getPeriod()));
        out.println("  Self Time: Time spent on the top of the stack.");
        out.println("  Total Time: Time the location spent on the stack. ");
        out.println("  Opt %: Percent of time spent in compiled and therfore non-interpreted code.");
        out.println(sep);
        out.println(title);
        out.println(sep);
        for (List<ProfilerNode<CPUSampler.Payload>> line : lines) {
            printAttributes(out, sampler, "", line, maxLength);
        }
        out.println(sep);
    }

    private static void printSamplingCallTree(PrintStream out, CPUSampler sampler) {
        int maxLength = Math.max(10, computeTitleMaxLength(sampler.getRootNodes(), 0));
        String title = String.format(" %-" + maxLength + "s |      Total Time     |  Opt %% ||       Self Time     |  Opt %% | Location             ", "Name");
        String sep = repeat("-", title.length());
        out.println(sep);
        out.println(String.format("Sampling CallTree. Recorded %s samples with period %dms.", sampler.getTotalSamples(), sampler.getPeriod()));
        out.println("  Self Time: Time spent on the top of the stack.");
        out.println("  Total Time: Time spent somewhere on the stack. ");
        out.println("  Opt %: Percent of time spent in compiled and therfore non-interpreted code.");
        out.println(sep);
        out.println(title);
        out.println(sep);
        printSamplingCallTreeRec(sampler, maxLength, "", sampler.getRootNodes(), out);
        out.println(sep);
    }

    private static void printSamplingCallTreeRec(CPUSampler sampler, int maxRootLength, String prefix, Collection<ProfilerNode<CPUSampler.Payload>> children, PrintStream out) {
        List<ProfilerNode<CPUSampler.Payload>> sortedChildren = new ArrayList<>(children);
        Collections.sort(sortedChildren, new Comparator<ProfilerNode<CPUSampler.Payload>>() {
            @Override
            public int compare(ProfilerNode<CPUSampler.Payload> o1, ProfilerNode<CPUSampler.Payload> o2) {
                return Long.compare(o2.getPayload().getHitCount(), o1.getPayload().getHitCount());
            }
        });

        for (ProfilerNode<CPUSampler.Payload> treeNode : sortedChildren) {
            if (treeNode == null) {
                continue;
            }
            printAttributes(out, sampler, prefix, Arrays.asList(treeNode), maxRootLength);
            printSamplingCallTreeRec(sampler, maxRootLength, prefix + " ", treeNode.getChildren(), out);
        }
    }

    private static int computeTitleMaxLength(Collection<ProfilerNode<CPUSampler.Payload>> children, int baseLength) {
        int maxLength = baseLength;
        for (ProfilerNode<CPUSampler.Payload> treeNode : children) {
            int rootNameLength = computeRootNameMaxLength(treeNode);
            maxLength = Math.max(baseLength + rootNameLength, maxLength);
            maxLength = Math.max(maxLength, computeTitleMaxLength(treeNode.getChildren(), baseLength + 1));
        }
        return maxLength;
    }

    private static boolean intersectsLines(SourceSection section1, SourceSection section2) {
        int x1 = section1.getStartLine();
        int x2 = section1.getEndLine();
        int y1 = section2.getStartLine();
        int y2 = section2.getEndLine();
        return x2 >= y1 && y2 >= x1;
    }

    private static void printAttributes(PrintStream out, CPUSampler sampler, String prefix, List<ProfilerNode<CPUSampler.Payload>> nodes, int maxRootLength) {
        long samplePeriod = sampler.getPeriod();
        long samples = sampler.getTotalSamples();

        long selfInterpreted = 0;
        long selfCompiled = 0;
        long totalInterpreted = 0;
        long totalCompiled = 0;
        for (ProfilerNode<CPUSampler.Payload> tree : nodes) {
            CPUSampler.Payload payload = tree.getPayload();
            selfInterpreted += payload.getSelfInterpretedHitCount();
            selfCompiled += payload.getSelfCompiledHitCount();
            if (!tree.isRecursive()) {
                totalInterpreted += payload.getInterpretedHitCount();
                totalCompiled += payload.getCompiledHitCount();
            }
        }

        long totalSamples = totalInterpreted + totalCompiled;
        if (totalSamples <= 0L) {
            // hide methods without any cost
            return;
        }
        assert totalSamples < samples;
        ProfilerNode<CPUSampler.Payload> firstNode = nodes.get(0);
        SourceSection sourceSection = firstNode.getSourceSection();
        String rootName = firstNode.getRootName();

        if (!firstNode.getTags().contains(StandardTags.RootTag.class)) {
            rootName += "~" + formatIndices(sourceSection, needsColumnSpecifier(firstNode));
        }

        long selfSamples = selfInterpreted + selfCompiled;
        long selfTime = selfSamples * samplePeriod;
        double selfCost = selfSamples / (double) samples;
        double selfCompiledP = 0.0;
        if (selfSamples > 0) {
            selfCompiledP = selfCompiled / (double) selfSamples;
        }
        String selfTimes = String.format("%10dms %5.1f%% | %5.1f%%", selfTime, selfCost * 100, selfCompiledP * 100);

        long totalTime = totalSamples * samplePeriod;
        double totalCost = totalSamples / (double) samples;
        double totalCompiledP = totalCompiled / (double) totalSamples;
        String totalTimes = String.format("%10dms %5.1f%% | %5.1f%%", totalTime, totalCost * 100, totalCompiledP * 100);

        String location = getShortDescription(sourceSection);

        out.println(String.format(" %-" + Math.max(maxRootLength, 10) + "s | %s || %s | %s ", //
                prefix + rootName, totalTimes, selfTimes, location));
    }

    private static boolean needsColumnSpecifier(ProfilerNode<CPUSampler.Payload> firstNode) {
        boolean needsColumnsSpecifier = false;
        SourceSection sourceSection = firstNode.getSourceSection();
        for (ProfilerNode<CPUSampler.Payload> node : firstNode.getParent().getChildren()) {
            if (node.getSourceSection() == sourceSection) {
                continue;
            }
            if (intersectsLines(node.getSourceSection(), sourceSection)) {
                needsColumnsSpecifier = true;
                break;
            }
        }
        return needsColumnsSpecifier;
    }

    private static int computeRootNameMaxLength(ProfilerNode<CPUSampler.Payload> treeNode) {
        int length = treeNode.getRootName().length();
        if (!treeNode.getTags().contains(StandardTags.RootTag.class)) {
            // reserve some space for the line and column info
            length += formatIndices(treeNode.getSourceSection(), needsColumnSpecifier(treeNode)).length() + 1;
        }
        return length;
    }
}