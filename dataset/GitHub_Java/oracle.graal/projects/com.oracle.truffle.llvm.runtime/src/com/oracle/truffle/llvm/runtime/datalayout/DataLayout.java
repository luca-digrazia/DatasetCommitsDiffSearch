/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.datalayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.llvm.runtime.datalayout.DataLayoutParser.DataTypeSpecification;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;

/**
 * Each LLVM bitcode file contains a data layout header, that determines which data types are
 * available and which alignment is used for each datatype. At the moment, this is mainly used to
 * determine the size of variable bit width integer values.
 *
 * Besides that, this class is hardly used and most other Sulong code parts contain hard-coded
 * assumptions regarding sizes/alignments...
 */
public final class DataLayout {

    private final List<DataTypeSpecification> dataLayout;

    public DataLayout() {
        this.dataLayout = new ArrayList<>();
    }

    public DataLayout(String layout) {
        this.dataLayout = DataLayoutParser.parseDataLayout(layout);
    }

    public int getSize(Type type) {
        return Math.max(1, getBitAlignment(type) / Byte.SIZE);
    }

    public int getBitAlignment(Type baseType) {
        if (baseType instanceof VariableBitWidthType) {
            /*
             * Handling of integer datatypes when the exact match not found
             * http://releases.llvm.org/3.9.0/docs/LangRef.html#data-layout
             */
            DataTypeSpecification integerLayout = dataLayout.stream().filter(d -> d.getType() == DataLayoutType.INTEGER_WIDTHS).findFirst().orElseThrow(IllegalStateException::new);
            int minPossibleSize = Arrays.stream(integerLayout.getValues()).max().orElseThrow(IllegalStateException::new);
            int size = baseType.getBitSize();
            for (int value : integerLayout.getValues()) {
                if (size < value && minPossibleSize > value) {
                    minPossibleSize = value;
                }
            }
            if (minPossibleSize >= size) {
                return minPossibleSize;
            } else {
                // is that correct?
                return ((size + 7) / 8) * 8;
            }
        } else {
            DataTypeSpecification spec = getDataTypeSpecification(baseType);
            if (spec == null) {
                throw new IllegalStateException("No data specification found for " + baseType);
            }
            return spec.getAbiAlignment();
        }
    }

    public DataLayout merge(DataLayout other) {
        DataLayout result = new DataLayout();
        for (DataTypeSpecification otherEntry : other.dataLayout) {
            DataTypeSpecification thisEntry;
            if (otherEntry.getType() == DataLayoutType.POINTER || otherEntry.getType() == DataLayoutType.INTEGER_WIDTHS) {
                thisEntry = getDataTypeSpecification(otherEntry.getType());
            } else if (otherEntry.getType() == DataLayoutType.INTEGER || otherEntry.getType() == DataLayoutType.FLOAT) {
                thisEntry = getDataTypeSpecification(otherEntry.getType(), otherEntry.getSize());
            } else {
                throw new IllegalStateException("Unknown data layout type: " + otherEntry.getType());
            }

            result.dataLayout.add(otherEntry);
            if (thisEntry != null && !thisEntry.equals(otherEntry)) {
                throw new IllegalStateException("Multiple bitcode files with incompatible layout strings are used: " + this.toString() + " vs. " + other.toString());
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return dataLayout.toString();
    }

    private DataTypeSpecification getDataTypeSpecification(Type baseType) {
        if (baseType instanceof PointerType || baseType instanceof FunctionType) {
            return getDataTypeSpecification(DataLayoutType.POINTER);
        } else if (baseType instanceof PrimitiveType) {
            PrimitiveType primitiveType = (PrimitiveType) baseType;
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                case I8:
                    // 1 is rounded up to 8 as well
                    return getDataTypeSpecification(DataLayoutType.INTEGER, 8);
                case I16:
                    return getDataTypeSpecification(DataLayoutType.INTEGER, 16);
                case I32:
                    return getDataTypeSpecification(DataLayoutType.INTEGER, 32);
                case I64:
                    return getDataTypeSpecification(DataLayoutType.INTEGER, 64);
                case HALF:
                    return getDataTypeSpecification(DataLayoutType.FLOAT, 16);
                case FLOAT:
                    return getDataTypeSpecification(DataLayoutType.FLOAT, 32);
                case DOUBLE:
                    return getDataTypeSpecification(DataLayoutType.FLOAT, 64);
                case X86_FP80:
                    return getDataTypeSpecification(DataLayoutType.FLOAT, 80);
            }
        }
        return null;
    }

    private DataTypeSpecification getDataTypeSpecification(DataLayoutType dataLayoutType) {
        for (DataTypeSpecification spec : dataLayout) {
            if (spec.getType().equals(dataLayoutType)) {
                return spec;
            }
        }
        return null;
    }

    private DataTypeSpecification getDataTypeSpecification(DataLayoutType dataLayoutType, int size) {
        for (DataTypeSpecification spec : dataLayout) {
            if (spec.getType().equals(dataLayoutType) && size == spec.getSize()) {
                return spec;
            }
        }
        return null;
    }
}
