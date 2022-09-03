/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.op;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNodeGen.LLVMNegateNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMPointerCompareNode extends LLVMExpressionNode {
    private final NativePointerCompare op;

    @Child private ToComparableValue convertVal1 = ToComparableValueNodeGen.create();
    @Child private ToComparableValue convertVal2 = ToComparableValueNodeGen.create();

    public LLVMPointerCompareNode(NativePointerCompare op) {
        this.op = op;
    }

    @Specialization
    protected boolean doGenericCompare(Object val1, Object val2) {
        return op.compare(convertVal1.executeWithTarget(val1), convertVal2.executeWithTarget(val2));
    }

    public enum Kind {
        ULT,
        ULE,
        SLT,
        SLE,
        EQ,
        NEQ,
    }

    protected abstract static class NativePointerCompare {
        abstract boolean compare(long val1, long val2);
    }

    public static LLVMExpressionNode create(Kind kind, LLVMExpressionNode l, LLVMExpressionNode r) {
        switch (kind) {
            case SLT:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return val1 < val2;
                    }
                }, l, r);
            case SLE:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return val1 <= val2;
                    }
                }, l, r);
            case ULE:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return Long.compareUnsigned(val1, val2) <= 0;
                    }
                }, l, r);
            case ULT:
                return LLVMPointerCompareNodeGen.create(new NativePointerCompare() {

                    @Override
                    public boolean compare(long val1, long val2) {
                        return Long.compareUnsigned(val1, val2) < 0;
                    }
                }, l, r);
            case EQ:
                return LLVMAddressEqualsNodeGen.create(l, r);
            case NEQ:
                return LLVMNegateNodeGen.create(LLVMAddressEqualsNodeGen.create(l, r));
            default:
                throw new AssertionError();

        }
    }

    public abstract static class LLVMNegateNode extends LLVMExpressionNode {
        @Child private LLVMExpressionNode booleanExpression;

        LLVMNegateNode(LLVMExpressionNode booleanExpression) {
            this.booleanExpression = booleanExpression;
        }

        @Specialization
        protected boolean doCompare(VirtualFrame frame) {
            try {
                return !booleanExpression.executeI1(frame);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }
}
