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

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

public final class WriteBarrierPre extends WriteBarrier implements Lowerable {

    @Input private ValueNode object;
    @Input private ValueNode previousValue;
    @Input private LocationNode location;
    private boolean doLoad;

    public ValueNode object() {
        return object;
    }

    public ValueNode previousValue() {
        return previousValue;
    }

    public boolean doLoad() {
        return doLoad;
    }

    public LocationNode location() {
        return location;
    }

    public WriteBarrierPre(ValueNode object, ValueNode previousValue, LocationNode location, boolean doLoad) {
        this.object = object;
        this.previousValue = previousValue;
        this.doLoad = doLoad;
        this.location = location;
    }

    public void lower(LoweringTool generator) {
        generator.getRuntime().lower(this, generator);
    }
}
