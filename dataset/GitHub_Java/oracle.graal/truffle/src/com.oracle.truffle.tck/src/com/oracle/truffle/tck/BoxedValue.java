/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

final class BoxedValue implements TruffleObject, ForeignAccess.FactoryModel {
    private final Object value;

    BoxedValue(Object v) {
        this.value = Objects.requireNonNull(v);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(BoxedValue.class, this);
    }

    @Override
    public CallTarget accessIsNull() {
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.FALSE));
    }

    @Override
    public CallTarget accessIsExecutable() {
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.FALSE));
    }

    @Override
    public CallTarget accessIsInstantiable() {
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.FALSE));
    }

    @Override
    public CallTarget accessIsBoxed() {
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.TRUE));
    }

    @Override
    public CallTarget accessHasSize() {
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.FALSE));
    }

    @Override
    public CallTarget accessGetSize() {
        return null;
    }

    @Override
    public CallTarget accessUnbox() {
        return Truffle.getRuntime().createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                BoxedValue boxed = (BoxedValue) ForeignAccess.getReceiver(frame);
                return boxed.value;
            }
        });
    }

    @Override
    public CallTarget accessRead() {
        return null;
    }

    @Override
    public CallTarget accessWrite() {
        return null;
    }

    @Override
    public CallTarget accessExecute(int argumentsLength) {
        return null;
    }

    @Override
    public CallTarget accessInvoke(int argumentsLength) {
        return null;
    }

    @Override
    public CallTarget accessNew(int argumentsLength) {
        return null;
    }

    @Override
    public CallTarget accessMessage(Message unknown) {
        return null;
    }

    @Override
    public CallTarget accessKeyInfo() {
        return null;
    }

    @Override
    public CallTarget accessHasKeys() {
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.FALSE));
    }

    @Override
    public CallTarget accessKeys() {
        return null;
    }

    @Override
    public CallTarget accessIsPointer() {
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.FALSE));
    }

    @Override
    public CallTarget accessAsPointer() {
        return null;
    }

    @Override
    public CallTarget accessToNative() {
        return null;
    }

    @Override
    public CallTarget accessKeyDeclaredLocation() {
        return null;
    }

}
