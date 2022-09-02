/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;

public final class HeapVerifier {
    private static final ObjectVerifier OBJECT_VERIFIER = new ObjectVerifier();
    private static final ImageHeapRegionVerifier IMAGE_HEAP_OBJECT_VERIFIER = new ImageHeapRegionVerifier();
    private static final ObjectReferenceVerifier REFERENCE_VERIFIER = new ObjectReferenceVerifier();

    @Platforms(Platform.HOSTED_ONLY.class)
    private HeapVerifier() {
    }

    public static boolean verify(Occasion occasion) {
        boolean success = true;
        success &= verifyImageHeapObjects();
        success &= verifyYoungGeneration(occasion);
        success &= verifyOldGeneration();
        return success;
    }

    private static boolean verifyImageHeapObjects() {
        if (HeapImpl.usesImageHeapChunks()) {
            return verifyChunkedImageHeap();
        } else {
            return verifyNonChunkedImageHeap();
        }
    }

    private static boolean verifyChunkedImageHeap() {
        boolean success = true;
        ImageHeapInfo info = HeapImpl.getImageHeapInfo();

        success &= verifyAlignedChunks(null, info.getFirstAlignedImageHeapChunk());
        success &= verifyUnalignedChunks(null, info.getFirstUnalignedImageHeapChunk());

        success &= RememberedSet.get().verify(info.getFirstAlignedImageHeapChunk());
        success &= RememberedSet.get().verify(info.getFirstUnalignedImageHeapChunk());

        return success;
    }

    private static boolean verifyNonChunkedImageHeap() {
        // Without chunks, just visit all the image heap objects.
        IMAGE_HEAP_OBJECT_VERIFIER.initialize();
        ImageHeapWalker.walkRegions(HeapImpl.getImageHeapInfo(), IMAGE_HEAP_OBJECT_VERIFIER);
        return IMAGE_HEAP_OBJECT_VERIFIER.getResult();
    }

    private static boolean verifyYoungGeneration(Occasion occasion) {
        boolean success = true;
        YoungGeneration youngGeneration = HeapImpl.getHeapImpl().getYoungGeneration();
        if (occasion.equals(HeapVerifier.Occasion.AFTER_COLLECTION)) {
            Space eden = youngGeneration.getEden();
            if (!eden.isEmpty()) {
                Log.log().string("Eden contains chunks after collection:  firstAlignedChunk: ").hex(eden.getFirstAlignedHeapChunk()).string(" firstUnalignedChunk: ")
                                .hex(eden.getFirstUnalignedHeapChunk()).newline();
                success = false;
            }
        }

        success &= verify(youngGeneration.getEden());

        for (int i = 0; i < youngGeneration.getMaxSurvivorSpaces(); i++) {
            Space fromSpace = youngGeneration.getSurvivorFromSpaceAt(i);
            Space toSpace = youngGeneration.getSurvivorToSpaceAt(i);

            if (!toSpace.isEmpty()) {
                Log.log().string("Survivor to-space ").signed(i).string(" contains chunks:  firstAlignedChunk: ").hex(toSpace.getFirstAlignedHeapChunk()).string(" firstUnalignedChunk: ")
                                .hex(toSpace.getFirstUnalignedHeapChunk()).newline();
                success = false;
            }

            success &= verify(fromSpace);
            success &= verify(toSpace);
        }

        return success;
    }

    private static boolean verifyOldGeneration() {
        boolean success = true;
        OldGeneration oldGeneration = HeapImpl.getHeapImpl().getOldGeneration();
        Space fromSpace = oldGeneration.getFromSpace();
        Space toSpace = oldGeneration.getToSpace();

        if (!toSpace.isEmpty()) {
            Log.log().string("Old generation to-space contains chunks:  firstAlignedChunk: ").hex(toSpace.getFirstAlignedHeapChunk()).string(" firstUnalignedChunk: ")
                            .hex(toSpace.getFirstUnalignedHeapChunk()).newline();
            success = false;
        }

        success &= verify(fromSpace);
        success &= RememberedSet.get().verify(fromSpace.getFirstAlignedHeapChunk());
        success &= RememberedSet.get().verify(fromSpace.getFirstUnalignedHeapChunk());

        success &= verify(toSpace);
        success &= RememberedSet.get().verify(toSpace.getFirstAlignedHeapChunk());
        success &= RememberedSet.get().verify(toSpace.getFirstUnalignedHeapChunk());
        return success;
    }

