/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.graal.hosted;

import java.util.Arrays;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOf;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOfNode;
import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

import jdk.vm.ci.meta.JavaKind;

@AutomaticFeature
@Platforms(AMD64.class)
class AMD64ArrayIndexOfForeignCallsFeature implements GraalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BeforeAnalysisAccessImpl impl = (BeforeAnalysisAccessImpl) access;
        AnalysisMetaAccess metaAccess = impl.getMetaAccess();
        for (SubstrateForeignCallDescriptor descriptor : AMD64ArrayIndexOfForeignCalls.FOREIGN_CALLS) {
            AnalysisMethod method = (AnalysisMethod) descriptor.findMethod(metaAccess);
            impl.registerAsCompiled(method);
        }
    }

    @Override
    public void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<SubstrateForeignCallDescriptor, SubstrateForeignCallLinkage> foreignCalls, boolean hosted) {

        for (SubstrateForeignCallDescriptor descriptor : AMD64ArrayIndexOfForeignCalls.FOREIGN_CALLS) {
            foreignCalls.put(descriptor, new SubstrateForeignCallLinkage(providers, descriptor));
        }
    }
}

@Platforms(AMD64.class)
class AMD64ArrayIndexOfForeignCalls {
    private static final ForeignCallDescriptor[] ORIGINAL_FOREIGN_CALLS = {
                    AMD64ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_BYTES,
                    AMD64ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS,
                    AMD64ArrayIndexOf.STUB_INDEX_OF_1_BYTE,
                    AMD64ArrayIndexOf.STUB_INDEX_OF_2_BYTES,
                    AMD64ArrayIndexOf.STUB_INDEX_OF_3_BYTES,
                    AMD64ArrayIndexOf.STUB_INDEX_OF_4_BYTES,
                    AMD64ArrayIndexOf.STUB_INDEX_OF_1_CHAR,
                    AMD64ArrayIndexOf.STUB_INDEX_OF_2_CHARS,
                    AMD64ArrayIndexOf.STUB_INDEX_OF_3_CHARS,
                    AMD64ArrayIndexOf.STUB_INDEX_OF_4_CHARS,
    };

    static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = Arrays.stream(ORIGINAL_FOREIGN_CALLS)
                    .map(call -> SnippetRuntime.findForeignCall(AMD64ArrayIndexOfForeignCalls.class, call.getName(), true))
                    .toArray(SubstrateForeignCallDescriptor[]::new);

    @SubstrateForeignCallTarget
    private static int indexOfTwoConsecutiveBytes(Pointer arrayPointer, int arrayLength, int searchValue) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, true, arrayPointer, arrayLength, searchValue);
    }

    @SubstrateForeignCallTarget
    private static int indexOfTwoConsecutiveChars(Pointer arrayPointer, int arrayLength, int searchValue) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Char, true, arrayPointer, arrayLength, searchValue);
    }

    @SubstrateForeignCallTarget
    private static int indexOf1Byte(Pointer arrayPointer, int arrayLength, byte b) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, arrayPointer, arrayLength, b);
    }

    @SubstrateForeignCallTarget
    private static int indexOf2Bytes(Pointer arrayPointer, int arrayLength, byte b1, byte b2) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, arrayPointer, arrayLength, b1, b2);
    }

    @SubstrateForeignCallTarget
    private static int indexOf3Bytes(Pointer arrayPointer, int arrayLength, byte b1, byte b2, byte b3) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, arrayPointer, arrayLength, b1, b2, b3);
    }

    @SubstrateForeignCallTarget
    private static int indexOf4Bytes(Pointer arrayPointer, int arrayLength, byte b1, byte b2, byte b3, byte b4) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, arrayPointer, arrayLength, b1, b2, b3, b4);
    }

    @SubstrateForeignCallTarget
    private static int indexOf1Char(Pointer arrayPointer, int arrayLength, char c) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Char, arrayPointer, arrayLength, c);
    }

    @SubstrateForeignCallTarget
    private static int indexOf2Chars(Pointer arrayPointer, int arrayLength, char c1, char c2) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Char, arrayPointer, arrayLength, c1, c2);
    }

    @SubstrateForeignCallTarget
    private static int indexOf3Chars(Pointer arrayPointer, int arrayLength, char c1, char c2, char c3) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Char, arrayPointer, arrayLength, c1, c2, c3);
    }

    @SubstrateForeignCallTarget
    private static int indexOf4Chars(Pointer arrayPointer, int arrayLength, char c1, char c2, char c3, char c4) {
        return AMD64ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Char, arrayPointer, arrayLength, c1, c2, c3, c4);
    }
}
