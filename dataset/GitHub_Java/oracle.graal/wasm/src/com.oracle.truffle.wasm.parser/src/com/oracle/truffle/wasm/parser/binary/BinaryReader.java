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


import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;

/** Simple recursive-descend parser for the binary WebAssembly format.
 */
public class BinaryReader extends BinaryStreamReader {

    private static final int MAGIC = 0x6d736100;
    private static final int VERSION = 0x00000001;

    private WasmLanguage wasmLanguage;
    private WasmModule wasmModule;

    public BinaryReader(WasmLanguage wasmLanguage, String moduleName, byte[] data) {
        super(data);
        this.wasmLanguage = wasmLanguage;
        this.wasmModule = new WasmModule(moduleName);
    }

    public void readModule() {
        Assert.assertEquals(read4(), MAGIC, "Invalid MAGIC number");
        Assert.assertEquals(read4(), VERSION, "Invalid VERSION number");
        readSections();
        wasmLanguage.getContextReference().get().registerModule(wasmModule);
    }

    public void readSections() {
        while (!isEOF()) {
            byte sectionID = read1();
            int size = readUnsignedInt32();
            int startOffset = offset;
            switch(sectionID) {
                case 0x00:
                    readCustomSection();
                    break;
                case 0x01:
                    readTypeSection();
                    break;
                case 0x02:
                    readImportSection();
                    break;
                case 0x03:
                    readFunctionSection();
                    break;
                case 0x04:
                    readTableSection();
                    break;
                case 0x05:
                    readMemorySection();
                    break;
                case 0x06:
                    readGlobalSection();
                    break;
                case 0x07:
                    readExportSection();
                    break;
                case 0x08:
                    readStartSection();
                    break;
                case 0x09:
                    readElementSection();
                    break;
                case 0x0A:
                    readCodeSection();
                    break;
                case 0x0B:
                    readDataSection();
                    break;
                default:
                    Assert.fail("invalid section ID: " + sectionID);
            }
            Assert.assertEquals(offset - startOffset, size, String.format("Declared section (0x%02X) size is incorrect", sectionID));
        }
    }

    public void readCustomSection() {
    }

    public void readTypeSection() {
        int numTypes = readVectorLength();
        for (int t = 0; t != numTypes; ++t) {
            byte type = read1();
            switch(type) {
                case 0x60:
                    readFunctionType();
                    break;
                default:
                    Assert.fail("Only function types are supported in the type section");
            }
        }
    }

    public void readImportSection() {

    }

    public void readFunctionSection() {
        int numFunctionTypeIdxs = readVectorLength();
        for (byte t = 0; t != numFunctionTypeIdxs; ++t) {
            int funcTypeIdx = readUnsignedInt32();
            wasmModule.symbolTable().allocateFunction(funcTypeIdx);
        }
    }

    public void readTableSection() {
    }

    public void readMemorySection() {
    }

    private void readDataSection() {
    }

    private void readCodeSection() {
        int numCodeEntries = readVectorLength();
        for (int entry = 0; entry < numCodeEntries; entry++) {
            int codeEntrySize = readUnsignedInt32();
            int startOffset = offset;
            // TODO: Offset the entry by the number of already parsed code entries
            readCodeEntry(codeEntrySize, entry);
            Assert.assertEquals(offset - startOffset, codeEntrySize, String.format("Code entry %d size is incorrect", entry));
        }
    }

    private void readCodeEntry(int codeEntrySize, int funcIndex) {
        int startOffset = offset;
        int numLocals = readVectorLength();
        for (int local = 0; local < numLocals; local++) {
            throw new RuntimeException("Not implemented");
        }
        int expressionSize = codeEntrySize - (offset - startOffset);
        byte returnTypeId = wasmModule.symbolTable().function(funcIndex).returnType();
        WasmBlockNode block = new WasmBlockNode(offset, expressionSize, returnTypeId);
        WasmRootNode rootNode = new WasmRootNode(wasmLanguage, data, block);
        readBlock(block);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        wasmModule.symbolTable().function(funcIndex).setCallTarget(callTarget);
        // TODO: For structured code, we need to set the expressionSize later
    }

    private void readBlock(WasmBlockNode currentBlock) {
        byte instruction;
        do {
            instruction = read1();
            switch (instruction) {
                case 0x41:  // i32.const
                {
                    int val = readSignedInt32();
                    break;
                }
                case 0x42:  // i64.const
                    Assert.fail("Not implemented");
                    break;
                case 0x43:  // f32.const
                {
                    float val = readF32();
                    break;
                }
                case 0x44:  // f64.const
                {
                    double val = readF64();
                    break;
                }
                default:
                    break;
            }
        } while (instruction != 0x0B);
    }

    private void readElementSection() {
    }

    private void readStartSection() {
    }

    private void readExportSection() {
    }

    private void readGlobalSection() {
    }

    public void readFunctionType() {
        int paramsLength = readVectorLength();
        int resultLength = peakUnsignedInt32(paramsLength);
        resultLength = (resultLength == 0x40) ? 0 : resultLength;
        int idx = wasmModule.symbolTable().allocateFunctionType(paramsLength, resultLength);
        readParameterList(idx, paramsLength);
        readResultList(idx);
    }

    public void readParameterList(int funcTypeIdx, int numParams) {
        for (int paramIdx = 0; paramIdx != numParams; ++paramIdx) {
            byte type = readValueType();
            wasmModule.symbolTable().registerFunctionTypeParameter(funcTypeIdx, paramIdx, type);
        }
    }

    // Specification seems ambiguous: https://webassembly.github.io/spec/core/binary/types.html#result-types
    // According to the spec, the result type can only be 0x40 (void) or 0xtt, where tt is a value type.
    // However, the Wasm binary compiler produces binaries with either 0x00 or 0x01 0xtt. Therefore, we support both.
    public void readResultList(int funcTypeIdx) {
        byte b = read1();
        switch (b) {
            case 0x40:  // special byte indicating empty return type (same as above)
                break;
            case 0x00:  // empty vector
                break;
            case 0x01:  // vector with one element (produced by the Wasm binary compiler)
                byte type = readValueType();
                wasmModule.symbolTable().registerFunctionTypeReturnType(funcTypeIdx, 0, type);
                break;
            default:
                Assert.fail(String.format("Invalid return value specifier: 0x%02X", b));
        }
    }

    public byte readValueType() {
        byte b = read1();
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

    public boolean isEOF() {
        return offset == data.length;
    }

    public int readVectorLength() {
        return readUnsignedInt32();
    }
}
