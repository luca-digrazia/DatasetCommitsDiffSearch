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
package com.oracle.graal.nodes.extended;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_3;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_1;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.TypeReference;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.type.StampTool;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Loads a method from the virtual method table of a given hub.
 */
@NodeInfo(cycles = CYCLES_3, size = SIZE_1)
public final class LoadMethodNode extends FixedWithNextNode implements Lowerable, Canonicalizable {

    public static final NodeClass<LoadMethodNode> TYPE = NodeClass.create(LoadMethodNode.class);
    @Input ValueNode hub;
    protected final ResolvedJavaMethod method;
    protected final ResolvedJavaType receiverType;

    /**
     * The caller or context type used to perform access checks when resolving {@link #method}.
     */
    protected final ResolvedJavaType callerType;

    public ValueNode getHub() {
        return hub;
    }

    public LoadMethodNode(@InjectedNodeParameter Stamp stamp, ResolvedJavaMethod method, ResolvedJavaType receiverType, ResolvedJavaType callerType, ValueNode hub) {
        super(TYPE, stamp);
        this.receiverType = receiverType;
        this.callerType = callerType;
        this.hub = hub;
        this.method = method;
        assert method.isConcrete() : "Cannot load abstract method from a hub";
        assert method.hasReceiver() : "Cannot load a static method from a hub";
        if (!method.isInVirtualMethodTable(receiverType)) {
            throw new GraalError("%s does not have a vtable entry in type %s", method, receiverType);
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (hub instanceof LoadHubNode) {
            ValueNode object = ((LoadHubNode) hub).getValue();
            TypeReference type = StampTool.typeReferenceOrNull(object);
            if (type != null) {
                if (type.isExact()) {
                    return resolveExactMethod(tool, type.getType());
                }
                Assumptions assumptions = graph().getAssumptions();
                AssumptionResult<ResolvedJavaMethod> resolvedMethod = type.getType().findUniqueConcreteMethod(method);
                if (resolvedMethod != null && resolvedMethod.canRecordTo(assumptions) && !type.getType().isInterface() && method.getDeclaringClass().isAssignableFrom(type.getType())) {
                    resolvedMethod.recordTo(assumptions);
                    return ConstantNode.forConstant(stamp(), resolvedMethod.getResult().getEncoding(), tool.getMetaAccess());
                }
            }
        }
        if (hub.isConstant()) {
            return resolveExactMethod(tool, tool.getConstantReflection().asJavaType(hub.asConstant()));
        }

        return this;
    }

    /**
     * Find the method which would be loaded.
     *
     * @param tool
     * @param type the exact type of object being loaded from
     * @return the method which would be invoked for {@code type} or null if it doesn't implement
     *         the method
     */
    private Node resolveExactMethod(CanonicalizerTool tool, ResolvedJavaType type) {
        ResolvedJavaMethod newMethod = type.resolveConcreteMethod(method, callerType);
        if (newMethod == null) {
            /*
             * This really represent a misuse of LoadMethod since we're loading from a class which
             * isn't known to implement the original method but for now at least fold it away.
             */
            return ConstantNode.forConstant(stamp(), JavaConstant.NULL_POINTER, null);
        } else {
            return ConstantNode.forConstant(stamp(), newMethod.getEncoding(), tool.getMetaAccess());
        }
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public ResolvedJavaType getReceiverType() {
        return receiverType;
    }

    public ResolvedJavaType getCallerType() {
        return callerType;
    }
}
