/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * JDK 8 implementation of {@code TruffleJDKServices}.
 */
public class TruffleJDKServices {

    @SuppressWarnings("unused")
    public static void exportTo(ClassLoader loader, String moduleName) {
        // No need to do anything on JDK 8
    }

    @SuppressWarnings("unused")
    public static void exportTo(Class<?> client) {
        // No need to do anything on JDK 8
    }

    /**
     * Gets the ordered list of loaders for {@link TruffleRuntimeAccess} providers.
     */
    static List<Iterable<TruffleRuntimeAccess>> getTruffleRuntimeLoaders() {
        Iterable<TruffleRuntimeAccess> jvmciProviders = getJVMCIProviders();
        if (Boolean.getBoolean("truffle.TrustAllTruffleRuntimeProviders")) {
            ServiceLoader<TruffleRuntimeAccess> standardProviders = ServiceLoader.load(TruffleRuntimeAccess.class);
            return Arrays.asList(jvmciProviders, standardProviders);
        } else {
            return Collections.singletonList(jvmciProviders);
        }
    }

    /**
     * Gets the {@link TruffleRuntimeAccess} providers available on the JVMCI class path.
     */
    private static Iterable<TruffleRuntimeAccess> getJVMCIProviders() {
        ClassLoader cl = Truffle.class.getClassLoader();
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        while (cl != null) {
            if (cl == scl) {
                /*
                 * If Truffle can see the app class loader, then it is not on the JVMCI class path.
                 * This means providers of TruffleRuntimeAccess on the JVMCI class path must be
                 * ignored as they will bind to the copy of Truffle resolved on the JVMCI class
                 * path. Failing to ignore will result in ServiceConfigurationErrors (e.g.,
                 * https://github.com/oracle/graal/issues/385#issuecomment-385313521).
                 */
                return null;
            }
            cl = cl.getParent();
        }

        Class<?> servicesClass;
        try {
            // Access JVMCI via reflection to avoid a hard
            // dependency on JVMCI from Truffle.
            servicesClass = Class.forName("jdk.vm.ci.services.Services");
        } catch (ClassNotFoundException e) {
            // JVMCI is unavailable so the default TruffleRuntime will be used
            return null;
        }

        return reflectiveServiceLoaderLoad(servicesClass);
    }

    @SuppressWarnings("unchecked")
    private static Iterable<TruffleRuntimeAccess> reflectiveServiceLoaderLoad(Class<?> servicesClass) {
        try {
            Method m = servicesClass.getDeclaredMethod("load", Class.class);
            return (Iterable<TruffleRuntimeAccess>) m.invoke(null, TruffleRuntimeAccess.class);
        } catch (Throwable e) {
            throw (InternalError) new InternalError().initCause(e);
        }
    }
}
