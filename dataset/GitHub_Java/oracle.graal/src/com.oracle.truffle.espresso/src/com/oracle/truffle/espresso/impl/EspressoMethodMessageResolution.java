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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.interop.ForeignToStaticObjectNode;
import com.oracle.truffle.espresso.interop.ForeignToStaticObjectNodeGen;
import com.oracle.truffle.espresso.interop.StaticObjectToForeignNode;
import com.oracle.truffle.espresso.interop.StaticObjectToForeignNodeGen;
import com.oracle.truffle.espresso.meta.Meta;

@MessageResolution(receiverType = Method.class)
public class EspressoMethodMessageResolution {

    @Resolve(message = "IS_EXECUTABLE")
    public abstract static class EspressoMethodForeignIsExecutableNode extends Node {

        public Object access(Object receiver) {
            return receiver instanceof Method;
        }
    }

    @Resolve(message = "EXECUTE")
    public abstract static class EspressoMethodExecuteNode extends Node {

        @Child private IndirectCallNode callNode = IndirectCallNode.create();
        @Child private StaticObjectToForeignNode toForeign = StaticObjectToForeignNodeGen.create();
        @Child private ForeignToStaticObjectNode toEspresso = ForeignToStaticObjectNodeGen.create();

        public Object access(Method receiver, Object[] arguments) {
            Meta meta = receiver.getDeclaringKlass().getMeta();
            Object[] arr = new Object[arguments.length];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = toEspresso.executeConvert(meta, arguments[i]);
            }
            Object result = callNode.call(receiver.getCallTarget(), arr);
            return toForeign.executeConvert(meta, result);
        }
    }

    @CanResolve
    public abstract static class CanResolveMethod extends Node {
        boolean test(TruffleObject object) {
            return object instanceof Method;
        }
    }
}
