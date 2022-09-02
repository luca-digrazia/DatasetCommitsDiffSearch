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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class ParserField {

    public static final ParserField[] EMPTY_ARRAY = new ParserField[0];

    /**
     * This value contains all flags as stored in the VM including internal ones.
     */
    private final int flags;
    private final Symbol<Name> name;
    private final Symbol<Type> type;
    private final int typeIndex;
    @CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    public int getFlags() {
        return flags;
    }

    public Symbol<Name> getName() {
        return name;
    }

    public Symbol<Type> getType() {
        return type;
    }

    public Attribute[] getAttributes() {
        return attributes;
    }

    public ParserField(int flags, Symbol<Name> name, Symbol<Type> type, int typeIndex, final Attribute[] attributes) {
        this.flags = flags;
        this.name = name;
        this.type = type;
        // Used to resolve the field on the holder constant pool.
        this.typeIndex = typeIndex;
        this.attributes = attributes;
    }

    public int getTypeIndex() {
        return typeIndex;
    }
}
