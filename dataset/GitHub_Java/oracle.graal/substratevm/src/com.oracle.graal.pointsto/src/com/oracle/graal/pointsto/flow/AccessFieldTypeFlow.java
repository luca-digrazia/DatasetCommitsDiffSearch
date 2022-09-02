/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import org.graalvm.compiler.nodes.java.AccessFieldNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.typestate.TypeState;

/** The base class for a field store or load operation type flow. */
public abstract class AccessFieldTypeFlow<T extends AccessFieldNode> extends TypeFlow<T> {

    /** The field that this flow stores into or loads from. */
    protected final AnalysisField field;

    protected AccessFieldTypeFlow(T node) {
        /* The declared type of a field access node is the field declared type. */
        super(node, ((AnalysisField) node.field()).getType());
        this.field = (AnalysisField) node.field();
    }

    protected AccessFieldTypeFlow(AccessFieldTypeFlow<T> original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows);
        this.field = original.field;
    }

    public AnalysisField field() {
        return field;
    }

    @Override
    public final boolean addState(BigBang bb, TypeState add) {
        /* Only a clone should be updated */
        assert this.isClone();
        return super.addState(bb, add);
    }
}
