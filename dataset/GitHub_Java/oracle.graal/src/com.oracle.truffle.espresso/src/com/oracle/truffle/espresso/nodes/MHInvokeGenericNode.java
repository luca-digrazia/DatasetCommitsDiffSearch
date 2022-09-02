/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class MHInvokeGenericNode extends EspressoBaseNode {
    private final int argCount;
    private final StaticObject appendix;
    @Child private DirectCallNode callNode;

    public MHInvokeGenericNode(Method method, StaticObject memberName, StaticObject appendix) {
        super(method);
        this.argCount = Signatures.parameterCount(getMethod().getParsedSignature(), false);
        this.appendix = appendix;
        Method target = (Method) memberName.getHiddenField(method.getMeta().HIDDEN_VMTARGET);
        this.callNode = DirectCallNode.create(target.getCallTarget());
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        Object[] args = new Object[argCount + 2];
        assert (getMethod().hasReceiver());
        args[0] = frame.getArguments()[0];
        copyOfRange(frame.getArguments(), 1, args, 1, argCount);
        args[args.length - 1] = appendix;
        return callNode.call(args);
    }

    @ExplodeLoop
    private static void copyOfRange(Object[] src, int from, Object[] dst, int start, final int length) {
        assert (src.length >= from + length && dst.length >= start + length);
        for (int i = 0; i < length; ++i) {
            dst[i + start] = src[i + from];
        }
    }
}
