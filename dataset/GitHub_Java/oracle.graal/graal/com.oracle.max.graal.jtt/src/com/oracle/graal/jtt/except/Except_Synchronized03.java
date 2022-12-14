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
package com.oracle.max.graal.jtt.except;

import org.junit.*;

/*
 */
public class Except_Synchronized03 {

    static final Except_Synchronized03 object = new Except_Synchronized03();

    int x = 1;

    public static int test(int i) throws Exception {
        if (i == 0) {
            return 0;
        }
        return object.test2(i);
    }

    @SuppressWarnings("all")
    public synchronized int test2(int i) throws Exception {
        while (true) {
            try {
                synchronized (this) {
                    Except_Synchronized03 object = null;
                    return object.x;
                }
            } catch (NullPointerException e) {
                return 2;
            }
        }
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(0, test(0));
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(2, test(1));
    }

}
