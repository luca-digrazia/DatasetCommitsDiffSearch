/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.api.code.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Node for a {@linkplain ForeignCallDescriptor foreign} call from within a stub.
 */
@NodeInfo(nameTemplate = "StubForeignCall#{p#descriptor/s}")
public class StubForeignCallNode extends FixedWithNextNode implements DeoptimizingNode, LIRLowerable, MemoryCheckpoint {

    @Input private final NodeInputList<ValueNode> arguments;
    private final MetaAccessProvider runtime;
    @Input private FrameState deoptState;

    private final ForeignCallDescriptor descriptor;

    public StubForeignCallNode(MetaAccessProvider runtime, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(StampFactory.forKind(Kind.fromJavaClass(descriptor.getResultType())));
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        this.runtime = runtime;
    }

    public ForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public LocationIdentity[] getLocationIdentities() {
        return runtime.getKilledLocations(descriptor);
    }

    protected Value[] operands(LIRGeneratorTool gen) {
        Value[] operands = new Value[arguments.size()];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = gen.operand(arguments.get(i));
        }
        return operands;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        assert graph().start() instanceof StubStartNode;
        ForeignCallLinkage linkage = gen.getRuntime().lookupForeignCall(descriptor);
        Value[] operands = operands(gen);
        Value result = gen.emitForeignCall(linkage, this, operands);
        if (result != null) {
            gen.setResult(this, result);
        }
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + descriptor;
        }
        return super.toString(verbosity);
    }

    @Override
    public boolean canDeoptimize() {
        return false;
    }

    @Override
    public FrameState getDeoptimizationState() {
        return null;
    }

    @Override
    public void setDeoptimizationState(FrameState state) {
    }

    @Override
    public DeoptimizationReason getDeoptimizationReason() {
        return null;
    }
}
