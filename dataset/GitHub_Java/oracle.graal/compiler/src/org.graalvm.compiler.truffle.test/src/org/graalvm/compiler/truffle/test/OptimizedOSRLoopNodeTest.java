/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationThreshold;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInvalidationReprofileCount;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleMinInvokeThreshold;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleOSRCompilationThreshold;
import static org.junit.Assert.assertSame;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedOSRLoopNode;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

@RunWith(Theories.class)
public class OptimizedOSRLoopNodeTest extends TestWithSynchronousCompiling {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

    private static final int OSR_THRESHOLD = TruffleCompilerOptions.getValue(TruffleOSRCompilationThreshold);
    private static final int OSR_INVALIDATION_REPROFILE = TruffleCompilerOptions.getValue(TruffleInvalidationReprofileCount);

    @DataPoint public static final OSRLoopFactory CONFIGURED = (repeating, readFrameSlots,
                    writtenFrameSlots) -> OptimizedOSRLoopNode.createOSRLoop(repeating, OSR_THRESHOLD,
                    OSR_INVALIDATION_REPROFILE, readFrameSlots, writtenFrameSlots);

    @DataPoint public static final OSRLoopFactory DEFAULT = (repeating, readFrameSlots,
                    writtenFrameSlots) -> (OptimizedOSRLoopNode) OptimizedOSRLoopNode.create(repeating);

    @BeforeClass
    public static void doBefore() {
        // ensure that all classes are properly loaded
        TestRootNode rootNode = new TestRootNode(DEFAULT, new TestRepeatingNode());
        CallTarget target = runtime.createCallTarget(rootNode);
        target.call(1);
        rootNode = new TestRootNode(CONFIGURED, new TestRepeatingNode());
        target = runtime.createCallTarget(rootNode);
        target.call(1);
    }

