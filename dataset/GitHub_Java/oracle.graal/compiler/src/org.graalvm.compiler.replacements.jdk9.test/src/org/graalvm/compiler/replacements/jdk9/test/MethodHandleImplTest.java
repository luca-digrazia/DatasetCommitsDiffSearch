/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.replacements.jdk9.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.graalvm.compiler.replacements.test.MethodSubstitutionTest;
import org.junit.Test;

public class MethodHandleImplTest extends MethodSubstitutionTest {

    static final MethodHandle squareHandle;
    static {
        try {
            squareHandle = MethodHandles.lookup().findStatic(MethodHandleImplTest.class, "square", MethodType.methodType(int.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public static int square(int a) {
        return a * a;
    }

    public static int invokeSquare() {
        try {
            return (int) squareHandle.invokeExact(6);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Test {@code MethodHandleImpl.isCompileConstant} by effect: If it is not intrinsified,
     * {@code Invoke#Invokers.maybeCustomize(mh)} will appear in the graph.
     */
    @Test
    public void testIsCompileConstant() {
        test("invokeSquare");
        testGraph("invokeSquare");
    }

}
