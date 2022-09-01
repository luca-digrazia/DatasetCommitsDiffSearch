/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.ImageHeapChunkWriter;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;

// Checkstyle: stop
import sun.nio.ch.DirectBuffer;
// Checkstyle: resume

/** Chunk writer that uses the same methods as memory management during image runtime. */
public class RuntimeImageHeapChunkWriter implements ImageHeapChunkWriter {
    private final Pointer heapBegin;
    private final Word layoutToBufferOffsetAddend;

    public RuntimeImageHeapChunkWriter(ByteBuffer buffer, long layoutToBufferOffsetAddend) {
        DirectBuffer direct = (DirectBuffer) buffer; // required from caller
        this.heapBegin = WordFactory.pointer(direct.address());
        this.layoutToBufferOffsetAddend = WordFactory.signed(layoutToBufferOffsetAddend);
    }

    private Pointer getChunkPointerInBuffer(int chunkPosition) {
        return heapBegin.add(chunkPosition).add(layoutToBufferOffsetAddend);
    }

    @Override
    public void initializeAlignedChunk(int chunkPosition, long topOffset, long endOffset, long offsetToPreviousChunk, long offsetToNextChunk) {
        AlignedHeapChunk.AlignedHeader header = (AlignedHeapChunk.AlignedHeader) getChunkPointerInBuffer(chunkPosition);
        header.setTopOffset(WordFactory.unsigned(topOffset));
        header.setEndOffset(WordFactory.unsigned(endOffset));
        header.setSpace(null);
        header.setOffsetToPreviousChunk(WordFactory.unsigned(offsetToPreviousChunk));
        header.setOffsetToNextChunk(WordFactory.unsigned(offsetToNextChunk));

        CardTable.cleanTableToPointer(AlignedChunkRememberedSet.getCardTableStart(header), AlignedChunkRememberedSet.getCardTableLimit(header));
        FirstObjectTable.initializeTableToLimit(AlignedChunkRememberedSet.getFirstObjectTableStart(header), AlignedChunkRememberedSet.getFirstObjectTableLimit(header));
    }

    @Override
    public void insertIntoAlignedChunkFirstObjectTable(int chunkPosition, long objectOffsetInChunk, long objectEndOffsetInChunk) {
        AlignedHeapChunk.AlignedHeader header = (AlignedHeapChunk.AlignedHeader) getChunkPointerInBuffer(chunkPosition);
        FirstObjectTable.setTableForObjectAtOffsetUnchecked(AlignedChunkRememberedSet.getFirstObjectTableStart(header),
                        WordFactory.unsigned(objectOffsetInChunk).subtract(AlignedHeapChunk.getObjectsStartOffset()),
                        WordFactory.unsigned(objectEndOffsetInChunk).subtract(AlignedHeapChunk.getObjectsStartOffset()));
    }

    @Override
    public void initializeUnalignedChunk(int chunkPosition, long topOffset, long endOffset, long offsetToPreviousChunk, long offsetToNextChunk) {
        UnalignedHeapChunk.UnalignedHeader header = (UnalignedHeapChunk.UnalignedHeader) getChunkPointerInBuffer(chunkPosition);
        header.setTopOffset(WordFactory.unsigned(topOffset));
        header.setEndOffset(WordFactory.unsigned(endOffset));
        header.setSpace(null);
        header.setOffsetToPreviousChunk(WordFactory.unsigned(offsetToPreviousChunk));
        header.setOffsetToNextChunk(WordFactory.unsigned(offsetToNextChunk));

        CardTable.cleanTableToPointer(UnalignedChunkRememberedSet.getCardTableStart(header), UnalignedChunkRememberedSet.getCardTableLimit(header));
    }
}
