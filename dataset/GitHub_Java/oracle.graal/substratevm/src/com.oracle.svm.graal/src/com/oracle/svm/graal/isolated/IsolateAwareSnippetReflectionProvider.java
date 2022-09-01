/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

final class IsolateAwareSnippetReflectionProvider implements SnippetReflectionProvider {
    @Override
    public JavaConstant forObject(Object object) {
        VMError.guarantee(!SubstrateOptions.shouldCompileInIsolates() || ImageHeapObjects.isInImageHeap(object));
        return SubstrateObjectConstant.forObject(object);
    }

    @Override
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        @SuppressWarnings("unchecked")
        T object = (T) KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(type, constant), Object.class);
        VMError.guarantee(!SubstrateOptions.shouldCompileInIsolates() || ImageHeapObjects.isInImageHeap(object));
        return object;
    }

    @Override
    public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public Class<?> originalClass(ResolvedJavaType type) {
        throw VMError.shouldNotReachHere();
    }
}
