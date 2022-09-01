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
package com.oracle.truffle.llvm.runtime.types;

import java.math.BigInteger;
import java.util.Objects;

public final class BigIntegerConstantType implements Type {

    private final IntegerType type;

    private final BigInteger value;

    public BigIntegerConstantType(IntegerType type, BigInteger value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public IntegerType getType() {
        return type;
    }

    public BigInteger getValue() {
        return value;
    }

    @Override
    public int getBits() {
        return type.getBits();
    }

    @Override
    public int getAlignment(DataSpecConverter targetDataLayout) {
        if (targetDataLayout != null) {
            return targetDataLayout.getBitAlignment(type.getLLVMBaseType()) / Byte.SIZE;

        } else {
            return type.getAlignment(targetDataLayout);
        }
    }

    @Override
    public int getSize(DataSpecConverter targetDataLayout) {
        return type.getSize(targetDataLayout);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + ((type == null) ? 0 : type.hashCode());
        hash = 41 * hash + ((value == null) ? 0 : value.hashCode());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BigIntegerConstantType) {
            BigIntegerConstantType other = (BigIntegerConstantType) obj;
            return Objects.equals(type, other.type) && Objects.equals(value, other.value);
        }
        return false;
    }

    @Override
    public String toString() {
        if (getType().getBits() == 1) {
            return value.equals(BigInteger.ZERO) ? "i1 false" : "i1 true";
        }
        return String.format("%s %s", type, value);
    }
}
