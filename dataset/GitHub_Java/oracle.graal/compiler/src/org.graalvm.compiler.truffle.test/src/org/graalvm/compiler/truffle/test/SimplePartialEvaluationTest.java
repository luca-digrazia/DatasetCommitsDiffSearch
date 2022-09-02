/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.GraalBailoutException;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.AddTestNode;
import org.graalvm.compiler.truffle.test.nodes.BlockTestNode;
import org.graalvm.compiler.truffle.test.nodes.ConstantTestNode;
import org.graalvm.compiler.truffle.test.nodes.ExplodeLoopUntilReturnNode;
import org.graalvm.compiler.truffle.test.nodes.ExplodeLoopUntilReturnWithThrowNode;
import org.graalvm.compiler.truffle.test.nodes.InliningNullCheckNode1;
import org.graalvm.compiler.truffle.test.nodes.InliningNullCheckNode2;
import org.graalvm.compiler.truffle.test.nodes.LambdaTestNode;
import org.graalvm.compiler.truffle.test.nodes.LoadLocalTestNode;
import org.graalvm.compiler.truffle.test.nodes.LoopTestNode;
import org.graalvm.compiler.truffle.test.nodes.NeverPartOfCompilationTestNode;
import org.graalvm.compiler.truffle.test.nodes.ObjectEqualsNode;
import org.graalvm.compiler.truffle.test.nodes.ObjectHashCodeNode;
import org.graalvm.compiler.truffle.test.nodes.PartialIntrinsicNode;
import org.graalvm.compiler.truffle.test.nodes.RecursionTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.graalvm.compiler.truffle.test.nodes.StoreLocalTestNode;
import org.graalvm.compiler.truffle.test.nodes.StringEqualsNode;
import org.graalvm.compiler.truffle.test.nodes.StringHashCodeFinalNode;
import org.graalvm.compiler.truffle.test.nodes.StringHashCodeNonFinalNode;
import org.graalvm.compiler.truffle.test.nodes.SynchronizedExceptionMergeNode;
import org.graalvm.compiler.truffle.test.nodes.explosion.LoopExplosionPhiNode;
import org.graalvm.compiler.truffle.test.nodes.explosion.NestedExplodedLoopTestNode;
import org.graalvm.compiler.truffle.test.nodes.explosion.TwoMergesExplodedLoopTestNode;
import org.graalvm.compiler.truffle.test.nodes.explosion.UnrollingTestNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;

public class SimplePartialEvaluationTest extends PartialEvaluationTest {

    private static TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope immediateCompilationScope;