    public static boolean verify(Space space) {
        boolean success = true;
        success &= verifyChunkList(space, "aligned", space.getFirstAlignedHeapChunk(), space.getLastAlignedHeapChunk());
        success &= verifyChunkList(space, "unaligned", space.getFirstUnalignedHeapChunk(), space.getLastUnalignedHeapChunk());
        success &= verifyAlignedChunks(space, space.getFirstAlignedHeapChunk());
        success &= verifyUnalignedChunks(space, space.getFirstUnalignedHeapChunk());
        return success;
    }

    private static boolean verifyChunkList(Space space, String kind, HeapChunk.Header<?> firstChunk, HeapChunk.Header<?> lastChunk) {
        boolean result = true;
        HeapChunk.Header<?> current = firstChunk;
        HeapChunk.Header<?> previous = WordFactory.nullPointer();
        while (current.isNonNull()) {
            HeapChunk.Header<?> previousOfCurrent = HeapChunk.getPrevious(current);
            if (previousOfCurrent.notEqual(previous)) {
                Log.log().string("Verification failed for the doubly-linked list that holds ").string(kind).string(" chunks:  space: ").string(space.getName()).string("  current: ").hex(current)
                                .string("  current.previous: ").hex(previousOfCurrent).string("  previous: ").hex(previous).newline();
                result = false;
            }
            previous = current;
            current = HeapChunk.getNext(current);
        }

        if (previous.notEqual(lastChunk)) {
            Log.log().string("Verification failed for the doubly-linked list that holds ").string(kind).string(" chunks:  space: ").string(space.getName()).string("  previous: ").hex(previous)
                            .string("  lastChunk: ").hex(lastChunk).newline();
            result = false;
        }
        return result;
    }

    private static boolean verifyAlignedChunks(Space space, AlignedHeader firstAlignedHeapChunk) {
        boolean success = true;
        AlignedHeader aChunk = firstAlignedHeapChunk;
        while (aChunk.isNonNull()) {
            if (space != aChunk.getSpace()) {
                Log.log().string("Space ").string(space.getName()).string(" contains aligned chunk ").hex(aChunk).string(" but the chunk does not reference the correct space: ")
                                .hex(Word.objectToUntrackedPointer(aChunk.getSpace())).newline();
                success = false;
            }

            OBJECT_VERIFIER.initialize(aChunk, WordFactory.nullPointer());
            AlignedHeapChunk.walkObjects(aChunk, OBJECT_VERIFIER);
            aChunk = HeapChunk.getNext(aChunk);
            success &= OBJECT_VERIFIER.result;
        }
        return success;
    }

    private static boolean verifyUnalignedChunks(Space space, UnalignedHeader firstUnalignedHeapChunk) {
        boolean success = true;
        UnalignedHeader uChunk = firstUnalignedHeapChunk;
        while (uChunk.isNonNull()) {
            if (space != uChunk.getSpace()) {
                Log.log().string("Space ").string(space.getName()).string(" contains unaligned chunk ").hex(uChunk).string(" but the chunk does not reference the correct space: ")
                                .hex(Word.objectToUntrackedPointer(uChunk.getSpace())).newline();
                success = false;
            }

            OBJECT_VERIFIER.initialize(WordFactory.nullPointer(), uChunk);
            UnalignedHeapChunk.walkObjects(uChunk, OBJECT_VERIFIER);
            uChunk = HeapChunk.getNext(uChunk);
            success &= OBJECT_VERIFIER.result;
        }
        return success;
    }

