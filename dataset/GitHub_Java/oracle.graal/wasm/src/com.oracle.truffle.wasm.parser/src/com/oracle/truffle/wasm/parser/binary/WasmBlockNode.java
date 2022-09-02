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
package com.oracle.truffle.wasm.parser.binary;

import com.oracle.truffle.api.frame.VirtualFrame;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class WasmBlockNode extends WasmNode {
    @CompilationFinal private final int startOffset;

    @CompilationFinal private final int size;

    @CompilationFinal private final byte typeId;

    public WasmBlockNode(int startOffset, int size, byte typeId) {
        this.startOffset = startOffset;
        this.size = size;
        this.typeId = typeId;
    }

    public void execute(VirtualFrame frame, CallContext callContext) {
        callContext.seek(startOffset);
        while (callContext.offset() < startOffset + size) {
            byte opcode = callContext.read1();
            switch (opcode) {
                case Instructions.I32_CONST: {
                    int value = callContext.readSignedInt32();
                    callContext.push(value);
                    break;
                }
                case Instructions.I64_CONST: {
                    long value = callContext.readSignedInt32();
                    callContext.push(value);
                    break;
                }
                case Instructions.F32_CONST: {
                    int value = callContext.readFloat32();
                    callContext.push(value);
                    break;
                }
                case Instructions.F64_CONST: {
                    long value = callContext.readFloat64();
                    callContext.push(value);
                    break;
                }
                case Instructions.END:
                    break;
                default:
                    Assert.fail(String.format("Unknown opcode: 0x%02X", opcode));
            }
        }
    }

    public byte typeId() {
        return typeId;
    }
}
