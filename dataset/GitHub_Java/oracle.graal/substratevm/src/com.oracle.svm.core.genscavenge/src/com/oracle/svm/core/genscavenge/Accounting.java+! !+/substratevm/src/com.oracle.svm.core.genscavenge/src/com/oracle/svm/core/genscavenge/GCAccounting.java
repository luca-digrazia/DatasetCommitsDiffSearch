/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.log.Log;

/**
 * Accounting for {@link GCImpl}. Times are in nanoseconds. ChunkBytes refer to bytes reserved (but
 * maybe not occupied). ObjectBytes refer to bytes occupied by objects.
 */
final class Accounting {
    /* State that is available to collection policies, etc. */
    private long incrementalCollectionCount = 0;
    private long incrementalCollectionTotalNanos = 0;
    private long completeCollectionCount = 0;
    private long completeCollectionTotalNanos = 0;
    private UnsignedWord collectedTotalChunkBytes = WordFactory.zero();
    private UnsignedWord normalChunkBytes = WordFactory.zero();
    private UnsignedWord promotedTotalChunkBytes = WordFactory.zero();
    private UnsignedWord copiedTotalChunkBytes = WordFactory.zero();

    /* Before and after measures. */
    private UnsignedWord youngChunkBytesBefore = WordFactory.zero();
    private UnsignedWord youngChunkBytesAfter = WordFactory.zero();
    private UnsignedWord oldChunkBytesBefore = WordFactory.zero();
    private UnsignedWord oldChunkBytesAfter = WordFactory.zero();
    private UnsignedWord lastCollectionPromotedChunkBytes = WordFactory.zero();

    /*
     * Bytes allocated in Objects, as opposed to bytes of chunks. These are only maintained if
     * -R:+PrintGCSummary because they are expensive.
     */
    private UnsignedWord collectedTotalObjectBytes = WordFactory.zero();
    private UnsignedWord youngObjectBytesBefore = WordFactory.zero();
    private UnsignedWord oldObjectBytesBefore = WordFactory.zero();
    private UnsignedWord normalObjectBytes = WordFactory.zero();

    @Platforms(Platform.HOSTED_ONLY.class)
    Accounting() {
    }

    long getIncrementalCollectionCount() {
        return incrementalCollectionCount;
    }

    long getIncrementalCollectionTotalNanos() {
        return incrementalCollectionTotalNanos;
    }

    UnsignedWord getNormalChunkBytes() {
        return normalChunkBytes;
    }

    long getCompleteCollectionCount() {
        return completeCollectionCount;
    }

    long getCompleteCollectionTotalNanos() {
        return completeCollectionTotalNanos;
    }

    UnsignedWord getCollectedTotalChunkBytes() {
        return collectedTotalChunkBytes;
    }

    UnsignedWord getCollectedTotalObjectBytes() {
        return collectedTotalObjectBytes;
    }

    UnsignedWord getNormalObjectBytes() {
        return normalObjectBytes;
    }

    UnsignedWord getOldGenerationAfterChunkBytes() {
        return oldChunkBytesAfter;
    }

    UnsignedWord getYoungChunkBytesAfter() {
        return youngChunkBytesAfter;
    }

    UnsignedWord getLastCollectionPromotedChunkBytes() {
        return lastCollectionPromotedChunkBytes;
    }

    void beforeCollection() {
        Log trace = Log.noopLog().string("[GCImpl.Accounting.beforeCollection:").newline();
        /* Gather some space statistics. */
        HeapImpl heap = HeapImpl.getHeapImpl();
        YoungGeneration youngGen = heap.getYoungGeneration();
        youngChunkBytesBefore = youngGen.getChunkUsedBytes();
        /* This is called before the collection, so OldSpace is FromSpace. */
        Space oldSpace = heap.getOldGeneration().getFromSpace();
        oldChunkBytesBefore = oldSpace.getChunkBytes();
        /* Objects are allocated in the young generation. */
        normalChunkBytes = normalChunkBytes.add(youngChunkBytesBefore);
        if (HeapOptions.PrintGCSummary.getValue()) {
            youngObjectBytesBefore = youngGen.getObjectBytes();
            oldObjectBytesBefore = oldSpace.getObjectBytes();
            normalObjectBytes = normalObjectBytes.add(youngObjectBytesBefore);
        }
        trace.string("  youngChunkBytesBefore: ").unsigned(youngChunkBytesBefore)
                        .string("  oldChunkBytesBefore: ").unsigned(oldChunkBytesBefore);
        trace.string("]").newline();
    }

