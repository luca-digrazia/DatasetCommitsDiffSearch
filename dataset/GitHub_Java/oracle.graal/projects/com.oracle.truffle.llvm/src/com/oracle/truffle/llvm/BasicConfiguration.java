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
package com.oracle.truffle.llvm;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.options.OptionDescriptor;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.llvm.parser.NodeFactory;
import com.oracle.truffle.llvm.parser.factories.BasicNodeFactory;
import com.oracle.truffle.llvm.parser.factories.BasicSystemContextExtension;
import com.oracle.truffle.llvm.parser.factories.BasicIntrinsicsProvider;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeMemory;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

public final class BasicConfiguration implements Configuration {

    @Override
    public String getConfigurationName() {
        return "basic";
    }

    @Override
    public List<OptionDescriptor> getOptionDescriptors() {
        return SulongEngineOption.describeOptions();
    }

    @Override
    public NodeFactory getNodeFactory(LLVMContext context) {
        return new BasicNodeFactory();
    }

    @Override
    public List<ContextExtension> createContextExtensions(com.oracle.truffle.api.TruffleLanguage.Env env, TruffleLanguage<?> language) {
        List<ContextExtension> result = new ArrayList<>();
        result.add(new BasicIntrinsicsProvider(language).collectIntrinsics(new BasicNodeFactory()));
        result.add(new BasicSystemContextExtension());
        if (env.getOptions().get(SulongEngineOption.ENABLE_NFI)) {
            result.add(new NFIContextExtension(env));
        }
        return result;
    }

    @Override
    @SuppressWarnings("deprecation")
    public <E> E getCapability(Class<E> type) {
        if (type.equals(LLVMMemory.class)) {
            return type.cast(LLVMNativeMemory.getInstance());
        } else if (type.equals(UnsafeArrayAccess.class)) {
            return type.cast(UnsafeArrayAccess.getInstance());
        }
        return null;
    }
}
