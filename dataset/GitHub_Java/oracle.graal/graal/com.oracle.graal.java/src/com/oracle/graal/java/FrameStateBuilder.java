/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import static com.oracle.graal.bytecode.Bytecodes.DUP;
import static com.oracle.graal.bytecode.Bytecodes.DUP2;
import static com.oracle.graal.bytecode.Bytecodes.DUP2_X1;
import static com.oracle.graal.bytecode.Bytecodes.DUP2_X2;
import static com.oracle.graal.bytecode.Bytecodes.DUP_X1;
import static com.oracle.graal.bytecode.Bytecodes.DUP_X2;
import static com.oracle.graal.bytecode.Bytecodes.POP;
import static com.oracle.graal.bytecode.Bytecodes.POP2;
import static com.oracle.graal.bytecode.Bytecodes.SWAP;
import static com.oracle.graal.java.BytecodeParserOptions.HideSubstitutionStates;
import static com.oracle.graal.nodes.FrameState.TWO_SLOT_MARKER;
import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.compiler.common.type.StampPair;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.NodeSourcePosition;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.nodeinfo.Verbosity;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.LoopBeginNode;
import com.oracle.graal.nodes.LoopExitNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.PhiNode;
import com.oracle.graal.nodes.ProxyNode;
import com.oracle.graal.nodes.StateSplit;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.IntrinsicContext.SideEffectsState;
import com.oracle.graal.nodes.graphbuilderconf.ParameterPlugin;
import com.oracle.graal.nodes.graphbuilderconf.TypePlugin;
import com.oracle.graal.nodes.java.MonitorIdNode;
import com.oracle.graal.nodes.util.GraphUtil;

public final class FrameStateBuilder implements SideEffectsState {

    private static final ValueNode[] EMPTY_ARRAY = new ValueNode[0];
    private static final MonitorIdNode[] EMPTY_MONITOR_ARRAY = new MonitorIdNode[0];

    private final BytecodeParser parser;
    private final ResolvedJavaMethod method;
    private int stackSize;
    protected final ValueNode[] locals;
    protected final ValueNode[] stack;
    private ValueNode[] lockedObjects;
    private boolean canVerifyKind;

    /**
     * @see BytecodeFrame#rethrowException
     */
    private boolean rethrowException;

    private MonitorIdNode[] monitorIds;
    private final StructuredGraph graph;
    private FrameState outerFrameState;

    /**
     * The closest {@link StateSplit#hasSideEffect() side-effect} predecessors. There will be more
     * than one when the current block contains no side-effects but merging predecessor blocks do.
     */
    private List<StateSplit> sideEffects;

    private JavaConstant constantReceiver;

    /**
     * Creates a new frame state builder for the given method and the given target graph.
     *
     * @param method the method whose frame is simulated
     * @param graph the target graph of Graal nodes created by the builder
     */
    public FrameStateBuilder(BytecodeParser parser, ResolvedJavaMethod method, StructuredGraph graph) {
        this.parser = parser;
        this.method = method;
        this.locals = allocateArray(method.getMaxLocals());
        this.stack = allocateArray(Math.max(1, method.getMaxStackSize()));
        this.lockedObjects = allocateArray(0);

        assert graph != null;

        this.monitorIds = EMPTY_MONITOR_ARRAY;
        this.graph = graph;
        this.canVerifyKind = true;
    }

    public void disableKindVerification() {
        canVerifyKind = false;
    }

    public void initializeFromArgumentsArray(ValueNode[] arguments) {

        int javaIndex = 0;
        int index = 0;
        if (!method.isStatic()) {
            // set the receiver
            locals[javaIndex] = arguments[index];
            javaIndex = 1;
            index = 1;
            constantReceiver = locals[0].asJavaConstant();
        }
        Signature sig = method.getSignature();
        int max = sig.getParameterCount(false);
        for (int i = 0; i < max; i++) {
            JavaKind kind = sig.getParameterKind(i);
            locals[javaIndex] = arguments[index];
            javaIndex++;
            if (kind.needsTwoSlots()) {
                locals[javaIndex] = TWO_SLOT_MARKER;
                javaIndex++;
            }
            index++;
        }
    }

