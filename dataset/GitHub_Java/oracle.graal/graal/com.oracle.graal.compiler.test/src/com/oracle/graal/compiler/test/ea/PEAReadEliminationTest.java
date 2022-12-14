/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test.ea;

import static org.junit.Assert.*;

import java.util.concurrent.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.virtual.phases.ea.*;

public class PEAReadEliminationTest extends GraalCompilerTest {

    private StructuredGraph graph;

    public static Object staticField;

    public static class TestObject implements Callable<Integer> {

        public int x;
        public int y;

        public TestObject(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Integer call() throws Exception {
            return x;
        }
    }

    public static class TestObject2 {

        public Object x;
        public Object y;

        public TestObject2(Object x, Object y) {
            this.x = x;
            this.y = y;
        }
    }

    @SuppressWarnings("all")
    public static int testSimpleSnippet(TestObject a) {
        a.x = 2;
        return a.x;
    }

    @Test
    public void testSimple() {
        ValueNode result = getReturn("testSimpleSnippet").result();
        assertTrue(graph.getNodes(LoadFieldNode.class).isEmpty());
        assertTrue(result.isConstant());
        assertEquals(2, result.asConstant().asInt());
    }

    @SuppressWarnings("all")
    public static int testParamSnippet(TestObject a, int b) {
        a.x = b;
        return a.x;
    }

    @Test
    public void testParam() {
        ValueNode result = getReturn("testParamSnippet").result();
        assertTrue(graph.getNodes(LoadFieldNode.class).isEmpty());
        assertEquals(graph.getLocal(1), result);
    }

    @SuppressWarnings("all")
    public static int testMaterializedSnippet(int a) {
        TestObject obj = new TestObject(a, 0);
        staticField = obj;
        return obj.x;
    }

    @Test
    public void testMaterialized() {
        ValueNode result = getReturn("testMaterializedSnippet").result();
        assertTrue(graph.getNodes(LoadFieldNode.class).isEmpty());
        assertEquals(graph.getLocal(0), result);
    }

    @SuppressWarnings("all")
    public static int testPhiSnippet(TestObject a, int b) {
        if (b < 0) {
            a.x = 1;
        } else {
            a.x = 2;
        }
        return a.x;
    }

    @Test
    public void testPhi() {
        ValueNode result = getReturn("testPhiSnippet").result();
        assertTrue(graph.getNodes(LoadFieldNode.class).isEmpty());
        assertTrue(result instanceof PhiNode);
        PhiNode phi = (PhiNode) result;
        assertTrue(phi.valueAt(0).isConstant());
        assertTrue(phi.valueAt(1).isConstant());
        assertEquals(1, phi.valueAt(0).asConstant().asInt());
        assertEquals(2, phi.valueAt(1).asConstant().asInt());
    }

    @SuppressWarnings("all")
    public static void testSimpleStoreSnippet(TestObject a, int b) {
        a.x = b;
        a.x = b;
    }

    @Test
    public void testSimpleStore() {
        processMethod("testSimpleStoreSnippet");
        assertEquals(1, graph.getNodes().filter(StoreFieldNode.class).count());
    }

    final ReturnNode getReturn(String snippet) {
        processMethod(snippet);
        assertEquals(1, graph.getNodes(ReturnNode.class).count());
        return graph.getNodes(ReturnNode.class).first();
    }

    private void processMethod(final String snippet) {
        graph = parse(snippet);
        new ComputeProbabilityPhase().apply(graph);
        Assumptions assumptions = new Assumptions(false);
        new InliningPhase(runtime(), null, assumptions, null, getDefaultPhasePlan(), OptimisticOptimizations.ALL).apply(graph);
        GraalOptions.PEAReadCache = true;
        new PartialEscapeAnalysisPhase(runtime(), assumptions, false).apply(graph);
    }
}
