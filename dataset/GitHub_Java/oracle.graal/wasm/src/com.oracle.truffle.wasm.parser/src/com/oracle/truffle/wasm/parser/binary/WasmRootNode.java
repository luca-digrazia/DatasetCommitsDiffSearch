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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class WasmRootNode extends RootNode {
    @CompilationFinal private final byte[] data;
    @Child private WasmBlockNode body;

    @CompilationFinal private int maxValueStackSize;

    public WasmRootNode(TruffleLanguage<?> language, byte[] data, WasmBlockNode body) {
        super(language);
        this.data = data;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        CallContext callContext = new CallContext(data, maxValueStackSize);
        body.execute(frame, callContext);
        long returnValue = callContext.pop();
        switch (body.typeId()) {
            case ValueTypes.I32_TYPE:
                return (int) returnValue;
            case ValueTypes.I64_TYPE:
                return returnValue;
            case ValueTypes.F32_TYPE:
                return Float.intBitsToFloat((int) returnValue);
            case ValueTypes.F64_TYPE:
                return Double.longBitsToDouble(returnValue);
            default:
                Assert.fail(String.format("Unknown type: 0x%02X", body.typeId()));
                return null;
        }
    }
}
