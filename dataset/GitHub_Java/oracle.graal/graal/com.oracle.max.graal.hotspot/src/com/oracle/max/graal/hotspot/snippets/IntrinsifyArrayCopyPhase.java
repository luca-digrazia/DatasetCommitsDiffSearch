/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot.snippets;

import java.lang.reflect.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.java.*;

public class IntrinsifyArrayCopyPhase extends Phase {
    private final GraalRuntime runtime;
    private RiResolvedMethod arrayCopy;
    private RiResolvedMethod byteArrayCopy;
    private RiResolvedMethod shortArrayCopy;
    private RiResolvedMethod charArrayCopy;
    private RiResolvedMethod intArrayCopy;
    private RiResolvedMethod longArrayCopy;
    private RiResolvedMethod floatArrayCopy;
    private RiResolvedMethod doubleArrayCopy;
    private RiResolvedMethod objectArrayCopy;

    public IntrinsifyArrayCopyPhase(GraalRuntime runtime) {
        this.runtime = runtime;
        try {
            byteArrayCopy = getArrayCopySnippet(runtime, byte.class);
            charArrayCopy = getArrayCopySnippet(runtime, char.class);
            shortArrayCopy = getArrayCopySnippet(runtime, short.class);
            intArrayCopy = getArrayCopySnippet(runtime, int.class);
            longArrayCopy = getArrayCopySnippet(runtime, long.class);
            floatArrayCopy = getArrayCopySnippet(runtime, float.class);
            doubleArrayCopy = getArrayCopySnippet(runtime, double.class);
            objectArrayCopy = getArrayCopySnippet(runtime, Object.class);
            arrayCopy = runtime.getRiMethod(System.class.getDeclaredMethod("arraycopy", Object.class, int.class, Object.class, int.class, int.class));
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private static RiResolvedMethod getArrayCopySnippet(RiRuntime runtime, Class<?> componentClass) throws NoSuchMethodException {
        Class<?> arrayClass = Array.newInstance(componentClass, 0).getClass();
        return runtime.getRiMethod(ArrayCopySnippets.class.getDeclaredMethod("arraycopy", arrayClass, int.class, arrayClass, int.class, int.class));
    }

    @Override
    protected void run(StructuredGraph graph) {
        boolean hits = false;
        for (MethodCallTargetNode methodCallTarget : graph.getNodes(MethodCallTargetNode.class)) {
            RiResolvedMethod targetMethod = methodCallTarget.targetMethod();
            RiResolvedMethod snippetMethod = null;
            if (targetMethod == arrayCopy) {
                ValueNode src = methodCallTarget.arguments().get(0);
                ValueNode dest = methodCallTarget.arguments().get(2);
                if (src == null || dest == null) { //TODO (gd) this should never be null : check
                    return;
                }
                RiResolvedType srcDeclaredType = src.declaredType();
                RiResolvedType destDeclaredType = dest.declaredType();
                if (srcDeclaredType != null
                                && srcDeclaredType.isArrayClass()
                                && destDeclaredType != null
                                && destDeclaredType.isArrayClass()) {
                    CiKind componentKind = srcDeclaredType.componentType().kind(false);
                    if (srcDeclaredType.componentType() == destDeclaredType.componentType()) {
                        if (componentKind == CiKind.Int) {
                            snippetMethod = intArrayCopy;
                        } else if (componentKind == CiKind.Char) {
                            snippetMethod = charArrayCopy;
                        } else if (componentKind == CiKind.Long) {
                            snippetMethod = longArrayCopy;
                        } else if (componentKind == CiKind.Byte) {
                            snippetMethod = byteArrayCopy;
                        } else if (componentKind == CiKind.Short) {
                            snippetMethod = shortArrayCopy;
                        } else if (componentKind == CiKind.Float) {
                            snippetMethod = floatArrayCopy;
                        } else if (componentKind == CiKind.Double) {
                            snippetMethod = doubleArrayCopy;
                        } else if (componentKind == CiKind.Object) {
                            snippetMethod = objectArrayCopy;
                        }
                    } else if (componentKind == CiKind.Object
                                    && srcDeclaredType.componentType().isSubtypeOf(destDeclaredType.componentType())) {
                        snippetMethod = objectArrayCopy;
                    }
                }
            }

            if (snippetMethod != null) {
                StructuredGraph snippetGraph = (StructuredGraph) snippetMethod.compilerStorage().get(Graph.class);
                assert snippetGraph != null : "ArrayCopySnippets should be installed";
                hits = true;
                Debug.log("%s > Intinsify (%s)", Debug.currentScope(), snippetMethod.signature().argumentTypeAt(0, snippetMethod.holder()).componentType());
                InliningUtil.inline(methodCallTarget.invoke(), snippetGraph, false);
            }
        }
        if (GraalOptions.OptCanonicalizer && hits) {
            new CanonicalizerPhase(null, runtime, null).apply(graph);
        }
    }
}
