/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm.syscall;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.posix.LLVMAMD64PosixCallNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.posix.LLVMAMD64PosixCallNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMAMD64SyscallRecvfromNode extends LLVMSyscallOperationNode {
    @Child private LLVMAMD64PosixCallNode recvfrom;

    public LLVMAMD64SyscallRecvfromNode() {
        recvfrom = LLVMAMD64PosixCallNodeGen.create("recvfrom", "(SINT32,UINT64,UINT64,SINT32,UINT64,UINT64):SINT64");
    }

    @Override
    public final String getName() {
        return "recvfrom";
    }

    @Specialization
    protected long doOp(long socket, LLVMNativePointer buffer, long length, long flags, LLVMNativePointer address, LLVMNativePointer addressLen) {
        return (long) recvfrom.execute((int) socket, buffer.asNative(), length, (int) flags, address.asNative(), addressLen.asNative());
    }

    @Specialization
    protected long doOp(long socket, long buffer, long length, long flags, long address, long addressLen) {
        return execute(socket, LLVMNativePointer.create(buffer), length, flags, LLVMNativePointer.create(address), LLVMNativePointer.create(addressLen));
    }
}
