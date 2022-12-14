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
package com.oracle.graal.nodes;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.spi.*;


public final class LoopEndNode extends EndNode {

    @Input(notDataflow = true) private LoopBeginNode loopBegin;
    @Data private boolean safepointPolling;
    @Data private int endIndex;

    public LoopEndNode(LoopBeginNode begin) {
        int idx = begin.nextEndIndex();
        assert idx >= 0;
        this.safepointPolling = true;
        this.endIndex = idx;
        this.loopBegin = begin;
    }

    @Override
    public MergeNode merge() {
        return loopBegin();
    }

    public LoopBeginNode loopBegin() {
        return loopBegin;
    }

    public void setLoopBegin(LoopBeginNode x) {
        updateUsages(this.loopBegin, x);
        this.loopBegin = x;
    }


    public void setSafepointPolling(boolean safePointPolling) {
        this.safepointPolling = safePointPolling;
    }

    public boolean hasSafepointPolling() {
        return safepointPolling;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitLoopEnd(this);
        super.generate(gen);
    }

    @Override
    public boolean verify() {
        assertTrue(loopBegin != null, "must have a loop begin");
        assertTrue(usages().count() == 0, "LoopEnds can not be used");
        return super.verify();
    }

    public int endIndex() {
        return endIndex;
    }

    public void setEndIndex(int idx) {
        this.endIndex = idx;
    }

    @Override
    public Iterable< ? extends Node> cfgSuccessors() {
        return Collections.emptyList();
    }
}
