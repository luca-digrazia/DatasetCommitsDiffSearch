/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.RawBufferLog;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.VMThreads;

@TargetClass(com.oracle.svm.core.util.VMError.class)
final class Target_com_oracle_svm_core_util_VMError {

    /*
     * These substitutions let the svm print the real message. The original VMError methods throw a
     * VMError, which let the svm just print the type name of VMError.
     */

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.", mayBeInlined = true)
    @Substitute
    private static RuntimeException shouldNotReachHere() {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), null, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.", mayBeInlined = true)
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg) {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), msg, null);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.", mayBeInlined = true)
    @Substitute
    private static RuntimeException shouldNotReachHere(Throwable ex) {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), null, ex);
    }

    @NeverInline("Accessing instruction pointer of the caller frame")
    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg, Throwable ex) {
        throw VMErrorSubstitutions.shouldNotReachHere(KnownIntrinsics.readReturnAddress(), msg, ex);
    }

    @Substitute
    private static RuntimeException unimplemented() {
        return unsupportedFeature("unimplemented");
    }

    @Substitute
    private static RuntimeException unsupportedFeature(String msg) {
        throw new UnsupportedFeatureError(msg);
    }
}

/** Dummy class to have a class with the file's name. */
public class VMErrorSubstitutions {

    private static final RawBufferLog fatalContextMessageWriter = new RawBufferLog();

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.")
    static RuntimeException shouldNotReachHere(CodePointer callerIP, String msg, Throwable ex) {
        ThreadStackPrinter.printBacktrace();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();
        VMErrorSubstitutions.shutdown(callerIP, msg, ex);
        return null;
    }

    @Uninterruptible(reason = "Allow use in uninterruptible code.", calleeMustBe = false)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
    static void shutdown(CodePointer callerIP, String msg, Throwable ex) {
        doShutdown(callerIP, msg, ex);
    }

    @NeverInline("Uses memory allocation in the stack frame.")
    private static boolean fatalContext(CodePointer callerIP, String msg, Throwable ex) {
        String fatalErrorMsg = msg != null ? msg : "Unknown Fatal Error";
        String exceptionClassName = null;
        String exceptionMessage = null;
        if (ex != null) {
            exceptionClassName = ex.getClass().getName();
            exceptionMessage = JDKUtils.getRawMessage(ex);
        }

        CCharPointer fatalMessageBytes = StackValue.get(512);
        fatalContextMessageWriter.setRawBuffer(fatalMessageBytes, 512);

        fatalContextMessageWriter.string("ip:");
        fatalContextMessageWriter.hex(callerIP);
        fatalContextMessageWriter.character('|');
        fatalContextMessageWriter.string(fatalErrorMsg);
        if (exceptionClassName != null) {
            fatalContextMessageWriter.character('|');
            fatalContextMessageWriter.string(exceptionClassName);
            if (exceptionMessage != null) {
                fatalContextMessageWriter.character('|');
                fatalContextMessageWriter.string(exceptionMessage);
            }
        }

        return ImageSingletons.lookup(LogHandler.class).fatalContext(fatalMessageBytes, WordFactory.unsigned(fatalContextMessageWriter.getRawBufferPos()));
    }

    @NeverInline("Starting a stack walk in the caller frame")
    private static void doShutdown(CodePointer callerIP, String msg, Throwable ex) {
        try {
            if (fatalContext(callerIP, msg, ex)) {
                Log log = Log.log();
                log.autoflush(true);

                /*
                 * Print the error message. If the diagnostic output fails, at least we printed the
                 * most important bit of information.
                 */
                log.string("Fatal error");
                if (msg != null) {
                    log.string(": ").string(msg);
                }
                if (ex != null) {
                    log.string(": ").exception(ex);
                } else {
                    log.newline();
                }

                SubstrateUtil.printDiagnostics(log, KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress());

                /*
                 * Print the error message again, so that the most important bit of information
                 * shows up as the last line (which is probably what users look at first).
                 */
                log.string("Fatal error");
                if (msg != null) {
                    log.string(": ").string(msg);
                }
                if (ex != null) {
                    log.string(": ").string(ex.getClass().getName()).string(": ").string(JDKUtils.getRawMessage(ex));
                }
                log.newline();
            }
        } catch (Throwable ignored) {
            /* Ignore exceptions reported during error reporting, we are going to exit anyway. */
        }
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }
}
