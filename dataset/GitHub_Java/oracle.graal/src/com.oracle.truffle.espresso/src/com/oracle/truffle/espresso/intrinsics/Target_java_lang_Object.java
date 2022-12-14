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

import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.runtime.StaticObjectWrapper;
import com.oracle.truffle.espresso.runtime.Utils;

import java.util.Arrays;

@EspressoIntrinsics
public class Target_java_lang_Object {
    @Intrinsic(hasReceiver = true)
    public static int hashCode(Object self) {
        // (Identity) hash code must be respected for wrappers.
        // The same object could be wrapped by two different instances of StaticObjectWrapper.
        // Wrappers are transparent, it's identity comes from the wrapped object.
        Object target = (self instanceof StaticObjectWrapper) ? ((StaticObjectWrapper) self).getWrapped() : self;
        return System.identityHashCode(target);
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) StaticObject getClass(Object self) {
        if (self instanceof StaticObject) {
            return ((StaticObject) self).getKlass().mirror();
        }
        Meta meta = Utils.getContext().getMeta();
        if (self instanceof int[]) {
            return meta.INT.array().rawKlass().mirror();
        } else if (self instanceof byte[]) {
            return meta.BYTE.array().rawKlass().mirror();
        } else if (self instanceof boolean[]) {
            return meta.BOOLEAN.array().rawKlass().mirror();
        } else if (self instanceof long[]) {
            return meta.LONG.array().rawKlass().mirror();
        } else if (self instanceof float[]) {
            return meta.FLOAT.array().rawKlass().mirror();
        } else if (self instanceof double[]) {
            return meta.DOUBLE.array().rawKlass().mirror();
        } else if (self instanceof char[]) {
            return meta.CHAR.array().rawKlass().mirror();
        } else if (self instanceof short[]) {
            return meta.SHORT.array().rawKlass().mirror();
        }
        throw EspressoError.shouldNotReachHere(".getClass failed. Non-espresso object: " + self);
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Object.class) Object clone(Object self) {
        // TODO(peterssen): self must implement Cloneable
        if (self instanceof StaticObjectArray) {
            // For arrays.
            return ((StaticObjectArray) self).clone();
        }

        if (self instanceof int[]) {
            return ((int[]) self).clone();
        } else if (self instanceof byte[]) {
            return ((byte[]) self).clone();
        } else if (self instanceof boolean[]) {
            return ((boolean[]) self).clone();
        } else if (self instanceof long[]) {
            return ((long[]) self).clone();
        } else if (self instanceof float[]) {
            return ((float[]) self).clone();
        } else if (self instanceof double[]) {
            return ((double[]) self).clone();
        } else if (self instanceof char[]) {
            return ((char[]) self).clone();
        } else if (self instanceof short[]) {
            return ((short[]) self).clone();
        }

        // Normal object just copy the
        return ((StaticObjectImpl) self).clone();
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */
    }

    @Intrinsic(hasReceiver = true)
    public static void notifyAll(Object self) {
        /* nop */
    }
}
