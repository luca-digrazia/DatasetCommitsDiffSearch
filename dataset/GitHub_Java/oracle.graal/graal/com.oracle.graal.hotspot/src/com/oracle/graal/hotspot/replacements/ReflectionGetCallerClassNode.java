/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.replacements.nodes.*;

public class ReflectionGetCallerClassNode extends MacroNode implements Canonicalizable, Lowerable {

    public ReflectionGetCallerClassNode(Invoke invoke) {
        super(invoke);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        ConstantNode callerClassNode = getCallerClassNode(tool.runtime());
        if (callerClassNode != null) {
            return callerClassNode;
        }
        return this;
    }

    @Override
    public void lower(LoweringTool tool, LoweringType loweringType) {
        StructuredGraph graph = (StructuredGraph) graph();

        ConstantNode callerClassNode = getCallerClassNode(tool.getRuntime());

        if (callerClassNode != null) {
            graph.replaceFixedWithFloating(this, callerClassNode);
        } else {
            graph.replaceFixedWithFixed(this, createInvoke());
        }
    }

    /**
     * If inlining is deep enough this method returns a {@link ConstantNode} of the caller class by
     * walking the the stack.
     * 
     * @param runtime
     * @return ConstantNode of the caller class, or null
     */
    private ConstantNode getCallerClassNode(MetaAccessProvider runtime) {
        if (!GraalOptions.IntrinsifyReflectionMethods) {
            return null;
        }

        // Walk back up the frame states to find the caller at the required depth.
        FrameState state = stateAfter();

        // Cf. JVM_GetCallerClass
        // NOTE: Start the loop at depth 1 because the current frame state does
        // not include the Reflection.getCallerClass() frame.
        for (int n = 1; state != null; state = state.outerFrameState(), n++) {
            HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) state.method();
            switch (n) {
                case 0:
                    throw GraalInternalError.shouldNotReachHere("current frame state does not include the Reflection.getCallerClass frame");
                case 1:
                    // Frame 0 and 1 must be caller sensitive (see JVM_GetCallerClass).
                    if (!method.isCallerSensitive()) {
                        return null;  // bail-out; let JVM_GetCallerClass do the work
                    }
                    break;
                default:
                    if (!method.ignoredBySecurityStackWalk()) {
                        // We have reached the desired frame; return the holder class.
                        HotSpotResolvedObjectType callerClass = (HotSpotResolvedObjectType) method.getDeclaringClass();
                        return ConstantNode.forObject(callerClass.mirror(), runtime, graph());
                    }
                    break;
            }
        }
        return null;  // bail-out; let JVM_GetCallerClass do the work
    }

}
