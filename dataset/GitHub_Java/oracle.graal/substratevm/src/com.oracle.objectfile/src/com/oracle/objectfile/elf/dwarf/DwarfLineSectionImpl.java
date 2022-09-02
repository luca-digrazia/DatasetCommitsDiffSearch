/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.objectfile.elf.dwarf;

import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;

import java.util.Map;

import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_LINE_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_STR_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_VERSION_2;
/**
 * Section generator for debug_line section.
 */
public class DwarfLineSectionImpl extends DwarfSectionImpl {
    /**
     * line header section always contains fixed number of bytes.
     */
    private static final int DW_LN_HEADER_SIZE = 27;
    /**
     * current generator follows C++ with line base -5.
     */
    private static final int DW_LN_LINE_BASE = -5;
    /**
     * current generator follows C++ with line range 14
     * giving full range -5 to 8.
     */
    private static final int DW_LN_LINE_RANGE = 14;
    /**
     *  current generator uses opcode base of 13
     *  which must equal DW_LNS_define_file + 1.
     */
    private static final int DW_LN_OPCODE_BASE = 13;

    /*
     * standard opcodes defined by Dwarf 2
     */
    /*
     *  0 can be returned to indicate an invalid opcode
     */
    private static final byte DW_LNS_undefined = 0;
    /*
     *  0 can be inserted as a prefix for extended opcodes
     */
    private static final byte DW_LNS_extended_prefix = 0;
    /*
     *  append current state as matrix row 0 args
     */
    private static final byte DW_LNS_copy = 1;
    /*
     * increment address 1 uleb arg
     */
    private static final byte DW_LNS_advance_pc = 2;
    /*
     *  increment line 1 sleb arg
     */
    private static final byte DW_LNS_advance_line = 3;
    /*
     * set file 1 uleb arg
     */
    private static final byte DW_LNS_set_file = 4;
    /*
     *  set column 1 uleb arg
     */
    private static final byte DW_LNS_set_column = 5;
    /*
     *  flip is_stmt 0 args
     */
    private static final byte DW_LNS_negate_stmt = 6;
    /*
     *  set end sequence and copy row 0 args
     */
    private static final byte DW_LNS_set_basic_block = 7;
    /*
     * increment address as per opcode 255 0 args
     */
    private static final byte DW_LNS_const_add_pc = 8;
    /*
     * increment address 1 ushort arg
     */
    private static final byte DW_LNS_fixed_advance_pc = 9;

    /*
     * extended opcodes defined by Dwarf 2
     */
    /*
     * there is no extended opcode 0
     */
    // private static final byte DW_LNE_undefined = 0;
    /*
     *  end sequence of addresses
     */
    private static final byte DW_LNE_end_sequence = 1;
    /*
     * set address as explicit long argument
     */
    private static final byte DW_LNE_set_address = 2;
    /*
     * set file as explicit string argument
     */
    private static final byte DW_LNE_define_file = 3;

    DwarfLineSectionImpl(DwarfSections dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_LINE_SECTION_NAME;
    }

