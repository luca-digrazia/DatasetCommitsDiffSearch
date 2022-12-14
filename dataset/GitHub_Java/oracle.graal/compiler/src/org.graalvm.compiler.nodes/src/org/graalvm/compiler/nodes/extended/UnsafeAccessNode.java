/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public abstract class UnsafeAccessNode extends FixedWithNextNode implements Canonicalizable {

    public static final NodeClass<UnsafeAccessNode> TYPE = NodeClass.create(UnsafeAccessNode.class);
    @Input ValueNode object;
    @Input ValueNode offset;
    protected final JavaKind accessKind;
    protected final LocationIdentity locationIdentity;
    protected final boolean forceAnyLocation;

    protected UnsafeAccessNode(NodeClass<? extends UnsafeAccessNode> c, Stamp stamp, ValueNode object, ValueNode offset, JavaKind accessKind, LocationIdentity locationIdentity,
                    boolean forceAnyLocation) {
        super(c, stamp);
        this.forceAnyLocation = forceAnyLocation;
        assert accessKind != null;
        assert locationIdentity != null;
        this.object = object;
        this.offset = offset;
        this.accessKind = accessKind;
        this.locationIdentity = locationIdentity;
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    public boolean isAnyLocationForced() {
        return forceAnyLocation;
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode offset() {
        return offset;
    }

    public JavaKind accessKind() {
        return accessKind;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (!isAnyLocationForced() && getLocationIdentity().isAny()) {
            if (offset().isConstant()) {
                long constantOffset = offset().asJavaConstant().asLong();

                // Try to canonicalize to a field access.
                ResolvedJavaType receiverType = StampTool.typeOrNull(object());
                if (receiverType != null) {
                    ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(constantOffset, accessKind());
                    // No need for checking that the receiver is non-null. The field access includes
                    // the null check and if a field is found, the offset is so small that this is
                    // never a valid access of an arbitrary address.
                    if (field != null && field.getJavaKind() == this.accessKind() && !field.isVolatile()) {
                        assert !graph().isAfterFloatingReadPhase() : "cannot add more precise memory location after floating read phase";
                        return cloneAsFieldAccess(graph().getAssumptions(), field);
                    }
                }
            }
            ResolvedJavaType receiverType = StampTool.typeOrNull(object());
            // Try to build a better location identity.
            if (receiverType != null && receiverType.isArray()) {
                LocationIdentity identity = NamedLocationIdentity.getArrayLocation(receiverType.getComponentType().getJavaKind());
                assert !graph().isAfterFloatingReadPhase() : "cannot add more precise memory location after floating read phase";
                return cloneAsArrayAccess(offset(), identity);
            }
        }

        return this;
    }

    protected abstract ValueNode cloneAsFieldAccess(Assumptions assumptions, ResolvedJavaField field);

    protected abstract ValueNode cloneAsArrayAccess(ValueNode location, LocationIdentity identity);
}
