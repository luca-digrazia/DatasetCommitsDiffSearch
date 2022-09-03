/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.LLVMAddressEQNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.LLVMAddressEqualsNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.LLVMAddressNEQNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.LLVMForeignEqualsNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.LLVMManagedEqualsNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.LLVMNativeEqualsNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.ToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMAddressCompareNode extends LLVMExpressionNode {

    public enum Kind {
        ULT,
        UGT,
        UGE,
        ULE,
        SLE,
        SGT,
        SGE,
        SLT,
        EQ,
        NEQ,
    }

    public static LLVMExpressionNode create(Kind kind, LLVMExpressionNode l, LLVMExpressionNode r) {
        switch (kind) {
            case SLT:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.signedLessThan(val2);
                    }

                }, l, r);

            case SGE:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.signedGreaterEquals(val2);
                    }

                }, l, r);
            case SGT:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.signedGreaterThan(val2);
                    }

                }, l, r);
            case SLE:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.signedLessEquals(val2);
                    }

                }, l, r);
            case UGE:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.unsignedGreaterEquals(val2);
                    }

                }, l, r);
            case UGT:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.unsignedGreaterThan(val2);
                    }

                }, l, r);
            case ULE:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.unsignedLessEquals(val2);
                    }

                }, l, r);
            case ULT:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.unsignedLessThan(val2);
                    }

                }, l, r);

            case EQ:
                return LLVMAddressEQNodeGen.create(l, r);
            case NEQ:
                return LLVMAddressNEQNodeGen.create(l, r);
            default:
                throw new AssertionError();

        }
    }

    protected abstract static class AddressCompare {

        abstract boolean compare(LLVMAddress val1, LLVMAddress val2);

    }

    private final AddressCompare op;

    public LLVMAddressCompareNode(AddressCompare op) {
        this.op = op;
    }

    @NodeChild(type = LLVMExpressionNode.class)
    protected abstract static class ToComparableValue extends LLVMExpressionNode {

        protected abstract LLVMAddress executeWithTarget(Object v1);

        @Specialization
        protected LLVMAddress doAddress(long address) {
            return LLVMAddress.fromLong(address);
        }

        @Specialization
        protected LLVMAddress doAddress(LLVMAddress address) {
            return address;
        }

        @Specialization
        protected LLVMAddress doLLVMGlobalVariableDescriptor(LLVMGlobalVariable address, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return globalAccess.getNativeLocation(address);
        }

        @Specialization
        protected LLVMAddress doManagedMalloc(LLVMVirtualAllocationAddress address) {
            if (address.isNull()) {
                return LLVMAddress.fromLong(address.getOffset());
            } else {
                return LLVMAddress.fromLong(getHashCode(address.getObject()) + address.getOffset());
            }
        }

        @Child private Node isNull = Message.IS_NULL.createNode();

        @Specialization
        protected LLVMAddress doLLVMTruffleObject(LLVMTruffleObject address) {
            if (ForeignAccess.sendIsNull(isNull, address.getObject())) {
                return LLVMAddress.fromLong(address.getOffset());
            } else {
                return LLVMAddress.fromLong(getHashCode(address.getObject()) + address.getOffset());
            }
        }

        @TruffleBoundary
        private static int getHashCode(Object address) {
            return address.hashCode();
        }

        @Specialization
        protected LLVMAddress doLLVMFunction(LLVMFunction address) {
            return LLVMAddress.fromLong(address.getFunctionPointer());
        }

        @Child private ForeignToLLVM toLLVM = ForeignToLLVM.create(ForeignToLLVMType.I64);

        @Specialization
        protected LLVMAddress doLLVMBoxedPrimitive(LLVMBoxedPrimitive address) {
            return LLVMAddress.fromLong((long) toLLVM.executeWithTarget(address.getValue()));
        }
    }

    @Child private ToComparableValue convertVal1 = ToComparableValueNodeGen.create(null);
    @Child private ToComparableValue convertVal2 = ToComparableValueNodeGen.create(null);

    @Specialization
    public boolean doGenericCompare(Object val1, Object val2) {
        return op.compare(convertVal1.executeWithTarget(val1), convertVal2.executeWithTarget(val2));
    }

    @ImportStatic(JavaInterop.class)
    abstract static class LLVMForeignEqualsNode extends Node {

        abstract boolean execute(TruffleObject obj1, TruffleObject obj2);

        @Specialization(guards = {"isJavaObject(obj1)", "isJavaObject(obj2)"})
        boolean doJava(TruffleObject obj1, TruffleObject obj2) {
            return JavaInterop.asJavaObject(obj1) == JavaInterop.asJavaObject(obj2);
        }

        @Fallback
        boolean doOther(TruffleObject obj1, TruffleObject obj2) {
            return obj1 == obj2;
        }
    }

    abstract static class LLVMManagedEqualsNode extends Node {

        abstract boolean execute(Object val1, Object val2);

        @Specialization
        boolean doForeign(LLVMTruffleObject obj1, LLVMTruffleObject obj2,
                        @Cached("createForeignEquals()") LLVMForeignEqualsNode equals) {
            return equals.execute(obj1.getObject(), obj2.getObject()) && obj1.getOffset() == obj2.getOffset();
        }

        @Specialization
        boolean doGlobal(LLVMGlobalVariable g1, LLVMGlobalVariable g2) {
            return g1 == g2;
        }

        @Specialization
        boolean doVirtual(LLVMVirtualAllocationAddress v1, LLVMVirtualAllocationAddress v2) {
            return v1.getObject() == v2.getObject() && v1.getOffset() == v2.getOffset();
        }

        @Specialization(guards = "val1.getClass() != val2.getClass()")
        @SuppressWarnings("unused")
        boolean doDifferentType(Object val1, Object val2) {
            // different type, and at least one of them is managed, and not a pointer
            // these objects can not have the same address
            return false;
        }

        static LLVMForeignEqualsNode createForeignEquals() {
            return LLVMForeignEqualsNodeGen.create();
        }
    }

    abstract static class LLVMNativeEqualsNode extends Node {

        abstract boolean execute(VirtualFrame frame,
                        Object val1, LLVMObjectNativeLibrary lib1,
                        Object val2, LLVMObjectNativeLibrary lib2);

        @Specialization(guards = {"lib1.isPointer(frame, val1)", "lib2.isPointer(frame, val2)"})
        boolean doPointerPointer(VirtualFrame frame,
                        Object val1, LLVMObjectNativeLibrary lib1,
                        Object val2, LLVMObjectNativeLibrary lib2) {
            try {
                return lib1.asPointer(frame, val1) == lib2.asPointer(frame, val2);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }

        @Specialization(guards = "!lib1.isPointer(frame, val1) || !lib2.isPointer(frame, val2)")
        @SuppressWarnings("unused")
        boolean doOther(VirtualFrame frame,
                        Object val1, LLVMObjectNativeLibrary lib1,
                        Object val2, LLVMObjectNativeLibrary lib2,
                        @Cached("createManagedEquals()") LLVMManagedEqualsNode managedEquals) {
            return managedEquals.execute(val1, val2);
        }

        static LLVMManagedEqualsNode createManagedEquals() {
            return LLVMManagedEqualsNodeGen.create();
        }
    }

    abstract static class LLVMAddressEqualsNode extends Node {

        abstract boolean execute(VirtualFrame frame, Object val1, Object val2);

        @Specialization(guards = {"lib1.guard(val1)", "lib2.guard(val2)"})
        boolean doCached(VirtualFrame frame, Object val1, Object val2,
                        @Cached("createCached(val1)") LLVMObjectNativeLibrary lib1,
                        @Cached("createCached(val2)") LLVMObjectNativeLibrary lib2,
                        @Cached("createEquals()") LLVMNativeEqualsNode equals) {
            return equals.execute(frame, val1, lib1, val2, lib2);
        }

        @Specialization(replaces = "doCached", guards = {"lib.guard(val1)", "lib.guard(val2)"})
        boolean doGeneric(VirtualFrame frame, Object val1, Object val2,
                        @Cached("createGeneric()") LLVMObjectNativeLibrary lib,
                        @Cached("createEquals()") LLVMNativeEqualsNode equals) {
            return equals.execute(frame, val1, lib, val2, lib);
        }

        static LLVMNativeEqualsNode createEquals() {
            return LLVMNativeEqualsNodeGen.create();
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    abstract static class LLVMAddressEQNode extends LLVMExpressionNode {

        @Child LLVMAddressEqualsNode equals = LLVMAddressEqualsNodeGen.create();

        @Specialization
        public boolean doCompare(VirtualFrame frame, Object val1, Object val2) {
            return equals.execute(frame, val1, val2);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    abstract static class LLVMAddressNEQNode extends LLVMExpressionNode {

        @Child LLVMAddressEqualsNode equals = LLVMAddressEqualsNodeGen.create();

        @Specialization
        public boolean doCompare(VirtualFrame frame, Object val1, Object val2) {
            return !equals.execute(frame, val1, val2);
        }
    }

}
