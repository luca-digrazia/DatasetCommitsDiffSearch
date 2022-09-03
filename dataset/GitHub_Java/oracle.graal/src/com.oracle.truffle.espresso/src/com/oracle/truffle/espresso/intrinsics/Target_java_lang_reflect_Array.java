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

package com.oracle.truffle.espresso.intrinsics;

import java.lang.reflect.Array;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;

@EspressoIntrinsics
public class Target_java_lang_reflect_Array {

    @Intrinsic
    public static Object newArray(@Type(Class.class) StaticObjectClass componentType, int length) {
        if (componentType.getMirror().isPrimitive()) {
            byte jvmPrimitiveType = (byte) componentType.getMirror().getJavaKind().getBasicType();
            return InterpreterToVM.allocateNativeArray(jvmPrimitiveType, length);
        }
        InterpreterToVM vm = EspressoLanguage.getCurrentContext().getVm();
        return vm.newArray(componentType.getMirror(), length);
    }

    @Intrinsic
    public static Object multiNewArray(@Type(Class.class) StaticObject componentType,
                    int[] dimensions) {
        return EspressoLanguage.getCurrentContext().getVm().newMultiArray(((StaticObjectClass) componentType).getMirror(), dimensions);
    }

    @Intrinsic
    public static int getLength(Object array) {
        try {
            return Array.getLength(MetaUtil.unwrap(array));
        } catch (IllegalArgumentException | NullPointerException e) {
            EspressoContext context = EspressoLanguage.getCurrentContext();
            throw context.getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Intrinsic
    public static boolean getBoolean(Object array, int index) {
        try {
            return Array.getBoolean(MetaUtil.unwrap(array), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static byte getByte(Object array, int index) {
        try {
            return Array.getByte(MetaUtil.unwrap(array), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static char getChar(Object array, int index) {
        try {
            return Array.getChar(MetaUtil.unwrap(array), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static short getShort(Object array, int index) {
        try {
            return Array.getShort(MetaUtil.unwrap(array), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static int getInt(Object array, int index) {
        try {
            return Array.getInt(MetaUtil.unwrap(array), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static float getFloat(Object array, int index) {
        try {
            return Array.getFloat(MetaUtil.unwrap(array), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static double getDouble(Object array, int index) {
        try {
            return Array.getDouble(MetaUtil.unwrap(array), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static long getLong(Object array, int index) {
        try {
            return Array.getLong(MetaUtil.unwrap(array), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static void setBoolean(Object array, int index, boolean value) {
        try {
            Array.setBoolean(MetaUtil.unwrap(array), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static void setByte(Object array, int index, byte value) {
        try {
            Array.setByte(MetaUtil.unwrap(array), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static void setChar(Object array, int index, char value) {
        try {
            Array.setChar(((StaticObjectArray) MetaUtil.unwrap(array)).getWrapped(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static void setShort(Object array, int index, short value) {
        try {
            Array.setShort(MetaUtil.unwrap(array), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static void setInt(Object array, int index, int value) {
        try {
            Array.setInt(MetaUtil.unwrap(array), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static void setFloat(Object array, int index, float value) {
        try {
            Array.setFloat(MetaUtil.unwrap(array), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static void setDouble(Object array, int index, double value) {
        try {
            Array.setDouble(MetaUtil.unwrap(array), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static void setLong(Object array, int index, long value) {
        try {
            Array.setLong(MetaUtil.unwrap(array), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static Object get(Object array, int index) {
        try {
            return Array.get(MetaUtil.unwrap(array), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass());
        }
    }

    @Intrinsic
    public static void set(Object array, int index, Object value) {
        if (array instanceof StaticObjectArray) {
            EspressoLanguage.getCurrentContext().getVm().setArrayObject(value, index, (StaticObjectArray) array);
        } else {
            if (array == StaticObject.NULL) {
                throw EspressoLanguage.getCurrentContext().getMeta().throwEx(NullPointerException.class);
            } else {
                throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
            }
        }
    }
}
