/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * 2 aint with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.posix.headers.linux;

import com.oracle.svm.core.posix.headers.PosixDirectives;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CMacroInfo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

/**
 */
@CContext(PosixDirectives.class)
@Platforms({ Platform.LINUX.class})
public class LinuxSched {

    @CFunction
    public static native int sched_setaffinity(int pid, int cpu_set_size, cpu_set_t set_ptr);

    @CFunction
    public static native int sched_getaffinity(int pid, int cpu_set_size, cpu_set_t set_ptr);

    @CFunction
    static native int __sched_cpucount(int cpu_set_size, cpu_set_t set_ptr);

    @CMacroInfo("CPU_COUNT_S")
    public static int CPU_COUNT_S(int cpu_set_size, cpu_set_t set_ptr) {
        return __sched_cpucount(cpu_set_size, set_ptr);
    }

    @CStruct
    public interface cpu_set_t extends PointerBase {
    }
}
