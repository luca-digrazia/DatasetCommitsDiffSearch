/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.gc.g1.G1ArrayRangePostWriteBarrier;
import org.graalvm.compiler.hotspot.gc.g1.G1ArrayRangePreWriteBarrier;
import org.graalvm.compiler.hotspot.gc.g1.G1PostWriteBarrier;
import org.graalvm.compiler.hotspot.gc.g1.G1PreWriteBarrier;
import org.graalvm.compiler.hotspot.gc.shared.SerialArrayRangeWriteBarrier;
import org.graalvm.compiler.hotspot.gc.shared.SerialWriteBarrier;
import org.graalvm.compiler.hotspot.phases.WriteBarrierAdditionPhase;
import org.graalvm.compiler.hotspot.phases.WriteBarrierVerificationPhase;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.LoopSafepointInsertionPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * The following tests validate the write barrier verification phase. For every tested snippet, an
 * array of write barrier indices and the total write barrier number are passed as parameters. The
 * indices denote the barriers that will be manually removed. The write barrier verification phase
 * runs after the write barrier removal and depending on the result an assertion might be generated.
 * The tests anticipate the presence or not of an assertion generated by the verification phase.
 */
public class WriteBarrierVerificationTest extends HotSpotGraalCompilerTest {

    public static int barrierIndex;

    private final GraalHotSpotVMConfig config = runtime().getVMConfig();

    public static class Container {

        public Container a;
        public Container b;
    }

    private static native void safepoint();

