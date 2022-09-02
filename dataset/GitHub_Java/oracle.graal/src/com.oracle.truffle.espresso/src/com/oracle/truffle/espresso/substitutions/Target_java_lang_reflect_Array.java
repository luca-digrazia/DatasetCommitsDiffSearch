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

package com.oracle.truffle.espresso.substitutions;

import java.lang.reflect.Array;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public final class Target_java_lang_reflect_Array {

    /**
     * Creates a new array with the specified component type and length. Invoking this method is
     * equivalent to creating an array as follows: <blockquote>
     * 
     * <pre>
     * int[] x = {length};
     * Array.newInstance(componentType, x);
     * </pre>
     * 
     * </blockquote>
     *
     * <p>
     * The number of dimensions of the new array must not exceed 255.
     *
     * @param componentType the {@code Class} object representing the component type of the new
     *            array
     * @param length the length of the new array
     * @return the new array
     * @exception NullPointerException if the specified {@code componentType} parameter is null
     * @exception IllegalArgumentException if componentType is {@link Void#TYPE} or if the number of
     *                dimensions of the requested array instance exceed 255.
     * @exception NegativeArraySizeException if the specified {@code length} is negative
     */
    @Substitution
    public static Object newArray(@Host(Class.class) StaticObject componentType, int length, @InjectMeta Meta meta) {
        if (StaticObject.isNull(componentType)) {
            throw meta.throwEx(meta.NullPointerException);
        }
        Klass component = componentType.getMirrorKlass();
        if (component == meta._void || Types.getArrayDimensions(component.getType()) >= 255) {
            throw meta.throwEx(meta.IllegalArgumentException);
        }

        if (component.isPrimitive()) {
            byte jvmPrimitiveType = (byte) component.getJavaKind().getBasicType();
            return InterpreterToVM.allocatePrimitiveArray(jvmPrimitiveType, length);
        }

        // NegativeArraySizeException is thrown in getInterpreterToVM().newArray
        // if (length < 0) {
        // throw meta.throwEx(meta.NegativeArraySizeException);
        // }
        return InterpreterToVM.newArray(component, length);
    }

    /**
     * Creates a new array with the specified component type and dimensions. If
     * {@code componentType} represents a non-array class or interface, the new array has
     * {@code dimensions.length} dimensions and {@code componentType} as its component type. If
     * {@code componentType} represents an array class, the number of dimensions of the new array is
     * equal to the sum of {@code dimensions.length} and the number of dimensions of
     * {@code componentType}. In this case, the component type of the new array is the component
     * type of {@code componentType}.
     *
     * <p>
     * The number of dimensions of the new array must not exceed 255.
     *
     * @param componentType the {@code Class} object representing the component type of the new
     *            array
     * @param dimensionsArray an array of {@code int} representing the dimensions of the new array
     * @return the new array
     * @exception NullPointerException if the specified {@code componentType} argument is null
     * @exception IllegalArgumentException if the specified {@code dimensions} argument is a
     *                zero-dimensional array, if componentType is {@link Void#TYPE}, or if the
     *                number of dimensions of the requested array instance exceed 255.
     * @exception NegativeArraySizeException if any of the components in the specified
     *                {@code dimensions} argument is negative.
     */
    @Substitution
    public static @Host(Object.class) StaticObject multiNewArray(@Host(Class.class) StaticObject componentType, @Host(int[].class) StaticObject dimensionsArray, @InjectMeta Meta meta) {
        if (StaticObject.isNull(componentType)) {
            throw meta.throwEx(meta.NullPointerException);
        }
        Klass component = componentType.getMirrorKlass();
        if (component == meta._void || StaticObject.isNull(dimensionsArray)) {
            throw meta.throwEx(meta.IllegalArgumentException);
        }
        final int[] dimensions = dimensionsArray.unwrap();
        int finalDimensions = dimensions.length;
        if (component.isArray()) {
            finalDimensions += Types.getArrayDimensions(component.getType());
        }
        if (dimensions.length == 0 || finalDimensions > 255) {
            throw meta.throwEx(meta.IllegalArgumentException);
        }
        for (int d : dimensions) {
            if (d < 0) {
                throw meta.throwEx(meta.NegativeArraySizeException);
            }
        }
        return meta.getInterpreterToVM().newMultiArray(component.getArrayClass(dimensions.length - 1), dimensions);
    }

    // TODO(garcia) Rework these acceses so that they no longer need a boundary.

    @Substitution
    @TruffleBoundary
    public static boolean getBoolean(@Host(Object.class) StaticObject array, int index, @InjectMeta Meta meta) {
        if (!(array.isArray())) {
            throw meta.throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getBoolean(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static byte getByte(@Host(Object.class) StaticObject array, int index, @InjectMeta Meta meta) {
        if (!(array.isArray())) {
            throw meta.throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getByte(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static char getChar(@Host(Object.class) StaticObject array, int index, @InjectMeta Meta meta) {
        if (!(array.isArray())) {
            throw meta.throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getChar(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static short getShort(@Host(Object.class) StaticObject array, int index, @InjectMeta Meta meta) {
        if (!(array.isArray())) {
            throw meta.throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getShort(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static int getInt(@Host(Object.class) StaticObject array, int index, @InjectMeta Meta meta) {
        if (!(array.isArray())) {
            throw meta.throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getInt(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static float getFloat(@Host(Object.class) StaticObject array, int index, @InjectMeta Meta meta) {
        if (!(array.isArray())) {
            throw meta.throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getFloat(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static double getDouble(@Host(Object.class) StaticObject array, int index, @InjectMeta Meta meta) {
        if (!(array.isArray())) {
            throw meta.throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getDouble(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static long getLong(@Host(Object.class) StaticObject array, int index, @InjectMeta Meta meta) {
        if (!(array.isArray())) {
            throw meta.throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getLong(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static void setBoolean(@Host(Object.class) StaticObject array, int index, boolean value, @InjectMeta Meta meta) {
        try {
            Array.setBoolean(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static void setByte(@Host(Object.class) StaticObject array, int index, byte value, @InjectMeta Meta meta) {
        try {
            Array.setByte(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static void setChar(@Host(Object.class) StaticObject array, int index, char value, @InjectMeta Meta meta) {
        try {
            Array.setChar(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static void setShort(@Host(Object.class) StaticObject array, int index, short value, @InjectMeta Meta meta) {
        try {
            Array.setShort(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static void setInt(@Host(Object.class) StaticObject array, int index, int value, @InjectMeta Meta meta) {
        try {
            Array.setInt(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static void setFloat(@Host(Object.class) StaticObject array, int index, float value, @InjectMeta Meta meta) {
        try {
            Array.setFloat(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static void setDouble(@Host(Object.class) StaticObject array, int index, double value, @InjectMeta Meta meta) {
        try {
            Array.setDouble(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    @TruffleBoundary
    public static void setLong(@Host(Object.class) StaticObject array, int index, long value, @InjectMeta Meta meta) {
        try {
            Array.setLong(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw meta.throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    /**
     * Sets the value of the indexed component of the specified array object to the specified new
     * value. The new value is first automatically unwrapped if the array has a primitive component
     * type.
     * 
     * @param array the array
     * @param index the index into the array
     * @param value the new value of the indexed component
     * @exception NullPointerException If the specified object argument is null
     * @exception IllegalArgumentException If the specified object argument is not an array, or if
     *                the array component type is primitive and an unwrapping conversion fails
     * @exception ArrayIndexOutOfBoundsException If the specified {@code index} argument is
     *                negative, or if it is greater than or equal to the length of the specified
     *                array
     */
    @Substitution
    public static void set(@Host(Object.class) StaticObject array, int index, @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (StaticObject.isNull(array)) {
            throw meta.throwEx(meta.NullPointerException);
        }
        if (array.isArray()) {
            // @formatter:off
            // Checkstyle: stop
            switch (array.getKlass().getComponentType().getJavaKind()) {
                case Boolean : vm.setArrayByte(meta.unboxBoolean(value) ? (byte) 1 : (byte) 0, index, array); break;
                case Byte    : vm.setArrayByte(meta.unboxByte(value), index, array);       break;
                case Short   : vm.setArrayShort(meta.unboxShort(value), index, array);     break;
                case Char    : vm.setArrayChar(meta.unboxCharacter(value), index, array);  break;
                case Int     : vm.setArrayInt(meta.unboxInteger(value), index, array);     break;
                case Float   : vm.setArrayFloat(meta.unboxFloat(value), index, array);     break;
                case Long    : vm.setArrayLong(meta.unboxLong(value), index, array);       break;
                case Double  : vm.setArrayDouble(meta.unboxDouble(value), index, array);   break;
                case Object  : vm.setArrayObject(value, index, array); break ;
                default      : throw EspressoError.shouldNotReachHere("invalid array type: " + array);
            }
            // @formatter:on
            // Checkstyle: resume
        } else {
            throw meta.throwEx(meta.IllegalArgumentException);
        }
    }

    /**
     * Returns the value of the indexed component in the specified array object. The value is
     * automatically wrapped in an object if it has a primitive type.
     *
     * @param array the array
     * @param index the index
     * @return the (possibly wrapped) value of the indexed component in the specified array
     * @exception NullPointerException If the specified object is null
     * @exception IllegalArgumentException If the specified object is not an array
     * @exception ArrayIndexOutOfBoundsException If the specified {@code index} argument is
     *                negative, or if it is greater than or equal to the length of the specified
     *                array
     */
    @Substitution
    public static @Host(Object.class) StaticObject get(@Host(Object.class) StaticObject array, int index, @InjectMeta Meta meta) {
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (StaticObject.isNull(array)) {
            throw meta.throwEx(meta.NullPointerException);
        }
        if (array.isArray()) {
            // @formatter:off
            // Checkstyle: stop
            switch (array.getKlass().getComponentType().getJavaKind()) {
                case Boolean : return meta.boxBoolean(vm.getArrayByte(index, array) != 0);
                case Byte    : return meta.boxByte(vm.getArrayByte(index, array));
                case Short   : return meta.boxShort(vm.getArrayShort(index, array));
                case Char    : return meta.boxCharacter(vm.getArrayChar(index, array));
                case Int     : return meta.boxInteger(vm.getArrayInt(index, array));
                case Float   : return meta.boxFloat(vm.getArrayFloat(index, array));
                case Long    : return meta.boxLong(vm.getArrayLong(index, array));
                case Double  : return meta.boxDouble(vm.getArrayDouble(index, array));
                case Object  : return vm.getArrayObject(index, array);
                default      : throw EspressoError.shouldNotReachHere("invalid array type: " + array);
            }
            // @formatter:on
            // Checkstyle: resume
        } else {
            throw meta.throwEx(meta.IllegalArgumentException);
        }
    }

}
