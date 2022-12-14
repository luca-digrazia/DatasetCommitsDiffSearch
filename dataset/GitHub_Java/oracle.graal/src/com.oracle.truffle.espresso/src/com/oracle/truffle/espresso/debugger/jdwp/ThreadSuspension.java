/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.debugger.jdwp;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class ThreadSuspension {

    @CompilerDirectives.CompilationFinal(dimensions = 1)
    private static StaticObject[] threads = new StaticObject[0];

    @CompilerDirectives.CompilationFinal(dimensions = 1)
    private static int[] suspensionCount = new int[0];

    public static void suspendThread(StaticObject thread) {
        for (int i = 0; i < threads.length; i++) {
            if (thread == threads[i]) {
                // increase the suspension count
                suspensionCount[i]++;
            }
        }
        // not yet suspended, so add to array
        StaticObject[] expanded = new StaticObject[threads.length + 1];
        System.arraycopy(threads, 0, expanded, 0, threads.length);
        expanded[threads.length] = thread;

        int[] temp = new int[threads.length + 1];
        System.arraycopy(suspensionCount, 0, temp, 0, threads.length);
        temp[threads.length] = 1;

        threads = expanded;
        suspensionCount = temp;
    }

    public static void resumeThread(StaticObject thread) {
        for (int i = 0; i < threads.length; i++) {
            if (thread == threads[i]) {
                if (suspensionCount[i] > 0) {
                    suspensionCount[i]--;
                }
            }
        }
    }

    public static int isSuspended(StaticObject thread) {
        for (int i = 0; i < threads.length; i++) {
            if (thread == threads[i]) {
                return suspensionCount[i] > 0 ? 1 : 0;
            }
        }
        return 0;
    }

    public static int getSuspensionCount(StaticObject thread) {
        for (int i = 0; i < threads.length; i++) {
            if (thread == threads[i]) {
                return suspensionCount[i];
            }
        }
        // this should never be reached
        return 0;
    }
}
