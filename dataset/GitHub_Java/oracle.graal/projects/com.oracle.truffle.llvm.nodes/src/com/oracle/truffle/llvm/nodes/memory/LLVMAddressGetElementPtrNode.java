/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.NodeFields;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
@NodeFields({@NodeField(type = int.class, name = "typeWidth"), @NodeField(type = Type.class, name = "targetType")})
public abstract class LLVMAddressGetElementPtrNode extends LLVMExpressionNode {

    public abstract int getTypeWidth();

    public abstract Type getTargetType();

    @Specialization
    public LLVMAddress executePointee(LLVMAddress addr, int val) {
        int incr = getTypeWidth() * val;
        return addr.increment(incr);
    }

    @Specialization
    public LLVMVirtualAllocationAddress executeTruffleObject(LLVMVirtualAllocationAddress addr, int val) {
        int incr = getTypeWidth() * val;
        return addr.increment(incr);
    }

    @Specialization
    public LLVMVirtualAllocationAddress executeTruffleObject(LLVMVirtualAllocationAddress addr, long val) {
        long incr = getTypeWidth() * val;
        return addr.increment(incr);
    }

    @Specialization
    public LLVMAddress executePointee(LLVMGlobalVariable addr, int val, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        int incr = getTypeWidth() * val;
        return globalAccess.getNativeLocation(addr).increment(incr);
    }

    @Specialization
    public LLVMTruffleObject executeTruffleObject(LLVMTruffleObject addr, int val) {
        int incr = getTypeWidth() * val;
        return addr.increment(incr, new PointerType(getTargetType()));
    }

    @Specialization
    public LLVMAddress executeLLVMBoxedPrimitive(LLVMBoxedPrimitive addr, int val) {
        if (addr.getValue() instanceof Long) {
            int incr = getTypeWidth() * val;
            return LLVMAddress.fromLong((long) addr.getValue() + incr);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError("Cannot do pointer arithmetic with address: " + addr.getValue());
        }
    }

    @Specialization
    public LLVMAddress executePointee(LLVMAddress addr, long val) {
        long incr = getTypeWidth() * val;
        return addr.increment(incr);
    }

    @Specialization
    public LLVMTruffleObject executeTruffleObject(LLVMTruffleObject addr, long val) {
        long incr = getTypeWidth() * val;
        return addr.increment(incr, new PointerType(getTargetType()));
    }

    @Specialization
    public LLVMAddress executeLLVMBoxedPrimitive(LLVMBoxedPrimitive addr, long val) {
        if (addr.getValue() instanceof Long) {
            long incr = getTypeWidth() * val;
            return LLVMAddress.fromLong((long) addr.getValue() + incr);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError("Cannot do pointer arithmetic with address: " + addr.getValue());
        }
    }

    @Specialization
    public LLVMAddress executePointee(LLVMGlobalVariable addr, long val, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        long incr = getTypeWidth() * val;
        return globalAccess.getNativeLocation(addr).increment(incr);
    }

}
