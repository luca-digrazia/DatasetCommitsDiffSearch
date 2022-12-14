/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.api.code.ValueUtil.asFloatReg;
import static com.oracle.graal.api.code.ValueUtil.asIntReg;
import static com.oracle.graal.api.code.ValueUtil.asLongReg;
import static com.oracle.graal.api.code.ValueUtil.isConstant;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.CONST;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.HINT;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.STACK;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.asm.sparc.SPARCAssembler;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Add;
import static com.oracle.graal.asm.sparc.SPARCAssembler.And;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fadds;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Fsubs;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Mulx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Or;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Sdivx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Sll;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Srl;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Sra;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Sub;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Xor;

import com.oracle.graal.graph.GraalInternalError;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.asm.TargetMethodAssembler;

// @formatter:off
public enum SPARCArithmetic {
    IADD, ISUB, IMUL, IDIV, IDIVREM, IREM, IUDIV, IUREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
    LADD, LSUB, LMUL, LDIV, LDIVREM, LREM, LUDIV, LUREM, LAND, LOR, LXOR, LSHL, LSHR, LUSHR,
    FADD, FSUB, FMUL, FDIV, FREM, FAND, FOR, FXOR,
    DADD, DSUB, DMUL, DDIV, DREM, DAND, DOR, DXOR,
    INEG, LNEG, FNEG, DNEG,
    I2L, L2I, I2B, I2C, I2S,
    F2D, D2F,
    I2F, I2D, F2I, D2I,
    L2F, L2D, F2L, D2L,
    MOV_I2F, MOV_L2D, MOV_F2I, MOV_D2L;

    public static class Op2Stack extends SPARCLIRInstruction {
        @Opcode private final SPARCArithmetic opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value x;
        @Alive({REG, STACK, CONST}) protected Value y;

        public Op2Stack(SPARCArithmetic opcode, Value result, Value x, Value y) {
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            emit(tasm, masm, opcode, result, x, y, null);
        }

        @Override
        public void verify() {
            super.verify();
            verifyKind(opcode, result, x, y);
        }
    }

    public static void emit(TargetMethodAssembler tasm, SPARCAssembler masm, SPARCArithmetic opcode, Value dst, Value src1, Value src2, LIRFrameState info) {
        int exceptionOffset = -1;
        if (isConstant(src1)) {
            switch (opcode) {
            case ISUB:  throw new InternalError("NYI");
            case IAND:  throw new InternalError("NYI");
            case IDIV:  throw new InternalError("NYI");
            case FSUB:  throw new InternalError("NYI");
            case FDIV:  throw new InternalError("NYI");
            case DSUB:  throw new InternalError("NYI");
            case DDIV:  throw new InternalError("NYI");
            default:
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(src2)) {
            if (is_simm13(tasm.asIntConst(src2))) {
                switch (opcode) {
                case IADD:
                    new Add(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                    break;
                case ISUB:
                    new Sub(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                    break;
                case IMUL:
                    new Mulx(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                    break;
                case IAND:
                    new And(masm, asIntReg(src1), tasm.asIntConst(src2), asIntReg(dst));
                    break;
                case ISHL:  throw new InternalError("NYI");
                case ISHR:  throw new InternalError("NYI");
                case IUSHR: throw new InternalError("NYI");
                case IXOR:  throw new InternalError("NYI");
                case LXOR:  throw new InternalError("NYI");
                case LUSHR: throw new InternalError("NYI");
                case FADD:  throw new InternalError("NYI");
                case FMUL:  throw new InternalError("NYI");
                case FDIV:  throw new InternalError("NYI");
                case DADD:  throw new InternalError("NYI");
                case DMUL:  throw new InternalError("NYI");
                case DDIV:  throw new InternalError("NYI");
                default:
                    throw GraalInternalError.shouldNotReachHere();
                }
            } else {
                throw new InternalError("NYI");
            }
        } else {
            switch (opcode) {
            case IADD:
                new Add(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISUB:
                new Sub(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IMUL:
                new Mulx(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IDIV:
                new Sdivx(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IAND:
                new And(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IOR:
                new Or(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IXOR:
                new Xor(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISHL:
                new Sll(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case ISHR:
                new Srl(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IUSHR:
                new Sra(masm, asIntReg(src1), asIntReg(src2), asIntReg(dst));
                break;
            case IREM:  throw new InternalError("NYI");
            case LADD:
                new Add(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LSUB:
                new Sub(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LMUL:
                new Mulx(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LDIV:
                new Sdivx(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LAND:
                new And(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LOR:
                new Or(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LXOR:
                new Xor(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LSHL:
                new Sll(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LSHR:
                new Srl(masm, asLongReg(src1), asLongReg(src2), asLongReg(dst));
                break;
            case LUSHR:
                new Sra(masm, asLongReg(src1), asLongReg(src2), asIntReg(dst));
                break;
            case LREM:  throw new InternalError("NYI");
            case FADD:
                new Fadds(masm, asFloatReg(src1), asFloatReg(src2), asFloatReg(dst));
                break;
            case FSUB:
                new Fsubs(masm, asFloatReg(src1), asFloatReg(src2), asFloatReg(dst));
                break;
            case FMUL:  throw new InternalError("NYI");
            case FDIV:  throw new InternalError("NYI");
            case FREM:  throw new InternalError("NYI");
            case DADD:  throw new InternalError("NYI");
            case DSUB:  throw new InternalError("NYI");
            case DMUL:  throw new InternalError("NYI");
            case DDIV:  throw new InternalError("NYI");
            case DREM:  throw new InternalError("NYI");
            default:
                throw GraalInternalError.shouldNotReachHere("missing: "  + opcode);
            }
        }

        if (info != null) {
            assert exceptionOffset != -1;
            tasm.recordImplicitException(exceptionOffset, info);
        }
    }

    private static final int max13 =  ((1 << 12) - 1);
    private static final int min13 = -(1 << 12);

    private static boolean is_simm13(int src) {
        return min13 <= src && src <= max13;
    }

    private static void verifyKind(SPARCArithmetic opcode, Value result, Value x, Value y) {
        if (((opcode.name().startsWith("I") && result.getKind() == Kind.Int && x.getKind().getStackKind() == Kind.Int && y.getKind().getStackKind() == Kind.Int)
            || (opcode.name().startsWith("L") && result.getKind() == Kind.Long && x.getKind() == Kind.Long && y.getKind() == Kind.Long)
            || (opcode.name().startsWith("F") && result.getKind() == Kind.Float && x.getKind() == Kind.Float && y.getKind() == Kind.Float)
            || (opcode.name().startsWith("D") && result.getKind() == Kind.Double && x.getKind() == Kind.Double && y.getKind() == Kind.Double)) == false) {
                throw GraalInternalError.shouldNotReachHere("opcode: "  + opcode.name() + " x: " + x.getKind() + " y: " + y.getKind());
        }
    }
}
