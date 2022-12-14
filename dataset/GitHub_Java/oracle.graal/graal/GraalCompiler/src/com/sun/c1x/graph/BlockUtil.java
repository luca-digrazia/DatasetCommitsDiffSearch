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
package com.sun.c1x.graph;

import java.util.*;

import com.sun.c1x.ir.*;

/**
 * The {@code BlockUtil} class contains a number of utilities for manipulating a CFG of basic blocks.
 */
public class BlockUtil {

    /**
     * Disconnects the specified block from all other blocks.
     * @param block the block to remove from the graph
     */
    public static void disconnectFromGraph(BlockBegin block) {
        ArrayList<Instruction> preds = new ArrayList<Instruction>(block.blockPredecessors());
        for (Instruction p : preds) {
            p.block().end().blockSuccessors().remove(block);
        }
        block.end().clearSuccessors();
    }
}
