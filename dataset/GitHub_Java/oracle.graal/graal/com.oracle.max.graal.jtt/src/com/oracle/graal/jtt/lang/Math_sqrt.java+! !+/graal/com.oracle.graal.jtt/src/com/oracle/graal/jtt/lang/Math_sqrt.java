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
package com.oracle.graal.jtt.lang;

import org.junit.*;

/*
 */
public class Math_sqrt {

    @SuppressWarnings("serial")
    public static class NaN extends Throwable {
    }

    public static double test(double arg) throws NaN {
        double v = Math.sqrt(arg);
        if (Double.isNaN(v)) {
            // NaN can't be tested against itself
            throw new NaN();
        }
        return v;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(2.0d, test(4.0d), 0);
    }

    @Test(expected = com.oracle.graal.jtt.lang.Math_sqrt.NaN.class)
    public void run1() throws Throwable {
        test(java.lang.Double.NaN);
    }

    @Test(expected = com.oracle.graal.jtt.lang.Math_sqrt.NaN.class)
    public void run2() throws Throwable {
        test(-1.0d);
    }

    @Test(expected = com.oracle.graal.jtt.lang.Math_sqrt.NaN.class)
    public void run3() throws Throwable {
        test(java.lang.Double.NEGATIVE_INFINITY);
    }

    @Test
    public void run4() throws Throwable {
        Assert.assertEquals(java.lang.Double.POSITIVE_INFINITY, test(java.lang.Double.POSITIVE_INFINITY), 0);
    }

    @Test
    public void run5() throws Throwable {
        Assert.assertEquals(0.0d, test(0.0d), 0);
    }

    @Test
    public void run6() throws Throwable {
        Assert.assertEquals(-0.0d, test(-0.0d), 0);
    }

}
