/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test.interop.values;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = ArrayObject.class)
public final class ArrayObject implements TruffleObject {

    final Object[] array;

    public ArrayObject(Object... array) {
        this.array = array;
    }

    public Object get(int i) {
        return array[i];
    }

    static boolean isInstance(TruffleObject object) {
        return object instanceof ArrayObject;
    }

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {

        Object access(ArrayObject obj, Number idx) {
            return obj.array[(int) idx.longValue()];
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {

        Object access(ArrayObject obj, Number idx, Object value) {
            return obj.array[(int) idx.longValue()] = value;
        }
    }

    @Resolve(message = "REMOVE")
    abstract static class RemoveNode extends Node {

        boolean access(ArrayObject obj, Number idx) {
            obj.array[(int) idx.longValue()] = "<removed>";
            return true;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class SizeNode extends Node {

        int access(ArrayObject obj) {
            return obj.array.length;
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return ArrayObjectForeign.ACCESS;
    }
}
