/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.test;

import static com.oracle.graal.debug.internal.MemUseTrackerImpl.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.nodes.StructuredGraph.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.printer.*;

/**
 * Used to benchmark memory usage during Graal compilation. Run with:
 *
 * <pre>
 *     mx vm -XX:-UseGraalClassLoader -cp @com.oracle.graal.hotspot.test com.oracle.graal.hotspot.test.MemoryUsageBenchmark
 * </pre>
 */
public class MemoryUsageBenchmark extends GraalCompilerTest {

    public static int simple(int a, int b) {
        return a + b;
    }

    public static int complex(CharSequence cs) {
        if (cs instanceof String) {
            return cs.hashCode();
        }

        if (cs instanceof StringBuffer) {
            int[] hash = {0};
            cs.chars().forEach(c -> hash[0] += c);
            return hash[0];
        }

        int res = 0;

        // Exercise lock elimination
        synchronized (cs) {
            res = cs.length();
        }
        synchronized (cs) {
            res = cs.hashCode() ^ 31;
        }

        for (int i = 0; i < cs.length(); i++) {
            res *= cs.charAt(i);
        }
        return res;
    }

    static class MemoryUsageCloseable implements AutoCloseable {

        private final long start;
        private final String name;

        public MemoryUsageCloseable(String name) {
            this.name = name;
            this.start = getCurrentThreadAllocatedBytes();
        }

        @Override
        public void close() {
            long end = getCurrentThreadAllocatedBytes();
            long allocated = end - start;
            System.out.println(name + ": " + allocated);
        }
    }

    public static void main(String[] args) {
        // Ensure a debug configuration for this thread is initialized
        if (Debug.isEnabled() && DebugScope.getConfig() == null) {
            DebugEnvironment.initialize(System.out);
        }
        new MemoryUsageBenchmark().run();
    }

    private void doCompilation(String methodName) {
        HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) getMetaAccess().lookupJavaMethod(getMethod(methodName));
        HotSpotBackend backend = runtime().getHostBackend();
        int id = method.allocateCompileId(INVOCATION_ENTRY_BCI);
        long ctask = 0L;
        CompilationTask task = new CompilationTask(backend, method, INVOCATION_ENTRY_BCI, ctask, id);
        task.runCompilation();

        // invalidate the compiled code
        method.reprofile();
    }

    private static final boolean verbose = Boolean.getBoolean("verbose");

    private void compileAndTime(String methodName) {
        // Warm up and initialize compiler phases used by this compilation
        for (int i = 0; i < 10; i++) {
            try (MemoryUsageCloseable c = verbose ? new MemoryUsageCloseable(methodName + "[" + i + "]") : null) {
                doCompilation(methodName);
            }
        }

        try (MemoryUsageCloseable c = new MemoryUsageCloseable(methodName + "[warm]")) {
            doCompilation(methodName);
        }
    }

    public void run() {
        compileAndTime("complex");
        compileAndTime("simple");
    }
}
