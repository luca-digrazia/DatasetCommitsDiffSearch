/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.loop;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

public abstract class InductionVariable {

    public enum Direction {
        Up, Down;

        public Direction opposite() {
            switch (this) {
                case Up:
                    return Down;
                case Down:
                    return Up;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    protected final LoopEx loop;

    public InductionVariable(LoopEx loop) {
        this.loop = loop;
    }

    public abstract Direction direction();

    public abstract ValueNode valueNode();

    public abstract ValueNode initNode();

    public abstract ValueNode strideNode();

    public abstract boolean isConstantInit();

    public abstract boolean isConstantStride();

    public abstract long constantInit();

    public abstract long constantStride();

    public abstract ValueNode extremumNode();

    public abstract boolean isConstantExtremum();

    public abstract long constantExtremum();
}
