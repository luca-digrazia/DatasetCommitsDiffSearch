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
package com.oracle.truffle.llvm.runtime.nodes.others;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMReplaceGlobalVariableStorageNode extends LLVMNode {

    private final LLVMGlobal descriptor;

    LLVMReplaceGlobalVariableStorageNode(LLVMGlobal descriptor) {
        this.descriptor = descriptor;
    }

    public abstract void execute( LLVMPointer value);

    @Specialization
    void doReplacee(LLVMPointer value,
                 @CachedContext(LLVMLanguage.class) LLVMContext context,
                 @Cached LLVMReplaceGlobalVariableStorageNode.ReplaceDynamicObjectHelper replaceHelper) {
        replaceHelper.execute(context.getGlobalStorage(), descriptor, value);
    }

    abstract static class ReplaceDynamicObjectHelper extends LLVMNode {

        public abstract void execute(DynamicObject object, LLVMGlobal descriptor, LLVMPointer value);

        @SuppressWarnings("unused")
        @Specialization(limit = "3", //
                guards = {
                        "object.getShape() == cachedShape",
                        "loc != null",
                        "loc.canSet(value)"
                }, //
                assumptions = {
                        "layoutAssumption"

                })
        protected void doDirect(DynamicObject object, LLVMGlobal descriptor, LLVMPointer value,
                                @Cached("object.getShape()") Shape cachedShape,
                                @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                                @Cached("cachedShape.getProperty(descriptor).getLocation()") Location loc) {
            CompilerAsserts.partialEvaluationConstant(descriptor);
            try {
                loc.set(object, value);
            } catch (IncompatibleLocationException | FinalLocationException e) {
                CompilerDirectives.transferToInterpreter();
                // cannot happen due to guard
                throw new RuntimeException("Location.canSet is inconsistent with Location.set");
            }
        }

        @TruffleBoundary
        @Specialization(guards = {
                "object.getShape().isValid()"
        }, replaces = {"doDirect"})
        protected static void doIndirect(DynamicObject object, LLVMGlobal descriptor, LLVMPointer value) {
            object.set(descriptor, value);
        }

        @Specialization(guards = "!object.getShape().isValid()")
        protected static void updateIndirect(DynamicObject object, LLVMGlobal descriptor, LLVMPointer value) {
            CompilerDirectives.transferToInterpreter();
            object.updateShape();
            doIndirect(object, descriptor, value);
        }
    }
}
