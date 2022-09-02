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
package com.oracle.svm.core.posix;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK11OrLater;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.DeprecatedPlatform;

@TargetClass(className = "java.net.AbstractPlainSocketImpl", onlyWith = JDK11OrLater.class)
@Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class})
final class Target_java_net_AbstractPlainSocketImpl_JDK11OrLater {
    // This class must not hide the "normal" Posix variant, and should be additive

    /* Do not re-format commented-out code: @formatter:off */
    // ported from: ./src/java.base/unix/native/libnet/SocketImpl.c
    //    32  JNIEXPORT jboolean JNICALL
    //    33  Java_java_net_AbstractPlainSocketImpl_isReusePortAvailable0(JNIEnv* env, jclass c1)
    @Substitute
    static boolean isReusePortAvailable0() {
        //    35      return (reuseport_available()) ? JNI_TRUE : JNI_FALSE;
        return JavaNetNetUtil.reuseport_available();
    }
    /* Do not re-format commented-out code: @formatter:on */
}