    public void initializeForMethodStart(Assumptions assumptions, boolean eagerResolve, Plugins plugins) {

        int javaIndex = 0;
        int index = 0;
        ResolvedJavaType originalType = method.getDeclaringClass();
        if (!method.isStatic()) {
            // add the receiver
            FloatingNode receiver = null;
            StampPair receiverStamp = null;
            if (plugins != null) {
                for (TypePlugin plugin : plugins.getTypePlugins()) {
                    receiverStamp = plugin.interceptType(parser, originalType, true);
                    if (receiverStamp != null) {
                        break;
                    }
                }
            }
            if (receiverStamp == null) {
                receiverStamp = StampFactory.forDeclaredType(assumptions, originalType, true);
            }

            if (plugins != null) {
                for (ParameterPlugin plugin : plugins.getParameterPlugins()) {
                    receiver = plugin.interceptParameter(parser, index, receiverStamp);
                    if (receiver != null) {
                        break;
                    }
                }
            }
            if (receiver == null) {
                receiver = new ParameterNode(javaIndex, receiverStamp);
            }

            locals[javaIndex] = graph.unique(receiver);
            javaIndex = 1;
            index = 1;
        }
        Signature sig = method.getSignature();
        int max = sig.getParameterCount(false);
        ResolvedJavaType accessingClass = originalType;
        for (int i = 0; i < max; i++) {
            JavaType type = sig.getParameterType(i, accessingClass);
            if (eagerResolve) {
                type = type.resolve(accessingClass);
            }
            JavaKind kind = type.getJavaKind();
            StampPair stamp = null;
            if (plugins != null) {
                for (TypePlugin plugin : plugins.getTypePlugins()) {
                    stamp = plugin.interceptType(parser, type, false);
                    if (stamp != null) {
                        break;
                    }
                }
            }
            if (stamp == null) {
                stamp = StampFactory.forDeclaredType(assumptions, type, false);
            }

            FloatingNode param = null;
            if (plugins != null) {
                for (ParameterPlugin plugin : plugins.getParameterPlugins()) {
                    param = plugin.interceptParameter(parser, index, stamp);
                    if (param != null) {
                        break;
                    }
                }
            }
            if (param == null) {
                param = new ParameterNode(index, stamp);
            }

            locals[javaIndex] = graph.unique(param);
            javaIndex++;
            if (kind.needsTwoSlots()) {
                locals[javaIndex] = TWO_SLOT_MARKER;
                javaIndex++;
            }
            index++;
        }
    }

    private FrameStateBuilder(FrameStateBuilder other) {
        this.parser = other.parser;
        this.method = other.method;
        this.stackSize = other.stackSize;
        this.locals = other.locals.clone();
        this.stack = other.stack.clone();
        this.lockedObjects = other.lockedObjects.length == 0 ? other.lockedObjects : other.lockedObjects.clone();
        this.rethrowException = other.rethrowException;
        this.canVerifyKind = other.canVerifyKind;

        assert locals.length == method.getMaxLocals();
        assert stack.length == Math.max(1, method.getMaxStackSize());

        assert other.graph != null;
        graph = other.graph;
        monitorIds = other.monitorIds.length == 0 ? other.monitorIds : other.monitorIds.clone();

        assert locals.length == method.getMaxLocals();
        assert stack.length == Math.max(1, method.getMaxStackSize());
        assert lockedObjects.length == monitorIds.length;
    }

