/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.MustNotAllocate;

/** A walker over different kinds of allocated memory. */
public abstract class MemoryWalker {

    /** Get the implementation of the MemoryWalker. */
    public static MemoryWalker getMemoryWalker() {
        return ImageSingletons.lookup(MemoryWalker.class);
    }

    /**
     * Walk memory applying the visitor. Returns true if all visits returned true, else false when
     * any visit returns false.
     */
    public abstract boolean visitMemory(Visitor visitor);

    /** This is the interface that clients have to implement. */
    public interface Visitor {

        /** Visit a region from the native image heap. */
        @MustNotAllocate(reason = "Must not allocate while visiting memory.")
        <T> boolean visitNativeImageHeapRegion(T bootImageHeapRegion, NativeImageHeapRegionAccess<T> access);

        /**
         * Visit a heap chunk, using the provided access methods. Return true if visiting should
         * continue, else false.
         */
        @MustNotAllocate(reason = "Must not allocate while visiting memory.")
        <T extends PointerBase> boolean visitHeapChunk(T heapChunk, HeapChunkAccess<T> access);

        @MustNotAllocate(reason = "Must not allocate while visiting memory.")
        <T> boolean visitImageCode(T imageCode, ImageCodeAccess<T> access);

        /**
         * Visit a runtime compiled method, using the provided access methods. Return true if
         * visiting should continue, else false.
         */
        @MustNotAllocate(reason = "Must not allocate while visiting memory.")
        <T> boolean visitRuntimeCompiledMethod(T runtimeMethod, RuntimeCompiledMethodAccess<T> access);
    }

    /** A set of access methods for visiting regions of the native image heap. */
    public interface NativeImageHeapRegionAccess<T> {

        /** Return the start of the native image heap region. */
        UnsignedWord getStart(T bootImageHeapRegion);

        /** Return the size of the native image heap region. */
        UnsignedWord getSize(T bootImageHeapRegion);

        /** Return the name of the native image heap region. */
        String getRegion(T bootImageHeapRegion);
    }

    /** A set of access methods for visiting heap chunk memory. */
    public interface HeapChunkAccess<T extends PointerBase> {

        /** Return the start of the heap chunk. */
        UnsignedWord getStart(T heapChunk);

        /** Return the size of the heap chunk. */
        UnsignedWord getSize(T heapChunk);

        /** Return the address where allocation starts within the heap chunk. */
        UnsignedWord getAllocationStart(T heapChunk);

        /**
         * Return the address where allocation has ended within the heap chunk. This is the first
         * address past the end of allocated space within the heap chunk.
         */
        UnsignedWord getAllocationEnd(T heapChunk);

        /**
         * Return the name of the region that contains the heap chunk. E.g., "young", "old", "free",
         * etc.
         */
        String getRegion(T heapChunk);

        /** Return true if the heap chunk is an aligned heap chunk, else false. */
        boolean isAligned(T heapChunk);

        /** Return true if the heap chunk is pinned in memory, else false. */
        boolean isPinned(T heapChunk);
    }

    /** A set of access methods for visiting image code. */
    public interface ImageCodeAccess<T> {

        /** Return the start of the image code. */
        UnsignedWord getStart(T imageCode);

        /** Return the size of the image code. */
        UnsignedWord getSize(T imageCode);

        /** Return the name of the image code region. */
        String getRegion(T imageCode);
    }

    /** A set of access methods for visiting runtime compiled code memory. */
    public interface RuntimeCompiledMethodAccess<T> {

        /** Return the start of the code of the runtime compiled method. */
        UnsignedWord getStart(T runtimeCompiledMethod);

        /** Return the size of the code of the runtime compiled method. */
        UnsignedWord getSize(T runtimeCompiledMethod);

        /** Return the name of the runtime compiled method. */
        String getName(T runtimeCompiledMethod);
    }
}
