/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.graal.api.replacements.MethodSubstitution;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.GuardsStage;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.FrameStateAssignmentPhase;
import com.oracle.graal.phases.common.GuardLoweringPhase;
import com.oracle.graal.phases.common.LoweringPhase;
import com.oracle.graal.phases.common.RemoveValueProxyPhase;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.replacements.Snippet;

/**
 * Macro nodes can be used to temporarily replace an invoke. They can, for example, be used to
 * implement constant folding for known JDK functions like {@link Class#isInterface()}.<br/>
 * <br/>
 * During lowering, multiple sources are queried in order to look for a replacement:
 * <ul>
 * <li>If {@link #getLoweredSnippetGraph(LoweringTool)} returns a non-null result, this graph is
 * used as a replacement.</li>
 * <li>If a {@link MethodSubstitution} for the target method is found, this substitution is used as
 * a replacement.</li>
 * <li>Otherwise, the macro node is replaced with an {@link InvokeNode}. Note that this is only
 * possible if the macro node is a {@link MacroStateSplitNode}.</li>
 * </ul>
 */
@NodeInfo
public abstract class MacroNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<MacroNode> TYPE = NodeClass.create(MacroNode.class);
    @Input protected NodeInputList<ValueNode> arguments;

    protected final int bci;
    protected final ResolvedJavaMethod targetMethod;
    protected final JavaType returnType;
    protected final InvokeKind invokeKind;

    protected MacroNode(NodeClass<? extends MacroNode> c, InvokeKind invokeKind, ResolvedJavaMethod targetMethod, int bci, JavaType returnType, ValueNode... arguments) {
        super(c, returnStamp(returnType));
        assert targetMethod.getSignature().getParameterCount(!targetMethod.isStatic()) == arguments.length;
        this.arguments = new NodeInputList<>(this, arguments);
        this.bci = bci;
        this.targetMethod = targetMethod;
        this.returnType = returnType;
        this.invokeKind = invokeKind;
        assert !isPlaceholderBci(bci);
    }

    private static Stamp returnStamp(JavaType returnType) {
        JavaKind kind = returnType.getJavaKind();
        if (kind == JavaKind.Object) {
            return StampFactory.declared((ResolvedJavaType) returnType);
        } else {
            return StampFactory.forKind(kind);
        }
    }

    public int getBci() {
        return bci;
    }

    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    public JavaType getReturnType() {
        return returnType;
    }

    protected FrameState stateAfter() {
        return null;
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
        final PhaseContext c = new PhaseContext(tool.getMetaAccess(), tool.getConstantReflection(), tool.getLowerer(), tool.getReplacements(), tool.getStampProvider());
        if (!graph().hasValueProxies()) {
            new RemoveValueProxyPhase().apply(replacementGraph);
        }
        GuardsStage guardsStage = graph().getGuardsStage();
        if (!guardsStage.allowsFloatingGuards()) {
            new GuardLoweringPhase().apply(replacementGraph, null);
            if (guardsStage.areFrameStatesAtDeopts()) {
                new FrameStateAssignmentPhase().apply(replacementGraph);
            }
        }
        try (Scope s = Debug.scope("LoweringSnippetTemplate", replacementGraph)) {
            new LoweringPhase(new CanonicalizerPhase(), tool.getLoweringStage()).apply(replacementGraph, c);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        return replacementGraph;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph replacementGraph = getLoweredSnippetGraph(tool);

        InvokeNode invoke = replaceWithInvoke();
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
            InliningUtil.inline(invoke, replacementGraph, false, null);
            Debug.dump(graph(), "After inlining replacement %s", replacementGraph);
        } else {
            if (isPlaceholderBci(invoke.bci())) {
                throw new JVMCIError("%s: cannot lower to invoke with placeholder BCI: %s", graph(), this);
            }

            if (invoke.stateAfter() == null) {
                ResolvedJavaMethod method = graph().method();
                if (method.getAnnotation(MethodSubstitution.class) != null || method.getAnnotation(Snippet.class) != null) {
                    // One cause for this is that a MacroNode is created for a method that
                    // no longer needs a MacroNode. For example, Class.getComponentType()
                    // only needs a MacroNode prior to JDK9 as it was given a non-native
                    // implementation in JDK9.
                    throw new JVMCIError("%s macro created for call to %s in %s must be lowerable to a snippet or intrinsic graph. "
                                    + "Maybe a macro node is not needed for this method in the current JDK?", getClass().getSimpleName(), targetMethod.format("%h.%n(%p)"), graph());
                }
                throw new JVMCIError("%s: cannot lower to invoke without state: %s", graph(), this);
            }
            invoke.lower(tool);
        }
    }

    protected InvokeNode replaceWithInvoke() {
        InvokeNode invoke = createInvoke();
        graph().replaceFixedWithFixed(this, invoke);
        return invoke;
    }

    protected InvokeNode createInvoke() {
        MethodCallTargetNode callTarget = graph().add(new MethodCallTargetNode(invokeKind, targetMethod, arguments.toArray(new ValueNode[arguments.size()]), returnType, null));
        InvokeNode invoke = graph().add(new InvokeNode(callTarget, bci));
        if (stateAfter() != null) {
            invoke.setStateAfter(stateAfter().duplicate());
            if (getStackKind() != JavaKind.Void) {
                invoke.stateAfter().replaceFirstInput(this, invoke);
            }
        }
        return invoke;
    }
}
