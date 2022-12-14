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
package com.oracle.max.graal.runtime.nodes;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.runtime.*;
import com.sun.cri.ci.*;


public final class FPConversionNode extends FloatingNode {
    private static final int INPUT_COUNT = 1;
    private static final int INPUT_OBJECT = 0;

    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    /**
     * The instruction that produces the object tested against null.
     */
     public Value value() {
        return (Value) inputs().get(super.inputCount() + INPUT_OBJECT);
    }

    public void setValue(Value n) {
        inputs().set(super.inputCount() + INPUT_OBJECT, n);
    }

    public FPConversionNode(CiKind kind, Value value, Graph graph) {
        super(kind, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.setValue(value);
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LIRGenerator.LIRGeneratorOp.class) {
            return (T) new LIRGenerator.LIRGeneratorOp() {
                @Override
                public void generate(Node n, LIRGenerator generator) {
                    FPConversionNode conv = (FPConversionNode) n;
                    CiValue reg = generator.createResultVariable(conv);
                    CiValue value = generator.load(conv.value());
                    CiValue tmp = generator.forceToSpill(value, conv.kind, false);
                    generator.lir().move(tmp, reg);
                }
            };
        }
        return super.lookup(clazz);
    }

    @Override
    public boolean valueEqual(Node i) {
        return i instanceof FPConversionNode && ((FPConversionNode) i).kind == kind;
    }

    @Override
    public void print(LogStream out) {
        out.print("fp conversion node ").print(value());
    }

    @Override
    public Node copy(Graph into) {
        return new FPConversionNode(kind, null, into);
    }
}
