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

import java.util.function.IntUnaryOperator;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;

/**
 * The common structure of the chunks of memory which make up the heap. HeapChunks are aggregated
 * into {@linkplain Space spaces}. A specific "subtype" of chunk should be accessed via its own
 * accessor class, such as {@link AlignedHeapChunk}, which provides methods that are specific to the
 * type of chunk and its layout. (Such classes intentionally do not subclass {@link HeapChunk} so to
 * not directly expose its methods.)
 * <p>
 * A HeapChunk is raw memory with a {@linkplain Header} on the beginning that stores bookkeeping
 * information about the HeapChunk. HeapChunks do not have any instance methods: instead they have
 * static methods that take the HeapChunk.Header as a parameter.
 * <p>
 * HeapChunks maintain Pointers to the current allocation point (top) with them, and the limit (end)
 * where Objects can be allocated. Subclasses of HeapChunks can add additional fields as needed.
 * <p>
 * HeapChunks maintain some fields that would otherwise have to be maintained in per-HeapChunk
 * memory by the Space that contains them. For example, the fields for linking lists of HeapChunks
 * in a Space is kept in each HeapChunk rather than in some storage outside the HeapChunk.
 * <p>
 * For fields that are maintained as more-specifically-typed Pointers by leaf "sub-classes",
 * HeapChunk defines the generic (Pointer) "get" methods, and only the "sub-classes" define "set"
 * methods that store more-specifically-typed Pointers, for type safety.
 * <p>
 * In addition to the declared fields of a HeapChunk.Header, for example, a subtype keeps a card
 * table for the write barrier, but because they are variable-sized, rather than declaring field in
 * the Header, static methods are used to compute Pointers to those "fields".
 * <p>
 * HeapChunks are *not* examined for interior Object references by the collector, though the Objects
 * allocated within the HeapChunk are examined by the collector.
 */
final class HeapChunk {
    private HeapChunk() { // all static
    }

    static class Options {
        @Option(help = "Number of bytes at the beginning of each heap chunk that are not used for payload data, i.e., can be freely used as metadata by the heap chunk provider.") //
        public static final HostedOptionKey<Integer> HeapChunkHeaderPadding = new HostedOptionKey<>(0);
    }

    static class HeaderPaddingSizeProvider implements IntUnaryOperator {
        @Override
        public int applyAsInt(int operand) {
            assert operand == 0 : "padding structure does not declare any fields";
            return Options.HeapChunkHeaderPadding.getValue();
        }
    }

    @RawStructure(sizeProvider = HeaderPaddingSizeProvider.class)
    private interface HeaderPadding extends PointerBase {
    }

    @RawStructure
    public interface Header<T extends Header<T>> extends HeaderPadding {

        /**
         * Pointer to the memory available for allocation, i.e., the end of the last allocated
         * object in the chunk.
         */
        @RawField
        @UniqueLocationIdentity
        Pointer getTop();

        @RawField
        @UniqueLocationIdentity
        void setTop(Pointer newTop);

        /** Pointer to limit of the memory available for allocation, i.e., the end of the memory. */
        @RawField
        @UniqueLocationIdentity
        Pointer getEnd();

        @RawField
        @UniqueLocationIdentity
        void setEnd(Pointer newEnd);

        /**
         * The Space this HeapChunk is part of.
         *
         * All Space instances are in the native image heap, so it is safe to have a reference to a
         * Java object that the GC does not see.
         */
        @RawField
        @UniqueLocationIdentity
        @PinnedObjectField
        Space getSpace();

        @RawField
        @UniqueLocationIdentity
        @PinnedObjectField
        void setSpace(Space newSpace);

        /** The previous HeapChunk in the doubly-linked list maintained by the Space. */
        @RawField
        @UniqueLocationIdentity
        T getPrevious();

        @RawField
        @UniqueLocationIdentity
        void setPrevious(T newPrevious);

        /** The next HeapChunk in the doubly-linked list maintained by the Space. */
        @RawField
        @UniqueLocationIdentity
        T getNext();

        @RawField
        @UniqueLocationIdentity
        void setNext(T newNext);
    }

    @NeverInline("Not performance critical")
    public static boolean walkObjectsFrom(Header<?> that, Pointer offset, ObjectVisitor visitor) {
        return walkObjectsFromInline(that, offset, visitor);
    }

