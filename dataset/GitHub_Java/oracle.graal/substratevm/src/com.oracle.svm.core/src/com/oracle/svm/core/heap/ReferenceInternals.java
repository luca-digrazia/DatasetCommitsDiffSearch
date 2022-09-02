/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.snippets.KnownIntrinsics;

// Checkstyle: stop
import sun.misc.Unsafe;
// Checkstyle: resume

/**
 * Methods implementing the internals of {@link Reference} or providing access to them. These are
 * not injected into {@link Target_java_lang_ref_Reference} so that subclasses of {@link Reference}
 * cannot interfere with them.
 */
public final class ReferenceInternals {
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> Target_java_lang_ref_Reference<T> cast(Reference<T> instance) {
        return SubstrateUtil.cast(instance, Target_java_lang_ref_Reference.class);
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> Reference<T> uncast(Target_java_lang_ref_Reference<T> instance) {
        return SubstrateUtil.cast(instance, Reference.class);
    }

    /**
     * Garbage collection might run between the allocation of this object and before its constructor
     * is called, so this returns a flag that is set in the constructor (which is
     * {@link Uninterruptible}) and indicates whether the instance is fully initialized.
     */
    public static <T> boolean isInitialized(Reference<T> instance) {
        return cast(instance).initialized;
    }

    public static <T> void clear(Reference<T> instance) {
        doClear(cast(instance));
    }

    static <T> void doClear(Target_java_lang_ref_Reference<T> instance) {
        instance.rawReferent = null;
    }

    public static <T> boolean enqueue(Reference<T> instance) {
        return doEnqueue(cast(instance));
    }

    public static <T> boolean doEnqueue(Target_java_lang_ref_Reference<T> instance) {
        Target_java_lang_ref_ReferenceQueue<? super T> q = instance.futureQueue;
        if (q != null) {
            return ReferenceQueueInternals.doEnqueue(q, uncast(instance));
        }
        return false;
    }

    /** Barrier-less read of {@link Target_java_lang_ref_Reference#rawReferent} as pointer. */
    public static <T> Pointer getReferentPointer(Reference<T> instance) {
        return Word.objectToUntrackedPointer(ObjectAccess.readObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.rawReferentFieldOffset)));
    }

    /** Barrier-less write of {@link Target_java_lang_ref_Reference#rawReferent} as pointer. */
    public static <T> void setReferentPointer(Reference<T> instance, Pointer value) {
        ObjectAccess.writeObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.rawReferentFieldOffset), value.toObject());
    }

    public static <T> boolean isDiscovered(Reference<T> instance) {
        return cast(instance).isDiscovered;
    }

    /** Barrier-less read of {@link Target_java_lang_ref_Reference#nextDiscovered}. */
    public static <T> Reference<?> getNextDiscovered(Reference<T> instance) {
        return KnownIntrinsics.convertUnknownValue(ObjectAccess.readObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.nextDiscoveredFieldOffset)), Reference.class);
    }

    public static <T> void clearDiscovered(Reference<T> instance) {
        setNextDiscovered(instance, null, false);
    }

    public static <T> Reference<T> setNextDiscovered(Reference<T> instance, Reference<?> newNext) {
        setNextDiscovered(instance, newNext, true);
        return instance;
    }

    /** Barrier-less write of {@link Target_java_lang_ref_Reference#nextDiscovered}. */
    private static <T> void setNextDiscovered(Reference<T> instance, Reference<?> newNext, boolean newIsDiscovered) {
        ObjectAccess.writeObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.nextDiscoveredFieldOffset), newNext);
        cast(instance).isDiscovered = newIsDiscovered;
    }

    /** Address of field {@link Target_java_lang_ref_Reference#nextDiscovered} in the instance. */
    public static <T> Pointer getNextDiscoveredFieldPointer(Reference<T> instance) {
        return Word.objectToUntrackedPointer(instance).add(WordFactory.signed(Target_java_lang_ref_Reference.nextDiscoveredFieldOffset));
    }

    /**
     * Clears the queue on which the reference should eventually be enqueued and returns the
     * previous value, which may be {@code null} if this reference should not be put on a queue, but
     * also if the method has been called before -- such as, in a race to queue it.
     */
    @SuppressWarnings("unchecked")
    static <T> Target_java_lang_ref_ReferenceQueue<? super T> clearFutureQueue(Reference<T> instance) {
        return (Target_java_lang_ref_ReferenceQueue<? super T>) UNSAFE.getAndSetObject(instance, Target_java_lang_ref_Reference.futureQueueFieldOffset, null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> boolean isEnqueued(Reference<T> instance) {
        return cast(instance).nextInQueue != instance;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> Reference<?> getQueueNext(Reference<T> instance) {
        return cast(instance).nextInQueue;
    }

    static <T> void setQueueNext(Reference<T> instance, Reference<?> newNext) {
        assert newNext != instance : "Creating self-loop.";
        cast(instance).nextInQueue = newNext;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> void clearQueueNext(Reference<T> instance) {
        cast(instance).nextInQueue = instance;
    }

    private ReferenceInternals() {
    }
}
