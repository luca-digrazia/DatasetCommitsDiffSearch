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

public class G1WriteBarrierPost extends WriteBarrierPost implements Lowerable {

    @Input private ValueNode object;
    @Input private ValueNode value;
    @Input private LocationNode location;
    private final boolean precise;

    @Override
    public ValueNode getObject() {
        return object;
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    @Override
    public LocationNode getLocation() {
        return location;
    }

    @Override
    public boolean usePrecise() {
        return precise;
    }

    public G1WriteBarrierPost(ValueNode object, ValueNode value, LocationNode location, boolean precise) {
        this.object = object;
        this.value = value;
        this.location = location;
        this.precise = precise;
    }

    @Override
    public void lower(LoweringTool generator) {
        generator.getRuntime().lower(this, generator);
    }

}
