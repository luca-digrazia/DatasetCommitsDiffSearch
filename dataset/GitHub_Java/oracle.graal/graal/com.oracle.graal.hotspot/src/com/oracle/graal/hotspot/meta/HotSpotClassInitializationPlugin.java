/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.hotspot.nodes.aot.InitializeKlassNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.ClassInitializationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class HotSpotClassInitializationPlugin implements ClassInitializationPlugin {
    @Override
    public boolean shouldApply(GraphBuilderContext builder, ResolvedJavaType type) {
        if (!builder.parsingIntrinsic()) {
            ResolvedJavaMethod method = builder.getGraph().method();
            ResolvedJavaType methodHolder = method.getDeclaringClass();
            // We can elide initialization nodes if type >=: methodHolder.
            // The type is already initialized by either "new" or "invokestatic".

            // Emit initialization node if type is an interface since:
            // JLS 12.4: Before a class is initialized, its direct superclass must be initialized,
            // but interfaces implemented by the class are not initialized.
            // and a class or interface type T will be initialized immediately
            // before the first occurrence of accesses listed in JLS 12.4.1.

            return !type.isAssignableFrom(methodHolder) || type.isInterface();
        }
        return false;
    }

    @Override
    public ValueNode apply(GraphBuilderContext builder, ResolvedJavaType type, FrameState frameState) {
        assert shouldApply(builder, type);
        Stamp hubStamp = builder.getStampProvider().createHubStamp((ObjectStamp) StampFactory.objectNonNull());
        ConstantNode hub = builder.append(ConstantNode.forConstant(hubStamp, ((HotSpotResolvedObjectType) type).klass(), builder.getMetaAccess(), builder.getGraph()));
        InitializeKlassNode initialize = builder.append(new InitializeKlassNode(hub));
        initialize.setStateBefore(frameState);
        return initialize;
    }
}
