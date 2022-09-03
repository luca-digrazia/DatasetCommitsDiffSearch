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
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ByteValueProfile;
import com.oracle.truffle.api.profiles.DoubleValueProfile;
import com.oracle.truffle.api.profiles.FloatValueProfile;
import com.oracle.truffle.api.profiles.IntValueProfile;
import com.oracle.truffle.api.profiles.LongValueProfile;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.Container.CachedLLVMAddressContainer;
import com.oracle.truffle.llvm.runtime.global.Container.CachedManagedContainer;
import com.oracle.truffle.llvm.runtime.global.Container.GenericLLVMAddressContainer;
import com.oracle.truffle.llvm.runtime.global.Container.GenericManagedContainer;
import com.oracle.truffle.llvm.runtime.global.Container.NativeContainer;
import com.oracle.truffle.llvm.runtime.global.Container.UninitializedContainer;
import com.oracle.truffle.llvm.runtime.global.Container.UninitializedManagedContainer;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccessFactory.GetContainerNodeGen;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMGlobalVariableAccess extends Node {

    abstract static class GetContainerNode extends Node {

        abstract Container executeWithTarget(Container container);

        @Specialization
        CachedLLVMAddressContainer getCachedLLVMAddressContainer(CachedLLVMAddressContainer container) {
            return container;
        }

        @Specialization
        CachedManagedContainer getCachedManagedContainer(CachedManagedContainer container) {
            return container;
        }

        @Specialization
        GenericLLVMAddressContainer getGenericLLVMAddressContainer(GenericLLVMAddressContainer container) {
            return container;
        }

        @Specialization
        GenericManagedContainer getGenericManagedContainer(GenericManagedContainer container) {
            return container;
        }

        @Specialization
        UninitializedManagedContainer getUninitializedManagedContainer(UninitializedManagedContainer container) {
            return container;
        }

        @Specialization
        NativeContainer getNativeContainer(NativeContainer container) {
            return container;
        }

        @Specialization
        UninitializedContainer getUninitializedContainer(UninitializedContainer container) {
            return container;
        }

    }

    @Child private GetContainerNode getContainer = GetContainerNodeGen.create();

    public void destroy(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.destroy();
    }

    public boolean isNative(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        return container.isNative();
    }

    public LLVMAddress getNativeLocation(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        return container.getNativeLocation(global);
    }

    public Type getType(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        return container.getType();
    }

    public void putI1(LLVMGlobalVariable global, boolean value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putI1(global, value);
    }

    public void putI8(LLVMGlobalVariable global, byte value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putI8(global, value);
    }

    public void putI16(LLVMGlobalVariable global, short value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putI16(global, value);
    }

    public void putAddress(LLVMGlobalVariable global, LLVMAddress value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putAddress(global, value);
    }

    public void putDouble(LLVMGlobalVariable global, double value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putDouble(global, value);
    }

    public void putFloat(LLVMGlobalVariable global, float value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putFloat(global, value);
    }

    public void putI64(LLVMGlobalVariable global, long value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putI64(global, value);
    }

    public void putI32(LLVMGlobalVariable global, int value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putI32(global, value);
    }

    public Object get(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        return container.get(global);
    }

    @CompilationFinal private DoubleValueProfile doubleProfile;

    public double getDouble(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        double value = container.getDouble(global);
        if (doubleProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            doubleProfile = DoubleValueProfile.createRawIdentityProfile();
        }
        return doubleProfile.profile(value);
    }

    @CompilationFinal private FloatValueProfile floatProfile;

    public float getFloat(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        float value = container.getFloat(global);
        if (floatProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            floatProfile = FloatValueProfile.createRawIdentityProfile();
        }
        return floatProfile.profile(value);
    }

    public short getI16(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        return container.getI16(global);
    }

    public boolean getI1(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        return container.getI1(global);
    }

    @CompilationFinal private IntValueProfile intProfile;

    public int getI32(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        int value = container.getI32(global);
        if (intProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            intProfile = IntValueProfile.createIdentityProfile();
        }
        return intProfile.profile(value);
    }

    @CompilationFinal private LongValueProfile longProfile;

    public long getI64(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        long value = container.getI64(global);
        if (longProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            longProfile = LongValueProfile.createIdentityProfile();
        }
        return longProfile.profile(value);
    }

    @CompilationFinal private ByteValueProfile byteProfile;

    public byte getI8(LLVMGlobalVariable global) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        byte value = container.getI8(global);
        if (byteProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            byteProfile = ByteValueProfile.createIdentityProfile();
        }
        return byteProfile.profile(value);
    }

    public void putLLVMTruffleObject(LLVMGlobalVariable global, LLVMTruffleObject value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putLLVMTruffleObject(global, value);
    }

    public void putFunction(LLVMGlobalVariable global, LLVMFunction value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putFunction(global, value);
    }

    public void putManaged(LLVMGlobalVariable global, LLVMVirtualAllocationAddress value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putManaged(global, value);
    }

    public void putBoxedPrimitive(LLVMGlobalVariable global, LLVMBoxedPrimitive value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        container.putBoxedPrimitive(global, value);
    }

    @Child private GetContainerNode getContainerOfValue;

    public void putGlobal(LLVMGlobalVariable global, LLVMGlobalVariable value) {
        Container container = getContainer.executeWithTarget(global.getContainer());
        CompilerAsserts.partialEvaluationConstant(container.getClass());
        if (container instanceof NativeContainer) {
            // special handling of NativeContainer#putGlobal
            if (getContainerOfValue == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getContainerOfValue = GetContainerNodeGen.create();
            }
            Container valueContainer = getContainerOfValue.executeWithTarget(value.getContainer());
            LLVMMemory.putAddress(((NativeContainer) container).getAddress(), valueContainer.getNativeLocation(value));
        } else {
            container.putGlobal(global, value);
        }
    }
}
