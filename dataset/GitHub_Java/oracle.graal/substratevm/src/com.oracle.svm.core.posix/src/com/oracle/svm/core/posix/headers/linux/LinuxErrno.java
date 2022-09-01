/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers.linux;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CIntPointer;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;

//Checkstyle: stop

@Platforms(Platform.LINUX_AND_JNI.class)
class LinuxErrno {

    @TargetClass(com.oracle.svm.core.posix.headers.Errno.class)
    static final class Target_com_oracle_svm_core_posix_headers_Errno {

        @Substitute
        @Uninterruptible(reason = "Called from uninterruptible code.")
        private static int errno() {
            return Util_com_oracle_svm_core_posix_headers_Errno.__errno_location().read();
        }

        @Substitute
        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static void set_errno(int value) {
            Util_com_oracle_svm_core_posix_headers_Errno.__errno_location().write(value);
        }
    }

    static final class Util_com_oracle_svm_core_posix_headers_Errno {
        @CFunction(transition = CFunction.Transition.NO_TRANSITION)
        static native CIntPointer __errno_location();
    }
}
