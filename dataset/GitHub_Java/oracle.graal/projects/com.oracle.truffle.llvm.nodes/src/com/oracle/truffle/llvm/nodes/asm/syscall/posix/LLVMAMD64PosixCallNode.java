/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.asm.syscall.posix;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public abstract class LLVMAMD64PosixCallNode extends LLVMNode {
    private final String name;
    private final String signature;

    @Child private Node nativeExecute;

    public LLVMAMD64PosixCallNode(String name, String signature, int args) {
        this.name = name;
        this.signature = signature;
        nativeExecute = Message.createExecute(args).createNode();
    }

    protected TruffleObject createFunction() {
        LLVMContext context = getContextReference().get();
        NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
        return nfiContextExtension.getNativeFunction(context, "@__sulong_posix_" + name, signature);
    }

    // Workaround for nice syntax + Truffle DSL
    public final Object execute(Object... args) {
        return executeObject(args);
    }

    public abstract Object executeObject(Object[] args);

    @Specialization
    public Object doCall(Object[] args, @Cached("createFunction()") TruffleObject function) {
        try {
            return ForeignAccess.sendExecute(nativeExecute, function, args);
        } catch (InteropException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return "posix " + name;
    }
}
