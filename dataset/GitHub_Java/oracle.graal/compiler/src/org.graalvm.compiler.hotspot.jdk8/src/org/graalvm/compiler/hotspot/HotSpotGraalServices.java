/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import java.util.Objects;

import jdk.vm.ci.hotspot.HotSpotMetaData;
import jdk.vm.ci.hotspot.HotSpotObjectConstantScope;

/**
 * Interface to HotSpot specific functionality that abstracts over which JDK version Graal is
 * running on.
 */
public class HotSpotGraalServices {

    /**
     * Get the implicit exceptions section of a {@code HotSpotMetaData} if it exists.
     */
    @SuppressWarnings("unused")
    public static byte[] getImplicitExceptionBytes(HotSpotMetaData metaData) {
        // Only supported by JDK13
        return null;
    }

    public static CompilationContext enterGlobalCompilationContext() {
        HotSpotObjectConstantScope impl = HotSpotObjectConstantScope.enterGlobalScope();
        return impl == null ? null : new CompilationContext(impl);
    }

    public static CompilationContext openLocalCompilationContext(Object description) {
        HotSpotObjectConstantScope impl = HotSpotObjectConstantScope.openLocalScope(Objects.requireNonNull(description));
        return impl == null ? null : new CompilationContext(impl);
    }
}
