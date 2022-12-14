/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.nfi;

import com.oracle.nfi.api.*;

/**
 * Class for obtaining the Native Function Interface runtime singleton object of this virtual
 * machine.
 */
public final class NativeFunctionInterfaceRuntime {
    private static final NativeFunctionInterface INTERFACE;

    /**
     * Creates a new {@link NativeFunctionInterface} instance if running on top of Graal.
     *
     * @throws UnsatisfiedLinkError if not running on top of Graal and initialize the
     *             {@link NativeFunctionInterface} instance with <code>null</code>.
     */
    private static native NativeFunctionInterface createInterface();

    public static NativeFunctionInterface getNativeFunctionInterface() {
        return INTERFACE;
    }

    static {
        NativeFunctionInterface instance;
        try {
            instance = createInterface();
        } catch (UnsatisfiedLinkError e) {
            instance = null;
        }
        INTERFACE = instance;
    }
}
