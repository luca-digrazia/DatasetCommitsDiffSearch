/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.predefined.wasi;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.exception.WasmExecutionException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.time.Instant;

public class WasiClockTimeGet extends WasmBuiltinRootNode {
    // https://github.com/WebAssembly/WASI/blob/master/phases/snapshot/docs.md#-clockid-enumu32
    public enum ClockId {
        Realtime,
        Monotonic,
        ProcessCpuTime,
        ThreadCpuTime
    }

    static ClockId[] clockIdValues = ClockId.values();

    public WasiClockTimeGet(WasmLanguage language, WasmInstance module) {
        super(language, module);
    }

    @Override
    public Object executeWithContext(VirtualFrame frame, WasmContext context) {
        final WasmMemory memory = module.symbolTable().memory();
        final int id = (int) frame.getArguments()[0];
        // Ignored for now
        // final long precision = (long) frame.getArguments()[1];
        final int resultAddress = (int) frame.getArguments()[2];

        final ClockId clockId = clockIdValues[id];
        switch (clockId) {
            case Realtime:
                long result = ChronoUnit.NANOS.between(Instant.EPOCH, Instant.now());
                memory.store_i64(this, resultAddress, result);
                break;
            case Monotonic:
            case ProcessCpuTime:
            case ThreadCpuTime:
                throw new WasmExecutionException(this, "Unimplemented ClockID: " + clockId.name());
        }

        return 0;
    }

    @TruffleBoundary
    private void checkEncodable(String argument) {
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(argument)) {
            throw new WasmExecutionException(this, "Argument '" + argument + "' contains non-ASCII characters.");
        }
    }

    @Override
    public String builtinNodeName() {
        return "__wasi_args_get";
    }
}
