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
package com.oracle.truffle.llvm.runtime.nodes.api;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.instrumentation.LLVMNodeObject;
import org.graalvm.collections.EconomicMap;

public final class LLVMNodeSourceDescriptor {

    private static final SourceSection DEFAULT_SOURCE_SECTION;

    static {
        final Source source = Source.newBuilder("llvm", "LLVM IR", "<llvm ir>").mimeType("text/plain").build();
        DEFAULT_SOURCE_SECTION = source.createUnavailableSection();
    }

    @CompilationFinal private LLVMSourceLocation sourceLocation;
    @CompilationFinal(dimensions = 1) private Class<? extends Tag>[] tags;
    @CompilationFinal private EconomicMap<String, Object> nodeObjectEntries;

    public LLVMSourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public SourceSection getSourceSection() {
        if (sourceLocation == null) {
            return DEFAULT_SOURCE_SECTION;
        }
        return sourceLocation.getSourceSection();
    }

    public Class<? extends Tag>[] getTags() {
        return tags;
    }

    public boolean hasTag(Class<? extends Tag> tag) {
        if (tags != null) {
            for (Class<? extends Tag> providedTag : tags) {
                if (tag == providedTag) {
                    return true;
                }
            }
        }
        return false;
    }

    public Object getNodeObject() {
        return LLVMNodeObject.create(nodeObjectEntries);
    }

    public void setSourceLocation(LLVMSourceLocation sourceLocation) {
        CompilerAsserts.neverPartOfCompilation();
        this.sourceLocation = sourceLocation;
    }

    public void setTags(Class<? extends Tag>[] tags) {
        CompilerAsserts.neverPartOfCompilation();
        this.tags = tags;
    }

    public void setNodeObjectEntries(EconomicMap<String, Object> nodeObject) {
        CompilerAsserts.neverPartOfCompilation();
        this.nodeObjectEntries = nodeObject;
    }
}
