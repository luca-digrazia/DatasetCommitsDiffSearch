/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;

public class StaticObjectImpl extends StaticObject {
    private final Map<String, Object> fields;
    private final boolean isStatic;

    public boolean isStatic() {
        return isStatic;
    }

    public StaticObjectImpl(Klass klass, Map<String, Object> fields, boolean isStatic) {
        super(klass);
        this.fields = fields;
        this.isStatic = isStatic;
    }

    // Shallow copy.
    public StaticObject clone() {
        return new StaticObjectImpl(getKlass(), new HashMap<>(fields), isStatic);
    }

    public StaticObjectImpl(Klass klass) {
        super(klass);
        if (klass == null) {
            this.fields = null;
        } else {
            this.fields = new HashMap<>();
            FieldInfo[] allFields = klass.getInstanceFields(true);
            for (FieldInfo fi : allFields) {
                Object value = null;
                switch (fi.getKind()) {
                    case Object:
                        value = StaticObject.NULL;
                        break;
                    case Float:
                        value = 0f;
                        break;
                    case Double:
                        value = 0.0;
                        break;
                    case Long:
                        value = 0L;
                        break;
                    case Char:
                        value = (char) 0;
                        break;
                    case Short:
                        value = (short) 0;
                        break;
                    case Int:
                        value = 0;
                        break;
                    case Byte:
                        value = (byte) 0;
                        break;
                    case Boolean:
                        value = false;
                        break;
                    case Illegal:
                    case Void:
                        throw new RuntimeException("Invalid type " + fi.getKind() + " for field: " + fi.getName());
                }
                this.fields.put(fi.getName(), value);
            }
        }
        this.isStatic = false;
    }

    public StaticObjectImpl(Klass klass, boolean isStatic) {
        super(klass);
        if (klass.getSuperclass() != null) {
            klass.getSuperclass().initialize();
        }
        this.fields = new HashMap<>();
        FieldInfo[] allFields = isStatic ? klass.getStaticFields() : klass.getInstanceFields(true);
        for (FieldInfo fi : allFields) {
            if (fi.isStatic() == isStatic) {
                Object value = null;
                switch (fi.getKind()) {
                    case Object:
                        value = StaticObject.NULL;
                        break;
                    case Float:
                        value = 0f;
                        break;
                    case Double:
                        value = 0.0;
                        break;
                    case Char:
                        value = (char) 0;
                        break;
                    case Short:
                        value = (short) 0;
                        break;
                    case Int:
                        value = 0;
                        break;
                    case Long:
                        value = 0L;
                        break;
                    case Byte:
                        value = (byte) 0;
                        break;
                    case Boolean:
                        value = false;
                        break;
                    case Illegal:
                    case Void:
                        throw new RuntimeException("Invalid type " + fi.getKind() + " for field: " + fi.getName());
                }
                this.fields.put(fi.getName(), value);
            }
        }
        this.isStatic = isStatic;
    }

    public Object getField(FieldInfo field) {
        // TODO(peterssen): Klass check
        return fields.get(field.getName());
    }

    public void setFieldByName(String name, Object value) {
        fields.put(name, value);
    }

    public void setField(FieldInfo field, Object value) {
        // TODO(peterssen): Klass check
        assert fields.containsKey(field.getName());
        fields.put(field.getName(), value);
    }
}
