/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public abstract class FixedNodeWithNext extends FixedNode {

    @NodeSuccessor
    private FixedNode next;

    public FixedNode next() {
        return next;
    }

    public void setNext(FixedNode x) {
        updatePredecessors(next, x);
        next = x;
    }

    public static final int SYNCHRONIZATION_ENTRY_BCI = -1;

    /**
     * Constructs a new instruction with the specified value type.
     * @param kind the value type for this instruction
     */
    public FixedNodeWithNext(CiKind kind, Graph graph) {
        super(kind, graph);
    }
}