    @BeforeClass
    public static void setup() {
        immediateCompilationScope = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleCompileImmediately, false);
    }

    @AfterClass
    public static void tearDown() {
        immediateCompilationScope.close();
    }

    public static Object constant42() {
        return 42;
    }

    @Test
    public void constantValue() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ConstantTestNode(42);
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "constantValue", result));
    }

    @Test
    public void addConstants() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new AddTestNode(new ConstantTestNode(40), new ConstantTestNode(2));
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "addConstants", result));
    }

    @Test
    @SuppressWarnings("try")
    public void neverPartOfCompilationTest() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode firstTree = new NeverPartOfCompilationTestNode(new ConstantTestNode(1), 2);
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "neverPartOfCompilationTest", firstTree));

        AbstractTestNode secondTree = new NeverPartOfCompilationTestNode(new ConstantTestNode(1), 1);
        try (PreventDumping noDump = new PreventDumping()) {
            assertPartialEvalEquals("constant42", new RootTestNode(fd, "neverPartOfCompilationTest", secondTree));
            Assert.fail("Expected verification error!");
        } catch (GraalBailoutException t) {
            // Expected verification error occurred.
            StackTraceElement[] trace = t.getStackTrace();
            if (truffleCompiler.getPartialEvaluator().getConfigForParsing().trackNodeSourcePosition() || GraalOptions.TrackNodeSourcePosition.getValue(getInitialOptions())) {
                assertStack(trace[0], "com.oracle.truffle.api.CompilerAsserts", "neverPartOfCompilation", "CompilerAsserts.java");
                assertStack(trace[1], "org.graalvm.compiler.truffle.test.nodes.NeverPartOfCompilationTestNode", "execute", "NeverPartOfCompilationTestNode.java");
                assertStack(trace[2], "org.graalvm.compiler.truffle.test.nodes.RootTestNode", "execute", "RootTestNode.java");
            } else {
                assertStack(trace[0], "org.graalvm.compiler.truffle.test.nodes.NeverPartOfCompilationTestNode", "execute", "NeverPartOfCompilationTestNode.java");
                assertStack(trace[1], "org.graalvm.compiler.truffle.test.nodes.RootTestNode", "execute", "RootTestNode.java");
            }
        }
    }

    private static void assertStack(StackTraceElement stack, String className, String methodName, String fileName) {
        Assert.assertEquals(className, stack.getClassName());
        Assert.assertEquals(methodName, stack.getMethodName());
        Assert.assertEquals(fileName, stack.getFileName());
    }

    @Test
    public void nestedLoopExplosion() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new AddTestNode(new NestedExplodedLoopTestNode(5), new ConstantTestNode(17));
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "nestedLoopExplosion", result));
    }

    @Test
    public void twoMergesLoopExplosion() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new AddTestNode(new TwoMergesExplodedLoopTestNode(5), new ConstantTestNode(37));
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "twoMergesLoopExplosion", result));
    }

    @Test
    public void sequenceConstants() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new BlockTestNode(new AbstractTestNode[]{new ConstantTestNode(40), new ConstantTestNode(42)});
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "sequenceConstants", result));
    }

    @Test
    public void localVariable() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new BlockTestNode(new AbstractTestNode[]{new StoreLocalTestNode("x", fd, new ConstantTestNode(42)), new LoadLocalTestNode("x", fd)});
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "localVariable", result));
    }

    @Test
    public void unrollUntilReturnNoLoop() {
        FrameDescriptor fd = new FrameDescriptor();
        UnrollingTestNode t = new UnrollingTestNode(5);
        AbstractTestNode result = new AddTestNode(t.new FullUnrollUntilReturnNoLoop(), new ConstantTestNode(37));
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "unrollUntilReturnNoLoop", result));
    }

    @Test
    public void unrollUntilReturnConsecutiveLoops() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 5;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new FullUnrollUntilReturnConsecutiveLoops(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "consecutiveLoops", result), new Object[]{});
        StructuredGraph peResult = lastCompiledGraph;

        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, loopIterations * 2, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.OUTSIDE_LOOP_MARKER, loopIterations * 2, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.OUTSIDE_LOOP_MARKER));
        // +1: the original exit
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, 1, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    public void unrollUntilReturnNestedLoops() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 2;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new FullUnrollUntilReturnNestedLoops(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "nestedLoopExplosion", result), new Object[]{});
        StructuredGraph peResult = lastCompiledGraph;

        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, loopIterations * loopIterations, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.OUTSIDE_LOOP_MARKER, loopIterations * loopIterations, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.OUTSIDE_LOOP_MARKER));
        // +1: the original exit
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, 1, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    public void unrollUntilReturnNestedLoopsContinueOuter01() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 2;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new FullUnrollUntilReturnNestedLoopsContinueOuter01(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "nestedLoopExplosion", result), new Object[]{});
        StructuredGraph peResult = lastCompiledGraph;

        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, 4, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, 1/* forced explosion */, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    public void unrollUntilReturnNestedLoopsContinueOuter02() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 2;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new FullUnrollUntilReturnNestedLoopsContinueOuter02(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "nestedLoopExplosion", result), new Object[]{});
        StructuredGraph peResult = lastCompiledGraph;

        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, 8, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, 1, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    public void unrollUntilReturnNestedLoopsContinueOuter03() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 2;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new FullUnrollUntilReturnNestedLoopsContinueOuter03(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "nestedLoopExplosion", result), new Object[]{});
        StructuredGraph peResult = lastCompiledGraph;

        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, 4, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, 1, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    public void unrollUntilReturnNestedLoopsContinueOuter04() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 2;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new FullUnrollUntilReturnNestedLoopsContinueOuter04(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "nestedLoopExplosion", result), new Object[]{});
        StructuredGraph peResult = lastCompiledGraph;

        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, 4, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, 1, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    public void unrollUntilReturnNestedLoopsContinueOuter05() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 2;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new FullUnrollUntilReturnNestedLoops(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "nestedLoopExplosion", result), new Object[]{});
        StructuredGraph peResult = lastCompiledGraph;

        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, 4, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, 1, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    public void unrollUntilReturn() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 5;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new FullUnrollUntilReturnExample(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "unrollUntilReturn", result), new Object[]{});
        StructuredGraph peResult = lastCompiledGraph;
        //@formatter:off
        /*
         * 5 iterations with 1 end and 2 exits (one early return)
         *
         * iteration 1
         *  exit1
         *  iteration 2
         *      exit1
         *      iteration 3
         *          exit1
         *          iteration 4
         *              exit1
         *              iteration 5
         *  exit2
         *
         */
        //@formatter:on
        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, loopIterations, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.OUTSIDE_LOOP_MARKER, loopIterations, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.OUTSIDE_LOOP_MARKER));
        // +1: the original exit
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, 1, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    public void complexUnrollFullUnroll() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 5;
        UnrollingTestNode test = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(test.new Unroll0(), new ConstantTestNode(37));
        try {
            compileHelper("Test", new RootTestNode(fd, "nestedLoopExplosion", result), new Object[]{});
            Assert.fail("Expected partial evaluation error!");
        } catch (Throwable t) {
            if (t.getCause() instanceof PermanentBailoutException && t.getMessage().contains("Partial evaluation did not reduce value to a constant, is a regular compiler node")) {
                return;
            }
            Assert.fail("Unrolling sink paths without UNTIL_RETURN should not be supported.");
        }
    }

    @Test
    public void complexUnrollFullUnrollUntilReturn() {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 5;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new Unroll01(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "nestedLoopExplosion", result), new Object[]{});
        StructuredGraph peResult = lastCompiledGraph;
        //@formatter:off
        /*
         * 5 iterations with 2 ends and 3 exits
         *
         * iteration 1
         *  end 1 & 2
         *      iteration 2
         *          end 1 & 2
         *              iteration 3
         *                  end 1 & end 2
         *                      iteration 4
         *                          end 1 & 2
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                          exit 1
         *                          exit 2
         *                  exit 1
         *                  exit 2
         *          exit 1
         *          exit 2
         *  exit 1
         *  exit 2
         *  exit 2 [original, before unrolling, one]
         */
        //@formatter:on
        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, loopIterations, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.OUTSIDE_LOOP_MARKER, loopIterations, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.OUTSIDE_LOOP_MARKER));
        // +1: the original exit
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, loopIterations + 1, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    @SuppressWarnings("try")
    public void complexUnrollFullExplodeUntilReturn() throws Exception {
        FrameDescriptor fd = new FrameDescriptor();
        final int loopIterations = 5;
        UnrollingTestNode t = new UnrollingTestNode(loopIterations);
        AbstractTestNode result = new AddTestNode(t.new Unroll02(), new ConstantTestNode(37));
        compileHelper("Test", new RootTestNode(fd, "nestedLoopExplosion", result), new Object[]{}, true);
        StructuredGraph peResult = lastCompiledGraph;

        //@formatter:off
        /*
         * 5 iterations with 2 ends and 3 exits
         *
         * iteration 1
         *  end 1
         *      iteration 2
         *          end 1
         *              iteration 3
         *                  end 1
         *                      iteration 4
         *                          end 1
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                          end 2
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                  end 2
         *                      iteration 4
         *                          end 1
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                          end 2
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *          end 2
         *              iteration 3
         *                  end 1
         *                      iteration 4
         *                          end 1
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                          end 2
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                  end 2
         *                      iteration 4
         *                          end 1
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                          end 2
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *  end 2
         *      iteration 2
         *          end 1
         *              iteration 3
         *                  end 1
         *                      iteration 4
         *                          end 1
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                          end 2
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                  end 2
         *                      iteration 4
         *                          end 1
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                          end 2
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *          end 2
         *              iteration 3
         *                  end 1
         *                      iteration 4
         *                          end 1
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                          end 2
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                  end 2
         *                      iteration 4
         *                          end 1
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         *                          end 2
         *                              iteration 5
         *                                  exit 1
         *                                  exit 2
         */
        //@formatter:on
        Assert.assertEquals(UnrollingTestNode.INSIDE_LOOP_MARKER, 31, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.INSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.OUTSIDE_LOOP_MARKER, 31, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.OUTSIDE_LOOP_MARKER));
        Assert.assertEquals(UnrollingTestNode.AFTER_LOOP_MARKER, 31, UnrollingTestNode.countBlackholeNodes(peResult, UnrollingTestNode.AFTER_LOOP_MARKER));
    }

    @Test
    public void longSequenceConstants() {
        FrameDescriptor fd = new FrameDescriptor();
        int length = 40;
        AbstractTestNode[] children = new AbstractTestNode[length];
        for (int i = 0; i < children.length; ++i) {
            children[i] = new ConstantTestNode(42);
        }

        AbstractTestNode result = new BlockTestNode(children);
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "longSequenceConstants", result));
    }

    @Test
    public void longAddConstants() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ConstantTestNode(2);
        for (int i = 0; i < 20; ++i) {
            result = new AddTestNode(result, new ConstantTestNode(2));
        }
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "longAddConstants", result));
    }

    @Test
    public void mixLocalAndAdd() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new BlockTestNode(new AbstractTestNode[]{new StoreLocalTestNode("x", fd, new ConstantTestNode(40)),
                        new StoreLocalTestNode("x", fd, new AddTestNode(new LoadLocalTestNode("x", fd), new ConstantTestNode(2))), new LoadLocalTestNode("x", fd)});
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "mixLocalAndAdd", result));
    }

    @Test
    public void loop() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new BlockTestNode(new AbstractTestNode[]{new StoreLocalTestNode("x", fd, new ConstantTestNode(0)),
                        new LoopTestNode(7, new StoreLocalTestNode("x", fd, new AddTestNode(new LoadLocalTestNode("x", fd), new ConstantTestNode(6))))});
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "loop", result));
    }

    @Test
    public void longLoop() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new BlockTestNode(new AbstractTestNode[]{new StoreLocalTestNode("x", fd, new ConstantTestNode(0)),
                        new LoopTestNode(42, new StoreLocalTestNode("x", fd, new AddTestNode(new LoadLocalTestNode("x", fd), new ConstantTestNode(1))))});
        RootTestNode rootNode = new RootTestNode(fd, "loop", result);
        assertPartialEvalNoInvokes(rootNode);
        assertPartialEvalEquals("constant42", rootNode);
    }

    @Test
    public void lambda() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new LambdaTestNode();
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "constantValue", result));
    }

    @Test
    public void allowedRecursion() {
        /* Recursion depth just below the threshold that reports it as too deep recursion. */
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new RecursionTestNode(TruffleCompilerOptions.getValue(PEGraphDecoder.Options.InliningDepthError) - 5);
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "allowedRecursion", result));
    }

    @Test(expected = BailoutException.class)
    public void tooDeepRecursion() {
        /* Recursion depth just above the threshold that reports it as too deep recursion. */
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new RecursionTestNode(TruffleCompilerOptions.getValue(PEGraphDecoder.Options.InliningDepthError));
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "tooDeepRecursion", result));
    }

    @Test
    public void intrinsicStatic() {
        /*
         * The intrinsic for String.equals() is inlined early during bytecode parsing, because we
         * call equals() on a value that has the static type String.
         */
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new StringEqualsNode("abc", "abf");
        RootNode rootNode = new RootTestNode(fd, "intrinsicStatic", result);
        OptimizedCallTarget compilable = compileHelper("intrinsicStatic", rootNode, new Object[0]);

        Assert.assertEquals(42, compilable.call(new Object[0]));
    }

    @Test
    public void intrinsicVirtual() {
        /*
         * The intrinsic for String.equals() is inlined late during Truffle partial evaluation,
         * because we call equals() on a value that has the static type Object, but during partial
         * evaluation the more precise type String is known.
         */
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ObjectEqualsNode("abc", "abf");
        RootNode rootNode = new RootTestNode(fd, "intrinsicVirtual", result);
        OptimizedCallTarget compilable = compileHelper("intrinsicVirtual", rootNode, new Object[0]);

        Assert.assertEquals(42, compilable.call(new Object[0]));
    }

    @Test
    public void intrinsicHashCode() {
        /*
         * The intrinsic for Object.hashCode() is inlined late during Truffle partial evaluation,
         * because we call hashCode() on a value whose exact type Object is only known during
         * partial evaluation.
         */
        FrameDescriptor fd = new FrameDescriptor();
        Object testObject = new Object();
        AbstractTestNode result = new ObjectHashCodeNode(testObject);
        RootNode rootNode = new RootTestNode(fd, "intrinsicHashCode", result);
        OptimizedCallTarget compilable = compileHelper("intrinsicHashCode", rootNode, new Object[0]);

        int actual = (Integer) compilable.call(new Object[0]);
        int expected = testObject.hashCode();
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void synchronizedExceptionMerge() {
        /*
         * Multiple non-inlineable methods with exception edges called from a synchronized method
         * lead to a complicated Graal graph that involves the BytecodeFrame.UNWIND_BCI. This test
         * checks that partial evaluation handles that case correctly.
         */
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new SynchronizedExceptionMergeNode();
        RootNode rootNode = new RootTestNode(fd, "synchronizedExceptionMerge", result);
        OptimizedCallTarget compilable = compileHelper("synchronizedExceptionMerge", rootNode, new Object[0]);

        Assert.assertEquals(42, compilable.call(new Object[0]));
    }

    @Test
    public void explodeLoopUntilReturn() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ExplodeLoopUntilReturnNode();
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "explodeLoopUntilReturn", result));
    }

    @Test
    public void explodeLoopUntilReturnWithThrow() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ExplodeLoopUntilReturnWithThrowNode();
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "explodeLoopUntilReturnWithThrow", result));
    }

    @Test
    public void intrinsicStringHashCodeFinal() {
        /* The intrinsic for String.hashcode() triggers on constant strings. */
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new StringHashCodeFinalNode("*");
        /* The hash code of "*" is 42. */
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "intrinsicStringHashCodeFinal", result));
    }

    @Test
    public void intrinsicStringHashCodeNonFinal() {
        /*
         * The intrinsic for String.hashcode() does not trigger on non-constant strings, so the
         * method String.hashCode() must be inlined during partial evaluation (so there must not be
         * an invoke after partial evaluation).
         */
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new StringHashCodeNonFinalNode("*");
        assertPartialEvalNoInvokes(new RootTestNode(fd, "intrinsicStringHashCodeNonFinal", result));
    }

    @Test
    public void inliningNullCheck1() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new InliningNullCheckNode1();
        RootNode rootNode = new RootTestNode(fd, "inliningNullCheck1", result);
        OptimizedCallTarget compilable = compileHelper("inliningNullCheck1", rootNode, new Object[0]);

        Assert.assertEquals(42, compilable.call(new Object[0]));
    }

    @Test
    public void inliningNullCheck2() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new InliningNullCheckNode2();
        RootNode rootNode = new RootTestNode(fd, "inliningNullCheck2", result);
        OptimizedCallTarget compilable = compileHelper("inliningNullCheck2", rootNode, new Object[0]);

        Assert.assertEquals(42, compilable.call(new Object[0]));
    }

    @Test
    public void loopExplosionPhi() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new LoopExplosionPhiNode();
        RootNode rootNode = new RootTestNode(fd, "loopExplosionPhi", result);
        OptimizedCallTarget compilable = compileHelper("loopExplosionPhi", rootNode, new Object[0]);

        Assert.assertEquals(1, compilable.call(new Object[0]));
    }

    @Test
    public void partialIntrinsic() {
        /*
         * Object.notifyAll() is a partial intrinsic on JDK 11, i.e., the intrinsic calls the
         * original implementation. Test that the call to the original implementation is not
         * recursively inlined as an intrinsic again.
         */
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new PartialIntrinsicNode();
        RootNode rootNode = new RootTestNode(fd, "partialIntrinsic", result);
        OptimizedCallTarget compilable = compileHelper("partialIntrinsic", rootNode, new Object[0]);

        Assert.assertEquals(42, compilable.call(new Object[0]));
    }
}
