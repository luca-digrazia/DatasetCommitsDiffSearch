/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import com.oracle.graal.api.meta.*;

/**
 * Represents a constant non-{@code null} object reference, within the compiler and across the
 * compiler/runtime interface.
 */
public final class HotSpotObjectConstantImpl extends JavaConstant implements HotSpotObjectConstant {

    private static final long serialVersionUID = 3592151693708093496L;

    public static JavaConstant forObject(Object object) {
        if (object == null) {
            return JavaConstant.NULL_OBJECT;
        } else {
            return new HotSpotObjectConstantImpl(object, false);
        }
    }

    public static JavaConstant forBoxedValue(Kind kind, Object value) {
        if (kind == Kind.Object) {
            return HotSpotObjectConstantImpl.forObject(value);
        } else {
            return JavaConstant.forBoxedPrimitive(value);
        }
    }

    public static Object asObject(Constant constant) {
        if (JavaConstant.isNull(constant)) {
            return null;
        } else {
            return ((HotSpotObjectConstantImpl) constant).object;
        }
    }

    public static Object asBoxedValue(Constant constant) {
        if (JavaConstant.isNull(constant)) {
            return null;
        } else if (constant instanceof HotSpotObjectConstantImpl) {
            return ((HotSpotObjectConstantImpl) constant).object;
        } else {
            return ((JavaConstant) constant).asBoxedPrimitive();
        }
    }

    public static boolean isCompressed(Constant constant) {
        if (JavaConstant.isNull(constant)) {
            return HotSpotCompressedNullConstant.NULL_OBJECT.equals(constant);
        } else {
            return ((HotSpotObjectConstantImpl) constant).compressed;
        }
    }

    private final Object object;
    private final boolean compressed;

    private HotSpotObjectConstantImpl(Object object, boolean compressed) {
        super(LIRKind.reference(compressed ? Kind.Int : Kind.Object));
        this.object = object;
        this.compressed = compressed;
        assert object != null;
    }

    public JavaConstant compress() {
        assert !compressed;
        return new HotSpotObjectConstantImpl(object, true);
    }

    public JavaConstant uncompress() {
        assert compressed;
        return new HotSpotObjectConstantImpl(object, false);
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public Object asBoxedPrimitive() {
        throw new IllegalArgumentException();
    }

    @Override
    public int asInt() {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean asBoolean() {
        throw new IllegalArgumentException();
    }

    @Override
    public long asLong() {
        throw new IllegalArgumentException();
    }

    @Override
    public float asFloat() {
        throw new IllegalArgumentException();
    }

    @Override
    public double asDouble() {
        throw new IllegalArgumentException();
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(object);
    }

    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof HotSpotObjectConstantImpl && super.equals(o) && object == ((HotSpotObjectConstantImpl) o).object);
    }

    @Override
    public String toValueString() {
        if (object instanceof String) {
            return (String) object;
        } else {
            return Kind.Object.format(object);
        }
    }

    @Override
    public String toString() {
        return (compressed ? "NarrowOop" : getKind().getJavaName()) + "[" + Kind.Object.format(object) + "]";
    }
}
