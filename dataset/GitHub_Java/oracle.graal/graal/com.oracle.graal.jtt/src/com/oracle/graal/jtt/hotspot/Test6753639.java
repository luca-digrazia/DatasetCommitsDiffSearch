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
package com.oracle.graal.jtt.hotspot;

import com.oracle.graal.jtt.*;
import org.junit.*;

/**
 * @test
 * @bug 6753639
 * @summary Strange optimisation in for loop with cyclic integer condition
 * 
 * @run main/othervm -Xbatch Test6753639
 */

public class Test6753639 extends JTTTest {

    public static int test() {
        int end = Integer.MAX_VALUE;
        int count = 0;
        for (int i = Integer.MAX_VALUE - 5; i <= end; i++) {
            count++;
            if (count > 100000) {
                return 95;
            }
        }
        return 97;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