    private static ValueNode[] allocateArray(int length) {
        return length == 0 ? EMPTY_ARRAY : new ValueNode[length];
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[locals: [");
        for (int i = 0; i < locals.length; i++) {
            sb.append(i == 0 ? "" : ",").append(locals[i] == null ? "_" : locals[i] == TWO_SLOT_MARKER ? "#" : locals[i].toString(Verbosity.Id));
        }
        sb.append("] stack: [");
        for (int i = 0; i < stackSize; i++) {
            sb.append(i == 0 ? "" : ",").append(stack[i] == null ? "_" : stack[i] == TWO_SLOT_MARKER ? "#" : stack[i].toString(Verbosity.Id));
        }
        sb.append("] locks: [");
        for (int i = 0; i < lockedObjects.length; i++) {
            sb.append(i == 0 ? "" : ",").append(lockedObjects[i].toString(Verbosity.Id)).append(" / ").append(monitorIds[i].toString(Verbosity.Id));
        }
        sb.append("]");
        if (rethrowException) {
            sb.append(" rethrowException");
        }
        sb.append("]");
        return sb.toString();
    }

    public FrameState create(int bci, StateSplit forStateSplit) {
        if (parser != null && parser.parsingIntrinsic()) {
            return parser.intrinsicContext.createFrameState(parser.getGraph(), this, forStateSplit);
        }

        // Skip intrinsic frames
        return create(bci, parser != null ? parser.getNonIntrinsicAncestor() : null, false, null, null);
    }

    /**
     * @param pushedValues if non-null, values to {@link #push(JavaKind, ValueNode)} to the stack
     *            before creating the {@link FrameState}
     */
    public FrameState create(int bci, BytecodeParser parent, boolean duringCall, JavaKind[] pushedSlotKinds, ValueNode[] pushedValues) {
        if (outerFrameState == null && parent != null) {
            assert !parent.parsingIntrinsic() : "must already have the next non-intrinsic ancestor";
            outerFrameState = parent.getFrameStateBuilder().create(parent.bci(), parent.getNonIntrinsicAncestor(), true, null, null);
        }
        if (bci == BytecodeFrame.AFTER_EXCEPTION_BCI && parent != null) {
            FrameState newFrameState = outerFrameState.duplicateModified(outerFrameState.bci, true, false, JavaKind.Void, new JavaKind[]{JavaKind.Object}, new ValueNode[]{stack[0]});
            return newFrameState;
        }
        if (bci == BytecodeFrame.INVALID_FRAMESTATE_BCI) {
            throw shouldNotReachHere();
        }

        if (pushedValues != null) {
            assert pushedSlotKinds.length == pushedValues.length;
            int stackSizeToRestore = stackSize;
            for (int i = 0; i < pushedValues.length; i++) {
                push(pushedSlotKinds[i], pushedValues[i]);
            }
            FrameState res = graph.add(new FrameState(outerFrameState, method, bci, locals, stack, stackSize, lockedObjects, Arrays.asList(monitorIds), rethrowException, duringCall));
            stackSize = stackSizeToRestore;
            return res;
        } else {
            if (bci == BytecodeFrame.AFTER_EXCEPTION_BCI) {
                assert outerFrameState == null;
                clearLocals();
            }
            return graph.add(new FrameState(outerFrameState, method, bci, locals, stack, stackSize, lockedObjects, Arrays.asList(monitorIds), rethrowException, duringCall));
        }
    }

    public NodeSourcePosition createBytecodePosition(int bci) {
        BytecodeParser parent = parser.getParent();
        if (HideSubstitutionStates.getValue()) {
            if (parser.parsingIntrinsic()) {
                // Attribute to the method being replaced
                return new NodeSourcePosition(constantReceiver, parent.getFrameStateBuilder().createBytecodePosition(parent.bci()), parser.intrinsicContext.getOriginalMethod(), -1);
            }
            // Skip intrinsic frames
            parent = parser.getNonIntrinsicAncestor();
        }
        return create(null, constantReceiver, bci, parent);
    }

    private NodeSourcePosition create(NodeSourcePosition o, JavaConstant receiver, int bci, BytecodeParser parent) {
        NodeSourcePosition outer = o;
        if (outer == null && parent != null) {
            outer = parent.getFrameStateBuilder().createBytecodePosition(parent.bci());
        }
        if (bci == BytecodeFrame.AFTER_EXCEPTION_BCI && parent != null) {
            return FrameState.toSourcePosition(outerFrameState);
        }
        if (bci == BytecodeFrame.INVALID_FRAMESTATE_BCI) {
            throw shouldNotReachHere();
        }
        return new NodeSourcePosition(receiver, outer, method, bci);
    }

    public FrameStateBuilder copy() {
        return new FrameStateBuilder(this);
    }

    public boolean isCompatibleWith(FrameStateBuilder other) {
        assert method.equals(other.method) && graph == other.graph && localsSize() == other.localsSize() : "Can only compare frame states of the same method";
        assert lockedObjects.length == monitorIds.length && other.lockedObjects.length == other.monitorIds.length : "mismatch between lockedObjects and monitorIds";

        if (stackSize() != other.stackSize()) {
            return false;
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode x = stack[i];
            ValueNode y = other.stack[i];
            assert x != null && y != null;
            if (x != y && (x == TWO_SLOT_MARKER || x.isDeleted() || y == TWO_SLOT_MARKER || y.isDeleted() || x.getStackKind() != y.getStackKind())) {
                return false;
            }
        }
        if (lockedObjects.length != other.lockedObjects.length) {
            return false;
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            if (GraphUtil.originalValue(lockedObjects[i]) != GraphUtil.originalValue(other.lockedObjects[i]) || monitorIds[i] != other.monitorIds[i]) {
                throw new BailoutException("unbalanced monitors");
            }
        }
        return true;
    }

    public void merge(AbstractMergeNode block, FrameStateBuilder other) {
        assert isCompatibleWith(other);

        for (int i = 0; i < localsSize(); i++) {
            locals[i] = merge(locals[i], other.locals[i], block);
        }
        for (int i = 0; i < stackSize(); i++) {
            stack[i] = merge(stack[i], other.stack[i], block);
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            lockedObjects[i] = merge(lockedObjects[i], other.lockedObjects[i], block);
            assert monitorIds[i] == other.monitorIds[i];
        }

        if (sideEffects == null) {
            sideEffects = other.sideEffects;
        } else {
            if (other.sideEffects != null) {
                sideEffects.addAll(other.sideEffects);
            }
        }
    }

    private ValueNode merge(ValueNode currentValue, ValueNode otherValue, AbstractMergeNode block) {
        if (currentValue == null || currentValue.isDeleted()) {
            return null;
        } else if (block.isPhiAtMerge(currentValue)) {
            if (otherValue == null || otherValue == TWO_SLOT_MARKER || otherValue.isDeleted() || currentValue.getStackKind() != otherValue.getStackKind()) {
                // This phi must be dead anyway, add input of correct stack kind to keep the graph
                // invariants.
                ((PhiNode) currentValue).addInput(ConstantNode.defaultForKind(currentValue.getStackKind(), graph));
            } else {
                ((PhiNode) currentValue).addInput(otherValue);
            }
            return currentValue;
        } else if (currentValue != otherValue) {
            if (currentValue == TWO_SLOT_MARKER || otherValue == TWO_SLOT_MARKER) {
                return null;
            } else if (otherValue == null || otherValue.isDeleted() || currentValue.getStackKind() != otherValue.getStackKind()) {
                return null;
            }
            assert !(block instanceof LoopBeginNode) : String.format("Phi functions for loop headers are create eagerly for changed locals and all stack slots: %s != %s", currentValue, otherValue);
            return createValuePhi(currentValue, otherValue, block);
        } else {
            return currentValue;
        }
    }

    private ValuePhiNode createValuePhi(ValueNode currentValue, ValueNode otherValue, AbstractMergeNode block) {
        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(currentValue.stamp().unrestricted(), block));
        for (int i = 0; i < block.phiPredecessorCount(); i++) {
            phi.addInput(currentValue);
        }
        phi.addInput(otherValue);
        assert phi.valueCount() == block.phiPredecessorCount() + 1;
        return phi;
    }

    public void inferPhiStamps(AbstractMergeNode block) {
        for (int i = 0; i < localsSize(); i++) {
            inferPhiStamp(block, locals[i]);
        }
        for (int i = 0; i < stackSize(); i++) {
            inferPhiStamp(block, stack[i]);
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            inferPhiStamp(block, lockedObjects[i]);
        }
    }

    private static void inferPhiStamp(AbstractMergeNode block, ValueNode node) {
        if (block.isPhiAtMerge(node)) {
            node.inferStamp();
        }
    }

    public void insertLoopPhis(LocalLiveness liveness, int loopId, LoopBeginNode loopBegin, boolean forcePhis, boolean stampFromValueForForcedPhis) {
        for (int i = 0; i < localsSize(); i++) {
            boolean changedInLoop = liveness.localIsChangedInLoop(loopId, i);
            if (forcePhis || changedInLoop) {
                locals[i] = createLoopPhi(loopBegin, locals[i], stampFromValueForForcedPhis && !changedInLoop);
            }
        }
        for (int i = 0; i < stackSize(); i++) {
            stack[i] = createLoopPhi(loopBegin, stack[i], false);
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            lockedObjects[i] = createLoopPhi(loopBegin, lockedObjects[i], false);
        }
    }

    public void insertLoopProxies(LoopExitNode loopExit, FrameStateBuilder loopEntryState) {
        for (int i = 0; i < localsSize(); i++) {
            ValueNode value = locals[i];
            if (value != null && value != TWO_SLOT_MARKER && (!loopEntryState.contains(value) || loopExit.loopBegin().isPhiAtMerge(value))) {
                Debug.log(" inserting proxy for %s", value);
                locals[i] = ProxyNode.forValue(value, loopExit, graph);
            }
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode value = stack[i];
            if (value != null && value != TWO_SLOT_MARKER && (!loopEntryState.contains(value) || loopExit.loopBegin().isPhiAtMerge(value))) {
                Debug.log(" inserting proxy for %s", value);
                stack[i] = ProxyNode.forValue(value, loopExit, graph);
            }
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            ValueNode value = lockedObjects[i];
            if (value != null && (!loopEntryState.contains(value) || loopExit.loopBegin().isPhiAtMerge(value))) {
                Debug.log(" inserting proxy for %s", value);
                lockedObjects[i] = ProxyNode.forValue(value, loopExit, graph);
            }
        }
    }

    public void insertProxies(Function<ValueNode, ValueNode> proxyFunction) {
        for (int i = 0; i < localsSize(); i++) {
            ValueNode value = locals[i];
            if (value != null && value != TWO_SLOT_MARKER) {
                Debug.log(" inserting proxy for %s", value);
                locals[i] = proxyFunction.apply(value);
            }
        }
        for (int i = 0; i < stackSize(); i++) {
            ValueNode value = stack[i];
            if (value != null && value != TWO_SLOT_MARKER) {
                Debug.log(" inserting proxy for %s", value);
                stack[i] = proxyFunction.apply(value);
            }
        }
        for (int i = 0; i < lockedObjects.length; i++) {
            ValueNode value = lockedObjects[i];
            if (value != null) {
                Debug.log(" inserting proxy for %s", value);
                lockedObjects[i] = proxyFunction.apply(value);
            }
        }
    }

    private ValueNode createLoopPhi(AbstractMergeNode block, ValueNode value, boolean stampFromValue) {
        if (value == null || value == TWO_SLOT_MARKER) {
            return value;
        }
        assert !block.isPhiAtMerge(value) : "phi function for this block already created";

        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(stampFromValue ? value.stamp() : value.stamp().unrestricted(), block));
        phi.addInput(value);
        return phi;
    }

