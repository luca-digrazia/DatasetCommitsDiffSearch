/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.typesystem;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.truffle.nodes.asserts.*;
import com.oracle.truffle.api.*;

/**
 * Macro node for method {@link CompilerDirectives#unsafeCast(Object, Class, boolean)}.
 */
public class CustomizedUnsafeStoreMacroNode extends NeverPartOfCompilationNode implements Canonicalizable {

    private static final int ARGUMENT_COUNT = 4;
    private static final int OBJECT_ARGUMENT_INDEX = 0;
    private static final int OFFSET_ARGUMENT_INDEX = 1;
    private static final int VALUE_ARGUMENT_INDEX = 2;
    private static final int LOCATION_ARGUMENT_INDEX = 3;

    public CustomizedUnsafeStoreMacroNode(Invoke invoke) {
        super(invoke, "The location argument could not be resolved to a constant.");
        assert arguments.size() == ARGUMENT_COUNT;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ValueNode locationArgument = arguments.get(LOCATION_ARGUMENT_INDEX);
        if (locationArgument.isConstant()) {
            ValueNode objectArgument = arguments.get(OBJECT_ARGUMENT_INDEX);
            ValueNode offsetArgument = arguments.get(OFFSET_ARGUMENT_INDEX);
            ValueNode valueArgument = arguments.get(VALUE_ARGUMENT_INDEX);
            Object locationIdentityObject = locationArgument.asConstant().asObject();
            LocationIdentity locationIdentity;
            if (locationIdentityObject == null) {
                locationIdentity = LocationIdentity.ANY_LOCATION;
            } else {
                locationIdentity = ObjectLocationIdentity.create(locationIdentityObject);
            }

            UnsafeStoreNode unsafeStoreNode = graph().add(new UnsafeStoreNode(objectArgument, offsetArgument, valueArgument, valueArgument.kind(), locationIdentity));
            unsafeStoreNode.setStateAfter(this.stateAfter());
            return unsafeStoreNode;
        }
        return this;
    }
}
