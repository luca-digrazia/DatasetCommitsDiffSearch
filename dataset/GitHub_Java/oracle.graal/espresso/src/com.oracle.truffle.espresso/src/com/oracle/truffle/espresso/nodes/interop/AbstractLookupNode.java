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

package com.oracle.truffle.espresso.nodes.interop;

import java.util.BitSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

public abstract class AbstractLookupNode extends Node {
    public static final char METHOD_SELECTION_SEPARATOR = '/';

    abstract Method[] getMethodArray(Klass k);

    @TruffleBoundary
    Method doLookup(Klass klass, String key, boolean publicOnly, boolean isStatic, int arity) {
        String methodName;
        String signature = null;
        int separatorIndex = key.indexOf(METHOD_SELECTION_SEPARATOR);
        if (separatorIndex >= 0) {
            methodName = key.substring(0, separatorIndex);
            signature = key.substring(separatorIndex + 1);
        } else {
            methodName = key;
        }
        Method result = null;
        for (Method m : getMethodArray(klass)) {
            if (matchMethod(m, methodName, signature, isStatic, publicOnly)) {
                if (m.getParameterCount() == arity) {
                    /* Multiple methods with the same name and arity, cannot disambiguate */
                    if (result != null) {
                        return null;
                    }
                    result = m;
                }
            }
        }
        return result;
    }

    private static boolean matchMethod(Method m, String methodName, String signature, boolean isStatic, boolean publicOnly) {
        return m.isPublic() == publicOnly && m.isStatic() == isStatic && !m.isSignaturePolymorphicDeclared() &&
                        m.getName().toString().equals(methodName) && (signature == null || m.getSignatureAsString().equals(signature));
    }

    @TruffleBoundary
    protected boolean isInvocable(Klass klass, String key, boolean publicOnly, boolean isStatic) {
        String methodName;
        String signature = null;
        int separatorIndex = key.indexOf(METHOD_SELECTION_SEPARATOR);
        if (separatorIndex >= 0) {
            methodName = key.substring(0, separatorIndex);
            signature = key.substring(separatorIndex + 1);
        } else {
            methodName = key;
        }
        BitSet seenArity = new BitSet();
        // we will disambiguate overloads with arity
        for (Method m : getMethodArray(klass)) {
            if (matchMethod(m, methodName, signature, isStatic, publicOnly)) {
                int arity = m.getParameterCount();
                if (seenArity.get(arity)) {
                    return false;
                }
                seenArity.set(arity);
            }
        }
        return !seenArity.isEmpty();
    }
}
