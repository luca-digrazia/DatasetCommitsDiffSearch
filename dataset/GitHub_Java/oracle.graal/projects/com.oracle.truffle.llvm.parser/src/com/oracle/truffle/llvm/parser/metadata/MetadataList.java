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
package com.oracle.truffle.llvm.parser.metadata;

import java.util.ArrayList;
import java.util.List;

public final class MetadataList {

    private final List<MDBaseNode> metadata = new ArrayList<>();
    private final List<MDNamedNode> namedMetadata = new ArrayList<>();
    private final List<MDKind> mdKinds = new ArrayList<>();

    public MetadataList() {
    }

    public void add(MDBaseNode element) {
        metadata.add(element);
    }

    public void addNamed(MDNamedNode namedNode) {
        namedMetadata.add(namedNode);
    }

    public MDNamedNode find(String name) {
        for (MDNamedNode namedNode : namedMetadata) {
            if (namedNode.getName().equals(name)) {
                return namedNode;
            }
        }
        return null;
    }

    public void addKind(MDKind newKind) {
        mdKinds.add(newKind);
    }

    public MDBaseNode removeLast() {
        if (metadata.size() <= 0) {
            throw new IllegalStateException();
        }
        return metadata.remove(metadata.size() - 1);
    }

    public MDReference getMDRef(long index) {
        return MDReference.fromIndex((int) index, this);
    }

    public MDReference getMDRefOrNullRef(long index) {
        // offsets into the metadatalist are incremented by 1 so 0 can indicate a nullpointer
        if (index == 0) {
            return MDReference.VOID;
        } else {
            return getMDRef(index - 1);
        }
    }

    MDBaseNode getFromRef(int index) {
        if (index >= metadata.size()) {
            return MDReference.VOID;
        }
        return metadata.get(index);
    }

    public MDKind getKind(long id) {
        for (MDKind kind : mdKinds) {
            if (kind.getId() == id) {
                return kind;
            }
        }
        throw new AssertionError("No kind with id: " + id);
    }

    public int size() {
        return metadata.size();
    }

    public void accept(MetadataVisitor visitor) {
        mdKinds.forEach(md -> md.accept(visitor));
        namedMetadata.forEach(md -> md.accept(visitor));
        metadata.forEach(md -> md.accept(visitor));
    }

    @Override
    public String toString() {
        return String.format("MetadataList (size=%d)", size());
    }

    public void initialize(MetadataList other) {
        metadata.addAll(other.metadata);
        namedMetadata.addAll(other.namedMetadata);
        mdKinds.addAll(other.mdKinds);
    }
}
