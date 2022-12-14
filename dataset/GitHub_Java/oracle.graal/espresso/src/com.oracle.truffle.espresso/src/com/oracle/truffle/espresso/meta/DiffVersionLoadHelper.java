/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.meta;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.JavaVersion;

final class DiffVersionLoadHelper {
    static final VersionRange VERSION_8_OR_LOWER = VersionRange.lower(8);
    static final VersionRange VERSION_9_TO_11 = new VersionRange(9, 11);
    static final VersionRange VERSION_9_OR_HIGHER = VersionRange.higher(9);
    static final VersionRange VERSION_11_OR_HIGHER = VersionRange.higher(11);
    static final VersionRange VERSION_11_TO_17 = new VersionRange(11, 17);
    static final VersionRange VERSION_17_OR_HIGHER = VersionRange.higher(17);
    static final VersionRange ALL = new VersionRange(0, JavaVersion.LATEST_SUPPORTED);

    private final Meta meta;
    private Symbol<Name> name;
    private Symbol<Type> type;
    private Symbol<Signature> signature;

    DiffVersionLoadHelper(Meta meta) {
        this.meta = meta;
    }

    DiffVersionLoadHelper klass(VersionRange range, Symbol<Type> t) {
        if (range.contains(meta.getJavaVersion())) {
            this.type = t;
        }
        return this;
    }

    ObjectKlass klass() {
        if (type == null) {
            throw EspressoError.shouldNotReachHere();
        }
        return meta.knownKlass(type);
    }

    ObjectKlass notRequiredKlass() {
        if (type == null) {
            return null;
        }
        return meta.loadKlassWithBootClassLoader(type);
    }

    DiffVersionLoadHelper method(VersionRange range, Symbol<Name> n, Symbol<Signature> s) {
        if (range.contains(meta.getJavaVersion())) {
            this.name = n;
            this.signature = s;
        }
        return this;
    }

    Method method(ObjectKlass klass) {
        if (name == null || signature == null) {
            throw EspressoError.shouldNotReachHere();
        }
        return klass.requireDeclaredMethod(name, signature);
    }

    Method notRequiredMethod(ObjectKlass klass) {
        if (name == null || signature == null) {
            return null;
        }
        return klass.lookupDeclaredMethod(name, signature);
    }

    DiffVersionLoadHelper field(VersionRange range, Symbol<Name> n, Symbol<Type> t) {
        if (range.contains(meta.getJavaVersion())) {
            this.name = n;
            this.type = t;
        }
        return this;
    }

    Field field(ObjectKlass klass) {
        if (name == null || type == null) {
            throw EspressoError.shouldNotReachHere();
        }
        return klass.requireDeclaredField(name, type);
    }

    Field notRequiredField(ObjectKlass klass) {
        if (name == null || type == null) {
            return null;
        }
        return klass.lookupDeclaredField(name, type);
    }

    static final class VersionRange {
        final int low;
        final int high;

        VersionRange(int low, int high) {
            this.low = low;
            this.high = high;
        }

        static VersionRange lower(int version) {
            return new VersionRange(0, version);
        }

        static VersionRange higher(int version) {
            return new VersionRange(version, JavaVersion.LATEST_SUPPORTED);
        }

        boolean contains(JavaVersion version) {
            return version.inRange(low, high);
        }
    }
}
