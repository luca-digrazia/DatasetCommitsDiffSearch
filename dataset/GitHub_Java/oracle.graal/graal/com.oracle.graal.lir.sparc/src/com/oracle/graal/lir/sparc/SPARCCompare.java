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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.isSimm13;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.asm.sparc.SPARCAssembler.Cmp;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.asm.*;

//@formatter:off
public enum SPARCCompare {
    ICMP, LCMP, ACMP, FCMP, DCMP;

    public static class CompareOp extends SPARCLIRInstruction {

        @Opcode private final SPARCCompare opcode;
        @Use({REG, STACK, CONST}) protected Value x;
        @Use({REG, STACK, CONST}) protected Value y;

        public CompareOp(SPARCCompare opcode, Value x, Value y) {
            this.opcode = opcode;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            emit(tasm, masm, opcode, x, y);
        }

        @Override
        protected void verify() {
            super.verify();
            assert (name().startsWith("I") && x.getKind() == Kind.Int && y.getKind().getStackKind() == Kind.Int)
                || (name().startsWith("L") && x.getKind() == Kind.Long && y.getKind() == Kind.Long)
                || (name().startsWith("A") && x.getKind() == Kind.Object && y.getKind() == Kind.Object)
                || (name().startsWith("F") && x.getKind() == Kind.Float && y.getKind() == Kind.Float)
                || (name().startsWith("D") && x.getKind() == Kind.Double && y.getKind() == Kind.Double);
        }
    }

    @SuppressWarnings("unused")
    public static void emit(TargetMethodAssembler tasm, SPARCAssembler masm, SPARCCompare opcode, Value x, Value y) {
        if (isRegister(y)) {
            switch (opcode) {
                case ICMP:
                    new Cmp(masm, asIntReg(x), asIntReg(y));
                    break;
                case LCMP:
                    new Cmp(masm, asLongReg(x), asLongReg(y));
                    break;
                case ACMP:
                    // masm.cmpptr(asObjectReg(x), asObjectReg(y));
                    break;
                case FCMP:
                    // masm.ucomiss(asFloatReg(x), asFloatReg(y));
                    break;
                case DCMP:
                    // masm.ucomisd(asDoubleReg(x), asDoubleReg(y));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(y)) {
            switch (opcode) {
                case ICMP:
                    assert isSimm13(tasm.asIntConst(y));
                    new Cmp(masm, asIntReg(x), tasm.asIntConst(y));
                    break;
                case LCMP:
                    assert isSimm13(tasm.asIntConst(y));
                    new Cmp(masm, asLongReg(x), tasm.asIntConst(y));
                    break;
                case ACMP:
                    if (((Constant) y).isNull()) {
                        // masm.cmpq(asObjectReg(x), 0);
                        break;
                    } else {
                        throw GraalInternalError.shouldNotReachHere("Only null object constants are allowed in comparisons");
                    }
                case FCMP:
                    // masm.ucomiss(asFloatReg(x), (AMD64Address) tasm.asFloatConstRef(y));
                    break;
                case DCMP:
                    // masm.ucomisd(asDoubleReg(x), (AMD64Address) tasm.asDoubleConstRef(y));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (opcode) {
                case ICMP:
                    // masm.cmpl(asIntReg(x), (AMD64Address) tasm.asIntAddr(y)); 
                    break;
                case LCMP:
                    // masm.cmpq(asLongReg(x), (AMD64Address) tasm.asLongAddr(y));
                    break;
                case ACMP:
                    // masm.cmpptr(asObjectReg(x), (AMD64Address) tasm.asObjectAddr(y));
                    break;
                case FCMP:
                    // masm.ucomiss(asFloatReg(x), (AMD64Address) tasm.asFloatAddr(y));
                    break;
                case DCMP:
                    // masm.ucomisd(asDoubleReg(x), (AMD64Address) tasm.asDoubleAddr(y)); 
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }
}
