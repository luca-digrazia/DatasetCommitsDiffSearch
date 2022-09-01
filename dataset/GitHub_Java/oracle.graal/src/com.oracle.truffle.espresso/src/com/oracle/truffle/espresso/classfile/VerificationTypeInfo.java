/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.meta.EspressoError;

public abstract class VerificationTypeInfo {
    private int tag;

    VerificationTypeInfo(int tag) {
        this.tag = tag;
    }

    public int getTag() {
        return tag;
    }

    public int getNewOffset() {
        throw EspressoError.shouldNotReachHere("Asking for new offset of non Uninitialized variable verification_type_info");
    }

    public int getConstantPoolOffset() {
        throw EspressoError.shouldNotReachHere("Asking for CPI of non reference verification_type_info");
    }
}

class PrimitiveTypeInfo extends VerificationTypeInfo {
    public PrimitiveTypeInfo(int tag) {
        super(tag);
    }
}

class UninitializedThis extends VerificationTypeInfo {
    public UninitializedThis(int tag) {
        super(tag);
    }
}

class UninitializedVariable extends VerificationTypeInfo {
    private final int newOffset;

    public UninitializedVariable(int tag, int newOffset) {
        super(tag);
        this.newOffset = newOffset;
    }

    @Override
    public int getNewOffset() {
        return newOffset;
    }
}

class ReferenceVariable extends VerificationTypeInfo {
    private final int constantPoolOffset;

    public ReferenceVariable(int tag, int constantPoolOffset) {
        super(tag);
        this.constantPoolOffset = constantPoolOffset;
    }

    @Override
    public int getConstantPoolOffset() {
        return constantPoolOffset;
    }
}
