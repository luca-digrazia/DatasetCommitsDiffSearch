/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.TriState;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;

@NodeInfo(shortName = "==", cycles = CYCLES_2)
public final class FloatEqualsNode extends CompareNode implements BinaryCommutative<ValueNode> {
    public static final NodeClass<FloatEqualsNode> TYPE = NodeClass.create(FloatEqualsNode.class);
    private static final FloatEqualsOp OP = new FloatEqualsOp();

    public FloatEqualsNode(ValueNode x, ValueNode y) {
        super(TYPE, Condition.EQ, false, x, y);
        assert x.stamp(NodeView.DEFAULT) instanceof FloatStamp && y.stamp(NodeView.DEFAULT) instanceof FloatStamp : x.stamp(NodeView.DEFAULT) + " " + y.stamp(NodeView.DEFAULT);
        assert x.stamp(NodeView.DEFAULT).isCompatible(y.stamp(NodeView.DEFAULT));
    }

    public static LogicNode create(ValueNode x, ValueNode y) {
        LogicNode result = CompareNode.tryConstantFoldPrimitive(Condition.EQ, x, y, false);
        if (result != null) {
            return result;
        } else {
            return new FloatEqualsNode(x, y).maybeCommuteInputs();
        }
    }

    public static LogicNode create(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                    ValueNode x, ValueNode y) {
        LogicNode value = OP.canonical(constantReflection, metaAccess, options, smallestCompareWidth, Condition.EQ, false, x, y);
        if (value != null) {
            return value;
        }
        return create(x, y);
    }

    @Override
    public boolean isIdentityComparison() {
        FloatStamp xStamp = (FloatStamp) x.stamp(NodeView.DEFAULT);
        FloatStamp yStamp = (FloatStamp) y.stamp(NodeView.DEFAULT);
        /*
         * If both stamps have at most one 0.0 and it's the same 0.0 then this is an identity
         * comparison. FloatStamp isn't careful about tracking the presence of -0.0 so assume that
         * anything that includes 0.0 might include -0.0. So if either one is non-zero then it's an
         * identity comparison.
         */
        return (!xStamp.contains(0.0) || !yStamp.contains(0.0));
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), Condition.EQ, unorderedIsTrue, forX, forY);
        if (value != null) {
            return value;
        }
        return this;
    }

    public static class FloatEqualsOp extends CompareOp {

        @Override
        public LogicNode canonical(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, Condition condition,
                        boolean unorderedIsTrue, ValueNode forX, ValueNode forY) {
            LogicNode result = super.canonical(constantReflection, metaAccess, options, smallestCompareWidth, condition, unorderedIsTrue, forX, forY);
            if (result != null) {
                return result;
            }
            Stamp xStampGeneric = forX.stamp(NodeView.DEFAULT);
            Stamp yStampGeneric = forY.stamp(NodeView.DEFAULT);
            if (xStampGeneric instanceof FloatStamp && yStampGeneric instanceof FloatStamp) {
                FloatStamp xStamp = (FloatStamp) xStampGeneric;
                FloatStamp yStamp = (FloatStamp) yStampGeneric;
                if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY) && xStamp.isNonNaN() && yStamp.isNonNaN()) {
                    return LogicConstantNode.tautology();
                } else if (xStamp.alwaysDistinct(yStamp)) {
                    return LogicConstantNode.contradiction();
                }
            }
            return null;
        }

        @Override
        protected CompareNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue) {
            if (newX.stamp(NodeView.DEFAULT) instanceof FloatStamp && newY.stamp(NodeView.DEFAULT) instanceof FloatStamp) {
                return new FloatEqualsNode(newX, newY);
            } else if (newX.stamp(NodeView.DEFAULT) instanceof IntegerStamp && newY.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                return new IntegerEqualsNode(newX, newY);
            }
            throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        if (!negated) {
            return xStamp.join(yStamp);
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        if (!negated) {
            return xStamp.join(yStamp);
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof FloatStamp && yStampGeneric instanceof FloatStamp) {
            FloatStamp xStamp = (FloatStamp) xStampGeneric;
            FloatStamp yStamp = (FloatStamp) yStampGeneric;
            if (xStamp.alwaysDistinct(yStamp)) {
                return TriState.FALSE;
            } else if (xStamp.neverDistinct(yStamp)) {
                return TriState.TRUE;
            }
        }
        return TriState.UNKNOWN;
    }
}
