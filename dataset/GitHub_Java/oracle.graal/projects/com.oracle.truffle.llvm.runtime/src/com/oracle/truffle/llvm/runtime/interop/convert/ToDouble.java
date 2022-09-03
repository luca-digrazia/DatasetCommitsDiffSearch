/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

public abstract class ToDouble extends ForeignToLLVM {

    @Child private ForeignToLLVM toDouble;

    @Specialization
    protected double fromInt(int value) {
        return value;
    }

    @Specialization
    protected double fromChar(char value) {
        return value;
    }

    @Specialization
    protected double fromLong(long value) {
        return value;
    }

    @Specialization
    protected double fromByte(byte value) {
        return value;
    }

    @Specialization
    protected double fromShort(short value) {
        return value;
    }

    @Specialization
    protected double fromFloat(float value) {
        return value;
    }

    @Specialization
    protected double fromDouble(double value) {
        return value;
    }

    @Specialization
    protected double fromBoolean(boolean value) {
        return (value ? 1.0 : 0.0);
    }

    @Specialization
    protected double fromString(String value) {
        return getSingleStringCharacter(value);
    }

    @Specialization
    protected double fromForeignPrimitive(LLVMBoxedPrimitive boxed) {
        return recursiveConvert(boxed.getValue());
    }

    @Specialization(guards = "notLLVM(obj)")
    protected double fromTruffleObject(TruffleObject obj) {
        return recursiveConvert(fromForeign(obj));
    }

    private double recursiveConvert(Object o) {
        if (toDouble == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toDouble = insert(getNodeFactory().createForeignToLLVM(ForeignToLLVMType.DOUBLE));
        }
        return (double) toDouble.executeWithTarget(o);
    }

    @TruffleBoundary
    static double slowPathPrimitiveConvert(LLVMMemory memory, ForeignToLLVM thiz, Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof Boolean) {
            return ((boolean) value ? 1.0 : 0.0);
        } else if (value instanceof Character) {
            return (char) value;
        } else if (value instanceof String) {
            return thiz.getSingleStringCharacter((String) value);
        } else if (value instanceof LLVMBoxedPrimitive) {
            return slowPathPrimitiveConvert(memory, thiz, ((LLVMBoxedPrimitive) value).getValue());
        } else if (value instanceof TruffleObject && notLLVM((TruffleObject) value)) {
            return slowPathPrimitiveConvert(memory, thiz, thiz.fromForeign((TruffleObject) value));
        } else {
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }
}
