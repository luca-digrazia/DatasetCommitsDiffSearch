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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * Returns -1, 0, or 1 if either x > y, x == y, or x < y.
 */
public final class NormalizeCompare extends Binary {

    private static final int INPUT_COUNT = 0;
    private static final int SUCCESSOR_COUNT = 0;

    /**
     * Creates a new compare operation.
     * @param opcode the bytecode opcode
     * @param kind the result kind
     * @param x the first input
     * @param y the second input
     */
    public NormalizeCompare(int opcode, CiKind kind, Value x, Value y, Graph graph) {
        super(kind, opcode, x, y, INPUT_COUNT, SUCCESSOR_COUNT, graph);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitMaterialize(this);
    }

    @Override
    public void print(LogStream out) {
        out.print(x()).
            print(' ').
            print(Bytecodes.operator(opcode)).
            print(' ').
            print(y());
    }

    @Override
    public Node copy(Graph into) {
        return new NormalizeCompare(opcode, kind, null, null, into);
    }

    public boolean isUnorderedLess() {
        return this.opcode == Bytecodes.FCMPL || this.opcode == Bytecodes.DCMPL;
    }
}
