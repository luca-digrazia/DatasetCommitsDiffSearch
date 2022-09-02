/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.collection;

import java.util.Arrays;

public final class ByteArrayList {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private byte[] array;
    private int offset;

    public ByteArrayList() {
        this.array = null;
        this.offset = 0;
    }

    public void add(byte b) {
        ensureSize();
        array[offset] = b;
        offset++;
    }

    public byte popBack() {
        offset--;
        return array[offset];
    }

    public byte get(int index) {
        return array[index];
    }

    public int size() {
        return offset;
    }

    private void ensureSize() {
        if (array == null) {
            array = new byte[4];
        } else if (offset == array.length) {
            byte[] narray = new byte[array.length * 2];
            System.arraycopy(array, 0, narray, 0, offset);
            array = narray;
        }
    }

    public byte[] toArray() {
        byte[] result = new byte[offset];
        if (array != null) {
            System.arraycopy(array, 0, result, 0, offset);
            return result;
        } else {
            return EMPTY_BYTE_ARRAY;
        }
    }

    public static byte[] concat(ByteArrayList... byteArrayLists) {
        int totalSize = Arrays.stream(byteArrayLists).mapToInt(ByteArrayList::size).sum();
        byte[] result = new byte[totalSize];
        int resultOffset = 0;
        for (ByteArrayList byteArrayList : byteArrayLists) {
            if (byteArrayList.array != null) {
                System.arraycopy(byteArrayList.array, 0, result, resultOffset, byteArrayList.offset);
                resultOffset += byteArrayList.offset;
            }
        }
        return result;
    }
}
