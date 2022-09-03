/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.intrinsics;

@EspressoIntrinsics
public class Target_java_lang_StrictMath {
    @Intrinsic
    public static double log(double a) {
        return StrictMath.log(a);
    }

    @Intrinsic
    public static double sin(double a) {
        return StrictMath.sin(a);
    }

    @Intrinsic
    public static double cos(double a) {
        return StrictMath.cos(a);
    }

    @Intrinsic
    public static double sqrt(double a) {
        return StrictMath.sqrt(a);
    }

    @Intrinsic
    public static double tan(double a) {
        return StrictMath.tan(a);
    }

    @Intrinsic
    public static double exp(double a) {
        return StrictMath.exp(a);
    }

    @Intrinsic
    public static double log10(double a) {
        return StrictMath.log10(a);
    }

    @Intrinsic
    public static double pow(double a, double b) {
        return StrictMath.pow(a, b);
    }
}
