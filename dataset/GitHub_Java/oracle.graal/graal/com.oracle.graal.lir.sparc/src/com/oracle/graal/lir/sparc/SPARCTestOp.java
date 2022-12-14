/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.Ldsw;
import com.oracle.graal.asm.sparc.SPARCAssembler.Ldx;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Cmp;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.asm.*;

public class SPARCTestOp extends SPARCLIRInstruction {

    @Use({REG}) protected Value x;
    @Use({REG, STACK, CONST}) protected Value y;

    public SPARCTestOp(Value x, Value y) {
        this.x = x;
        this.y = y;
    }

    @Override
    @SuppressWarnings("unused")
    public void emitCode(TargetMethodAssembler tasm, SPARCAssembler asm) {
        if (isRegister(y)) {
            switch (x.getKind()) {
                case Int:
                    new Cmp(asm, asIntReg(x), asIntReg(y));
                    break;
                case Long:
                    new Cmp(asm, asLongReg(x), asLongReg(y));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(y)) {
            switch (x.getKind()) {
                case Int:
                    new Cmp(asm, asIntReg(x), tasm.asIntConst(y));
                    break;
                case Long:
                    new Cmp(asm, asLongReg(x), tasm.asIntConst(y));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (x.getKind()) {
                case Int:
                    new Ldsw(asm, (SPARCAddress) tasm.asIntAddr(y), asIntReg(y));
                    new Cmp(asm, asIntReg(x), asIntReg(y));
                    break;
                case Long:
                    new Ldx(asm, (SPARCAddress) tasm.asLongAddr(y), asLongReg(y));
                    new Cmp(asm, asLongReg(x), asLongReg(y));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

}
