/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.ffi.amd64;

import com.oracle.graal.api.code.*;

public class AMD64NativeLibraryHandle implements NativeLibraryHandle {

    private final long handle;
    // The native side (graalCompilerToVM.cpp) sets the rtldDefault handle to 0xDEADFACE if the
    // platform is Windows.
    // AMD64NativeFunctionInterface checks if rtld_default handle is valid.
    // Windows is currently not supported.
    // Using 0 is not possible as it is a valid value for rtldDefault on some platforms.
    public static final long INVALID_RTLD_DEFAULT_HANDLE = 0xDEADFACE;

    public AMD64NativeLibraryHandle(long handle) {
        this.handle = handle;
    }

    public long asRawValue() {
        return handle;
    }

    public boolean isValid() {
        return handle != 0;
    }
}
