/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * Assertions about the code produced by the Truffle compiler. All operations have no effect when
 * either executed in the interpreter or in the compiled code. The assertions are checked during
 * code generation and the Truffle compiler produces for failing assertions a stack trace that
 * identifies the code position of the assertion in the context of the current compilation.
 *
 * @since 0.8 or earlier
 */
public final class CompilerAsserts {
    private CompilerAsserts() {
    }

    /**
     * Assertion that this code position should never be reached during compilation. It can be used
     * for exceptional code paths or rare code paths that should never be included in a compilation
     * unit. See {@link CompilerDirectives#transferToInterpreter()} for the corresponding compiler
     * directive.
     * 
     * @since 0.8 or earlier
     */
    public static void neverPartOfCompilation() {
    }

    /**
     * Assertion that this code position should never be reached during compilation. It can be used
     * for exceptional code paths or rare code paths that should never be included in a compilation
     * unit. See {@link CompilerDirectives#transferToInterpreter()} for the corresponding compiler
     * directive.
     *
     * @param message text associated with the bailout exception
     * @since 0.8 or earlier
     */
    public static void neverPartOfCompilation(String message) {
    }

    /**
     * Assertion that the corresponding value is reduced to a constant during compilation.
     *
     * @param value the value that must be constant during compilation
     * @since 0.8 or earlier
     */
    public static <T> void compilationConstant(Object value) {
        if (!CompilerDirectives.isCompilationConstant(value)) {
            neverPartOfCompilation("Value is not compilation constant");
        }
    }

    /**
     * Assertion that the corresponding value is reduced to a constant during the initial partial
     * evaluation phase.
     *
     * @param value the value that must be constant during compilation
     * @since 0.8 or earlier
     */
    public static <T> void partialEvaluationConstant(Object value) {
    }

    /**
     * Assertion that the corresponding primitive boolean value is reduced to a constant during the
     * initial partial evaluation phase.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.2
     */
    public static <T> void partialEvaluationConstant(boolean value) {
    }

    /**
     * Assertion that the corresponding primitive byte value is reduced to a constant during the
     * initial partial evaluation phase.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.2
     */
    public static <T> void partialEvaluationConstant(byte value) {
    }

    /**
     * Assertion that the corresponding primitive short value is reduced to a constant during the
     * initial partial evaluation phase.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.2
     */
    public static <T> void partialEvaluationConstant(short value) {
    }

    /**
     * Assertion that the corresponding primitive char value is reduced to a constant during the
     * initial partial evaluation phase.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.2
     */
    public static <T> void partialEvaluationConstant(char value) {
    }

    /**
     * Assertion that the corresponding primitive int value is reduced to a constant during the
     * initial partial evaluation phase.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.2
     */
    public static <T> void partialEvaluationConstant(int value) {
    }

    /**
     * Assertion that the corresponding primitive float value is reduced to a constant during the
     * initial partial evaluation phase.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.2
     */
    public static <T> void partialEvaluationConstant(float value) {
    }

    /**
     * Assertion that the corresponding primitive long value is reduced to a constant during the
     * initial partial evaluation phase.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.2
     */
    public static <T> void partialEvaluationConstant(long value) {
    }

    /**
     * Assertion that the corresponding primitive double value is reduced to a constant during the
     * initial partial evaluation phase.
     *
     * @param value the value that must be constant during partial evaluation.
     * @since 19.2
     */
    public static <T> void partialEvaluationConstant(double value) {
    }
}
