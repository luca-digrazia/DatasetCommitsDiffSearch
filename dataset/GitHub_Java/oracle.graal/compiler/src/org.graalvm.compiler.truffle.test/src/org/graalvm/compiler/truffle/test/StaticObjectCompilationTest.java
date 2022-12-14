/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticPropertyKind;
import com.oracle.truffle.api.staticobject.StaticShape;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.polyglot.Context;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.io.Closeable;

@RunWith(Theories.class)
public class StaticObjectCompilationTest extends PartialEvaluationTest {
    static class FieldBasedStorage {
        final int finalProperty = 42;
        int property;
    }

    @DataPoints //
    public static final StaticObjectTestEnvironment[] environments = StaticObjectTestEnvironment.getEnvironments();

    @AfterClass
    public static void teardown() {
        for (StaticObjectTestEnvironment env : environments) {
            env.close();
        }
    }

    @Test
    public void simplePropertyAccesses() {
        // Field-based storage
        try (StaticObjectTestEnvironment te = new StaticObjectTestEnvironment(false)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty finalProperty = new DefaultStaticProperty("finalProperty", StaticPropertyKind.Int, true);
            StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
            builder.property(finalProperty).property(property);
            Object staticObject = builder.build().getFactory().create();

            FieldBasedStorage fbs = new FieldBasedStorage();

            // Property set
            assertPartialEvalEquals(toRootNode((f) -> fbs.property = 42), toRootNode((f) -> {
                finalProperty.setInt(staticObject, 42);
                return null;
            }), new Object[0]);
            assertPartialEvalEquals(toRootNode((f) -> fbs.property = 42), toRootNode((f) -> {
                property.setInt(staticObject, 42);
                return null;
            }), new Object[0]);

            finalProperty.setInt(staticObject, 42);
            // Property get
            assertPartialEvalEquals(toRootNode((f) -> 42), toRootNode((f) -> finalProperty.getInt(staticObject)), new Object[0]);
            assertPartialEvalEquals(toRootNode((f) -> fbs.finalProperty), toRootNode((f) -> finalProperty.getInt(staticObject)), new Object[0]);
            assertPartialEvalEquals(toRootNode((f) -> fbs.property), toRootNode((f) -> property.getInt(staticObject)), new Object[0]);
        }
    }

    @Test
    public void propertyAccessesInHierarchy() {
        // Field-based storage
        try (StaticObjectTestEnvironment te = new StaticObjectTestEnvironment(false)) {
            StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s1p1 = new DefaultStaticProperty("property", StaticPropertyKind.Int, true);
            b1.property(s1p1);
            StaticShape<DefaultStaticObjectFactory> s1 = b1.build();

            StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s2p1 = new DefaultStaticProperty("property", StaticPropertyKind.Int, true);
            b2.property(s2p1);
            StaticShape<DefaultStaticObjectFactory> s2 = b2.build(s1);
            Object o2 = s2.getFactory().create();

            s1p1.setInt(o2, 24);
            s2p1.setInt(o2, 42);

            assertPartialEvalEquals(toRootNode((f) -> 24), toRootNode((f) -> s1p1.getInt(o2)), new Object[0]);
            assertPartialEvalEquals(toRootNode((f) -> 42), toRootNode((f) -> s2p1.getInt(o2)), new Object[0]);
        }
    }

