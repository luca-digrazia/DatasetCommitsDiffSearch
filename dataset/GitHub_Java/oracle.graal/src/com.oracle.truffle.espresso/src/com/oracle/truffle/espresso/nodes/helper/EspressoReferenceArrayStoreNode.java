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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class EspressoReferenceArrayStoreNode extends Node {
    @Child TypeCheckNode typeCheck;
    @CompilerDirectives.CompilationFinal boolean noOutOfBoundEx = true;
    @CompilerDirectives.CompilationFinal boolean noArrayStoreEx = true;

    public EspressoReferenceArrayStoreNode(EspressoContext context) {
        this.typeCheck = TypeCheckNodeGen.create(context);
    }

    public void arrayStore(StaticObject value, int index, StaticObject array) {
        assert !array.isForeignObject();
        assert array.isArray();
        if (index >= 0 && index < array.length()) {
            if (StaticObject.isNull(value) || typeCheck.executeTypeCheck(((ArrayKlass) array.getKlass()).getComponentType(), value.getKlass())) {
                array.putObjectUnsafe(value, index);
            } else {
                enterArrayStoreEx();
                throw Meta.throwException(typeCheck.getMeta().java_lang_ArrayStoreException);
            }
        } else {
            enterOutOfBound();
            throw Meta.throwException(typeCheck.getMeta().java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    private void enterOutOfBound() {
        if (noOutOfBoundEx) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            noOutOfBoundEx = false;
        }
    }

    private void enterArrayStoreEx() {
        if (noArrayStoreEx) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            noArrayStoreEx = false;
        }
    }
}
