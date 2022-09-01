/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.compiler.common.type.FloatStamp;
import com.oracle.graal.compiler.common.type.PrimitiveStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.gen.ArithmeticLIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeCycles;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodeinfo.NodeSize;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.BinaryNode;
import com.oracle.graal.nodes.calc.DivNode;
import com.oracle.graal.nodes.calc.MulNode;
import com.oracle.graal.nodes.calc.SqrtNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(nameTemplate = "MathIntrinsic#{p#operation/s}", cycles = NodeCycles.CYCLES_UNKOWN, size = NodeSize.SIZE_1)
public final class BinaryMathIntrinsicNode extends BinaryNode implements ArithmeticLIRLowerable, Lowerable {

    public static final NodeClass<BinaryMathIntrinsicNode> TYPE = NodeClass.create(BinaryMathIntrinsicNode.class);
    protected final BinaryOperation operation;

    public enum BinaryOperation {
        POW(new ForeignCallDescriptor("arithmeticPow", double.class, double.class, double.class));

        public final ForeignCallDescriptor foreignCallDescriptor;

        BinaryOperation(ForeignCallDescriptor foreignCallDescriptor) {
            this.foreignCallDescriptor = foreignCallDescriptor;
        }
    }

    public BinaryOperation getOperation() {
        return operation;
    }

    public static ValueNode create(ValueNode forX, ValueNode forY, BinaryOperation op) {
        ValueNode c = tryConstantFold(forX, forY, op);
        if (c != null) {
            return c;
        }
        return new BinaryMathIntrinsicNode(forX, forY, op);
    }

    protected static ValueNode tryConstantFold(ValueNode forX, ValueNode forY, BinaryOperation op) {
        if (forX.isConstant() && forY.isConstant()) {
            double ret = doCompute(forX.asJavaConstant().asDouble(), forY.asJavaConstant().asDouble(), op);
            return ConstantNode.forDouble(ret);
        }
        return null;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return stamp();
    }

    protected BinaryMathIntrinsicNode(ValueNode forX, ValueNode forY, BinaryOperation op) {
        super(TYPE, StampFactory.forKind(JavaKind.Double), forX, forY);
        assert forX.stamp() instanceof FloatStamp && PrimitiveStamp.getBits(forX.stamp()) == 64;
        assert forY.stamp() instanceof FloatStamp && PrimitiveStamp.getBits(forY.stamp()) == 64;
        this.operation = op;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        Value xValue = nodeValueMap.operand(getX());
        Value yValue = nodeValueMap.operand(getY());
        Value result;
        switch (getOperation()) {
            case POW:
                result = gen.emitMathPow(xValue, yValue);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        nodeValueMap.setResult(this, result);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode c = tryConstantFold(forX, forY, getOperation());
        if (c != null) {
            return c;
        }
        if (forY.isConstant()) {
            double yValue = forY.asJavaConstant().asDouble();
            // If the second argument is positive or negative zero, then the result is 1.0.
            if (yValue == 0.0D) {
                return ConstantNode.forDouble(1);
            }

            // If the second argument is 1.0, then the result is the same as the first argument.
            if (yValue == 1.0D) {
                return x;
            }

            // If the second argument is NaN, then the result is NaN.
            if (Double.isNaN(yValue)) {
                return ConstantNode.forDouble(Double.NaN);
            }

            // x**-1 = 1/x
            if (yValue == -1.0D) {
                return new DivNode(ConstantNode.forDouble(1), x);
            }

            // x**2 = x*x
            if (yValue == 2.0D) {
                return new MulNode(x, x);
            }

            // x**0.5 = sqrt(x)
            if (yValue == 0.5D && x.stamp() instanceof FloatStamp && ((FloatStamp) x.stamp()).lowerBound() >= 0.0D) {
                return new SqrtNode(x);
            }
        }
        return this;
    }

    @NodeIntrinsic
    public static native double compute(double x, double y, @ConstantNodeParameter BinaryOperation op);

    private static double doCompute(double x, double y, BinaryOperation op) {
        switch (op) {
            case POW:
                return Math.pow(x, y);
            default:
                throw new GraalError("unknown op %s", op);
        }
    }

}
