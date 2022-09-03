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
 * The {@code Convert} class represents a conversion between primitive types.
 */
public final class Convert extends FloatingNode {
    @Input private Value value;

    @Data public final int opcode;

    public Value value() {
        return value;
    }

    public void setValue(Value x) {
        updateUsages(value, x);
        value = x;
    }

    /**
     * Constructs a new Convert instance.
     * @param opcode the bytecode representing the operation
     * @param value the instruction producing the input value
     * @param kind the result type of this instruction
     * @param graph
     */
    public Convert(int opcode, Value value, CiKind kind, Graph graph) {
        super(kind, graph);
        this.opcode = opcode;
        setValue(value);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitConvert(this);
    }

    @Override
    public void print(LogStream out) {
        out.print(Bytecodes.nameOf(opcode)).print('(').print(value()).print(')');
    }
}
