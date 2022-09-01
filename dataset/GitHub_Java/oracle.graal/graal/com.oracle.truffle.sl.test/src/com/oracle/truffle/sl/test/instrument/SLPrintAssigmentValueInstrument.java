/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test.instrument;

import java.io.*;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.local.*;

/**
 * This sample instrument provides prints the value of an assignment (after the assignment is
 * complete) to the {@link PrintStream} specified in the constructor. This instrument can only be
 * attached to a wrapped {@link SLWriteLocalVariableNode}, but provides no guards to protect it from
 * being attached elsewhere.
 */
public final class SLPrintAssigmentValueInstrument extends Instrument {

    private PrintStream output;

    public SLPrintAssigmentValueInstrument(PrintStream output) {
        this.output = output;
    }

    @Override
    public void leave(Node astNode, VirtualFrame frame, Object result) {
        output.println(result);
    }
}
