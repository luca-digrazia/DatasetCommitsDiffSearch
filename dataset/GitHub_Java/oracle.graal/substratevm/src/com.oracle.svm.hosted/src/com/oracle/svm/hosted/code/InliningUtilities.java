/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.ValueProxy;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.hosted.nodes.AssertValueNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class InliningUtilities {

    public static boolean isTrivialMethod(StructuredGraph graph) {
        int numInvokes = 0;
        int numOthers = 0;
        for (Node n : graph.getNodes()) {
            if (n instanceof StartNode || n instanceof ParameterNode || n instanceof FullInfopointNode ||
                            n instanceof ValueProxy || n instanceof AssertValueNode) {
                continue;
            }
            if (n instanceof MethodCallTargetNode) {
                numInvokes++;
            } else {
                numOthers++;
            }

            if (!shouldBeTrivial(numInvokes, numOthers, graph)) {
                return false;
            }
        }

        return true;
    }

    private static boolean shouldBeTrivial(int numInvokes, int numOthers, StructuredGraph graph) {
        if (numInvokes == 0) {
            // This is a leaf method => we can be generous.
            return numOthers <= SubstrateOptions.MaxNodesInTrivialLeafMethod.getValue(graph.getOptions());
        } else if (numInvokes <= SubstrateOptions.MaxInvokesInTrivialMethod.getValue(graph.getOptions())) {
            return numOthers <= SubstrateOptions.MaxNodesInTrivialMethod.getValue(graph.getOptions());
        } else {
            return false;
        }
    }

    public static int recursionDepth(Invoke invoke, ResolvedJavaMethod callee) {
        FrameState state = invoke.stateAfter();
        int result = 0;
        do {
            if (state.getMethod().equals(callee)) {
                result++;
            }
            state = state.outerFrameState();
        } while (state != null);
        return result;
    }
}