    @Override
    public void createContent() {
        /*
         * we need to create a header, dir table, file table and line
         * number table encoding for each CU
         */

        /*
         * write entries for each file listed in the primary list
         */
        int pos = 0;
        for (ClassEntry classEntry : getPrimaryClasses()) {
            if (classEntry.getFileName().length() != 0) {
                int startPos = pos;
                classEntry.setLineIndex(startPos);
                int headerSize = headerSize();
                int dirTableSize = computeDirTableSize(classEntry);
                int fileTableSize = computeFileTableSize(classEntry);
                int prologueSize = headerSize + dirTableSize + fileTableSize;
                classEntry.setLinePrologueSize(prologueSize);
                int lineNumberTableSize = computeLineNUmberTableSize(classEntry);
                int totalSize = prologueSize + lineNumberTableSize;
                classEntry.setTotalSize(totalSize);
                pos += totalSize;
            }
        }
        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    public int headerSize() {
        /*
         * header size is standard 31 bytes
         * uint32 total_length
         * uint16 version
         * uint32 prologue_length
         * uint8 min_insn_length
         * uint8 default_is_stmt
         * int8 line_base
         * uint8 line_range
         * uint8 opcode_base
         * uint8 li_opcode_base
         * uint8[opcode_base-1] standard_opcode_lengths
         */

        return DW_LN_HEADER_SIZE;
    }

    public int computeDirTableSize(ClassEntry classEntry) {
        /*
         * table contains a sequence of 'nul'-terminated
         * dir name bytes followed by an extra 'nul'
         * and then a sequence of 'nul'-terminated
         * file name bytes followed by an extra 'nul'
         *
         * for now we assume dir and file names are ASCII
         * byte strings
         */
        int dirSize = 0;
        for (DirEntry dir : classEntry.getLocalDirs()) {
            dirSize += dir.getPathString().length() + 1;
        }
        /*
         * allow for separator nul
         */
        dirSize++;
        return dirSize;
    }

    public int computeFileTableSize(ClassEntry classEntry) {
        /*
         * table contains a sequence of 'nul'-terminated
         * dir name bytes followed by an extra 'nul'
         * and then a sequence of 'nul'-terminated
         * file name bytes followed by an extra 'nul'

         * for now we assume dir and file names are ASCII
         * byte strings
         */
        int fileSize = 0;
        for (FileEntry localEntry : classEntry.getLocalFiles()) {
            /*
             * we want the file base name excluding path
             */
            String baseName = localEntry.getFileName();
            int length = baseName.length();
            fileSize += length + 1;
            DirEntry dirEntry = localEntry.getDirEntry();
            int idx = classEntry.localDirsIdx(dirEntry);
            fileSize += putULEB(idx, scratch, 0);
            /*
             * the two zero timestamps require 1 byte each
             */
            fileSize += 2;
        }
        /*
         * allow for terminator nul
         */
        fileSize++;
        return fileSize;
    }

    public int computeLineNUmberTableSize(ClassEntry classEntry) {
        /*
         * sigh -- we have to do this by generating the
         * content even though we cannot write it into a byte[]
        */
        return writeLineNumberTable(classEntry, null, 0);
    }

    @Override
    public byte[] getOrDecideContent(Map<ObjectFile.Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        ObjectFile.Element textElement = getElement().getOwner().elementForName(".text");
        LayoutDecisionMap decisionMap = alreadyDecided.get(textElement);
        if (decisionMap != null) {
            Object valueObj = decisionMap.getDecidedValue(LayoutDecision.Kind.VADDR);
            if (valueObj != null && valueObj instanceof Number) {
                /*
                 * this may not be the final vaddr for the text segment
                 * but it will be close enough to make debug easier
                 * i.e. to within a 4k page or two
                 */
                debugTextBase = ((Number) valueObj).longValue();
            }
        }
        return super.getOrDecideContent(alreadyDecided, contentHint);
    }

    @Override
    public void writeContent() {
        byte[] buffer = getContent();

        int pos = 0;
        checkDebug(pos);
        debug("  [0x%08x] DEBUG_LINE\n", pos);

        for (ClassEntry classEntry : getPrimaryClasses()) {
            if (classEntry.getFileName().length() != 0) {
                int startPos = pos;
                assert classEntry.getLineIndex() == startPos;
                debug("  [0x%08x] Compile Unit for %s\n", pos, classEntry.getFileName());
                pos = writeHeader(classEntry, buffer, pos);
                debug("  [0x%08x] headerSize = 0x%08x\n", pos, pos - startPos);
                int dirTablePos = pos;
                pos = writeDirTable(classEntry, buffer, pos);
                debug("  [0x%08x] dirTableSize = 0x%08x\n", pos, pos - dirTablePos);
                int fileTablePos = pos;
                pos = writeFileTable(classEntry, buffer, pos);
                debug("  [0x%08x] fileTableSize = 0x%08x\n", pos, pos - fileTablePos);
                int lineNumberTablePos = pos;
                pos = writeLineNumberTable(classEntry, buffer, pos);
                debug("  [0x%08x] lineNumberTableSize = 0x%x\n", pos, pos - lineNumberTablePos);
                debug("  [0x%08x] size = 0x%x\n", pos, pos - startPos);
            }
        }
        assert pos == buffer.length;
    }

    public int writeHeader(ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        /*
         * 4 ubyte length field
         */
        pos = putInt(classEntry.getTotalSize() - 4, buffer, pos);
        /*
         * 2 ubyte version is always 2
         */
        pos = putShort(DW_VERSION_2, buffer, pos);
        /*
         * 4 ubyte prologue length includes rest of header and
         * dir + file table section
         */
        int prologueSize = classEntry.getLinePrologueSize() - 6;
        pos = putInt(prologueSize, buffer, pos);
        /*
         * 1 ubyte min instruction length is always 1
         */
        pos = putByte((byte) 1, buffer, pos);
        /*
         * 1 byte default is_stmt is always 1
         */
        pos = putByte((byte) 1, buffer, pos);
        /*
         * 1 byte line base is always -5
         */
        pos = putByte((byte) DW_LN_LINE_BASE, buffer, pos);
        /*
         * 1 ubyte line range is always 14 giving range -5 to 8
         */
        pos = putByte((byte) DW_LN_LINE_RANGE, buffer, pos);
        /*
         * 1 ubyte opcode base is always 13
         */
        pos = putByte((byte) DW_LN_OPCODE_BASE, buffer, pos);
        /*
         * specify opcode arg sizes for the standard opcodes
         */
        /* DW_LNS_copy */
        putByte((byte) 0, buffer, pos);
        /* DW_LNS_advance_pc */
        putByte((byte) 1, buffer, pos + 1);
        /* DW_LNS_advance_line */
        putByte((byte) 1, buffer, pos + 2);
        /* DW_LNS_set_file */
        putByte((byte) 1, buffer, pos + 3);
        /* DW_LNS_set_column */
        putByte((byte) 1, buffer, pos + 4);
        /* DW_LNS_negate_stmt */
        putByte((byte) 0, buffer, pos + 5);
        /* DW_LNS_set_basic_block */
        putByte((byte) 0, buffer, pos + 6);
       /* DW_LNS_const_add_pc */
        putByte((byte) 0, buffer, pos + 7);
        /* DW_LNS_fixed_advance_pc */
        putByte((byte) 1, buffer, pos + 8);
        /* DW_LNS_end_sequence */
        putByte((byte) 0, buffer, pos + 9);
        /* DW_LNS_set_address */
        putByte((byte) 0, buffer, pos + 10);
        /* DW_LNS_define_file */
        pos = putByte((byte) 1, buffer, pos + 11);
        return pos;
    }

    public int writeDirTable(ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        debug("  [0x%08x] Dir  Name\n", pos);
        /*
         * write out the list of dirs referenced form this file entry
         */
        int dirIdx = 1;
        for (DirEntry dir : classEntry.getLocalDirs()) {
            /*
             * write nul terminated string text.
             */
            debug("  [0x%08x] %-4d %s\n", pos, dirIdx, dir.getPath());
            pos = putAsciiStringBytes(dir.getPathString(), buffer, pos);
            dirIdx++;
        }
        /*
         * separate dirs from files with a nul
         */
        pos = putByte((byte) 0, buffer, pos);
        return pos;
    }

    public int writeFileTable(ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        int fileIdx = 1;
        debug("  [0x%08x] Entry Dir  Name\n", pos);
        for (FileEntry localEntry : classEntry.getLocalFiles()) {
            /*
             * we need the file name minus path, the associated dir index, and 0 for time stamps
             */
            String baseName = localEntry.getFileName();
            DirEntry dirEntry = localEntry.getDirEntry();
            int dirIdx = classEntry.localDirsIdx(dirEntry);
            debug("  [0x%08x] %-5d %-5d %s\n", pos, fileIdx, dirIdx, baseName);
            pos = putAsciiStringBytes(baseName, buffer, pos);
            pos = putULEB(dirIdx, buffer, pos);
            pos = putULEB(0, buffer, pos);
            pos = putULEB(0, buffer, pos);
            fileIdx++;
        }
        /*
         * terminate files with a nul
         */
        pos = putByte((byte) 0, buffer, pos);
        return pos;
    }

    public int debugLine = 1;
    public int debugCopyCount = 0;

    public int writeLineNumberTable(ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        /*
         * the primary file entry should always be first in the local files list
         */
        assert classEntry.localFilesIdx(classEntry.getFileEntry()) == 1;
        String primaryClassName = classEntry.getClassName();
        String primaryFileName = classEntry.getFileName();
        String file = primaryFileName;
        int fileIdx = 1;
        debug("  [0x%08x] primary class %s\n", pos, primaryClassName);
        debug("  [0x%08x] primary file %s\n", pos, primaryFileName);
        for (PrimaryEntry primaryEntry : classEntry.getPrimaryEntries()) {
            Range primaryRange = primaryEntry.getPrimary();
            assert primaryRange.getFileName().equals(primaryFileName);
            /*
             * each primary represents a method i.e. a contiguous
             * sequence of subranges. we assume the default state
             * at the start of each sequence because we always post an
             * end_sequence when we finish all the subranges in the method
             */
            long line = primaryRange.getLine();
            if (line < 0 && primaryEntry.getSubranges().size() > 0) {
                line = primaryEntry.getSubranges().get(0).getLine();
            }
            if (line < 0) {
                line = 0;
            }
            long address = primaryRange.getLo();

            /*
             * set state for primary
             */
            debug("  [0x%08x] primary range [0x%08x, 0x%08x] %s:%d\n", pos, debugTextBase + primaryRange.getLo(), debugTextBase + primaryRange.getHi(), primaryRange.getFullMethodName(),
                  primaryRange.getLine());

            /*
             * initialize and write a row for the start of the primary method
             */
            pos = putSetFile(file, fileIdx, buffer, pos);
            pos = putSetBasicBlock(buffer, pos);
            /*
             * address is currently 0
              */
            pos = putSetAddress(address, buffer, pos);
            /*
             * state machine value of line is currently 1
             * increment to desired line
             */
            if (line != 1) {
                pos = putAdvanceLine(line - 1, buffer, pos);
            }
            pos = putCopy(buffer, pos);

            /*
             * now write a row for each subrange lo and hi
             */
            for (Range subrange : primaryEntry.getSubranges()) {
                assert subrange.getLo() >= primaryRange.getLo();
                assert subrange.getHi() <= primaryRange.getHi();
                FileEntry subFileEntry = primaryEntry.getSubrangeFileEntry(subrange);
                String subfile = subFileEntry.getFileName();
                int subFileIdx = classEntry.localFilesIdx(subFileEntry);
                long subLine = subrange.getLine();
                long subAddressLo = subrange.getLo();
                long subAddressHi = subrange.getHi();
                debug("  [0x%08x] sub range [0x%08x, 0x%08x] %s:%d\n", pos, debugTextBase + subAddressLo, debugTextBase + subAddressHi, subrange.getFullMethodName(), subLine);
                if (subLine < 0) {
                    /*
                     * no line info so stay at previous file:line
                     */
                    subLine = line;
                    subfile = file;
                    subFileIdx = fileIdx;
                    debug("  [0x%08x] missing line info - staying put at %s:%d\n", pos, file, line);
                }
                /*
                 * there is a temptation to append end sequence at here
                 * when the hiAddress lies strictly between the current
                 * address and the start of the next subrange because,
                 * ostensibly, we have void space between the end of
                 * the current subrange and the start of the next one.
                 * however, debug works better if we treat all the insns up
                 * to the next range start as belonging to the current line
                 *
                 * if we have to update to a new file then do so
                 */
                if (subFileIdx != fileIdx) {
                    /*
                     * update the current file
                     */
                    pos = putSetFile(subfile, subFileIdx, buffer, pos);
                    file = subfile;
                    fileIdx = subFileIdx;
                }
                /*
                 * check if we can advance line and/or address in
                 * one byte with a special opcode
                 */
                long lineDelta = subLine - line;
                long addressDelta = subAddressLo - address;
                byte opcode = isSpecialOpcode(addressDelta, lineDelta);
                if (opcode != DW_LNS_undefined) {
                    /*
                     * ignore pointless write when addressDelta == lineDelta == 0
                     */
                    if (addressDelta != 0 || lineDelta != 0) {
                        pos = putSpecialOpcode(opcode, buffer, pos);
                    }
                } else {
                    /*
                     * does it help to divide and conquer using
                     * a fixed address increment
                     */
                    int remainder = isConstAddPC(addressDelta);
                    if (remainder > 0) {
                        pos = putConstAddPC(buffer, pos);
                        /*
                         * the remaining address can be handled with a
                         * special opcode but what about the line delta
                         */
                        opcode = isSpecialOpcode(remainder, lineDelta);
                        if (opcode != DW_LNS_undefined) {
                            /*
                             * address remainder and line now fit
                             */
                            pos = putSpecialOpcode(opcode, buffer, pos);
                        } else {
                            /*
                             * ok, bump the line separately then use a
                             * special opcode for the address remainder
                             */
                            opcode = isSpecialOpcode(remainder, 0);
                            assert opcode != DW_LNS_undefined;
                            pos = putAdvanceLine(lineDelta, buffer, pos);
                            pos = putSpecialOpcode(opcode, buffer, pos);
                        }
                    } else {
                        /*
                         * increment line and pc separately
                         */
                        if (lineDelta != 0) {
                            pos = putAdvanceLine(lineDelta, buffer, pos);
                        }
                        /*
                         * n.b. we might just have had an out of range line increment
                         * with a zero address increment
                         */
                        if (addressDelta > 0) {
                            /*
                             * see if we can use a ushort for the increment
                             */
                            if (isFixedAdvancePC(addressDelta)) {
                                pos = putFixedAdvancePC((short) addressDelta, buffer, pos);
                            } else {
                                pos = putAdvancePC(addressDelta, buffer, pos);
                            }
                        }
                        pos = putCopy(buffer, pos);
                    }
                }
                /*
                 * move line and address range on
                 */
                line += lineDelta;
                address += addressDelta;
            }
            /*
             * append a final end sequence just below the next primary range
             */
            if (address < primaryRange.getHi()) {
                long addressDelta = primaryRange.getHi() - address;
                /*
                 * increment address before we write the end sequence
                 */
                pos = putAdvancePC(addressDelta, buffer, pos);
            }
            pos = putEndSequence(buffer, pos);
        }
        debug("  [0x%08x] primary file processed %s\n", pos, primaryFileName);

        return pos;
    }

    @Override
    protected void debug(String format, Object... args) {
        if (((int) args[0] - debugBase) < 0x100000) {
            super.debug(format, args);
        } else if (format.startsWith("  [0x%08x] primary file")) {
            super.debug(format, args);
        }
    }

    public int putCopy(byte[] buffer, int p) {
        byte opcode = DW_LNS_copy;
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            debugCopyCount++;
            debug("  [0x%08x] Copy %d\n", pos, debugCopyCount);
            return putByte(opcode, buffer, pos);
        }
    }