    /**
     * Adds a locked monitor to this frame state.
     *
     * @param object the object whose monitor will be locked.
     */
    public void pushLock(ValueNode object, MonitorIdNode monitorId) {
        assert object.isAlive() && object.getStackKind() == JavaKind.Object : "unexpected value: " + object;
        lockedObjects = Arrays.copyOf(lockedObjects, lockedObjects.length + 1);
        monitorIds = Arrays.copyOf(monitorIds, monitorIds.length + 1);
        lockedObjects[lockedObjects.length - 1] = object;
        monitorIds[monitorIds.length - 1] = monitorId;
        assert lockedObjects.length == monitorIds.length;
    }

    /**
     * Removes a locked monitor from this frame state.
     *
     * @return the object whose monitor was removed from the locks list.
     */
    public ValueNode popLock() {
        try {
            return lockedObjects[lockedObjects.length - 1];
        } finally {
            lockedObjects = lockedObjects.length == 1 ? EMPTY_ARRAY : Arrays.copyOf(lockedObjects, lockedObjects.length - 1);
            monitorIds = monitorIds.length == 1 ? EMPTY_MONITOR_ARRAY : Arrays.copyOf(monitorIds, monitorIds.length - 1);
            assert lockedObjects.length == monitorIds.length;
        }
    }

