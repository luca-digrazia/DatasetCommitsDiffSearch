/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.expression;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.nodes.*;

/**
 * This class is similar to the {@link SLLogicalAndNode}.
 */
@NodeInfo(shortName = "||")
@SuppressWarnings("unused")
public abstract class SLLogicalOrNode extends SLBinaryNode {

    public SLLogicalOrNode(SourceSection src) {
        super(src);
    }

    @ShortCircuit("rightNode")
    protected boolean needsRightNode(boolean left) {
        return !left;
    }

    @ShortCircuit("rightNode")
    protected boolean needsRightNode(Object left) {
        return left instanceof Boolean && needsRightNode(((Boolean) left).booleanValue());
    }

    @Specialization(rewriteOn = RuntimeException.class)
    protected boolean doBoolean(boolean left, boolean hasRight, boolean right) {
        return left || right;
    }
}
