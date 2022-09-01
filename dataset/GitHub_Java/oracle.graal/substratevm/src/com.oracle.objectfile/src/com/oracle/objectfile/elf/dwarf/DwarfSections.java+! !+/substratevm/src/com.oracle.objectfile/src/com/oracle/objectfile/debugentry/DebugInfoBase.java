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

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.DirEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.Range;
import com.oracle.objectfile.debugentry.StringTable;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange;
import com.oracle.objectfile.elf.ELFMachine;

/**
 * A class that models the debug info in an organization that facilitates generation of the required
 * DWARF sections. It groups common data and behaviours for use by the various subclasses of class
 * DwarfSectionImpl that take responsibility for generating content for a specific section type.
 */
public class DwarfSections {

    /*
     * Names of the different ELF sections we create or reference in reverse dependency order.
     */
    public static final String TEXT_SECTION_NAME = ".text";
    public static final String DW_STR_SECTION_NAME = ".debug_str";
    public static final String DW_LINE_SECTION_NAME = ".debug_line";
    public static final String DW_FRAME_SECTION_NAME = ".debug_frame";
    public static final String DW_ABBREV_SECTION_NAME = ".debug_abbrev";
    public static final String DW_INFO_SECTION_NAME = ".debug_info";
    public static final String DW_ARANGES_SECTION_NAME = ".debug_aranges";

    /**
     * Currently generated debug info relies on DWARF spec vesion 2.
     */
    public static final short DW_VERSION_2 = 2;

    /*
     * Define all the abbrev section codes we need for our DIEs.
     */
    @SuppressWarnings("unused") public static final int DW_ABBREV_CODE_null = 0;
    public static final int DW_ABBREV_CODE_compile_unit = 1;
    public static final int DW_ABBREV_CODE_subprogram = 2;

    /*
     * Define all the Dwarf tags we need for our DIEs.
     */
    public static final int DW_TAG_compile_unit = 0x11;
    public static final int DW_TAG_subprogram = 0x2e;
    /*
     * Define all the Dwarf attributes we need for our DIEs.
     */
    public static final int DW_AT_null = 0x0;
    public static final int DW_AT_name = 0x3;
    @SuppressWarnings("unused") public static final int DW_AT_comp_dir = 0x1b;
    public static final int DW_AT_stmt_list = 0x10;
    public static final int DW_AT_low_pc = 0x11;
    public static final int DW_AT_hi_pc = 0x12;
    public static final int DW_AT_language = 0x13;
    public static final int DW_AT_external = 0x3f;
    @SuppressWarnings("unused") public static final int DW_AT_return_addr = 0x2a;
    @SuppressWarnings("unused") public static final int DW_AT_frame_base = 0x40;
    /*
     * Define all the Dwarf attribute forms we need for our DIEs.
     */
    public static final int DW_FORM_null = 0x0;
    @SuppressWarnings("unused") private static final int DW_FORM_string = 0x8;
    public static final int DW_FORM_strp = 0xe;
    public static final int DW_FORM_addr = 0x1;
    public static final int DW_FORM_data1 = 0x0b;
    public static final int DW_FORM_data4 = 0x6;
    @SuppressWarnings("unused") public static final int DW_FORM_data8 = 0x7;
    @SuppressWarnings("unused") public static final int DW_FORM_block1 = 0x0a;
    public static final int DW_FORM_flag = 0xc;

    /*
     * Define specific attribute values for given attribute or form types.
     */
    /*
     * DIE header has_children attribute values.
     */
    public static final byte DW_CHILDREN_no = 0;
    public static final byte DW_CHILDREN_yes = 1;
    /*
     * DW_FORM_flag attribute values.
     */
    @SuppressWarnings("unused") public static final byte DW_FLAG_false = 0;
    public static final byte DW_FLAG_true = 1;
    /*
     * Value for DW_AT_language attribute with form DATA1.
     */
    public static final byte DW_LANG_Java = 0xb;

    /*
     * DW_AT_Accessibility attribute values.
     *
     * These are not needed until we make functions members.
     */
    @SuppressWarnings("unused") public static final byte DW_ACCESS_public = 1;
    @SuppressWarnings("unused") public static final byte DW_ACCESS_protected = 2;
    @SuppressWarnings("unused") public static final byte DW_ACCESS_private = 3;

