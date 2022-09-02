/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jni.JNIRuntimeAccess;

/**
 * Registration of classes, methods, and fields accessed via JNI by C code of the JDK.
 */
@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticFeature
class JNIRegistrationJava extends JNIRegistrationUtil implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess a) {
        rerunClassInit(a, "java.io.RandomAccessFile", "java.lang.ProcessEnvironment");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        /*
         * It is difficult to track down all the places where exceptions are thrown via JNI. And
         * unconditional registration is cheap. Therefore, we register them unconditionally.
         */
        registerForThrowNew(a, "java.lang.Exception", "java.lang.Error", "java.lang.OutOfMemoryError",
                        "java.lang.RuntimeException", "java.lang.NullPointerException", "java.lang.ArrayIndexOutOfBoundsException",
                        "java.lang.IllegalArgumentException", "java.lang.IllegalAccessException", "java.lang.IllegalAccessError", "java.lang.InternalError",
                        "java.lang.NoSuchFieldException", "java.lang.NoSuchMethodException", "java.lang.ClassNotFoundException", "java.lang.NumberFormatException",
                        "java.lang.NoSuchFieldError", "java.lang.NoSuchMethodError", "java.lang.UnsatisfiedLinkError", "java.lang.StringIndexOutOfBoundsException",
                        "java.lang.InstantiationException", "java.lang.UnsupportedOperationException",
                        "java.io.IOException", "java.io.FileNotFoundException", "java.io.SyncFailedException", "java.io.InterruptedIOException",
                        "java.util.zip.DataFormatException");
        JNIRuntimeAccess.register(constructor(a, "java.io.FileNotFoundException", String.class, String.class));

        /* Unconditional Integer and Boolean JNI registration (cheap) */
        JNIRuntimeAccess.register(clazz(a, "java.lang.Integer"));
        JNIRuntimeAccess.register(constructor(a, "java.lang.Integer", int.class));
        JNIRuntimeAccess.register(fields(a, "java.lang.Integer", "value"));
        JNIRuntimeAccess.register(clazz(a, "java.lang.Boolean"));
        JNIRuntimeAccess.register(constructor(a, "java.lang.Boolean", boolean.class));
        JNIRuntimeAccess.register(method(a, "java.lang.Boolean", "getBoolean", String.class));

        /*
         * Core JDK elements accessed from many places all around the JDK. They can be registered
         * unconditionally.
         */

        JNIRuntimeAccess.register(java.io.FileDescriptor.class);
        JNIRuntimeAccess.register(fields(a, "java.io.FileDescriptor", "fd"));
        if (isWindows()) {
            JNIRuntimeAccess.register(fields(a, "java.io.FileDescriptor", "handle"));
        }
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            JNIRuntimeAccess.register(fields(a, "java.io.FileDescriptor", "append"));
        }

        // TODO classify the remaining registrations

        JNIRuntimeAccess.register(byte[].class); /* used by ProcessEnvironment.environ() */

        JNIRuntimeAccess.register(java.lang.String.class);
        JNIRuntimeAccess.register(java.lang.System.class);
        JNIRuntimeAccess.register(method(a, "java.lang.System", "getProperty", String.class));
        JNIRuntimeAccess.register(java.nio.charset.Charset.class);
        JNIRuntimeAccess.register(method(a, "java.nio.charset.Charset", "isSupported", String.class));
        JNIRuntimeAccess.register(constructor(a, "java.lang.String", byte[].class, String.class));
        JNIRuntimeAccess.register(method(a, "java.lang.String", "getBytes", String.class));

        JNIRuntimeAccess.register(java.io.File.class);
        JNIRuntimeAccess.register(fields(a, "java.io.File", "path"));

        a.registerReachabilityHandler(JNIRegistrationJava::registerFileOutputStreamInitIDs, method(a, "java.io.FileOutputStream", "initIDs"));
        a.registerReachabilityHandler(JNIRegistrationJava::registerFileInputStreamInitIDs, method(a, "java.io.FileInputStream", "initIDs"));
        a.registerReachabilityHandler(JNIRegistrationJava::registerRandomAccessFileInitIDs, method(a, "java.io.RandomAccessFile", "initIDs"));

        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            JNIRuntimeAccess.register(fields(a, "java.util.zip.Inflater", "inputConsumed", "outputConsumed"));
        }
    }

    private static void registerFileOutputStreamInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.io.FileOutputStream", "fd"));
    }

    private static void registerFileInputStreamInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.io.FileInputStream", "fd"));
    }

    private static void registerRandomAccessFileInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.io.RandomAccessFile", "fd"));
    }
}
