/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.op;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMArithmeticFactory;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMArithmetic.LLVMArithmeticOpNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChildren({@NodeChild("leftNode"), @NodeChild("rightNode")})
public abstract class LLVMArithmeticNode extends LLVMExpressionNode {

    public abstract static class LLVMAddNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean add(boolean left, boolean right) {
            return left ^ right;
        }

        @Specialization
        protected byte add(byte left, byte right) {
            return (byte) (left + right);
        }

        @Specialization
        protected short add(short left, short right) {
            return (short) (left + right);
        }

        @Specialization
        protected int add(int left, int right) {
            return left + right;
        }

        @Specialization
        protected long add(long left, long right) {
            return left + right;
        }

        @Specialization
        protected LLVMPointer add(LLVMPointer left, long right) {
            return left.increment(right);
        }

        @Specialization
        protected LLVMPointer add(long left, LLVMPointer right) {
            return right.increment(left);
        }

        @Specialization
        protected LLVMIVarBit add(LLVMIVarBit left, LLVMIVarBit right) {
            return left.add(right);
        }

        @Specialization
        protected float add(float left, float right) {
            return left + right;
        }

        @Specialization
        protected double add(double left, double right) {
            return left + right;
        }

        protected LLVMArithmeticOpNode createFP80AddNode() {
            return LLVMArithmeticFactory.createAddNode();
        }

