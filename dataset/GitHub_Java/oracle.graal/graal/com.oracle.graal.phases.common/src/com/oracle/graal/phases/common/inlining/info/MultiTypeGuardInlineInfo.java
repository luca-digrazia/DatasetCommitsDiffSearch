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
package com.oracle.graal.phases.common.inlining.info;

import static com.oracle.graal.compiler.common.GraalOptions.UseGraalQueries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.internal.jvmci.meta.DeoptimizationAction;
import jdk.internal.jvmci.meta.DeoptimizationReason;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.JavaTypeProfile.ProfiledType;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;
import jdk.internal.jvmci.meta.ResolvedJavaType;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.BeginNode;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.DeoptimizeNode;
import com.oracle.graal.nodes.EndNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.GuardedValueNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.InvokeWithExceptionNode;
import com.oracle.graal.nodes.MergeNode;
import com.oracle.graal.nodes.PhiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.extended.LoadHubNode;
import com.oracle.graal.nodes.java.ExceptionObjectNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.java.TypeSwitchNode;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.common.inlining.info.elem.Inlineable;
import com.oracle.graal.phases.util.Providers;

/**
 * Polymorphic inlining of m methods with n type checks (n &ge; m) in case that the profiling
 * information suggests a reasonable amount of different receiver types and different methods. If an
 * unknown type is encountered a deoptimization is triggered.
 */
public class MultiTypeGuardInlineInfo extends AbstractInlineInfo {

    private final List<ResolvedJavaMethod> concretes;
    private final double[] methodProbabilities;
    private final double maximumMethodProbability;
    private final ArrayList<Integer> typesToConcretes;
    private final ArrayList<ProfiledType> ptypes;
    private final double notRecordedTypeProbability;
    private final Inlineable[] inlineableElements;

    public MultiTypeGuardInlineInfo(Invoke invoke, ArrayList<ResolvedJavaMethod> concretes, ArrayList<ProfiledType> ptypes, ArrayList<Integer> typesToConcretes, double notRecordedTypeProbability) {
        super(invoke);
        assert concretes.size() > 0 : "must have at least one method";
        assert ptypes.size() == typesToConcretes.size() : "array lengths must match";

        this.concretes = concretes;
        this.ptypes = ptypes;
        this.typesToConcretes = typesToConcretes;
        this.notRecordedTypeProbability = notRecordedTypeProbability;
        this.inlineableElements = new Inlineable[concretes.size()];
        this.methodProbabilities = computeMethodProbabilities();
        this.maximumMethodProbability = maximumMethodProbability();
        assert maximumMethodProbability > 0;
        assert assertUniqueTypes(ptypes);
    }

    private static boolean assertUniqueTypes(ArrayList<ProfiledType> ptypes) {
        Set<ResolvedJavaType> set = new HashSet<>();
        for (ProfiledType ptype : ptypes) {
            set.add(ptype.getType());
        }
        return set.size() == ptypes.size();
    }

    private double[] computeMethodProbabilities() {
        double[] result = new double[concretes.size()];
        for (int i = 0; i < typesToConcretes.size(); i++) {
            int concrete = typesToConcretes.get(i);
            double probability = ptypes.get(i).getProbability();
            result[concrete] += probability;
        }
        return result;
    }

    private double maximumMethodProbability() {
        double max = 0;
        for (int i = 0; i < methodProbabilities.length; i++) {
            max = Math.max(max, methodProbabilities[i]);
        }
        return max;
    }

    @Override
    public int numberOfMethods() {
        return concretes.size();
    }

    @Override
    public ResolvedJavaMethod methodAt(int index) {
        assert index >= 0 && index < concretes.size();
        return concretes.get(index);
    }

    @Override
    public Inlineable inlineableElementAt(int index) {
        assert index >= 0 && index < concretes.size();
        return inlineableElements[index];
    }

    @Override
    public double probabilityAt(int index) {
        return methodProbabilities[index];
    }

    @Override
    public double relevanceAt(int index) {
        return probabilityAt(index) / maximumMethodProbability;
    }

    @Override
    public void setInlinableElement(int index, Inlineable inlineableElement) {
        assert index >= 0 && index < concretes.size();
        inlineableElements[index] = inlineableElement;
    }

    @Override
    public Collection<Node> inline(Providers providers) {
        if (hasSingleMethod()) {
            return inlineSingleMethod(graph(), providers.getStampProvider());
        } else {
            return inlineMultipleMethods(graph(), providers);
        }
    }

