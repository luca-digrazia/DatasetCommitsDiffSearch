/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

/**
 * Provides access to the profiling information of one specific method. Every accessor method
 * returns the information that is available at the time of invocation. If a method is invoked
 * multiple times, it may return significantly different results for every invocation as the
 * profiling information may be changed by other Java threads at any time.
 */
public interface ProfilingInfo {

    /**
     * Represents the three possibilities that an exception was seen at a specific BCI.
     */
    public enum ExceptionSeen {
        TRUE, FALSE, NOT_SUPPORTED;

        public static ExceptionSeen get(boolean value) {
            return value ? TRUE : FALSE;
        }
    }

    /**
     * Returns the length of the bytecodes associated with this profile.
     */
    int getCodeSize();

    /**
     * Returns an estimate of how often the branch at the given byte code was taken.
     * 
     * @return The estimated probability, with 0.0 meaning never and 1.0 meaning always, or -1 if
     *         this information is not available.
     */
    double getBranchTakenProbability(int bci);

    /**
     * Returns an estimate of how often the switch cases are taken at the given BCI. The default
     * case is stored as the last entry.
     * 
     * @return A double value that contains the estimated probabilities, with 0.0 meaning never and
     *         1.0 meaning always, or -1 if this information is not available.
     */
    double[] getSwitchProbabilities(int bci);

    /**
     * Returns the TypeProfile for the given BCI.
     * 
     * @return Returns an JavaTypeProfile object, or null if not available.
     */
    JavaTypeProfile getTypeProfile(int bci);

    /**
     * Returns information if the given BCI did ever throw an exception.
     * 
     * @return {@link ExceptionSeen#TRUE} if the instruction has thrown an exception at least once,
     *         {@link ExceptionSeen#FALSE} if it never threw an exception, and
     *         {@link ExceptionSeen#NOT_SUPPORTED} if this information was not recorded.
     */
    ExceptionSeen getExceptionSeen(int bci);

    /**
     * Returns an estimate how often the current BCI was executed. Avoid comparing execution counts
     * to each other, as the returned value highly depends on the time of invocation.
     * 
     * @return the estimated execution count or -1 if not available.
     */
    int getExecutionCount(int bci);

    /**
     * Returns how frequently a method was deoptimized for the given deoptimization reason. This
     * only indicates how often the method did fall back to the interpreter for the execution and
     * does not indicate how often it was recompiled.
     * 
     * @param reason the reason for which the number of deoptimizations should be queried
     * @return the number of times the compiled method deoptimized for the given reason.
     */
    int getDeoptimizationCount(DeoptimizationReason reason);

}
