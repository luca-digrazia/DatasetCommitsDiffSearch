/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(value = InteropLibrary.class, receiverType = Long.class)
@SuppressWarnings("unused")
final class DefaultLongExports {

    @ExportMessage
    static boolean fitsInByte(Long receiver) {
        long l = receiver;
        return l == (byte) l;
    }

    @ExportMessage
    static boolean fitsInInt(Long receiver) {
        long l = receiver;
        return l == (int) l;
    }

    @ExportMessage
    static boolean fitsInShort(Long receiver) {
        long l = receiver;
        return l == (short) l;
    }

    @ExportMessage
    static boolean fitsInFloat(Long receiver) {
        return NumberUtils.inSafeFloatRange(receiver);
    }

    @ExportMessage
    static boolean fitsInDouble(Long receiver) {
        return NumberUtils.inSafeDoubleRange(receiver);
    }

    @ExportMessage
    static byte asByte(Long receiver) throws UnsupportedMessageException {
        return NumberUtils.asByte(receiver);
    }

    @ExportMessage
    static short asShort(Long receiver) throws UnsupportedMessageException {
        return NumberUtils.asShort(receiver);
    }

    @ExportMessage
    static int asInt(Long receiver) throws UnsupportedMessageException {
        return NumberUtils.asInt(receiver);
    }

    @ExportMessage
    static float asFloat(Long receiver) throws UnsupportedMessageException {
        return NumberUtils.asFloat(receiver);
    }

    @ExportMessage
    static double asDouble(Long receiver) throws UnsupportedMessageException {
        return NumberUtils.asDouble(receiver);
    }

    @ExportMessage
    static boolean isNumber(Long receiver) {
        return true;
    }

    @ExportMessage
    static boolean fitsInLong(Long receiver) {
        return true;
    }

    @ExportMessage
    static long asLong(Long receiver) {
        return receiver;
    }

}
