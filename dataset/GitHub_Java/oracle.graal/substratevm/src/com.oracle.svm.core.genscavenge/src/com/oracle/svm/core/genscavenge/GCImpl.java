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

//Checkstyle: stop

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readReturnAddress;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;
import com.sun.management.GcInfo;

import sun.management.Util;

//Checkstyle: resume

/**
 * Garbage collector (incremental or complete) for {@link HeapImpl}.
 */
public class GCImpl implements GC {
    private final RememberedSetConstructor rememberedSetConstructor = new RememberedSetConstructor();
    private final GreyToBlackObjRefVisitor greyToBlackObjRefVisitor = new GreyToBlackObjRefVisitor();
    private final GreyToBlackObjectVisitor greyToBlackObjectVisitor = new GreyToBlackObjectVisitor(greyToBlackObjRefVisitor);
    private final CollectionPolicy collectOnlyCompletelyPolicy = new CollectionPolicy.OnlyCompletely();

    private final Accounting accounting = new Accounting();
    private final CollectionVMOperation collectOperation = new CollectionVMOperation();

    private final OutOfMemoryError oldGenerationSizeExceeded = new OutOfMemoryError("Garbage-collected heap size exceeded.");

    private final NoAllocationVerifier noAllocationVerifier = NoAllocationVerifier.factory("GCImpl.GCImpl()", false);

    private final GarbageCollectorManagementFactory gcManagementFactory = new GarbageCollectorManagementFactory();

    private final ThreadLocalMTWalker threadLocalsWalker = createThreadLocalsWalker();
    private final RuntimeCodeCacheWalker runtimeCodeCacheWalker = new RuntimeCodeCacheWalker(greyToBlackObjRefVisitor);
    private final RuntimeCodeCacheCleaner runtimeCodeCacheCleaner = new RuntimeCodeCacheCleaner();

    private CollectionPolicy policy;
    private boolean completeCollection = false;
    private UnsignedWord sizeBefore = WordFactory.zero();

    @Platforms(Platform.HOSTED_ONLY.class)
    protected GCImpl(FeatureAccess access) {
        this.policy = CollectionPolicy.getInitialPolicy(access);
        RuntimeSupport.getRuntimeSupport().addShutdownHook(this::printGCSummary);
    }

