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
package com.oracle.graal.asm.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.test.*;

public abstract class AssemblerTest extends GraalTest {

    protected final CodeCacheProvider codeCache;

    public interface CodeGenTest {

        Buffer generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig);
    }

    public AssemblerTest() {
        this.codeCache = Graal.getRequiredCapability(CodeCacheProvider.class);
    }

    protected InstalledCode assembleMethod(Method m, CodeGenTest test) {
        ResolvedJavaMethod method = codeCache.lookupJavaMethod(m);
        RegisterConfig registerConfig = codeCache.lookupRegisterConfig(method);

        CompilationResult compResult = new CompilationResult();

        Buffer codeBuffer = test.generateCode(compResult, codeCache.getTarget(), registerConfig);
        compResult.setTargetCode(codeBuffer.close(true), codeBuffer.position());

        return codeCache.addMethod(method, compResult, null);
    }

    protected void assertReturn(String methodName, CodeGenTest test, Object expected, Object... args) {
        Method method = getMethod(methodName);
        InstalledCode code = assembleMethod(method, test);

        Object actual = code.executeVarargs(args);
        Assert.assertEquals("unexpected return value: " + actual, actual, expected);
    }
}
