/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.predefined.emscripten;

import static org.graalvm.wasm.ValueTypes.F64_TYPE;
import static org.graalvm.wasm.ValueTypes.I32_TYPE;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.PredefinedModule;
import org.graalvm.wasm.ReferenceTypes;

public class EmscriptenModule extends PredefinedModule {
    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {
        WasmModule module = new WasmModule(name, null);
        final WasmMemory memory = defineMemory(context, module, "memory", 32, 4096);
        defineFunction(module, "abort", types(I32_TYPE), types(), new AbortNode(language, null, memory));
        defineFunction(module, "abortOnCannotGrowMemory", types(I32_TYPE), types(I32_TYPE), new AbortOnCannotGrowMemory(language, null, memory));
        defineFunction(module, "_abort", types(), types(), new AbortNode(language, null, memory));
        defineFunction(module, "_emscripten_memcpy_big", types(I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new EmscriptenMemcpyBig(language, null, memory));
        defineFunction(module, "_emscripten_get_heap_size", types(), types(I32_TYPE), new EmscriptenGetHeapSize(language, null, memory));
        defineFunction(module, "_emscripten_resize_heap", types(I32_TYPE), types(I32_TYPE), new EmscriptenResizeHeap(language, null, memory));
        defineFunction(module, "_gettimeofday", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new GetTimeOfDay(language, null, memory));
        defineFunction(module, "_llvm_exp2_f64", types(F64_TYPE), types(F64_TYPE), new LLVMExp2F64(language, null, memory));
        defineFunction(module, "___wasi_fd_write", types(I32_TYPE, I32_TYPE, I32_TYPE, I32_TYPE), types(I32_TYPE), new WasiFdWrite(language, memory));
        defineFunction(module, "___lock", types(I32_TYPE), types(), new Lock(language, null, memory));
        defineFunction(module, "___unlock", types(I32_TYPE), types(), new Unlock(language, null, memory));
        defineFunction(module, "___setErrNo", types(I32_TYPE), types(), new SetErrNo(language, null, memory));
        defineFunction(module, "___syscall140", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("___syscall140", language, null, memory));
        defineFunction(module, "___syscall146", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("___syscall146", language, null, memory));
        defineFunction(module, "___syscall54", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("___syscall54", language, null, memory));
        defineFunction(module, "___syscall6", types(I32_TYPE, I32_TYPE), types(I32_TYPE), new UnimplementedNode("___syscall6", language, null, memory));
        defineFunction(module, "setTempRet0", types(I32_TYPE), types(), new UnimplementedNode("setTempRet0", language, null, memory));
        defineGlobal(context, module, "__table_base", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineGlobal(context, module, "__memory_base", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineGlobal(context, module, "DYNAMICTOP_PTR", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineGlobal(context, module, "DYNAMIC_BASE", I32_TYPE, GlobalModifier.CONSTANT, 0);
        defineTable(context, module, "table", 0, -1, ReferenceTypes.FUNCREF);
        return module;
    }
}
