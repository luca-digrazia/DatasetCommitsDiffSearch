/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.persist;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.model.ComponentInfo;

public interface MetadataLoader {

    void close() throws IOException;

    ComponentInfo getComponentInfo();

    List<InstallerStopException> getErrors();

    JarFile getJarFile();

    String getLicensePath();

    MetadataLoader infoOnly(boolean only);

    boolean isNoVerifySymlinks();

    void loadPaths();

    Map<String, String> loadPermissions() throws IOException;

    Map<String, String> loadSymlinks() throws IOException;

    void setNoVerifySymlinks(boolean noVerifySymlinks);

}
