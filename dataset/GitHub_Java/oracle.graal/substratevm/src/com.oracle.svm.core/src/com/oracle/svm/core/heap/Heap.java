/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.os.CommittedMemoryProvider;

import jdk.vm.ci.meta.MetaAccessProvider;

public abstract class Heap {
    @Fold
    public static Heap getHeap() {
        return ImageSingletons.lookup(Heap.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected Heap() {
    }

    /**
     * Notifies the heap that a new thread was attached to the VM. This allows to initialize
     * heap-specific datastructures, e.g., the TLAB. This method is called for every thread except
     * the main thread (i.e., the one that maps the image heap).
     */
    @Uninterruptible(reason = "Called during startup.")
    public abstract void attachThread(IsolateThread isolateThread);

    /**
     * Notifies the heap that a thread will be detached from the VM. This allows to cleanup
     * heap-specific resources, e.g., the TLAB. This method is called for every thread except the
     * main thread (i.e., the one that maps the image heap).
     */
    public abstract void detachThread(IsolateThread isolateThread);

    public abstract void suspendAllocation();

    public abstract void resumeAllocation();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean isAllocationDisallowed();

    public abstract GC getGC();

    public abstract RuntimeCodeInfoGCSupport getRuntimeCodeInfoGCSupport();

    /**
     * Walk all the objects in the heap. Must only be executed as part of a VM operation that causes
     * a safepoint.
     */
    public abstract boolean walkObjects(ObjectVisitor visitor);

    /**
     * Walk all native image heap objects. Must only be executed as part of a VM operation that
     * causes a safepoint.
     */
    public abstract boolean walkImageHeapObjects(ObjectVisitor visitor);

    /**
     * Walk all heap objects except the native image heap objects. Must only be executed as part of
     * a VM operation that causes a safepoint.
     */
    public abstract boolean walkCollectedHeapObjects(ObjectVisitor visitor);

    /** Returns the number of classes in the heap (initialized as well as uninitialized). */
    public abstract int getClassCount();

    /** Returns all loaded classes in the heap (see {@link PredefinedClassesSupport}). */
    public List<Class<?>> getLoadedClasses() {
        List<Class<?>> all = getAllClasses();
        ArrayList<Class<?>> loaded = new ArrayList<>(all.size());
        for (Class<?> clazz : all) {
            if (DynamicHub.fromClass(clazz).isLoaded()) {
                loaded.add(clazz);
            }
        }
        return loaded;
    }

    /**
     * Get all known classes. Intentionally protected to prevent access to classes that have not
     * been "loaded" yet, see {@link PredefinedClassesSupport}.
     */
    protected abstract List<Class<?>> getAllClasses();

    /**
     * Get the ObjectHeader implementation that this Heap uses.
     *
     * TODO: This is used during native image generation to put appropriate headers on Objects in
     * the native image heap. Is there any reason to expose the whole ObjectHeader interface, since
     * only setBootImageOnLong(0L) is used then, to get the native image object header bits?
     *
     * TODO: Would an "Unsigned getBootImageObjectHeaderBits()" method be sufficient?
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract ObjectHeader getObjectHeader();

    /** Tear down the heap and free all allocated virtual memory chunks. */
    @Uninterruptible(reason = "Tear-down in progress.")
    public abstract boolean tearDown();

    /** Prepare the heap for a safepoint. */
    public abstract void prepareForSafepoint();

    /** Reset the heap to the normal execution state. */
    public abstract void endSafepoint();

    /**
     * Returns a suitable {@link BarrierSet} for the garbage collector that is used for this heap.
     */
    public abstract BarrierSet createBarrierSet(MetaAccessProvider metaAccess);

    /**
     * Returns a multiple to which the heap address space should be aligned to at runtime.
     *
     * @see CommittedMemoryProvider#guaranteesHeapPreferredAddressSpaceAlignment()
     */
    @Fold
    public abstract int getPreferredAddressSpaceAlignment();

    /**
     * Returns the offset that the image heap should have when mapping the native image file to the
     * address space in memory.
     */
    @Fold
    public abstract int getImageHeapOffsetInAddressSpace();

    /**
     * Returns true if the given object is located in the image heap.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean isInImageHeap(Object object);

    /** Returns true if the object at the given address is located in the image heap. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean isInImageHeap(Pointer objectPtr);

    /**
     * Determines if the heap currently has {@link Reference} objects that are pending to be
     * {@linkplain java.lang.ref.ReferenceQueue enqueued}.
     */
    public abstract boolean hasReferencePendingList();

    /** Blocks until the heap has pending {@linkplain Reference references}. */
    public abstract void waitForReferencePendingList() throws InterruptedException;

    /** Unblocks any threads in {@link #waitForReferencePendingList()}. */
    public abstract void wakeUpReferencePendingListWaiters();

    /**
     * Atomically get the list of pending {@linkplain Reference references} and clears (resets) it.
     * May return {@code null}.
     */
    public abstract Reference<?> getAndClearReferencePendingList();
}
