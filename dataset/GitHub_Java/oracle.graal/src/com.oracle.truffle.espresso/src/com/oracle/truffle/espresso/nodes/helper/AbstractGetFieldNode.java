/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.helper;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class AbstractGetFieldNode extends Node {
    final Field field;
    final String fieldName;
    final int slotCount;
    static final int CACHED_LIBRARY_LIMIT = 3;

    AbstractGetFieldNode(Field field) {
        this.field = field;
        this.fieldName = field.getNameAsString();
        this.slotCount = field.getKind().getSlotCount();
    }

    public abstract int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex);

    public static AbstractGetFieldNode create(Field f) {
        // @formatter:off
        switch (f.getKind()) {
            case Boolean: return BooleanGetFieldNodeGen.create(f);
            case Byte:    return ByteGetFieldNodeGen.create(f);
            case Short:   return ShortGetFieldNodeGen.create(f);
            case Char:    return CharGetFieldNodeGen.create(f);
            case Int:     return IntGetFieldNodeGen.create(f);
            case Float:   return FloatGetFieldNodeGen.create(f);
            case Long:    return LongGetFieldNodeGen.create(f);
            case Double:  return DoubleGetFieldNodeGen.create(f);
            case Object:  return ObjectGetFieldNodeGen.create(f);
            default:
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    protected Object getForeignField(StaticObject receiver, InteropLibrary interopLibrary, EspressoContext context, BranchProfile error) {
        assert field.getDeclaringKlass().isAssignableFrom(receiver.getKlass());
        assert !field.isStatic();
        Object value;
        try {
            value = interopLibrary.readMember(receiver.rawForeignObject(), fieldName);
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            error.enter();
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_NoSuchFieldError, "Foreign object has no readable field " + fieldName);
        }
        return value;
    }
}

abstract class IntGetFieldNode extends AbstractGetFieldNode {
    IntGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Int;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex) {
        root.notifyFieldAccess(frame, statementIndex, field, receiver);
        root.putInt(frame, at, executeGetField(receiver));
        return slotCount;
    }

    abstract int executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isEspressoObject()")
    int doEspresso(StaticObject receiver) {
        return receiver.getIntField(field);
    }

    @Specialization(guards = "receiver.isForeignObject()", limit = "CACHED_LIBRARY_LIMIT")
    int doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        Object value = getForeignField(receiver, interopLibrary, context, error);
        try {
            return (int) toEspressoNode.execute(value, context.getMeta()._int);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Foreign field " + fieldName + " cannot be cast to int");
        }
    }
}

abstract class BooleanGetFieldNode extends AbstractGetFieldNode {
    BooleanGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Boolean;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex) {
        root.notifyFieldAccess(frame, statementIndex, field, receiver);
        root.putInt(frame, at, executeGetField(receiver) ? 1 : 0);
        return slotCount;
    }

    abstract boolean executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isEspressoObject()")
    boolean doEspresso(StaticObject receiver) {
        return receiver.getBooleanField(field);
    }

    @Specialization(guards = "receiver.isForeignObject()", limit = "CACHED_LIBRARY_LIMIT")
    boolean doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        Object value = getForeignField(receiver, interopLibrary, context, error);
        try {
            return (boolean) toEspressoNode.execute(value, context.getMeta()._boolean);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Foreign field " + fieldName + " cannot be cast to boolean");
        }
    }
}

abstract class CharGetFieldNode extends AbstractGetFieldNode {
    CharGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Char;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex) {
        root.notifyFieldAccess(frame, statementIndex, field, receiver);
        root.putInt(frame, at, executeGetField(receiver));
        return slotCount;
    }

    abstract char executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isEspressoObject()")
    char doEspresso(StaticObject receiver) {
        return receiver.getCharField(field);
    }

    @Specialization(guards = "receiver.isForeignObject()", limit = "CACHED_LIBRARY_LIMIT")
    char doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        Object value = getForeignField(receiver, interopLibrary, context, error);
        try {
            return (char) toEspressoNode.execute(value, context.getMeta()._char);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Foreign field " + fieldName + " cannot be cast to char");
        }
    }
}

