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
package com.oracle.graal.hotspot.ri;

import com.oracle.max.cri.ri.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.Compiler;

/**
 * Dummy profiling information in case that a method was not executed frequently enough so that
 * no profiling information does exist yet.
 */
public final class HotSpotNoProfilingInfo extends CompilerObject implements RiProfilingInfo {
    /**
     *
     */
    private static final long serialVersionUID = 4357945025049704109L;
    // Be optimistic and return false for exceptionSeen. A methodDataOop is allocated in case of a deoptimization.
    private static final HotSpotMethodDataAccessor noData = HotSpotMethodData.getNoDataAccessor(false);

    public HotSpotNoProfilingInfo(Compiler compiler) {
        super(compiler);
    }

    @Override
    public RiTypeProfile getTypeProfile(int bci) {
        return noData.getTypeProfile(null, -1);
    }

    @Override
    public double getBranchTakenProbability(int bci) {
        return noData.getBranchTakenProbability(null, -1);
    }

    @Override
    public double[] getSwitchProbabilities(int bci) {
        return noData.getSwitchProbabilities(null, -1);
    }

    @Override
    public RiExceptionSeen getExceptionSeen(int bci) {
        return noData.getExceptionSeen(null, -1);
    }

    @Override
    public int getExecutionCount(int bci) {
        return noData.getExecutionCount(null, -1);
    }
}