    // This method is executed exactly once per object in the heap.
    private static boolean verifyObject(Object obj, AlignedHeader aChunk, UnalignedHeader uChunk) {
        Pointer ptr = Word.objectToUntrackedPointer(obj);
        if (ptr.isNull()) {
            Log.log().string("Encounter a null pointer while walking the heap objects.").newline();
            return false;
        }

        int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        if (ptr.unsignedRemainder(objectAlignment).notEqual(0)) {
            Log.log().string("Object ").hex(ptr).string(" is not properly aligned to ").signed(objectAlignment).string(" bytes.").newline();
            return false;
        }

        UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(ptr);
        if (ObjectHeaderImpl.isProducedHeapChunkZapped(header) || ObjectHeaderImpl.isConsumedHeapChunkZapped(header)) {
            Log.log().string("Object ").hex(ptr).string(" has a zapped header: ").hex(header).newline();
            return false;
        }

        if (ObjectHeaderImpl.isForwardedHeader(header)) {
            Log.log().string("Object ").hex(ptr).string(" has a forwarded header: ").hex(header).newline();
            return false;
        }

        if (HeapImpl.usesImageHeapChunks() || !HeapImpl.getHeapImpl().isInImageHeap(obj)) {
            assert aChunk.isNonNull() ^ uChunk.isNonNull();
            HeapChunk.Header<?> expectedChunk = aChunk.isNonNull() ? aChunk : uChunk;
            HeapChunk.Header<?> chunk = HeapChunk.getEnclosingHeapChunk(obj);
            if (chunk.notEqual(expectedChunk)) {
                Log.log().string("Object ").hex(ptr).string(" should have ").hex(expectedChunk).string(" as its enclosing chunk but getEnclosingHeapChunk returned ").hex(chunk).newline();
                return false;
            }

            Pointer chunkStart = HeapChunk.asPointer(chunk);
            Pointer chunkTop = HeapChunk.getTopPointer(chunk);
            if (chunkStart.aboveOrEqual(ptr) || chunkTop.belowOrEqual(ptr)) {
                Log.log().string("Object ").hex(ptr).string(" is not within the allocated part of the chunk: [").hex(chunkStart).string(", ").hex(chunkTop).string("]").newline();
                return false;
            }

            if (aChunk.isNonNull()) {
                if (!ObjectHeaderImpl.isAlignedHeader(header)) {
                    Log.log().string("Header of object ").hex(ptr).string(" is not marked as aligned: ").hex(header).newline();
                    return false;
                }
            } else {
                assert uChunk.isNonNull();
                if (!ObjectHeaderImpl.isUnalignedHeader(header)) {
                    Log.log().string("Header of object ").hex(ptr).string(" is not marked as unaligned: ").hex(header).newline();
                    return false;
                }
            }
        }

        DynamicHub hub = KnownIntrinsics.readHub(obj);
        if (!HeapImpl.getHeapImpl().isInImageHeap(hub)) {
            Log.log().string("Object ").hex(ptr).string(" references a hub that is not in the image heap: ").hex(Word.objectToUntrackedPointer(hub)).newline();
            return false;
        }

        return verifyReferences(obj);
    }

    // This method is executed exactly once per object in the heap.
    private static boolean verifyReferences(Object obj) {
        REFERENCE_VERIFIER.reset();
        InteriorObjRefWalker.walkObject(obj, REFERENCE_VERIFIER);

        boolean success = REFERENCE_VERIFIER.result;
        DynamicHub hub = KnownIntrinsics.readHub(obj);
        if (hub.isReferenceInstanceClass()) {
            // The referent field of java.lang.Reference is excluded from the reference map, so we
            // need to verify it separately.
            Reference<?> ref = KnownIntrinsics.convertUnknownValue(obj, Reference.class);
            success &= verifyReferent(ref);
        }
        return success;
    }

    // This method is executed exactly once for each object reference in the heap and on the stack.
    public static boolean verifyReference(Pointer objRef, boolean compressed) {
        Pointer ptr = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
        if (!isInHeap(ptr)) {
            Log.log().string("Object reference at ").hex(objRef).string(" points outside the Java heap: ").hex(ptr).newline();
            return false;
        }
        return true;
    }

