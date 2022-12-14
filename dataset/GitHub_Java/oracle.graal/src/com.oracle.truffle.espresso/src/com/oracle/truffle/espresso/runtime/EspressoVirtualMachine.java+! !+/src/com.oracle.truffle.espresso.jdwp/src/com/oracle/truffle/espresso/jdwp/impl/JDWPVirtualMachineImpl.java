/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.jdwp.api.JDWPVirtualMachine;

public class EspressoVirtualMachine implements JDWPVirtualMachine {
    public static final int SIZE = 8;

    public static final String VM_Description = "Espresso 64-Bit VM";
    public static final String vmVersion = System.getProperty("java.version");
    public static final String vmName = "Espresso 64-Bit VM";

    public int getSizeOfFieldRef() {
        return SIZE;
    }

    public int getSizeOfMethodRef() {
        return SIZE;
    }

    public int getSizeofObjectRef() {
        return SIZE;
    }

    public int getSizeOfClassRef() {
        return SIZE;
    }

    public int getSizeOfFrameRef() {
        return SIZE;
    }

    @Override
    public String getVmDescription() {
        return VM_Description;
    }

    @Override
    public String getVmVersion() {
        return vmVersion;
    }

    @Override
    public String getVmName() {
        return vmName;
    }
}
