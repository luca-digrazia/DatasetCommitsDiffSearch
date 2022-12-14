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
package com.oracle.graal.hotspot;

import java.util.*;

import com.oracle.graal.api.code.stack.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;

public class HotSpotStackFrameReference implements InspectedFrame {

    private CompilerToVM compilerToVM;

    // information used to find the stack frame
    private long stackPointer;
    private int frameNumber;

    // information about the stack frame's contents
    private int bci;
    private long metaspaceMethod;
    private Object[] locals;
    private boolean[] localIsVirtual;

    public long getStackPointer() {
        return stackPointer;
    }

    public int getFrameNumber() {
        return frameNumber;
    }

    public Object getLocal(int index) {
        return locals[index];
    }

    public boolean isVirtual(int index) {
        return localIsVirtual == null ? false : localIsVirtual[index];
    }

    public void materializeVirtualObjects(boolean invalidateCode) {
        compilerToVM.materializeVirtualObjects(this, invalidateCode);
    }

    public int getBytecodeIndex() {
        return bci;
    }

    public ResolvedJavaMethod getMethod() {
        return HotSpotResolvedJavaMethod.fromMetaspace(metaspaceMethod);
    }

    public boolean hasVirtualObjects() {
        return localIsVirtual != null;
    }

    @Override
    public String toString() {
        return "HotSpotStackFrameReference [stackPointer=" + stackPointer + ", frameNumber=" + frameNumber + ", bci=" + bci + ", method=" + getMethod() + ", locals=" + Arrays.toString(locals) +
                        ", localIsVirtual=" + Arrays.toString(localIsVirtual) + "]";
    }
}
