/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.classfile.Constants.FIELD_ID_OBFUSCATE;
import static com.oracle.truffle.espresso.classfile.Constants.FIELD_ID_TYPE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.staticobject.StaticProperty;

final class LinkedField extends StaticProperty {
    enum IdMode {
        REGULAR,
        WITH_TYPE,
        OBFUSCATED,
    }

    @CompilationFinal private ParserField parserField;
    private final int slot;

    LinkedField(ParserField parserField, int slot, boolean storeAsFinal, IdMode mode) {
        super(parserField.getPropertyKind(), storeAsFinal);
        this.parserField = maybeCorrectParserField(parserField, mode);
        this.slot = slot;
    }

    private static ParserField maybeCorrectParserField(ParserField parserField, IdMode mode) {
        switch (mode) {
            case REGULAR:
                return parserField;
            case WITH_TYPE:
                return parserField.withFlags(FIELD_ID_TYPE);
            case OBFUSCATED:
                return parserField.withFlags(FIELD_ID_OBFUSCATE);
        }
        throw EspressoError.shouldNotReachHere();
    }

    /**
     * This method is required by the Static Object Model. In Espresso we should rather call
     * `getName()` and use Symbols.
     */
    @Override
    protected String getId() {
        int flags = getFlags();
        Symbol<Name> name = getName();
        if ((flags & FIELD_ID_TYPE) == FIELD_ID_TYPE) {
            // Field name and type.
            return idFromNameAndType(name, getType());
        } else if ((flags & FIELD_ID_OBFUSCATE) == FIELD_ID_OBFUSCATE) {
            // "{primitive, hidden, reference}Field{slot}"

            return (getKind().isPrimitive() ? "primitive" : (isHidden() ? "hidden" : "reference")) + "Field" + slot;
        } else {
            // Regular name
            return name.toString();
        }
    }

    static String idFromNameAndType(Symbol<Name> name, Symbol<Type> type) {
        String typeString = type.toString();
        // Strip 'L' and ';' from the type symbol.
        if (Types.isReference(type)) {
            int arrayDims = Types.getArrayDimensions(type);
            typeString = typeString.substring(arrayDims + 1, typeString.length() - 1);
            while (arrayDims > 0) {
                typeString = typeString + "[]";
                arrayDims--;
            }
        }
        typeString = typeString.replace('/', '_');
        return name.toString() + "_" + typeString;
    }

    public Symbol<Name> getName() {
        // no need to go through getParserField(), since name
        // can't change on redefinition on a linked field
        return parserField.getName();
    }

    /**
     * The slot is the position in the `fieldTable` of the ObjectKlass.
     */
    public int getSlot() {
        return slot;
    }

    public Symbol<Type> getType() {
        return getParserField().getType();
    }

    public int getFlags() {
        return getParserField().getFlags();
    }

    public JavaKind getKind() {
        // no need to go through getParserField(), since kind
        // can't change on redefinition on a linked field
        return parserField.getKind();
    }

    public Attribute getAttribute(Symbol<Name> name) {
        for (Attribute a : getParserField().getAttributes()) {
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }

    public boolean isHidden() {
        return getParserField().isHidden();
    }

    ParserField getParserField() {
        // block execution during class redefinition
        ClassRedefinition.check();

        ParserField current = parserField;
        if (!current.getRedefineAssumption().isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            do {
                current = parserField;
            } while (!current.getRedefineAssumption().isValid());
        }
        return current;
    }

    public void redefine(ParserField newParserField) {
        ParserField old = parserField;
        parserField = newParserField;
        old.getRedefineAssumption().invalidate();
    }
}
