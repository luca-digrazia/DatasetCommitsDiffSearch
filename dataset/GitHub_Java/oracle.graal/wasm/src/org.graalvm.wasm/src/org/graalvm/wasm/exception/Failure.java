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

package org.graalvm.wasm.exception;

public enum Failure {
    EXIT(FailureType.EXIT, "program exited"),

    UNSPECIFIED_TRAP(FailureType.TRAP, "unspecified"),
    INT_DIVIDE_BY_ZERO(FailureType.TRAP, "integer divide by zero"),
    INT_OVERFLOW(FailureType.TRAP, "integer overflow"),

    UNSPECIFIED_EXHAUSTION(FailureType.EXHAUSTION, "unspecified"),

    UNSPECIFIED_MALFORMED(FailureType.MALFORMED, "unspecified"),

    UNSPECIFIED_INVALID(FailureType.INVALID, "unspecified"),
    RETURN_STACK_SIZE_MISMATCH(FailureType.INVALID, "type mismatch"),

    UNSPECIFIED_UNLINKABLE(FailureType.UNLINKABLE, "unspecified"),

    UNSPECIFIED_INTERNAL(FailureType.INTERNAL, "unspecified"),
    OTHER_ARITHMETIC_EXCEPTION(FailureType.INTERNAL, "non-standard arithmetic exception");

    public enum FailureType {
        EXIT("exit"),
        TRAP("trap"),
        EXHAUSTION("exhaustion"),
        MALFORMED("malformed"),
        INVALID("invalid"),
        UNLINKABLE("unlinkable"),
        INTERNAL("internal");

        public final String name;

        FailureType(String name) {
            this.name = name;
        }
    }

    public final FailureType type;
    public final String name;

    Failure(FailureType type, String name) {
        this.type = type;
        this.name = name;
    }

    public static Failure fromArithmeticException(ArithmeticException exception) {
        switch (exception.getMessage()) {
            case "/ by zero":
            case "BigInteger divide by zero":
                return Failure.INT_DIVIDE_BY_ZERO;
            default:
                return Failure.OTHER_ARITHMETIC_EXCEPTION;
        }
    }
}