    /*
     * Others that are not yet needed.
     */
    @SuppressWarnings("unused") public static final int DW_AT_type = 0; // only present for non-void
    // functions
    @SuppressWarnings("unused") public static final int DW_AT_accessibility = 0;

    /*
     * CIE and FDE entries.
     */

    /* Full byte/word values. */
    public static final int DW_CFA_CIE_id = -1;
    @SuppressWarnings("unused") public static final int DW_CFA_FDE_id = 0;

    public static final byte DW_CFA_CIE_version = 1;

    /* Values encoded in high 2 bits. */
    public static final byte DW_CFA_advance_loc = 0x1;
    public static final byte DW_CFA_offset = 0x2;
    @SuppressWarnings("unused") public static final byte DW_CFA_restore = 0x3;

    /* Values encoded in low 6 bits. */
    public static final byte DW_CFA_nop = 0x0;
    @SuppressWarnings("unused") public static final byte DW_CFA_set_loc1 = 0x1;
    public static final byte DW_CFA_advance_loc1 = 0x2;
    public static final byte DW_CFA_advance_loc2 = 0x3;
    public static final byte DW_CFA_advance_loc4 = 0x4;
    @SuppressWarnings("unused") public static final byte DW_CFA_offset_extended = 0x5;
    @SuppressWarnings("unused") public static final byte DW_CFA_restore_extended = 0x6;
    @SuppressWarnings("unused") public static final byte DW_CFA_undefined = 0x7;
    @SuppressWarnings("unused") public static final byte DW_CFA_same_value = 0x8;
    public static final byte DW_CFA_register = 0x9;
    public static final byte DW_CFA_def_cfa = 0xc;
    @SuppressWarnings("unused") public static final byte DW_CFA_def_cfa_register = 0xd;
    public static final byte DW_CFA_def_cfa_offset = 0xe;

    private ByteOrder byteOrder;
    private DwarfStrSectionImpl dwarfStrSection;
    private DwarfAbbrevSectionImpl dwarfAbbrevSection;
    private DwarfInfoSectionImpl dwarfInfoSection;
    private DwarfARangesSectionImpl dwarfARangesSection;
    private DwarfLineSectionImpl dwarfLineSection;
    private DwarfFrameSectionImpl dwarfFameSection;

    public DwarfSections(ELFMachine elfMachine, ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
        dwarfStrSection = new DwarfStrSectionImpl(this);
        dwarfAbbrevSection = new DwarfAbbrevSectionImpl(this);
        dwarfInfoSection = new DwarfInfoSectionImpl(this);
        dwarfARangesSection = new DwarfARangesSectionImpl(this);
        dwarfLineSection = new DwarfLineSectionImpl(this);
        if (elfMachine == ELFMachine.AArch64) {
            dwarfFameSection = new DwarfFrameSectionImplAArch64(this);
        } else {
            dwarfFameSection = new DwarfFrameSectionImplX86_64(this);
        }
    }

    public DwarfStrSectionImpl getStrSectionImpl() {
        return dwarfStrSection;
    }

    public DwarfAbbrevSectionImpl getAbbrevSectionImpl() {
        return dwarfAbbrevSection;
    }

    public DwarfFrameSectionImpl getFrameSectionImpl() {
        return dwarfFameSection;
    }

    public DwarfInfoSectionImpl getInfoSectionImpl() {
        return dwarfInfoSection;
    }

    public DwarfARangesSectionImpl getARangesSectionImpl() {
        return dwarfARangesSection;
    }

    public DwarfLineSectionImpl getLineSectionImpl() {
        return dwarfLineSection;
    }

    /**
     * A table listing all known strings, some of which may be marked for insertion into the
     * debug_str section.
     */
    private StringTable stringTable = new StringTable();

    /**
     * Index of all dirs in which files are found to reside either as part of substrate/compiler or
     * user code.
     */
    private Map<Path, DirEntry> dirsIndex = new HashMap<>();

