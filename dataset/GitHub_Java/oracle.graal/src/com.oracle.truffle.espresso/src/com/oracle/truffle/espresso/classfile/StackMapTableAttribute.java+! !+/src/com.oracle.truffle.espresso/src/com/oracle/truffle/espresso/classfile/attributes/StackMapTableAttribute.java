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

package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.runtime.Attribute;

public class StackMapTableAttribute extends Attribute {

    public static final Symbol<Name> NAME = Symbol.Name.StackMapTable;

    public StackMapFrame[] getEntries() {
        return entries;
    }

    private final StackMapFrame[] entries;
    private boolean truncated = false;

    public StackMapTableAttribute(Symbol<Symbol.Name> name, StackMapFrame[] entries) {
        super(name, null);
        this.entries = entries;
    }

    public void print(Klass klass) {
        System.err.println("    StackMapTable {");
        for (StackMapFrame sme : entries) {
            sme.print(klass);
        }
        System.err.println("    }");
    }

    public boolean isTruncated() {
        return truncated;
    }

    void setTruncated() {
        this.truncated = true;
    }
}
