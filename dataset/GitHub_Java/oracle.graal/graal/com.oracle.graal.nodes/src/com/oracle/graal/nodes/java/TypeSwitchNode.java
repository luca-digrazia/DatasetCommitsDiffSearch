/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code TypeSwitchNode} performs a lookup based on the type of the input value.
 * The type comparison is an exact type comparison, not an instanceof.
 */
public final class TypeSwitchNode extends SwitchNode implements LIRLowerable, Simplifiable {

    private final ResolvedJavaType[] keys;

    public TypeSwitchNode(ValueNode value, BeginNode[] successors, ResolvedJavaType[] keys, double[] probability) {
        super(value, successors, probability);
        assert successors.length == keys.length + 1;
        assert successors.length == probability.length;
        this.keys = keys;
    }

    public TypeSwitchNode(ValueNode value, ResolvedJavaType[] keys, double[] switchProbability) {
        this(value, new BeginNode[switchProbability.length], keys, switchProbability);
    }

    public ResolvedJavaType keyAt(int i) {
        return keys[i];
    }

    public int keysLength() {
        return keys.length;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitTypeSwitch(this);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        // TODO(ls) perform simplifications based on the type of value
    }
}
