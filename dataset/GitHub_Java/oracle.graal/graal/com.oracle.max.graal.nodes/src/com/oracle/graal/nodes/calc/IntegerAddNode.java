/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;

@NodeInfo(shortName = "+")
public class IntegerAddNode extends IntegerArithmeticNode implements Canonicalizable, LIRLowerable {

    public IntegerAddNode(CiKind kind, ValueNode x, ValueNode y) {
        super(kind, x, y);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (x().isConstant() && !y().isConstant()) {
            return graph().unique(new IntegerAddNode(kind(), y(), x()));
        }
        if (x().isConstant()) {
            if (kind() == CiKind.Int) {
                return ConstantNode.forInt(x().asConstant().asInt() + y().asConstant().asInt(), graph());
            } else {
                assert kind() == CiKind.Long;
                return ConstantNode.forLong(x().asConstant().asLong() + y().asConstant().asLong(), graph());
            }
        } else if (y().isConstant()) {
            if (kind() == CiKind.Int) {
                int c = y().asConstant().asInt();
                if (c == 0) {
                    return x();
                }
            } else {
                assert kind() == CiKind.Long;
                long c = y().asConstant().asLong();
                if (c == 0) {
                    return x();
                }
            }
        }
        return this;
    }

    public static boolean isIntegerAddition(ValueNode result, ValueNode a, ValueNode b) {
        CiKind kind = result.kind();
        if (kind != a.kind() || kind != b.kind() || !(kind.isInt() || kind.isLong())) {
            return false;
        }
        if (result.isConstant() && a.isConstant() && b.isConstant()) {
            if (kind.isInt()) {
                return result.asConstant().asInt() == a.asConstant().asInt() + b.asConstant().asInt();
            } else if (kind.isLong()) {
                return result.asConstant().asLong() == a.asConstant().asLong() + b.asConstant().asLong();
            }
        } else if (result instanceof IntegerAddNode) {
            IntegerAddNode add = (IntegerAddNode) result;
            return (add.x() == a && add.y() == b) || (add.y() == a && add.x() == b);
        }
        return false;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        CiValue op1 = gen.operand(x());
        assert op1 != null : x() + ", this=" + this;
        CiValue op2 = gen.operand(y());
        if (!y().isConstant() && !FloatAddNode.livesLonger(this, y(), gen)) {
            CiValue op = op1;
            op1 = op2;
            op2 = op;
        }
        gen.setResult(this, gen.emitAdd(op1, op2));
    }
}
