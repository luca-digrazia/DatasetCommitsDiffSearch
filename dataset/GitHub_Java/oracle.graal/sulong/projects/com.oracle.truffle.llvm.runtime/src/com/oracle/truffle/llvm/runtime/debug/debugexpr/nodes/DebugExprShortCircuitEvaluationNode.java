/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class DebugExprShortCircuitEvaluationNode extends LLVMExpressionNode {

    @Child private LLVMExpressionNode leftNode;
    @Child private LLVMExpressionNode rightNode;

    private final ConditionProfile evaluateRightProfile = ConditionProfile.createCountingProfile();

    public DebugExprShortCircuitEvaluationNode(LLVMExpressionNode leftNode, LLVMExpressionNode rightNode) {
        this.leftNode = leftNode;
        this.rightNode = rightNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return executeI1(frame);
    }

    @Override
    public final boolean executeI1(VirtualFrame frame) {
        boolean leftValue;
        try {
            leftValue = leftNode.executeI1(frame);
        } catch (UnexpectedResultException e) {
            throw DebugExprException.typeError(this, e.getResult(), null);
        }
        boolean rightValue;
        try {
            if (evaluateRightProfile.profile(isEvaluateRight(leftValue))) {
                rightValue = rightNode.executeI1(frame);
            } else {
                rightValue = false;
            }
        } catch (UnexpectedResultException e) {
            throw DebugExprException.typeError(this, leftValue, e.getResult());
        }
        return execute(leftValue, rightValue);
    }

    protected abstract boolean isEvaluateRight(boolean leftValue);

    /**
     * Calculates the result of the short circuit operation. If the right node is not evaluated then
     * <code>false</code> is provided.
     */
    protected abstract boolean execute(boolean leftValue, boolean rightValue);

}
