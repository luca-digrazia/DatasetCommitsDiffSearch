/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.optimize;

import org.junit.*;

/*
 * Tests optimization of float operations.
 */
public class VN_Float01 {

    public static float test(float arg) {
        if (arg == 0) {
            return add(arg + 10);
        }
        if (arg == 1) {
            return sub(arg + 10);
        }
        if (arg == 2) {
            return mul(arg + 10);
        }
        if (arg == 3) {
            return div(arg + 10);
        }
        return 0;
    }

    public static float add(float x) {
        float c = 1;
        float t = x + c;
        float u = x + c;
        return t + u;
    }

    public static float sub(float x) {
        float c = 1;
        float t = x - c;
        float u = x - c;
        return t - u;
    }

    public static float mul(float x) {
        float c = 1;
        float t = x * c;
        float u = x * c;
        return t * u;
    }

    public static float div(float x) {
        float c = 1;
        float t = x / c;
        float u = x / c;
        return t / u;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(22f, test(0f), 0);
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(0f, test(1f), 0);
    }

    @Test
    public void run2() throws Throwable {
        Assert.assertEquals(144f, test(2f), 0);
    }

    @Test
    public void run3() throws Throwable {
        Assert.assertEquals(1f, test(3f), 0);
    }

}
