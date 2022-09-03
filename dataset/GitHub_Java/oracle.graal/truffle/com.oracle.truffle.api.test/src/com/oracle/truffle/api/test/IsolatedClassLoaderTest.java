/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IsolatedClassLoaderTest {
    private static final boolean JDK8 = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    @Test
    public void loadLanguageByOwnClassLoaderOnJDK8() throws Exception {
        if (!JDK8) {
            return;
        }
        List<URL> arr = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File cpEntry = new File(entry);
            arr.add(cpEntry.toURI().toURL());
        }
        final ClassLoader testLoader = IsolatedClassLoaderTest.class.getClassLoader();
        final ClassLoader parentLoader = testLoader.getParent();
        ClassLoader loader = new URLClassLoader(arr.toArray(new URL[0]), parentLoader) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith("com.oracle.truffle.api")) {
                    return super.findClass(name);
                }
                return super.loadClass(name);
            }
        };
        Class<?> locator = loader.loadClass("com.oracle.truffle.api.impl.TruffleLocator");
        assertEquals("Right classloader", loader, locator.getClassLoader());

        final Method loadersMethod = locator.getDeclaredMethod("loaders");
        loadersMethod.setAccessible(true);
        Set<?> loaders = (Set<?>) loadersMethod.invoke(null);
        assertTrue("Contains locator's loader: " + loaders, loaders.contains(loader));
        assertTrue("Contains system loader: " + loader, loaders.contains(ClassLoader.getSystemClassLoader()));
    }
}
