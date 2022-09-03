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
package com.oracle.max.graal.jtt.lang;

import org.junit.*;

@SuppressWarnings("static-method")
public final class Object_getClass01 {

    static final Object object = new Object();
    static final Object string = new String();
    static final Object_getClass01 thisObject = new Object_getClass01();

    public static String test(int i) {
        if (i == 0) {
            return object.getClass().toString();
        }
        if (i == 1) {
            return string.getClass().toString();
        }
        if (i == 2) {
            return thisObject.getClass().toString();
        }
        if (i == 3) {
            return thisObject.getClass().getClass().toString();
        }
        return null;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals("class java.lang.Object", test(0));
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals("class java.lang.String", test(1));
    }

    @Test
    public void run2() throws Throwable {
        Assert.assertEquals("class com.oracle.max.graal.jtt.lang.Object_getClass01", test(2));
    }

    @Test
    public void run3() throws Throwable {
        Assert.assertEquals("class java.lang.Class", test(3));
    }

    @Test
    public void run4() throws Throwable {
        Assert.assertEquals(null, test(4));
    }

}
