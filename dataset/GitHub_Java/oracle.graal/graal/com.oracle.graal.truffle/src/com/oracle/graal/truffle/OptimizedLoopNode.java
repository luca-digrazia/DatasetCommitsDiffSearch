/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Temporary node for legacy loop count reporting support as it was most likely done in other
 * implementations.
 */
public final class OptimizedLoopNode extends LoopNode {

    @CompilationFinal private int loopCount;

    public OptimizedLoopNode(RepeatingNode body) {
        super(body);
    }

    @Override
    public void executeLoop(VirtualFrame frame) {
        try {
            do {
            } while (executeBody(frame));
        } finally {
            loopDone();
        }
    }

    private final boolean executeBody(VirtualFrame frame) {
        boolean result = executeRepeatNode(frame);
        if (CompilerDirectives.inInterpreter()) {
            if (result) {
                loopCount++;
            }
        }
        return result;
    }

    private void loopDone() {
        if (CompilerDirectives.inInterpreter()) {
            getRootNode().reportLoopCount(loopCount);
            loopCount = 0;
        }
    }
}