    public MonitorIdNode peekMonitorId() {
        return monitorIds[monitorIds.length - 1];
    }

    /**
     * @return the current lock depth
     */
    public int lockDepth(boolean includeParents) {
        int depth = lockedObjects.length;
        assert depth == monitorIds.length;
        if (includeParents && parser.getParent() != null) {
            depth += parser.getParent().frameState.lockDepth(true);
        }
        return depth;
    }

    public boolean contains(ValueNode value) {
        for (int i = 0; i < localsSize(); i++) {
            if (locals[i] == value) {
                return true;
            }
        }
        for (int i = 0; i < stackSize(); i++) {
            if (stack[i] == value) {
                return true;
            }
        }
        assert lockedObjects.length == monitorIds.length;
        for (int i = 0; i < lockedObjects.length; i++) {
            if (lockedObjects[i] == value || monitorIds[i] == value) {
                return true;
            }
        }
        return false;
    }

    public void clearNonLiveLocals(BciBlock block, LocalLiveness liveness, boolean liveIn) {
        /*
         * (lstadler) if somebody is tempted to remove/disable this clearing code: it's possible to
         * remove it for normal compilations, but not for OSR compilations - otherwise dead object
         * slots at the OSR entry aren't cleared. it is also not enough to rely on PiNodes with
         * Kind.Illegal, because the conflicting branch might not have been parsed.
         */
        if (!parser.graphBuilderConfig.clearNonLiveLocals()) {
            return;
        }
        if (liveIn) {
            for (int i = 0; i < locals.length; i++) {
                if (!liveness.localIsLiveIn(block, i)) {
                    assert locals[i] != TWO_SLOT_MARKER || locals[i - 1] == null : "Clearing of second slot must have cleared the first slot too";
                    locals[i] = null;
                }
            }
        } else {
            for (int i = 0; i < locals.length; i++) {
                if (!liveness.localIsLiveOut(block, i)) {
                    assert locals[i] != TWO_SLOT_MARKER || locals[i - 1] == null : "Clearing of second slot must have cleared the first slot too";
                    locals[i] = null;
                }
            }
        }
    }

