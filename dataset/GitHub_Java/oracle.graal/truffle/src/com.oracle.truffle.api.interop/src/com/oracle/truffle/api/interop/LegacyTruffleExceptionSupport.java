/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.interop.ExceptionType.CANCEL;
import static com.oracle.truffle.api.interop.ExceptionType.EXIT;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.source.SourceSection;
import static com.oracle.truffle.api.interop.ExceptionType.RUNTIME_ERROR;
import static com.oracle.truffle.api.interop.ExceptionType.PARSE_ERROR;

@SuppressWarnings({"unused", "deprecation"})
final class LegacyTruffleExceptionSupport {

    private LegacyTruffleExceptionSupport() {
    }

    static boolean isException(Object receiver) {
        return receiver instanceof Throwable && receiver instanceof com.oracle.truffle.api.TruffleException &&
                        !((com.oracle.truffle.api.TruffleException) receiver).isInternalError();
    }

    static RuntimeException throwException(Object receiver) {
        throw sthrow(RuntimeException.class, (Throwable) receiver);
    }

    static boolean isExceptionUnwind(Object receiver) {
        return receiver instanceof ThreadDeath;
    }

    static ExceptionType getExceptionType(Object receiver) {
        com.oracle.truffle.api.TruffleException truffleException = (com.oracle.truffle.api.TruffleException) receiver;
        if (truffleException.isCancelled()) {
            return CANCEL;
        } else if (truffleException.isExit()) {
            return EXIT;
        } else if (truffleException.isSyntaxError()) {
            return PARSE_ERROR;
        } else {
            return RUNTIME_ERROR;
        }
    }

    static boolean isExceptionIncompleteSource(Object receiver) {
        return ((com.oracle.truffle.api.TruffleException) receiver).isIncompleteSource();
    }

    static int getExceptionExitStatus(Object receiver) throws UnsupportedMessageException {
        com.oracle.truffle.api.TruffleException truffleException = (com.oracle.truffle.api.TruffleException) receiver;
        if (truffleException.isExit()) {
            return truffleException.getExitStatus();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    static boolean hasSourceLocation(Object receiver) {
        return ((com.oracle.truffle.api.TruffleException) receiver).getSourceLocation() != null;
    }

    static SourceSection getSourceLocation(Object receiver) throws UnsupportedMessageException {
        SourceSection sourceLocation = ((com.oracle.truffle.api.TruffleException) receiver).getSourceLocation();
        if (sourceLocation == null) {
            throw UnsupportedMessageException.create();
        }
        return sourceLocation;
    }

    @TruffleBoundary
    static boolean hasExceptionCause(Object receiver) {
        Throwable cause = ((Throwable) receiver).getCause();
        if (!(cause instanceof com.oracle.truffle.api.TruffleException)) {
            return false;
        }
        return true;
    }

    @TruffleBoundary
    static Object getExceptionCause(Object receiver) throws UnsupportedMessageException {
        if (!hasExceptionCause(receiver)) {
            throw UnsupportedMessageException.create();
        } else {
            return ((Throwable) receiver).getCause();
        }
    }

    @TruffleBoundary
    static boolean hasExceptionSuppressed(Object receiver) {
        return InteropAccessor.EXCEPTION.hasExceptionSuppressed(receiver);
    }

    @TruffleBoundary
    static Object getExceptionSuppressed(Object receiver) throws UnsupportedMessageException {
        return InteropAccessor.EXCEPTION.getExceptionSuppressed(receiver);
    }

    static boolean hasExceptionMessage(Object receiver) {
        return ((Throwable) receiver).getMessage() != null;
    }

    static Object getExceptionMessage(Object receiver) throws UnsupportedMessageException {
        String message = ((Throwable) receiver).getMessage();
        if (message == null) {
            throw UnsupportedMessageException.create();
        }
        return message;
    }

    @TruffleBoundary
    static boolean hasExceptionStackTrace(Object receiver) {
        return TruffleStackTrace.fillIn((Throwable) receiver) != null;
    }

    @TruffleBoundary
    static Object getExceptionStackTrace(Object receiver) throws UnsupportedMessageException {
        return InteropAccessor.EXCEPTION.getExceptionStackTrace(receiver);
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T sthrow(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }
}