    /*
     * Test that we achieve compilation on the first execution with a loop invoked
     */
    @Theory
    public void testOSRSingleInvocation(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode());
        CallTarget target = runtime.createCallTarget(rootNode);
        target.call(OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());
        target.call(2);
        assertCompiled(rootNode.getOSRTarget());
        Assert.assertTrue(rootNode.wasRepeatingCalledCompiled());
    }

    @Theory
    public void testOSRAndRewriteDoesNotStifleTargetCompilation(OSRLoopFactory factory) {
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope s = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleCompilationThreshold, 3)) {
            TestRootNodeWithReplacement rootNode = new TestRootNodeWithReplacement(factory, new TestRepeatingNode());
            OptimizedCallTarget target = new OptimizedCallTarget(null, rootNode);
            target.call(OSR_THRESHOLD + 1);
            assertCompiled(rootNode.getOSRTarget());
            assertNotCompiled(target);
            target.nodeReplaced(rootNode.toReplace, new TestRepeatingNode(), "test");
            for (int i = 0; i < TruffleCompilerOptions.getValue(TruffleCompilationThreshold) - 1; i++) {
                target.call(2);
            }
            assertCompiled(rootNode.getOSRTarget());
            assertCompiled(target);
        }
    }

    /*
     * Test that calling CompilerDirectives.transferToInterpreter does not invalidate the target.
     */
    @Test
    public void testTransferToInterpreter() {
        OSRLoopFactory factory = CONFIGURED;

        class TransferToInterpreterTestRepeatingNode extends TestRepeatingNode {
            @Override
            public boolean executeRepeating(VirtualFrame frame) {
                try {
                    if (CompilerDirectives.inCompiledCode()) {
                        CompilerDirectives.transferToInterpreter();
                    }
                    int counter = frame.getInt(param1);
                    frame.setInt(param1, counter - 1);
                    return counter != 0;
                } catch (FrameSlotTypeException e) {
                    return false;
                }
            }
        }

        TestRootNode rootNode = new TestRootNode(factory, new TransferToInterpreterTestRepeatingNode());
        CallTarget target = runtime.createCallTarget(rootNode);
        target.call(OSR_THRESHOLD + 1);
        try {
            // Invalidation is asynchronous.
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Assert.assertNotNull(rootNode.getOSRTarget());
    }

    /*
     * Test OSR is not triggered just below the osr threshold.
     */
    @Theory
    public void testNonOSR(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode());
        runtime.createCallTarget(rootNode).call(OSR_THRESHOLD);
        assertNotCompiled(rootNode.getOSRTarget());
    }

    /*
     * Test frame slot changes in the loop cause deoptimization and reoptimization.
     */
    @Test
    @Ignore("Needs mayor revision - GR-2515")
    public void testOSRFrameSlotChangeDuringOSR() {
        OSRLoopFactory factory = CONFIGURED;
        TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode() {

            @Override
            public boolean executeRepeating(VirtualFrame frame) {
                boolean next = super.executeRepeating(frame);
                if (!next) {
                    // might trigger a deopt
                    frame.setDouble(param2, 42.0);
                }
                return next;
            }

        }) {

            @Override
            public Object execute(VirtualFrame frame) {
                Object result = super.execute(frame);
                try {
                    Assert.assertEquals(42.0d, frame.getDouble(param2), 0.01);
                } catch (FrameSlotTypeException e) {
                    Assert.fail();
                }
                return result;
            }

        };

        executeNoCallTarget(rootNode, OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());
        executeNoCallTarget(rootNode, 1);
        assertNotCompiled(rootNode.getOSRTarget()); // now deoptimized
        executeNoCallTarget(rootNode, OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());
        executeNoCallTarget(rootNode, 1); // maybe deoptimizing
        executeNoCallTarget(rootNode, OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());
        executeNoCallTarget(rootNode, 1); // not deoptimizing
        assertCompiled(rootNode.getOSRTarget());
        Assert.assertTrue(rootNode.wasRepeatingCalledCompiled());
    }

    /*
     * Test that if osr compilation is forced without any execution we do not deoptimize on first
     * execution.
     */
    @Theory
    public void testNoInvalidationWithoutFirstExecution(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode());
        RootCallTarget target = runtime.createCallTarget(rootNode);
        rootNode.forceOSR();
        assertCompiled(rootNode.getOSRTarget());
        target.call(1); // should not invalidate OSR
        assertCompiled(rootNode.getOSRTarget());
        Assert.assertTrue(rootNode.wasRepeatingCalledCompiled());
    }

    /*
     * Test that OSR compilation also works if the loop node is not embedded in a CallTarget, but
     * just called directly with the node's execute method.
     */
    @Theory
    public void testExecutionWithoutCallTarget(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode());
        executeNoCallTarget(rootNode, OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());
    }

    /*
     * Test behavior of invalidations when they come from the outside.
     */
    @Theory
    public void testExternalInvalidations(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode());
        executeNoCallTarget(rootNode, OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());

        for (int i = 0; i < 10; i++) {
            rootNode.getOSRTarget().invalidate();
            Assert.assertNotNull(rootNode.getOSRTarget());
            assertNotCompiled(rootNode.getOSRTarget());
            Assert.assertNotNull(rootNode.getOSRTarget()); // no eager cleanup for thread safety

            executeNoCallTarget(rootNode, OSR_THRESHOLD - 1);
            assertNotCompiled(rootNode.getOSRTarget());
            Assert.assertNull(rootNode.getOSRTarget()); // cleaned up after further call

            executeNoCallTarget(rootNode, OSR_THRESHOLD + 1);
            assertCompiled(rootNode.getOSRTarget());
        }
    }

    /*
     * Test behavior of OSR compile loops if the invalidate internally during loop execution. Also
     * test that it respects the invalidation reprofile count.
     */
    @Theory
    public void testInternalInvalidations(OSRLoopFactory factory) {
        TestRepeatingNode repeating = new TestRepeatingNode();
        TestRootNode rootNode = new TestRootNode(factory, repeating);
        CallTarget target = runtime.createCallTarget(rootNode);
        target.call(OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());

        repeating.invalidationCounter = 5;
        target.call(4);
        assertCompiled(rootNode.getOSRTarget());
        target.call(2); // should trigger invalidation
        assertNotCompiled(rootNode.getOSRTarget());
    }

    /*
     * Test outer invalidation still triggers OSR.
     */
    @Theory
    public void testOuterInvalidationTriggersOSR(OSRLoopFactory factory) {
        TestRepeatingNode repeating = new TestRepeatingNode();
        TestRootNode rootNode = new TestRootNode(factory, repeating);
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);

        // compile inner
        target.call(OSR_THRESHOLD + 1);
        OptimizedCallTarget osrTarget = rootNode.getOSRTarget();
        assertCompiled(osrTarget);

        int i;
        for (i = 0; i < TruffleCompilerOptions.getValue(TruffleMinInvokeThreshold) - 2; i++) {
            target.call(0);
            assertNotCompiled(target);
            assertCompiled(rootNode.getOSRTarget());
        }

        target.call(0);
        assertCompiled(target);
        assertCompiled(rootNode.getOSRTarget());
        assertSame(rootNode.getOSRTarget(), osrTarget);

        target.invalidate();
        assertNotCompiled(target);
        assertCompiled(rootNode.getOSRTarget());
        assertSame(rootNode.getOSRTarget(), osrTarget);

        // after invalidating the outer method the osr target should still be valid and used
        target.call(15);
        // even though target is compiled it is invoked in interpreter
        target.invalidate();
        assertCompiled(rootNode.getOSRTarget());
        assertSame(rootNode.getOSRTarget(), osrTarget);
        // assertNotCompiled(target);

        // now externally invalidate the osr target and see if we compile again
        rootNode.getOSRTarget().invalidate();
        target.call(OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());

    }

    /*
     * Test that if a call target is called a min invocation theshold times it is unlikely that it
     * needs OSR at all.
     */
    @Theory
    public void testNoOSRAfterMinInvocationThreshold(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode());
        RootCallTarget target = runtime.createCallTarget(rootNode);
        int i;
        for (i = 0; i < TruffleCompilerOptions.getValue(TruffleMinInvokeThreshold); i++) {
            target.call(0);
            assertNotCompiled(rootNode.getOSRTarget());
        }
        target.call(OSR_THRESHOLD);
        assertNotCompiled(rootNode.getOSRTarget());
    }

    /*
     * Test that loop counts of osr loops propagate loop counts the parent call target.
     */
    @Theory
    public void testOSRMinInvocationThresholdPropagateLoopCounts(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode());
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
        int osrThreshold = OSR_THRESHOLD;
        int truffleMinInvokes = TruffleCompilerOptions.getValue(TruffleMinInvokeThreshold);

        int i;
        int invokesleft = osrThreshold;
        for (i = 0; i < truffleMinInvokes - 1; i++) {
            int invokes = osrThreshold / truffleMinInvokes;
            invokesleft -= invokes;
            target.call(invokes);
            assertNotCompiled(rootNode.getOSRTarget());
        }
        assertNotCompiled(target);
        /*
         * This should trigger OSR, but since the parent is compiling/compiled already we won't do
         * OSR.
         */
        target.call(invokesleft + 1);
        assertNotCompiled(rootNode.getOSRTarget());
        assertCompiled(target);
    }

    /*
     * Test that we are not crashing in multi threaded environments.
     */
    @Theory
    public void testThreadSafety(OSRLoopFactory factory) {
        int threshold = OSR_THRESHOLD;
        IntStream.generate(() -> 10).limit(10).parallel().forEach(i -> {
            TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode());
            IntStream.generate(() -> threshold).limit(10).parallel().forEach(k -> executeNoCallTarget(rootNode, threshold + 1));
            waitForCompiled(rootNode.getOSRTarget());
        });

        IntStream.generate(() -> 10).limit(10).parallel().forEach(i -> {
            TestRootNode rootNode = new TestRootNode(factory, new TestRepeatingNode());
            IntStream.generate(() -> threshold).limit(10).parallel().forEach(k -> executeNoCallTarget(rootNode, threshold));
            waitForCompiled(rootNode.getOSRTarget());
        });
    }

    /*
     * Test that two silbling loops are compiled independently.
     */
    @Theory
    public void testTwoLoopsSilblings(OSRLoopFactory factory) {
        TwoSilblingLoopNodesTest rootNode = new TwoSilblingLoopNodesTest(factory, new TestRepeatingNode(), new TestRepeatingNode());
        CallTarget target = runtime.createCallTarget(rootNode);
        target.call(OSR_THRESHOLD + 1, 1);
        assertCompiled(rootNode.getOSRTarget());
        assertNotCompiled(rootNode.getOSRTarget2());
        target.call(1, OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());
        assertCompiled(rootNode.getOSRTarget2());
        Assert.assertTrue(rootNode.getOSRTarget() != rootNode.getOSRTarget2());
    }

    private static class TwoSilblingLoopNodesTest extends TestRootNode {
        @Child OptimizedOSRLoopNode loopNode2;

        protected TwoSilblingLoopNodesTest(OSRLoopFactory factory, TestRepeatingNode repeating1, TestRepeatingNode repeating2) {
            super(factory, repeating1);
            loopNode2 = factory.createOSRLoop(repeating2, null, null);
            repeating2.param1 = param2;
        }

        public OptimizedCallTarget getOSRTarget2() {
            return loopNode2.getCompiledOSRLoop();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            super.execute(frame);
            loopNode2.executeLoop(frame);
            return null;
        }
    }

    /*
     * Test that two loops in a parent child relationship are propagating loop counts.
     */
    @Theory
    public void testTwoLoopsParentChild1(OSRLoopFactory factory) {
        ChildLoopRepeatingNode childLoop = new ChildLoopRepeatingNode(factory, new TestRepeatingNode(), loop -> {
            assertNotCompiled(loop.getOSRTarget());
            return null;
        });
        TestRootNode rootNode = new TestRootNode(factory, childLoop);
        CallTarget target = runtime.createCallTarget(rootNode);

        target.call(1, OSR_THRESHOLD);
        assertCompiled(rootNode.getOSRTarget());
        assertNotCompiled(childLoop.getOSRTarget());
    }

    @Theory
    public void testTwoLoopsParentChild2(OSRLoopFactory factory) {
        ChildLoopRepeatingNode childLoop = new ChildLoopRepeatingNode(factory, new TestRepeatingNode(), loop -> {
            assertCompiled(loop.getOSRTarget());
            return null;
        });
        TestRootNode rootNode = new TestRootNode(factory, childLoop);
        CallTarget target = runtime.createCallTarget(rootNode);

        target.call(1, OSR_THRESHOLD + 1);
        assertCompiled(rootNode.getOSRTarget());
        assertCompiled(childLoop.getOSRTarget());
    }

    private static class ChildLoopRepeatingNode extends TestRepeatingNode {

        @Child OptimizedOSRLoopNode loopNode2;

        private final Function<ChildLoopRepeatingNode, Void> onBackedge;

        protected ChildLoopRepeatingNode(OSRLoopFactory factory, TestRepeatingNode child, Function<ChildLoopRepeatingNode, Void> onBackedge) {
            this.loopNode2 = factory.createOSRLoop(child, null, null);
            this.onBackedge = onBackedge;

        }

        public OptimizedCallTarget getOSRTarget() {
            return loopNode2.getCompiledOSRLoop();
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                ((TestRepeatingNode) loopNode2.getRepeatingNode()).param1 = param2;
            }
            boolean next = super.executeRepeating(frame);
            if (next) {
                loopNode2.executeLoop(frame);
            } else {
                onBackedge.apply(this);
            }
            return next;
        }

    }

    /*
     * Test that a custom loop reported using LoopNode#reportLoopCount contributes to the OSR
     * compilation heuristic.
     */
    @Theory
    public void testCustomLoopContributingToOSR1(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new CustomInnerLoopRepeatingNode());
        runtime.createCallTarget(rootNode).call(10, OSR_THRESHOLD / 10 - 1); // triggers
        assertNotCompiled(rootNode.getOSRTarget());
        runtime.createCallTarget(rootNode).call(10, OSR_THRESHOLD / 10); // triggers
        assertCompiled(rootNode.getOSRTarget());
    }

    @Theory
    public void testCustomLoopContributingToOSR2(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new CustomInnerLoopRepeatingNode());
        runtime.createCallTarget(rootNode).call(1, OSR_THRESHOLD - 1);
        assertNotCompiled(rootNode.getOSRTarget());
        runtime.createCallTarget(rootNode).call(1, OSR_THRESHOLD);
        assertCompiled(rootNode.getOSRTarget());
    }

    @Theory
    public void testCustomLoopContributingToOSR3(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new CustomInnerLoopRepeatingNode());
        runtime.createCallTarget(rootNode).call(2, OSR_THRESHOLD / 2);
        assertCompiled(rootNode.getOSRTarget());
    }

    @Theory
    public void testCustomLoopContributingToOSR4(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new CustomInnerLoopRepeatingNode());
        runtime.createCallTarget(rootNode).call(2, OSR_THRESHOLD / 2 - 1);
        assertNotCompiled(rootNode.getOSRTarget());
    }

    private static class CustomInnerLoopRepeatingNode extends TestRepeatingNode {

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            boolean next = super.executeRepeating(frame);
            if (next) {
                // its like beeing in body
                try {
                    int count = frame.getInt(param2);

                    // imagine loop work with size count here

                    LoopNode.reportLoopCount(this, count);

                } catch (FrameSlotTypeException e) {
                    throw new AssertionError();
                }
            }
            return next;
        }

    }

    /*
     * OSR stack frames should not show up.
     */
    @Theory
    public void testStackTraceDoesNotShowOSR(OSRLoopFactory factory) {
        TestRootNode rootNode = new TestRootNode(factory, new TestOSRStackTrace());
        CallTarget target = runtime.createCallTarget(rootNode);
        target.call(1);
        rootNode.forceOSR();
        assertCompiled(rootNode.getOSRTarget());
        target.call(1);
        assertCompiled(rootNode.getOSRTarget());
    }

    private static class TestOSRStackTrace extends TestRepeatingNode {

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            boolean next = super.executeRepeating(frame);
            if (!next) {
                checkStackTrace();
            }
            return next;
        }

        @TruffleBoundary
        private void checkStackTrace() {
            final OptimizedOSRLoopNode loop = (OptimizedOSRLoopNode) getParent();
            final OptimizedCallTarget compiledLoop = loop.getCompiledOSRLoop();

            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {
                @Override
                public Void visitFrame(FrameInstance frameInstance) {
                    Assert.assertNotSame(compiledLoop, frameInstance.getCallTarget());
                    return null;
                }
            });
        }

    }

    /* Useful to test no dependencies on a root call target. */
    private static void executeNoCallTarget(TestRootNode rootNode, int count) {
        rootNode.adoptChildren();
        rootNode.execute(Truffle.getRuntime().createVirtualFrame(new Object[]{count}, rootNode.getFrameDescriptor()));
    }

    private interface OSRLoopFactory {
        OptimizedOSRLoopNode createOSRLoop(RepeatingNode repeating, FrameSlot[] readFrameSlots, FrameSlot[] writtenframeSlots);
    }

    private static void waitForCompiled(OptimizedCallTarget target) {
        if (target != null) {
            try {
                runtime.waitForCompilation(target, 10000);
            } catch (ExecutionException | TimeoutException e) {
                Assert.fail("timeout");
            }
        }
    }

    private static class TestRootNodeWithReplacement extends TestRootNode {
        @Child OptimizedOSRLoopNode toReplace;

        public TestRootNodeWithReplacement(OSRLoopFactory factory, TestRepeatingNode repeating) {
            super(factory, repeating);
            toReplace = factory.createOSRLoop(repeating, new FrameSlot[]{param1, param2}, new FrameSlot[]{param1, param2});
        }
    }

    private static class TestRootNode extends RootNode {

        @Child OptimizedOSRLoopNode loopNode;

        final FrameSlot param1;
        final FrameSlot param2;

        protected TestRootNode(OSRLoopFactory factory, TestRepeatingNode repeating) {
            super(null, new FrameDescriptor());
            param1 = getFrameDescriptor().addFrameSlot("param1", FrameSlotKind.Int);
            param2 = getFrameDescriptor().addFrameSlot("param2", FrameSlotKind.Int);
            loopNode = factory.createOSRLoop(repeating, new FrameSlot[]{param1, param2}, new FrameSlot[]{param1, param2});
            repeating.param1 = param1;
            repeating.param2 = param2;
        }

        public void forceOSR() {
            loopNode.forceOSR();
        }

        public OptimizedCallTarget getOSRTarget() {
            return loopNode.getCompiledOSRLoop();
        }

        public boolean wasRepeatingCalledCompiled() {
            return ((TestRepeatingNode) loopNode.getRepeatingNode()).compiled;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int nextLoopCount = (int) frame.getArguments()[0];
            frame.setInt(param1, nextLoopCount);
            if (frame.getArguments().length > 1) {
                frame.setInt(param2, (int) frame.getArguments()[1]);
            } else {
                frame.setInt(param2, 0);
            }
            loopNode.executeLoop(frame);
            return null;
        }
    }

    private static class TestRepeatingNode extends Node implements RepeatingNode {
        int invalidationCounter = -1;

        @CompilationFinal FrameSlot param1;
        @CompilationFinal FrameSlot param2;

        boolean compiled;

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            try {
                if (invalidationCounter >= 0) {
                    if (invalidationCounter == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        invalidationCounter = -1; // disable
                    } else {
                        invalidationCounter--;
                    }
                }

                if (CompilerDirectives.inCompiledCode()) {
                    compiled = true;
                } else {
                    compiled = false;
                }

                int counter = frame.getInt(param1);
                frame.setInt(param1, counter - 1);
                return counter != 0;
            } catch (FrameSlotTypeException e) {
                return false;
            }
        }

    }

}
