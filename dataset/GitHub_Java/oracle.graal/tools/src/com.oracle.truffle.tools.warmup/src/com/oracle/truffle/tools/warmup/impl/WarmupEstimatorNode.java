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
package com.oracle.truffle.tools.warmup.impl;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

class WarmupEstimatorNode extends ExecutionEventNode {

    private long start;
    private List<Long> samples = new ArrayList<>();
    private static final String DOUBLE_FORMAT = "%-15s: %d\n";

    void printResults(PrintStream out, double epsilon) {
        final long peak = peak();
        out.printf(DOUBLE_FORMAT, "Peak", peak);
        final int peakStart = peakIter(peak, epsilon);
        out.printf(DOUBLE_FORMAT, "Peak Start", peakStart);
        out.printf(DOUBLE_FORMAT, "Warmup", warmup(peakStart, peak));
        out.printf(DOUBLE_FORMAT, "Iterations", samples.size());
    }

    private long warmup(int peakIter, long peak) {
        long warmup = 0;
        for (int i = 0; i < peakIter; i++) {
            warmup += samples.get(i) - peak;
        }
        return warmup;
    }

    private int peakIter(long peak, double epsilon) {
        for (int i = 0; i < samples.size(); i++) {
            if (samples.get(i) < peak * (1 + epsilon)) {
                return i;
            }
        }
        throw new AssertionError("Should not reach here.");
    }

    private Long peak() {
        return samples.stream().min(Long::compareTo).get();
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        start = System.nanoTime();
    }

    @Override
    protected void onReturnValue(VirtualFrame frame, Object result) {
        final long duration = System.nanoTime() - start;
        samples.add(duration);
    }
}