    /**
     * Clears all local variables.
     */
    public void clearLocals() {
        for (int i = 0; i < locals.length; i++) {
            locals[i] = null;
        }
    }

    /**
     * @see BytecodeFrame#rethrowException
     */
    public boolean rethrowException() {
        return rethrowException;
    }

    /**
     * @see BytecodeFrame#rethrowException
     */
    public void setRethrowException(boolean b) {
        rethrowException = b;
    }

    /**
     * Returns the size of the local variables.
     *
     * @return the size of the local variables
     */
    public int localsSize() {
        return locals.length;
    }

    /**
     * Gets the current size (height) of the stack.
     */
    public int stackSize() {
        return stackSize;
    }

    private boolean verifyKind(JavaKind slotKind, ValueNode x) {
        assert x != null;
        assert x != TWO_SLOT_MARKER;
        assert slotKind.getSlotCount() > 0;

        if (canVerifyKind) {
            assert x.getStackKind() == slotKind.getStackKind();
        }
        return true;
    }

    /**
     * Loads the local variable at the specified index, checking that the returned value is non-null
     * and that two-stack values are properly handled.
     *
     * @param i the index of the local variable to load
     * @param slotKind the kind of the local variable from the point of view of the bytecodes
     * @return the instruction that produced the specified local
     */
    public ValueNode loadLocal(int i, JavaKind slotKind) {
        ValueNode x = locals[i];
        assert verifyKind(slotKind, x);
        assert slotKind.needsTwoSlots() ? locals[i + 1] == TWO_SLOT_MARKER : (i == locals.length - 1 || locals[i + 1] != TWO_SLOT_MARKER);
        return x;
    }

