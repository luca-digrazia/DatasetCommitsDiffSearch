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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * This method is usually responsible for invoking the type-checking lambda forms. As such, its job
 * it to extract the method handle (given as receiver), and find a way to the payload.
 */
public abstract class MHInvokeBasicNode extends MethodHandleIntrinsicNode {

    private final int form;
    private final int vmentry;
    private final int hiddenVmtarget;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract Object executeCall(Object[] args, Method target);

    public static boolean canInline(Method target, Method cachedTarget) {
        return target.identity() == cachedTarget.identity();
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = {"inliningEnabled()", "canInline(target, cachedTarget)"})
    Object executeCallDirect(Object[] args, Method target,
                    @Cached("target") Method cachedTarget,
                    @Cached("create(target.getCallTarget())") DirectCallNode directCallNode) {
        return directCallNode.call(args);
    }

    @Specialization(replaces = "executeCallDirect")
    Object executeCallIndirect(Object[] args, Method target,
                    @Cached("create()") IndirectCallNode callNode) {
        return callNode.call(target.getCallTarget(), args);
    }

    public MHInvokeBasicNode(Method method) {
        super(method);
        Meta meta = getMeta();
        this.form = meta.MethodHandle_form.getFieldIndex();
        this.vmentry = meta.LambdaForm_vmentry.getFieldIndex();
        this.hiddenVmtarget = meta.HIDDEN_VMTARGET.getFieldIndex();
    }

    @Override
    public Object call(Object[] args) {
        StaticObject mh = (StaticObject) args[0];
        StaticObject lform = (StaticObject) mh.getUnsafeField(form);
        StaticObject mname = (StaticObject) lform.getUnsafeField(vmentry);
        Method target = (Method) mname.getUnsafeField(hiddenVmtarget);
        return executeCall(args, target);
    }
}
