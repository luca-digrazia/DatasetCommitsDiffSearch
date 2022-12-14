/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import static com.oracle.svm.core.posix.headers.Limits.MAXPATHLEN;
import static com.oracle.svm.core.posix.headers.Pwd.getpwuid;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.posix.headers.Pwd.passwd;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.Utsname;

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public abstract class PosixSystemPropertiesSupport extends SystemPropertiesSupport {

    /*
     * Initialization code is adapted from the JDK native code that initializes the system
     * properties, as found in src/solaris/native/java/lang/java_props_md.c
     */

    @Override
    protected String userNameValue() {
        passwd pwent = getpwuid(Unistd.getuid());
        return pwent.isNull() ? "?" : CTypeConversion.toJavaString(pwent.pw_name());
    }

    @Override
    protected String userHomeValue() {
        passwd pwent = getpwuid(Unistd.getuid());
        return pwent.isNull() ? "?" : CTypeConversion.toJavaString(pwent.pw_dir());
    }

    @Override
    protected String userDirValue() {
        int bufSize = MAXPATHLEN();
        CCharPointer buf = StackValue.get(bufSize);
        if (Unistd.getcwd(buf, WordFactory.unsigned(bufSize)).isNonNull()) {
            return CTypeConversion.toJavaString(buf);
        } else {
            throw new java.lang.Error("Properties init: Could not determine current working directory.");
        }
    }

    @Override
    protected String osVersionValue() {
        Utsname.utsname name = StackValue.get(Utsname.utsname.class);
        if (Utsname.uname(name) >= 0) {
            return CTypeConversion.toJavaString(name.release());
        }
        return "Unknown";
    }
}
