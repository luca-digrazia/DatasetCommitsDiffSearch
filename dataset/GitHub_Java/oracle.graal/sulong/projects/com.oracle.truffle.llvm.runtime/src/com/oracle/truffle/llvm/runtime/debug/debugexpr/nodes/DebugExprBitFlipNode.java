package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild(value = "child", type = LLVMExpressionNode.class)
public abstract class DebugExprBitFlipNode extends LLVMExpressionNode {
    public abstract Object executeWithTarget(Object child);

    @NodeInfo(shortName = "~")
    public abstract static class BitFlipNode extends DebugExprBitFlipNode {
        @Specialization
        protected boolean flip(boolean child) {
            return !child;
        }

        @Specialization
        protected byte flip(byte child) {
            return (byte) ~child;
        }

        @Specialization
        protected short flip(short child) {
            return (short) ~child;
        }

        @Specialization
        protected char flip(char child) {
            return (char) ~child;
        }

        @Specialization
        protected int flip(int child) {
            return ~child;
        }

        @Specialization
        protected long flip(long child) {
            return ~child;
        }

        @Fallback
        protected Object typeError(Object child) {
            throw DebugExprException.typeError(this, child);
        }

    }

}
