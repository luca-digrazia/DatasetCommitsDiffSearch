/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.tests;

import junit.framework.AssertionFailedError;

import org.junit.*;

import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.nodes.*;

public class StraighteningTest extends GraphTest {

    private static final String REFERENCE_SNIPPET = "ref";

    public static boolean ref(int a, int b) {
        return a == b;
    }

    public static boolean test1Snippet(int a, int b) {
        int c = a;
        if (c == b) {
            c = 0x55;
        }
        if (c != 0x55) {
            return false;
        }
        return true;
    }

    public static boolean test3Snippet(int a, int b) {
        int val = (int) System.currentTimeMillis();
        int c = val + 1;
        if (a == b) {
            c = val;
        }
        if (c != val) {
            return false;
        }
        return true;
    }

    public static boolean test2Snippet(int a, int b) {
        int c;
        if (a == b) {
            c = 1;
        } else {
            c = 0;
        }
        return c == 1;
    }

    @Test(expected = AssertionFailedError.class)
    public void test1() {
        test("test1Snippet");
    }

    @Test(expected = AssertionFailedError.class)
    public void test2() {
        test("test2Snippet");
    }

    @Test(expected = AssertionFailedError.class)
    public void test3() {
        test("test3Snippet");
    }

    private void test(String snippet) {
        StructuredGraph graph = parse(snippet);
        Debug.dump(graph, "Graph");
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        StructuredGraph referenceGraph = parse(REFERENCE_SNIPPET);
        assertEquals(referenceGraph, graph);
    }
}
