/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.microbenchmarks.lir.trace;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;

import com.oracle.graal.microbenchmarks.lir.GraalCompilerState;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;

/**
 * TODO(zapster) document me!
 *
 */
public abstract class ControlFlowGraphState extends GraalCompilerState {

    public ControlFlowGraph cfg;

    @Setup(Level.Trial)
    public void beforeBenchmark() {
        // setup graph
        initializeMethod();
        prepareRequest();
        // compute cfg
        this.cfg = ControlFlowGraph.compute(graph(), true, true, true, true);
    }

}
