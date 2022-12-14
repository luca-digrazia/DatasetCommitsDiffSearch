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
package org.graalvm.wasm;

import org.graalvm.wasm.memory.WasmMemory;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Represents the state of a WebAssembly module.
 */
@SuppressWarnings("static-method")
public class RuntimeState {
    private static final int INITIAL_GLOBALS_SIZE = 64;

    private final WasmModule module;

    /**
     * This array is monotonically populated from the left. An index i denotes the i-th global in
     * this module. The value at the index i denotes the address of the global in the memory space
     * for all the globals from all the modules (see {@link GlobalRegistry}).
     *
     * This separation of global indices is done because the index spaces of the globals are
     * module-specific, and the globals can be imported across modules. Thus, the address-space of
     * the globals is not the same as the module-specific index-space.
     */
    @CompilationFinal(dimensions = 1) private int[] globalAddresses;

    /**
     * The table from the context-specific table space, which this module is using.
     *
     * In the current WebAssembly specification, a module can use at most one table. The value
     * {@code null} denotes that this module uses no table.
     */
    @CompilationFinal private WasmTable table;

    /**
     * Memory that this module is using.
     *
     * In the current WebAssembly specification, a module can use at most one memory. The value
     * {@code null} denotes that this module uses no memory.
     */
    @CompilationFinal private WasmMemory memory;

    private void ensureGlobalsCapacity(int index) {
        while (index >= globalAddresses.length) {
            final int[] nGlobalAddresses = new int[globalAddresses.length * 2];
            System.arraycopy(globalAddresses, 0, nGlobalAddresses, 0, globalAddresses.length);
            globalAddresses = nGlobalAddresses;
        }
    }

    public RuntimeState(WasmModule module) {
        this.module = module;
        this.globalAddresses = new int[INITIAL_GLOBALS_SIZE];
        ensureGlobalsCapacity(module.maxGlobalIndex());
    }

    public SymbolTable symbolTable() {
        return module.symbolTable();
    }

    public byte[] data() {
        return module.data();
    }

    public WasmModule module() {
        return module;
    }

    public int globalAddress(int index) {
        return globalAddresses[index];
    }

    void setGlobalAddress(int globalIndex, int address) {
        // TODO: checkNotLinked();
        globalAddresses[globalIndex] = address;
    }

    public WasmTable table() {
        return table;
    }

    void setTable(WasmTable table) {
        // TODO: checkNotLinked();
        this.table = table;
    }

    public WasmMemory memory() {
        return memory;
    }

    public void setMemory(WasmMemory memory) {
        // TODO: checkNotLinked();
        this.memory = memory;
    }
}
