/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.max.cri.ci.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Represents an atomic compare-and-swap operation
 * The result is a boolean that contains whether the value matched the expected value.
 */
public class CompareAndSwapNode extends AbstractStateSplit implements LIRLowerable, Lowerable, MemoryCheckpoint {

    @Input private ValueNode object;
    @Input private ValueNode offset;
    @Input private ValueNode expected;
    @Input private ValueNode newValue;

    public ValueNode object() {
        return object;
    }

    public ValueNode offset() {
        return offset;
    }

    public ValueNode expected() {
        return expected;
    }

    public ValueNode newValue() {
        return newValue;
    }

    public CompareAndSwapNode(ValueNode object, ValueNode offset, ValueNode expected, ValueNode newValue) {
        super(StampFactory.forKind(CiKind.Boolean.stackKind()));
        assert expected.kind() == newValue.kind();
        this.object = object;
        this.offset = offset;
        this.expected = expected;
        this.newValue = newValue;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitCompareAndSwap(this);
    }

    @Override
    public void lower(CiLoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    // specialized on value type until boxing/unboxing is sorted out in intrinsification
    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static boolean compareAndSwap(Object object, long offset, Object expected, Object newValue) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static boolean compareAndSwap(Object object, long offset, long expected, long newValue) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static boolean compareAndSwap(Object object, long offset, int expected, int newValue) {
        throw new UnsupportedOperationException();
    }
}