    /**
     * Stores a given local variable at the specified index. If the value occupies two slots, then
     * the next local variable index is also overwritten.
     *
     * @param i the index at which to store
     * @param slotKind the kind of the local variable from the point of view of the bytecodes
     * @param x the instruction which produces the value for the local
     */
    public void storeLocal(int i, JavaKind slotKind, ValueNode x) {
        assert verifyKind(slotKind, x);

        if (locals[i] == TWO_SLOT_MARKER) {
            /* Writing the second slot of a two-slot value invalidates the first slot. */
            locals[i - 1] = null;
        }
        locals[i] = x;
        if (slotKind.needsTwoSlots()) {
            /* Writing a two-slot value: mark the second slot. */
            locals[i + 1] = TWO_SLOT_MARKER;
        } else if (i < locals.length - 1 && locals[i + 1] == TWO_SLOT_MARKER) {
            /*
             * Writing a one-slot value to an index previously occupied by a two-slot value: clear
             * the old marker of the second slot.
             */
            locals[i + 1] = null;
        }
    }

    /**
     * Pushes an instruction onto the stack with the expected type.
     *
     * @param slotKind the kind of the stack element from the point of view of the bytecodes
     * @param x the instruction to push onto the stack
     */
    public void push(JavaKind slotKind, ValueNode x) {
        assert verifyKind(slotKind, x);

        xpush(x);
        if (slotKind.needsTwoSlots()) {
            xpush(TWO_SLOT_MARKER);
        }
    }

    public void pushReturn(JavaKind slotKind, ValueNode x) {
        if (slotKind != JavaKind.Void) {
            push(slotKind, x);
        }
    }

    /**
     * Pops an instruction off the stack with the expected type.
     *
     * @param slotKind the kind of the stack element from the point of view of the bytecodes
     * @return the instruction on the top of the stack
     */
    public ValueNode pop(JavaKind slotKind) {
        if (slotKind.needsTwoSlots()) {
            ValueNode s = xpop();
            assert s == TWO_SLOT_MARKER;
        }
        ValueNode x = xpop();
        assert verifyKind(slotKind, x);
        return x;
    }

    private void xpush(ValueNode x) {
        assert x != null;
        stack[stackSize++] = x;
    }

    private ValueNode xpop() {
        ValueNode result = stack[--stackSize];
        assert result != null;
        return result;
    }

    private ValueNode xpeek() {
        ValueNode result = stack[stackSize - 1];
        assert result != null;
        return result;
    }

    /**
     * Pop the specified number of slots off of this stack and return them as an array of
     * instructions.
     *
     * @return an array containing the arguments off of the stack
     */
    public ValueNode[] popArguments(int argSize) {
        ValueNode[] result = allocateArray(argSize);
        for (int i = argSize - 1; i >= 0; i--) {
            ValueNode x = xpop();
            if (x == TWO_SLOT_MARKER) {
                /* Ignore second slot of two-slot value. */
                x = xpop();
            }
            assert x != null && x != TWO_SLOT_MARKER;
            result[i] = x;
        }
        return result;
    }

    /**
     * Clears all values on this stack.
     */
    public void clearStack() {
        stackSize = 0;
    }

