/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
/*
 */
package com.oracle.max.graal.jtt.except;

import org.junit.*;

public class BC_laload {

    static long[] arr = {0L, -1L, 4L, 1000000000000L};

    public static long test(int arg) {
        final long[] array = arg == -2 ? null : arr;
        return array[arg];
    }

    @Test(expected = java.lang.NullPointerException.class)
    public void run0() throws Throwable {
        test(-2);
    }

    @Test(expected = java.lang.ArrayIndexOutOfBoundsException.class)
    public void run1() throws Throwable {
        test(-1);
    }

    @Test
    public void run2() throws Throwable {
        Assert.assertEquals(0L, test(0));
    }

    @Test(expected = java.lang.ArrayIndexOutOfBoundsException.class)
    public void run3() throws Throwable {
        test(4);
    }

}
