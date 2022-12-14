/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.optimize;

import java.lang.reflect.*;
import java.util.*;

import org.junit.*;

import com.oracle.graal.test.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.jtt.*;

@SuppressWarnings("unused")
public class ConditionalEliminiation02 extends JTTTest {

    private static Object o = null;

    private static class A {

        public A(int y) {
            this.y = y;
        }

        int y;
    }

    public int test(A a, boolean isNull, boolean isVeryNull) {
        if (o == null) {
            if (!isNull) {
                if (o == null) {
                    return a.y;
                }
            }
            if (!isVeryNull) {
                if (o == null) {
                    return a.y;
                }
            }
        }
        return -1;
    }

    @Test
    public void run0() throws Throwable {
        runTest(EnumSet.of(DeoptimizationReason.NullCheckException), "test", new A(5), false, false);
    }

    @Test
    public void run1() throws Throwable {
        runTest(EnumSet.of(DeoptimizationReason.NullCheckException), "test", new Object[]{null, true, true});
    }

}
