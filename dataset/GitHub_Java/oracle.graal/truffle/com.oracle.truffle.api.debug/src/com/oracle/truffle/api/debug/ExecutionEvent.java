/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * This event is delivered to all
 * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#onEvent(com.oracle.truffle.api.vm.EventConsumer)
 * registered event handlers} when an execution is about to be started. The event is the intended
 * place to initialize debugger - e.g. set
 * {@link Debugger#setLineBreakpoint(int, com.oracle.truffle.api.source.LineLocation, boolean)
 * breakpoints} or specify to execution should halt on the {@link #prepareStepInto() first possible
 * occurrence}. Methods in this event can only be used while the handlers process the event. Then
 * the state of the event becomes invalid and subsequent calls to the event methods yield
 * {@link IllegalStateException}. One can however obtain reference to {@link Debugger} instance and
 * keep it to further manipulate with debugging capabilities of the
 * {@link com.oracle.truffle.api.vm.PolyglotEngine} when it is running.
 * 
 * @since 0.9
 */
@SuppressWarnings("javadoc")
public final class ExecutionEvent {
    private Object[] debugger;
    private final PolyglotEngine engine;
    private final int currentDepth;
    private final Source source;

    ExecutionEvent(PolyglotEngine engine, int currentDepth, Object[] debugger, Source source) {
        this.debugger = debugger;
        this.engine = engine;
        this.currentDepth = currentDepth;
        this.source = source;
    }

    /**
     * Debugger associated with the execution. This debugger remains valid after the event is
     * processed, it is possible and suggested to keep a reference to it and use it any time later
     * when evaluating sources in the {@link com.oracle.truffle.api.vm.PolyglotEngine}.
     *
     * @return instance of debugger associated with the just starting execution and any subsequent
     *         ones in the same {@link com.oracle.truffle.api.vm.PolyglotEngine}.
     * @since 0.9
     */
    public synchronized Debugger getDebugger() {
        if (debugger == null) {
            throw new IllegalStateException("Event was disposed.");
        }
        if (debugger[0] instanceof Debugger) {
            return (Debugger) debugger[0];
        }
        Debugger dbg = Debugger.find(engine, true);
        dbg.executionStarted(currentDepth, source);
        debugger[0] = dbg;
        return dbg;
    }

    /**
     * Prepare to execute in Continue mode when guest language program execution resumes. In this
     * mode:
     * <ul>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at a node to which an enabled breakpoint is attached,
     * <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </ul>
     * 
     * @since 0.9
     */
    public void prepareContinue() {
        getDebugger().prepareContinue(-1);
    }

    /**
     * Prepare to execute in StepInto mode when guest language program execution resumes. In this
     * mode:
     * <ul>
     * <li>User breakpoints are disabled.</li>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at a node with the tag representing halt <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * <li>StepInto mode persists only through one resumption (i.e. {@code stepIntoCount} steps),
     * and reverts by default to Continue mode.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the specified number is {@code <= 0}
     * @since 0.9
     */
    public void prepareStepInto() {
        getDebugger().prepareStepInto(1);
    }

    synchronized void dispose() {
        debugger = null;
    }
}
