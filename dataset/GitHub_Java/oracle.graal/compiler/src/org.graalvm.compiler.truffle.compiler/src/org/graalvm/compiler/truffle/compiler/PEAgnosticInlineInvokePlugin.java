/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;

import java.util.ArrayList;
import java.util.List;

public class PEAgnosticInlineInvokePlugin extends PEInlineInvokePlugin {
    private final EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke;
    private final List<Invoke> indirectInvokes = new ArrayList<>();
    private final TruffleMetaAccessProvider truffleMetaAccessProvider;
    private JavaConstant lastDirectCallNode;
    private boolean indirectCall;
    private PartialEvaluator partialEvaluator;

    public PEAgnosticInlineInvokePlugin(TruffleMetaAccessProvider truffleMetaAccessProvider, PartialEvaluator partialEvaluator) {
        this.truffleMetaAccessProvider = truffleMetaAccessProvider;
        this.partialEvaluator = partialEvaluator;
        this.truffleCallNodeToInvoke = EconomicMap.create();
    }

    @Override
    public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
        InlineInfo inlineInfo = asInlineInfo(original);
        if (original.equals(partialEvaluator.callDirectMethod)) {
            ValueNode arg0 = arguments[1];
            if (!arg0.isConstant()) {
                GraalError.shouldNotReachHere("The direct call node does not resolve to a constant!");
            }
            lastDirectCallNode = (JavaConstant) arg0.asConstant();
        }
        if (original.equals(partialEvaluator.callIndirectMethod)) {
            indirectCall = true;
        }
        return inlineInfo;
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod original, Invoke invoke) {
        if (original.equals(partialEvaluator.callBoundary)) {
            if (lastDirectCallNode == null) {
                if (indirectCall) {
                    indirectCall = false;
                    indirectInvokes.add(invoke);
                }
                return;
            }
            TruffleCallNode truffleCallNode = truffleMetaAccessProvider.findCallNode(lastDirectCallNode);
            truffleCallNodeToInvoke.put(truffleCallNode, invoke);
            lastDirectCallNode = null;
        }
    }

    public EconomicMap<TruffleCallNode, Invoke> getTruffleCallNodeToInvoke() {
        return truffleCallNodeToInvoke;
    }

    public List<Invoke> getIndirectInvokes() {
        return indirectInvokes;
    }
}
