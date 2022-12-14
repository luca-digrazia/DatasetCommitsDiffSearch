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
package com.oracle.svm.core.heap;

import java.lang.ref.Reference;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

public final class ReferenceHandler {
    @Fold
    static boolean useDedicatedThread() {
        return SubstrateOptions.UseReferenceHandlerThread.getValue() && SubstrateOptions.MultiThreaded.getValue();
    }

    public static void maybeProcessCurrentlyPending() {
        if (!useDedicatedThread()) {
            ThreadingSupportImpl.pauseRecurringCallback("An exception in a recurring callback must not interrupt pending reference processing because it could result in a memory leak.");
            try {
                ReferenceInternals.processPendingReferences();
                processCleaners();
            } catch (StackOverflowError | OutOfMemoryError e) {
                throw e;
            } catch (Throwable t) {
                VMError.shouldNotReachHere("Reference processing and cleaners must handle all potential exceptions", t);
            } finally {
                ThreadingSupportImpl.resumeRecurringCallback();
            }
        }
    }

    static void processCleaners() {
        // Note: (sun.misc|jdk.internal).Cleaner objects are invoked in pending reference processing

        if (JavaVersionUtil.JAVA_SPEC > 8) {
            // Process the JDK's common cleaner, additional cleaners start their own threads
            Target_java_lang_ref_Cleaner commonCleaner = Target_jdk_internal_ref_CleanerFactory.cleaner();
            Reference<?> ref = commonCleaner.impl.queue.poll();
            while (ref != null) {
                try {
                    Target_java_lang_ref_Cleaner_Cleanable cl = SubstrateUtil.cast(ref, Target_java_lang_ref_Cleaner_Cleanable.class);
                    cl.clean();
                } catch (Throwable e) {
                    // ignore exceptions from the cleanup action and thread interrupts
                }
                ref = commonCleaner.impl.queue.poll();
            }
        }
    }

    private ReferenceHandler() {
    }
}

final class ReferenceHandlerRunnable implements Runnable {
    @Override
    public void run() {
        /*
         * Precaution: this thread does not register a callback itself, but a subclass of Reference,
         * ReferenceQueue, or a Cleaner or Cleanable might do strange things.
         */
        ThreadingSupportImpl.pauseRecurringCallback("An exception in a recurring callback must not interrupt pending reference processing because it could result in a memory leak.");
        try {
            while (true) {
                ReferenceInternals.waitForPendingReferences();
                ReferenceInternals.processPendingReferences();
                ReferenceHandler.processCleaners();
            }
        } catch (InterruptedException e) {
            VMError.guarantee(VMThreads.isTearingDown(), "Reference Handler should only be interrupted during tear-down");
        } catch (Throwable t) {
            VMError.shouldNotReachHere("Reference processing and cleaners must handle all potential exceptions", t);
        } finally {
            ThreadingSupportImpl.resumeRecurringCallback();
        }
    }
}

@AutomaticFeature
class ReferenceHandlerThreadFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ReferenceHandler.useDedicatedThread();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        Thread thread = new Thread(new ReferenceHandlerRunnable(), "Reference Handler");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.setDaemon(true);
        RuntimeSupport.getRuntimeSupport().addInitializationHook(thread::start);
    }
}
