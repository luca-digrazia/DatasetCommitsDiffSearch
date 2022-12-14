package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ExperimentalSplittingStrategyTest extends AbstractSplittingStrategyTest {

    private static TruffleCompilerOptions.TruffleOptionsOverrideScope experimentalSplittingScope;

    @BeforeClass
    public static void before() {
        experimentalSplittingScope = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleExperimentalSplitting, true);
    }

    @AfterClass
    public static void after() {
        experimentalSplittingScope.close();
    }

    @Before
    public void boostBudget() {
        createDummyTargetsToBoostGrowingSplitLimit();
    }

    // Root node for all nodes in this test
    @ReportPolymorphism
    abstract static class SplittingTestNode extends Node {
        public abstract Object execute(VirtualFrame frame);
    }

    @NodeChild
    abstract static class TurnsPolymorphicOnZeroNode extends SplittingTestNode {
        @Specialization(guards = "value != 0")
        static int do1(int value) {
            return value;
        }

        @Specialization
        static int do2(int value) {
            return value;
        }

        @Fallback
        static int do3(VirtualFrame frame, Object value) {
            return 0;
        }
    }

    @NodeChild
    abstract static class TurnsPolymorphicOnZeroButSpecializationIsExcludedNode extends SplittingTestNode {
        @Specialization(guards = "value != 0")
        int do1(int value) {
            return value;
        }

        @ReportPolymorphism.Exclude
        @Specialization
        int do2(int value) {
            return value;
        }

        @Fallback
        int do3(VirtualFrame frame, Object value) {
            return 0;
        }
    }

    @NodeChild
    @ReportPolymorphism.Exclude
    abstract static class TurnsPolymorphicOnZeroButClassIsExcludedNode extends SplittingTestNode {
        @Specialization(guards = "value != 0")
        int do1(int value) {
            return value;
        }

        @Specialization
        int do2(int value) {
            return value;
        }

        @Fallback
        int do3(VirtualFrame frame, Object value) {
            return 0;
        }
    }

    static class ReturnsArgumentNode extends SplittingTestNode {
        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }
    }

    class SplittingTestRootNode extends SplittableRootNode {
        @Child private SplittingTestNode bodyNode;

        SplittingTestRootNode(SplittingTestNode bodyNode) {
            super();
            this.bodyNode = bodyNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return bodyNode.execute(frame);
        }
    }

    @NodeChild
    @ReportPolymorphism
    abstract static class HasInlineCacheNode extends SplittingTestNode {
        @Specialization(limit = "2", //
                        guards = "target.getRootNode() == cachedNode")
        protected static Object doDirect(RootCallTarget target, @Cached("target.getRootNode()") RootNode cachedNode) {
            return target.call(noArguments);
        }

        @Specialization(replaces = "doDirect")
        protected static Object doIndirect(RootCallTarget target) {
            return target.call(noArguments);
        }
    }

    static class TwoDummiesAndAnotherNode extends SplittingTestNode {
        int counter;
        RootCallTarget dummy = runtime.createCallTarget(new DummyRootNode());

        @Override
        public Object execute(VirtualFrame frame) {
            if (counter < 2) {
                counter++;
            } else {
                counter = 0;
                dummy = runtime.createCallTarget(new DummyRootNode());
            }
            return dummy;
        }
    }

    private static Boolean getNeedsSplit(OptimizedCallTarget callTarget) {
        try {
            return (Boolean) reflectivelyGetField(callTarget, "needsSplit");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Assert.assertTrue("Cannot read \"needsSplit\" field from OptimizedCallTarget", false);
            return false;
        }
    }

    @Test
    public void testSplitsDirectCalls() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(ExperimentalSplittingStrategyTestFactory.HasInlineCacheNodeGen.create(new ReturnsArgumentNode())));
        Object[] first = new Object[]{runtime.createCallTarget(new DummyRootNode())};
        Object[] second = new Object[]{runtime.createCallTarget(new DummyRootNode())};
        testSplitsDirectCallsHelper(callTarget, first, second);

        callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(ExperimentalSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsArgumentNode())));
        // two callers for a target are needed
        testSplitsDirectCallsHelper(callTarget, new Object[]{1}, new Object[]{0});
    }

    private void testSplitsDirectCallsHelper(OptimizedCallTarget callTarget, Object[] firstArgs, Object[] secondArgs) {
        // two callers for a target are needed
        runtime.createDirectCallNode(callTarget);
        final DirectCallNode directCallNode = runtime.createDirectCallNode(callTarget);
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        directCallNode.call(secondArgs);
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callTarget));
        directCallNode.call(secondArgs);
        Assert.assertTrue("Target needs split but not split", directCallNode.isCallTargetCloned());

        // Test new dirrectCallNode will split
        final DirectCallNode newCallNode = runtime.createDirectCallNode(callTarget);
        newCallNode.call(firstArgs);
        Assert.assertTrue("new call node to \"needs split\" target is not split", newCallNode.isCallTargetCloned());
    }

    @Test
    public void testDoesNotSplitsDirectCalls() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(ExperimentalSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsArgumentNode())));
        testDoesNotSplitDirectCallHelper(callTarget, new Object[]{1}, new Object[]{0});

        callTarget = (OptimizedCallTarget) runtime.createCallTarget(new SplittingTestRootNode(
                        ExperimentalSplittingStrategyTestFactory.TurnsPolymorphicOnZeroButClassIsExcludedNodeGen.create(new ReturnsArgumentNode())));
        testDoesNotSplitDirectCallHelper(callTarget, new Object[]{1}, new Object[]{0});

        callTarget = (OptimizedCallTarget) runtime.createCallTarget(new SplittingTestRootNode(
                        ExperimentalSplittingStrategyTestFactory.TurnsPolymorphicOnZeroButClassIsExcludedNodeGen.create(new ReturnsArgumentNode())));
        testDoesNotSplitDirectCallHelper(callTarget, new Object[]{1}, new Object[]{0});
    }

    private void testDoesNotSplitDirectCallHelper(OptimizedCallTarget callTarget, Object[] firstArgs, Object[] secondArgs) {
        final RootCallTarget outer = runtime.createCallTarget(new CallsInnerNode(callTarget));
        outer.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        outer.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        outer.call(secondArgs);
        Assert.assertFalse("Target needs split even though it has only one caller", getNeedsSplit(callTarget));

        // Test new dirrectCallNode will NOT split
        final DirectCallNode newCallNode = runtime.createDirectCallNode(callTarget);
        newCallNode.call(secondArgs);
        Assert.assertFalse("new call node to \"needs split\" target is not split", newCallNode.isCallTargetCloned());
    }

    class CallsInnerNode extends SplittableRootNode {

        CallsInnerNode(RootCallTarget toCall) {
            this.callNode = (OptimizedDirectCallNode) runtime.createDirectCallNode(toCall);
        }

        @Child private OptimizedDirectCallNode callNode = null;

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
        }
    }

    @Test
    public void testSplitPropagatesThrongSoleCallers() {
        OptimizedCallTarget turnsPolymorphic = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(ExperimentalSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsArgumentNode())));
        testPropagatesThroughSoleCallers(turnsPolymorphic, new Object[]{1}, new Object[]{0});
        turnsPolymorphic = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(ExperimentalSplittingStrategyTestFactory.HasInlineCacheNodeGen.create(new ReturnsArgumentNode())));
        Object[] first = new Object[]{runtime.createCallTarget(new DummyRootNode())};
        Object[] second = new Object[]{runtime.createCallTarget(new DummyRootNode())};
        testPropagatesThroughSoleCallers(turnsPolymorphic, first, second);
    }

    private void testPropagatesThroughSoleCallers(OptimizedCallTarget turnsPolymorphic, Object[] firstArgs, Object[] secondArgs) {
        final OptimizedCallTarget callsInner = (OptimizedCallTarget) runtime.createCallTarget(new CallsInnerNode(turnsPolymorphic));
        final OptimizedCallTarget callsCallsInner = (OptimizedCallTarget) runtime.createCallTarget(new CallsInnerNode(callsInner));
        // two callers for a target are needed
        runtime.createDirectCallNode(callsCallsInner);
        final DirectCallNode directCallNode = runtime.createDirectCallNode(callsCallsInner);
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callsCallsInner));
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callsInner));
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(turnsPolymorphic));
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callsCallsInner));
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callsInner));
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(turnsPolymorphic));
        directCallNode.call(secondArgs);
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callsCallsInner));
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callsInner));
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(turnsPolymorphic));

        directCallNode.call(secondArgs);
        Assert.assertTrue("Target needs split but not split", directCallNode.isCallTargetCloned());

        // Test new dirrectCallNode will split
        DirectCallNode newCallNode = runtime.createDirectCallNode(callsCallsInner);
        newCallNode.call(secondArgs);
        Assert.assertTrue("new call node to \"needs split\" target is not split", newCallNode.isCallTargetCloned());

        newCallNode = runtime.createDirectCallNode(callsInner);
        newCallNode.call(secondArgs);
        Assert.assertTrue("new call node to \"needs split\" target is not split", newCallNode.isCallTargetCloned());

        newCallNode = runtime.createDirectCallNode(turnsPolymorphic);
        newCallNode.call(secondArgs);
        Assert.assertTrue("new call node to \"needs split\" target is not split", newCallNode.isCallTargetCloned());
    }

    @Test
    public void testNoSplitsDirectCallsBecauseFirstExecution() {
        final OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(new SplittableRootNode() {
            @Child private OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) runtime.createDirectCallNode(runtime.createCallTarget(
                            new SplittingTestRootNode(ExperimentalSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsArgumentNode()))));

            @Override
            public Object execute(VirtualFrame frame) {
                final Object[] first = {1};
                callNode.call(first);
                callNode.call(first);
                // This call turns the node polymorphic
                final Object[] second = {0};
                callNode.call(second);
                return null;
            }
        });
        // Multiple call nodes
        runtime.createDirectCallNode(callTarget);
        runtime.createDirectCallNode(callTarget);
        final DirectCallNode directCallNode = runtime.createDirectCallNode(callTarget);

        directCallNode.call(new Object[]{0});
        Assert.assertFalse("Target needs split after first execution", getNeedsSplit(callTarget));
    }
}