    void afterCollection(boolean completeCollection, Timer collectionTimer) {
        if (completeCollection) {
            afterCompleteCollection(collectionTimer);
        } else {
            afterIncrementalCollection(collectionTimer);
        }
    }

    private void afterIncrementalCollection(Timer collectionTimer) {
        Log trace = Log.noopLog().string("[GCImpl.Accounting.afterIncrementalCollection:");
        /*
         * Aggregating collection information is needed because any given collection policy may not
         * be called for all collections, but may want to make decisions based on the aggregate
         * values.
         */
        incrementalCollectionCount += 1;
        afterCollectionCommon();
        /* Incremental collections only promote. */
        lastCollectionPromotedChunkBytes = oldChunkBytesAfter.subtract(oldChunkBytesBefore);
        promotedTotalChunkBytes = promotedTotalChunkBytes.add(lastCollectionPromotedChunkBytes);
        incrementalCollectionTotalNanos += collectionTimer.getMeasuredNanos();
        trace.string("  incrementalCollectionCount: ").signed(incrementalCollectionCount)
                        .string("  oldChunkBytesAfter: ").unsigned(oldChunkBytesAfter)
                        .string("  oldChunkBytesBefore: ").unsigned(oldChunkBytesBefore)
                        .string("  promotedChunkBytes: ").unsigned(lastCollectionPromotedChunkBytes);
        trace.string("]").newline();
    }

    private void afterCompleteCollection(Timer collectionTimer) {
        Log trace = Log.noopLog().string("[GCImpl.Accounting.afterCompleteCollection:");
        completeCollectionCount += 1;
        afterCollectionCommon();
        /* Complete collections only copy, and they copy everything. */
        copiedTotalChunkBytes = copiedTotalChunkBytes.add(oldChunkBytesAfter);
        completeCollectionTotalNanos += collectionTimer.getMeasuredNanos();
        trace.string("  completeCollectionCount: ").signed(completeCollectionCount)
                        .string("  oldChunkBytesAfter: ").unsigned(oldChunkBytesAfter);
        trace.string("]").newline();
    }

    void afterCollectionCommon() {
        HeapImpl heap = HeapImpl.getHeapImpl();
        // This is called after the collection, after the space flip, so OldSpace is FromSpace.
        YoungGeneration youngGen = heap.getYoungGeneration();
        youngChunkBytesAfter = youngGen.getChunkUsedBytes();
        Space oldSpace = heap.getOldGeneration().getFromSpace();
        oldChunkBytesAfter = oldSpace.getChunkBytes();
        UnsignedWord beforeChunkBytes = youngChunkBytesBefore.add(oldChunkBytesBefore);
        UnsignedWord afterChunkBytes = oldChunkBytesAfter.add(youngChunkBytesAfter);
        UnsignedWord collectedChunkBytes = beforeChunkBytes.subtract(afterChunkBytes);
        collectedTotalChunkBytes = collectedTotalChunkBytes.add(collectedChunkBytes);
        if (HeapOptions.PrintGCSummary.getValue()) {
            UnsignedWord youngObjectBytesAfter = youngGen.getObjectBytes();
            UnsignedWord oldObjectBytesAfter = oldSpace.getObjectBytes();
            UnsignedWord beforeObjectBytes = youngObjectBytesBefore.add(oldObjectBytesBefore);
            UnsignedWord collectedObjectBytes = beforeObjectBytes.subtract(oldObjectBytesAfter).subtract(youngObjectBytesAfter);
            collectedTotalObjectBytes = collectedTotalObjectBytes.add(collectedObjectBytes);
        }
    }
}
