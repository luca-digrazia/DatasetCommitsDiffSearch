/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.interop.values;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class LongArrayObject implements TruffleObject {

    final long[] array;
    final Object type;

    public LongArrayObject(Object type, long... array) {
        this.array = array;
        this.type = type;
    }

    @ExportMessage
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    Object getNativeType() {
        return type;
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return array.length;
    }

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    boolean hasArrayElement(long idx) {
        return Long.compareUnsigned(idx, array.length) < 0;
    }

    @ExportMessage
    boolean isArrayElementInsertable(@SuppressWarnings("unused") long idx) {
        return false;
    }

    @ExportMessage
    long readArrayElement(long idx) throws InvalidArrayIndexException {
        if (!hasArrayElement(idx)) {
            throw InvalidArrayIndexException.create(idx);
        }
        return array[(int) idx];
    }

    @ExportMessage(limit = "3")
    void writeArrayElement(long idx, Object value,
                    @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException, InvalidArrayIndexException {
        if (!hasArrayElement(idx)) {
            throw InvalidArrayIndexException.create(idx);
        }
        try {
            array[(int) idx] = interop.asLong(value);
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }
}
