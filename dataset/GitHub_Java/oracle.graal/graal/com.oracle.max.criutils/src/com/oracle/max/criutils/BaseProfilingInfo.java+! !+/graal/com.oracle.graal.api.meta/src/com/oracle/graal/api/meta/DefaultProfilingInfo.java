/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.criutils;

import com.oracle.graal.api.meta.*;


/**
 * Dummy profiling information in case that a method was not executed frequently enough so that
 * no profiling information does exist yet, or in case that the profiling information should not be used.
 */
public final class BaseProfilingInfo implements ProfilingInfo {
    private static final ProfilingInfo[] NO_PROFILING_INFO = new ProfilingInfo[] {
        new BaseProfilingInfo(ExceptionSeen.TRUE),
        new BaseProfilingInfo(ExceptionSeen.FALSE),
        new BaseProfilingInfo(ExceptionSeen.NOT_SUPPORTED)
    };

    private final ExceptionSeen exceptionSeen;

    BaseProfilingInfo(ExceptionSeen exceptionSeen) {
        this.exceptionSeen = exceptionSeen;
    }

    @Override
    public int codeSize() {
        return 0;
    }

    @Override
    public JavaTypeProfile getTypeProfile(int bci) {
        return null;
    }

    @Override
    public double getBranchTakenProbability(int bci) {
        return -1;
    }

    @Override
    public double[] getSwitchProbabilities(int bci) {
        return null;
    }

    @Override
    public ExceptionSeen getExceptionSeen(int bci) {
        return exceptionSeen;
    }

    @Override
    public int getExecutionCount(int bci) {
        return -1;
    }

    public static ProfilingInfo get(ExceptionSeen exceptionSeen) {
        return NO_PROFILING_INFO[exceptionSeen.ordinal()];
    }

    @Override
    public int getDeoptimizationCount(DeoptimizationReason reason) {
        return 0;
    }

    @Override
    public String toString() {
        return "BaseProfilingInfo<" + MetaUtil.profileToString(this, null, "; ") + ">";
    }
}
