/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso;

import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.substitutions.HostJavaVersionUtil;
import com.oracle.truffle.espresso.vm.UnsafeAccess;
import org.graalvm.nativeimage.hosted.Feature;
import sun.misc.Unsafe;

import java.lang.ref.PublicFinalReference;
import java.lang.reflect.InvocationTargetException;
import java.security.ProtectionDomain;

/**
 * Support for finalizers (FinalReference) in Espresso.
 * 
 * <p>
 * Espresso implements non-strong references e.g. {@link java.lang.ref.WeakReference} by using the
 * host equivalents, inheriting the same semantics and behavior as the host VM.
 * 
 * Since FinalReference is package private, Espresso injects {@link PublicFinalReference} in the
 * boot class loader of the host VM to open the hierarchy. This mechanism is very fragile, but
 * allows Espresso to share the same implementation for HotSpot and SubstrateVM.
 */
public final class FinalizationFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ensureInitialized();
    }

    static final Class<?> PUBLIC_FINAL_REFERENCE;

    /**
     * Compiled {@link java.lang.ref.PublicFinalReference} without the poisoned static initializer.
     */
    private static final byte[] PUBLIC_FINAL_REFERENCE_BYTES = new byte[]{-54, -2, -70, -66, 0, 0, 0, 52, 0, 28, 7, 0, 2, 1, 0, 34, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 114, 101, 102, 47, 80,
                    117, 98, 108, 105, 99, 70, 105, 110, 97, 108, 82, 101, 102, 101, 114, 101, 110, 99, 101, 7, 0, 4, 1, 0, 28, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 114, 101, 102, 47, 70, 105,
                    110, 97, 108, 82, 101, 102, 101, 114, 101, 110, 99, 101, 1, 0, 6, 60, 105, 110, 105, 116, 62, 1, 0, 51, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99,
                    116, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 114, 101, 102, 47, 82, 101, 102, 101, 114, 101, 110, 99, 101, 81, 117, 101, 117, 101, 59, 41, 86, 1, 0, 9, 83, 105, 103,
                    110, 97, 116, 117, 114, 101, 1, 0, 42, 40, 84, 84, 59, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 114, 101, 102, 47, 82, 101, 102, 101, 114, 101, 110, 99, 101, 81, 117, 101,
                    117, 101, 60, 45, 84, 84, 59, 62, 59, 41, 86, 1, 0, 4, 67, 111, 100, 101, 10, 0, 3, 0, 11, 12, 0, 5, 0, 6, 1, 0, 15, 76, 105, 110, 101, 78, 117, 109, 98, 101, 114, 84, 97, 98, 108,
                    101, 1, 0, 18, 76, 111, 99, 97, 108, 86, 97, 114, 105, 97, 98, 108, 101, 84, 97, 98, 108, 101, 1, 0, 4, 116, 104, 105, 115, 1, 0, 36, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103,
                    47, 114, 101, 102, 47, 80, 117, 98, 108, 105, 99, 70, 105, 110, 97, 108, 82, 101, 102, 101, 114, 101, 110, 99, 101, 59, 1, 0, 8, 114, 101, 102, 101, 114, 101, 110, 116, 1, 0, 18,
                    76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 59, 1, 0, 5, 113, 117, 101, 117, 101, 1, 0, 30, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 114,
                    101, 102, 47, 82, 101, 102, 101, 114, 101, 110, 99, 101, 81, 117, 101, 117, 101, 59, 1, 0, 22, 76, 111, 99, 97, 108, 86, 97, 114, 105, 97, 98, 108, 101, 84, 121, 112, 101, 84, 97,
                    98, 108, 101, 1, 0, 41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 114, 101, 102, 47, 80, 117, 98, 108, 105, 99, 70, 105, 110, 97, 108, 82, 101, 102, 101, 114, 101, 110, 99,
                    101, 60, 84, 84, 59, 62, 59, 1, 0, 3, 84, 84, 59, 1, 0, 36, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 114, 101, 102, 47, 82, 101, 102, 101, 114, 101, 110, 99, 101, 81, 117,
                    101, 117, 101, 60, 45, 84, 84, 59, 62, 59, 1, 0, 16, 77, 101, 116, 104, 111, 100, 80, 97, 114, 97, 109, 101, 116, 101, 114, 115, 1, 0, 10, 83, 111, 117, 114, 99, 101, 70, 105, 108,
                    101, 1, 0, 25, 80, 117, 98, 108, 105, 99, 70, 105, 110, 97, 108, 82, 101, 102, 101, 114, 101, 110, 99, 101, 46, 106, 97, 118, 97, 1, 0, 57, 60, 84, 58, 76, 106, 97, 118, 97, 47,
                    108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 59, 62, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 114, 101, 102, 47, 70, 105, 110, 97, 108, 82, 101, 102, 101, 114, 101,
                    110, 99, 101, 60, 84, 84, 59, 62, 59, 4, 33, 0, 1, 0, 3, 0, 0, 0, 0, 0, 1, 0, 1, 0, 5, 0, 6, 0, 3, 0, 7, 0, 0, 0, 2, 0, 8, 0, 9, 0, 0, 0, 111, 0, 3, 0, 3, 0, 0, 0, 7, 42, 43, 44,
                    -73, 0, 10, -79, 0, 0, 0, 3, 0, 12, 0, 0, 0, 10, 0, 2, 0, 0, 0, 51, 0, 6, 0, 52, 0, 13, 0, 0, 0, 32, 0, 3, 0, 0, 0, 7, 0, 14, 0, 15, 0, 0, 0, 0, 0, 7, 0, 16, 0, 17, 0, 1, 0, 0, 0,
                    7, 0, 18, 0, 19, 0, 2, 0, 20, 0, 0, 0, 32, 0, 3, 0, 0, 0, 7, 0, 14, 0, 21, 0, 0, 0, 0, 0, 7, 0, 16, 0, 22, 0, 1, 0, 0, 0, 7, 0, 18, 0, 23, 0, 2, 0, 24, 0, 0, 0, 9, 2, 0, 16, 0, 0,
                    0, 18, 0, 0, 0, 2, 0, 25, 0, 0, 0, 2, 0, 26, 0, 7, 0, 0, 0, 2, 0, 27};

    static {
        PUBLIC_FINAL_REFERENCE = injectClassInBootClassLoader("java/lang/ref/PublicFinalReference", PUBLIC_FINAL_REFERENCE_BYTES);
    }

    public static void ensureInitialized() {
        /* nop */
    }

    /**
     * Inject raw class in the host boot class loader.
     */
    private static Class<?> injectClassInBootClassLoader(String className, byte[] classBytes) {
        EspressoError.guarantee(HostJavaVersionUtil.JAVA_SPEC == 8 || HostJavaVersionUtil.JAVA_SPEC == 11, "Unsupported host Java version: {}", HostJavaVersionUtil.JAVA_SPEC);
        if (HostJavaVersionUtil.JAVA_SPEC == 8) {
            // Inject class via sun.misc.Unsafe#defineClass.
            // The use of reflection here is deliberate, so the code compiles with both Java 8/11.
            try {
                java.lang.reflect.Method defineClass = Unsafe.class.getDeclaredMethod("defineClass",
                                String.class, byte[].class, int.class, int.class, ClassLoader.class, ProtectionDomain.class);
                defineClass.setAccessible(true);
                return (Class<?>) defineClass.invoke(UnsafeAccess.get(), className, classBytes, 0, classBytes.length, null, null);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        } else if (HostJavaVersionUtil.JAVA_SPEC >= 11 /* removal of sun.misc.Unsafe#defineClass */) {
            // Inject class via j.l.ClassLoader#defineClass1.
            try {
                java.lang.reflect.Method defineClass1 = ClassLoader.class.getDeclaredMethod("defineClass1",
                                ClassLoader.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class, String.class);
                defineClass1.setAccessible(true);
                return (Class<?>) defineClass1.invoke(null, null, className, classBytes, 0, classBytes.length, null, null);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        } else {
            throw EspressoError.shouldNotReachHere("Java version not supported: " + HostJavaVersionUtil.JAVA_SPEC);
        }
    }
}