    public int putAdvancePC(long uleb, byte[] buffer, int p) {
        byte opcode = DW_LNS_advance_pc;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putULEB(uleb, scratch, 0);
        } else {
            debugAddress += uleb;
            debug("  [0x%08x] Advance PC by %d to 0x%08x\n", pos, uleb, debugAddress);
            pos = putByte(opcode, buffer, pos);
            return putULEB(uleb, buffer, pos);
        }
    }

    public int putAdvanceLine(long sleb, byte[] buffer, int p) {
        byte opcode = DW_LNS_advance_line;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putSLEB(sleb, scratch, 0);
        } else {
            debugLine += sleb;
            debug("  [0x%08x] Advance Line by %d to %d\n", pos, sleb, debugLine);
            pos = putByte(opcode, buffer, pos);
            return putSLEB(sleb, buffer, pos);
        }
    }

    public int putSetFile(String file, long uleb, byte[] buffer, int p) {
        byte opcode = DW_LNS_set_file;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putULEB(uleb, scratch, 0);
        } else {
            debug("  [0x%08x] Set File Name to entry %d in the File Name Table (%s)\n", pos, uleb, file);
            pos = putByte(opcode, buffer, pos);
            return putULEB(uleb, buffer, pos);
        }
    }

    public int putSetColumn(long uleb, byte[] buffer, int p) {
        byte opcode = DW_LNS_set_column;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putULEB(uleb, scratch, 0);
        } else {
            pos = putByte(opcode, buffer, pos);
            return putULEB(uleb, buffer, pos);
        }
    }

    public int putNegateStmt(byte[] buffer, int p) {
        byte opcode = DW_LNS_negate_stmt;
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            return putByte(opcode, buffer, pos);
        }
    }

    public int putSetBasicBlock(byte[] buffer, int p) {
        byte opcode = DW_LNS_set_basic_block;
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            debug("  [0x%08x] Set basic block\n", pos);
            return putByte(opcode, buffer, pos);
        }
    }

    public int putConstAddPC(byte[] buffer, int p) {
        byte opcode = DW_LNS_const_add_pc;
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            int advance = opcodeAddress((byte) 255);
            debugAddress += advance;
            debug("  [0x%08x] Advance PC by constant %d to 0x%08x\n", pos, advance, debugAddress);
            return putByte(opcode, buffer, pos);
        }
    }

    public int putFixedAdvancePC(short arg, byte[] buffer, int p) {
        byte opcode = DW_LNS_fixed_advance_pc;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putShort(arg, scratch, 0);
        } else {
            debugAddress += arg;
            debug("  [0x%08x] Fixed advance Address by %d to 0x%08x\n", pos, arg, debugAddress);
            pos = putByte(opcode, buffer, pos);
            return putShort(arg, buffer, pos);
        }
    }

    public int putEndSequence(byte[] buffer, int p) {
        byte opcode = DW_LNE_end_sequence;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(DW_LNS_extended_prefix, scratch, 0);
            /*
             * insert extended insn byte count as ULEB
             */
            pos = pos + putULEB(1, scratch, 0);
            return pos + putByte(opcode, scratch, 0);
        } else {
            debug("  [0x%08x] Extended opcode 1: End sequence\n", pos);
            debugAddress = debugTextBase;
            debugLine = 1;
            debugCopyCount = 0;
            pos = putByte(DW_LNS_extended_prefix, buffer, pos);
            /*
             * insert extended insn byte count as ULEB
             */
            pos = putULEB(1, buffer, pos);
            return putByte(opcode, buffer, pos);
        }
    }

    public int putSetAddress(long arg, byte[] buffer, int p) {
        byte opcode = DW_LNE_set_address;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(DW_LNS_extended_prefix, scratch, 0);
            /*
             * insert extended insn byte count as ULEB
             */
            pos = pos + putULEB(9, scratch, 0);
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putLong(arg, scratch, 0);
        } else {
            debugAddress = debugTextBase + (int) arg;
            debug("  [0x%08x] Extended opcode 2: Set Address to 0x%08x\n", pos, debugAddress);
            pos = putByte(DW_LNS_extended_prefix, buffer, pos);
            /*
             * insert extended insn byte count as ULEB
             */
            pos = putULEB(9, buffer, pos);
            pos = putByte(opcode, buffer, pos);
            return putRelocatableCodeOffset(arg, buffer, pos);
        }
    }

    public int putDefineFile(String file, long uleb1, long uleb2, long uleb3, byte[] buffer, int p) {
        byte opcode = DW_LNE_define_file;
        int pos = p;
        /*
         * calculate bytes needed for opcode + args
         */
        int fileBytes = file.length() + 1;
        long insnBytes = 1;
        insnBytes += fileBytes;
        insnBytes += putULEB(uleb1, scratch, 0);
        insnBytes += putULEB(uleb2, scratch, 0);
        insnBytes += putULEB(uleb3, scratch, 0);
        if (buffer == null) {
            pos = pos + putByte(DW_LNS_extended_prefix, scratch, 0);
            /*
             * write insnBytes as a ULEB
             */
            pos += putULEB(insnBytes, scratch, 0);
            return pos + (int) insnBytes;
        } else {
            debug("  [0x%08x] Extended opcode 3: Define File %s idx %d ts1 %d ts2 %d\n", pos, file, uleb1, uleb2, uleb3);
            pos = putByte(DW_LNS_extended_prefix, buffer, pos);
            /*
             * insert insn length as uleb
             */
            pos = putULEB(insnBytes, buffer, pos);
            /*
             * insert opcode and args
             */
            pos = putByte(opcode, buffer, pos);
            pos = putAsciiStringBytes(file, buffer, pos);
            pos = putULEB(uleb1, buffer, pos);
            pos = putULEB(uleb2, buffer, pos);
            return putULEB(uleb3, buffer, pos);
        }
    }

    public static int opcodeId(byte opcode) {
        int iopcode = opcode & 0xff;
        return iopcode - DW_LN_OPCODE_BASE;
    }

    public static int opcodeAddress(byte opcode) {
        int iopcode = opcode & 0xff;
        return (iopcode - DW_LN_OPCODE_BASE) / DW_LN_LINE_RANGE;
    }

    public static int opcodeLine(byte opcode) {
        int iopcode = opcode & 0xff;
        return ((iopcode - DW_LN_OPCODE_BASE) % DW_LN_LINE_RANGE) + DW_LN_LINE_BASE;
    }

    public int putSpecialOpcode(byte opcode, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            if (debug && opcode == 0) {
                debug("  [0x%08x] ERROR Special Opcode %d: Address 0x%08x Line %d\n", debugAddress, debugLine);
            }
            debugAddress += opcodeAddress(opcode);
            debugLine += opcodeLine(opcode);
            debug("  [0x%08x] Special Opcode %d: advance Address by %d to 0x%08x and Line by %d to %d\n",
                  pos, opcodeId(opcode), opcodeAddress(opcode), debugAddress, opcodeLine(opcode), debugLine);
            return putByte(opcode, buffer, pos);
        }
    }

    private static final int MAX_ADDRESS_ONLY_DELTA = (0xff - DW_LN_OPCODE_BASE) / DW_LN_LINE_RANGE;
    private static final int MAX_ADDPC_DELTA = MAX_ADDRESS_ONLY_DELTA + (MAX_ADDRESS_ONLY_DELTA - 1);

    public static byte isSpecialOpcode(long addressDelta, long lineDelta) {
        if (addressDelta < 0) {
            return DW_LNS_undefined;
        }
        if (lineDelta >= DW_LN_LINE_BASE) {
            long offsetLineDelta = lineDelta - DW_LN_LINE_BASE;
            if (offsetLineDelta < DW_LN_LINE_RANGE) {
                /*
                 * line_delta can be encoded
                 * check if address is ok
                 */
                if (addressDelta <= MAX_ADDRESS_ONLY_DELTA) {
                    long opcode = DW_LN_OPCODE_BASE + (addressDelta * DW_LN_LINE_RANGE) + offsetLineDelta;
                    if (opcode <= 255) {
                        return (byte) opcode;
                    }
                }
            }
        }

        /*
         * answer no by returning an invalid opcode
         */
        return DW_LNS_undefined;
    }

    public static int isConstAddPC(long addressDelta) {
        if (addressDelta < MAX_ADDRESS_ONLY_DELTA) {
            return 0;
        }
        if (addressDelta <= MAX_ADDPC_DELTA) {
            return (int) (addressDelta - MAX_ADDRESS_ONLY_DELTA);
        } else {
            return 0;
        }
    }

    public static boolean isFixedAdvancePC(long addressDiff) {
        return addressDiff >= 0 && addressDiff < 0xffff;
    }

    /**
     * debug_line section content depends on debug_str section content and offset.
     */
    public static final String TARGET_SECTION_NAME = DW_STR_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    public final LayoutDecision.Kind[] targetSectionKinds = {
            LayoutDecision.Kind.CONTENT,
            LayoutDecision.Kind.OFFSET,
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }
}
