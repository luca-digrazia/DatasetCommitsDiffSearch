/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class CodeInvalidationTest extends AbstractPolyglotTest {
    abstract static class BaseNode extends Node {
        abstract Object execute(VirtualFrame frame);
    }

    static final class NodeToInvalidate extends BaseNode {
        private final ThreadLocal<Boolean> valid;
        private final CountDownLatch latch;
        private final ThreadLocal<Boolean> latchCountedDown = ThreadLocal.withInitial(() -> false);

        NodeToInvalidate(ThreadLocal<Boolean> valid, CountDownLatch latch) {
            this.valid = valid;
            this.latch = latch;
        }

        @Override
        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            if (CompilerDirectives.inCompiledCode()) {
                if (!isValid()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new AssertionError("Code invalidated!");
                }
            }
            return false;
        }

        @CompilerDirectives.TruffleBoundary
        boolean isValid() {
            if (!latchCountedDown.get()) {
                latch.countDown();
                latchCountedDown.set(true);
            }
            return valid.get();
        }
    }

    static final class AlwaysThrowNode extends BaseNode {
        @Override
        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new RuntimeException("Node replace successful!");
        }
    }

    static final class WhileLoopNode extends BaseNode {

        @Child private LoopNode loop;

        @CompilerDirectives.CompilationFinal FrameSlot loopIndexSlot;
        @CompilerDirectives.CompilationFinal FrameSlot loopResultSlot;

        WhileLoopNode(Object loopCount, BaseNode child) {
            this.loop = Truffle.getRuntime().createLoopNode(new LoopConditionNode(loopCount, child));
        }

        FrameSlot getLoopIndex() {
            FrameSlot indexSlot = loopIndexSlot;
            if (indexSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                synchronized (this) {
                    indexSlot = loopIndexSlot;
                    if (indexSlot == null) {
                        FrameDescriptor descriptor = getRootNode().getFrameDescriptor();
                        indexSlot = descriptor.findOrAddFrameSlot("loopIndex" + getLoopDepth());
                        // System.out.println(Thread.currentThread().getId() + ": Loop index slot
                        // added to descriptor " + System.identityHashCode(descriptor));
                        loopIndexSlot = indexSlot;
                    }
                }
            }
            return indexSlot;
        }

        FrameSlot getResult() {
            FrameSlot resultSlot = loopResultSlot;
            if (resultSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                synchronized (this) {
                    resultSlot = loopResultSlot;
                    if (resultSlot == null) {
                        FrameDescriptor descriptor = getRootNode().getFrameDescriptor();
                        resultSlot = descriptor.findOrAddFrameSlot("loopResult" + getLoopDepth());
                        // System.out.println(Thread.currentThread().getId() + ": Loop result slot
                        // added to descriptor " + System.identityHashCode(descriptor));
                        loopResultSlot = resultSlot;
                    }
                }
            }
            return resultSlot;
        }

        private int getLoopDepth() {
            Node node = getParent();
            int count = 0;
            while (node != null) {
                if (node instanceof WhileLoopNode) {
                    count++;
                }
                node = node.getParent();
            }
            return count;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(getResult(), false);
            frame.setInt(getLoopIndex(), 0);
            loop.execute(frame);
            try {
                return frame.getObject(loopResultSlot);
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }

        final class LoopConditionNode extends BaseNode implements RepeatingNode {

            @Child private volatile BaseNode child;

            private final int loopCount;
            private final boolean infinite;

            LoopConditionNode(Object loopCount, BaseNode child) {
                this.child = child;
                boolean inf = false;
                if (loopCount instanceof Double) {
                    if (((Double) loopCount).isInfinite()) {
                        inf = true;
                    }
                    this.loopCount = ((Double) loopCount).intValue();
                } else if (loopCount instanceof Integer) {
                    this.loopCount = (int) loopCount;
                } else {
                    this.loopCount = 0;
                }
                this.infinite = inf;

            }

            @Override
            public boolean executeRepeating(VirtualFrame frame) {
                int i;
                try {
                    i = frame.getInt(loopIndexSlot);
                } catch (FrameSlotTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(e);
                }
                if (infinite || i < loopCount) {
                    Object resultValue = execute(frame);
                    frame.setInt(loopIndexSlot, i + 1);
                    frame.setObject(loopResultSlot, resultValue);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            Object execute(VirtualFrame frame) {
                return child.execute(frame);
            }
        }
    }

    private static class RunCode implements Runnable {
        private final OptimizedCallTarget loopCallTarget;
        private final NodeToInvalidate nodeToInvalidate;
        private final Context context;
        private final Source code;

        RunCode(OptimizedCallTarget loopCallTarget, Context context, Source code, NodeToInvalidate nodeToInvalidate) {
            this.loopCallTarget = loopCallTarget;
            this.context = context;
            this.code = code;
            this.nodeToInvalidate = nodeToInvalidate;
        }

        @Override
        public void run() {
            try {
                if (nodeToInvalidate != null) {
                    nodeToInvalidate.valid.set(false);
                }
                context.eval(code);
            } catch (PolyglotException e) {
                if ("java.lang.AssertionError: Code invalidated!".equals(e.getMessage())) {
                    nodeToInvalidate.replace(new AlwaysThrowNode());
                } else {
                    throw e;
                }
            }
        }
    }

    @Test
    public void testInvalidation() throws IOException, InterruptedException, ExecutionException {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        CountDownLatch latch = new CountDownLatch(2);
        NodeToInvalidate nodeToInvalidate = new NodeToInvalidate(ThreadLocal.withInitial(() -> true), latch);
        WhileLoopNode testedCode = new WhileLoopNode(1000000000, nodeToInvalidate);
        LoopNode loopNode = testedCode.loop;

        /*
         * Make sure we use the same call target for the single source that we use. Otherwise
         * storing the frame slots in member fields of WholeLoopNode wouldn't work as the
         * WhileLoopNode is pre-created before the parsing, and so it can be used only in one call
         * target (root node).
         */
        CallTarget[] callTarget = new CallTarget[1];
        setupEnv(Context.create(), new ProxyLanguage() {
            private final List<CallTarget> targets = new LinkedList<>(); // To prevent from GC

            @Override
            protected synchronized CallTarget parse(ParsingRequest request) throws Exception {
                com.oracle.truffle.api.source.Source source = request.getSource();
                if (callTarget[0] == null) {
                    callTarget[0] = Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {

                        @Node.Child private volatile BaseNode child = testedCode;

                        @Override
                        public Object execute(VirtualFrame frame) {
                            return child.execute(frame);
                        }

                        @Override
                        public SourceSection getSourceSection() {
                            return source.createSection(1);
                        }

                    });
                }
                targets.add(callTarget[0]);
                return callTarget[0];
            }

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });

        Source source = Source.newBuilder(ProxyLanguage.ID, "", "DummySource").build();
        // First execution should compile the loop.
        context.eval(source);
        if (!(Truffle.getRuntime() instanceof DefaultTruffleRuntime)) {
            Future<?> future1;
            Future<?> future2;
            OptimizedCallTarget loopCallTarget = (OptimizedCallTarget) getLoopNodeCallTarget(loopNode);
            Assert.assertNotNull(loopCallTarget);
            Assert.assertTrue(loopCallTarget.isValid());
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                future1 = executor.submit(new RunCode(loopCallTarget, context, source, null));
                nodeToInvalidate.latch.await();
                future2 = executor.submit(new RunCode(loopCallTarget, context, source, nodeToInvalidate));
                future1.get();
                future2.get();
                Assert.fail();
            } catch (ExecutionException e) {
                Assert.assertTrue(e.getCause() instanceof PolyglotException);
                Assert.assertEquals(e.getCause().getMessage(), "java.lang.RuntimeException: Node replace successful!");
            } finally {
                executor.shutdownNow();
                executor.awaitTermination(100, TimeUnit.SECONDS);
            }
        }
    }

    private static Object getLoopNodeCallTarget(LoopNode loopNode) {
        Object toRet = null;
        if (loopNode instanceof ReplaceObserver) {
            try {
                Field callTargetField = loopNode.getClass().getSuperclass().getDeclaredField("compiledOSRLoop");
                callTargetField.setAccessible(true);
                toRet = callTargetField.get(loopNode);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new AssertionError("Unable to obtain OSR call target of a loop node!", e);
            }
        }
        return toRet;
    }
}