abstract class ShortGetFieldNode extends AbstractGetFieldNode {
    ShortGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Short;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex) {
        root.notifyFieldAccess(frame, statementIndex, field, receiver);
        root.putInt(frame, at, executeGetField(receiver));
        return slotCount;
    }

    abstract short executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isEspressoObject()")
    short doEspresso(StaticObject receiver) {
        return receiver.getShortField(field);
    }

    @Specialization(guards = "receiver.isForeignObject()", limit = "CACHED_LIBRARY_LIMIT")
    short doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        Object value = getForeignField(receiver, interopLibrary, context, error);
        try {
            return (short) toEspressoNode.execute(value, context.getMeta()._short);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Foreign field " + fieldName + " cannot be cast to short");
        }
    }
}

abstract class ByteGetFieldNode extends AbstractGetFieldNode {
    ByteGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Byte;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex) {
        root.notifyFieldAccess(frame, statementIndex, field, receiver);
        root.putInt(frame, at, executeGetField(receiver));
        return slotCount;
    }

    abstract byte executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isEspressoObject()")
    byte doEspresso(StaticObject receiver) {
        return receiver.getByteField(field);
    }

    @Specialization(guards = "receiver.isForeignObject()", limit = "CACHED_LIBRARY_LIMIT")
    byte doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        Object value = getForeignField(receiver, interopLibrary, context, error);
        try {
            return (byte) toEspressoNode.execute(value, context.getMeta()._byte);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Foreign field " + fieldName + " cannot be cast to byte");
        }
    }
}

abstract class LongGetFieldNode extends AbstractGetFieldNode {
    LongGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Long;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex) {
        root.notifyFieldAccess(frame, statementIndex, field, receiver);
        root.putLong(frame, at, executeGetField(receiver));
        return slotCount;
    }

    abstract long executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isEspressoObject()")
    long doEspresso(StaticObject receiver) {
        return receiver.getLongField(field);
    }

    @Specialization(guards = "receiver.isForeignObject()", limit = "CACHED_LIBRARY_LIMIT")
    long doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        Object value = getForeignField(receiver, interopLibrary, context, error);
        try {
            return (long) toEspressoNode.execute(value, context.getMeta()._long);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Foreign field " + fieldName + " cannot be cast to long");
        }
    }
}

abstract class FloatGetFieldNode extends AbstractGetFieldNode {
    FloatGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Float;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex) {
        root.notifyFieldAccess(frame, statementIndex, field, receiver);
        root.putFloat(frame, at, executeGetField(receiver));
        return slotCount;
    }

    abstract float executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isEspressoObject()")
    float doEspresso(StaticObject receiver) {
        return receiver.getFloatField(field);
    }

    @Specialization(guards = "receiver.isForeignObject()", limit = "CACHED_LIBRARY_LIMIT")
    float doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        Object value = getForeignField(receiver, interopLibrary, context, error);
        try {
            return (float) toEspressoNode.execute(value, context.getMeta()._float);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Foreign field " + fieldName + " cannot be cast to float");
        }
    }
}

abstract class DoubleGetFieldNode extends AbstractGetFieldNode {
    DoubleGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Double;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex) {
        root.notifyFieldAccess(frame, statementIndex, field, receiver);
        root.putDouble(frame, at, executeGetField(receiver));
        return slotCount;
    }

    abstract double executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isEspressoObject()")
    double doEspresso(StaticObject receiver) {
        return receiver.getDoubleField(field);
    }

    @Specialization(guards = "receiver.isForeignObject()", limit = "CACHED_LIBRARY_LIMIT")
    double doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        Object value = getForeignField(receiver, interopLibrary, context, error);
        try {
            return (double) toEspressoNode.execute(value, context.getMeta()._double);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Foreign field " + fieldName + " cannot be cast to double");
        }
    }
}

abstract class ObjectGetFieldNode extends AbstractGetFieldNode {
    final Klass typeKlass;

    ObjectGetFieldNode(Field f) {
        super(f);
        this.typeKlass = f.resolveTypeKlass();
        assert f.getKind() == JavaKind.Object;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int at, int statementIndex) {
        root.notifyFieldAccess(frame, statementIndex, field, receiver);
        root.putObject(frame, at, executeGetField(receiver));
        return slotCount;
    }

    abstract StaticObject executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isEspressoObject()")
    StaticObject doEspresso(StaticObject receiver) {
        return receiver.getField(field);
    }

    @Specialization(guards = "receiver.isForeignObject()", limit = "CACHED_LIBRARY_LIMIT")
    StaticObject doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        Object value = getForeignField(receiver, interopLibrary, context, error);
        try {
            return (StaticObject) toEspressoNode.execute(value, typeKlass);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Foreign field " + fieldName + " cannot be cast to " + typeKlass.getNameAsString());
        }
    }
}
