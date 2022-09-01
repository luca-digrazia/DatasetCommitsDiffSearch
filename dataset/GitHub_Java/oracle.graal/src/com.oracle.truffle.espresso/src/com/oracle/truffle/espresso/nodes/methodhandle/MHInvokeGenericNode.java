/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.methodhandle;

import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Orchestrates the semantics of invoke and invoke exacts. Creating a call site for an invoke method
 * goes to java code to create an invoker method that implements type checking and the actual
 * invocation of the payload. This node is basically a bridge to the actual work.
 */
public class MHInvokeGenericNode extends MethodHandleIntrinsicNode {
    private final StaticObject appendix;
    @Child private DirectCallNode callNode;

    public MHInvokeGenericNode(Method method, StaticObject memberName, StaticObject appendix) {
        super(method);
        this.appendix = appendix;
        Method target = (Method) memberName.getHiddenField(method.getMeta().HIDDEN_VMTARGET);
        // Call the invoker java code spun for us.
        if (getContext().SplitMethodHandles) {
            this.callNode = DirectCallNode.create(target.forceSplit().getCallTarget());
        } else {
            this.callNode = DirectCallNode.create(target.getCallTarget());
        }
    }

    @Override
    public Object call(Object[] args) {
        // The quick node gave us the room to append the appendix.
        assert args[args.length - 1] == null;
        args[args.length - 1] = appendix;
        return callNode.call(args);
    }

    public static MHInvokeGenericNode create(Klass accessingKlass, Method method, Symbol<Symbol.Name> methodName, Symbol<Symbol.Signature> signature, Meta meta) {
        Klass callerKlass = accessingKlass == null ? meta.Object : accessingKlass;
        StaticObject appendixBox = StaticObject.createArray(meta.Object_array, new Object[1]);
        // Ask java code to spin an invoker for us.
        StaticObject memberName = (StaticObject) meta.MethodHandleNatives_linkMethod.invokeDirect(
                        null,
                        callerKlass.mirror(), (int) REF_invokeVirtual,
                        meta.MethodHandle.mirror(), meta.toGuestString(methodName), meta.toGuestString(signature),
                        appendixBox);
        StaticObject appendix = appendixBox.get(0);
        return new MHInvokeGenericNode(method, memberName, appendix);
    }
}
