/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import org.graalvm.compiler.core.common.NumUtil;

import com.oracle.svm.core.annotate.DuplicatedInNativeCode;

@DuplicatedInNativeCode
public enum ReferenceType {
    None(0),     // non-reference class
    Other(1),    // Subclass of Reference, but not a subclass of one of the classes below
    Soft(2),     // Subclass of SoftReference
    Weak(3),     // Subclass of WeakReference
    Unused(4),
    Phantom(5);  // Subclass of PhantomReference

    private final byte value;

    ReferenceType(int value) {
        this.value = NumUtil.safeToByte(value);
    }

    public byte getValue() {
        return value;
    }
}
