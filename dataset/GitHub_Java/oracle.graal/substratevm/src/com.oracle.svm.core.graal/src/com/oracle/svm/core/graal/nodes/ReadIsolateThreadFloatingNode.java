/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.spi.LIRLowerable;

import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;

import jdk.vm.ci.code.Register;

/**
 * Reads the value of a specific register.
 *
 * This is a floating node that uses the register directly as the result. It is more efficient than
 * using a {@link ReadRegisterFixedNode}, but limits usages.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public final class ReadIsolateThreadFloatingNode extends ReadRegisterFloatingNode implements LIRLowerable {
    public static final NodeClass<ReadIsolateThreadFloatingNode> TYPE = NodeClass.create(ReadIsolateThreadFloatingNode.class);

    public ReadIsolateThreadFloatingNode() {
        super(TYPE);
    }

    @Override
    protected Register getReadRegister(SubstrateRegisterConfig registerConfig) {
        return registerConfig.getThreadRegister();
    }
}
