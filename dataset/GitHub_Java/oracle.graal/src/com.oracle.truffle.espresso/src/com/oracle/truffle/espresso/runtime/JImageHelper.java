/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime;

import java.io.File;

import com.oracle.truffle.api.interop.TruffleObject;

/**
 * 
 */
public class JImageHelper implements ModulesReaderHelper {

    // pointer to native JImageFile
    private final TruffleObject jimage;
    private final JImageLibrary library;

    JImageHelper(File file, EspressoContext context) {
        this.library = context.jimageLibrary();
        jimage = library.init(file.getPath());
    }

    @Override
    public void close() {
        library.close(jimage);
    }

    @Override
    public byte[] getClassBytes(String moduleName, String name) {
        return library.getClassBytes(jimage, moduleName, name);
    }

    @Override
    public String packageToModule(String packageName) {
        if (!library.getContext().modulesInitialized()) {
            return JAVA_BASE;
        }
        return library.packageToModule(jimage, packageName);
    }
}