    private static ThreadLocalMTWalker createThreadLocalsWalker() {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            return new ThreadLocalMTWalker();
        }
        return null;
    }

    @Override
    public void collect(GCCause cause) {
        final UnsignedWord requestingEpoch = possibleCollectionPrologue();
        collectWithoutAllocating(cause);
        possibleCollectionEpilogue(requestingEpoch);
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of garbage collection.")
    void collectWithoutAllocating(GCCause cause) {
        int size = SizeOf.get(CollectionVMOperationData.class);
        CollectionVMOperationData data = StackValue.get(size);
        MemoryUtil.fillToMemoryAtomic((Pointer) data, WordFactory.unsigned(size), (byte) 0);
        data.setNativeVMOperation(collectOperation);
        data.setCauseId(cause.getId());
        data.setRequestingEpoch(getCollectionEpoch());
        collectOperation.enqueue(data);
        if (data.getOutOfMemory()) {
            throw oldGenerationSizeExceeded;
        }
    }

    /** The body of the VMOperation to do the collection. */
    private boolean collectOperation(GCCause cause, UnsignedWord requestingEpoch) {
        final Log trace = Log.noopLog().string("[GCImpl.collectOperation:").newline()
                        .string("  epoch: ").unsigned(getCollectionEpoch())
                        .string("  cause: ").string(cause.getName())
                        .string("  requestingEpoch: ").unsigned(requestingEpoch)
                        .newline();
        assert VMOperation.isGCInProgress() : "Collection should be a VMOperation.";
        assert getCollectionEpoch().equal(requestingEpoch);

        mutatorTimer.close();
        startCollectionOrExit();

        resetTimers();
        incrementCollectionEpoch();

        /* Flush chunks from thread-local lists to global lists. */
        ThreadLocalAllocation.disableThreadLocalAllocation();

        printGCBefore(cause.getName());
        boolean outOfMemory = collectImpl(cause.getName());
        HeapPolicy.youngUsedBytes.set(getAccounting().getYoungChunkBytesAfter());
        printGCAfter(cause.getName());

        finishCollection();
        mutatorTimer.open();

        trace.string("]").newline();
        return outOfMemory;
    }

    @SuppressWarnings("try")
    private boolean collectImpl(String cause) {
        final Log trace = Log.noopLog().string("[GCImpl.collectImpl:").newline().string("  epoch: ").unsigned(getCollectionEpoch()).string("  cause: ").string(cause).newline();
        boolean outOfMemory;

        precondition();

        trace.string("  Begin collection: ");
        try (NoAllocationVerifier nav = noAllocationVerifier.open()) {
            trace.string("  Verify before: ");
            try (Timer vbt = verifyBeforeTimer.open()) {
                HeapImpl.getHeapImpl().verifyBeforeGC(cause, getCollectionEpoch());
            }
            outOfMemory = doCollectImpl(getPolicy());
            if (outOfMemory) {
                // Avoid running out of memory with a full GC that reclaims softly reachable objects
                ReferenceObjectProcessing.setSoftReferencesAreWeak(true);
                try {
                    outOfMemory = doCollectImpl(collectOnlyCompletelyPolicy);
                } finally {
                    ReferenceObjectProcessing.setSoftReferencesAreWeak(false);
                }
            }
        }
        trace.string("  Verify after: ");
        try (Timer vat = verifyAfterTimer.open()) {
            HeapImpl.getHeapImpl().verifyAfterGC(cause, getCollectionEpoch());
        }

        postcondition();

        trace.string("]").newline();
        return outOfMemory;
    }

    @SuppressWarnings("try")
    private boolean doCollectImpl(CollectionPolicy appliedPolicy) {
        CommittedMemoryProvider.get().beforeGarbageCollection();

        getAccounting().beforeCollection();

        try (Timer ct = collectionTimer.open()) {
            if (appliedPolicy.collectIncrementally()) {
                scavenge(true);
            }
            completeCollection = appliedPolicy.collectCompletely();
            if (completeCollection) {
                scavenge(false);
            }
        }
        CommittedMemoryProvider.get().afterGarbageCollection(completeCollection);

        getAccounting().afterCollection(completeCollection, collectionTimer);
        UnsignedWord maxBytes = HeapPolicy.getMaximumHeapSize();
        UnsignedWord usedBytes = getChunkUsedBytesAfterCollection();
        boolean outOfMemory = usedBytes.aboveThan(maxBytes);

        ReferenceObjectProcessing.afterCollection(usedBytes, maxBytes);

        return outOfMemory;
    }

    private void printGCBefore(String cause) {
        final Log verboseGCLog = Log.log();
        final HeapImpl heap = HeapImpl.getHeapImpl();
        sizeBefore = ((SubstrateOptions.PrintGC.getValue() || HeapOptions.PrintHeapShape.getValue()) ? heap.getUsedChunkBytes() : WordFactory.zero());
        if (SubstrateOptions.VerboseGC.getValue() && getCollectionEpoch().equal(1)) {
            verboseGCLog.string("[Heap policy parameters: ").newline();
            verboseGCLog.string("  YoungGenerationSize: ").unsigned(HeapPolicy.getMaximumYoungGenerationSize()).newline();
            verboseGCLog.string("      MaximumHeapSize: ").unsigned(HeapPolicy.getMaximumHeapSize()).newline();
            verboseGCLog.string("      MinimumHeapSize: ").unsigned(HeapPolicy.getMinimumHeapSize()).newline();
            verboseGCLog.string("     AlignedChunkSize: ").unsigned(HeapPolicy.getAlignedHeapChunkSize()).newline();
            verboseGCLog.string("  LargeArrayThreshold: ").unsigned(HeapPolicy.getLargeArrayThreshold()).string("]").newline();
            if (HeapOptions.PrintHeapShape.getValue()) {
                HeapImpl.getHeapImpl().logImageHeapPartitionBoundaries(verboseGCLog).newline();
            }
        }
        if (SubstrateOptions.VerboseGC.getValue()) {
            verboseGCLog.string("[");
            verboseGCLog.string("[");
            final long startTime = System.nanoTime();
            if (HeapOptions.PrintGCTimeStamps.getValue()) {
                verboseGCLog.unsigned(TimeUtils.roundNanosToMillis(Timer.getTimeSinceFirstAllocation(startTime))).string(" msec: ");
            } else {
                verboseGCLog.unsigned(startTime);
            }
            verboseGCLog.string(" GC:").string(" before").string("  epoch: ").unsigned(getCollectionEpoch()).string("  cause: ").string(cause);
            if (HeapOptions.PrintHeapShape.getValue()) {
                heap.report(verboseGCLog);
            }
            verboseGCLog.string("]").newline();
        }
    }

    private void printGCAfter(String cause) {
        final Log verboseGCLog = Log.log();
        final HeapImpl heap = HeapImpl.getHeapImpl();
        if (SubstrateOptions.PrintGC.getValue() || SubstrateOptions.VerboseGC.getValue()) {
            if (SubstrateOptions.PrintGC.getValue()) {
                final Log printGCLog = Log.log();
                final UnsignedWord sizeAfter = heap.getUsedChunkBytes();
                printGCLog.string("[");
                if (HeapOptions.PrintGCTimeStamps.getValue()) {
                    final long finishNanos = collectionTimer.getFinish();
                    printGCLog.unsigned(TimeUtils.roundNanosToMillis(Timer.getTimeSinceFirstAllocation(finishNanos))).string(" msec: ");
                }
                printGCLog.string(completeCollection ? "Full GC" : "Incremental GC");
                printGCLog.string(" (").string(cause).string(") ");
                printGCLog.unsigned(sizeBefore.unsignedDivide(1024));
                printGCLog.string("K->");
                printGCLog.unsigned(sizeAfter.unsignedDivide(1024)).string("K, ");
                printGCLog.rational(collectionTimer.getCollectedNanos(), TimeUtils.nanosPerSecond, 7).string(" secs");
                printGCLog.string("]").newline();
            }
            if (SubstrateOptions.VerboseGC.getValue()) {
                verboseGCLog.string(" [");
                final long finishNanos = collectionTimer.getFinish();
                if (HeapOptions.PrintGCTimeStamps.getValue()) {
                    verboseGCLog.unsigned(TimeUtils.roundNanosToMillis(Timer.getTimeSinceFirstAllocation(finishNanos))).string(" msec: ");
                } else {
                    verboseGCLog.unsigned(finishNanos);
                }
                verboseGCLog.string(" GC:").string(" after ").string("  epoch: ").unsigned(getCollectionEpoch()).string("  cause: ").string(cause);
                verboseGCLog.string("  policy: ");
                getPolicy().nameToLog(verboseGCLog);
                verboseGCLog.string("  type: ").string(completeCollection ? "complete" : "incremental");
                if (HeapOptions.PrintHeapShape.getValue()) {
                    heap.report(verboseGCLog);
                }
                if (!HeapOptions.PrintGCTimes.getValue()) {
                    verboseGCLog.newline();
                    verboseGCLog.string("  collection time: ").unsigned(collectionTimer.getCollectedNanos()).string(" nanoSeconds");
                } else {
                    logGCTimers(verboseGCLog);
                }
                verboseGCLog.string("]");
                verboseGCLog.string("]").newline();
            }
        }
    }

    private static void precondition() {
        OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
        assert oldGen.getToSpace().isEmpty() : "oldGen.getToSpace() should be empty before a collection.";
    }

    private void postcondition() {
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final YoungGeneration youngGen = heap.getYoungGeneration();
        final OldGeneration oldGen = heap.getOldGeneration();
        verbosePostCondition();
        assert youngGen.getEden().isEmpty() : "youngGen.getEden() should be empty after a collection.";
        assert oldGen.getToSpace().isEmpty() : "oldGen.getToSpace() should be empty after a collection.";
    }

    private void verbosePostCondition() {
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final YoungGeneration youngGen = heap.getYoungGeneration();
        final OldGeneration oldGen = heap.getOldGeneration();
        /*
         * Note to self: I can get output similar to this *all the time* by running with
         * -R:+VerboseGC -R:+PrintHeapShape -R:+TraceHeapChunks
         */
        final boolean forceForTesting = false;
        if (runtimeAssertions() || forceForTesting) {
            final Log witness = Log.log();
            if ((!youngGen.getEden().isEmpty()) || forceForTesting) {
                witness.string("[GCImpl.postcondition: Eden space should be empty after a collection.").newline();
                /* Print raw fields before trying to walk the chunk lists. */
                witness.string("  These should all be 0:").newline();
                witness.string("    Eden space first AlignedChunk:   ").hex(youngGen.getEden().getFirstAlignedHeapChunk()).newline();
                witness.string("    Eden space last  AlignedChunk:   ").hex(youngGen.getEden().getLastAlignedHeapChunk()).newline();
                witness.string("    Eden space first UnalignedChunk: ").hex(youngGen.getEden().getFirstUnalignedHeapChunk()).newline();
                witness.string("    Eden space last  UnalignedChunk: ").hex(youngGen.getEden().getLastUnalignedHeapChunk()).newline();
                youngGen.getEden().report(witness, true).newline();
                witness.string("  verifying the heap:");
                heap.verifyAfterGC("because Eden space is not empty", getCollectionEpoch());
                witness.string("]").newline();
            }
            for (int i = 0; i < HeapPolicy.getMaxSurvivorSpaces(); i++) {
                if ((!youngGen.getSurvivorToSpaceAt(i).isEmpty()) || forceForTesting) {
                    witness.string("[GCImpl.postcondition: Survivor toSpace should be empty after a collection.").newline();
                    /* Print raw fields before trying to walk the chunk lists. */
                    witness.string("  These should all be 0:").newline();
                    witness.string("    Survivor space ").signed(i).string(" first AlignedChunk:   ").hex(youngGen.getSurvivorToSpaceAt(i).getFirstAlignedHeapChunk()).newline();
                    witness.string("    Survivor space ").signed(i).string(" last  AlignedChunk:   ").hex(youngGen.getSurvivorToSpaceAt(i).getLastAlignedHeapChunk()).newline();
                    witness.string("    Survivor space ").signed(i).string(" first UnalignedChunk: ").hex(youngGen.getSurvivorToSpaceAt(i).getFirstUnalignedHeapChunk()).newline();
                    witness.string("    Survivor space ").signed(i).string(" last  UnalignedChunk: ").hex(youngGen.getSurvivorToSpaceAt(i).getLastUnalignedHeapChunk()).newline();
                    youngGen.getSurvivorToSpaceAt(i).report(witness, true).newline();
                    witness.string("  verifying the heap:");
                    heap.verifyAfterGC("because Survivor toSpace is not empty", getCollectionEpoch());
                    witness.string("]").newline();
                }
            }
            if ((!oldGen.getToSpace().isEmpty()) || forceForTesting) {
                witness.string("[GCImpl.postcondition: oldGen toSpace should be empty after a collection.").newline();
                /* Print raw fields before trying to walk the chunk lists. */
                witness.string("  These should all be 0:").newline();
                witness.string("    oldGen toSpace first AlignedChunk:   ").hex(oldGen.getToSpace().getFirstAlignedHeapChunk()).newline();
                witness.string("    oldGen toSpace last  AlignedChunk:   ").hex(oldGen.getToSpace().getLastAlignedHeapChunk()).newline();
                witness.string("    oldGen.toSpace first UnalignedChunk: ").hex(oldGen.getToSpace().getFirstUnalignedHeapChunk()).newline();
                witness.string("    oldGen.toSpace last  UnalignedChunk: ").hex(oldGen.getToSpace().getLastUnalignedHeapChunk()).newline();
                oldGen.getToSpace().report(witness, true).newline();
                oldGen.getFromSpace().report(witness, true).newline();
                witness.string("  verifying the heap:");
                heap.verifyAfterGC("because oldGen toSpace is not empty", getCollectionEpoch());
                witness.string("]").newline();
            }
        }
    }

    private UnsignedWord getChunkUsedBytesAfterCollection() {
        /* The old generation and the survivor spaces have objects */
        UnsignedWord survivorUsedBytes = HeapImpl.getHeapImpl().getYoungGeneration().getSurvivorChunkUsedBytes();
        return getAccounting().getOldGenerationAfterChunkBytes().add(survivorUsedBytes);
    }

    @Fold
    static boolean runtimeAssertions() {
        return SubstrateOptions.getRuntimeAssertionsForClass(GCImpl.class.getName());
    }

    @Fold
    public static GCImpl getGCImpl() {
        final GCImpl gcImpl = HeapImpl.getHeapImpl().getGCImpl();
        assert gcImpl != null;
        return gcImpl;
    }

    @Override
    public void collectCompletely(final GCCause cause) {
        final CollectionPolicy oldPolicy = getPolicy();
        try {
            setPolicy(collectOnlyCompletelyPolicy);
            collect(cause);
        } finally {
            setPolicy(oldPolicy);
        }
    }

    boolean isCompleteCollection() {
        return completeCollection;
    }

    /** Scavenge, either from dirty roots or from all roots, and process discovered references. */
    @SuppressWarnings("try")
    private void scavenge(boolean fromDirtyRoots) {
        try (GreyToBlackObjRefVisitor.Counters gtborv = greyToBlackObjRefVisitor.openCounters()) {
            final Log trace = Log.noopLog().string("[GCImpl.scavenge:").string("  fromDirtyRoots: ").bool(fromDirtyRoots).newline();
            try (Timer rst = rootScanTimer.open()) {
                trace.string("  Cheney scan: ");
                if (fromDirtyRoots) {
                    cheneyScanFromDirtyRoots();
                } else {
                    cheneyScanFromRoots();
                }
            }
            trace.string("  Discovered references: ");
            try (Timer drt = referenceObjectsTimer.open()) {
                Reference<?> newlyPendingList = ReferenceObjectProcessing.processRememberedReferences();
                HeapImpl.getHeapImpl().addToReferencePendingList(newlyPendingList);
            }
            trace.string("  Release spaces: ");
            try (Timer rst = releaseSpacesTimer.open()) {
                releaseSpaces();
            }
            trace.string("  Swap spaces: ");
            swapSpaces();
            trace.string("]").newline();
        }
    }

    /**
     * Visit all the memory that is reserved for runtime compiled code. References from the runtime
     * compiled code to the Java heap must be consider as either strong or weak references,
     * depending on whether the code is currently on the execution stack.
     */
    @SuppressWarnings("try")
    private void walkRuntimeCodeCache() {
        try (Timer wrm = walkRuntimeCodeCacheTimer.open()) {
            RuntimeCodeInfoMemory.singleton().walkRuntimeMethods(runtimeCodeCacheWalker);
        }
    }

    @SuppressWarnings("try")
    private void cleanRuntimeCodeCache() {
        try (Timer wrm = cleanRuntimeCodeCacheTimer.open()) {
            RuntimeCodeInfoMemory.singleton().walkRuntimeMethods(runtimeCodeCacheCleaner);
        }
    }

    @SuppressWarnings("try")
    private void cheneyScanFromRoots() {
        final Log trace = Log.noopLog().string("[GCImpl.cheneyScanFromRoots:").newline();

        try (Timer csfrt = cheneyScanFromRootsTimer.open()) {
            /* Take a snapshot of the heap so that I can visit all the promoted Objects. */
            /*
             * Debugging tip: I could move the taking of the snapshot and the scanning of grey
             * Objects into each of the blackening methods, or even put them around individual
             * Object reference visits.
             */
            prepareForPromotion(false);

            /*
             * Make sure all chunks with pinned objects are in toSpace, and any formerly pinned
             * objects are in fromSpace.
             */
            promoteIndividualPinnedObjects();

            /*
             * Stack references are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenStackRoots();

            /* Custom memory regions which contain object references. */
            walkThreadLocals();

            /*
             * Native image Objects are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenImageHeapRoots();

            /* Visit all the Objects promoted since the snapshot. */
            scanGreyObjects(false);

            if (DeoptimizationSupport.enabled()) {
                /* Visit the runtime compiled code, now that we know all the reachable objects. */
                walkRuntimeCodeCache();

                /* Visit all objects that became reachable because of the compiled code. */
                scanGreyObjects(false);

                /* Clean the code cache, now that all live objects were visited. */
                cleanRuntimeCodeCache();
            }

            greyToBlackObjectVisitor.reset();
        }

        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void cheneyScanFromDirtyRoots() {
        final Log trace = Log.noopLog().string("[GCImpl.cheneyScanFromDirtyRoots:").newline();

        try (Timer csfdrt = cheneyScanFromDirtyRootsTimer.open()) {
            /*
             * Move all the chunks in fromSpace to toSpace. That does not make those chunks grey, so
             * I have to use the dirty cards marks to blacken them, but that's what card marks are
             * for.
             */
            OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
            oldGen.emptyFromSpaceIntoToSpace();

            /* Take a snapshot of the heap so that I can visit all the promoted Objects. */
            /*
             * Debugging tip: I could move the taking of the snapshot and the scanning of grey
             * Objects into each of the blackening methods, or even put them around individual
             * Object reference visits.
             */
            prepareForPromotion(true);

            /*
             * Make sure any released objects are in toSpace (because this is an incremental
             * collection). I do this before blackening any roots to make sure the chunks with
             * pinned objects are moved entirely, as opposed to promoting the objects individually
             * by roots. This makes the objects in those chunks grey.
             */
            promoteIndividualPinnedObjects();

            /*
             * Blacken Objects that are dirty roots. There are dirty cards in ToSpace. Do this early
             * so I don't have to walk the cards of individually promoted objects, which will be
             * visited by the grey object scanner.
             */
            blackenDirtyCardRoots();

            /*
             * Stack references are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenStackRoots();

            /* Custom memory regions which contain object references. */
            walkThreadLocals();

            /*
             * Native image Objects are grey at the beginning of a collection, so I need to blacken
             * them.
             */
            blackenImageHeapRoots();

            /* Visit all the Objects promoted since the snapshot, transitively. */
            scanGreyObjects(true);

            if (DeoptimizationSupport.enabled()) {
                /* Visit the runtime compiled code, now that we know all the reachable objects. */
                walkRuntimeCodeCache();

                /* Visit all objects that became reachable because of the compiled code. */
                scanGreyObjects(true);

                /* Clean the code cache, now that all live objects were visited. */
                cleanRuntimeCodeCache();
            }

            greyToBlackObjectVisitor.reset();
        }

        trace.string("]").newline();
    }

    @SuppressWarnings("try")
    private void promoteIndividualPinnedObjects() {
        final Log trace = Log.noopLog().string("[GCImpl.promoteIndividualPinnedObjects:").newline();
        try (Timer ppot = promotePinnedObjectsTimer.open()) {
            PinnedObjectImpl rest = PinnedObjectImpl.claimPinnedObjectList();
            while (rest != null) {
                final PinnedObjectImpl first = rest;
                final PinnedObjectImpl next = first.getNext();
                if (first.isOpen()) {
                    /*
                     * Promote the chunk with the object, and put this PinnedObject on the new list
                     * (which reverses the list).
                     */
                    promotePinnedObject(first);
                    PinnedObjectImpl.pushPinnedObject(first);
                }
                rest = next;
            }
        }
        trace.string("]").newline();
    }

    @NeverInline("Starting a stack walk in the caller frame. " +
                    "Note that we could start the stack frame also further down the stack, because GC stack frames must not access any objects that are processed by the GC. " +
                    "But we don't store stack frame information for the first frame we would need to process.")
    @Uninterruptible(reason = "Required by called JavaStackWalker methods. We are at a safepoint during GC, so it does not change anything for this method.", calleeMustBe = false)
    @SuppressWarnings("try")
    private void blackenStackRoots() {
        final Log trace = Log.noopLog().string("[GCImpl.blackenStackRoots:").newline();
        try (Timer bsr = blackenStackRootsTimer.open()) {
            Pointer sp = readCallerStackPointer();
            trace.string("[blackenStackRoots:").string("  sp: ").hex(sp);
            CodePointer ip = readReturnAddress();
            trace.string("  ip: ").hex(ip).newline();

            JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
            JavaStackWalker.initWalk(walk, sp, ip);
            walkStack(walk);

            if (SubstrateOptions.MultiThreaded.getValue()) {
                /*
                 * Scan the stacks of all the threads. Other threads will be blocked at a safepoint
                 * (or in native code) so they will each have a JavaFrameAnchor in their VMThread.
                 */
                for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                    if (vmThread == CurrentIsolate.getCurrentThread()) {
                        /*
                         * The current thread is already scanned by code above, so we do not have to
                         * do anything for it here. It might have a JavaFrameAnchor from earlier
                         * Java-to-C transitions, but certainly not at the top of the stack since it
                         * is running this code, so just this scan would be incomplete.
                         */
                        continue;
                    }
                    if (JavaStackWalker.initWalk(walk, vmThread)) {
                        walkStack(walk);
                    }
                    trace.newline();
                }
            }
            trace.string("]").newline();
        }
        trace.string("]").newline();
    }

    /**
     * This method inlines {@link JavaStackWalker#continueWalk(JavaStackWalk, CodeInfo)} and
     * {@link CodeInfoTable#visitObjectReferences}. This avoids looking up the
     * {@link SimpleCodeInfoQueryResult} twice per frame, and also ensures that there are no virtual
     * calls to a stack frame visitor.
     */
    @Uninterruptible(reason = "Required by called JavaStackWalker methods. We are at a safepoint during GC, so it does not change anything for this method.", calleeMustBe = false)
    private void walkStack(JavaStackWalk walk) {
        assert VMOperation.isGCInProgress() : "This methods accesses a CodeInfo without a tether";

        while (true) {
            SimpleCodeInfoQueryResult queryResult = StackValue.get(SimpleCodeInfoQueryResult.class);
            Pointer sp = walk.getSP();
            CodePointer ip = walk.getPossiblyStaleIP();

            /* We are during a GC, so tethering of the CodeInfo is not necessary. */
            CodeInfo codeInfo = CodeInfoAccess.convert(walk.getIPCodeInfo());
            DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
            if (deoptFrame == null) {
                if (codeInfo.isNull()) {
                    throw JavaStackWalker.reportUnknownFrameEncountered(sp, ip, deoptFrame);
                }

                CodeInfoAccess.lookupCodeInfo(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip), queryResult);
                assert Deoptimizer.checkDeoptimized(sp) == null : "We are at a safepoint, so no deoptimization can have happened even though looking up the code info is not uninterruptible";

                NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getReferenceMapEncoding(codeInfo);
                long referenceMapIndex = queryResult.getReferenceMapIndex();
                if (referenceMapIndex == CodeInfoQueryResult.NO_REFERENCE_MAP) {
                    throw CodeInfoTable.reportNoReferenceMap(sp, ip, codeInfo);
                }
                CodeReferenceMapDecoder.walkOffsetsFromPointer(sp, referenceMapEncoding, referenceMapIndex, greyToBlackObjRefVisitor);
            } else {
                /*
                 * This is a deoptimized frame. The DeoptimizedFrame object is stored in the frame,
                 * but it is pinned so we do not need to visit references of the frame.
                 */
            }

            if (DeoptimizationSupport.enabled() && codeInfo != CodeInfoTable.getImageCodeInfo()) {
                /*
                 * For runtime-compiled code that is currently on the stack, we need to treat all
                 * the references to Java heap objects as strong references. It is important that we
                 * really walk *all* those references here. Otherwise, RuntimeCodeCacheWalker might
                 * decide to invalidate too much code, depending on the order in which the CodeInfo
                 * objects are visited.
                 */
                RuntimeCodeInfoAccess.walkStrongReferences(codeInfo, greyToBlackObjRefVisitor);
                RuntimeCodeInfoAccess.walkWeakReferences(codeInfo, greyToBlackObjRefVisitor);
            }

            if (!JavaStackWalker.continueWalk(walk, queryResult, deoptFrame)) {
                /* No more caller frame found. */
                return;
            }
        }
    }

    @SuppressWarnings("try")
    private void walkThreadLocals() {
        final Log trace = Log.noopLog().string("[walkRegisteredObjectReferences").string(":").newline();
        if (threadLocalsWalker != null) {
            try (Timer wrm = walkThreadLocalsTimer.open()) {
                trace.string("[ThreadLocalsWalker:").newline();
                threadLocalsWalker.walk(greyToBlackObjRefVisitor);
                trace.string("]").newline();
            }
        }
        trace.string("]").newline();
    }

    private void blackenImageHeapRoots() {
        Log trace = Log.noopLog().string("[blackenImageHeapRoots:").newline();
        HeapImpl.getHeapImpl().walkNativeImageHeapRegions(blackenImageHeapRootsVisitor);
        trace.string("]").newline();
    }

    private final BlackenImageHeapRootsVisitor blackenImageHeapRootsVisitor = new BlackenImageHeapRootsVisitor();

    private class BlackenImageHeapRootsVisitor implements MemoryWalker.Visitor {
        @Override
        @SuppressWarnings("try")
        public <T> boolean visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            if (access.containsReferences(region) && access.isWritable(region)) {
                try (Timer timer = blackenImageHeapRootsTimer.open()) {
                    ImageHeapInfo imageHeapInfo = HeapImpl.getImageHeapInfo();
                    Pointer cur = Word.objectToUntrackedPointer(imageHeapInfo.firstWritableReferenceObject);
                    final Pointer last = Word.objectToUntrackedPointer(imageHeapInfo.lastWritableReferenceObject);
                    while (cur.belowOrEqual(last)) {
                        Object obj = cur.toObject();
                        if (obj != null) {
                            greyToBlackObjectVisitor.visitObjectInline(obj);
                        }
                        cur = LayoutEncoding.getObjectEnd(obj);
                    }
                }
            }
            return true;
        }

        @Override
        public <T extends PointerBase> boolean visitHeapChunk(T heapChunk, MemoryWalker.HeapChunkAccess<T> access) {
            throw VMError.shouldNotReachHere();
        }

        @Override
        public <T extends CodeInfo> boolean visitCode(T codeInfo, MemoryWalker.CodeAccess<T> access) {
            throw VMError.shouldNotReachHere();
        }
    }

    @SuppressWarnings("try")
    private void blackenDirtyCardRoots() {
        final Log trace = Log.noopLog().string("[GCImpl.blackenDirtyCardRoots:").newline();
        try (Timer bdcrt = blackenDirtyCardRootsTimer.open()) {
            /*
             * Walk To-Space looking for dirty cards, and within those for old-to-young pointers.
             * Promote any referenced young objects.
             */
            final HeapImpl heap = HeapImpl.getHeapImpl();
            heap.getOldGeneration().walkDirtyObjects(greyToBlackObjectVisitor, true);
        }
        trace.string("]").newline();
    }

    private static void prepareForPromotion(boolean isIncremental) {
        final Log trace = Log.noopLog().string("[GCImpl.prepareForPromotion:").newline();

        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        oldGen.prepareForPromotion();
        if (isIncremental) {
            heap.getYoungGeneration().prepareForPromotion();
        }
        trace.string("]").newline();

    }

    @SuppressWarnings("try")
    private void scanGreyObjects(boolean isIncremental) {
        final Log trace = Log.noopLog().string("[GCImpl.scanGreyObjects").newline();
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        try (Timer sgot = scanGreyObjectsTimer.open()) {
            if (isIncremental) {
                scanGreyObjectsLoop();
            } else {
                oldGen.scanGreyObjects();
            }
        }
        trace.string("]").newline();
    }

    private static void scanGreyObjectsLoop() {
        final Log trace = Log.noopLog().string("[GCImpl.scanGreyObjectsLoop").newline();
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final YoungGeneration youngGen = heap.getYoungGeneration();
        final OldGeneration oldGen = heap.getOldGeneration();
        boolean hasGrey = true;
        while (hasGrey) {
            hasGrey = youngGen.scanGreyObjects();
            hasGrey |= oldGen.scanGreyObjects();
        }
        trace.string("]").newline();
    }

    private static void promotePinnedObject(PinnedObjectImpl pinned) {
        final Log trace = Log.noopLog().string("[GCImpl.promotePinnedObject").string("  pinned: ").object(pinned);
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        /* Find the chunk the object is in, and if necessary, move it to To space. */
        final Object referent = pinned.getObject();
        if (referent != null && !heap.isInImageHeap(referent)) {
            trace.string("  referent: ").object(referent);
            /*
             * The referent doesn't move, so I can ignore the result of the promotion because I
             * don't have to update any pointers to it.
             */
            oldGen.promoteObjectChunk(referent);
        }
        trace.string("]").newline();
    }

    private static void swapSpaces() {
        final Log trace = Log.noopLog().string("[GCImpl.swapSpaces:");
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        heap.getYoungGeneration().swapSpaces();
        oldGen.swapSpaces();
        trace.string("]").newline();
    }

    private void releaseSpaces() {
        final Log trace = Log.noopLog().string("[GCImpl.releaseSpaces:");
        final HeapImpl heap = HeapImpl.getHeapImpl();
        heap.getYoungGeneration().releaseSpaces();
        if (completeCollection) {
            heap.getOldGeneration().releaseSpaces();
        }
        trace.string("]").newline();
    }

    /* Collection in progress methods. */

    private boolean collectionInProgress;

    boolean isCollectionInProgress() {
        return collectionInProgress;
    }

    private void startCollectionOrExit() {
        CollectionInProgressError.exitIf(collectionInProgress);
        collectionInProgress = true;
    }

    private void finishCollection() {
        assert collectionInProgress;
        collectionInProgress = false;
    }

    UnsignedWord possibleCollectionPrologue() {
        return getCollectionEpoch();
    }

    /**
     * Do whatever is necessary if a collection occurred since the a call to
     * {@link #possibleCollectionPrologue()}. Note that this method may get called by several
     * threads for the same collection.
     */
    @SuppressWarnings("try")
    void possibleCollectionEpilogue(UnsignedWord requestingEpoch) {
        if (requestingEpoch.aboveOrEqual(getCollectionEpoch())) {
            /* No GC happened, so do not run any epilogue. */
            return;

        } else if (VMOperation.isInProgress()) {
            /*
             * We are inside a VMOperation where we are not allowed to do certain things, e.g.,
             * perform a synchronization (because it can deadlock when a lock is held outside the
             * VMOperation).
             *
             * Note that the GC operation we are running the epilogue for is no longer in progress,
             * otherwise this check would always return.
             */
            return;

        } else if (!JavaThreads.currentJavaThreadInitialized()) {
            /*
             * Too early in the attach sequence of a thread to do anything useful, e.g., perform a
             * synchronization. Probably the allocation slow path for the first allocation of that
             * thread caused this epilogue.
             */
            return;
        }

        Timer refsTimer = new Timer("Enqueuing pending references and invoking internal cleaners");
        try (Timer timer = refsTimer.open()) {
            ReferenceHandler.maybeProcessCurrentlyPending();
        }
        if (SubstrateOptions.VerboseGC.getValue() && HeapOptions.PrintGCTimes.getValue()) {
            logOneTimer(Log.log(), "[GC epilogue reference processing: ", refsTimer);
            Log.log().string("]");
        }
    }

    /* Collection counting. */

    /** A counter for collections. */
    private UnsignedWord collectionEpoch = WordFactory.zero();

    public UnsignedWord getCollectionEpoch() {
        return collectionEpoch;
    }

    private void incrementCollectionEpoch() {
        collectionEpoch = collectionEpoch.add(1);
    }

    /*
     * Field access methods.
     */

    Accounting getAccounting() {
        return accounting;
    }

    private CollectionPolicy getPolicy() {
        return policy;
    }

    private void setPolicy(final CollectionPolicy newPolicy) {
        policy = newPolicy;
    }

    GreyToBlackObjectVisitor getGreyToBlackObjectVisitor() {
        return greyToBlackObjectVisitor;
    }

    /*
     * Timers.
     */
    private final Timer blackenImageHeapRootsTimer = new Timer("blackenImageHeapRootsTimer");
    private final Timer blackenDirtyCardRootsTimer = new Timer("blackenDirtyCardRoots");
    private final Timer blackenStackRootsTimer = new Timer("blackenStackRoots");
    private final Timer cheneyScanFromRootsTimer = new Timer("cheneyScanFromRoots");
    private final Timer cheneyScanFromDirtyRootsTimer = new Timer("cheneyScanFromDirtyRoots");
    private final Timer collectionTimer = new Timer("collection");
    private final Timer referenceObjectsTimer = new Timer("referenceObjects");
    private final Timer promotePinnedObjectsTimer = new Timer("promotePinnedObjects");
    private final Timer rootScanTimer = new Timer("rootScan");
    private final Timer scanGreyObjectsTimer = new Timer("scanGreyObject");
    private final Timer releaseSpacesTimer = new Timer("releaseSpaces");
    private final Timer verifyAfterTimer = new Timer("verifyAfter");
    private final Timer verifyBeforeTimer = new Timer("verifyBefore");
    private final Timer walkThreadLocalsTimer = new Timer("walkThreadLocals");
    private final Timer walkRuntimeCodeCacheTimer = new Timer("walkRuntimeCodeCacheTimer");
    private final Timer cleanRuntimeCodeCacheTimer = new Timer("cleanRuntimeCodeCacheTimer");
    private final Timer watchersBeforeTimer = new Timer("watchersBefore");
    private final Timer watchersAfterTimer = new Timer("watchersAfter");
    private final Timer mutatorTimer = new Timer("Mutator");

    private void resetTimers() {
        final Log trace = Log.noopLog();
        trace.string("[GCImpl.resetTimers:");
        watchersBeforeTimer.reset();
        verifyBeforeTimer.reset();
        collectionTimer.reset();
        rootScanTimer.reset();
        cheneyScanFromRootsTimer.reset();
        cheneyScanFromDirtyRootsTimer.reset();
        promotePinnedObjectsTimer.reset();
        blackenStackRootsTimer.reset();
        walkThreadLocalsTimer.reset();
        walkRuntimeCodeCacheTimer.reset();
        cleanRuntimeCodeCacheTimer.reset();
        blackenImageHeapRootsTimer.reset();
        blackenDirtyCardRootsTimer.reset();
        scanGreyObjectsTimer.reset();
        referenceObjectsTimer.reset();
        releaseSpacesTimer.reset();
        verifyAfterTimer.reset();
        watchersAfterTimer.reset();
        /* The mutator timer is *not* reset here. */
        trace.string("]").newline();
    }

    private void logGCTimers(final Log log) {
        if (log.isEnabled()) {
            log.newline();
            log.string("  [GC nanoseconds:");
            logOneTimer(log, "    ", watchersBeforeTimer);
            logOneTimer(log, "    ", verifyBeforeTimer);
            logOneTimer(log, "    ", collectionTimer);
            logOneTimer(log, "      ", rootScanTimer);
            logOneTimer(log, "        ", cheneyScanFromRootsTimer);
            logOneTimer(log, "        ", cheneyScanFromDirtyRootsTimer);
            logOneTimer(log, "          ", promotePinnedObjectsTimer);
            logOneTimer(log, "          ", blackenStackRootsTimer);
            logOneTimer(log, "          ", walkThreadLocalsTimer);
            logOneTimer(log, "          ", walkRuntimeCodeCacheTimer);
            logOneTimer(log, "          ", cleanRuntimeCodeCacheTimer);
            logOneTimer(log, "          ", blackenImageHeapRootsTimer);
            logOneTimer(log, "          ", blackenDirtyCardRootsTimer);
            logOneTimer(log, "          ", scanGreyObjectsTimer);
            logOneTimer(log, "      ", referenceObjectsTimer);
            logOneTimer(log, "      ", releaseSpacesTimer);
            logOneTimer(log, "    ", verifyAfterTimer);
            logOneTimer(log, "    ", watchersAfterTimer);
            logGCLoad(log, "    ", "GCLoad", collectionTimer, mutatorTimer);
            log.string("]");
        }
    }

    private static void logOneTimer(final Log log, final String prefix, final Timer timer) {
        if (timer.getCollectedNanos() > 0) {
            log.newline().string(prefix).string(timer.getName()).string(": ").signed(timer.getCollectedNanos());
        }
    }

    /**
     * Log the "GC load" for this collection as the collection time divided by the sum of the
     * previous mutator interval plus the collection time. This method uses wall-time, and so does
     * not take in to account that the collector is single-threaded, while the mutator might be
     * multi-threaded.
     */
    private static void logGCLoad(Log log, String prefix, String label, Timer cTimer, Timer mTimer) {
        final long collectionNanos = cTimer.getLastIntervalNanos();
        final long mutatorNanos = mTimer.getLastIntervalNanos();
        final long intervalNanos = mutatorNanos + collectionNanos;
        final long intervalGCPercent = (((100 * collectionNanos) + (intervalNanos / 2)) / intervalNanos);
        log.newline().string(prefix).string(label).string(": ").signed(intervalGCPercent).string("%");
    }

    /**
     * Accounting for this collector. Times are in nanoseconds. ChunkBytes refer to bytes reserved
     * (but maybe not occupied). ObjectBytes refer to bytes occupied by objects.
     */
    static class Accounting {
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

        UnsignedWord lastCollectionPromotedChunkBytes() {
            return lastCollectionPromotedChunkBytes;
        }

        /*
         * Methods for collectors.
         */

        void beforeCollection() {
            final Log trace = Log.noopLog().string("[GCImpl.Accounting.beforeCollection:").newline();
            /* Gather some space statistics. */
            final HeapImpl heap = HeapImpl.getHeapImpl();
            final YoungGeneration youngGen = heap.getYoungGeneration();
            youngChunkBytesBefore = youngGen.getChunkUsedBytes();
            /* This is called before the collection, so OldSpace is FromSpace. */
            final Space oldSpace = heap.getOldGeneration().getFromSpace();
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
            final Log trace = Log.noopLog().string("[GCImpl.Accounting.afterIncrementalCollection:");
            /*
             * Aggregating collection information is needed because any given collection policy may
             * not be called for all collections, but may want to make decisions based on the
             * aggregate values.
             */
            incrementalCollectionCount += 1;
            afterCollectionCommon();
            /* Incremental collections only promote. */
            lastCollectionPromotedChunkBytes = oldChunkBytesAfter.subtract(oldChunkBytesBefore);
            promotedTotalChunkBytes = promotedTotalChunkBytes.add(lastCollectionPromotedChunkBytes);
            incrementalCollectionTotalNanos += collectionTimer.getCollectedNanos();
            trace.string("  incrementalCollectionCount: ").signed(incrementalCollectionCount)
                            .string("  oldChunkBytesAfter: ").unsigned(oldChunkBytesAfter)
                            .string("  oldChunkBytesBefore: ").unsigned(oldChunkBytesBefore)
                            .string("  promotedChunkBytes: ").unsigned(lastCollectionPromotedChunkBytes);
            trace.string("]").newline();
        }

        private void afterCompleteCollection(Timer collectionTimer) {
            final Log trace = Log.noopLog().string("[GCImpl.Accounting.afterCompleteCollection:");
            completeCollectionCount += 1;
            afterCollectionCommon();
            /* Complete collections only copy, and they copy everything. */
            copiedTotalChunkBytes = copiedTotalChunkBytes.add(oldChunkBytesAfter);
            completeCollectionTotalNanos += collectionTimer.getCollectedNanos();
            trace.string("  completeCollectionCount: ").signed(completeCollectionCount)
                            .string("  oldChunkBytesAfter: ").unsigned(oldChunkBytesAfter);
            trace.string("]").newline();
        }

        void afterCollectionCommon() {
            final HeapImpl heap = HeapImpl.getHeapImpl();
            // This is called after the collection, after the space flip, so OldSpace is FromSpace.
            final YoungGeneration youngGen = heap.getYoungGeneration();
            youngChunkBytesAfter = youngGen.getChunkUsedBytes();
            final Space oldSpace = heap.getOldGeneration().getFromSpace();
            oldChunkBytesAfter = oldSpace.getChunkBytes();
            final UnsignedWord beforeChunkBytes = youngChunkBytesBefore.add(oldChunkBytesBefore);
            final UnsignedWord afterChunkBytes = oldChunkBytesAfter.add(youngChunkBytesAfter);
            final UnsignedWord collectedChunkBytes = beforeChunkBytes.subtract(afterChunkBytes);
            collectedTotalChunkBytes = collectedTotalChunkBytes.add(collectedChunkBytes);
            if (HeapOptions.PrintGCSummary.getValue()) {
                UnsignedWord youngObjectBytesAfter = youngGen.getObjectBytes();
                UnsignedWord oldObjectBytesAfter = oldSpace.getObjectBytes();
                final UnsignedWord beforeObjectBytes = youngObjectBytesBefore.add(oldObjectBytesBefore);
                final UnsignedWord collectedObjectBytes = beforeObjectBytes.subtract(oldObjectBytesAfter).subtract(youngObjectBytesAfter);
                collectedTotalObjectBytes = collectedTotalObjectBytes.add(collectedObjectBytes);
            }
        }
    }

    static class Timer implements AutoCloseable {
        private final String name;
        private long openNanos;
        private long closeNanos;
        private long collectedNanos;

        public Timer(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Timer open() {
            openNanos = System.nanoTime();
            closeNanos = 0L;
            return this;
        }

        @Override
        public void close() {
            /* If a timer was not opened, pretend it was opened at the start of the VM. */
            if (openNanos == 0L) {
                openNanos = HeapChunkProvider.getFirstAllocationTime();
            }
            closeNanos = System.nanoTime();
            collectedNanos += closeNanos - openNanos;
        }

        public void reset() {
            openNanos = 0L;
            closeNanos = 0L;
            collectedNanos = 0L;
        }

        public long getFinish() {
            assert closeNanos > 0L : "Should have closed timer";
            return closeNanos;
        }

        /** Get all the nanoseconds collected between open/close pairs since the last reset. */
        long getCollectedNanos() {
            return collectedNanos;
        }

        /** Get the nanoseconds collected by the most recent open/close pair. */
        long getLastIntervalNanos() {
            assert openNanos > 0L : "Should have opened timer";
            assert closeNanos > 0L : "Should have closed timer";
            return closeNanos - openNanos;
        }

        static long getTimeSinceFirstAllocation(final long nanos) {
            return nanos - HeapChunkProvider.getFirstAllocationTime();
        }
    }

    RememberedSetConstructor getRememberedSetConstructor() {
        return rememberedSetConstructor;
    }

    protected static class RememberedSetConstructor implements ObjectVisitor {
        AlignedHeapChunk.AlignedHeader chunk;

        @Platforms(Platform.HOSTED_ONLY.class)
        RememberedSetConstructor() {
        }

        public void initialize(AlignedHeapChunk.AlignedHeader aChunk) {
            this.chunk = aChunk;
        }

        @Override
        public boolean visitObject(final Object o) {
            return visitObjectInline(o);
        }

        @Override
        @AlwaysInline("GC performance")
        public boolean visitObjectInline(final Object o) {
            AlignedHeapChunk.setUpRememberedSetForObjectOfAlignedHeapChunk(chunk, o);
            return true;
        }

        public void reset() {
            chunk = WordFactory.nullPointer();
        }
    }

    /** Signals that a collection is already in progress. */
    @SuppressWarnings("serial")
    static final class CollectionInProgressError extends Error {
        static void exitIf(final boolean state) {
            if (state) {
                /* Throw an error to capture the stack backtrace. */
                final Log failure = Log.log();
                failure.string("[CollectionInProgressError:");
                failure.newline();
                ThreadStackPrinter.printBacktrace();
                failure.string("]").newline();
                throw CollectionInProgressError.SINGLETON;
            }
        }

        private CollectionInProgressError() {
        }

        private static final CollectionInProgressError SINGLETON = new CollectionInProgressError();
    }

    private static class CollectionVMOperation extends NativeVMOperation {
        protected CollectionVMOperation() {
            super("Garbage collection", SystemEffect.SAFEPOINT);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isGC() {
            return true;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while collecting")
        protected void operate(NativeVMOperationData data) {
            /*
             * Exceptions during collections are fatal. The heap is likely in an inconsistent state.
             * The GC must also be allocation free, i.e., we cannot allocate exception stack traces
             * while in the GC. This is bad for diagnosing errors in the GC. To improve the
             * situation a bit, we switch on the flag to make implicit exceptions such as
             * NullPointerExceptions fatal errors. This ensures that we fail early at the place
             * where the fatal error reporting can still dump the full stack trace.
             */
            ImplicitExceptions.activateImplicitExceptionsAreFatal();
            try {
                CollectionVMOperationData d = (CollectionVMOperationData) data;
                boolean outOfMemory = HeapImpl.getHeapImpl().getGCImpl().collectOperation(GCCause.fromId(d.getCauseId()), d.getRequestingEpoch());
                d.setOutOfMemory(outOfMemory);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            } finally {
                ImplicitExceptions.deactivateImplicitExceptionsAreFatal();
            }
        }

        @Override
        protected boolean hasWork(NativeVMOperationData data) {
            CollectionVMOperationData d = (CollectionVMOperationData) data;
            return HeapImpl.getHeapImpl().getGCImpl().getCollectionEpoch().equal(d.getRequestingEpoch());
        }
    }

    @RawStructure
    private interface CollectionVMOperationData extends NativeVMOperationData {
        @RawField
        int getCauseId();

        @RawField
        void setCauseId(int value);

        @RawField
        UnsignedWord getRequestingEpoch();

        @RawField
        void setRequestingEpoch(UnsignedWord value);

        @RawField
        boolean getOutOfMemory();

        @RawField
        void setOutOfMemory(boolean value);
    }

    private void printGCSummary() {
        if (!HeapOptions.PrintGCSummary.getValue()) {
            return;
        }

        final Log log = Log.log();
        final String prefix = "PrintGCSummary: ";

        log.string(prefix).string("YoungGenerationSize: ").unsigned(HeapPolicy.getMaximumYoungGenerationSize()).newline();
        log.string(prefix).string("MinimumHeapSize: ").unsigned(HeapPolicy.getMinimumHeapSize()).newline();
        log.string(prefix).string("MaximumHeapSize: ").unsigned(HeapPolicy.getMaximumHeapSize()).newline();
        log.string(prefix).string("AlignedChunkSize: ").unsigned(HeapPolicy.getAlignedHeapChunkSize()).newline();

        JavaVMOperation.enqueueBlockingSafepoint("PrintGCSummaryShutdownHook", ThreadLocalAllocation::disableThreadLocalAllocation);
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final Space edenSpace = heap.getYoungGeneration().getEden();
        final UnsignedWord youngChunkBytes = edenSpace.getChunkBytes();
        final UnsignedWord youngObjectBytes = edenSpace.getObjectBytes();

        final UnsignedWord allocatedNormalChunkBytes = accounting.getNormalChunkBytes().add(youngChunkBytes);
        final UnsignedWord allocatedNormalObjectBytes = accounting.getNormalObjectBytes().add(youngObjectBytes);

        log.string(prefix).string("CollectedTotalChunkBytes: ").signed(accounting.getCollectedTotalChunkBytes()).newline();
        log.string(prefix).string("CollectedTotalObjectBytes: ").signed(accounting.getCollectedTotalObjectBytes()).newline();
        log.string(prefix).string("AllocatedNormalChunkBytes: ").signed(allocatedNormalChunkBytes).newline();
        log.string(prefix).string("AllocatedNormalObjectBytes: ").signed(allocatedNormalObjectBytes).newline();

        final long incrementalNanos = accounting.getIncrementalCollectionTotalNanos();
        log.string(prefix).string("IncrementalGCCount: ").signed(accounting.getIncrementalCollectionCount()).newline();
        log.string(prefix).string("IncrementalGCNanos: ").signed(incrementalNanos).newline();
        final long completeNanos = accounting.getCompleteCollectionTotalNanos();
        log.string(prefix).string("CompleteGCCount: ").signed(accounting.getCompleteCollectionCount()).newline();
        log.string(prefix).string("CompleteGCNanos: ").signed(completeNanos).newline();

        final long gcNanos = incrementalNanos + completeNanos;
        final long mutatorNanos = mutatorTimer.getCollectedNanos();
        final long totalNanos = gcNanos + mutatorNanos;
        final long roundedGCLoad = (0 < totalNanos ? TimeUtils.roundedDivide(100 * gcNanos, totalNanos) : 0);
        log.string(prefix).string("GCNanos: ").signed(gcNanos).newline();
        log.string(prefix).string("TotalNanos: ").signed(totalNanos).newline();
        log.string(prefix).string("GCLoadPercent: ").signed(roundedGCLoad).newline();
    }

    @Override
    public List<GarbageCollectorMXBean> getGarbageCollectorMXBeanList() {
        return gcManagementFactory.getGCBeanList();
    }
}

final class GarbageCollectorManagementFactory {

    private final List<GarbageCollectorMXBean> gcBeanList;

    GarbageCollectorManagementFactory() {
        final List<GarbageCollectorMXBean> newList = new ArrayList<>();
        /* Changing the order of this list will break assumptions we take in the object replacer. */
        newList.add(new IncrementalGarbageCollectorMXBean());
        newList.add(new CompleteGarbageCollectorMXBean());
        gcBeanList = newList;
    }

    List<GarbageCollectorMXBean> getGCBeanList() {
        return gcBeanList;
    }

    private static final class IncrementalGarbageCollectorMXBean implements com.sun.management.GarbageCollectorMXBean, NotificationEmitter {

        private IncrementalGarbageCollectorMXBean() {
        }

        @Override
        public long getCollectionCount() {
            return HeapImpl.getHeapImpl().getGCImpl().getAccounting().getIncrementalCollectionCount();
        }

        @Override
        public long getCollectionTime() {
            final long nanos = HeapImpl.getHeapImpl().getGCImpl().getAccounting().getIncrementalCollectionTotalNanos();
            return TimeUtils.roundNanosToMillis(nanos);
        }

        @Override
        public String[] getMemoryPoolNames() {
            /* Return a new array each time because arrays are not immutable. */
            return new String[]{"young generation space"};
        }

        @Override
        public String getName() {
            /* Changing this name will break assumptions we take in the object replacer. */
            return "young generation scavenger";
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public ObjectName getObjectName() {
            return Util.newObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE, getName());
        }

        @Override
        public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        }

        @Override
        public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        }

        @Override
        public void removeNotificationListener(NotificationListener listener) {
        }

        @Override
        public MBeanNotificationInfo[] getNotificationInfo() {
            return new MBeanNotificationInfo[0];
        }

        @Override
        public GcInfo getLastGcInfo() {
            return null;
        }
    }

    private static final class CompleteGarbageCollectorMXBean implements com.sun.management.GarbageCollectorMXBean, NotificationEmitter {

        private CompleteGarbageCollectorMXBean() {
        }

        @Override
        public long getCollectionCount() {
            return HeapImpl.getHeapImpl().getGCImpl().getAccounting().getCompleteCollectionCount();
        }

        @Override
        public long getCollectionTime() {
            final long nanos = HeapImpl.getHeapImpl().getGCImpl().getAccounting().getCompleteCollectionTotalNanos();
            return TimeUtils.roundNanosToMillis(nanos);
        }

        @Override
        public String[] getMemoryPoolNames() {
            /* Return a new array each time because arrays are not immutable. */
            return new String[]{"young generation space", "old generation space"};
        }

        @Override
        public String getName() {
            /* Changing this name will break assumptions we take in the object replacer. */
            return "complete scavenger";
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public ObjectName getObjectName() {
            return Util.newObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE, getName());
        }

        @Override
        public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        }

        @Override
        public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        }

        @Override
        public void removeNotificationListener(NotificationListener listener) {
        }

        @Override
        public MBeanNotificationInfo[] getNotificationInfo() {
            return new MBeanNotificationInfo[0];
        }

        @Override
        public GcInfo getLastGcInfo() {
            return null;
        }
    }
}
