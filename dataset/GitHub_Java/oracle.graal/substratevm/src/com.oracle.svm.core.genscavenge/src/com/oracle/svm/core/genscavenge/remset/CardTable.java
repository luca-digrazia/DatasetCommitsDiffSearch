/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.remset;

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.graal.BarrierSnippets;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * A card table is a remembered set that summarizes pointer stores into a region. A card is "dirty"
 * if a pointer has been stored recently into the memory summarized by the card, or "clean"
 * otherwise.
 * <p>
 * When looking for roots into the young space, the whole old space need not be searched for
 * pointers, only the parts of the old space covered by dirty cards. The card table works in concert
 * with the {@link FirstObjectTable} to find objects that cross onto memory covered by a card.
 * <p>
 * At each pointer store the card corresponding to the destination of the store is dirtied. At each
 * collection, the dirty cards are scanned and the corresponding memory is examined for pointers to
 * the young space. When the memory has been scanned, the corresponding card is cleaned.
 * <p>
 * Implementation notes:
 * <ul>
 * <li>In theory, I only need "clean" and "dirty" values in the table, but since bit manipulations
 * are expensive (particularly atomic bit manipulations), I trade space for time and make each entry
 * a byte.</li>
 *
 * <li>The "dirty" value is 0, since that makes dirtying a card a single "clearByte" instructions
 * which is available in all the instruction set architectures I care about, whereas a "setByte"
 * with a non-zero value takes more instruction space.</li>
 * </ul>
 */
final class CardTable {
    public static final int BYTES_COVERED_BY_ENTRY = 512;

    private static final int ENTRY_SIZE_BYTES = 1;

    private static final int DIRTY_ENTRY = 0;
    private static final int CLEAN_ENTRY = 1;

    private static final CardTableVerificationVisitor CARD_TABLE_VERIFICATION_VISITOR = new CardTableVerificationVisitor();

    private CardTable() {
    }

    public static void cleanTable(Pointer tableStart, UnsignedWord size) {
        UnmanagedMemoryUtil.fill(tableStart, size, (byte) CLEAN_ENTRY);
    }

    public static void setDirty(Pointer table, UnsignedWord index) {
        table.writeByte(indexToTableOffset(index), (byte) DIRTY_ENTRY, BarrierSnippets.CARD_REMEMBERED_SET_LOCATION);
    }

    public static void setClean(Pointer table, UnsignedWord index) {
        table.writeByte(indexToTableOffset(index), (byte) CLEAN_ENTRY, BarrierSnippets.CARD_REMEMBERED_SET_LOCATION);
    }

    public static boolean isDirty(Pointer table, UnsignedWord index) {
        int entry = readEntry(table, index);
        return entry == DIRTY_ENTRY;
    }

    private static boolean isClean(Pointer table, UnsignedWord index) {
        int entry = readEntry(table, index);
        return entry == CLEAN_ENTRY;
    }

    private static int readEntry(Pointer table, UnsignedWord index) {
        return table.readByte(indexToTableOffset(index));
    }

    private static UnsignedWord indexToTableOffset(UnsignedWord index) {
        return index.multiply(ENTRY_SIZE_BYTES);
    }

    public static UnsignedWord memoryOffsetToIndex(UnsignedWord offset) {
        return offset.unsignedDivide(BYTES_COVERED_BY_ENTRY);
    }

    public static Pointer indexToMemoryPointer(Pointer memoryStart, UnsignedWord index) {
        UnsignedWord offset = index.multiply(BYTES_COVERED_BY_ENTRY);
        return memoryStart.add(offset);
    }

    public static UnsignedWord tableSizeForMemorySize(UnsignedWord memorySize) {
        UnsignedWord maxIndex = indexLimitForMemorySize(memorySize);
        return maxIndex.multiply(ENTRY_SIZE_BYTES);
    }

    public static UnsignedWord indexLimitForMemorySize(UnsignedWord memorySize) {
        UnsignedWord roundedMemory = UnsignedUtils.roundUp(memorySize, WordFactory.unsigned(BYTES_COVERED_BY_ENTRY));
        return CardTable.memoryOffsetToIndex(roundedMemory);
    }

    public static boolean verify(Pointer ctStart, Pointer objectsStart, Pointer objectsLimit) {
        boolean success = true;
        Pointer curPtr = objectsStart;
        while (curPtr.belowThan(objectsLimit)) {
            Object curObj = curPtr.toObject();
            CARD_TABLE_VERIFICATION_VISITOR.initialize(curObj, ctStart, objectsStart);
            InteriorObjRefWalker.walkObject(curObj, CARD_TABLE_VERIFICATION_VISITOR);
            success &= CARD_TABLE_VERIFICATION_VISITOR.success;
            curPtr = LayoutEncoding.getObjectEnd(curObj);
        }
        return success;
    }

    private static class CardTableVerificationVisitor implements ObjectReferenceVisitor {
        private Object parentObject;
        private Pointer cardTableStart;
        private Pointer objectsStart;
        private boolean success;

        @SuppressWarnings("hiding")
        public void initialize(Object parentObject, Pointer cardTableStart, Pointer objectsStart) {
            this.parentObject = parentObject;
            this.cardTableStart = cardTableStart;
            this.objectsStart = objectsStart;
            this.success = true;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            Pointer objPtr = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (objPtr.isNonNull() && !HeapImpl.getHeapImpl().isInImageHeap(objPtr)) {
                Object obj = objPtr.toObject();
                HeapChunk.Header<?> objChunk = HeapChunk.getEnclosingHeapChunk(obj);
                Space chunkSpace = HeapChunk.getSpace(objChunk);
                if (chunkSpace.isYoungSpace()) {
                    UnsignedWord cardTableIndex = memoryOffsetToIndex(objRef.subtract(objectsStart));
                    if (isClean(cardTableStart, cardTableIndex)) {
                        Pointer cardTableAddress = cardTableStart.add(indexToTableOffset(cardTableIndex));
                        Log.log().string("Object ").hex(Word.objectToUntrackedPointer(parentObject)).string(" has an object reference at ").hex(objRef).string(" that points to ").hex(objPtr)
                                        .string(", which is in the young generation. However, the card table at ").hex(cardTableAddress).string(" is clean.").newline();
                        this.success = false;
                    }
                }
            }
            return true;
        }
    }
}
