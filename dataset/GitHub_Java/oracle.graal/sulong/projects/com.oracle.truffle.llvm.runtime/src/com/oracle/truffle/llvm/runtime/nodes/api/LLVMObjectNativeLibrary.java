/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.api;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;

/**
 * Interface for objects that have a real native representation, or can be transformed into one. If
 * an object or the {@link ObjectType} of a {@link DynamicObject} implements
 * {@link LLVMObjectNativeLibrary.Provider}, it can be used as a pointer value.
 */
public abstract class LLVMObjectNativeLibrary extends LLVMNode {

    public abstract boolean guard(Object obj);

    /**
     * Check whether the object has a representation as native pointer.
     */
    public abstract boolean isPointer(Object obj);

    /**
     * Check whether the object represent the NULL pointer.
     */
    public abstract boolean isNull(Object obj);

    /**
     * Get the native pointer representation of an object. This can only be called when
     * {@link #isPointer} is true.
     */
    public abstract long asPointer(Object obj) throws InteropException;

    /**
     * Transform this object to a native representation. The return value of this method should
     * return true for {@link #isPointer}. If {@link #isPointer} is already true, this method can
     * return obj without doing anything else.
     */
    public abstract Object toNative(Object obj) throws InteropException;

    public static LLVMObjectNativeLibrary createCached(Object obj) {
        return LLVMObjectNativeFactory.createCached(obj);
    }

    public static LLVMObjectNativeLibrary createGeneric() {
        return LLVMObjectNativeFactory.createGeneric();
    }

    public interface Provider {

        LLVMObjectNativeLibrary createLLVMObjectNativeLibrary();
    }
}