    public boolean shouldInline() {
        for (ResolvedJavaMethod method : concretes) {
            if (method.shouldBeInlined()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSingleMethod() {
        return concretes.size() == 1 && !shouldFallbackToInvoke();
    }

    private boolean shouldFallbackToInvoke() {
        return notRecordedTypeProbability > 0;
    }

    private Collection<Node> inlineMultipleMethods(StructuredGraph graph, Providers providers) {
        int numberOfMethods = concretes.size();
        FixedNode continuation = invoke.next();

        // setup merge and phi nodes for results and exceptions
        AbstractMergeNode returnMerge = graph.add(new MergeNode());
        returnMerge.setStateAfter(invoke.stateAfter());

        PhiNode returnValuePhi = null;
        if (invoke.asNode().getStackKind() != JavaKind.Void) {
            returnValuePhi = graph.addWithoutUnique(new ValuePhiNode(invoke.asNode().stamp().unrestricted(), returnMerge));
        }

        AbstractMergeNode exceptionMerge = null;
        PhiNode exceptionObjectPhi = null;
        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
            ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithException.exceptionEdge();

            exceptionMerge = graph.add(new MergeNode());

            FixedNode exceptionSux = exceptionEdge.next();
            graph.addBeforeFixed(exceptionSux, exceptionMerge);
            exceptionObjectPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(JavaKind.Object), exceptionMerge));
            exceptionMerge.setStateAfter(exceptionEdge.stateAfter().duplicateModified(invoke.stateAfter().bci, true, JavaKind.Object, new JavaKind[]{JavaKind.Object},
                            new ValueNode[]{exceptionObjectPhi}));
        }

        // create one separate block for each invoked method
        AbstractBeginNode[] successors = new AbstractBeginNode[numberOfMethods + 1];
        for (int i = 0; i < numberOfMethods; i++) {
            successors[i] = createInvocationBlock(graph, invoke, returnMerge, returnValuePhi, exceptionMerge, exceptionObjectPhi, true);
        }

        // create the successor for an unknown type
        FixedNode unknownTypeSux;
        if (shouldFallbackToInvoke()) {
            unknownTypeSux = createInvocationBlock(graph, invoke, returnMerge, returnValuePhi, exceptionMerge, exceptionObjectPhi, false);
        } else {
            unknownTypeSux = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TypeCheckedInliningViolated));
        }
        successors[successors.length - 1] = BeginNode.begin(unknownTypeSux);

        // replace the invoke exception edge
        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) invoke;
            ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithExceptionNode.exceptionEdge();
            exceptionEdge.replaceAtUsages(exceptionObjectPhi);
            exceptionEdge.setNext(null);
            GraphUtil.killCFG(invokeWithExceptionNode.exceptionEdge());
        }

        assert invoke.asNode().isAlive();

        // replace the invoke with a switch on the type of the actual receiver
        boolean methodDispatch = createDispatchOnTypeBeforeInvoke(graph, successors, false, providers.getStampProvider());

        assert invoke.next() == continuation;
        invoke.setNext(null);
        returnMerge.setNext(continuation);
        if (UseGraalQueries.getValue()) {
            InliningUtil.removeAttachedInstrumentation(invoke);
        }
        if (returnValuePhi != null) {
            invoke.asNode().replaceAtUsages(returnValuePhi);
        }
        invoke.asNode().safeDelete();

        ArrayList<GuardedValueNode> replacementNodes = new ArrayList<>();

        // prepare the anchors for the invokes
        for (int i = 0; i < numberOfMethods; i++) {
            AbstractBeginNode node = successors[i];
            Invoke invokeForInlining = (Invoke) node.next();

            ResolvedJavaType commonType;
            if (methodDispatch) {
                commonType = concretes.get(i).getDeclaringClass();
            } else {
                commonType = getLeastCommonType(i);
            }

            ValueNode receiver = ((MethodCallTargetNode) invokeForInlining.callTarget()).receiver();
            boolean exact = (getTypeCount(i) == 1 && !methodDispatch);
            GuardedValueNode anchoredReceiver = InliningUtil.createAnchoredReceiver(graph, node, commonType, receiver, exact);
            invokeForInlining.callTarget().replaceFirstInput(receiver, anchoredReceiver);

            assert !anchoredReceiver.isDeleted() : anchoredReceiver;
            replacementNodes.add(anchoredReceiver);
        }
        if (shouldFallbackToInvoke()) {
            replacementNodes.add(null);
        }

        Collection<Node> canonicalizeNodes = new ArrayList<>();
        // do the actual inlining for every invoke
        for (int i = 0; i < numberOfMethods; i++) {
            Invoke invokeForInlining = (Invoke) successors[i].next();
            canonicalizeNodes.addAll(inline(invokeForInlining, methodAt(i), inlineableElementAt(i), false));
        }
        if (returnValuePhi != null) {
            canonicalizeNodes.add(returnValuePhi);
        }
        return canonicalizeNodes;
    }

    private int getTypeCount(int concreteMethodIndex) {
        int count = 0;
        for (int i = 0; i < typesToConcretes.size(); i++) {
            if (typesToConcretes.get(i) == concreteMethodIndex) {
                count++;
            }
        }
        return count;
    }

    private ResolvedJavaType getLeastCommonType(int concreteMethodIndex) {
        ResolvedJavaType commonType = null;
        for (int i = 0; i < typesToConcretes.size(); i++) {
            if (typesToConcretes.get(i) == concreteMethodIndex) {
                if (commonType == null) {
                    commonType = ptypes.get(i).getType();
                } else {
                    commonType = commonType.findLeastCommonAncestor(ptypes.get(i).getType());
                }
            }
        }
        assert commonType != null;
        return commonType;
    }

    private ResolvedJavaType getLeastCommonType() {
        ResolvedJavaType result = getLeastCommonType(0);
        for (int i = 1; i < concretes.size(); i++) {
            result = result.findLeastCommonAncestor(getLeastCommonType(i));
        }
        return result;
    }

    private Collection<Node> inlineSingleMethod(StructuredGraph graph, StampProvider stampProvider) {
        assert concretes.size() == 1 && inlineableElements.length == 1 && ptypes.size() > 1 && !shouldFallbackToInvoke() && notRecordedTypeProbability == 0;

        AbstractBeginNode calleeEntryNode = graph.add(new BeginNode());

        AbstractBeginNode unknownTypeSux = createUnknownTypeSuccessor(graph);
        AbstractBeginNode[] successors = new AbstractBeginNode[]{calleeEntryNode, unknownTypeSux};
        createDispatchOnTypeBeforeInvoke(graph, successors, false, stampProvider);

        calleeEntryNode.setNext(invoke.asNode());

        return inline(invoke, methodAt(0), inlineableElementAt(0), false);
    }

    private boolean createDispatchOnTypeBeforeInvoke(StructuredGraph graph, AbstractBeginNode[] successors, boolean invokeIsOnlySuccessor, StampProvider stampProvider) {
        assert ptypes.size() >= 1;
        ValueNode nonNullReceiver = InliningUtil.nonNullReceiver(invoke);
        LoadHubNode hub = graph.unique(new LoadHubNode(stampProvider, nonNullReceiver));

        Debug.log("Type switch with %d types", concretes.size());

        ResolvedJavaType[] keys = new ResolvedJavaType[ptypes.size()];
        double[] keyProbabilities = new double[ptypes.size() + 1];
        int[] keySuccessors = new int[ptypes.size() + 1];
        double totalProbability = notRecordedTypeProbability;
        for (int i = 0; i < ptypes.size(); i++) {
            keys[i] = ptypes.get(i).getType();
            keyProbabilities[i] = ptypes.get(i).getProbability();
            totalProbability += keyProbabilities[i];
            keySuccessors[i] = invokeIsOnlySuccessor ? 0 : typesToConcretes.get(i);
            assert keySuccessors[i] < successors.length - 1 : "last successor is the unknownTypeSux";
        }
        keyProbabilities[keyProbabilities.length - 1] = notRecordedTypeProbability;
        keySuccessors[keySuccessors.length - 1] = successors.length - 1;

        // Normalize the probabilities.
        for (int i = 0; i < keyProbabilities.length; i++) {
            keyProbabilities[i] /= totalProbability;
        }

        TypeSwitchNode typeSwitch = graph.add(new TypeSwitchNode(hub, successors, keys, keyProbabilities, keySuccessors));
        FixedWithNextNode pred = (FixedWithNextNode) invoke.asNode().predecessor();
        pred.setNext(typeSwitch);
        return false;
    }

    private static AbstractBeginNode createInvocationBlock(StructuredGraph graph, Invoke invoke, AbstractMergeNode returnMerge, PhiNode returnValuePhi, AbstractMergeNode exceptionMerge,
                    PhiNode exceptionObjectPhi, boolean useForInlining) {
        Invoke duplicatedInvoke = duplicateInvokeForInlining(graph, invoke, exceptionMerge, exceptionObjectPhi, useForInlining);
        AbstractBeginNode calleeEntryNode = graph.add(new BeginNode());
        calleeEntryNode.setNext(duplicatedInvoke.asNode());

        EndNode endNode = graph.add(new EndNode());
        duplicatedInvoke.setNext(endNode);
        returnMerge.addForwardEnd(endNode);

        if (returnValuePhi != null) {
            returnValuePhi.addInput(duplicatedInvoke.asNode());
        }
        return calleeEntryNode;
    }

    private static Invoke duplicateInvokeForInlining(StructuredGraph graph, Invoke invoke, AbstractMergeNode exceptionMerge, PhiNode exceptionObjectPhi, boolean useForInlining) {
        Invoke result = (Invoke) invoke.asNode().copyWithInputs();
        Node callTarget = result.callTarget().copyWithInputs();
        result.asNode().replaceFirstInput(result.callTarget(), callTarget);
        result.setUseForInlining(useForInlining);

        JavaKind kind = invoke.asNode().getStackKind();
        if (kind != JavaKind.Void) {
            FrameState stateAfter = invoke.stateAfter();
            stateAfter = stateAfter.duplicate(stateAfter.bci);
            stateAfter.replaceFirstInput(invoke.asNode(), result.asNode());
            result.setStateAfter(stateAfter);
        }

        if (invoke instanceof InvokeWithExceptionNode) {
            assert exceptionMerge != null && exceptionObjectPhi != null;

            InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
            ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithException.exceptionEdge();
            FrameState stateAfterException = exceptionEdge.stateAfter();

            ExceptionObjectNode newExceptionEdge = (ExceptionObjectNode) exceptionEdge.copyWithInputs();
            // set new state (pop old exception object, push new one)
            newExceptionEdge.setStateAfter(stateAfterException.duplicateModified(JavaKind.Object, JavaKind.Object, newExceptionEdge));

            EndNode endNode = graph.add(new EndNode());
            newExceptionEdge.setNext(endNode);
            exceptionMerge.addForwardEnd(endNode);
            exceptionObjectPhi.addInput(newExceptionEdge);

            ((InvokeWithExceptionNode) result).setExceptionEdge(newExceptionEdge);
        }
        return result;
    }

    @Override
    public void tryToDevirtualizeInvoke(Providers providers) {
        if (hasSingleMethod()) {
            devirtualizeWithTypeSwitch(graph(), InvokeKind.Special, concretes.get(0), providers.getStampProvider());
        } else {
            tryToDevirtualizeMultipleMethods(graph(), providers.getStampProvider());
        }
    }

    private void tryToDevirtualizeMultipleMethods(StructuredGraph graph, StampProvider stampProvider) {
        MethodCallTargetNode methodCallTarget = (MethodCallTargetNode) invoke.callTarget();
        if (methodCallTarget.invokeKind() == InvokeKind.Interface) {
            ResolvedJavaMethod targetMethod = methodCallTarget.targetMethod();
            ResolvedJavaType leastCommonType = getLeastCommonType();
            ResolvedJavaType contextType = invoke.getContextType();
            // check if we have a common base type that implements the interface -> in that case
            // we have a vtable entry for the interface method and can use a less expensive
            // virtual call
            if (!leastCommonType.isInterface() && targetMethod.getDeclaringClass().isAssignableFrom(leastCommonType)) {
                ResolvedJavaMethod baseClassTargetMethod = leastCommonType.resolveConcreteMethod(targetMethod, contextType);
                if (baseClassTargetMethod != null) {
                    devirtualizeWithTypeSwitch(graph, InvokeKind.Virtual, leastCommonType.resolveConcreteMethod(targetMethod, contextType), stampProvider);
                }
            }
        }
    }

    private void devirtualizeWithTypeSwitch(StructuredGraph graph, InvokeKind kind, ResolvedJavaMethod target, StampProvider stampProvider) {
        AbstractBeginNode invocationEntry = graph.add(new BeginNode());
        AbstractBeginNode unknownTypeSux = createUnknownTypeSuccessor(graph);
        AbstractBeginNode[] successors = new AbstractBeginNode[]{invocationEntry, unknownTypeSux};
        createDispatchOnTypeBeforeInvoke(graph, successors, true, stampProvider);

        invocationEntry.setNext(invoke.asNode());
        ValueNode receiver = ((MethodCallTargetNode) invoke.callTarget()).receiver();
        GuardedValueNode anchoredReceiver = InliningUtil.createAnchoredReceiver(graph, invocationEntry, target.getDeclaringClass(), receiver, false);
        invoke.callTarget().replaceFirstInput(receiver, anchoredReceiver);
        InliningUtil.replaceInvokeCallTarget(invoke, graph, kind, target);
    }

    private static AbstractBeginNode createUnknownTypeSuccessor(StructuredGraph graph) {
        return BeginNode.begin(graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TypeCheckedInliningViolated)));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(shouldFallbackToInvoke() ? "megamorphic" : "polymorphic");
        builder.append(", ");
        builder.append(concretes.size());
        builder.append(" methods [ ");
        for (int i = 0; i < concretes.size(); i++) {
            builder.append(concretes.get(i).format("  %H.%n(%p):%r"));
        }
        builder.append(" ], ");
        builder.append(ptypes.size());
        builder.append(" type checks [ ");
        for (int i = 0; i < ptypes.size(); i++) {
            builder.append("  ");
            builder.append(ptypes.get(i).getType().getName());
            builder.append(ptypes.get(i).getProbability());
        }
        builder.append(" ]");
        return builder.toString();
    }
}
