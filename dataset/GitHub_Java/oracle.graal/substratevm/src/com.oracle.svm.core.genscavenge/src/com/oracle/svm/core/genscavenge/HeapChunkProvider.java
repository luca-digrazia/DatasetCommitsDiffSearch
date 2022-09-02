/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.HeapChunk.Header;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicUnsigned;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.thread.VMThreads;

/**
 * Allocates and frees the memory for aligned and unaligned heap chunks. The methods are
 * thread-safe, so no locking is necessary when calling them.
 *
 * Memory for aligned chunks is not immediately released to the OS. Up to
 * {@link HeapPolicy#getMinimumHeapSize()} chunks are saved in an unused chunk list. Memory for
 * unaligned chunks is released immediately.
 */
final class HeapChunkProvider {
    /**
     * The head of the linked list of unused aligned chunks. Chunks are chained using
     * {@link HeapChunk#getNext}.
     */
    private final UninterruptibleUtils.AtomicPointer<AlignedHeader> unusedAlignedChunks = new UninterruptibleUtils.AtomicPointer<>();

    /**
     * The number of chunks in the {@link #unusedAlignedChunks} list.
     *
     * The value is not updated atomically with respect to the {@link #unusedAlignedChunks list
     * head}, but this is OK because we only need the number of chunks for policy code (to avoid
     * running down the list and counting the number of chunks).
     */
    private final AtomicUnsigned bytesInUnusedAlignedChunks = new AtomicUnsigned();

    /**
     * The time of the first allocation, as the basis for computing deltas.
     *
     * We do not care if we race on initializing the field, since the times will be similar in that
     * case anyway.
     */
    private long firstAllocationTime;

    @Platforms(Platform.HOSTED_ONLY.class)
    HeapChunkProvider() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getBytesInUnusedChunks() {
        return bytesInUnusedAlignedChunks.get();
    }

    @AlwaysInline("Remove all logging when noopLog is returned by this method")
    private static Log log() {
        return Log.noopLog();
    }

    private static final OutOfMemoryError ALIGNED_OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate an aligned heap chunk");

    private static final OutOfMemoryError UNALIGNED_OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate an unaligned heap chunk");

    /** Acquire a new AlignedHeapChunk, either from the free list or from the operating system. */
    AlignedHeader produceAlignedChunk() {
        UnsignedWord chunkSize = HeapPolicy.getAlignedHeapChunkSize();
        log().string("[HeapChunkProvider.produceAlignedChunk  chunk size: ").unsigned(chunkSize).newline();

        AlignedHeader result = popUnusedAlignedChunk();
        log().string("  unused chunk: ").hex(result).newline();

        if (result.isNull()) {
            /* Unused list was empty, need to allocate memory. */
            noteFirstAllocationTime();
            result = (AlignedHeader) CommittedMemoryProvider.get().allocate(chunkSize, HeapPolicy.getAlignedHeapChunkAlignment(), false);
            if (result.isNull()) {
                throw ALIGNED_OUT_OF_MEMORY_ERROR;
            }
            log().string("  new chunk: ").hex(result).newline();

            initializeChunk(result, chunkSize);
            resetAlignedHeapChunk(result);
        }
        assert HeapChunk.getTopOffset(result).equal(AlignedHeapChunk.getObjectsStartOffset());
        assert HeapChunk.getEndOffset(result).equal(chunkSize);

        if (HeapPolicy.getZapProducedHeapChunks()) {
            zap(result, HeapPolicy.getProducedHeapChunkZapWord());
        }

        HeapPolicy.increaseEdenUsedBytes(chunkSize);

        log().string("  result chunk: ").hex(result).string("  ]").newline();
        return result;
    }

    /** Release an AlignedHeapChunk, either to the free list or back to the operating system. */
    void consumeAlignedChunk(AlignedHeader chunk) {
        log().string("[HeapChunkProvider.consumeAlignedChunk  chunk: ").hex(chunk).newline();

        if (keepAlignedChunk()) {
            cleanAlignedChunk(chunk);
            pushUnusedAlignedChunk(chunk);
        } else {
            log().string("  release memory to the OS").newline();
            freeAlignedChunk(chunk);
        }
        log().string("  ]").newline();
    }

    private static boolean keepAlignedChunk() {
        UnsignedWord minimumHeapSize = HeapPolicy.getMinimumHeapSize();
        UnsignedWord committedBytes = HeapImpl.getHeapImpl().getCommittedBytes();
        return committedBytes.belowThan(minimumHeapSize);
    }

    private static void cleanAlignedChunk(AlignedHeader alignedChunk) {
        resetAlignedHeapChunk(alignedChunk);
        if (HeapPolicy.getZapConsumedHeapChunks()) {
            zap(alignedChunk, HeapPolicy.getConsumedHeapChunkZapWord());
        }
    }