    @Theory
    public void allocation(StaticObjectTestEnvironment te) {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(te, new AllocationNode(te));
            assertNoInvokes(graph);
            if (te.arrayBased) {
                // The array that stores primitive fields
                assertCount(graph, VirtualArrayNode.class, 1);
            }
            assertCount(graph, VirtualInstanceNode.class, 1);
        } finally {
            te.context.leave();
        }
    }

    @Theory
    public void readOnce(StaticObjectTestEnvironment te) {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(te, new ReadOnceNode(te));
            assertNoInvokes(graph);

            if (te.arrayBased) {
                assertCount(graph, RawLoadNode.class, 1);
            } else {
                assertCount(graph, LoadFieldNode.class, 1);
            }
        } finally {
            te.context.leave();
        }
    }

    @Theory
    public void readMultipleAndAdd(StaticObjectTestEnvironment te) {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(te, new ReadMultipleAndSumNode(te));
            assertNoInvokes(graph);

            if (te.arrayBased) {
                assertCount(graph, RawLoadNode.class, 1);
            } else {
                assertCount(graph, LoadFieldNode.class, 1);
            }
            assertCount(graph, AddNode.class, 2);
        } finally {
            te.context.leave();
        }
    }

    @Theory
    public void writeOnce(StaticObjectTestEnvironment te) {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(te, new WriteOnceNode(te));
            assertNoInvokes(graph);

            if (te.arrayBased) {
                assertCount(graph, RawStoreNode.class, 1);
            } else {
                assertCount(graph, StoreFieldNode.class, 1);
            }
        } finally {
            te.context.leave();
        }
    }

    @Theory
    public void writeMultiple(StaticObjectTestEnvironment te) {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(te, new WriteMultipleNode(te));
            assertNoInvokes(graph);

            if (te.arrayBased) {
                assertCount(graph, RawStoreNode.class, 3);
            } else {
                assertCount(graph, StoreFieldNode.class, 1);
            }
        } finally {
            te.context.leave();
        }
    }

    @Theory
    public void allocateSetAndGet(StaticObjectTestEnvironment te) {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(te, new AllocateSetAndGetNode(te));
            assertNoInvokes(graph);
            assertCount(graph, VirtualInstanceNode.class, 0);
            assertCount(graph, RawLoadNode.class, 0);
            assertCount(graph, LoadFieldNode.class, 0);
            assertCount(graph, RawStoreNode.class, 0);
            assertCount(graph, StoreFieldNode.class, 0);
        } finally {
            te.context.leave();
        }
    }

    private StructuredGraph partialEval(StaticObjectTestEnvironment te, StaticObjectAbstractNode node) {
        RootNode rootNode = new StaticObjectRootNode(te.testLanguage, new FrameDescriptor(), node);
        StructuredGraph graph = partialEval(rootNode);
        return graph;
    }

    private static void assertNoInvokes(StructuredGraph graph) {
        for (MethodCallTargetNode node : graph.getNodes(MethodCallTargetNode.TYPE)) {
            Assert.fail("Found invalid method call target node: " + node + " (" + node.targetMethod() + ")");
        }
    }

    private static void assertCount(StructuredGraph graph, Class<? extends org.graalvm.compiler.graph.Node> nodeClass, int expected) {
        Assert.assertEquals(expected, graph.getNodes().filter(nodeClass).count());
    }

    private abstract static class StaticObjectAbstractNode extends Node {
        final StaticShape<DefaultStaticObjectFactory> shape;
        final StaticProperty property;

        StaticObjectAbstractNode(StaticObjectTestEnvironment te) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
            builder.property(property);
            shape = builder.build();
        }

        abstract Object execute(VirtualFrame frame);
    }

    private static class AllocationNode extends StaticObjectAbstractNode {
        AllocationNode(StaticObjectTestEnvironment te) {
            super(te);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return shape.getFactory().create();
        }
    }

    private static class ReadOnceNode extends StaticObjectAbstractNode {
        final Object staticObject;

        ReadOnceNode(StaticObjectTestEnvironment te) {
            super(te);
            staticObject = shape.getFactory().create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return property.getInt(staticObject);
        }
    }

    private static class ReadMultipleAndSumNode extends StaticObjectAbstractNode {
        final Object staticObject;

        ReadMultipleAndSumNode(StaticObjectTestEnvironment te) {
            super(te);
            staticObject = shape.getFactory().create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int a = property.getInt(staticObject);
            int b = property.getInt(staticObject);
            int c = property.getInt(staticObject);
            return a + b + c;
        }
    }

    private static class WriteOnceNode extends StaticObjectAbstractNode {
        final Object staticObject;

        WriteOnceNode(StaticObjectTestEnvironment te) {
            super(te);
            staticObject = shape.getFactory().create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            property.setInt(staticObject, 42);
            return null;
        }
    }

    private static class WriteMultipleNode extends StaticObjectAbstractNode {
        final Object staticObject;

        WriteMultipleNode(StaticObjectTestEnvironment te) {
            super(te);
            staticObject = shape.getFactory().create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            property.setInt(staticObject, 42);
            property.setInt(staticObject, 42);
            property.setInt(staticObject, 42);
            return null;
        }
    }

    private static class AllocateSetAndGetNode extends StaticObjectAbstractNode {
        AllocateSetAndGetNode(StaticObjectTestEnvironment te) {
            super(te);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object staticObject = shape.getFactory().create();
            property.setInt(staticObject, 42);
            return property.getInt(staticObject) == 42;
        }
    }

    @NodeInfo
    private static class StaticObjectRootNode extends RootNode {
        @Child private StaticObjectAbstractNode node;

        StaticObjectRootNode(TruffleLanguage<?> language, FrameDescriptor descriptor, StaticObjectAbstractNode node) {
            super(language, descriptor);
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(frame);
        }
    }

    static class StaticObjectTestEnvironment implements Closeable {
        final boolean arrayBased;
        final TruffleLanguage<?> testLanguage;
        final Context context;

        StaticObjectTestEnvironment(boolean arrayBased) {
            this.arrayBased = arrayBased;
            context = Context.newBuilder(TestLanguage.TEST_LANGUAGE_ID).//
                            allowExperimentalOptions(true).//
                            option("engine.StaticObjectStorageStrategy", this.arrayBased ? "array-based" : "field-based").//
                            build();
            context.initialize(TestLanguage.TEST_LANGUAGE_ID);
            context.enter();
            testLanguage = TestLanguage.getCurrentContext().getLanguage();
            context.leave();
        }

        @Override
        public void close() {
            context.close();
        }

        @Override
        public String toString() {
            return (arrayBased ? "Array-based" : "Field-based") + " storage";
        }

        static StaticObjectTestEnvironment[] getEnvironments() {
            return new StaticObjectTestEnvironment[]{new StaticObjectTestEnvironment(true), new StaticObjectTestEnvironment(false)};
        }
    }

    @TruffleLanguage.Registration(id = TestLanguage.TEST_LANGUAGE_ID, name = TestLanguage.TEST_LANGUAGE_ID)
    public static class TestLanguage extends TruffleLanguage<TestContext> {
        static final String TEST_LANGUAGE_ID = "StaticObjectCompilationTest_TestLanguage";

        @Override
        protected TestContext createContext(Env env) {
            return new TestContext(this);
        }

        static TestContext getCurrentContext() {
            return getCurrentContext(TestLanguage.class);
        }
    }

    static class TestContext {
        private final TestLanguage language;

        TestContext(TestLanguage language) {
            this.language = language;
        }

        TestLanguage getLanguage() {
            return language;
        }
    }
}
