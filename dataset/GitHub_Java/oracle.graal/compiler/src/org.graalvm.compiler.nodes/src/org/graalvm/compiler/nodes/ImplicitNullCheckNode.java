/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;

import jdk.vm.ci.meta.JavaConstant;

@NodeInfo
public abstract class ImplicitNullCheckNode extends DeoptimizingFixedWithNextNode {

    public static final NodeClass<ImplicitNullCheckNode> TYPE = NodeClass.create(ImplicitNullCheckNode.class);

    protected JavaConstant deoptReasonAndAction;
    protected JavaConstant deoptSpeculation;

    protected ImplicitNullCheckNode(NodeClass<? extends ImplicitNullCheckNode> c, Stamp stamp) {
        super(c, stamp);
    }

    protected ImplicitNullCheckNode(NodeClass<? extends ImplicitNullCheckNode> c, Stamp stamp, FrameState stateBefore) {
        super(c, stamp, stateBefore);
    }

    public JavaConstant getDeoptReasonAndAction() {
        return deoptReasonAndAction;
    }

    public void setDeoptReasonAndAction(JavaConstant deoptReasonAndAction) {
        this.deoptReasonAndAction = deoptReasonAndAction;
    }

    public JavaConstant getDeoptSpeculation() {
        return deoptSpeculation;
    }

    public void setDeoptSpeculation(JavaConstant deoptSpeculation) {
        this.deoptSpeculation = deoptSpeculation;
    }

}
