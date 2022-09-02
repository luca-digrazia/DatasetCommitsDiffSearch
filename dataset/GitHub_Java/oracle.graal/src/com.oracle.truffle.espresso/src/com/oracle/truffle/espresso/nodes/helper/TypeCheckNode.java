/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Implements specialized type checking in espresso.
 * <p>
 * This node has 3 stages of specialization:
 * <ul>
 * <li>Trivial: When the node has seen only cases which are trivial to check, only check for those.
 * <li>Cached: The first time the node sees a non-trivial check, invalidate trivial check and start
 * using a cache.
 * <li>When the cache overflows, invalidate it, restore the trivial cases, and start specializing
 * for the general case (interface, array, regular class...)
 * </ul>
 * <p>
 * Note that this node can be used even if the type to check is known to be a constant, as all
 * checks should fold.
 */
@SuppressWarnings("unused")
public abstract class TypeCheckNode extends Node implements ContextAccess {
    protected static final int LIMIT = 5;

    private final EspressoContext context;

    public abstract boolean executeTypeCheck(Klass typeToCheck, Klass k);

    public TypeCheckNode(EspressoContext context) {
        this.context = context;
    }

    @Specialization(guards = "typeToCheck == k")
    protected boolean typeCheckEquals(Klass typeToCheck, Klass k) {
        return true;
    }

    @Specialization(guards = "isJLObject(typeToCheck)")
    protected boolean typeCheckJLObject(Klass typeToCheck, Klass k) {
        return true;
    }

    @Specialization(guards = "isFinal(typeToCheck)")
    protected boolean typeCheckFinal(ObjectKlass typeToCheck, Klass k) {
        return typeToCheck == k;
    }

    @Specialization(replaces = {"typeCheckEquals", "typeCheckJLObject", "typeCheckFinal"}, guards = {"typeToCheck == cachedTTC", "k == cachedKlass"}, limit = "LIMIT")
    protected boolean typeCheckCached(Klass typeToCheck, Klass k,
                    @Cached("typeToCheck") Klass cachedTTC,
                    @Cached("k") Klass cachedKlass,
                    @Cached("doTypeCheck(typeToCheck, k)") boolean result) {
        return result;
    }

    @Specialization(replaces = "typeCheckCached", guards = "typeToCheck == k")
    protected boolean typeCheckEqualsAfterCache(Klass typeToCheck, Klass k) {
        return true;
    }

    @Specialization(replaces = "typeCheckCached", guards = "isJLObject(typeToCheck)")
    protected boolean typeCheckJLObjectAfterCache(Klass typeToCheck, Klass k) {
        return true;
    }

    @Specialization(replaces = "typeCheckCached", guards = "isFinal(typeToCheck)")
    protected boolean typeCheckFinalAfterCache(ObjectKlass typeToCheck, Klass k) {
        return typeToCheck == k;
    }

    @Specialization(replaces = "typeCheckCached", guards = "arraySameDim(typeToCheck, k)")
    protected boolean typeCheckArraySameDim(ArrayKlass typeToCheck, ArrayKlass k,
                    @Cached("createChild()") TypeCheckNode tcn) {
        return tcn.executeTypeCheck(typeToCheck.getElementalType(), k.getElementalType());
    }

    @Specialization(replaces = "typeCheckCached", guards = "arrayBiggerDim(typeToCheck, k)")
    protected boolean typeCheckArrayBiggerDim(ArrayKlass typeToCheck, ArrayKlass k) {
        return false;
    }

    @Specialization(replaces = "typeCheckCached", guards = "arrayBiggerDim(k, typeToCheck)")
    protected boolean typeCheckArrayLowerDim(ArrayKlass typeToCheck, ArrayKlass k) {
        Klass elem = typeToCheck.getElementalType();
        return elem == getMeta().java_lang_Object || elem == getMeta().java_io_Serializable || elem == getMeta().java_lang_Cloneable;
    }

    @Specialization(replaces = "typeCheckCached", guards = "isInterface(typeToCheck)")
    protected boolean typeCheckInterface(Klass typeToCheck, Klass k) {
        return typeToCheck.checkInterfaceSubclassing(k);
    }

    @Specialization(replaces = "typeCheckCached")
    protected boolean typeCheckRegular(Klass typeToCheck, Klass k) {
        return typeToCheck.checkRegularClassSubclassing(k);
    }

    protected final boolean isJLObject(Klass k) {
        return k == getMeta().java_lang_Object;
    }

    protected static boolean isFinal(Klass k) {
        return k.isFinal();
    }

    protected static boolean isPrimitive(Klass k) {
        return k.isPrimitive();
    }

    protected static boolean doTypeCheck(Klass typeToCheck, Klass k) {
        return typeToCheck.isAssignableFrom(k);
    }

    protected boolean arraySameDim(ArrayKlass k1, ArrayKlass k2) {
        return k1.getDimension() == k2.getDimension();
    }

    protected boolean arrayBiggerDim(ArrayKlass k1, ArrayKlass k2) {
        return k1.getDimension() > k2.getDimension();
    }

    protected boolean isInterface(Klass k) {
        return k.isInterface();
    }

    protected TypeCheckNode createChild() {
        return TypeCheckNodeGen.create(context);
    }

    @Override
    public final EspressoContext getContext() {
        return context;
    }
}
