/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;

/**
 * An exception thrown if a {@link TruffleObject} does not support a interop message.
 *
 * @since 0.11
 */
@SuppressWarnings("deprecation")
public final class UnsupportedMessageException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private static final Message LEGACY = new Message() {

        @Override
        public int hashCode() {
            return 13;
        }

        @Override
        public boolean equals(Object message) {
            return this == message;
        }
    };

    private final Message message;

    private UnsupportedMessageException() {
        this.message = LEGACY;
    }

    private UnsupportedMessageException(Message message) {
        this.message = message;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public String getMessage() {
        return "Message unsupported.";
    }

    /**
     * @since 0.11
     * @deprecated The unsupported message is no longer available. The unsupported message is known
     *             by the caller anyway, therefore it is redundant. Will be removed without
     *             replacement.
     */
    @Deprecated
    public Message getUnsupportedMessage() {
        return message;
    }

    /**
     * @since 0.11
     * @deprecated use {@link #create()} instead. Interop exceptions should directly be thrown and
     *             no longer be hidden as runtime exceptions.
     */
    @Deprecated
    public static RuntimeException raise(Message message) {
        CompilerDirectives.transferToInterpreter();
        return silenceException(RuntimeException.class, new UnsupportedMessageException(message));
    }

    /**
     * Creates an {@link UnsupportedMessageException} to indicate that an {@link InteropLibrary
     * interop} message is not supported.
     *
     * @since 1.0
     */
    public static UnsupportedMessageException create() {
        CompilerDirectives.transferToInterpreter();
        return new UnsupportedMessageException();
    }

}
