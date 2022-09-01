/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.instrument;

import com.oracle.truffle.api.nodes.*;

/**
 * A node that can be inserted into a Truffle AST in order to attach <em>instrumentation</em> at a
 * particular node.
 * <p>
 * A wrapper <em>decorates</em> an AST node (its <em>child</em>) by acting as a transparent
 * <em>proxy</em> for the child with respect to Truffle execution semantics.
 * <p>
 * A wrapper is also expected to notify its associated {@link Probe} when certain
 * {@link ExecutionEvents} occur at the wrapper during program execution.
 * <p>
 * The wrapper's {@link Probe} is shared by every copy of the wrapper made when the AST is copied.
 * <p>
 * Wrappers methods must be amenable to Truffle/Graal inlining.
 * <p>
 * <strong>Disclaimer:</strong> experimental interface under development.
 */
public interface Wrapper extends PhylumTagged {

    /**
     * Gets the AST node being instrumented, which should never be an instance of {@link Wrapper}.
     */
    Node getChild();

    /**
     * Gets the {@link Probe} to which events occurring at this wrapper's child are propagated.
     */
    Probe getProbe();

}
