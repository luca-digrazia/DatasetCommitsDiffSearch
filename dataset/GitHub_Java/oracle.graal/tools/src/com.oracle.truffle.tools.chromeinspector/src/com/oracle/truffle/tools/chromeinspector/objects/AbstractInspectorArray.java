/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.objects;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

/**
 * A base class for arrays returned by Inspector module.
 */
@MessageResolution(receiverType = AbstractInspectorArray.class)
abstract class AbstractInspectorArray implements TruffleObject {

    @Override
    public ForeignAccess getForeignAccess() {
        return AbstractInspectorArrayForeign.ACCESS;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof AbstractInspectorArray;
    }

    abstract int getLength();

    abstract Object getElementAt(int index);

    @Resolve(message = "HAS_SIZE")
    abstract static class InspectorArrayHasSizeNode extends Node {

        @SuppressWarnings("unused")
        public Object access(AbstractInspectorArray array) {
            return true;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class InspectorArrayGetSizeNode extends Node {

        public Object access(AbstractInspectorArray array) {
            return array.getLength();
        }
    }

    @Resolve(message = "READ")
    abstract static class InspectorArrayReadNode extends Node {

        public Object access(AbstractInspectorArray array, int index) {
            return array.getElementAt(index);
        }
    }
}
