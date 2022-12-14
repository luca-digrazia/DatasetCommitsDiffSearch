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
package com.oracle.truffle.llvm.runtime.debug;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.util.HashMap;
import java.util.Map;

public final class LLVMDebugValueContainer extends LLVMDebugObject {

    public static final String FRAMESLOT_NAME = "\tSource-Level Values";
    private static final String GLOBALS_CONTAINER_NAME = "\tGlobal Variables";
    private static final LLVMSourceType TYPE = new LLVMSourceType(() -> "", 0, 0, 0) {
        @Override
        public LLVMSourceType getOffset(long newOffset) {
            return this;
        }
    };

    private final Map<Object, Object> members;

    @TruffleBoundary
    private LLVMDebugValueContainer() {
        super(null, 0, TYPE);
        members = new HashMap<>();
    }

    @TruffleBoundary
    public void addMember(Object key, Object element) {
        members.put(key, element);
    }

    @Override
    @TruffleBoundary
    public Object[] getKeys() {
        return members.keySet().toArray();
    }

    @Override
    @TruffleBoundary
    public Object getMember(Object identifier) {
        return members.get(identifier);
    }

    @Override
    protected Object getValue() {
        return "";
    }

    public static LLVMDebugValueContainer createContainer() {
        return new LLVMDebugValueContainer();
    }

    public static LLVMDebugValueContainer findOrAddGlobalsContainer(LLVMDebugValueContainer container) {
        LLVMDebugValueContainer globalsContainer = (LLVMDebugValueContainer) container.getMember(GLOBALS_CONTAINER_NAME);
        if (globalsContainer == null) {
            globalsContainer = new LLVMDebugValueContainer();
            container.addMember(GLOBALS_CONTAINER_NAME, globalsContainer);
        }
        return globalsContainer;
    }
}
