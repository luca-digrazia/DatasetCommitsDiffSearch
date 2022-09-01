/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.jdk9.test;

import static org.junit.Assume.assumeTrue;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;

public final class MathFMAConstantInputTest extends GraalCompilerTest {

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    public static float floatFMA() {
        return Math.fma(2.0f, 2.0f, 2.0f);
    }

    @Test
    public void testFloatFMA() {
        test("floatFMA");
    }

    public static double doubleFMA() {
        return Math.fma(2.0d, 2.0d, 2.0d);
    }

    @Test
    public void testDoubleFMA() {
        test("doubleFMA");
    }

}
