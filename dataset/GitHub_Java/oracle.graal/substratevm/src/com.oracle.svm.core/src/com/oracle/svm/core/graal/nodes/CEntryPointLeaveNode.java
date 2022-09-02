/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.DeoptimizingNode.DeoptBefore;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_8, size = NodeSize.SIZE_8, allowedUsageTypes = {InputType.Memory})
public class CEntryPointLeaveNode extends DeoptimizingFixedWithNextNode implements Simplifiable, Lowerable, SingleMemoryKill, DeoptBefore {

    public static final NodeClass<CEntryPointLeaveNode> TYPE = NodeClass.create(CEntryPointLeaveNode.class);

    /** @see CEntryPointActions */
    public enum LeaveAction {
        Leave,
        DetachThread,
        TearDownIsolate,
        ExceptionAbort;
    }

    protected final LeaveAction leaveAction;
    @OptionalInput protected ValueNode exception;

    private boolean returnValueAnchored;

    public CEntryPointLeaveNode(LeaveAction leaveAction) {
        this(leaveAction, null);
    }

    public CEntryPointLeaveNode(LeaveAction leaveAction, ValueNode exception) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert (leaveAction == LeaveAction.ExceptionAbort) == (exception != null);
        this.leaveAction = leaveAction;
        this.exception = exception;
    }

    public LeaveAction getLeaveAction() {
        return leaveAction;
    }

    public ValueNode getException() {
        return exception;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public boolean canUseAsStateDuring() {
        return true;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (tool.allUsagesAvailable() && !returnValueAnchored) {
            returnValueAnchored = true;
            anchorReturnValue();
        }
    }

    /**
     * The {@link CEntryPointLeaveNode} performs the thread state transition from JAVA to NATIVE
     * state. After that transition, no more normal Java code must be executed. Therefore we need to
     * ensure that the return value of the method is scheduled before the transition, by anchoring
     * it to the control flow before this node. We do this here after the graph has been built
     * instead of during bytecode parsing when the node is allocated because there are too many code
     * paths, including node intrinsics, that allocate a {@link CEntryPointLeaveNode} and so
     * providing the return value everywhere would be tedious.
     *
     * We walk the control flow graph down from this node to control flow sinks. Because we know and
     * require that {@link CEntryPointLeaveNode} are inserted shortly before the {@link ReturnNode},
     * only few control flow operations need to be considered. In particular, we disallow
     * {@link MergeNode control flow joins} and instead require the users of the node to adapt their
     * code if this constraint is violated.
     */
    private void anchorReturnValue() {
        if (graph().method().getSignature().getReturnKind() == JavaKind.Void) {
            /*
             * Methods without a return value do not need anchoring. This allows more flexible
             * usages of CEntryPointLeaveNode with more control flow, since there is no problematic
             * scheduling of a return value possible.
             */
            return;
        }

        List<ReturnNode> returns = new ArrayList<>(1);
        collectReturns(this, returns);
        if (returns.size() == 1) {
            ReturnNode returnNode = returns.get(0);
            ValueNode returnValue = returnNode.result();
            assert returnValue != null : "methods with return type void are already excluded";
            if (returnValue == this) {
                /* Returning the status value produced by this node. */
            } else {
                FixedValueAnchorNode anchoredReturnValue = graph().add(new FixedValueAnchorNode(returnValue));
                graph().addBeforeFixed(this, anchoredReturnValue);
                returnNode.replaceAllInputs(returnValue, anchoredReturnValue);
            }

        } else if (leaveAction == LeaveAction.ExceptionAbort) {
            VMError.guarantee(returns.size() == 0, "Must not have a ReturnNode when aborting with an exception");
        } else {
            throw VMError.shouldNotReachHere("Unexpected number of ReturnNode found: " + returns.size() +
                            " in method " + graph().method().format("%H.%n(%p)"));
        }
    }

    private static void collectReturns(Node n, List<ReturnNode> returns) {
        Node cur = n;
        while (true) {
            if (cur instanceof FixedWithNextNode) {
                cur = ((FixedWithNextNode) cur).next();
            } else {
                if (cur instanceof IfNode) {
                    for (Node sux : cur.successors()) {
                        collectReturns(sux, returns);
                    }
                } else if (cur instanceof ReturnNode) {
                    returns.add((ReturnNode) cur);
                } else if (cur instanceof DeadEndNode) {
                    /* Ignore fatal errors, they are a VM exit. */
                } else {
                    throw VMError.shouldNotReachHere("Unexpected control flow structure after CEntryPointLeaveNode. Disallowed node " + cur +
                                    " in method " + ((StructuredGraph) cur.graph()).method().format("%H.%n(%p)"));
                }
                return;
            }
        }
    }
}
