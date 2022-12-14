/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.java;

import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.type.*;

/**
 * The {@code TypeCheckNode} is the base class of casts and instanceof tests.
 */
public abstract class TypeCheckNode extends BooleanNode {

    protected static final RiResolvedType[] EMPTY_HINTS = new RiResolvedType[0];
    @Input private ValueNode object;
    @Input private ValueNode targetClassInstruction;
    @Data private final RiResolvedType targetClass;
    @Data private final RiResolvedType[] hints;
    @Data private final boolean hintsExact;

    /**
     * Creates a new TypeCheckNode.
     * @param targetClassInstruction the instruction which produces the class which is being cast to or checked against
     * @param targetClass the class that is being casted to or checked against
     * @param object the node which produces the object
     * @param kind the result type of this node
     */
    public TypeCheckNode(ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object, RiResolvedType[] hints, boolean hintsExact, Stamp stamp) {
        super(stamp);
        this.targetClassInstruction = targetClassInstruction;
        this.targetClass = targetClass;
        this.object = object;
        this.hints = hints;
        this.hintsExact = hintsExact;
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode targetClassInstruction() {
        return targetClassInstruction;
    }

    /**
     * Gets the target class, i.e. the class being cast to, or the class being tested against.
     * @return the target class
     */
    public RiResolvedType targetClass() {
        return targetClass;
    }

    public RiResolvedType[] hints() {
        return hints;
    }

    public boolean hintsExact() {
        return hintsExact;
    }
}
