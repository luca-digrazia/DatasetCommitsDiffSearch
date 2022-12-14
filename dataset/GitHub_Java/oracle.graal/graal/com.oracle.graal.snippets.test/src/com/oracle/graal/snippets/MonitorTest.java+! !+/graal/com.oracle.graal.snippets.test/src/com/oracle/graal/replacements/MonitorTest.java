/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.snippets;

import org.junit.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.virtual.phases.ea.*;

public class MonitorTest extends GraalCompilerTest {

    @Test
    public void test0() {
        test("lockObjectSimple", new Object(), new Object());
        test("lockObjectSimple", new Object(), null);
    }

    @Test
    public void test0_1() {
        test("lockThisSimple", "test1", new Object());
        test("lockThisSimple", "test1", null);
    }

    @Test
    public void test0_2() {
        test("lockObjectSimple", null, "test1");
    }

    @Test
    public void test1_1() {
        test("lockObject", new Object(), "test1", new String[1]);
    }

    @Test
    public void test1_2() {
        test("lockObject", null, "test1_1", new String[1]);
    }

    @Test
    public void test2() {
        test("lockThis", "test2", new String[1]);
    }

    /**
     * Tests monitor operations on {@link PartialEscapeAnalysisPhase virtual objects}.
     */
    @Test
    public void test3() {
        test("lockLocalObject", "test3", new String[1]);
    }

    /**
     * Tests recursive locking of objects which should be biasable.
     */
    @Test
    public void test4() {
        Chars src = new Chars("1234567890".toCharArray());
        Chars dst = new Chars(src.data.length);
        test("copyObj", src, dst, 100);
    }

    /**
     * Tests recursive locking of objects which do not appear to be biasable.
     */
    @Test
    public void test5() {
        char[] src = "1234567890".toCharArray();
        char[] dst = new char[src.length];
        test("copyArr", src, dst, 100);
    }

    /**
     * Extends {@link #test4()} with contention.
     */
    @Test
    public void test6() {
        Chars src = new Chars("1234567890".toCharArray());
        Chars dst = new Chars(src.data.length);
        int n = Runtime.getRuntime().availableProcessors();
        testN(n, "copyObj", src, dst, 100);
    }

    /**
     * Extends {@link #test5()} with contention.
     */
    @Test
    public void test7() {
        char[] src = "1234567890".toCharArray();
        char[] dst = new char[src.length];
        int n = Runtime.getRuntime().availableProcessors();
        testN(n, "copyArr", src, dst, 100);
    }

    private static String setAndGet(String[] box, String value) {
        synchronized (box) {
            box[0] = null;
        }

        // Do a GC while a object is locked (by the caller)
        System.gc();

        synchronized (box) {
            box[0] = value;
        }
        return box[0];
    }

    public static Object lockObjectSimple(Object o, Object value) {
        synchronized (o) {
            value.hashCode();
            return value;
        }
    }

    public String lockThisSimple(String value, Object o) {
        synchronized (this) {
            synchronized (value) {
                o.hashCode();
                return value;
            }
        }
    }

    public static String lockObject(Object o, String value, String[] box) {
        synchronized (o) {
            return setAndGet(box, value);
        }
    }

    public String lockThis(String value, String[] box) {
        synchronized (this) {
            return setAndGet(box, value);
        }
    }

    public static String lockLocalObject(String value, String[] box) {
        Object o = new Object();
        synchronized (o) {
            return setAndGet(box, value);
        }
    }

    static class Chars {

        final char[] data;

        public Chars(int size) {
            this.data = new char[size];
        }

        public Chars(char[] data) {
            this.data = data;
        }
    }

    public static String copyObj(Chars src, Chars dst, int n) {
        System.out.println(Thread.currentThread().getName() + " reps=" + n + ", src.length=" + src.data.length);
        int total = 0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < src.data.length; i++) {
                synchronized (src) {
                    synchronized (dst) {
                        synchronized (src) {
                            synchronized (dst) {
                                dst.data[i] = src.data[i];
                                total++;
                            }
                        }
                    }
                }
            }
        }
        System.out.println(Thread.currentThread().getName() + " total " + total);
        return new String(dst.data);
    }

    public static String copyArr(char[] src, char[] dst, int n) {
        System.out.println(Thread.currentThread().getName() + " reps=" + n + ", src.length=" + src.length);
        int total = 0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < src.length; i++) {
                synchronized (src) {
                    synchronized (dst) {
                        synchronized (src) {
                            synchronized (dst) {
                                dst[i] = src[i];
                                total++;
                            }
                        }
                    }
                }
            }
        }
        System.out.println(Thread.currentThread().getName() + " total " + total);
        return new String(dst);
    }
}
