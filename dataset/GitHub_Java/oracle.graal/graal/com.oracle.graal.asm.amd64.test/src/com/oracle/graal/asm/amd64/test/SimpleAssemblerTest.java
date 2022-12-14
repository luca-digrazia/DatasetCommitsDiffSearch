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
package com.oracle.graal.asm.amd64.test;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.test.*;

public class SimpleAssemblerTest extends AssemblerTest<AMD64MacroAssembler> {

    @Override
    protected AMD64MacroAssembler createAssembler(TargetDescription target, RegisterConfig registerConfig) {
        return new AMD64MacroAssembler(target, registerConfig);
    }

    @Test
    public void intTest() {
        CodeGenTest<AMD64Assembler> test = new CodeGenTest<AMD64Assembler>() {

            @Override
            public void generateCode(CompilationResult compResult, AMD64Assembler asm, RegisterConfig registerConfig) {
                Register ret = registerConfig.getReturnRegister(Kind.Int);
                asm.movl(ret, 8472);
                asm.ret(0);
            }
        };
        assertReturn("intStub", test, 8472);
    }

    @Test
    public void doubleTest() {
        CodeGenTest<AMD64MacroAssembler> test = new CodeGenTest<AMD64MacroAssembler>() {

            @Override
            public void generateCode(CompilationResult compResult, AMD64MacroAssembler asm, RegisterConfig registerConfig) {
                Register ret = registerConfig.getReturnRegister(Kind.Double);
                compResult.recordDataReference(asm.codeBuffer.position(), Constant.forDouble(84.72), 8, false);
                asm.movdbl(ret, Address.Placeholder);
                asm.ret(0);
            }
        };
        assertReturn("doubleStub", test, 84.72);
    }

    public static int intStub() {
        return 0;
    }

    public static double doubleStub() {
        return 0.0;
    }
}