    @AlwaysInline("GC performance")
    public static boolean walkObjectsFromInline(Header<?> that, Pointer startOffset, ObjectVisitor visitor) {
        Pointer offset = startOffset;
        while (offset.belowThan(that.getTop())) {
            Object obj = offset.toObject();
            if (!visitor.visitObjectInline(obj)) {
                return false;
            }
            offset = offset.add(LayoutEncoding.getSizeFromObject(obj));
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord availableObjectMemory(Header<?> that) {
        return that.getEnd().subtract(that.getTop());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void setTopCarefully(Header<?> that, Pointer newTop) {
        assert that.getTop().belowOrEqual(newTop) : "newTop too low.";
        assert newTop.belowOrEqual(that.getEnd()) : "newTop too high.";
        that.setTop(newTop);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static Pointer asPointer(Header<?> that) {
        return (Pointer) that;
    }

    public static HeapChunk.Header<?> getEnclosingHeapChunk(Object obj) {
        assert !HeapImpl.getHeapImpl().isInImageHeap(obj) : "Must be checked before calling this method";
        assert !ObjectHeaderImpl.isPointerToForwardedObject(Word.objectToUntrackedPointer(obj)) : "Forwarded objects must be a pointer and not an object";
        if (ObjectHeaderImpl.isAlignedObject(obj)) {
            return AlignedHeapChunk.getEnclosingChunk(obj);
        } else {
            assert ObjectHeaderImpl.isUnalignedObject(obj);
            return UnalignedHeapChunk.getEnclosingChunk(obj);
        }
    }

    public static HeapChunk.Header<?> getEnclosingHeapChunk(Pointer ptrToObj, UnsignedWord header) {
        if (ObjectHeaderImpl.isAlignedHeader(ptrToObj, header)) {
            return AlignedHeapChunk.getEnclosingChunkFromPointer(ptrToObj);
        } else {
            return UnalignedHeapChunk.getEnclosingChunkFromObjectPointer(ptrToObj);
        }
    }

    abstract static class MemoryWalkerAccessImpl<T extends HeapChunk.Header<?>> implements MemoryWalker.HeapChunkAccess<T> {
        @Platforms(Platform.HOSTED_ONLY.class)
        MemoryWalkerAccessImpl() {
        }

        @Override
        public UnsignedWord getStart(T heapChunk) {
            return (UnsignedWord) heapChunk;
        }

        @Override
        public UnsignedWord getSize(T heapChunk) {
            return heapChunk.getEnd().subtract(getStart(heapChunk));
        }

        @Override
        public UnsignedWord getAllocationEnd(T heapChunk) {
            return heapChunk.getTop();
        }

        @Override
        public String getRegion(T heapChunk) {
            /* This method knows too much about spaces, especially the "free" space. */
            final Space space = heapChunk.getSpace();
            final String result;
            if (space == null) {
                result = "free";
            } else if (space.isYoungSpace()) {
                result = "young";
            } else {
                result = "old";
            }
            return result;
        }

    }

    static boolean verifyObjects(Header<?> that, Pointer start) {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifier().getTraceLog().string("[HeapChunk.verify:");
        trace.string("  that:  ").hex(that).string("  start: ").hex(start).string("  top: ").hex(that.getTop()).string("  end: ").hex(that.getEnd());
        Pointer p = start;
        while (p.belowThan(that.getTop())) {
            if (!HeapImpl.getHeapImpl().getHeapVerifier().verifyObjectAt(p)) {
                final Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[HeapChunk.verify:");
                witness.string("  that:  ").hex(that).string("  start: ").hex(start).string("  top: ").hex(that.getTop()).string("  end: ").hex(that.getEnd());
                witness.string("  space: ").string(that.getSpace().getName());
                witness.string("  object at p: ").hex(p).string("  fails to verify").string("]").newline();
                trace.string("  returns false]").newline();
                return false;
            }
            /* Step carefully over the object. */
            final UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointerCarefully(p);
            final Object o;
            if (ObjectHeaderImpl.isForwardedHeaderCarefully(header)) {
                o = ObjectHeaderImpl.getForwardedObject(p);
            } else {
                o = p.toObject();
            }
            p = p.add(LayoutEncoding.getSizeFromObject(o));
        }
        trace.string("  returns true]").newline();
        return true;
    }
}
