/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.interop.java;

import java.lang.reflect.Field;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

abstract class LookupFieldNode extends Node {
    static final int LIMIT = 3;

    LookupFieldNode() {
    }

    static LookupFieldNode create() {
        return LookupFieldNodeGen.create();
    }

    public abstract Field execute(JavaObject object, String name);

    @SuppressWarnings("unused")
    @Specialization(guards = {"cachedField != null", "object.getClazz() == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
    static Field doCached(JavaObject object, String name,
                    @Cached("object.getClazz()") Class<?> cachedClazz,
                    @Cached("name") String cachedName,
                    @Cached("doUncached(object, name)") Field cachedField) {
        assert cachedField == JavaInteropReflect.findField(object, name);
        return cachedField;
    }

    @Specialization(replaces = "doCached")
    static Field doUncached(JavaObject object, String name) {
        return JavaInteropReflect.findField(object, name);
    }
}
