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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;

public class WasmIfNode extends WasmNode {
    @CompilationFinal private final byte returnTypeId;
    @CompilationFinal private final int initialStackPointer;
    @Child WasmNode trueBranch;
    @Child WasmNode falseBranch;

    public WasmIfNode(WasmCodeEntry codeEntry, WasmNode trueBranch, WasmNode falseBranch, int byteLength, byte returnTypeId, int initialStackPointer, int byteConstantLength) {
        super(codeEntry, byteLength, byteConstantLength);
        this.returnTypeId = returnTypeId;
        this.initialStackPointer = initialStackPointer;
        this.trueBranch = trueBranch;
        this.falseBranch = falseBranch;
    }

    @Override
    public int execute(VirtualFrame frame) {
        int stackPointer = initialStackPointer - 1;
        int condition = popInt(frame, stackPointer);
        if (condition != 0) {
            return trueBranch.execute(frame);
        } else {
            return falseBranch.execute(frame);
        }
    }

    @Override
    public byte returnTypeId() {
        return returnTypeId;
    }

}
