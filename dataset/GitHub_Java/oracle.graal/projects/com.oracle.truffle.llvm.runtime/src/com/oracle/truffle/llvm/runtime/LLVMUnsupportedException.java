/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

public final class LLVMUnsupportedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum UnsupportedReason {
        OTHER_TYPE_NOT_IMPLEMENTED("other type not implemented"),
        /**
         * We cannot let Truffle LLVM function pointeres escape to native functions.
         */
        FUNCTION_POINTER_ESCAPES_TO_NATIVE("function pointer escapes to native"),
        /**
         * Inline assembler calls.
         */
        INLINE_ASSEMBLER("inline assembler"),
        /**
         * "@llvm.va_start" and other intrinsic.
         */
        VA_COPY("va_copy"),
        /**
         * Clang fails to produce the correct IR.
         */
        CLANG_ERROR("clang error"),
        /**
         * Vector cast.
         */
        VECTOR_CAST("vector cast"),
        /**
         * setjmp and longjmp intrinsic.
         */
        SET_JMP_LONG_JMP("setjmp/longjmp"),
        FLOAT_OTHER_TYPE_NOT_IMPLEMENTED("float other type not implemented"),
        CONSTANT_EXPRESSION("constant expression"),
        PARSER_ERROR_VOID_SLOT("parser error void slot"),
        MULTITHREADING("multithreading"),
        VOID_NOT_VOID_FUNCTION_CALL_MISMATCH("void / not void function call mismatch");

        private final String description;

        UnsupportedReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

    }

    private final UnsupportedReason reason;

    public LLVMUnsupportedException(UnsupportedReason reason) {
        super(reason.getDescription());
        this.reason = reason;
    }

    public UnsupportedReason getReason() {
        return reason;
    }

}
