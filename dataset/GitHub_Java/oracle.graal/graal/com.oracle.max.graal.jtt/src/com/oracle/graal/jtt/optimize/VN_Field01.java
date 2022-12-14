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
package com.oracle.max.graal.jtt.optimize;

import org.junit.*;

/*
 * Tests constant folding of integer operations.
 */
public class VN_Field01 {

    static final VN_Field01 object = new VN_Field01();

    int field = 9;

    public static int test(int arg) {
        if (arg == 0) {
            return test1();
        }
        if (arg == 1) {
            return test2();
        }
        if (arg == 2) {
            return test3();
        }
        return 0;
    }

    private static int test1() {
        VN_Field01 a = object;
        return a.field + a.field;
    }

    private static int test2() {
        VN_Field01 a = object;
        VN_Field01 b = object;
        return a.field + b.field;
    }

    @SuppressWarnings("all")
    private static int test3() {
        VN_Field01 a = null;
        VN_Field01 b = null;
        return a.field + b.field;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(18, test(0));
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(18, test(1));
    }

    @Test(expected = java.lang.NullPointerException.class)
    public void run2() throws Throwable {
        test(2);
    }

}