    /**
     * Push a chunk to the global linked list of unused chunks.
     * <p>
     * This method is <em>not</em> atomic. It only runs when the VMThreads.THREAD_MUTEX is held (or
     * the virtual machine is single-threaded). However it must not be allowed to compete with pops
     * from the global free-list, because it might cause them an ABA problem. Pushing is only used
     * during garbage collection, so making popping uninterruptible prevents simultaneous pushing
     * and popping.
     *
     * Note the asymmetry with {@link #popUnusedAlignedChunk()}, which does not use a global free
     * list.
     */
    private void pushUnusedAlignedChunk(AlignedHeader chunk) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            VMThreads.guaranteeOwnsThreadMutex("Should hold the lock when pushing to the global list.");
        }
        log().string("  old list top: ").hex(unusedAlignedChunks.get()).string("  list bytes ").signed(bytesInUnusedAlignedChunks.get()).newline();

        HeapChunk.setNext(chunk, unusedAlignedChunks.get());
        unusedAlignedChunks.set(chunk);
        bytesInUnusedAlignedChunks.addAndGet(HeapPolicy.getAlignedHeapChunkSize());

        log().string("  new list top: ").hex(unusedAlignedChunks.get()).string("  list bytes ").signed(bytesInUnusedAlignedChunks.get()).newline();
    }

    /**
     * Pop a chunk from the global linked list of unused chunks. Returns {@code null} if the list is
     * empty.
     * <p>
     * This method uses compareAndSet to protect itself from races with competing pop operations,
     * but it is <em>not</em> safe with respect to competing pushes. Since pushes can happen during
     * garbage collections, I avoid the ABA problem by making the kernel of this method
     * uninterruptible so it can not be interrupted by a safepoint.
     */
    private AlignedHeader popUnusedAlignedChunk() {
        log().string("  old list top: ").hex(unusedAlignedChunks.get()).string("  list bytes ").signed(bytesInUnusedAlignedChunks.get()).newline();

        AlignedHeader result = popUnusedAlignedChunkUninterruptibly();
        if (result.isNull()) {
            return WordFactory.nullPointer();
        } else {
            bytesInUnusedAlignedChunks.subtractAndGet(HeapPolicy.getAlignedHeapChunkSize());
            log().string("  new list top: ").hex(unusedAlignedChunks.get()).string("  list bytes ").signed(bytesInUnusedAlignedChunks.get()).newline();
            return result;
        }
    }

    @Uninterruptible(reason = "Must not be interrupted by competing pushes.")
    private AlignedHeader popUnusedAlignedChunkUninterruptibly() {
        while (true) {
            AlignedHeader result = unusedAlignedChunks.get();
            if (result.isNull()) {
                return WordFactory.nullPointer();
            } else {
                AlignedHeader next = HeapChunk.getNext(result);
                if (unusedAlignedChunks.compareAndSet(result, next)) {
                    HeapChunk.setNext(result, WordFactory.nullPointer());
                    return result;
                }
            }
        }
    }

    /** Acquire an UnalignedHeapChunk from the operating system. */
    UnalignedHeader produceUnalignedChunk(UnsignedWord objectSize) {
        UnsignedWord chunkSize = UnalignedHeapChunk.getChunkSizeForObject(objectSize);
        log().string("[HeapChunkProvider.produceUnalignedChunk  objectSize: ").unsigned(objectSize).string("  chunkSize: ").hex(chunkSize).newline();

        noteFirstAllocationTime();
        UnalignedHeader result = (UnalignedHeader) CommittedMemoryProvider.get().allocate(chunkSize, CommittedMemoryProvider.UNALIGNED, false);
        if (result.isNull()) {
            throw UNALIGNED_OUT_OF_MEMORY_ERROR;
        }

        initializeChunk(result, chunkSize);
        resetUnalignedChunk(result);
        assert objectSize.belowOrEqual(HeapChunk.availableObjectMemory(result)) : "UnalignedHeapChunk insufficient for requested object";

        if (HeapPolicy.getZapProducedHeapChunks()) {
            zap(result, HeapPolicy.getProducedHeapChunkZapWord());
        }

        HeapPolicy.increaseEdenUsedBytes(chunkSize);

        log().string("  returns ").hex(result).string("  ]").newline();
        return result;
    }

    /**
     * Release an UnalignedHeapChunk back to the operating system. They are never recycled to a free
     * list.
     */
    static void consumeUnalignedChunk(UnalignedHeader chunk) {
        UnsignedWord chunkSize = unalignedChunkSize(chunk);
        log().string("[HeapChunkProvider.consumeUnalignedChunk  chunk: ").hex(chunk).string("  chunkSize: ").hex(chunkSize).newline();

        freeUnalignedChunk(chunk);

        log().string(" ]").newline();
    }

    /** Initialize the immutable state of a chunk. */
    private static void initializeChunk(Header<?> that, UnsignedWord chunkSize) {
        HeapChunk.setEndOffset(that, chunkSize);
    }

    /** Reset the mutable state of a chunk. */
    private static void resetChunkHeader(Header<?> chunk, Pointer objectsStart) {
        HeapChunk.setTopPointer(chunk, objectsStart);
        HeapChunk.setSpace(chunk, null);
        HeapChunk.setNext(chunk, WordFactory.nullPointer());
        HeapChunk.setPrevious(chunk, WordFactory.nullPointer());
    }

    private static void resetAlignedHeapChunk(AlignedHeader chunk) {
        resetChunkHeader(chunk, AlignedHeapChunk.getObjectsStart(chunk));

        CardTable.cleanTableToPointer(AlignedHeapChunk.getCardTableStart(chunk), AlignedHeapChunk.getCardTableLimit(chunk));
        FirstObjectTable.initializeTableToLimit(AlignedHeapChunk.getFirstObjectTableStart(chunk), AlignedHeapChunk.getFirstObjectTableLimit(chunk));
    }

    private static void resetUnalignedChunk(UnalignedHeader result) {
        resetChunkHeader(result, UnalignedHeapChunk.getObjectStart(result));

        CardTable.cleanTableToPointer(UnalignedHeapChunk.getCardTableStart(result), UnalignedHeapChunk.getCardTableLimit(result));
    }

    private static void zap(Header<?> chunk, WordBase value) {
        Pointer start = HeapChunk.getTopPointer(chunk);
        Pointer limit = HeapChunk.getEndPointer(chunk);
        log().string("  zap chunk: ").hex(chunk).string("  start: ").hex(start).string("  limit: ").hex(limit).string("  value: ").hex(value).newline();
        for (Pointer p = start; p.belowThan(limit); p = p.add(FrameAccess.wordSize())) {
            p.writeWord(0, value);
        }
    }

    Log report(Log log, boolean traceHeapChunks) {
        log.string("[Unused:").indent(true);
        log.string("aligned: ").signed(bytesInUnusedAlignedChunks.get())
                        .string("/")
                        .signed(bytesInUnusedAlignedChunks.get().unsignedDivide(HeapPolicy.getAlignedHeapChunkSize()));
        if (traceHeapChunks) {
            if (unusedAlignedChunks.get().isNonNull()) {
                log.newline().string("aligned chunks:").redent(true);
                for (AlignedHeapChunk.AlignedHeader aChunk = unusedAlignedChunks.get(); aChunk.isNonNull(); aChunk = HeapChunk.getNext(aChunk)) {
                    log.newline().hex(aChunk).string(" (").hex(AlignedHeapChunk.getObjectsStart(aChunk)).string("-").hex(HeapChunk.getTopPointer(aChunk)).string(")");
                }
                log.redent(false);
            }
        }
        log.redent(false).string("]");
        return log;
    }

    boolean walkHeapChunks(MemoryWalker.Visitor visitor) {
        boolean continueVisiting = true;
        MemoryWalker.HeapChunkAccess<AlignedHeapChunk.AlignedHeader> access = AlignedHeapChunk.getMemoryWalkerAccess();
        for (AlignedHeapChunk.AlignedHeader aChunk = unusedAlignedChunks.get(); continueVisiting && aChunk.isNonNull(); aChunk = HeapChunk.getNext(aChunk)) {
            continueVisiting = visitor.visitHeapChunk(aChunk, access);
        }
        return continueVisiting;
    }

    private void noteFirstAllocationTime() {
        if (firstAllocationTime == 0L) {
            firstAllocationTime = System.nanoTime();
        }
    }

    long getFirstAllocationTime() {
        return firstAllocationTime;
    }

    boolean slowlyFindPointer(Pointer p) {
        for (AlignedHeader chunk = unusedAlignedChunks.get(); chunk.isNonNull(); chunk = HeapChunk.getNext(chunk)) {
            Pointer chunkPtr = HeapChunk.asPointer(chunk);
            if (p.aboveOrEqual(chunkPtr) && p.belowThan(chunkPtr.add(HeapPolicy.getAlignedHeapChunkSize()))) {
                return true;
            }
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        freeAlignedChunkList(unusedAlignedChunks.get());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void freeAlignedChunkList(AlignedHeader first) {
        for (AlignedHeader chunk = first; chunk.isNonNull();) {
            AlignedHeader next = HeapChunk.getNext(chunk);
            freeAlignedChunk(chunk);
            chunk = next;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void freeUnalignedChunkList(UnalignedHeader first) {
        for (UnalignedHeader chunk = first; chunk.isNonNull();) {
            UnalignedHeader next = HeapChunk.getNext(chunk);
            freeUnalignedChunk(chunk);
            chunk = next;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void freeAlignedChunk(AlignedHeader chunk) {
        CommittedMemoryProvider.get().free(chunk, HeapPolicy.getAlignedHeapChunkSize(), HeapPolicy.getAlignedHeapChunkAlignment(), false);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void freeUnalignedChunk(UnalignedHeader chunk) {
        CommittedMemoryProvider.get().free(chunk, unalignedChunkSize(chunk), CommittedMemoryProvider.UNALIGNED, false);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord unalignedChunkSize(UnalignedHeader chunk) {
        return HeapChunk.getEndOffset(chunk);
    }
}
