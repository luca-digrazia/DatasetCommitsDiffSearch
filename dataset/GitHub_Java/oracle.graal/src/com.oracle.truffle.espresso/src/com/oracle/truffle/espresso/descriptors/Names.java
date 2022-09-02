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
package com.oracle.truffle.espresso.descriptors;

import com.oracle.truffle.espresso.descriptors.Symbol.Name;

/**
 * Manages access to "name" symbols.
 *
 * Names do not have a well-defined format, except for not being empty.
 *
 * TODO(peterssen): In debug mode this class should warn if name symbol is valid type or signature.
 */
public final class Names {
    private final Symbols symbols;

    public Names(Symbols symbols) {
        this.symbols = symbols;
    }

    public final Symbol<Name> lookup(ByteSequence bytes) {
        return symbols.lookup(bytes);
    }

    public final Symbol<Name> lookup(String name) {
        return lookup(ByteSequence.create(name));
    }

    public final Symbol<Name> getOrCreate(String name) {
        return symbols.symbolify(ByteSequence.create(name));
    }
}
