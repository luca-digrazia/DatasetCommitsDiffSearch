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
package com.oracle.max.graal.nodes.calc;

import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;

/**
 * The {@code ConditionalNode} class represents a comparison that yields one of two values. Note that these nodes are not
 * built directly from the bytecode but are introduced by canonicalization.
 */
public class ConditionalNode extends BinaryNode implements Canonicalizable, LIRLowerable {

    @Input private BooleanNode condition;

    public BooleanNode condition() {
        return condition;
    }

    public ConditionalNode(BooleanNode condition, ValueNode trueValue, ValueNode falseValue) {
        super(trueValue.kind(), trueValue, falseValue);
        assert trueValue.kind() == falseValue.kind();
        this.condition = condition;
    }

    public ValueNode trueValue() {
        return x();
    }

    public ValueNode falseValue() {
        return y();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (condition instanceof ConstantNode) {
            ConstantNode c = (ConstantNode) condition;
            if (c.asConstant().asBoolean()) {
                return trueValue();
            } else {
                return falseValue();
            }
        }
        if (trueValue() == falseValue()) {
            return trueValue();
        }

        return this;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        generator.emitConditional(this);
    }
}
