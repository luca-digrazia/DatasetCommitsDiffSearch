/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.max.cri.ci.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public abstract class WriteBarrier extends FixedWithNextNode {

    public WriteBarrier() {
        super(StampFactory.illegal());
    }

    protected void generateBarrier(CiValue adr, LIRGeneratorTool gen) {
        HotSpotVMConfig config = CompilerImpl.getInstance().getConfig();
        CiValue base = gen.emitUShr(adr, CiConstant.forInt(config.cardtableShift));

        long startAddress = config.cardtableStartAddress;
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            base = gen.emitAdd(base, CiConstant.forLong(config.cardtableStartAddress));
        }
        gen.emitStore(new CiAddress(CiKind.Boolean, base, displacement), CiConstant.FALSE, false);
    }
}
