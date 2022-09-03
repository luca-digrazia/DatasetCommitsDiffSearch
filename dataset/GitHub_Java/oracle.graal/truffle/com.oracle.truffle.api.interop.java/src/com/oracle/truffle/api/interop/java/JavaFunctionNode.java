/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.nodes.RootNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

final class JavaFunctionNode extends RootNode {
    JavaFunctionNode() {
        super(JavaInteropLanguage.class, null, null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        JavaInterop.JavaFunctionObject receiver = (JavaInterop.JavaFunctionObject) ForeignAccess.getReceiver(frame);
        List<Object> args = ForeignAccess.getArguments(frame);
        return execute(receiver, args.toArray());
    }

    static Object execute(JavaInterop.JavaFunctionObject receiver, Object[] args) {
        return execute(receiver.method, receiver.obj, args);
    }

    @SuppressWarnings("paramAssign")
    @TruffleBoundary
    static Object execute(Method method, Object obj, Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof JavaInterop.JavaObject) {
                args[i] = ((JavaInterop.JavaObject) args[i]).obj;
            }
        }
        try {
            Object ret = method.invoke(obj, args);
            if (JavaInterop.isPrimitive(ret)) {
                return ret;
            }
            return JavaInterop.asTruffleObject(ret);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
