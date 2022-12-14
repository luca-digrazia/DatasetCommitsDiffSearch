/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.graalvm.compiler.truffle.runtime.OptimizedBlockNode;
import org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.PartialBlocks;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.nodes.BlockNode.ElementExceptionHandler;
import com.oracle.truffle.sl.SLLanguage;

public class OptimizedBlockNodeTest {

    @Test
    public void testExactlyBlockSize() {
        int blockSize = 1;
        for (int i = 0; i < 5; i++) {
            setup(blockSize);
            OptimizedBlockNode<?> block = createBlock(blockSize, 1);
            OptimizedCallTarget target = createTest(block);
            target.call();
            target.compile(true);
            // should not trigger and block compilation
            assertNull(block.getPartialBlocks());
            blockSize = blockSize * 2;
        }
    }

    @Test
    public void testBlockSizePlusOne() {
        int groupSize = 1;
        for (int i = 0; i < 5; i++) {
            setup(groupSize);
            OptimizedBlockNode<TestElement> block = createBlock(groupSize + 1, 1);
            OptimizedCallTarget target = createTest(block);
            assertNull(block.getPartialBlocks());
            target.call();
            target.compile(true);

            // should not trigger and block compilation
            PartialBlocks partialBlocks = block.getPartialBlocks();
            assertNotNull(partialBlocks);
            assertNotNull(partialBlocks.getBlockRanges());
            assertEquals(1, partialBlocks.getBlockRanges().length);
            assertEquals(groupSize, partialBlocks.getBlockRanges()[0]);
            assertNotNull(partialBlocks.getBlockTargets());
            assertEquals(2, partialBlocks.getBlockTargets().length);
            assertTrue(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());

            assertEquals(groupSize, target.call());

            // stays valid after call
            assertTrue(target.isValid());
            assertTrue(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());

            // test explicit invalidations
            partialBlocks.getBlockTargets()[0].invalidate(null, "test invalidation");
            assertTrue(target.isValid());
            assertFalse(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());

            target.invalidate(null, "test invalidation");
            assertFalse(target.isValid());
            assertFalse(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());
            assertEquals(groupSize, target.call());
            // 0 or 1 might be compiled or not
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());

            // test partial recompilation
            OptimizedCallTarget oldCallTarget = partialBlocks.getBlockTargets()[1];
            long oldAdress = oldCallTarget.getCodeAddress();
            target.compile(true);
            assertSame(partialBlocks, block.getPartialBlocks());
            assertTrue(target.isValid());
            assertTrue(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());
            assertSame(oldCallTarget, partialBlocks.getBlockTargets()[1]);
            assertNotEquals(0, oldAdress);
            assertEquals(oldAdress, partialBlocks.getBlockTargets()[1].getCodeAddress());

            groupSize = groupSize * 2;
        }
    }

    @Test
    public void testSimulateReplace() {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks partialBlocks;
        int groupSize;
        int expectedResult;

        groupSize = 2;
        setup(groupSize);
        block = createBlock(groupSize * 3, 1);
        target = createTest(block);
        expectedResult = groupSize * 3 - 1;
        assertEquals(expectedResult, target.call());
        target.compile(true);
        partialBlocks = block.getPartialBlocks();

        assertEquals(3, partialBlocks.getBlockTargets().length);
        assertValid(target, partialBlocks);
        assertEquals(expectedResult, target.call());

        block.getElements()[0].simulateReplace();
        assertFalse(target.isValid());
        assertFalse(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertTrue(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[1].simulateReplace();
        assertFalse(target.isValid());
        assertFalse(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertTrue(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[2].simulateReplace();
        assertFalse(target.isValid());
        assertTrue(partialBlocks.getBlockTargets()[0].isValid());
        assertFalse(partialBlocks.getBlockTargets()[1].isValid());
        assertTrue(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[3].simulateReplace();
        assertFalse(target.isValid());
        assertTrue(partialBlocks.getBlockTargets()[0].isValid());
        assertFalse(partialBlocks.getBlockTargets()[1].isValid());
        assertTrue(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[4].simulateReplace();
        assertFalse(target.isValid());
        assertTrue(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertFalse(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[5].simulateReplace();
        assertFalse(target.isValid());
        assertTrue(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertFalse(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        block.getElements()[1].simulateReplace();
        assertFalse(target.isValid());
        assertFalse(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        assertValid(target, partialBlocks);
    }

    @Test
    public void testExecuteMethods() throws UnexpectedResultException {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks partialBlocks;
        Object expectedResult;
        MaterializedFrame testFrame = Truffle.getRuntime().createMaterializedFrame(new Object[0]);

        setup(3);

        block = createBlock(9, 1, null);
        target = createTest(block);
        expectedResult = 8;
        assertEquals(expectedResult, target.call());
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);

        assertEquals(expectedResult, block.executeInt(testFrame, 0, null));
        block.executeVoid(testFrame, 0, null);
        OptimizedBlockNode<TestElement> block0 = block;
        assertUnexpected(() -> block0.executeLong(testFrame, 0, null), expectedResult);
        assertUnexpected(() -> block0.executeDouble(testFrame, 0, null), expectedResult);
        assertUnexpected(() -> block0.executeBoolean(testFrame, 0, null), expectedResult);
        assertValid(target, partialBlocks);

        expectedResult = 42.d;
        block = createBlock(13, 1, expectedResult);
        target = createTest(block);
        assertEquals(expectedResult, target.call());
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);

        assertEquals(expectedResult, block.executeDouble(testFrame, 0, null));
        block.executeVoid(testFrame, 0, null);
        OptimizedBlockNode<TestElement> block1 = block;
        assertUnexpected(() -> block1.executeLong(testFrame, 0, null), expectedResult);
        assertUnexpected(() -> block1.executeInt(testFrame, 0, null), expectedResult);
        assertUnexpected(() -> block1.executeBoolean(testFrame, 0, null), expectedResult);
        assertValid(target, partialBlocks);

        expectedResult = 42L;
        block = createBlock(12, 1, expectedResult);
        target = createTest(block);
        assertEquals(expectedResult, target.call());
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);

        assertEquals(expectedResult, block.executeLong(testFrame, 0, null));
        block.executeVoid(testFrame, 0, null);
        OptimizedBlockNode<TestElement> block2 = block;
        assertUnexpected(() -> block2.executeDouble(testFrame, 0, null), expectedResult);
        assertUnexpected(() -> block2.executeInt(testFrame, 0, null), expectedResult);
        assertUnexpected(() -> block2.executeBoolean(testFrame, 0, null), expectedResult);
        assertValid(target, partialBlocks);

        expectedResult = false;
        block = createBlock(7, 1, expectedResult);
        target = createTest(block);
        assertEquals(expectedResult, target.call());
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);

        assertEquals(expectedResult, block.executeBoolean(testFrame, 0, null));
        block.executeVoid(testFrame, 0, null);
        OptimizedBlockNode<TestElement> block3 = block;
        assertUnexpected(() -> block3.executeDouble(testFrame, 0, null), expectedResult);
        assertUnexpected(() -> block3.executeInt(testFrame, 0, null), expectedResult);
        assertUnexpected(() -> block3.executeLong(testFrame, 0, null), expectedResult);
        assertValid(target, partialBlocks);
    }

    @Test
    public void testStartsWithCompilation() {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks partialBlocks;
        Object expectedResult;
        boolean[] elementExecuted;

        setup(2);

        block = createBlock(5, 1, null);
        target = createTest(block);
        elementExecuted = ((TestRootNode) block.getRootNode()).elementExecuted;
        expectedResult = 4;
        assertEquals(expectedResult, target.call(0));
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);
        assertEquals(expectedResult, target.call(1));
        // first time observed a different start value -> deopt
        assertInvalid(target, partialBlocks, 0);
        assertFalse(elementExecuted[0]);
        assertTrue(elementExecuted[1]);
        assertTrue(elementExecuted[2]);
        assertTrue(elementExecuted[3]);
        assertTrue(elementExecuted[4]);
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);
        assertEquals(expectedResult, target.call(3));
        assertFalse(elementExecuted[0]);
        assertFalse(elementExecuted[1]);
        assertFalse(elementExecuted[2]);
        assertTrue(elementExecuted[3]);
        assertTrue(elementExecuted[4]);
        assertValid(target, partialBlocks);
        target.compile(true);
        assertValid(target, partialBlocks);
        assertEquals(expectedResult, target.call(4));
        assertFalse(elementExecuted[0]);
        assertFalse(elementExecuted[1]);
        assertFalse(elementExecuted[2]);
        assertFalse(elementExecuted[3]);
        assertTrue(elementExecuted[4]);
        assertValid(target, partialBlocks);
        target.compile(true);
        try {
            target.call(5);
            fail();
        } catch (IllegalArgumentException e) {
        }
        assertFalse(elementExecuted[0]);
        assertFalse(elementExecuted[1]);
        assertFalse(elementExecuted[2]);
        assertFalse(elementExecuted[3]);
        assertFalse(elementExecuted[4]);
    }

    @Test
    public void testHierarchicalBlocks() {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks partialBlocks;

        setup(3);

        block = createBlock(5, 2, null);
        target = createTest(block);
        target.call();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();

        assertEquals(2, partialBlocks.getBlockTargets().length);
        assertEquals(1, partialBlocks.getBlockRanges().length);
        assertEquals(3, partialBlocks.getBlockRanges()[0]);

        block = createBlock(5, 3, null);
        target = createTest(block);
        target.call();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();

        assertEquals(2, partialBlocks.getBlockTargets().length);
        assertEquals(1, partialBlocks.getBlockRanges().length);
        assertEquals(3, partialBlocks.getBlockRanges()[0]);
    }

    @Test
    public void testHierarchicalUnbalanced() {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks partialBlocks;

        setup(50);
        block = createBlock(10, 4, null);
        target = createTest(block);
        target.call();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();

        assertEquals(3, partialBlocks.getBlockTargets().length);
        assertEquals(2, partialBlocks.getBlockRanges().length);
        assertEquals(4, partialBlocks.getBlockRanges()[0]);
        assertEquals(8, partialBlocks.getBlockRanges()[1]);
    }

    @Test
    public void testNoCallCompilation() {
        int blockSize = 1;
        for (int i = 0; i < 5; i++) {
            setup(blockSize);
            OptimizedBlockNode<?> block = createBlock(blockSize + 1, 1);
            OptimizedCallTarget target = createTest(block);
            target.compile(true);
            assertValid(target, block.getPartialBlocks());
            target.call();
            assertValid(target, block.getPartialBlocks());
            blockSize = blockSize * 2;
        }
    }

    @Test
    public void testExceptionHandlerCompilation() {
        int blockSize = 1;
        ElementExceptionHandler e = new ElementExceptionHandler() {
            @Override
            public void onBlockElementException(VirtualFrame frame, Throwable ex, int elementIndex) {
                CompilerAsserts.partialEvaluationConstant(this.getClass());
                CompilerAsserts.partialEvaluationConstant(elementIndex);
            }
        };
        for (int i = 0; i < 5; i++) {
            setup(blockSize);
            OptimizedBlockNode<?> block = createBlock(blockSize + 1, 1);
            OptimizedCallTarget target = createTest(block);
            ((TestRootNode) target.getRootNode()).exceptionHandler = e;
            target.call(0);
            target.compile(true);
            assertValid(target, block.getPartialBlocks());
            target.call(0);
            assertValid(target, block.getPartialBlocks());
            blockSize = blockSize * 2;
        }
    }

    @Test
    public void testSimpleLanguageExample() {
        final int testBlockSize = 128;
        final int targetBlocks = 4;

        setup(testBlockSize);
        int emptyNodeCount = generateSLFunction(context, "empty", 0).getNonTrivialNodeCount();
        int singleNodeCount = generateSLFunction(context, "single", 1).getNonTrivialNodeCount();
        int twoNodeCount = generateSLFunction(context, "two", 2).getNonTrivialNodeCount();
        int singleStatementNodeCount = twoNodeCount - singleNodeCount;
        int blockOverhead = singleNodeCount - emptyNodeCount - singleStatementNodeCount;

        context.initialize("sl");

        int statements = Math.floorDiv(((testBlockSize * targetBlocks) - (blockOverhead)), singleStatementNodeCount);
        OptimizedCallTarget target = generateSLFunction(context, "test", statements);
        assertEquals((statements - 1) * singleStatementNodeCount + singleNodeCount, target.getNonTrivialNodeCount());

        Value v = context.getBindings("sl").getMember("test");

        // make it compile with threshold
        for (int i = 0; i < TEST_COMPILATION_THRESHOLD; i++) {
            assertEquals(statements, v.execute().asInt());
        }
        assertTrue(target.isValid());
        List<OptimizedBlockNode<?>> blocks = new ArrayList<>();
        target.getRootNode().accept(new NodeVisitor() {

            @Override
            public boolean visit(Node node) {
                if (node instanceof OptimizedBlockNode<?>) {
                    blocks.add((OptimizedBlockNode<?>) node);
                }
                return true;
            }
        });
        assertEquals(1, blocks.size());
        OptimizedBlockNode<?> block = blocks.iterator().next();
        PartialBlocks partialBlocks = block.getPartialBlocks();
        assertNotNull(partialBlocks);
        assertEquals(targetBlocks, partialBlocks.getBlockTargets().length);
    }

    private static OptimizedBlockNode<TestElement> createBlock(int blockSize, int depth) {
        return createBlock(blockSize, depth, null);
    }

    private static OptimizedBlockNode<TestElement> createBlock(int blockSize, int depth, Object returnValue) {
        if (depth == 0) {
            return null;
        }
        TestElement[] elements = new TestElement[blockSize];
        for (int i = 0; i < blockSize; i++) {
            elements[i] = new TestElement(createBlock(blockSize, depth - 1, returnValue), returnValue == null ? i : returnValue, i);
        }
        return (OptimizedBlockNode<TestElement>) BlockNode.create(elements);
    }

    private static OptimizedCallTarget createTest(BlockNode<?> block) {
        TestRootNode root = new TestRootNode(block, "Block[" + block.getElements().length + "]");
        return (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root);
    }

    private static OptimizedCallTarget generateSLFunction(Context context, String name, int statements) {
        StringBuilder b = new StringBuilder("function " + name + "(){");
        if (statements > 0) {
            b.append("i = 0;\n");
        }
        for (int i = 0; i < statements; i++) {
            b.append("i = i + 1;\n");
        }
        if (statements > 0) {
            b.append("return i;\n");
        }
        b.append("}");
        context.eval("sl", b.toString());
        context.getBindings("sl").getMember(name).execute();
        context.enter();
        try {
            return ((OptimizedCallTarget) SLLanguage.getCurrentContext().getFunctionRegistry().getFunction(name).getCallTarget());
        } finally {
            context.leave();
        }
    }

    private static void assertValid(OptimizedCallTarget target, PartialBlocks partialBlocks) {
        assertNotNull(partialBlocks);
        assertTrue(target.isValid());
        for (int i = 0; i < partialBlocks.getBlockTargets().length; i++) {
            OptimizedCallTarget blockTarget = partialBlocks.getBlockTargets()[i];
            assertTrue(String.valueOf(i), blockTarget.isValid());
        }
    }

    private static void assertInvalid(OptimizedCallTarget target, PartialBlocks partialBlocks, int startIndex) {
        assertFalse(target.isValid());
        for (int i = startIndex; i < partialBlocks.getBlockTargets().length; i++) {
            OptimizedCallTarget blockTarget = partialBlocks.getBlockTargets()[i];
            assertFalse(String.valueOf(i), blockTarget.isValid());
        }
    }

    private static void assertUnexpected(Callable<?> callable, Object result) {
        try {
            callable.call();
        } catch (UnexpectedResultException t) {
            assertEquals(result, t.getResult());
            return;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        fail("expected unexpected result but no exception was thrown");
    }

    private static TruffleRuntimeOptionsOverrideScope backgroundCompilation;

    @BeforeClass
    public static void beforeClass() {
        backgroundCompilation = TruffleRuntimeOptions.overrideOptions(SharedTruffleRuntimeOptions.TruffleBackgroundCompilation, false);

        /*
         * Do some dummy compilation to make sure everything is resolved.
         */
        OptimizedBlockNode<?> block = createBlock(10, 1);
        OptimizedCallTarget target = createTest(block);
        target.call();
        target.compile(true);
    }

    @AfterClass
    public static void afterClass() {
        backgroundCompilation.close();
    }

    private Context context;

    @After
    public void clearContext() {
        if (context != null) {
            context.leave();
            context.close();
        }
    }

    private static final int TEST_COMPILATION_THRESHOLD = 10;

    private void setup(int blockCompilationSize) {
        clearContext();
        context = Context.newBuilder().allowAllAccess(true)//
                        .option("engine.PartialBlockCompilationSize", String.valueOf(blockCompilationSize))//
                        .option("engine.CompilationThreshold", String.valueOf(TEST_COMPILATION_THRESHOLD)).build();
        context.enter();
    }

    static class ElementChildNode extends Node {

        @Override
        public NodeCost getCost() {
            // we don't want this to contribute to node costs
            return NodeCost.NONE;
        }

    }

    static class TestElement extends Node implements BlockNode.TypedElement {

        @Child BlockNode<?> childBlock;
        @Child ElementChildNode childNode = new ElementChildNode();

        final Object returnValue;

        final int childIndex;

        @CompilationFinal TestRootNode root;

        TestElement(BlockNode<?> childBlock, Object returnValue, int childIndex) {
            this.childBlock = childBlock;
            this.returnValue = returnValue;
            this.childIndex = childIndex;
        }

        public void simulateReplace() {
            childNode.replace(new ElementChildNode());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                if (root == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    root = (TestRootNode) getRootNode();
                }
            }
            if (root != null) {
                root.elementExecuted[childIndex] = true;
            }
            if (childBlock != null) {
                return childBlock.execute(frame, 0, null);
            }
            return returnValue;
        }

        @Override
        public byte executeByte(VirtualFrame frame) throws UnexpectedResultException {
            Object result = execute(frame);
            if (result instanceof Byte) {
                return (byte) result;
            }
            throw new UnexpectedResultException(result);
        }

        @Override
        public short executeShort(VirtualFrame frame) throws UnexpectedResultException {
            Object result = execute(frame);
            if (result instanceof Short) {
                return (short) result;
            }
            throw new UnexpectedResultException(result);
        }

        @Override
        public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            Object result = execute(frame);
            if (result instanceof Integer) {
                return (int) result;
            }
            throw new UnexpectedResultException(result);
        }

        @Override
        public char executeChar(VirtualFrame frame) throws UnexpectedResultException {
            Object result = execute(frame);
            if (result instanceof Character) {
                return (char) result;
            }
            throw new UnexpectedResultException(result);
        }

        @Override
        public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
            Object result = execute(frame);
            if (result instanceof Long) {
                return (long) result;
            }
            throw new UnexpectedResultException(result);
        }

        @Override
        public float executeFloat(VirtualFrame frame) throws UnexpectedResultException {
            Object result = execute(frame);
            if (result instanceof Float) {
                return (float) result;
            }
            throw new UnexpectedResultException(result);
        }

        @Override
        public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
            Object result = execute(frame);
            if (result instanceof Double) {
                return (double) result;
            }
            throw new UnexpectedResultException(result);
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
            Object result = execute(frame);
            if (result instanceof Boolean) {
                return (boolean) result;
            }
            throw new UnexpectedResultException(result);
        }

    }

    static class TestRootNode extends RootNode {

        @Child BlockNode<?> block;
        final boolean[] elementExecuted;

        private final String name;
        @CompilationFinal ElementExceptionHandler exceptionHandler;

        TestRootNode(BlockNode<?> block, String name) {
            super(null);
            this.block = block;
            this.name = name;
            this.elementExecuted = new boolean[block.getElements().length];
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            for (int i = 0; i < elementExecuted.length; i++) {
                elementExecuted[i] = false;
            }
            int startIndex;
            if (frame.getArguments().length > 0) {
                startIndex = (int) frame.getArguments()[0];
            } else {
                startIndex = 0;
            }
            return block.execute(frame, startIndex, exceptionHandler);
        }

        @Override
        public String toString() {
            return getName();
        }

    }

}
