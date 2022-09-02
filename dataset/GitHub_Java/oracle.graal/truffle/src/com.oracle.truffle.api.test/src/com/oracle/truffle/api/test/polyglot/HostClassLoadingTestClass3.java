/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import java.io.IOException;
import java.io.InputStream;

/**
 * This class is used in {@link HostClassLoadingTest}. Renaming this class might be requiring
 * changes.
 */
public final class HostClassLoadingTestClass3 {

    public static int staticField = 42;

    public static long testMethod() throws IOException {
        String path = "/" + HostClassLoadingTestClass3.class.getName().replace('.', '/') + ".class";

        InputStream stream1 = HostClassLoadingTestClass3.class.getResourceAsStream(path);
        long size = countBytes(stream1);

        InputStream stream2 = HostClassLoadingTestClass3.class.getResourceAsStream(path);
        InputStream stream3 = HostClassLoadingTestClass3.class.getResourceAsStream(path);

        long skipped1 = checkedSkip(stream2, size / 2);
        checkedSkip(stream3, size);
        checkedSkip(stream2, size - skipped1);

        assertAtEOF(stream1);
        assertAtEOF(stream2);
        assertAtEOF(stream3);

        stream3.close();
        stream2.close();
        stream1.close();

        return size;
    }

    static long countBytes(InputStream stream) throws IOException {
        return stream.skip(Integer.MAX_VALUE);
    }

    private static long checkedSkip(InputStream stream, long skip) throws IOException {
        long skipped = stream.skip(skip);
        if (skipped != skip) {
            throw new AssertionError("Unexpected number of bytes skipped");
        }
        return skipped;
    }

    private static void assertAtEOF(InputStream stream) throws IOException, AssertionError {
        int b = stream.read();
        if (b != -1) {
            throw new AssertionError("Expected end of stream");
        }
    }
}
