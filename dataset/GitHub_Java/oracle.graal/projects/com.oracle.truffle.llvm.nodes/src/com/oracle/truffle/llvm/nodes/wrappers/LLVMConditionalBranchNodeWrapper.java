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
package com.oracle.truffle.llvm.nodes.wrappers;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.control.LLVMConditionalBranchNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class LLVMConditionalBranchNodeWrapper implements InstrumentableFactory<LLVMConditionalBranchNode> {

    @Override
    public WrapperNode createWrapper(LLVMConditionalBranchNode node, ProbeNode probe) {
        return new LLVMConditionalBranchNodeWrapper0(probe, node);
    }

    private static final class LLVMConditionalBranchNodeWrapper0 extends LLVMConditionalBranchNode implements WrapperNode {

        @Child private ProbeNode probeNode;

        @Child private LLVMConditionalBranchNode delegate;

        private LLVMConditionalBranchNodeWrapper0(ProbeNode probeNode, LLVMConditionalBranchNode delegate) {
            super(delegate.getSourceLocation());
            this.probeNode = probeNode;
            this.delegate = delegate;
        }

        @Override
        public int getSuccessorCount() {
            return delegate.getSuccessorCount();
        }

        @Override
        public LLVMExpressionNode getPhiNode(int successorIndex) {
            return delegate.getPhiNode(successorIndex);
        }

        @Override
        public boolean executeCondition(VirtualFrame frame) {
            boolean returnValue;
            for (;;) {
                boolean wasOnReturnExecuted = false;
                try {
                    probeNode.onEnter(frame);
                    returnValue = delegate.executeCondition(frame);
                    wasOnReturnExecuted = true;
                    probeNode.onReturnValue(frame, returnValue);
                    break;
                } catch (Throwable t) {
                    Object result = probeNode.onReturnExceptionalOrUnwind(frame, t, wasOnReturnExecuted);
                    if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                        continue;
                    } else if (result != null) {
                        returnValue = (boolean) result;
                        break;
                    }
                    throw t;
                }
            }
            return returnValue;
        }

        @Override
        public int getTrueSuccessor() {
            return delegate.getTrueSuccessor();
        }

        @Override
        public int getFalseSuccessor() {
            return delegate.getFalseSuccessor();
        }

        @Override
        public Node getDelegateNode() {
            return delegate;
        }

        @Override
        public ProbeNode getProbeNode() {
            return probeNode;
        }
    }
}
