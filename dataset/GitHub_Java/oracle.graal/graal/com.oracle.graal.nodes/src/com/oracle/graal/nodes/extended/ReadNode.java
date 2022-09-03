/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Reads an {@linkplain AccessNode accessed} value.
 */
public final class ReadNode extends FloatableAccessNode implements Node.IterableNodeType, LIRLowerable, Canonicalizable, PiPushable {

    public ReadNode(ValueNode object, ValueNode location, Stamp stamp) {
        super(object, location, stamp);
    }

    public ReadNode(ValueNode object, ValueNode location, Stamp stamp, List<ValueNode> dependencies) {
        super(object, location, stamp, dependencies);
    }

    private ReadNode(ValueNode object, int displacement, Object locationIdentity, Kind kind) {
        super(object, object.graph().add(new LocationNode(locationIdentity, kind, displacement)), StampFactory.forKind(kind));
    }

    private ReadNode(ValueNode object, ValueNode location, ValueNode dependency) {
        /*
         * Used by node intrinsics. Since the initial value for location is a parameter, i.e., a
         * LocalNode, the constructor cannot use the declared type LocationNode.
         */
        super(object, location, StampFactory.forNodeIntrinsic(), dependency);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, location().generateLoad(gen, object(), this));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        return canonicalizeRead(this, location(), object(), tool);
    }

    @Override
    public FloatingAccessNode asFloatingNode(ValueNode lastLocationAccess) {
        return graph().unique(new FloatingReadNode(object(), location(), lastLocationAccess, stamp(), dependencies()));
    }

    public static ValueNode canonicalizeRead(ValueNode read, LocationNode location, ValueNode object, CanonicalizerTool tool) {
        MetaAccessProvider runtime = tool.runtime();
        if (runtime != null && object != null && object.isConstant()) {
            if (location.locationIdentity() == LocationNode.FINAL_LOCATION && location.getClass() == LocationNode.class) {
                long displacement = location.displacement();
                Kind kind = location.getValueKind();
                if (object.kind() == Kind.Object) {
                    Object base = object.asConstant().asObject();
                    if (base != null) {
                        Constant constant = tool.runtime().readUnsafeConstant(kind, base, displacement);
                        if (constant != null) {
                            return ConstantNode.forConstant(constant, runtime, read.graph());
                        }
                    }
                } else if (object.kind() == Kind.Long || object.kind().getStackKind() == Kind.Int) {
                    long base = object.asConstant().asLong();
                    if (base != 0L) {
                        Constant constant = tool.runtime().readUnsafeConstant(kind, null, base + displacement);
                        if (constant != null) {
                            return ConstantNode.forConstant(constant, runtime, read.graph());
                        }
                    }
                }
            }
        }
        return read;
    }

    @Override
    public boolean push(PiNode parent) {
        Object locId = location().locationIdentity();
        if (locId instanceof ResolvedJavaField) {
            ResolvedJavaType fieldType = ((ResolvedJavaField) locId).getDeclaringClass();
            ResolvedJavaType beforePiType = parent.object().objectStamp().type();

            if (fieldType.isAssignableFrom(beforePiType)) {
                replaceFirstInput(parent, parent.object());
                return true;
            }
        }
        return false;
    }

    /**
     * Reads a value from memory.
     * 
     * @param base the base pointer for the memory access
     * @param displacement the displacement of the access
     * @param locationIdentity the identity of the access
     * @param kind the kind of the value read
     * @return the value read from memory
     */
    @NodeIntrinsic(setStampFromReturnType = true)
    public static native <T> T read(Object base, @ConstantNodeParameter int displacement, @ConstantNodeParameter Object locationIdentity, @ConstantNodeParameter Kind kind);
}
