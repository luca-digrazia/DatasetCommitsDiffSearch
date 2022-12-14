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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.snippets.*;

/**
 * Node that is used to maintain a stack based counter of how many locks
 * are currently held.
 */
public final class MonitorCounterNode extends FloatingNode implements LIRGenLowerable {

    public MonitorCounterNode(Kind wordKind) {
        super(StampFactory.forWord(wordKind, true));
    }

    @Override
    public void generate(LIRGenerator gen) {
        assert graph().getNodes().filter(MonitorCounterNode.class).count() == 1 : "monitor counters not canonicalized to single instance";
        StackSlot counter = gen.frameMap().allocateStackBlock(gen.target().wordSize, false);
        Value result = gen.emitLea(counter);
        gen.setResult(this, result);
    }


    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static Word counter(@ConstantNodeParameter Kind wordKind) {
        throw new UnsupportedOperationException();
    }

}
