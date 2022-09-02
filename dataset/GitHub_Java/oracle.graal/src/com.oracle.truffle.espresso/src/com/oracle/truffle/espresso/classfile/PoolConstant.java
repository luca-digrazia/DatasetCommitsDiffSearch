/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;

/**
 * Base interface for entries in a constant pool.
 *
 * There is exactly one subclass representing the final or resolved version of a constant type. For
 * some constant types, there are one or more additional subclasses representing unresolved
 * version(s) of the constant. The process of resolving a constant will always result with relevant
 * constant pool slot being updated with the resolved version. That is, the constant pool will never
 * be updated with an unresolved version of a constant.
 */
public interface PoolConstant {

    Tag tag();

    String toString(ConstantPool pool);

    // TODO(garcia) call this function after parsing CP to verify each Pool constant is well-formed.

    /**
     * Throws VerifyError if the constant is ill-formed (/ex: a StringConstant does not refer to an
     * UTF8Constant
     * 
     * @param pool The constant pool in which this constant appears.
     */
    @SuppressWarnings("unused")
    default void checkValidity(ConstantPool pool) {
    }

    static byte u1(int i) {
        assert (byte) i == i;
        return (byte) i;
    }

    static char u2(int i) {
        assert (char) i == i;
        return (char) i;
    }
}
