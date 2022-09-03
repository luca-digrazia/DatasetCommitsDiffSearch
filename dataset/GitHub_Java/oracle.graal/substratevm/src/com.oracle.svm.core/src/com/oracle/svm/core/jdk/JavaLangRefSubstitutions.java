/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.ref.Reference;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.FeebleReference;
import com.oracle.svm.core.heap.FeebleReferenceList;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaField;

class ReferenceWrapper extends FeebleReference<Object> {
    protected final Object original;

    protected ReferenceWrapper(Object referent, final FeebleReferenceList<Object> list, Object original) {
        super(referent, list);
        this.original = original;
    }

    protected static Object unwrap(FeebleReference<? extends Object> wrapper) {
        return (wrapper == null ? null : ((ReferenceWrapper) wrapper).original);
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ComputeReferenceValue implements CustomFieldValueComputer {
    @Override
    public Object compute(ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return ((Reference<?>) receiver).get();
    }
}

@TargetClass(java.lang.ref.Reference.class)
@Substitute
final class Target_java_lang_ref_Reference {

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    protected final ReferenceWrapper feeble;

    /**
     * References that are in the native image are actually strong references, since we do not GC
     * the native image heap. Since creating a {@link FeebleReference} during native image
     * generation is not possible, we store the strong reference in a separate field.
     */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeReferenceValue.class)//
    protected final Object bootImageStrongValue;

    @Substitute
    protected Target_java_lang_ref_Reference(Object referent) {
        this(referent, null);
    }

    @Substitute
    protected Target_java_lang_ref_Reference(Object referent, Target_java_lang_ref_ReferenceQueue queue) {
        this.feeble = new ReferenceWrapper(referent, queue == null ? null : queue.feeble, this);
        this.bootImageStrongValue = null;
    }

    @Substitute
    public Object get() {
        if (feeble != null) {
            return feeble.get();
        } else {
            return bootImageStrongValue;
        }
    }

    @Substitute
    public void clear() {
        if (feeble != null) {
            feeble.clear();
        }
    }

    @Substitute
    @SuppressWarnings("unused")
    private static boolean tryHandlePending(boolean waitForNotify) {
        throw VMError.unimplemented();
    }
}

@TargetClass(java.lang.ref.ReferenceQueue.class)
@Substitute
final class Target_java_lang_ref_ReferenceQueue {

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = FeebleReferenceList.class)//
    protected final FeebleReferenceList<Object> feeble;

    @Substitute
    protected Target_java_lang_ref_ReferenceQueue() {
        this.feeble = FeebleReferenceList.factory();
    }

    @Substitute
    public Object poll() {
        return ReferenceWrapper.unwrap(feeble.pop());
    }

    @Substitute
    public Object remove() throws InterruptedException {
        if (VMOperation.isInProgress()) {
            throw new IllegalStateException("Calling ReferenceQueue.remove() inside a VMOperation would block.");
        }
        return ReferenceWrapper.unwrap(feeble.remove());
    }

    @Substitute
    public Object remove(long timeoutMillis) throws InterruptedException {
        if (VMOperation.isInProgress()) {
            throw new IllegalStateException("Calling ReferenceQueue.remove(long) inside a VMOperation would block.");
        }
        return ReferenceWrapper.unwrap(feeble.remove(timeoutMillis));
    }

    @KeepOriginal
    native boolean enqueue(Reference<?> r);
}

/** SubstrateVM does not support Finalizer references. */
@TargetClass(className = "java.lang.ref.Finalizer")
@Delete
final class Target_java_lang_ref_Finalizer {
}

/** SubstrateVM does not run a Finalizer thread. */
@TargetClass(className = "java.lang.ref.Finalizer", innerClass = "FinalizerThread")
@Delete
final class Target_java_lang_ref_Finalizer_FinalizerThread {
}

/** Dummy class to have a class with the file's name. */
public final class JavaLangRefSubstitutions {
}
