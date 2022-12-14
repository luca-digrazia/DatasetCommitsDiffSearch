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
package com.oracle.truffle.wasm.constants;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class Instructions {

    public static final int UNREACHABLE = 0x00;
    public static final int NOP = 0x01;

    public static final int BLOCK = 0x02;
    public static final int LOOP = 0x03;
    public static final int IF = 0x04;
    public static final int ELSE = 0x05;
    public static final int END = 0x0B;

    public static final int BR = 0x0C;
    public static final int BR_IF = 0x0D;
    public static final int BR_TABLE = 0x0E;

    public static final int RETURN = 0x0F;
    public static final int CALL = 0x10;
    public static final int CALL_INDIRECT = 0x11;

    public static final int DROP = 0x1A;
    public static final int SELECT = 0x1B;

    public static final int LOCAL_GET = 0x20;
    public static final int LOCAL_SET = 0x21;
    public static final int LOCAL_TEE = 0x22;
    public static final int GLOBAL_GET = 0x23;
    public static final int GLOBAL_SET = 0x24;

    public static final int I32_LOAD = 0x28;
    public static final int I64_LOAD = 0x29;
    public static final int F32_LOAD = 0x2A;
    public static final int F64_LOAD = 0x2B;
    public static final int I32_LOAD8_S = 0x2C;
    public static final int I32_LOAD8_U = 0x2D;
    public static final int I32_LOAD16_S = 0x2E;
    public static final int I32_LOAD16_U = 0x2F;
    public static final int I64_LOAD8_S = 0x30;
    public static final int I64_LOAD8_U = 0x31;
    public static final int I64_LOAD16_S = 0x32;
    public static final int I64_LOAD16_U = 0x33;
    public static final int I64_LOAD32_S = 0x34;
    public static final int I64_LOAD32_U = 0x35;
    public static final int I32_STORE = 0x36;
    public static final int I64_STORE = 0x37;
    public static final int F32_STORE = 0x38;
    public static final int F64_STORE = 0x39;
    public static final int I32_STORE_8 = 0x3A;
    public static final int I32_STORE_16 = 0x3B;
    public static final int I64_STORE_8 = 0x3C;
    public static final int I64_STORE_16 = 0x3D;
    public static final int I64_STORE_32 = 0x3E;
    public static final int MEMORY_SIZE = 0x3F;
    public static final int MEMORY_GROW = 0x40;

    public static final int I32_CONST = 0x41;
    public static final int I64_CONST = 0x42;
    public static final int F32_CONST = 0x43;
    public static final int F64_CONST = 0x44;

    public static final int I32_EQZ = 0x45;
    public static final int I32_EQ = 0x46;
    public static final int I32_NE = 0x47;
    public static final int I32_LT_S = 0x48;
    public static final int I32_LT_U = 0x49;
    public static final int I32_GT_S = 0x4A;
    public static final int I32_GT_U = 0x4B;
    public static final int I32_LE_S = 0x4C;
    public static final int I32_LE_U = 0x4D;
    public static final int I32_GE_S = 0x4E;
    public static final int I32_GE_U = 0x4F;

    public static final int I64_EQZ = 0x50;
    public static final int I64_EQ = 0x51;
    public static final int I64_NE = 0x52;
    public static final int I64_LT_S = 0x53;
    public static final int I64_LT_U = 0x54;
    public static final int I64_GT_S = 0x55;
    public static final int I64_GT_U = 0x56;
    public static final int I64_LE_S = 0x57;
    public static final int I64_LE_U = 0x58;
    public static final int I64_GE_S = 0x59;
    public static final int I64_GE_U = 0x5A;

    public static final int F32_EQ = 0x5B;
    public static final int F32_NE = 0x5C;
    public static final int F32_LT = 0x5D;
    public static final int F32_GT = 0x5E;
    public static final int F32_LE = 0x5F;
    public static final int F32_GE = 0x60;

    public static final int F64_EQ = 0x61;
    public static final int F64_NE = 0x62;
    public static final int F64_LT = 0x63;
    public static final int F64_GT = 0x64;
    public static final int F64_LE = 0x65;
    public static final int F64_GE = 0x66;

    public static final int I32_CLZ = 0x67;
    public static final int I32_CTZ = 0x68;
    public static final int I32_POPCNT = 0x69;
    public static final int I32_ADD = 0x6A;
    public static final int I32_SUB = 0x6B;
    public static final int I32_MUL = 0x6C;
    public static final int I32_DIV_S = 0x6D;
    public static final int I32_DIV_U = 0x6E;
    public static final int I32_REM_S = 0x6F;
    public static final int I32_REM_U = 0x70;
    public static final int I32_AND = 0x71;
    public static final int I32_OR = 0x72;
    public static final int I32_XOR = 0x73;
    public static final int I32_SHL = 0x74;
    public static final int I32_SHR_S = 0x75;
    public static final int I32_SHR_U = 0x76;
    public static final int I32_ROTL = 0x77;
    public static final int I32_ROTR = 0x78;

    public static final int I64_CLZ = 0x79;
    public static final int I64_CTZ = 0x7A;
    public static final int I64_POPCNT = 0x7B;
    public static final int I64_ADD = 0x7C;
    public static final int I64_SUB = 0x7D;
    public static final int I64_MUL = 0x7E;
    public static final int I64_DIV_S = 0x7F;
    public static final int I64_DIV_U = 0x80;
    public static final int I64_REM_S = 0x81;
    public static final int I64_REM_U = 0x82;
    public static final int I64_AND = 0x83;
    public static final int I64_OR = 0x84;
    public static final int I64_XOR = 0x85;
    public static final int I64_SHL = 0x86;
    public static final int I64_SHR_S = 0x87;
    public static final int I64_SHR_U = 0x88;
    public static final int I64_ROTL = 0x89;
    public static final int I64_ROTR = 0x8A;

    public static final int F32_ABS = 0x8B;
    public static final int F32_NEG = 0x8C;
    public static final int F32_CEIL = 0x8D;
    public static final int F32_FLOOR = 0x8E;
    public static final int F32_TRUNC = 0x8F;
    public static final int F32_NEAREST = 0x90;
    public static final int F32_SQRT = 0x91;
    public static final int F32_ADD = 0x92;
    public static final int F32_SUB = 0x93;
    public static final int F32_MUL = 0x94;
    public static final int F32_DIV = 0x95;
    public static final int F32_MIN = 0x96;
    public static final int F32_MAX = 0x97;
    public static final int F32_COPYSIGN = 0x98;

    public static final int F64_ABS = 0x99;
    public static final int F64_NEG = 0x9A;
    public static final int F64_CEIL = 0x9B;
    public static final int F64_FLOOR = 0x9C;
    public static final int F64_TRUNC = 0x9D;
    public static final int F64_NEAREST = 0x9E;
    public static final int F64_SQRT = 0x9F;
    public static final int F64_ADD = 0xA0;
    public static final int F64_SUB = 0xA1;
    public static final int F64_MUL = 0xA2;
    public static final int F64_DIV = 0xA3;
    public static final int F64_MIN = 0xA4;
    public static final int F64_MAX = 0xA5;
    public static final int F64_COPYSIGN = 0xA6;

    public static final int I32_WRAP_I64 = 0xA7;
    public static final int I32_TRUNC_F32_S = 0xA8;
    public static final int I32_TRUNC_F32_U = 0xA9;
    public static final int I32_TRUNC_F64_S = 0xAA;
    public static final int I32_TRUNC_F64_U = 0xAB;
    public static final int I64_EXTEND_I32_S = 0xAC;
    public static final int I64_EXTEND_I32_U = 0xAD;
    public static final int I64_TRUNC_F32_S = 0xAE;
    public static final int I64_TRUNC_F32_U = 0xAF;
    public static final int I64_TRUNC_F64_S = 0xB0;
    public static final int I64_TRUNC_F64_U = 0xB1;
    public static final int F32_CONVERT_I32_S = 0xB2;
    public static final int F32_CONVERT_I32_U = 0xB3;
    public static final int F32_CONVERT_I64_S = 0xB4;
    public static final int F32_CONVERT_I64_U = 0xB5;
    public static final int F32_DEMOTE_F64 = 0xB6;
    public static final int F64_CONVERT_I32_S = 0xB7;
    public static final int F64_CONVERT_I32_U = 0xB8;
    public static final int F64_CONVERT_I64_S = 0xB9;
    public static final int F64_CONVERT_I64_U = 0xBA;
    public static final int F64_PROMOTE_F32 = 0xBB;
    public static final int I32_REINTERPRET_F32 = 0xBC;
    public static final int I64_REINTERPRET_F64 = 0xBD;
    public static final int F32_REINTERPRET_I32 = 0xBE;
    public static final int F64_REINTERPRET_I64 = 0xBF;

    private static String[] decodingTable = new String[256];

    private Instructions() {
    }

    static {
        try {
            for (Field f : Instructions.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType().isPrimitive()) {
                    int code = f.getInt(null);
                    String representation = f.getName().toLowerCase();
                    if (representation.startsWith("i32") || representation.startsWith("i64") ||
                                    representation.startsWith("f32") || representation.startsWith("f64") ||
                                    representation.startsWith("local") || representation.startsWith("global")) {
                        representation = representation.replaceFirst("_", ".");
                    }
                    decodingTable[code] = representation;
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    static String rawDecode(byte[] instructions, int offset, int before, int after) {
        StringBuilder result = new StringBuilder();
        for (int i = offset - before; i <= offset + after; i++) {
            if (i == offset) {
                result.append("-> ");
            } else {
                result.append("   ");
            }
            final int opcode = Byte.toUnsignedInt(instructions[i]);
            String representation = decodingTable[opcode];
            result.append(String.format("%03d", opcode)).append(" ").append(representation).append("\n");
        }
        return result.toString();
    }
}
