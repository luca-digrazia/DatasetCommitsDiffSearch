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

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.util.zip.ZipFile", innerClass = "CleanableResource", onlyWith = JDK11OrLater.class)
public final class Target_java_util_zip_ZipFile_CleanableResource {

    @SuppressWarnings({"unused"})
    @Alias
    Target_java_util_zip_ZipFile_CleanableResource(ZipFile zf, File file, int mode) throws IOException {
    }

    @Substitute
    static Target_java_util_zip_ZipFile_CleanableResource get(ZipFile zf, File file, int mode)
                    throws IOException {
        /* Skip FinalizableResource handling - SubstrateVM does not support Finalizers */
        return new Target_java_util_zip_ZipFile_CleanableResource(zf, file, mode);
    }
}