    /*
     * The obvious traversal structure for debug records is:
     *
     * 1) by top level compiled method (primary Range) ordered by ascending address
     *
     * 2) by inlined method (sub range) within top level method ordered by ascending address
     *
     * These can be used to ensure that all debug records are generated in increasing address order
     *
     * An alternative traversal option is
     *
     * 1) by top level class (String id)
     *
     * 2) by top level compiled method (primary Range) within a class ordered by ascending address
     *
     * 3) by inlined method (sub range) within top level method ordered by ascending address
     *
     * This relies on the (current) fact that methods of a given class always appear in a single
     * continuous address range with no intervening code from other methods or data values. this
     * means we can treat each class as a compilation unit, allowing data common to all methods of
     * the class to be shared.
     *
     * A third option appears to be to traverse via files, then top level class within file etc.
     * Unfortunately, files cannot be treated as the compilation unit. A file F may contain multiple
     * classes, say C1 and C2. There is no guarantee that methods for some other class C' in file F'
     * will not be compiled into the address space interleaved between methods of C1 and C2. That is
     * a shame because generating debug info records one file at a time would allow more sharing
     * e.g. enabling all classes in a file to share a single copy of the file and dir tables.
     */

    /**
     * List of class entries detailing class info for primary ranges.
     */
    private LinkedList<ClassEntry> primaryClasses = new LinkedList<>();
    /**
     * index of already seen classes.
     */
    private Map<String, ClassEntry> primaryClassesIndex = new HashMap<>();

    /**
     * Index of files which contain primary or secondary ranges.
     */
    private Map<Path, FileEntry> filesIndex = new HashMap<>();

    /**
     * Indirects this call to the string table.
     *
     * @param string the string whose index is required.
     *
     * @return the offset of the string in the .debug_str section.
     */
    public int debugStringIndex(String string) {
        return stringTable.debugStringIndex(string);
    }

    /**
     * Entry point allowing ELFObjectFile to pass on information about types, code and heap data.
     *
     * @param debugInfoProvider provider instance passed by ObjectFile client.
     */
    @SuppressWarnings("try")
    public void installDebugInfo(DebugInfoProvider debugInfoProvider) {
        /*
         * This will be needed once we add support for type info:
         *
         * DebugTypeInfoProvider typeInfoProvider = debugInfoProvider.typeInfoProvider(); for
         * (DebugTypeInfo debugTypeInfo : typeInfoProvider) { install types }
         */

        /*
         * Ensure we have a null string in the string section.
         */
        stringTable.uniqueDebugString("");

        debugInfoProvider.codeInfoProvider().forEach(debugCodeInfo -> debugCodeInfo.debugContext((debugContext) -> {
            /*
             * primary file name and full method name need to be written to the debug_str section
             */
            String fileName = debugCodeInfo.fileName();
            Path filePath = debugCodeInfo.filePath();
            // switch '$' in class names for '.'
            String className = debugCodeInfo.className().replaceAll("\\$", ".");
            String methodName = debugCodeInfo.methodName();
            String paramNames = debugCodeInfo.paramNames();
            String returnTypeName = debugCodeInfo.returnTypeName();
            int lo = debugCodeInfo.addressLo();
            int hi = debugCodeInfo.addressHi();
            int primaryLine = debugCodeInfo.line();

            Range primaryRange = new Range(fileName, filePath, className, methodName, paramNames, returnTypeName, stringTable, lo, hi, primaryLine);
            debugContext.log(DebugContext.INFO_LEVEL, "PrimaryRange %s.%s %s %s:%d [0x%x, 0x%x]", className, methodName, filePath, fileName, primaryLine, lo, hi);
            addRange(primaryRange, debugCodeInfo.getFrameSizeChanges(), debugCodeInfo.getFrameSize());
            debugCodeInfo.lineInfoProvider().forEach(debugLineInfo -> {
                String fileNameAtLine = debugLineInfo.fileName();
                Path filePathAtLine = debugLineInfo.filePath();
                // Switch '$' in class names for '.'
                String classNameAtLine = debugLineInfo.className().replaceAll("\\$", ".");
                String methodNameAtLine = debugLineInfo.methodName();
                int loAtLine = lo + debugLineInfo.addressLo();
                int hiAtLine = lo + debugLineInfo.addressHi();
                int line = debugLineInfo.line();
                /*
                 * Record all subranges even if they have no line or file so we at least get a
                 * symbol for them.
                 */
                Range subRange = new Range(fileNameAtLine, filePathAtLine, classNameAtLine, methodNameAtLine, "", "", stringTable, loAtLine, hiAtLine, line, primaryRange);
                addSubRange(primaryRange, subRange);
                try (DebugContext.Scope s = debugContext.scope("Subranges")) {
                    debugContext.log(DebugContext.VERBOSE_LEVEL, "SubRange %s.%s %s %s:%d 0x%x, 0x%x]", classNameAtLine, methodNameAtLine, filePathAtLine, fileNameAtLine, line, loAtLine, hiAtLine);
                }
            });
        }));
        /*
         * This will be needed once we add support for data info:
         *
         * DebugDataInfoProvider dataInfoProvider = debugInfoProvider.dataInfoProvider(); for
         * (DebugDataInfo debugDataInfo : dataInfoProvider) { install details of heap elements
         * String name = debugDataInfo.toString(); }
         */
    }

