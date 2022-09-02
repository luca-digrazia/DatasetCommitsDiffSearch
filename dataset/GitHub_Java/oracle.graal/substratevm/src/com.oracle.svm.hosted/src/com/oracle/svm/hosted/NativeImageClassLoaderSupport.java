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
package com.oracle.svm.hosted;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

import org.graalvm.compiler.options.OptionValues;

public class NativeImageClassLoaderSupport extends AbstractNativeImageClassLoaderSupport {

    NativeImageClassLoaderSupport(ClassLoader defaultSystemClassLoader, String[] classpath, @SuppressWarnings("unused") String[] modulePath) {
        super(defaultSystemClassLoader, classpath);
    }

    @Override
    public List<Path> modulepath() {
        return Collections.emptyList();
    }

    @Override
    List<Path> applicationModulePath() {
        return Collections.emptyList();
    }

    @Override
    public Optional<Object> findModule(String moduleName) {
        return Optional.empty();
    }

    @Override
    void processAddExportsAndAddOpens(OptionValues parsedHostedOptions) {
        /* Nothing to do for Java 8 */
    }

    @Override
    Class<?> loadClassFromModule(Object module, String className) throws ClassNotFoundException {
        if (module != null) {
            throw new ClassNotFoundException(className,
                            new UnsupportedOperationException("NativeImageClassLoader for Java 8 does not support modules"));
        }
        return Class.forName(className, false, classPathClassLoader);
    }

    @Override
    public void initAllClasses(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
        new ClassInit(executor, imageClassLoader, this).init();
    }
}
