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

import java.lang.reflect.Modifier;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.runtime.Attribute;

// Structural shareable klass (superklass in superinterfaces resolved and linked)
// contains shape, field locations.
// Klass shape, vtable and field locations can be computed at the structural level.
public final class LinkedKlass {

    public static final LinkedKlass[] EMPTY_ARRAY = new LinkedKlass[0];
    private final ParserKlass parserKlass;

    // Linked structural references.
    private final LinkedKlass superKlass;

    @CompilationFinal(dimensions = 1) //
    private final LinkedKlass[] interfaces;

    @CompilationFinal(dimensions = 1) //
    private final LinkedMethod[] methods;

    @CompilationFinal(dimensions = 1) //
    private final LinkedField[] fields; // Field slots already computed.

    protected LinkedMethod[] getLinkedMethods() {
        return methods;
    }

    protected LinkedField[] getLinkedFields() {
        return fields;
    }

    protected final int instanceFieldCount;
    protected final int staticFieldCount;

    public LinkedKlass(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {

        assert Arrays.stream(interfaces).allMatch(i -> Modifier.isInterface(i.getFlags()));
        assert superKlass == null || !Modifier.isInterface(superKlass.getFlags());

        int instanceFieldSlot = superKlass != null ? superKlass.instanceFieldCount : 0;
        int staticFieldSlot = 0;

        final int methodCount = parserKlass.getMethods().length;
        final int fieldCount = parserKlass.getFields().length;

        LinkedField[] linkedFields = new LinkedField[fieldCount];
        LinkedMethod[] linkedMethods = new LinkedMethod[methodCount];

        for (int i = 0; i < fieldCount; ++i) {
            ParserField parserField = parserKlass.getFields()[i];

            int slot = Modifier.isStatic(parserField.getFlags())
                            ? staticFieldSlot++
                            : instanceFieldSlot++;

            linkedFields[i] = new LinkedField(parserField, this, slot);
        }

        for (int i = 0; i < methodCount; ++i) {
            ParserMethod parserMethod = parserKlass.getMethods()[i];
            // TODO(peterssen): Methods with custom constant pool should spawned here, but not
            // supported.
            linkedMethods[i] = new LinkedMethod(parserMethod, this);
        }

        this.parserKlass = parserKlass;
        this.superKlass = superKlass;
        this.interfaces = interfaces;
        this.staticFieldCount = staticFieldSlot;
        this.instanceFieldCount = instanceFieldSlot;
        this.fields = linkedFields;
        this.methods = linkedMethods;
    }

    public boolean equals(LinkedKlass other) {
        return parserKlass == other.parserKlass &&
                        superKlass == other.superKlass &&
                        /* reference equals */ Arrays.equals(interfaces, other.interfaces);
    }

    int getFlags() {
        return parserKlass.getFlags();
    }

    ConstantPool getConstantPool() {
        return parserKlass.getConstantPool();
    }

    public Attribute getAttribute(Symbol<Name> name) {
        return parserKlass.getAttribute(name);
    }

    Symbol<Type> getType() {
        return parserKlass.getType();
    }

    public Symbol<Name> getName() {
        return parserKlass.getName();
    }
}
