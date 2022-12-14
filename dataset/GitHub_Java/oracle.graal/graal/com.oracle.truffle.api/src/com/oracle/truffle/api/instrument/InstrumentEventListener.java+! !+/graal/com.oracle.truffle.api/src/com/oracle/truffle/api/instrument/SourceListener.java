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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * A client of the instrumentation framework that requests event notifications from the language
 * engine.
 */
public interface InstrumentEventListener {

    /**
     * The guest language runtime is starting to load a source. Care should be taken to ensure that
     * under any circumstance there is always a following call to {@link #loadEnding(Source)} with
     * the same argument.
     */
    void loadStarting(Source source);

    /**
     * The guest language runtime has finished loading a source. Care should be taken to ensure that
     * under any circumstance there is always a prior call to {@link #loadStarting(Source)} with the
     * same argument.
     */
    void loadEnding(Source source);

    /**
     * A guest language call is about to be executed.
     */
    void callEntering(Node astNode, String name);

    /**
     * A guest language call has just completed.
     */
    void callReturned(Node astNode, String name);

    /**
     * An opportunity for instrumentation to interact with Truffle AST execution halted at some
     * node.
     */
    void haltedAt(Node astNode, MaterializedFrame frame);

}
