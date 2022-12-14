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
package com.oracle.truffle.llvm.parser.factories;

import java.util.Arrays;

import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStatementNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI32SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI64SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI8SwitchNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;

public class LLVMSwitchFactory {

    public static LLVMStatementNode createSwitch(LLVMExpressionNode cond, int defaultLabel, int[] otherLabels, LLVMExpressionNode[] cases, LLVMBaseType llvmType) {
        switch (llvmType) {
            case I8:
                LLVMI8Node[] i8Cases = Arrays.copyOf(cases, cases.length, LLVMI8Node[].class);
                return new LLVMI8SwitchNode((LLVMI8Node) cond, i8Cases, otherLabels, defaultLabel);
            case I32:
                LLVMI32Node[] i32Cases = Arrays.copyOf(cases, cases.length, LLVMI32Node[].class);
                return new LLVMI32SwitchNode((LLVMI32Node) cond, i32Cases, otherLabels, defaultLabel);
            case I64:
                LLVMI64Node[] i64Cases = Arrays.copyOf(cases, cases.length, LLVMI64Node[].class);
                return new LLVMI64SwitchNode((LLVMI64Node) cond, i64Cases, otherLabels, defaultLabel);
            default:
                throw new AssertionError(llvmType);
        }
    }

}
