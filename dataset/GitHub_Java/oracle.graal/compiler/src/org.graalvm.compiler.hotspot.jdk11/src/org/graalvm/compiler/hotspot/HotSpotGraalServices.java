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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaData;
import jdk.vm.ci.services.Services;

/**
 * LabsJDK 11 version of {@code HotSpotGraalServices}.
 */
public class HotSpotGraalServices {

    private static final MethodHandle metaDataImplicitExceptionBytes;
    private static final MethodHandle runtimeExitHotSpot;
    private static final MethodHandle scopeOpenLocalScope;
    private static final MethodHandle scopeEnterGlobalScope;

    static {
        MethodHandle implicitExceptionBytes = null;
        MethodHandle exitHotSpot = null;
        MethodHandle enterGlobalScope = null;
        MethodHandle openLocalScope = null;
        boolean firstFound = false;
        try {
            Class<?> scopeClass = Class.forName("jdk.vm.ci.hotspot.HotSpotObjectConstantScope");
            enterGlobalScope = MethodHandles.lookup().unreflect(scopeClass.getDeclaredMethod("enterGlobalScope"));
            firstFound = true;
            openLocalScope = MethodHandles.lookup().unreflect(scopeClass.getDeclaredMethod("openLocalScope", Object.class));
            implicitExceptionBytes = MethodHandles.lookup().unreflect(HotSpotMetaData.class.getDeclaredMethod("implicitExceptionBytes"));
            exitHotSpot = MethodHandles.lookup().unreflect(HotSpotJVMCIRuntime.class.getDeclaredMethod("exitHotSpot", Integer.TYPE));
        } catch (Exception e) {
            // If the very first method is unavailable assume nothing is available. Otherwise only
            // some are missing so complain about it.
            if (firstFound) {
                throw new InternalError("some methods are unavailable", e);
            }
        }
        metaDataImplicitExceptionBytes = implicitExceptionBytes;
        runtimeExitHotSpot = exitHotSpot;
        scopeOpenLocalScope = openLocalScope;
        scopeEnterGlobalScope = enterGlobalScope;
        assert (implicitExceptionBytes != null) == (exitHotSpot != null);
        assert (implicitExceptionBytes != null) == (openLocalScope != null);
        assert (implicitExceptionBytes != null) == (enterGlobalScope != null);
    }

    /**
     * Get the implicit exceptions section of a {@code HotSpotMetaData} if it exists.
     */
    @SuppressWarnings("unused")
    public static byte[] getImplicitExceptionBytes(HotSpotMetaData metaData) {
        if (metaDataImplicitExceptionBytes == null) {
            return null;
        }
        try {
            return (byte[]) metaDataImplicitExceptionBytes.invoke(metaData);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static CompilationContext enterGlobalCompilationContext() {
        if (scopeEnterGlobalScope != null) {
            try {
                AutoCloseable impl = (AutoCloseable) scopeEnterGlobalScope.invoke();
                return impl == null ? null : new CompilationContext(impl);
            } catch (Throwable throwable) {
                throw new InternalError(throwable);
            }
        } else {
            return null;
        }
    }

    @SuppressWarnings("unused")
    public static CompilationContext openLocalCompilationContext(Object description) {
        if (scopeOpenLocalScope != null) {
            try {
                AutoCloseable impl = (AutoCloseable) scopeOpenLocalScope.invoke(Objects.requireNonNull(description));
                return impl == null ? null : new CompilationContext(impl);
            } catch (Throwable throwable) {
                throw new InternalError(throwable);
            }
        } else {
            return null;
        }
    }

    public static void exit(int status) {
        if (Services.IS_IN_NATIVE_IMAGE) {
            try {
                runtimeExitHotSpot.invoke(status);
            } catch (Throwable throwable) {
                throw new InternalError(throwable);
            }
        } else {
            System.exit(status);
        }
    }
}
