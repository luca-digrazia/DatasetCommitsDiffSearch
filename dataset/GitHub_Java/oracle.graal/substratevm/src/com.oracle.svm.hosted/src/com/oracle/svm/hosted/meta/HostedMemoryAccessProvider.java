/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;

public class HostedMemoryAccessProvider implements MemoryAccessProvider {

    private final HostedMetaAccess metaAccess;

    public HostedMemoryAccessProvider(HostedMetaAccess metaAccess) {
        this.metaAccess = metaAccess;
    }

    @Override
    public JavaConstant readPrimitiveConstant(JavaKind stackKind, Constant base, long displacement, int bits) {

        JavaConstant result = doRead(stackKind, (JavaConstant) base, displacement);

        if (result == null) {
            return null;
        }

        /* Wrap the result in the expected stack kind. */
        JavaConstant wrappedResult;
        switch (stackKind) {
            case Int:
                wrappedResult = JavaConstant.forInt(result.asInt());
                break;
            case Long:
            case Float:
            case Double:
                assert result.getJavaKind() == stackKind;
                wrappedResult = result;
                break;
            default:
                throw VMError.shouldNotReachHere();
        }

        return wrappedResult;
    }

    @Override
    public JavaConstant readObjectConstant(Constant base, long displacement) {
        return doRead(JavaKind.Object, (JavaConstant) base, displacement);
    }

    /**
     * Try to look up the original field from the given displacement, and read the field value.
     */
    private JavaConstant doRead(JavaKind stackKind, JavaConstant base, long displacement) {
        if (base.getJavaKind() != JavaKind.Object) {
            return null;
        }
        HostedType type = (HostedType) metaAccess.lookupJavaType(base);
        HostedField field = (HostedField) type.findInstanceFieldWithOffset(displacement, null);
        if (field == null) {
            return null;
        }

        assert field.getStorageKind().getStackKind() == stackKind;

        JavaConstant result = field.readValue(base);
        assert result.getJavaKind().getStackKind() == stackKind;

        return result;
    }
}
