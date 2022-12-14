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
package org.graalvm.compiler.replacements.nodes;

import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This duplicates a {@link MacroStateSplitNode} using {@link WithExceptionNode} as a base class.
 *
 * See the documentation of {@link MacroInvokable} for more information.
 *
 * @see MacroNode
 * @see MacroStateSplitNode
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate",
          size = SIZE_UNKNOWN,
          sizeRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate")
//@formatter:on
public abstract class MacroStateSplitWithExceptionNode extends WithExceptionNode implements Lowerable, MacroInvokable, StateSplit, SingleMemoryKill {

    public static final NodeClass<MacroStateSplitWithExceptionNode> TYPE = NodeClass.create(MacroStateSplitWithExceptionNode.class);
    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    protected final int bci;
    protected final ResolvedJavaMethod callerMethod;
    protected final ResolvedJavaMethod targetMethod;
    protected final InvokeKind invokeKind;
    protected final StampPair returnStamp;

    protected MacroStateSplitWithExceptionNode(NodeClass<? extends MacroStateSplitWithExceptionNode> c, MacroParams p) {
        super(c, p.returnStamp != null ? p.returnStamp.getTrustedStamp() : null);
        this.arguments = new NodeInputList<>(this, p.arguments);
        this.bci = p.bci;
        this.callerMethod = p.callerMethod;
        this.targetMethod = p.targetMethod;
        this.returnStamp = p.returnStamp;
        this.invokeKind = p.invokeKind;
        assert !isPlaceholderBci(p.bci);
        assert MacroInvokable.assertArgumentCount(this);
    }

    @Override
    public ResolvedJavaMethod getContextMethod() {
        return callerMethod;
    }

    @Override
    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int bci) {
        // nothing to do here, macro nodes get bci during construction
    }

    @Override
    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    @Override
    public InvokeKind getInvokeKind() {
        return invokeKind;
    }

    @Override
    protected void afterClone(Node other) {
        updateInliningLogAfterClone(other);
    }

    @Override
    public FixedNode asFixedNode() {
        return this;
    }

    /**
     * Gets a snippet to be used for lowering this macro node. The returned graph (if non-null) must
     * have been {@linkplain #lowerReplacement(StructuredGraph, LoweringTool) lowered}.
     */
    @SuppressWarnings("unused")
    protected StructuredGraph getLoweredSnippetGraph(LoweringTool tool) {
        return null;
    }

    /**
     * Applies {@linkplain LoweringPhase lowering} to a replacement graph.
     *
     * @param replacementGraph a replacement (i.e., snippet or method substitution) graph
     */
    @SuppressWarnings("try")
    protected StructuredGraph lowerReplacement(final StructuredGraph replacementGraph, LoweringTool tool) {
        if (graph().isAfterStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            new RemoveValueProxyPhase().apply(replacementGraph);
        }
        GuardsStage guardsStage = graph().getGuardsStage();
        if (!guardsStage.allowsFloatingGuards()) {
            new GuardLoweringPhase().apply(replacementGraph, null);
            if (guardsStage.areFrameStatesAtDeopts()) {
                new FrameStateAssignmentPhase().apply(replacementGraph);
            }
        }
        DebugContext debug = replacementGraph.getDebug();
        try (DebugContext.Scope s = debug.scope("LoweringSnippetTemplate", replacementGraph)) {
            new LoweringPhase(CanonicalizerPhase.create(), tool.getLoweringStage()).apply(replacementGraph, tool);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        return replacementGraph;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph replacementGraph = getLoweredSnippetGraph(tool);

        InvokeWithExceptionNode invoke = replaceWithInvoke();
        assert invoke.verify();

        if (replacementGraph != null) {
            // Pull out the receiver null check so that a replaced
            // receiver can be lowered if necessary
            if (!targetMethod.isStatic()) {
                ValueNode nonNullReceiver = InliningUtil.nonNullReceiver(invoke);
                if (nonNullReceiver instanceof Lowerable) {
                    ((Lowerable) nonNullReceiver).lower(tool);
                }
            }
            InliningUtil.inline(invoke, replacementGraph, false, targetMethod, "Replace with graph.", "LoweringPhase");
            replacementGraph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph(), "After inlining replacement %s", replacementGraph);
        } else {
            if (isPlaceholderBci(invoke.bci())) {
                throw new GraalError("%s: cannot lower to invoke with placeholder BCI: %s", graph(), this);
            }

            if (invoke.stateAfter() == null) {
                throw new GraalError("%s: cannot lower to invoke without state: %s", graph(), this);
            }
            invoke.lower(tool);
        }
    }

    @SuppressWarnings("try")
    public InvokeWithExceptionNode replaceWithInvoke() {
        try (DebugCloseable context = withNodeSourcePosition()) {
            InvokeWithExceptionNode invoke = createInvoke();
            graph().replaceWithExceptionSplit(this, invoke);
            return invoke;
        }
    }

    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.any();
    }

    /**
     * Create invoke. This node is not modified. The exception edge is not yet set.
     */
    protected InvokeWithExceptionNode createInvoke() {
        MethodCallTargetNode callTarget = graph().add(new MethodCallTargetNode(invokeKind, targetMethod, getArguments().toArray(new ValueNode[arguments.size()]), returnStamp, null));
        InvokeWithExceptionNode invoke = graph().add(new InvokeWithExceptionNode(callTarget, null, bci));
        if (stateAfter() != null) {
            invoke.setStateAfter(stateAfter().duplicate());
            if (getStackKind() != JavaKind.Void) {
                invoke.stateAfter().replaceFirstInput(this, invoke);
            }
        }
        return invoke;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

}
