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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Array;

import static com.oracle.truffle.espresso.vm.InterpreterToVM.instanceOf;

/**
 * Jumbo class that does everything for any type of object, while maintaining same performance, whether they be arrays,
 * classes or regular objects. This allows for leaf type-checks.
 *
 * This does not come for free, however, as the implementation is pretty ugly.
 */
public final class StaticObject implements TruffleObject {

    private static final Unsafe U;

    public static final StaticObject VOID = new StaticObject();

    public static final StaticObject NULL = new StaticObject();

    static {
        try {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    // Only non-primitive fields are stored in this
    private final Object fields;

    /**
     * Stores all primitive types contiguously in a single byte array, without any unused bits
     * between prims (except for 7 bits with booleans). In order to quickly reconstruct a long (for
     * example), which would require reading 16 bytes and concatenating them, call Unsafe which can
     * directly read a long.
     */
    private final byte[] primitiveFields;

    // Dedicated constructor for VOID and NULL pseudo-singletons
    private StaticObject() {
        this.klass = null;
        this.fields = null;
        this.primitiveFields = null;
    }

    // Constructor for object copy
    public StaticObject(ObjectKlass klass, Object[] fields, byte[] primitiveFields) {
        this.klass = klass;
        this.fields = fields;
        this.primitiveFields = primitiveFields;
    }

    // Constructor for regular objects.
    public StaticObject(ObjectKlass klass) {
        this(klass, false);
    }

    public StaticObject(ObjectKlass klass, boolean isStatic) {
        this.klass = klass;
        // assert !isStatic || klass.isInitialized();
        if (isStatic) {
            this.fields = klass.getStaticObjectFieldsCount() > 0 ? new Object[klass.getStaticObjectFieldsCount()] : null;
            this.primitiveFields = klass.getStaticWordFieldsCount() > 0 ? new byte[klass.getStaticWordFieldsCount()] : null;
        } else {
            this.fields = klass.getObjectFieldsCount() > 0 ? new Object[klass.getObjectFieldsCount()] : null;
            this.primitiveFields = klass.getWordFieldsCount() > 0 ? new byte[klass.getWordFieldsCount()] : null;
        }
        initFields(klass, isStatic);
    }

    /**
     * Constructor for Array objects.
     *
     * Current implementation stores the array in lieu of fields. fields being an Object, a char array can be stored under it without any boxing happening.
     * The array could have been stored in fields[0], but getting to the array would then require an additional indirection.
     *
     * Regular objects still always have an Object[] hiding under fields. In order to preserve the behavior and avoid casting to Object[] (a non-leaf cast), we perform field accesses with Unsafe operations.
     */
    public StaticObject(ArrayKlass klass, Object array) {
        this.klass = klass;
        assert klass.isArray();
        assert array != null;
        assert !(array instanceof StaticObject);
        assert array.getClass().isArray();
        this.fields = array;
        this.primitiveFields = new byte[JavaKind.Int.getByteCount()];
        U.putInt(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, Array.getLength(array));
    }

    private final Klass klass;

    public final Klass getKlass() {
        return klass;
    }

    public static boolean isNull(StaticObject object) {
        assert object != null;
        return object == StaticObject.NULL;
    }

    public static boolean notNull(StaticObject object) {
        return !isNull(object);
    }

    public final boolean isStaticStorage() {
        return this == getKlass().getStatics();
    }

    // FIXME(peterssen): Klass does not need to be initialized, just prepared?.
    public boolean isStatic() {
        return this == getKlass().getStatics();
    }

    // Shallow copy.
    public StaticObject copy() {
        if (getKlass().isArray()) {
            return new StaticObject((ArrayKlass) getKlass(), cloneWrapped());
        } else {
            CompilerAsserts.neverPartOfCompilation();
            return new StaticObject((ObjectKlass) getKlass(), fields == null ? null : ((Object[])fields).clone(), primitiveFields == null ? null : primitiveFields.clone());
        }
    }

    @ExplodeLoop
    private void initFields(ObjectKlass thisKlass, boolean isStatic) {
        CompilerAsserts.partialEvaluationConstant(thisKlass);
        if (isStatic) {
            for (Field f : thisKlass.getStaticFieldTable()) {
                assert f.isStatic();
                if (f.getKind().isSubWord()) {
                    setWordField(f, MetaUtil.defaultWordFieldValue(f.getKind()));
                } else if (f.getKind().isPrimitive()) {
                    // not a subword but primitive -> long or double
                    setLongField(f, (long) MetaUtil.defaultFieldValue(f.getKind()));
                } else {
                    setUnsafeField(f.getFieldIndex(), MetaUtil.defaultFieldValue(f.getKind()));
                }
            }
        } else {
            for (Field f : thisKlass.getFieldTable()) {
                assert !f.isStatic();
                if (f.isHidden()) {
                    setUnsafeField(f.getFieldIndex(), null);
                } else {
                    if (f.getKind().isSubWord()) {
                        setWordField(f, MetaUtil.defaultWordFieldValue(f.getKind()));
                    } else if (f.getKind().isPrimitive()) {
                        // not a subword but primitive -> long or double
                        setLongField(f, (long) MetaUtil.defaultFieldValue(f.getKind()));
                    } else {
                        setUnsafeField(f.getFieldIndex(), MetaUtil.defaultFieldValue(f.getKind()));
                    }
                }
            }
        }
    }

    // Start non primitive field handling.

    public final Object getFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.getObjectVolatile(fields, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * field.getFieldIndex());
    }

    public final Object getField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        Object result;
        if (field.isVolatile()) {
            result = getFieldVolatile(field);
        } else {
            result = getUnsafeField(field.getFieldIndex());
        }
        assert result != null;
        return result;
    }

