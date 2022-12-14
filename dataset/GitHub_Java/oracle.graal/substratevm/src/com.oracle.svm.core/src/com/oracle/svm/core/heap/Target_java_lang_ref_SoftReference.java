/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LUDICROUSLY_FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.lang.ref.SoftReference;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.TimeUtils;

/**
 * A less-than-strong but more-than-weak reference, offering collection according to the time of
 * last access and current memory pressure.
 */
@TargetClass(SoftReference.class)
final class Target_java_lang_ref_SoftReference<T> {
    /** The current time, set by the garbage collector. */
    @Alias @InjectAccessors(SoftReferenceClockAccessor.class) //
    static long clock;

    /** The {@link #clock} value when {@code get()} was last called. */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    long timestamp;
}

final class SoftReferenceClockAccessor {
    static long clock = 0;

    static long get() {
        if (probability(VERY_SLOW_PATH_PROBABILITY, clock == 0)) {
            update(); // only once if a SoftReference is created before the very first GC
        }
        return clock;
    }

    static void update() {
        long now = TimeUtils.divideNanosToMillis(System.nanoTime());
        if (probability(LUDICROUSLY_FAST_PATH_PROBABILITY, now >= clock)) {
            clock = now; // just to be safe: nanoTime() should be monotonous
        }
    }
}
