/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.debug;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_IGNORED;

import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.NamedLocationIdentity;
import com.oracle.graal.nodes.memory.MemoryCheckpoint;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code StringToBytesNode} transforms a compilation-time String into a byte array in the
 * compiled code.
 */
@NodeInfo(allowedUsageTypes = InputType.Memory, cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public final class StringToBytesNode extends FixedWithNextNode implements Lowerable, MemoryCheckpoint.Single {

    public static final NodeClass<StringToBytesNode> TYPE = NodeClass.create(StringToBytesNode.class);

    private final String value;

    public StringToBytesNode(String value, Stamp stamp) {
        super(TYPE, stamp);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(JavaKind.Byte);
    }
}
