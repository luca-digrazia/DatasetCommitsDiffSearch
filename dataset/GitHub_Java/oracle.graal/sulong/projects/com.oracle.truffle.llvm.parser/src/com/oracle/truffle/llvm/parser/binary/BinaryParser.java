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
package com.oracle.truffle.llvm.parser.binary;

import com.oracle.truffle.llvm.parser.elf.ElfDynamicSection;
import com.oracle.truffle.llvm.parser.elf.ElfFile;
import com.oracle.truffle.llvm.parser.elf.ElfSectionHeaderTable;
import com.oracle.truffle.llvm.parser.macho.MachOFile;
import com.oracle.truffle.llvm.parser.macho.Xar;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.scanner.BitStream;
import org.graalvm.polyglot.io.ByteSequence;

import java.util.List;

/**
 * Parses a binary {@linkplain ByteSequence file} and returns the embedded {@linkplain ByteSequence bitcode data}.
 * Supported file types are plain bitcode, ELF files, and Mach-O files.
 */
public final class BinaryParser {

    public enum Magic {
        BC_MAGIC_WORD(0xdec04342L), // 'BC' c0de
        WRAPPER_MAGIC_WORD(0x0B17C0DEL),
        ELF_MAGIC_WORD(0x464C457FL),
        MH_MAGIC(0xFEEDFACEL),
        MH_CIGAM(0xCEFAEDFEL),
        MH_MAGIC_64(0xFEEDFACFL),
        MH_CIGAM_64(0xCFFAEDFEL),
        XAR_MAGIC(0x21726178L),
        UNKNOWN(0);

        public final long magic;

        Magic(long magic) {
            this.magic = magic;
        }

        private static final Magic[] VALUES = values();

        public static Magic get(long magic) {
            for (Magic m : VALUES) {
                if (m.magic == magic) {
                    return m;
                }
            }
            return UNKNOWN;
        }

        public static Magic get(BitStream b) {
            try {
                return get(Integer.toUnsignedLong((int) b.read(0, Integer.SIZE)));
            } catch (Exception e) {
                /*
                 * An exception here means we can't read at least 4 bytes from the file. That means
                 * it is definitely not a bitcode or ELF file.
                 */
                return UNKNOWN;
            }
        }
    }

    public static ModelModule parse(ByteSequence bytes) {
        assert bytes != null;

        final ModelModule model = new ModelModule();
        ByteSequence bitcode = parseBitcode(bytes, model);
        if (bitcode == null) {
            // unsupported file
            return null;
        }
        return model;
    }

    private static ByteSequence parseBitcode(ByteSequence bytes, ModelModule model) {
        BitStream b = BitStream.create(bytes);
        Magic magicWord = Magic.get(b);
        switch (magicWord) {
            case BC_MAGIC_WORD:
                return bytes;
            case WRAPPER_MAGIC_WORD:
                // 0: magic word
                // 32: version
                // 64: offset32
                long offset = b.read(64, Integer.SIZE);
                // 96: size32
                long size = b.read(96, Integer.SIZE);
                return bytes.subSequence((int) offset, (int) (offset + size));
            case ELF_MAGIC_WORD:
                ElfFile elfFile = ElfFile.create(bytes);
                ElfSectionHeaderTable.Entry llvmbc = elfFile.getSectionHeaderTable().getEntry(".llvmbc");
                if (llvmbc == null) {
                    // ELF File does not contain an .llvmbc section
                    return null;
                }
                ElfDynamicSection dynamicSection = elfFile.getDynamicSection();
                if (dynamicSection != null) {
                    List<String> libraries = dynamicSection.getDTNeeded();
                    List<String> paths = dynamicSection.getDTRPath();
                    model.addLibraries(libraries);
                    model.addLibraryPaths(paths);
                }
                long elfOffset = llvmbc.getOffset();
                long elfSize = llvmbc.getSize();
                return bytes.subSequence((int) elfOffset, (int) (elfOffset + elfSize));
            case MH_MAGIC:
            case MH_CIGAM:
            case MH_MAGIC_64:
            case MH_CIGAM_64:
                MachOFile machOFile = MachOFile.create(bytes);

                List<String> libraries = machOFile.getDyLibs();
                model.addLibraries(libraries);

                ByteSequence machoBitcode = machOFile.extractBitcode();
                if (machoBitcode == null) {
                    return null;
                }
                return parseBitcode(machoBitcode, model);
            case XAR_MAGIC:
                Xar xarFile = Xar.create(bytes);
                ByteSequence xarBitcode = xarFile.extractBitcode();
                if (xarBitcode == null) {
                    return null;
                }
                return parseBitcode(xarBitcode, model);
            default:
                return null;
        }
    }

}
