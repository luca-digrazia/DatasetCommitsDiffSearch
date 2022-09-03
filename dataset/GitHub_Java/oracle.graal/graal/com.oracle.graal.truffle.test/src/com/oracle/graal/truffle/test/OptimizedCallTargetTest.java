/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.oracle.graal.truffle.GraalTruffleRuntime;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.graal.truffle.TruffleCompilerOptions;
import com.oracle.graal.truffle.test.nodes.AbstractTestNode;
import com.oracle.graal.truffle.test.nodes.ConstantTestNode;
import com.oracle.graal.truffle.test.nodes.RootTestNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

public class OptimizedCallTargetTest {
    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();
    private static final Field nodeRewritingAssumptionField;
    static {
        try {
            nodeRewritingAssumptionField = OptimizedCallTarget.class.getDeclaredField("nodeRewritingAssumption");
            nodeRewritingAssumptionField.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new AssertionError(e);
        }
    }

    private static Assumption getRewriteAssumption(OptimizedCallTarget target) {
        try {
            return (Assumption) nodeRewritingAssumptionField.get(target);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertCompiled(OptimizedCallTarget target) {
        assertNotNull(target);
        try {
            runtime.waitForCompilation(target, 10000);
        } catch (ExecutionException | TimeoutException e) {
            fail("timeout");
        }
        assertTrue(target.isValid());
    }

    private static void assertNotCompiled(OptimizedCallTarget target) {
        assertNotNull(target);
        assertFalse(target.isValid());
    }

    private static final class CallTestNode extends AbstractTestNode {
        @Child private DirectCallNode callNode;

        CallTestNode(CallTarget ct) {
            this.callNode = runtime.createDirectCallNode(ct);
        }

        @Override
        public int execute(VirtualFrame frame) {
            return (int) callNode.call(frame, frame.getArguments());
        }
    }

    volatile int testInvalidationCounterCompiled = 0;
    volatile int testInvalidationCounterInterpreted = 0;
    volatile boolean doInvalidate;

    @Test
    public void testCompilationHeuristics() {
        testInvalidationCounterCompiled = 0;
        testInvalidationCounterInterpreted = 0;
        doInvalidate = false;
        OptimizedCallTarget target = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(TruffleLanguage.class, null, null) {
            @Override
            public Object execute(VirtualFrame frame) {
                if (CompilerDirectives.inInterpreter()) {
                    testInvalidationCounterInterpreted++;
                } else {
                    testInvalidationCounterCompiled++;
                }
                // doInvalidate needs to be volatile otherwise it floats up.
                if (doInvalidate) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                return null;
            }
        });
        final int compilationThreshold = TruffleCompilerOptions.TruffleCompilationThreshold.getValue();
        final int reprofileCount = TruffleCompilerOptions.TruffleReplaceReprofileCount.getValue();
        assertTrue(compilationThreshold >= 2);

        int expectedCompiledCount = 0;
        int expectedInterpreterCount = 0;
        for (int i = 0; i < compilationThreshold; i++) {
            target.call();
        }
        expectedInterpreterCount += compilationThreshold;
        assertCompiled(target);
        assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
        assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);

        for (int j = 1; j < 100; j++) {

            for (int i = 0; i < compilationThreshold; i++) {
                target.call();
            }
            expectedCompiledCount += compilationThreshold;
            assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
            assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);
            assertCompiled(target);

            target.invalidate();
            assertNotCompiled(target);

            for (int i = 0; i < reprofileCount; i++) {
                target.call();
            }
            assertCompiled(target);
            expectedInterpreterCount += reprofileCount;
            assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
            assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);

            doInvalidate = true;
            expectedCompiledCount++;
            target.call();
            assertNotCompiled(target);
            doInvalidate = false;

            assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
            assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);

            for (int i = 0; i < reprofileCount; i++) {
                target.call();
            }
            assertCompiled(target);
            expectedInterpreterCount += reprofileCount;

            assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
            assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);

            for (int i = 0; i < compilationThreshold; i++) {
                target.call();
            }

            assertCompiled(target);

            expectedCompiledCount += compilationThreshold;
            assertEquals(expectedCompiledCount, testInvalidationCounterCompiled);
            assertEquals(expectedInterpreterCount, testInvalidationCounterInterpreted);
        }
    }

    @Test
    public void testRewriteAssumption() {
        String testName = "testRewriteAssumption";
        final int compilationThreshold = TruffleCompilerOptions.TruffleCompilationThreshold.getValue();
        assertTrue(compilationThreshold >= 2);
        assertTrue("test only works with inlining enabled", TruffleCompilerOptions.TruffleFunctionInlining.getValue());

        IntStream.range(0, 8).parallel().forEach(i -> {
            OptimizedCallTarget innermostCallTarget = (OptimizedCallTarget) runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), testName + 0, new AbstractTestNode() {
                @Child private AbstractTestNode child = new ConstantTestNode(42);
                @Child private AbstractTestNode dummy = new ConstantTestNode(17);

                @Override
                public int execute(VirtualFrame frame) {
                    int k = (int) frame.getArguments()[0];
                    if (k > compilationThreshold) {
                        CompilerDirectives.transferToInterpreter();
                        dummy.replace(new ConstantTestNode(k));
                    }
                    return child.execute(frame);
                }
            }));
            OptimizedCallTarget ct = innermostCallTarget;
            ct = (OptimizedCallTarget) runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), testName + 1, new CallTestNode(ct)));
            ct = (OptimizedCallTarget) runtime.createCallTarget(new RootTestNode(new FrameDescriptor(), testName + 2, new CallTestNode(ct)));
            final OptimizedCallTarget outermostCallTarget = ct;

            assertNull("assumption is initially null", getRewriteAssumption(innermostCallTarget));

            IntStream.range(0, compilationThreshold / 2).parallel().forEach(k -> {
                assertEquals(42, outermostCallTarget.call(k));
                assertNull("assumption stays null in the interpreter", getRewriteAssumption(innermostCallTarget));
            });

            outermostCallTarget.compile();
            assertCompiled(outermostCallTarget);
            Assumption firstRewriteAssumption = getRewriteAssumption(innermostCallTarget);
            assertNotNull("assumption must not be null after compilation", firstRewriteAssumption);
            assertTrue(firstRewriteAssumption.isValid());

            List<Assumption> rewriteAssumptions = IntStream.range(0, 2 * compilationThreshold).parallel().mapToObj(k -> {
                assertEquals(42, outermostCallTarget.call(k));

                Assumption rewriteAssumptionAfter = getRewriteAssumption(innermostCallTarget);
                assertNotNull("assumption must not be null after compilation", rewriteAssumptionAfter);
                return rewriteAssumptionAfter;
            }).collect(Collectors.toList());

            Assumption finalRewriteAssumption = getRewriteAssumption(innermostCallTarget);
            assertNotNull("assumption must not be null after compilation", finalRewriteAssumption);
            assertNotSame(firstRewriteAssumption, finalRewriteAssumption);
            assertFalse(firstRewriteAssumption.isValid());
            assertTrue(finalRewriteAssumption.isValid());

            assertFalse(rewriteAssumptions.stream().filter(a -> a != finalRewriteAssumption).anyMatch(Assumption::isValid));
        });
    }
}
