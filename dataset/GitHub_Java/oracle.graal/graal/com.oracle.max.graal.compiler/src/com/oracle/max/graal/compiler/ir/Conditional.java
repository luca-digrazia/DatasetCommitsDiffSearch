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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.asm.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.gen.LIRGenerator.LIRGeneratorOp;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.CanonicalizerOp;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code Conditional} class represents a comparison that yields one of two values.
 * Note that these nodes are not built directly from the bytecode but are introduced
 * by conditional expression elimination.
 */
public final class Conditional extends Binary {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_CONDITION = 0;

    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    public BooleanNode condition() {
        return (BooleanNode) inputs().get(super.inputCount() + INPUT_CONDITION);
    }

    public void setCondition(BooleanNode n) {
        inputs().set(super.inputCount() + INPUT_CONDITION, n);
    }

    /**
     * Constructs a new IfOp.
     * @param x the instruction producing the first value to be compared
     * @param condition the condition of the comparison
     * @param y the instruction producing the second value to be compared
     * @param trueValue the value produced if the condition is true
     * @param falseValue the value produced if the condition is false
     */
    public Conditional(BooleanNode condition, Value trueValue, Value falseValue, Graph graph) {
        // TODO: return the appropriate bytecode IF_ICMPEQ, etc
        super(trueValue.kind.meet(falseValue.kind), Bytecodes.ILLEGAL, trueValue, falseValue, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setCondition(condition);
    }

    // for copying
    private Conditional(CiKind kind, Graph graph) {
        super(kind, Bytecodes.ILLEGAL, null, null, INPUT_COUNT, SUCCESSOR_COUNT, graph);
    }

    public Value trueValue() {
        return x();
    }

    public Value falseValue() {
        return y();
    }

    @Override
    public boolean valueEqual(Node i) {
        if (i instanceof Conditional) {
            return super.valueEqual(i);
        }
        return false;
    }

    @Override
    public void print(LogStream out) {
        out.print(x()).
        print(' ').
        print(condition()).
        print(' ').
        print(y()).
        print(" ? ").
        print(trueValue()).
        print(" : ").
        print(falseValue());
    }

    @Override
    public Node copy(Graph into) {
        Conditional x = new Conditional(kind, into);
        return x;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        if (clazz == LIRGeneratorOp.class) {
            return (T) LIRGEN;
        }
        return super.lookup(clazz);
    }

    private static final CanonicalizerOp CANONICALIZER = new CanonicalizerOp() {
        @Override
        public Node canonical(Node node) {
            Conditional conditional = (Conditional) node;
            BooleanNode condition = conditional.condition();
            Value trueValue = conditional.trueValue();
            Value falseValue = conditional.falseValue();
            if (condition instanceof Constant) {
                Constant c = (Constant) condition;
                if (c.asConstant().asBoolean()) {
                    return trueValue;
                } else {
                    return falseValue;
                }
            }
            if (trueValue == falseValue) {
                return trueValue;
            }
            if (trueValue instanceof Constant && falseValue instanceof Constant
                            && trueValue.kind == CiKind.Int && falseValue.kind == CiKind.Int) {
                int trueInt = trueValue.asConstant().asInt();
                int falseInt = falseValue.asConstant().asInt();
                if (trueInt == 0 && falseInt == 1) {
                    TTY.println("> Conditional canon'ed to Materialize");
                    return new MaterializeNode(new NegateBooleanNode(condition, node.graph()), node.graph());
                } else if (trueInt == 1 && falseInt == 0) {
                    TTY.println("> Conditional canon'ed to Materialize");
                    return new MaterializeNode(condition, node.graph());
                }
            }
            return conditional;
        }
    };

    private static final LIRGeneratorOp LIRGEN = new LIRGeneratorOp() {

        @Override
        public void generate(Node n, LIRGenerator generator) {
            Conditional conditional = (Conditional) n;
            BooleanNode condition = conditional.condition();

            CiVariable result = generator.createResultVariable(conditional);
            // try to use cmp + cmov first
            Condition cond = null;
            CiValue left = null;
            CiValue right = null;
            if (condition instanceof Compare) {
                Compare compare = (Compare) condition;
                Value x = compare.x();
                Value y = compare.y();
                left = generator.load(x);
                if (!generator.canInlineAsConstant(y)) {
                    right = generator.load(y);
                } else {
                    right = generator.makeOperand(y);
                }
                cond = compare.condition;
            } else if (condition instanceof IsNonNull) {
                IsNonNull isNonNull = (IsNonNull) condition;
                left = generator.load(isNonNull.object());
                right = CiConstant.NULL_OBJECT;
                cond = Condition.NE;
            }
            CiValue tVal = generator.makeOperand(conditional.trueValue());
            CiValue fVal = generator.makeOperand(conditional.falseValue());
            if (cond != null) {
                assert left != null && right != null;
                generator.lir().cmp(cond, left, right);
                generator.lir().cmove(cond, tVal, fVal, result);
            } else {
                LIRBlock trueSuccessor = new LIRBlock(new Label(), null);
                generator.emitBooleanBranch(condition, trueSuccessor, null, null);
                LIRList lir = generator.lir();
                lir.move(fVal, result);
                Label label = new Label();
                lir.branch(Condition.TRUE, label);
                lir.branchDestination(trueSuccessor.label);
                lir.move(tVal, result);
                lir.branchDestination(label);
            }
        }
    };
}
