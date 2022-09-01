/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.graalvm.wasm.utils;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class Assert {
    public static void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail(message);
        }
    }

    public static void assertNotNull(String message, Object object) {
        assertTrue(message, object != null);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        if (!actual.equals(expected)) {
            fail(format("%s %s != %s", message, expected, actual));
        }
    }

    public static void assertFloatEquals(String message, Float expected, Float actual, Float epsilon) {
        if (Math.abs(actual - expected) > epsilon) {
            fail(format("%s %s \u2209 %s +/- %s", message, actual, expected, epsilon));
        }
    }

    public static void assertDoubleEquals(String message, Double expected, Double actual, Double epsilon) {
        if (Math.abs(actual - expected) > epsilon) {
            fail(format("%s %s \u2209 %s +/- %s", message, actual, expected, epsilon));
        }
    }

    public static void fail(String message) {
        throw new RuntimeException(message);
    }

    @TruffleBoundary
    public static String format(String format, Object... args) {
        return String.format(format, args);
    }
}