        @Specialization
        protected LLVM80BitFloat add(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80AddNode()") LLVMArithmeticOpNode node) {
            try {
                return (LLVM80BitFloat) node.execute(left, right);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

        @Specialization
        protected LLVMDoubleVector add(LLVMDoubleVector left, LLVMDoubleVector right) {
            return left.add(right);
        }

        @Specialization
        protected LLVMFloatVector add(LLVMFloatVector left, LLVMFloatVector right) {
            return left.add(right);
        }

        @Specialization
        protected LLVMI16Vector add(LLVMI16Vector left, LLVMI16Vector right) {
            return left.add(right);
        }

        @Specialization
        protected LLVMI1Vector add(LLVMI1Vector left, LLVMI1Vector right) {
            return left.add(right);
        }

        @Specialization
        protected LLVMI32Vector add(LLVMI32Vector left, LLVMI32Vector right) {
            return left.add(right);
        }

        @Specialization
        protected LLVMI64Vector add(LLVMI64Vector left, LLVMI64Vector right) {
            return left.add(right);
        }

        @Specialization
        protected LLVMI8Vector add(LLVMI8Vector left, LLVMI8Vector right) {
            return left.add(right);
        }
    }

    public abstract static class LLVMMulNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean mul(boolean left, boolean right) {
            return left & right;
        }

        @Specialization
        protected byte mul(byte left, byte right) {
            return (byte) (left * right);
        }

        @Specialization
        protected short mul(short left, short right) {
            return (short) (left * right);
        }

        @Specialization
        protected int mul(int left, int right) {
            return left * right;
        }

        @Specialization
        protected long mul(long left, long right) {
            return left * right;
        }

        @Specialization
        protected LLVMIVarBit mul(LLVMIVarBit left, LLVMIVarBit right) {
            return left.mul(right);
        }

        @Specialization
        protected float mul(float left, float right) {
            return left * right;
        }

        @Specialization
        protected double mul(double left, double right) {
            return left * right;
        }

        protected LLVMArithmeticOpNode createFP80MulNode() {
            return LLVMArithmeticFactory.createMulNode();
        }

        @Specialization
        protected LLVM80BitFloat mul(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80MulNode()") LLVMArithmeticOpNode node) {
            try {
                return (LLVM80BitFloat) node.execute(left, right);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

        @Specialization
        protected LLVMDoubleVector mul(LLVMDoubleVector left, LLVMDoubleVector right) {
            return left.mul(right);
        }

        @Specialization
        protected LLVMFloatVector mul(LLVMFloatVector left, LLVMFloatVector right) {
            return left.mul(right);
        }

        @Specialization
        protected LLVMI16Vector mul(LLVMI16Vector left, LLVMI16Vector right) {
            return left.mul(right);
        }

        @Specialization
        protected LLVMI1Vector mul(LLVMI1Vector left, LLVMI1Vector right) {
            return left.mul(right);
        }

        @Specialization
        protected LLVMI32Vector mul(LLVMI32Vector left, LLVMI32Vector right) {
            return left.mul(right);
        }

        @Specialization
        protected LLVMI64Vector mul(LLVMI64Vector left, LLVMI64Vector right) {
            return left.mul(right);
        }

        @Specialization
        protected LLVMI8Vector mul(LLVMI8Vector left, LLVMI8Vector right) {
            return left.mul(right);
        }
    }

    public abstract static class LLVMSubNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean sub(boolean left, boolean right) {
            return left ^ right;
        }

        @Specialization
        protected byte sub(byte left, byte right) {
            return (byte) (left - right);
        }

        @Specialization
        protected short sub(short left, short right) {
            return (short) (left - right);
        }

        @Specialization
        protected int sub(int left, int right) {
            return left - right;
        }

        @Specialization
        protected long sub(long left, long right) {
            return left - right;
        }

        @Specialization(guards = "left.getObject() == right.getObject()")
        protected long sub(LLVMManagedPointer left, LLVMManagedPointer right) {
            return sub(left.getOffset(), right.getOffset());
        }

        @Specialization(guards = "left.getObject() != right.getObject()")
        protected long sub(LLVMManagedPointer left, LLVMManagedPointer right,
                        @Cached("createToComparableValue()") ToComparableValue toComparableValueLeft,
                        @Cached("createToComparableValue()") ToComparableValue toComparableValueRight) {
            return sub(toComparableValueLeft.executeWithTarget(left), toComparableValueRight.executeWithTarget(right));
        }

        @Specialization
        protected long sub(LLVMManagedPointer left, LLVMNativePointer right,
                        @Cached("createToComparableValue()") ToComparableValue toComparableValue) {
            return sub(left, right.asNative(), toComparableValue);
        }

        @Specialization
        protected long sub(LLVMNativePointer left, LLVMManagedPointer right,
                        @Cached("createToComparableValue()") ToComparableValue toComparableValue) {
            return sub(left.asNative(), right, toComparableValue);
        }

        @Specialization
        protected long sub(LLVMNativePointer left, LLVMNativePointer right) {
            return sub(left.asNative(), right.asNative());
        }

        @Specialization
        protected long sub(long left, LLVMPointer right,
                        @Cached("createToComparableValue()") ToComparableValue toComparableValue) {
            return sub(left, toComparableValue.executeWithTarget(right));
        }

        @Specialization
        protected long sub(LLVMPointer left, long right,
                        @Cached("createToComparableValue()") ToComparableValue toComparableValue) {
            return toComparableValue.executeWithTarget(left) - right;
        }

        @Specialization
        protected LLVMIVarBit sub(LLVMIVarBit left, LLVMIVarBit right) {
            return left.sub(right);
        }

        @Specialization
        protected float sub(float left, float right) {
            return left - right;
        }

        @Specialization
        protected double sub(double left, double right) {
            return left - right;
        }

        protected LLVMArithmeticOpNode createFP80SubNode() {
            return LLVMArithmeticFactory.createSubNode();
        }

        @Specialization
        protected LLVM80BitFloat sub(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80SubNode()") LLVMArithmeticOpNode node) {
            try {
                return (LLVM80BitFloat) node.execute(left, right);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

        @Specialization
        protected LLVMDoubleVector sub(LLVMDoubleVector left, LLVMDoubleVector right) {
            return left.sub(right);
        }

        @Specialization
        protected LLVMFloatVector sub(LLVMFloatVector left, LLVMFloatVector right) {
            return left.sub(right);
        }

        @Specialization
        protected LLVMI16Vector sub(LLVMI16Vector left, LLVMI16Vector right) {
            return left.sub(right);
        }

        @Specialization
        protected LLVMI1Vector sub(LLVMI1Vector left, LLVMI1Vector right) {
            return left.sub(right);
        }

        @Specialization
        protected LLVMI32Vector sub(LLVMI32Vector left, LLVMI32Vector right) {
            return left.sub(right);
        }

        @Specialization
        protected LLVMI64Vector sub(LLVMI64Vector left, LLVMI64Vector right) {
            return left.sub(right);
        }

        @Specialization
        protected LLVMI8Vector sub(LLVMI8Vector left, LLVMI8Vector right) {
            return left.sub(right);
        }

        protected static ToComparableValue createToComparableValue() {
            return ToComparableValueNodeGen.create();
        }
    }

    public abstract static class LLVMDivNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean div(boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return left;
        }

        @Specialization
        protected byte div(byte left, byte right) {
            return (byte) (left / right);
        }

        @Specialization
        protected short div(short left, short right) {
            return (short) (left / right);
        }

        @Specialization
        protected int div(int left, int right) {
            return left / right;
        }

        @Specialization
        protected long div(long left, long right) {
            return left / right;
        }

        @Specialization
        protected LLVMIVarBit div(LLVMIVarBit left, LLVMIVarBit right) {
            return left.div(right);
        }

        @Specialization
        protected float div(float left, float right) {
            return left / right;
        }

        @Specialization
        protected double div(double left, double right) {
            return left / right;
        }

        protected LLVMArithmeticOpNode createFP80DivNode() {
            return LLVMArithmeticFactory.createDivNode();
        }

        @Specialization
        protected LLVM80BitFloat div(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80DivNode()") LLVMArithmeticOpNode node) {
            try {
                return (LLVM80BitFloat) node.execute(left, right);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

        @Specialization
        protected LLVMDoubleVector div(LLVMDoubleVector left, LLVMDoubleVector right) {
            return left.div(right);
        }

        @Specialization
        protected LLVMFloatVector div(LLVMFloatVector left, LLVMFloatVector right) {
            return left.div(right);
        }

        @Specialization
        protected LLVMI16Vector div(LLVMI16Vector left, LLVMI16Vector right) {
            return left.div(right);
        }

        @Specialization
        protected LLVMI1Vector div(LLVMI1Vector left, LLVMI1Vector right) {
            return left.div(right);
        }

        @Specialization
        protected LLVMI32Vector div(LLVMI32Vector left, LLVMI32Vector right) {
            return left.div(right);
        }

        @Specialization
        protected LLVMI64Vector div(LLVMI64Vector left, LLVMI64Vector right) {
            return left.div(right);
        }

        @Specialization
        protected LLVMI8Vector div(LLVMI8Vector left, LLVMI8Vector right) {
            return left.div(right);
        }
    }

    public abstract static class LLVMUDivNode extends LLVMArithmeticNode {

        @Specialization
        protected boolean udiv(boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return left;
        }

        @Specialization
        protected byte udiv(byte left, byte right) {
            return (byte) (Byte.toUnsignedInt(left) / Byte.toUnsignedInt(right));
        }

        @Specialization
        protected short udiv(short left, short right) {
            return (short) (Short.toUnsignedInt(left) / Short.toUnsignedInt(right));
        }

        @Specialization
        protected int udiv(int left, int right) {
            return Integer.divideUnsigned(left, right);
        }

        @Specialization
        protected long udiv(long left, long right) {
            return Long.divideUnsigned(left, right);
        }

        @Specialization
        protected LLVMIVarBit udiv(LLVMIVarBit left, LLVMIVarBit right) {
            return left.unsignedDiv(right);
        }

        @Specialization
        protected LLVMI16Vector udiv(LLVMI16Vector left, LLVMI16Vector right) {
            return left.divUnsigned(right);
        }

        @Specialization
        protected LLVMI1Vector udiv(LLVMI1Vector left, LLVMI1Vector right) {
            return left.divUnsigned(right);
        }

        @Specialization
        protected LLVMI32Vector udiv(LLVMI32Vector left, LLVMI32Vector right) {
            return left.divUnsigned(right);

        }

        @Specialization
        protected LLVMI64Vector udiv(LLVMI64Vector left, LLVMI64Vector right) {
            return left.divUnsigned(right);

        }

        @Specialization
        protected LLVMI8Vector udiv(LLVMI8Vector left, LLVMI8Vector right) {
            return left.divUnsigned(right);
        }
    }

    public abstract static class LLVMRemNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean rem(@SuppressWarnings("unused") boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return false;
        }

        @Specialization
        protected byte rem(byte left, byte right) {
            return (byte) (left % right);
        }

        @Specialization
        protected short rem(short left, short right) {
            return (short) (left % right);
        }

        @Specialization
        protected int rem(int left, int right) {
            return left % right;
        }

        @Specialization
        protected long rem(long left, long right) {
            return left % right;
        }

        @Specialization
        protected LLVMIVarBit rem(LLVMIVarBit left, LLVMIVarBit right) {
            return left.rem(right);
        }

        @Specialization
        protected float rem(float left, float right) {
            return left % right;
        }

        @Specialization
        protected double rem(double left, double right) {
            return left % right;
        }

        protected LLVMArithmeticOpNode createFP80RemNode() {
            return LLVMArithmeticFactory.createRemNode();
        }

        @Specialization
        protected LLVM80BitFloat rem(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80RemNode()") LLVMArithmeticOpNode node) {
            try {
                return (LLVM80BitFloat) node.execute(left, right);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

        @Specialization
        protected LLVMDoubleVector rem(LLVMDoubleVector left, LLVMDoubleVector right) {
            return left.rem(right);
        }

        @Specialization
        protected LLVMFloatVector rem(LLVMFloatVector left, LLVMFloatVector right) {
            return left.rem(right);
        }

        @Specialization
        protected LLVMI16Vector rem(LLVMI16Vector left, LLVMI16Vector right) {
            return left.rem(right);
        }

        @Specialization
        protected LLVMI1Vector rem(LLVMI1Vector left, LLVMI1Vector right) {
            return left.rem(right);
        }

        @Specialization
        protected LLVMI32Vector rem(LLVMI32Vector left, LLVMI32Vector right) {
            return left.rem(right);
        }

        @Specialization
        protected LLVMI64Vector rem(LLVMI64Vector left, LLVMI64Vector right) {
            return left.rem(right);
        }

        @Specialization
        protected LLVMI8Vector rem(LLVMI8Vector left, LLVMI8Vector right) {
            return left.rem(right);
        }
    }

    public abstract static class LLVMURemNode extends LLVMArithmeticNode {

        @Specialization
        protected byte urem(byte left, byte right) {
            return (byte) (Byte.toUnsignedInt(left) % Byte.toUnsignedInt(right));
        }

        @Specialization
        protected short urem(short left, short right) {
            return (short) (Short.toUnsignedInt(left) % Short.toUnsignedInt(right));
        }

        @Specialization
        protected boolean urem(@SuppressWarnings("unused") boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return false;
        }

        @Specialization
        protected int urem(int left, int right) {
            return Integer.remainderUnsigned(left, right);
        }

        @Specialization
        protected long urem(long left, long right) {
            return Long.remainderUnsigned(left, right);
        }

        @Specialization
        protected long urem(LLVMPointer left, long right) {
            return left.urem(right);
        }

        @Specialization
        protected long urem(LLVMPointer left, LLVMNativePointer right) {
            return urem(left, right.asNative());
        }

        @Specialization
        protected long urem(LLVMPointer left, LLVMManagedPointer right,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
            return urem(left, toNative.executeWithTarget(right).asNative());
        }

        @Specialization
        protected long urem(long left, LLVMNativePointer right) {
            return urem(left, right.asNative());
        }

        @Specialization
        protected long urem(long left, LLVMManagedPointer right,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
            return urem(left, toNative.executeWithTarget(right).asNative());
        }

        @Specialization
        protected LLVMIVarBit urem(LLVMIVarBit left, LLVMIVarBit right) {
            return left.unsignedRem(right);
        }

        @Specialization
        protected LLVMI16Vector urem(LLVMI16Vector left, LLVMI16Vector right) {
            return left.remUnsigned(right);
        }

        @Specialization
        protected LLVMI1Vector urem(LLVMI1Vector left, LLVMI1Vector right) {
            return left.remUnsigned(right);
        }

        @Specialization
        protected LLVMI32Vector urem(LLVMI32Vector left, LLVMI32Vector right) {
            return left.remUnsigned(right);
        }

        @Specialization
        protected LLVMI64Vector urem(LLVMI64Vector left, LLVMI64Vector right) {
            return left.remUnsigned(right);
        }

        @Specialization
        protected LLVMI8Vector urem(LLVMI8Vector left, LLVMI8Vector right) {
            return left.remUnsigned(right);
        }
    }
}
