/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;

/**
 * Interface that needs to be implemented by nodes which need deoptimization information.
 * 
 */
public interface DeoptimizingNode {

    /**
     * Returns true if this particular instance needs deoptimization information.
     * 
     * @return true if this particular instance needs deoptimization information
     */
    boolean canDeoptimize();

    /**
     * Returns the deoptimization information associated with this node if any.
     * 
     * @return the deoptimization information associated with this node if any.
     */
    FrameState getDeoptimizationState();

    /**
     * Set the deoptimization information associated with this node.
     * 
     * @param state the FrameState which represents the deoptimization information.
     */
    void setDeoptimizationState(FrameState state);

    /**
     * Returns the reason for deoptimization triggered by this node. If deoptimization at this point
     * can happen for external reasons (i.e. not explicitely triggered by this node) this method can
     * return null.
     * 
     * @return the reason for deoptimization triggered by this node.
     */
    DeoptimizationReason getDeoptimizationReason();

    /**
     * Returns true if this node needs deoptimization information for stack-walking purposes because
     * it is a call-site. While most other nodes use deoptimization information representing a state
     * that happened before them, these nodes use a state that is valid during the call itself.
     * 
     * @return true if this node needs deoptimization information for stack-walking purposes.
     */
    boolean isCallSiteDeoptimization();
}