    /**
     * Performs a raw stack operation as defined in the Java bytecode specification.
     *
     * @param opcode The Java bytecode.
     */
    public void stackOp(int opcode) {
        switch (opcode) {
            case POP: {
                ValueNode w1 = xpop();
                assert w1 != TWO_SLOT_MARKER;
                break;
            }
            case POP2: {
                xpop();
                ValueNode w2 = xpop();
                assert w2 != TWO_SLOT_MARKER;
                break;
            }
            case DUP: {
                ValueNode w1 = xpeek();
                assert w1 != TWO_SLOT_MARKER;
                xpush(w1);
                break;
            }
            case DUP_X1: {
                ValueNode w1 = xpop();
                ValueNode w2 = xpop();
                assert w1 != TWO_SLOT_MARKER;
                xpush(w1);
                xpush(w2);
                xpush(w1);
                break;
            }
            case DUP_X2: {
                ValueNode w1 = xpop();
                ValueNode w2 = xpop();
                ValueNode w3 = xpop();
                assert w1 != TWO_SLOT_MARKER;
                xpush(w1);
                xpush(w3);
                xpush(w2);
                xpush(w1);
                break;
            }
            case DUP2: {
                ValueNode w1 = xpop();
                ValueNode w2 = xpop();
                xpush(w2);
                xpush(w1);
                xpush(w2);
                xpush(w1);
                break;
            }
            case DUP2_X1: {
                ValueNode w1 = xpop();
                ValueNode w2 = xpop();
                ValueNode w3 = xpop();
                xpush(w2);
                xpush(w1);
                xpush(w3);
                xpush(w2);
                xpush(w1);
                break;
            }
            case DUP2_X2: {
                ValueNode w1 = xpop();
                ValueNode w2 = xpop();
                ValueNode w3 = xpop();
                ValueNode w4 = xpop();
                xpush(w2);
                xpush(w1);
                xpush(w4);
                xpush(w3);
                xpush(w2);
                xpush(w1);
                break;
            }
            case SWAP: {
                ValueNode w1 = xpop();
                ValueNode w2 = xpop();
                assert w1 != TWO_SLOT_MARKER;
                assert w2 != TWO_SLOT_MARKER;
                xpush(w1);
                xpush(w2);
                break;
            }
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    public int hashCode() {
        int result = hashCode(locals, locals.length);
        result *= 13;
        result += hashCode(stack, this.stackSize);
        return result;
    }

    private static int hashCode(Object[] a, int length) {
        int result = 1;
        for (int i = 0; i < length; ++i) {
            Object element = a[i];
            result = 31 * result + (element == null ? 0 : System.identityHashCode(element));
        }
        return result;
    }

    private static boolean equals(ValueNode[] a, ValueNode[] b, int length) {
        for (int i = 0; i < length; ++i) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object otherObject) {
        if (otherObject instanceof FrameStateBuilder) {
            FrameStateBuilder other = (FrameStateBuilder) otherObject;
            if (!other.method.equals(method)) {
                return false;
            }
            if (other.stackSize != stackSize) {
                return false;
            }
            if (other.parser != parser) {
                return false;
            }
            if (other.rethrowException != rethrowException) {
                return false;
            }
            if (other.graph != graph) {
                return false;
            }
            if (other.locals.length != locals.length) {
                return false;
            }
            return equals(other.locals, locals, locals.length) && equals(other.stack, stack, stackSize) && equals(other.lockedObjects, lockedObjects, lockedObjects.length) &&
                            equals(other.monitorIds, monitorIds, monitorIds.length);
        }
        return false;
    }

    @Override
    public boolean isAfterSideEffect() {
        return sideEffects != null;
    }

    @Override
    public Iterable<StateSplit> sideEffects() {
        return sideEffects;
    }

    @Override
    public void addSideEffect(StateSplit sideEffect) {
        assert sideEffect != null;
        assert sideEffect.hasSideEffect();
        if (sideEffects == null) {
            sideEffects = new ArrayList<>(4);
        }
        sideEffects.add(sideEffect);
    }

    public void traceState() {
        Debug.log(String.format("|   state [nr locals = %d, stack depth = %d, method = %s]", localsSize(), stackSize(), method));
        for (int i = 0; i < localsSize(); ++i) {
            ValueNode value = locals[i];
            Debug.log(String.format("|   local[%d] = %-8s : %s", i, value == null ? "bogus" : value == TWO_SLOT_MARKER ? "second" : value.getStackKind().getJavaName(), value));
        }
        for (int i = 0; i < stackSize(); ++i) {
            ValueNode value = stack[i];
            Debug.log(String.format("|   stack[%d] = %-8s : %s", i, value == null ? "bogus" : value == TWO_SLOT_MARKER ? "second" : value.getStackKind().getJavaName(), value));
        }
    }
}