    private ClassEntry ensureClassEntry(Range range) {
        String className = range.getClassName();
        /*
         * See if we already have an entry.
         */
        ClassEntry classEntry = primaryClassesIndex.get(className);
        if (classEntry == null) {
            /*
             * Create and index the entry associating it with the right file.
             */
            FileEntry fileEntry = ensureFileEntry(range);
            classEntry = new ClassEntry(className, fileEntry);
            primaryClasses.add(classEntry);
            primaryClassesIndex.put(className, classEntry);
        }
        assert classEntry.getClassName().equals(className);
        return classEntry;
    }

    private FileEntry ensureFileEntry(Range range) {
        String fileName = range.getFileName();
        if (fileName == null) {
            return null;
        }
        Path filePath = range.getFilePath();
        Path fileAsPath = range.getFileAsPath();
        /*
         * Ensure we have an entry.
         */
        FileEntry fileEntry = filesIndex.get(fileAsPath);
        if (fileEntry == null) {
            DirEntry dirEntry = ensureDirEntry(filePath);
            fileEntry = new FileEntry(fileName, dirEntry);
            /*
             * Index the file entry by file path.
             */
            filesIndex.put(fileAsPath, fileEntry);
            if (!range.isPrimary()) {
                /* Check we have a file for the corresponding primary range. */
                Range primaryRange = range.getPrimary();
                FileEntry primaryFileEntry = filesIndex.get(primaryRange.getFileAsPath());
                assert primaryFileEntry != null;
            }
        }
        return fileEntry;
    }

    private void addRange(Range primaryRange, List<DebugFrameSizeChange> frameSizeInfos, int frameSize) {
        assert primaryRange.isPrimary();
        ClassEntry classEntry = ensureClassEntry(primaryRange);
        classEntry.addPrimary(primaryRange, frameSizeInfos, frameSize);
    }

    private void addSubRange(Range primaryRange, Range subrange) {
        assert primaryRange.isPrimary();
        assert !subrange.isPrimary();
        String className = primaryRange.getClassName();
        ClassEntry classEntry = primaryClassesIndex.get(className);
        FileEntry subrangeFileEntry = ensureFileEntry(subrange);
        /*
         * The primary range should already have been seen and associated with a primary class entry.
         */
        assert classEntry.primaryIndexFor(primaryRange) != null;
        if (subrangeFileEntry != null) {
            classEntry.addSubRange(subrange, subrangeFileEntry);
        }
    }

    private DirEntry ensureDirEntry(Path filePath) {
        if (filePath == null) {
            return null;
        }
        DirEntry dirEntry = dirsIndex.get(filePath);
        if (dirEntry == null) {
            dirEntry = new DirEntry(filePath);
            dirsIndex.put(filePath, dirEntry);
        }
        return dirEntry;
    }

    public StringTable getStringTable() {
        return stringTable;
    }

    public LinkedList<ClassEntry> getPrimaryClasses() {
        return primaryClasses;
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }
}
