/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Immutable constant pool implementation backed by an array of constants.
 */
final class ConstantPoolImpl extends ConstantPool {

    private final int majorVersion;
    private final int minorVersion;

    @CompilationFinal(dimensions = 1) //
    private final PoolConstant[] constants;

    private final byte[] rawBytes;

    ConstantPoolImpl(PoolConstant[] constants, int majorVersion, int minorVersion, byte[] rawBytes) {
        this.constants = Objects.requireNonNull(constants);
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.rawBytes = rawBytes;
    }

    @Override
    public int length() {
        return constants.length;
    }

    @Override
    public byte[] getRawBytes() {
        return rawBytes;
    }

    @Override
    public PoolConstant at(int index, String description) {
        try {
            return constants[index];
        } catch (IndexOutOfBoundsException exception) {
            throw ConstantPool.classFormatError("Constant pool index (" + index + ")" + (description == null ? "" : " for " + description) + " is out of range");
        }
    }

    @Override
    public int getMajorVersion() {
        return majorVersion;
    }

    @Override
    public int getMinorVersion() {
        return minorVersion;
    }
}
