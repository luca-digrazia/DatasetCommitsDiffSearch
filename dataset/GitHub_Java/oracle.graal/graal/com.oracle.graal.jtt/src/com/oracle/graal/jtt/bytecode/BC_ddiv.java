/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.jtt.bytecode;

import org.junit.Test;

import com.oracle.graal.jtt.JTTTest;

public class BC_ddiv extends JTTTest {

    public static double test(double a, double b) {
        return a / b;
    }

    // 0.0, -0.0, 1.0, -1.0

    @Test
    public void run0() {
        runTest("test", 0.0d, 0.0d);
    }

    @Test
    public void run1() {
        runTest("test", -0.0d, 0.0d);
    }

    @Test
    public void run2() {
        runTest("test", 0.0d, -0.0d);
    }

    @Test
    public void run3() {
        runTest("test", -0.0d, -0.0d);
    }

    @Test
    public void run4() {
        runTest("test", -1.0d, 0.0d);
    }

    @Test
    public void run5() {
        runTest("test", 0.0d, -1.0d);
    }

    @Test
    public void run6() {
        runTest("test", -1.0d, -1.0d);
    }

    @Test
    public void run7() {
        runTest("test", -1.0d, -0.0d);
    }

    @Test
    public void run8() {
        runTest("test", -0.0d, -1.0d);
    }

    @Test
    public void run9() {
        runTest("test", 311.0d, 0.0d);
    }

    @Test
    public void run10() {
        runTest("test", 0.0d, 311.0d);
    }

    @Test
    public void run11() {
        runTest("test", 311.0d, -0.0d);
    }

    @Test
    public void run12() {
        runTest("test", -0.0d, 311.0d);
    }

    // POSITIVE_INFINITY

    @Test
    public void runPositiveInfinity0() {
        runTest("test", Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity1() {
        runTest("test", Double.POSITIVE_INFINITY, 0.0d);
    }

    @Test
    public void runPositiveInfinity2() {
        runTest("test", Double.POSITIVE_INFINITY, -0.0d);
    }

    @Test
    public void runPositiveInfinity3() {
        runTest("test", Double.POSITIVE_INFINITY, 1.0d);
    }

    @Test
    public void runPositiveInfinity4() {
        runTest("test", Double.POSITIVE_INFINITY, -1.0d);
    }

    @Test
    public void runPositiveInfinity5() {
        runTest("test", Double.POSITIVE_INFINITY, 123.0d);
    }

    @Test
    public void runPositiveInfinity6() {
        runTest("test", Double.POSITIVE_INFINITY, -123.0d);
    }

    @Test
    public void runPositiveInfinity7() {
        runTest("test", 0.0d, Double.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity8() {
        runTest("test", -0.0d, Double.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity9() {
        runTest("test", 1.0d, Double.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity10() {
        runTest("test", -1.0d, Double.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity11() {
        runTest("test", 123.0d, Double.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity12() {
        runTest("test", 1123.0d, Double.POSITIVE_INFINITY);
    }

    // NEGATIVE_INFINITY

    @Test
    public void runNegativeInfinity0() {
        runTest("test", Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity1() {
        runTest("test", Double.NEGATIVE_INFINITY, 0.0d);
    }

    @Test
    public void runNegativeInfinity2() {
        runTest("test", Double.NEGATIVE_INFINITY, -0.0d);
    }

    @Test
    public void runNegativeInfinity3() {
        runTest("test", Double.NEGATIVE_INFINITY, 1.0d);
    }

    @Test
    public void runNegativeInfinity4() {
        runTest("test", Double.NEGATIVE_INFINITY, -1.0d);
    }

    @Test
    public void runNegativeInfinity5() {
        runTest("test", Double.NEGATIVE_INFINITY, 234.0d);
    }

    @Test
    public void runNegativeInfinity6() {
        runTest("test", Double.NEGATIVE_INFINITY, -234.0d);
    }

    @Test
    public void runNegativeInfinity7() {
        runTest("test", 0.0d, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity8() {
        runTest("test", -0.0d, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity9() {
        runTest("test", 1.0d, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity10() {
        runTest("test", -1.0d, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity11() {
        runTest("test", 234.0d, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity12() {
        runTest("test", -234.0d, Double.NEGATIVE_INFINITY);
    }

    // Nan

    @Test
    public void runNaN0() {
        runTest("test", Double.NaN, Double.NaN);
    }

    @Test
    public void runNaN1() {
        runTest("test", Double.NaN, 0.0d);
    }

    @Test
    public void runNaN2() {
        runTest("test", Double.NaN, -0.0d);
    }

    @Test
    public void runNaN3() {
        runTest("test", Double.NaN, 1.0d);
    }

    @Test
    public void runNaN4() {
        runTest("test", Double.NaN, -1.0d);
    }

    @Test
    public void runNaN5() {
        runTest("test", Double.NaN, 345.0d);
    }

    @Test
    public void runNaN6() {
        runTest("test", Double.NaN, -345.0d);
    }

    @Test
    public void runNaN7() {
        runTest("test", 0.0d, Double.NaN);
    }

    @Test
    public void runNaN8() {
        runTest("test", -0.0d, Double.NaN);
    }

    @Test
    public void runNaN9() {
        runTest("test", 1.0d, Double.NaN);
    }

    @Test
    public void runNaN10() {
        runTest("test", -1.0d, Double.NaN);
    }

    @Test
    public void runNaN11() {
        runTest("test", 345.0d, Double.NaN);
    }

    @Test
    public void runNaN12() {
        runTest("test", -345.0d, Double.NaN);
    }

    // Various

    @Test
    public void runVarious() {
        runTest("test", 311.0d, 10d);
    }

}
