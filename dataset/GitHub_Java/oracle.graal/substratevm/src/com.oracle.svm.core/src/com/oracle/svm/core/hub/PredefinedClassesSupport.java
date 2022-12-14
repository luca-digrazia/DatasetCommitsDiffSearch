/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.core.hub;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

public final class PredefinedClassesSupport {

    @Fold
    static PredefinedClassesSupport singleton() {
        return ImageSingletons.lookup(PredefinedClassesSupport.class);
    }

    public static String hash(byte[] classData, int offset, int length) {
        try { // Only for lookups, cryptographic properties are irrelevant
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(classData, offset, length);
            return SubstrateUtil.toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Set<Class<?>> predefinedClasses = new HashSet<>();

    private final ReentrantLock lock = new ReentrantLock();

    /** Predefined classes by hash. */
    private final EconomicMap<String, Class<?>> predefinedClassesByName = ImageHeapMap.create();

    /** Predefined classes which have already been loaded, by name. */
    private final EconomicMap<String, Class<?>> loadedClassesByName = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerClass(String hash, Class<?> clazz) {
        Class<?> existing = singleton().predefinedClassesByName.putIfAbsent(hash, clazz);
        VMError.guarantee(existing == null, "Can define only one class per hash");
        singleton().predefinedClasses.add(clazz);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean isPredefined(Class<?> clazz) {
        return singleton().predefinedClasses.contains(clazz);
    }

    public static Class<?> loadClass(ClassLoader classLoader, String expectedName, byte[] data, int offset, int length, ProtectionDomain protectionDomain) {
        String hash = hash(data, offset, length);
        Class<?> clazz = singleton().predefinedClassesByName.get(hash);
        if (clazz == null) {
            String name = (expectedName != null) ? expectedName : "(name not specified)";
            throw VMError.unsupportedFeature("Defining a class from new bytecodes at run time is not supported. Class " + name +
                            " with hash " + hash + " was not provided during the image build. Please see BuildConfiguration.md.");
        }
        return singleton().load(classLoader, protectionDomain, clazz);
    }

    private Class<?> load(ClassLoader classLoader, ProtectionDomain protectionDomain, Class<?> clazz) {
        lock.lock();
        try {
            boolean alreadyLoaded = singleton().loadedClassesByName.putIfAbsent(clazz.getName(), clazz) != null;
            if (alreadyLoaded) {
                if (classLoader == clazz.getClassLoader()) {
                    throw new LinkageError("loader " + classLoader + " attempted duplicate class definition for " + clazz.getName() + " defined by " + clazz.getClassLoader());
                } else {
                    throw VMError.unsupportedFeature("A predefined class can be loaded (defined) at runtime only once by a single class loader. " +
                                    "Hierarchies of class loaders and distinct sets of classes are not supported. Class " + clazz.getName() + " has already " +
                                    "been loaded by class loader: " + clazz.getClassLoader());
                }
            }
            /*
             * The following is part of the locked block so that other threads can observe only the
             * initialized values once the class can be found.
             */
            DynamicHub hub = DynamicHub.fromClass(clazz);
            hub.setClassLoaderAtRuntime(classLoader);
            if (protectionDomain != null) {
                hub.setProtectionDomainAtRuntime(protectionDomain);
            }
            return clazz;
        } finally {
            lock.unlock();
        }
    }

    static Class<?> getLoadedForNameOrNull(String name, ClassLoader classLoader) {
        Class<?> clazz = singleton().getLoaded(name);
        if (clazz == null || !isSameOrParent(clazz.getClassLoader(), classLoader)) {
            return null;
        }
        return clazz;
    }

    private Class<?> getLoaded(String name) {
        lock.lock();
        try {
            return loadedClassesByName.get(name);
        } finally {
            lock.unlock();
        }
    }

    private static boolean isSameOrParent(ClassLoader parent, ClassLoader child) {
        if (parent == null) {
            return true; // boot loader: any loader's parent
        }
        ClassLoader c = child;
        do {
            if (c == parent) {
                return true;
            }
            c = c.getParent();
        } while (c != null);
        return false;
    }

    public static class TestingBackdoor {
        public static UnmodifiableEconomicMap<String, Class<?>> getPredefinedClasses() {
            return singleton().predefinedClassesByName;
        }
    }
}

@AutomaticFeature
final class ClassDefinitionFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PredefinedClassesSupport.class, new PredefinedClassesSupport());
    }
}
