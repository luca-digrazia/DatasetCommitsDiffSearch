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
package com.oracle.truffle.llvm.nodes.vars;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.types.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.types.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.types.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.types.vector.LLVMI8Vector;

@NodeChild(value = "valueNode", type = LLVMExpressionNode.class)
@NodeField(name = "slot", type = FrameSlot.class)
public abstract class LLVMWriteVectorNode extends LLVMExpressionNode {

    protected abstract FrameSlot getSlot();

    @Specialization
    protected Object writeVector(VirtualFrame frame, LLVMDoubleVector value) {
        frame.setObject(getSlot(), value);
        return null;
    }

    @Specialization
    protected Object writeVector(VirtualFrame frame, LLVMFloatVector value) {
        frame.setObject(getSlot(), value);
        return null;
    }

    @Specialization
    protected Object writeVector(VirtualFrame frame, LLVMI16Vector value) {
        frame.setObject(getSlot(), value);
        return null;
    }

    @Specialization
    protected Object writeVector(VirtualFrame frame, LLVMI1Vector value) {
        frame.setObject(getSlot(), value);
        return null;
    }

    @Specialization
    protected Object writeVector(VirtualFrame frame, LLVMI32Vector value) {
        frame.setObject(getSlot(), value);
        return null;
    }

    @Specialization
    protected Object writeVector(VirtualFrame frame, LLVMI64Vector value) {
        frame.setObject(getSlot(), value);
        return null;
    }

    @Specialization
    protected Object writeVector(VirtualFrame frame, LLVMI8Vector value) {
        frame.setObject(getSlot(), value);
        return null;
    }

}