    public static void test1Snippet(Container main) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        barrierIndex = 0;
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        safepoint();
        barrierIndex = 2;
        main.b = temp2;
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test1() {
        test("test1Snippet", 2, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test2() {
        test("test1Snippet", 2, new int[]{2});
    }

    public static void test2Snippet(Container main) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        barrierIndex = 0;
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        barrierIndex = 2;
        main.b = temp2;
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test3() {
        test("test2Snippet", 2, new int[]{1});
    }

    @Test
    public void test4() {
        test("test2Snippet", 2, new int[]{2});
    }

    public static void test3Snippet(Container main, boolean test) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        barrierIndex = 0;
        safepoint();
        for (int i = 0; i < 10; i++) {
            if (test) {
                barrierIndex = 1;
                main.a = temp1;
                barrierIndex = 2;
                main.b = temp2;
            } else {
                barrierIndex = 3;
                main.a = temp1;
                barrierIndex = 4;
                main.b = temp2;
            }
        }
    }

    @Test(expected = AssertionError.class)
    public void test5() {
        test("test3Snippet", 4, new int[]{1, 2});
    }

    @Test(expected = AssertionError.class)
    public void test6() {
        test("test3Snippet", 4, new int[]{3, 4});
    }

    @Test(expected = AssertionError.class)
    public void test7() {
        test("test3Snippet", 4, new int[]{1});
    }

    @Test
    public void test8() {
        test("test3Snippet", 4, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test9() {
        test("test3Snippet", 4, new int[]{3});
    }

    @Test
    public void test10() {
        test("test3Snippet", 4, new int[]{4});
    }

    public static void test4Snippet(Container main, boolean test) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        for (int i = 0; i < 10; i++) {
            if (test) {
                barrierIndex = 2;
                main.a = temp1;
                barrierIndex = 3;
                main.b = temp2;
            } else {
                barrierIndex = 4;
                main.a = temp2;
                barrierIndex = 5;
                main.b = temp1;
            }
        }
    }

    @Test(expected = AssertionError.class)
    public void test11() {
        test("test4Snippet", 5, new int[]{2, 3});
    }

    @Test(expected = AssertionError.class)
    public void test12() {
        test("test4Snippet", 5, new int[]{4, 5});
    }

    @Test(expected = AssertionError.class)
    public void test13() {
        test("test4Snippet", 5, new int[]{1});
    }

    public static void test5Snippet(Container main) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        if (main.a == main.b) {
            barrierIndex = 2;
            main.a = temp1;
            barrierIndex = 3;
            main.b = temp2;
        } else {
            barrierIndex = 4;
            main.a = temp2;
            barrierIndex = 5;
            main.b = temp1;
        }
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test14() {
        test("test5Snippet", 5, new int[]{1});
    }

    @Test
    public void test15() {
        test("test5Snippet", 5, new int[]{2});
    }

    @Test
    public void test16() {
        test("test5Snippet", 5, new int[]{4});
    }

    @Test
    public void test17() {
        test("test5Snippet", 5, new int[]{3});
    }

    @Test
    public void test18() {
        test("test5Snippet", 5, new int[]{5});
    }

    @Test
    public void test19() {
        test("test5Snippet", 5, new int[]{2, 3});
    }

    @Test
    public void test20() {
        test("test5Snippet", 5, new int[]{4, 5});
    }

    public static void test6Snippet(Container main, boolean test) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        if (test) {
            barrierIndex = 2;
            main.a = temp1;
            barrierIndex = 3;
            main.b = temp1.a.a;
        } else {
            barrierIndex = 4;
            main.a = temp2;
            barrierIndex = 5;
            main.b = temp2.a.a;
        }
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test21() {
        test("test6Snippet", 5, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test22() {
        test("test6Snippet", 5, new int[]{1, 2});
    }

    @Test
    public void test23() {
        test("test6Snippet", 5, new int[]{3});
    }

    @Test
    public void test24() {
        test("test6Snippet", 5, new int[]{4});
    }

    public static void test7Snippet(Container main, boolean test) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        if (test) {
            barrierIndex = 2;
            main.a = temp1;
        }
        barrierIndex = 3;
        main.b = temp2;
        safepoint();
    }

    @Test
    public void test25() {
        test("test7Snippet", 3, new int[]{2});
    }

    @Test
    public void test26() {
        test("test7Snippet", 3, new int[]{3});
    }

    @Test
    public void test27() {
        test("test7Snippet", 3, new int[]{2, 3});
    }

    @Test(expected = AssertionError.class)
    public void test28() {
        test("test7Snippet", 3, new int[]{1});
    }

    public static void test8Snippet(Container main, boolean test) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        if (test) {
            barrierIndex = 1;
            main.a = temp1;
        }
        barrierIndex = 2;
        main.b = temp2;
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test29() {
        test("test8Snippet", 2, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test30() {
        test("test8Snippet", 2, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test31() {
        test("test8Snippet", 2, new int[]{1, 2});
    }

    public static void test9Snippet(Container main1, Container main2, boolean test) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        if (test) {
            barrierIndex = 1;
            main1.a = temp1;
        } else {
            barrierIndex = 2;
            main2.a = temp1;
        }
        barrierIndex = 3;
        main1.b = temp2;
        barrierIndex = 4;
        main2.b = temp2;
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test32() {
        test("test9Snippet", 4, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test33() {
        test("test9Snippet", 4, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test34() {
        test("test9Snippet", 4, new int[]{3});
    }

    @Test(expected = AssertionError.class)
    public void test35() {
        test("test9Snippet", 4, new int[]{4});
    }

    @Test(expected = AssertionError.class)
    public void test36() {
        test("test9Snippet", 4, new int[]{1, 2});
    }

    @Test(expected = AssertionError.class)
    public void test37() {
        test("test9Snippet", 4, new int[]{3, 4});
    }

    public static void test10Snippet(Container main1, Container main2, boolean test) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        if (test) {
            barrierIndex = 1;
            main1.a = temp1;
            barrierIndex = 2;
            main2.a = temp2;
        } else {
            barrierIndex = 3;
            main2.a = temp1;
        }
        barrierIndex = 4;
        main1.b = temp2;
        barrierIndex = 5;
        main2.b = temp2;
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test38() {
        test("test10Snippet", 5, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test39() {
        test("test10Snippet", 5, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test40() {
        test("test10Snippet", 5, new int[]{3});
    }

    @Test(expected = AssertionError.class)
    public void test41() {
        test("test10Snippet", 5, new int[]{4});
    }

    @Test
    public void test42() {
        test("test10Snippet", 5, new int[]{5});
    }

    @Test(expected = AssertionError.class)
    public void test43() {
        test("test10Snippet", 5, new int[]{1, 2});
    }

    @Test(expected = AssertionError.class)
    public void test44() {
        test("test10Snippet", 5, new int[]{1, 2, 3});
    }

    @Test(expected = AssertionError.class)
    public void test45() {
        test("test10Snippet", 5, new int[]{3, 4});
    }

    public static void test11Snippet(Container main1, Container main2, Container main3, boolean test) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        if (test) {
            barrierIndex = 1;
            main1.a = temp1;
            barrierIndex = 2;
            main3.a = temp1;
            if (!test) {
                barrierIndex = 3;
                main2.a = temp2;
            } else {
                barrierIndex = 4;
                main1.a = temp2;
                barrierIndex = 5;
                main3.a = temp2;
            }
        } else {
            barrierIndex = 6;
            main1.b = temp2;
            for (int i = 0; i < 10; i++) {
                barrierIndex = 7;
                main3.a = temp1;
            }
            barrierIndex = 8;
            main3.b = temp2;
        }
        barrierIndex = 9;
        main1.b = temp2;
        barrierIndex = 10;
        main2.b = temp2;
        barrierIndex = 11;
        main3.b = temp2;
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test46() {
        test("test11Snippet", 11, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test47() {
        test("test11Snippet", 11, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test48() {
        test("test11Snippet", 11, new int[]{3});
    }

    @Test(expected = AssertionError.class)
    public void test49() {
        test("test11Snippet", 11, new int[]{6});
    }

    @Test(expected = AssertionError.class)
    public void test50() {
        test("test11Snippet", 11, new int[]{7});
    }

    @Test(expected = AssertionError.class)
    public void test51() {
        test("test11Snippet", 11, new int[]{8});
    }

    @Test(expected = AssertionError.class)
    public void test52() {
        test("test11Snippet", 11, new int[]{9});
    }

    @Test(expected = AssertionError.class)
    public void test53() {
        test("test11Snippet", 11, new int[]{10});
    }

    @Test
    public void test54() {
        test("test11Snippet", 11, new int[]{4});
    }

    @Test
    public void test55() {
        test("test11Snippet", 11, new int[]{5});
    }

    @Test
    public void test56() {
        test("test11Snippet", 11, new int[]{11});
    }

    public static void test12Snippet(Container main, Container main1, boolean test) {
        Container temp1 = new Container();
        Container temp2 = new Container();
        barrierIndex = 0;
        safepoint();
        barrierIndex = 7;
        main1.a = temp1;
        for (int i = 0; i < 10; i++) {
            if (test) {
                barrierIndex = 1;
                main.a = temp1;
                barrierIndex = 2;
                main.b = temp2;
            } else {
                barrierIndex = 3;
                main.a = temp1;
                barrierIndex = 4;
                main.b = temp2;
            }
        }
        barrierIndex = 5;
        main.a = temp1;
        barrierIndex = 6;
        main.b = temp1;
        barrierIndex = 8;
        main1.b = temp1;
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test57() {
        test("test12Snippet", 8, new int[]{5});
    }

    @Test
    public void test58() {
        test("test12Snippet", 8, new int[]{6});
    }

    @Test(expected = AssertionError.class)
    public void test59() {
        test("test12Snippet", 8, new int[]{7});
    }

    @Test(expected = AssertionError.class)
    public void test60() {
        test("test12Snippet", 8, new int[]{8});
    }

    public static void test13Snippet(Object[] a, Object[] b) {
        System.arraycopy(a, 0, b, 0, a.length);
    }

    private interface GraphPredicate {
        int apply(StructuredGraph graph);
    }

    private void test(final String snippet, final int expectedBarriers, final int... removedBarrierIndices) {
        GraphPredicate noCheck = noArg -> expectedBarriers;
        testPredicate(snippet, noCheck, removedBarrierIndices);
    }

    @SuppressWarnings("try")
    private void testPredicate(final String snippet, final GraphPredicate expectedBarriers, final int... removedBarrierIndices) {
        DebugContext debug = getDebugContext();
        try (DebugCloseable d = debug.disableIntercept(); DebugContext.Scope s = debug.scope("WriteBarrierVerificationTest", new DebugDumpScope(snippet))) {
            final StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES, debug);
            HighTierContext highTierContext = getDefaultHighTierContext();
            createInliningPhase().apply(graph, highTierContext);

            MidTierContext midTierContext = new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, graph.getProfilingInfo());

            new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, highTierContext);
            new GuardLoweringPhase().apply(graph, midTierContext);
            new LoopSafepointInsertionPhase().apply(graph);
            new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, highTierContext);

            new WriteBarrierAdditionPhase(config).apply(graph);

            int barriers = 0;
            // First, the total number of expected barriers is checked.
            if (config.useG1GC) {
                barriers = graph.getNodes().filter(G1PreWriteBarrier.class).count() + graph.getNodes().filter(G1PostWriteBarrier.class).count() +
                                graph.getNodes().filter(G1ArrayRangePreWriteBarrier.class).count() + graph.getNodes().filter(G1ArrayRangePostWriteBarrier.class).count();
                Assert.assertTrue(expectedBarriers.apply(graph) * 2 == barriers);
            } else {
                barriers = graph.getNodes().filter(SerialWriteBarrier.class).count() + graph.getNodes().filter(SerialArrayRangeWriteBarrier.class).count();
                Assert.assertTrue(expectedBarriers.apply(graph) == barriers);
            }
            ResolvedJavaField barrierIndexField = getMetaAccess().lookupJavaField(WriteBarrierVerificationTest.class.getDeclaredField("barrierIndex"));
            LocationIdentity barrierIdentity = new FieldLocationIdentity(barrierIndexField);
            // Iterate over all write nodes and remove barriers according to input indices.
            NodeIteratorClosure<Boolean> closure = new NodeIteratorClosure<Boolean>() {

                @Override
                protected Boolean processNode(FixedNode node, Boolean currentState) {
                    if (node instanceof WriteNode) {
                        WriteNode write = (WriteNode) node;
                        LocationIdentity obj = write.getLocationIdentity();
                        if (obj.equals(barrierIdentity)) {
                            /*
                             * A "barrierIndex" variable was found and is checked against the input
                             * barrier array.
                             */
                            if (eliminateBarrier(write.value().asJavaConstant().asInt(), removedBarrierIndices)) {
                                return true;
                            }
                        }
                    } else if (node instanceof SerialWriteBarrier || node instanceof G1PostWriteBarrier) {
                        // Remove flagged write barriers.
                        if (currentState) {
                            graph.removeFixed(((FixedWithNextNode) node));
                            return false;
                        }
                    }
                    return currentState;
                }

                private boolean eliminateBarrier(int index, int[] map) {
                    for (int i = 0; i < map.length; i++) {
                        if (map[i] == index) {
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                protected EconomicMap<LoopExitNode, Boolean> processLoop(LoopBeginNode loop, Boolean initialState) {
                    return ReentrantNodeIterator.processLoop(this, loop, initialState).exitStates;
                }

                @Override
                protected Boolean merge(AbstractMergeNode merge, List<Boolean> states) {
                    return false;
                }

                @Override
                protected Boolean afterSplit(AbstractBeginNode node, Boolean oldState) {
                    return false;
                }
            };

            try (Scope disabled = debug.disable()) {
                ReentrantNodeIterator.apply(closure, graph.start(), false);
                new WriteBarrierVerificationPhase(config).apply(graph);
            } catch (AssertionError error) {
                /*
                 * Catch assertion, test for expected one and re-throw in order to validate unit
                 * test.
                 */
                Assert.assertTrue(error.getMessage().contains("Write barrier must be present"));
                throw error;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
