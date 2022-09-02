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
package com.oracle.svm.util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

public final class ModuleSupport {
    private ModuleSupport() {
    }

    public static ResourceBundle getResourceBundle(String bundleName, Locale locale, ClassLoader loader) {
        return ResourceBundle.getBundle(bundleName, locale, loader);
    }

    /**
     * Gets all resources in the modules named by {@code modules} from the Java runtime image.
     */
    @SuppressWarnings("unused")
    public static List<String> getModuleResources(Collection<String> names) {
        /* Nothing to do in JDK 8 version. JDK 11 version provides a proper implementation. */
        assert JavaVersionUtil.JAVA_SPEC <= 8;
        return Collections.emptyList();
    }

    /**
     * Add the proper module opening to allow accesses from accessingClass to declaringClass.
     */
    @SuppressWarnings("unused")
    static void openModule(Class<?> declaringClass, Class<?> accessingClass) {
        /* Nothing to do in JDK 8 version. JDK 11 version provides a proper implementation. */
        assert JavaVersionUtil.JAVA_SPEC <= 8;
    }

    /**
     * Register the PlatformClassLoader.
     */
    @SuppressWarnings("unused")
    public static void registerPlatformClassLoader() {
        /* Nothing to do in JDK 8 version. JDK 11 version provides a proper implementation. */
        assert JavaVersionUtil.JAVA_SPEC <= 8;
    }

    public static ClassLoader getPlatformClassLoader() {
        return ClassLoader.getSystemClassLoader();
    }

    /**
     * Exports and opens all packages in the module named {@code name} to all unnamed modules.
     *
     * @param optional if {@code false} and there is no module named {@code name},
     *            {@link NoSuchElementException} is thrown
     */
    @SuppressWarnings("unused")
    public static void exportAndOpenAllPackagesToUnnamed(String name, boolean optional) {
        /* Nothing to do in JDK 8 version. JDK 11 version provides a proper implementation. */
        assert JavaVersionUtil.JAVA_SPEC <= 8;
    }

    /**
     * Gets the name of the module containing {@code clazz}.
     */
    @SuppressWarnings("unused")
    public static String getModuleName(Class<?> clazz) {
        assert JavaVersionUtil.JAVA_SPEC <= 8;
        return null;
    }
}
