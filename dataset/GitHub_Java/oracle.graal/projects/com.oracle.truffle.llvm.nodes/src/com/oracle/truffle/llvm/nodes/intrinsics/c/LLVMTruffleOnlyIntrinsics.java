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
package com.oracle.truffle.llvm.nodes.intrinsics.c;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeCallUtils;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public final class LLVMTruffleOnlyIntrinsics {

    private LLVMTruffleOnlyIntrinsics() {
    }

    public abstract static class LLVMTruffleOnlyI64Intrinsic extends LLVMIntrinsic {

        @Child private Node foreignExecute;

        protected final TruffleObject nativeFunction;

        public LLVMTruffleOnlyI64Intrinsic(TruffleObject nativeSymbol, String signature, int arity) {
            nativeFunction = LLVMNativeCallUtils.bindNativeSymbol(nativeSymbol, signature);
            foreignExecute = Message.createExecute(arity).createNode();
        }

        protected final long callNative(Object... args) {
            try {
                return (long) ForeignAccess.sendExecute(foreignExecute, nativeFunction, args);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public abstract static class LLVMTruffleOnlyI32Intrinsic extends LLVMIntrinsic {

        @Child private Node foreignExecute;

        protected final TruffleObject nativeFunction;

        public LLVMTruffleOnlyI32Intrinsic(TruffleObject nativeSymbol, String signature, int arity) {
            nativeFunction = LLVMNativeCallUtils.bindNativeSymbol(nativeSymbol, signature);
            foreignExecute = Message.createExecute(arity).createNode();
        }

        protected final int callNative(Object... args) {
            try {
                return (int) ForeignAccess.sendExecute(foreignExecute, nativeFunction, args);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMStrlen extends LLVMTruffleOnlyI64Intrinsic {

        protected static final long MAX_JAVA_LEN = 512;

        public LLVMStrlen(TruffleObject nativeSymbol) {
            super(nativeSymbol, "(POINTER):SINT64", 1);
        }

        @Specialization
        public long executeIntrinsic(LLVMAddress string) {
            return strlen(string.getVal());
        }

        @Specialization
        public long executeIntrinsic(LLVMGlobalVariable string, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return strlen(globalAccess.getNativeLocation(string).getVal());
        }

        @CompilationFinal private boolean inJava = true;

        protected long strlen(long value) {
            if (inJava) {
                long s;
                for (s = value; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, LLVMMemory.getI8(s) != 0); s++) {
                }
                long result = s - value;
                if (result > MAX_JAVA_LEN) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    inJava = false;
                }
                return result;
            } else {
                return callNative(value);
            }
        }

        @Child private Node foreignHasSize = Message.HAS_SIZE.createNode();
        @Child private Node foreignGetSize = Message.GET_SIZE.createNode();
        @Child private ForeignToLLVM toLLVM = ForeignToLLVM.create(ForeignToLLVMType.I64);

        @Specialization
        public long executeIntrinsic(TruffleObject object) {
            boolean hasSize = ForeignAccess.sendHasSize(foreignHasSize, object);
            if (hasSize) {
                Object strlen;
                try {
                    strlen = ForeignAccess.sendGetSize(foreignGetSize, object);
                    long size = (long) toLLVM.executeWithTarget(strlen);
                    return size;
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(e);
                }
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(object);
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMStrCmp extends LLVMTruffleOnlyI32Intrinsic {

        public LLVMStrCmp(TruffleObject symbol) {
            super(symbol, "(POINTER,POINTER):SINT32", 2);
        }

        @Specialization
        public int executeIntrinsic(LLVMAddress str1, LLVMAddress str2) {
            return callNative(str1.getVal(), str2.getVal());
        }

        @Specialization
        public int executeIntrinsic(LLVMGlobalVariable str1, LLVMAddress str2, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return callNative(globalAccess.getNativeLocation(str1).getVal(), str2.getVal());
        }

        @Specialization
        public int executeIntrinsic(LLVMAddress str1, LLVMGlobalVariable str2, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return callNative(str1.getVal(), globalAccess.getNativeLocation(str2).getVal());
        }

        @Specialization
        public int executeIntrinsic(LLVMGlobalVariable str1, LLVMGlobalVariable str2, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess1,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess2) {
            return callNative(globalAccess1.getNativeLocation(str1).getVal(), globalAccess2.getNativeLocation(str2).getVal());
        }

        @Child private Node readStr1 = Message.READ.createNode();
        @Child private Node readStr2 = Message.READ.createNode();
        @Child private Node getSize1 = Message.GET_SIZE.createNode();
        @Child private Node getSize2 = Message.GET_SIZE.createNode();
        @Child private ForeignToLLVM toLLVMSize1 = ForeignToLLVM.create(ForeignToLLVMType.I64);
        @Child private ForeignToLLVM toLLVMSize2 = ForeignToLLVM.create(ForeignToLLVMType.I64);
        @Child private ForeignToLLVM toLLVM1 = ForeignToLLVM.create(ForeignToLLVMType.I8);
        @Child private ForeignToLLVM toLLVM2 = ForeignToLLVM.create(ForeignToLLVMType.I8);

        @Specialization(limit = "20", guards = {"getSize1(str1) == size1", "getSize2(str2) == size2"})
        @ExplodeLoop
        public int executeIntrinsic(TruffleObject str1, TruffleObject str2, @Cached("getSize1(str1)") long size1, @Cached("getSize2(str2)") long size2) {
            try {
                int i;
                for (i = 0; i < size1; i++) {
                    Object s1 = ForeignAccess.sendRead(readStr1, str1, i);
                    byte c1 = (byte) toLLVM1.executeWithTarget(s1);
                    if (i >= size2) {
                        return c1;
                    }
                    Object s2 = ForeignAccess.sendRead(readStr2, str2, i);
                    byte c2 = (byte) toLLVM2.executeWithTarget(s2);
                    if (c1 != c2) {
                        return c1 - c2;
                    }
                }
                if (i < size2) {
                    Object s2 = ForeignAccess.sendRead(readStr2, str2, i);
                    byte c2 = (byte) toLLVM2.executeWithTarget(s2);
                    return -c2;
                } else {
                    return 0;
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        protected long getSize2(TruffleObject str2) {
            try {
                return (long) toLLVMSize2.executeWithTarget(ForeignAccess.sendGetSize(getSize2, str2));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

        protected long getSize1(TruffleObject str1) {
            try {
                return (long) toLLVMSize1.executeWithTarget(ForeignAccess.sendGetSize(getSize1, str1));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

        @Specialization(guards = {"getSize2(str2) == size2"})
        @ExplodeLoop
        public int executeIntrinsic(LLVMAddress str1, TruffleObject str2, @Cached("getSize2(str2)") long size2) {
            try {
                char[] arr = new char[(int) size2];
                for (int i = 0; i < size2; i++) {
                    Object s2 = ForeignAccess.sendRead(readStr2, str2, i);
                    byte c2 = (byte) toLLVM2.executeWithTarget(s2);
                    arr[i] = (char) c2;
                }
                return compare(str1, arr);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        private static int compare(LLVMAddress str1, char[] arr) {
            int i;
            long ptr = str1.getVal();
            for (i = 0; true; i++) {
                char c1 = (char) LLVMMemory.getI8(ptr + i);
                if (c1 == '\0') {
                    break;
                }
                if (i >= arr.length) {
                    return c1;
                }
                if (c1 != arr[i]) {
                    return c1 - arr[i];
                }
            }
            if (i < arr.length) {
                return -arr[i];
            } else {
                return 0;
            }
        }
    }

}