    // Use with caution.
    public final Object getUnsafeField(int fieldIndex) {
        return U.getObject(fields, (long)Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * fieldIndex);
    }

    public final void setFieldVolatile(Field field, Object value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        U.putObjectVolatile(fields, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * field.getFieldIndex(), value);
    }

    public final void setField(Field field, Object value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert !field.getKind().isSubWord();
        if (field.isVolatile()) {
            setFieldVolatile(field, value);
        } else {
            setUnsafeField(field.getFieldIndex(), value);
        }
    }

    private void setUnsafeField(int index, Object value) {
        U.putObject(fields, (long) Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index, value);
    }

    public boolean compareAndSwapField(Field field, Object before, Object after) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.compareAndSwapObject(fields, (long) Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * field.getFieldIndex(), before, after);
    }

    // End non-primitive field handling
    // Start subword field handling

    // Have a getter/Setter pair for each kind of primitive. Though a bit ugly, it avoids a switch
    // when kind is known beforehand.

    public final boolean getBooleanField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Boolean;
        if (field.isVolatile()) {
            return getByteFieldVolatile(field) != 0;
        } else {
            return U.getByte(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex()) != 0;
        }
    }

    public byte getByteFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.getByteVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
    }

    public final byte getByteField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Byte;
        if (field.isVolatile()) {
            return getByteFieldVolatile(field);
        } else {
            return U.getByte(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
        }
    }

    public final char getCharField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Char;
        if (field.isVolatile()) {
            return getCharFieldVolatile(field);
        } else {
            return U.getChar(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
        }
    }

    public char getCharFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.getCharVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
    }

    public final short getShortField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Short;
        if (field.isVolatile()) {
            return getShortFieldVolatile(field);
        } else {
            return U.getShort(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
        }
    }

    public short getShortFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.getShortVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
    }

    public final int getIntField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Int || field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            return getIntFieldVolatile(field);
        } else {
            return U.getInt(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
        }
    }

    public int getIntFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.getIntVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
    }

    public final void setBooleanField(Field field, boolean value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Boolean;
        if (field.isVolatile()) {
            U.putByteVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (byte) (value ? 1 : 0));
        } else {
            U.putByte(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (byte) (value ? 1 : 0));
        }
    }

    public final void setByteField(Field field, byte value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Byte;
        if (field.isVolatile()) {
            U.putByteVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        } else {
            U.putByte(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        }
    }

    public final void setCharField(Field field, char value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Char;
        if (field.isVolatile()) {
            U.putCharVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        } else {
            U.putChar(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        }
    }

    public final void setShortField(Field field, short value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Short;
        if (field.isVolatile()) {
            U.putShortVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        } else {
            U.putShort(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        }
    }

    public final void setIntField(Field field, int value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind() == JavaKind.Int || field.getKind() == JavaKind.Float;
        if (field.isVolatile()) {
            U.putIntVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        } else {
            U.putInt(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        }
    }

    public boolean compareAndSwapIntField(Field field, int before, int after) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.compareAndSwapInt(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), before, after);
    }

    // This multi-kind setter sticks around for object initialization.
    private void setWordField(Field field, int value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().isSubWord();
        if (field.isVolatile()) {
            setWordFieldVolatile(field, value);
        } else {
            applySetWordField(field, value);
        }
    }

    public void setWordFieldVolatile(Field field, int value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        switch (field.getKind()) {
            case Boolean:
            case Byte:
                U.putByteVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (byte) value);
                break;
            case Char:
                U.putCharVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (char) value);
                break;
            case Short:
                U.putShortVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (short) value);
                break;
            case Int:
            case Float:
                U.putIntVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
                break;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    private void applySetWordField(Field field, int value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        switch (field.getKind()) {
            case Boolean:
            case Byte:
                U.putByte(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (byte) value);
                break;
            case Char:
                U.putChar(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (char) value);
                break;
            case Short:
                U.putShort(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), (short) value);
                break;
            case Int:
            case Float:
                U.putInt(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
                break;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    // End subword field handling
    // start big words field handling

    public final long getLongFieldVolatile(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.getLongVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
    }

    public final long getLongField(Field field) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            return getLongFieldVolatile(field);
        } else {
            return U.getLong(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex());
        }
    }

    public final void setLongFieldVolatile(Field field, long value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        U.putLongVolatile(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
    }

    public final void setLongField(Field field, long value) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        assert field.getKind().needsTwoSlots();
        if (field.isVolatile()) {
            setLongFieldVolatile(field, value);
        } else {
            U.putLong(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), value);
        }
    }

    public boolean compareAndSwapLongField(Field field, long before, long after) {
        assert field.getDeclaringKlass().isAssignableFrom(getKlass());
        return U.compareAndSwapLong(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * field.getFieldIndex(), before, after);
    }

    // End big words field handling.

    // Given a guest Class, get the corresponding Klass.
    public final Klass getMirrorKlass() {
        assert getKlass().getType() == Symbol.Type.Class;
        return (Klass) getHiddenField(getKlass().getMeta().HIDDEN_MIRROR_KLASS);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (this == VOID) {
            return "void";
        }
        if (this == NULL) {
            return "null";
        }
        if (getKlass() == getKlass().getMeta().String) {
            return Meta.toHostString(this);
        }
        return getKlass().getType().toString();
    }

    public void setHiddenField(Field hiddenField, Object value) {
        assert hiddenField.isHidden();
        setUnsafeField(hiddenField.getFieldIndex(), value);
    }

    public Object getHiddenField(Field hiddenField) {
        assert hiddenField.isHidden();
        return getUnsafeField(hiddenField.getFieldIndex());
    }

    public ForeignAccess getForeignAccess() {
        return StaticObjectMessageResolutionForeign.ACCESS;
    }

    /**
     * Start of Array manipulation:
     */

    @SuppressWarnings("unchecked")
    public <T> T unwrap() {
        assert isArray();
        return (T) fields;
    }

    public <T> T get(int index) {
        assert isArray();
        return this.<T[]> unwrap()[index];
    }

    /**
     * Workaround to avoid casting to Object[] in InterpreterToVM (non-leaf type check).
     */
    public void putObject(StaticObject value, int index, Meta meta) {
        assert isArray();
        if (index >= 0 && index < length()) {
            U.putObject(fields, (long) Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index, arrayStoreExCheck(value, klass.getComponentType(), meta));
        } else {
            CompilerDirectives.transferToInterpreter();
            throw meta.throwEx(ArrayIndexOutOfBoundsException.class);
        }
    }

    private static Object arrayStoreExCheck(StaticObject value, Klass componentType, Meta meta) {
        if (StaticObject.isNull(value) || instanceOf(value, componentType)) {
            return value;
        } else {
            throw meta.throwEx(ArrayStoreException.class);
        }
    }

    public int length() {
        assert isArray();
        return U.getInt(primitiveFields, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    private Object cloneWrapped() {
        assert isArray();
        if (fields instanceof boolean[]) {
            return this.<boolean[]> unwrap().clone();
        }
        if (fields instanceof byte[]) {
            return this.<byte[]> unwrap().clone();
        }
        if (fields instanceof char[]) {
            return this.<char[]> unwrap().clone();
        }
        if (fields instanceof short[]) {
            return this.<short[]> unwrap().clone();
        }
        if (fields instanceof int[]) {
            return this.<int[]> unwrap().clone();
        }
        if (fields instanceof float[]) {
            return this.<float[]> unwrap().clone();
        }
        if (fields instanceof double[]) {
            return this.<double[]> unwrap().clone();
        }
        if (fields instanceof long[]) {
            return this.<long[]> unwrap().clone();
        }
        return this.<StaticObject[]> unwrap().clone();
    }

    public static StaticObject wrap(StaticObject[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta.Object_array, array);
    }

    public static StaticObject wrap(byte[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._byte_array, array);
    }

    public static StaticObject wrap(boolean[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._boolean_array, array);
    }

    public static StaticObject wrap(char[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._char_array, array);
    }

    public static StaticObject wrap(short[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._short_array, array);
    }

    public static StaticObject wrap(int[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._int_array, array);
    }

    public static StaticObject wrap(float[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._float_array, array);
    }

    public static StaticObject wrap(double[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._double_array, array);
    }

    public static StaticObject wrap(long[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObject(meta._long_array, array);
    }

    public static StaticObject wrapPrimitiveArray(Object array) {
        assert array != null;
        assert array.getClass().isArray() && array.getClass().getComponentType().isPrimitive();
        if (array instanceof boolean[]) {
            return wrap((boolean[]) array);
        }
        if (array instanceof byte[]) {
            return wrap((byte[]) array);
        }
        if (array instanceof char[]) {
            return wrap((char[]) array);
        }
        if (array instanceof short[]) {
            return wrap((short[]) array);
        }
        if (array instanceof int[]) {
            return wrap((int[]) array);
        }
        if (array instanceof float[]) {
            return wrap((float[]) array);
        }
        if (array instanceof double[]) {
            return wrap((double[]) array);
        }
        if (array instanceof long[]) {
            return wrap((long[]) array);
        }
        throw EspressoError.shouldNotReachHere("Not a primitive array " + array);
    }

    public boolean isArray() {
        return getKlass().isArray();
    }
}
