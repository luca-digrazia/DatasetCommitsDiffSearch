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
package com.oracle.max.graal.jtt.reflect;

import java.lang.reflect.*;

import org.junit.*;

public class Array_newInstance04 {

    public static boolean test(int i, int j) {
        final int[] dims = {i, j};
        return Array.newInstance(Array_newInstance04.class, dims) != null;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(true, test(1, 0));
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(true, test(2, 2));
    }

    @Test
    public void run2() throws Throwable {
        Assert.assertEquals(true, test(3, 2));
    }

    @Test(expected = java.lang.NegativeArraySizeException.class)
    public void run3() throws Throwable {
        test(0, -1);
    }

}
