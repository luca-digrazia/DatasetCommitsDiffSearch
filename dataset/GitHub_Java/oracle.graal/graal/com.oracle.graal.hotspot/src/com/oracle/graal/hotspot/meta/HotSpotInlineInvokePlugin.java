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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderPlugin.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

final class HotSpotInlineInvokePlugin implements InlineInvokePlugin {
    private final Replacements replacements;
    private final HotSpotSuitesProvider suites;

    public HotSpotInlineInvokePlugin(HotSpotSuitesProvider suites, Replacements replacements) {
        this.suites = suites;
        this.replacements = replacements;
    }

    public ResolvedJavaMethod getInlinedMethod(GraphBuilderContext builder, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
        ResolvedJavaMethod subst = replacements.getMethodSubstitutionMethod(method);
        if (subst != null) {
            // Forced inlining of intrinsics
            return subst;
        }
        if (builder.parsingReplacement()) {
            if (suites.getNodeIntrinsification().getIntrinsic(method) != null) {
                // @NodeIntrinsic methods are handled by HotSpotAnnotatedInvocationPlugin
                return null;
            }
            // Force inlining when parsing replacements
            return method;
        } else {
            assert suites.getNodeIntrinsification().getIntrinsic(method) == null : String.format("@%s method %s must only be called from within a replacement%n%s",
                            NodeIntrinsic.class.getSimpleName(), method.format("%h.%n"), builder);
            if (InlineDuringParsing.getValue() && method.hasBytecodes() && method.getCode().length <= TrivialInliningSize.getValue() && builder.getDepth() < InlineDuringParsingMaxDepth.getValue()) {
                return method;
            }
        }
        return null;
    }
}
