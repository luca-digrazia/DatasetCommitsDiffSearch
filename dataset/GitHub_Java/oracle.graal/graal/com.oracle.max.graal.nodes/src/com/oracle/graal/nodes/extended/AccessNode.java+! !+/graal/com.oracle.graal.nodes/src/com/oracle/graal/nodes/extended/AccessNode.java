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
package com.oracle.graal.nodes.extended;

import com.oracle.max.cri.ci.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

public abstract class AccessNode extends AbstractStateSplit implements Access {

    @Input private ValueNode object;
    @Input private GuardNode guard;
    @Input private LocationNode location;
    @Data private boolean nullCheck;

    public ValueNode object() {
        return object;
    }

    public GuardNode guard() {
        return guard;
    }

    public void setGuard(GuardNode x) {
        updateUsages(guard, x);
        guard = x;
    }

    public LocationNode location() {
        return location;
    }

    public boolean getNullCheck() {
        return nullCheck;
    }

    public void setNullCheck(boolean check) {
        this.nullCheck = check;
    }

    public AccessNode(CiKind kind, ValueNode object, LocationNode location) {
        super(StampFactory.forKind(kind));
        this.object = object;
        this.location = location;
    }
}
