/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import static com.oracle.graal.graph.UnsafeAccess.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A special purpose store node that differs from {@link UnsafeStoreNode} in that it is not a
 * {@link StateSplit} and takes a computed address instead of an object.
 */
public class DirectReadNode extends FixedWithNextNode implements LIRLowerable {

    @Input private ValueNode address;
    private final Kind readKind;

    public DirectReadNode(ValueNode address, Kind readKind) {
        super(StampFactory.forKind(readKind));
        this.address = address;
        this.readKind = readKind;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitLoad(readKind, gen.operand(address), 0, Value.ILLEGAL, 0, false));
    }

    @SuppressWarnings("unchecked")
    @NodeIntrinsic
    public static <T> T read(long address, @ConstantNodeParameter Kind kind) {
        if (kind == Kind.Boolean) {
            return (T) Boolean.valueOf(unsafe.getByte(address) != 0);
        }
        if (kind == Kind.Byte) {
            return (T) (Byte) unsafe.getByte(address);
        }
        if (kind == Kind.Short) {
            return (T) (Short) unsafe.getShort(address);
        }
        if (kind == Kind.Char) {
            return (T) (Character) unsafe.getChar(address);
        }
        if (kind == Kind.Int) {
            return (T) (Integer) unsafe.getInt(address);
        }
        if (kind == Kind.Float) {
            return (T) (Float) unsafe.getFloat(address);
        }
        if (kind == Kind.Long) {
            return (T) (Long) unsafe.getLong(address);
        }
        assert kind == Kind.Double;
        return (T) (Double) unsafe.getDouble(address);
    }
}