    private static boolean isInHeap(Pointer ptr) {
        HeapImpl heap = HeapImpl.getHeapImpl();
        return heap.isInImageHeap(ptr) || isInYoungGen(ptr) || isInOldGen(ptr);
    }

    private static boolean isInYoungGen(Pointer ptr) {
        YoungGeneration youngGen = HeapImpl.getHeapImpl().getYoungGeneration();
        if (findPointerInSpace(youngGen.getEden(), ptr)) {
            return true;
        }

        for (int i = 0; i < youngGen.getMaxSurvivorSpaces(); i++) {
            if (findPointerInSpace(youngGen.getSurvivorFromSpaceAt(i), ptr)) {
                return true;
            }
            if (findPointerInSpace(youngGen.getSurvivorToSpaceAt(i), ptr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInOldGen(Pointer ptr) {
        OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
        return findPointerInSpace(oldGen.getFromSpace(), ptr) || findPointerInSpace(oldGen.getToSpace(), ptr);
    }

    private static boolean findPointerInSpace(Space space, Pointer p) {
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            Pointer start = AlignedHeapChunk.getObjectsStart(aChunk);
            if (start.belowOrEqual(p) && p.belowThan(HeapChunk.getTopPointer(aChunk))) {
                return true;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }

        UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            Pointer start = UnalignedHeapChunk.getObjectStart(uChunk);
            if (start.belowOrEqual(p) && p.belowThan(HeapChunk.getTopPointer(uChunk))) {
                return true;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        return false;
    }

    private static boolean verifyReferent(Reference<?> ref) {
        Pointer ptr = ReferenceInternals.getReferentPointer(ref);
        if (!isInHeap(ptr)) {
            Log.log().string("Referent in ").hex(Word.objectToUntrackedPointer(ref)).string(" points outside the Java heap: ").hex(ptr).newline();
            return false;
        }
        return true;
    }

    private static class ImageHeapRegionVerifier implements MemoryWalker.ImageHeapRegionVisitor {
        private final ImageHeapObjectVerifier objectVerifier;

        @Platforms(Platform.HOSTED_ONLY.class)
        ImageHeapRegionVerifier() {
            objectVerifier = new ImageHeapObjectVerifier();
        }

        public void initialize() {
            objectVerifier.initialize(WordFactory.nullPointer(), WordFactory.nullPointer());
        }

        public boolean getResult() {
            return objectVerifier.result;
        }

        @Override
        public <T> boolean visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            access.visitObjects(region, objectVerifier);
            return true;
        }
    }

    private static class ObjectVerifier implements ObjectVisitor {
        protected boolean result;
        private AlignedHeader aChunk;
        private UnalignedHeader uChunk;

        @Platforms(Platform.HOSTED_ONLY.class)
        ObjectVerifier() {
        }

        @SuppressWarnings("hiding")
        void initialize(AlignedHeader aChunk, UnalignedHeader uChunk) {
            this.result = true;
            this.aChunk = aChunk;
            this.uChunk = uChunk;
        }

        @Override
        public boolean visitObject(Object object) {
            result &= verifyObject(object, aChunk, uChunk);
            return true;
        }
    }

    private static class ImageHeapObjectVerifier extends ObjectVerifier {
        @Platforms(Platform.HOSTED_ONLY.class)
        ImageHeapObjectVerifier() {
        }

        @Override
        public boolean visitObject(Object object) {
            Word pointer = Word.objectToUntrackedPointer(object);
            if (!HeapImpl.getHeapImpl().isInImageHeap(object)) {
                Log.log().string("Image heap object ").hex(pointer).string(" is not considered as part of the image heap.").newline();
                result = false;
            }

            return super.visitObject(object);
        }
    }

    private static class ObjectReferenceVerifier implements ObjectReferenceVisitor {
        private boolean result;

        @Platforms(Platform.HOSTED_ONLY.class)
        ObjectReferenceVerifier() {
        }

        public void reset() {
            this.result = true;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            result &= verifyReference(objRef, compressed);
            return true;
        }
    }

    public enum Occasion {
        BEFORE_COLLECTION,
        AFTER_COLLECTION
    }
}
