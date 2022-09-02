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
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class BinaryStreamReader {
    protected byte[] data;
    protected int offset;
    private byte[] bytesConsumed;

    public BinaryStreamReader(byte[] data) {
        this.data = data;
        this.offset = 0;
        this.bytesConsumed = new byte[1];
    }

    public int readSignedInt32() {
        int value = peekSignedInt32(data, offset, bytesConsumed);
        offset += bytesConsumed[0];
        return value;
    }

    public int readSignedInt32(byte[] bytesConsumedOut) {
        byte[] out = bytesConsumedOut != null ? bytesConsumedOut : bytesConsumed;
        int value = peekSignedInt32(data, offset, out);
        offset += out[0];
        return value;
    }

    @ExplodeLoop
    public static int peekSignedInt32(byte[] data, int initialOffset, byte[] bytesConsumed) {
        int result = 0;
        int shift = 0;
        int offset = initialOffset;
        byte b;
        do {
            b = peek1(data, offset);
            result |= ((b & 0x7F) << shift);
            shift += 7;
            offset++;
        } while ((b & 0x80) != 0);

        if ((shift < 32) && (b & 0x40) != 0) {
            result |= (~0 << shift);
        }

        if (bytesConsumed != null) {
            bytesConsumed[0] = (byte) (shift / 7);
        }
        return result;
    }

    @ExplodeLoop
    public int peekUnsignedInt32(int ahead) {
        int result = 0;
        int shift = 0;
        int i = 0;
        do {
            byte b = peek1(i + ahead);
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
            i++;
        } while (shift < 35);
        if (shift == 35) {
            Assert.fail("Unsigned LEB128 overflow");
        }
        return result;
    }

    // This is used for indices, so we don't expect values larger than 2^31.
    public int readUnsignedInt32() {
        int result = 0;
        int shift = 0;
        do {
            byte b = read1();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        } while (shift < 35);
        if (shift == 35) {
            Assert.fail("Unsigned LEB128 overflow");
        }
        return result;
    }

    public long readSignedInt64() {
        long value = peekSignedInt64(data, offset, bytesConsumed);
        offset += bytesConsumed[0];
        return value;
    }

    public long readSignedInt64(byte[] bytesConsumedOut) {
        byte[] out = bytesConsumedOut != null ? bytesConsumedOut : bytesConsumed;
        int value = peekSignedInt32(data, offset, out);
        offset += out[0];
        return value;
    }

    @ExplodeLoop
    public static long peekSignedInt64(byte[] data, int initialOffset, byte[] bytesConsumed) {
        long result = 0;
        int shift = 0;
        int offset = initialOffset;
        byte b;
        do {
            b = peek1(data, offset);
            result |= ((b & 0x7F) << shift);
            shift += 7;
            offset++;
        } while ((b & 0x80) != 0);

        if ((shift < 64) && (b & 0x40) != 0) {
            result |= (~0 << shift);
        }

        if (bytesConsumed != null) {
            bytesConsumed[0] = (byte) (shift / 7);
        }
        return result;
    }

    public static int peekFloatAsInt32(byte[] data, int offset) {
        return peek4(data, offset);
    }

    public int readFloatAsInt32() {
        return read4();
    }

    public static long peekFloatAsInt64(byte[] data, int offset) {
        return peek8(data, offset);
    }

    public long readFloatAsInt64() {
        return read8();
    }

    public byte read1() {
        byte value = peek1(data, offset);
        offset++;
        return value;
    }

    public static byte peek1(byte[] data, int offset) {
        return data[offset];
    }

    public static int peek4(byte[] data, int offset) {
        int result = 0;
        for (int i = 0; i != 4; ++i) {
            int x = peek1(data, offset + i) & 0xFF;
            result |= x << 8 * i;
        }
        return result;
    }

    public static long peek8(byte[] data, int offset) {
        long result = 0;
        for (int i = 0; i != 8; ++i) {
            long x = peek1(data, offset + i) & 0xFF;
            result |= x << 8 * i;
        }
        return result;
    }

    public int read4() {
        int result = 0;
        for (int i = 0; i != 4; ++i) {
            int x = Byte.toUnsignedInt(read1());
            result |= x << 8 * i;
        }
        return result;
    }

    public long read8() {
        long result = 0;
        for (int i = 0; i != 8; ++i) {
            long x = Byte.toUnsignedLong(read1());
            result |= x << 8 * i;
        }
        return result;
    }

    public byte peek1() {
        return data[offset];
    }

    public byte peek1(int ahead) {
        return data[offset + ahead];
    }

    public int offset() {
        return offset;
    }

    public static byte peekBlockType(byte[] data, int offset) {
        byte type = peek1(data, offset);
        switch (type) {
            case 0x40:
                return type;
            default:
                return peekValueType(data, offset);
        }
    }

    public byte readBlockType() {
        byte type = peekBlockType(data, offset);
        offset++;
        return type;
    }

    public static byte peekValueType(byte[] data, int offset) {
        byte b = peek1(data, offset);
        switch (b) {
            case 0x7F:  // i32
            case 0x7E:  // i64
            case 0x7D:  // f32
            case 0x7C:  // f64
                break;
            default:
                Assert.fail(String.format("Invalid value type: 0x%02X", b));
        }
        return b;
    }

    public byte readValueType() {
        byte b = peekValueType(data, offset);
        offset++;
        return b;
    }

}
