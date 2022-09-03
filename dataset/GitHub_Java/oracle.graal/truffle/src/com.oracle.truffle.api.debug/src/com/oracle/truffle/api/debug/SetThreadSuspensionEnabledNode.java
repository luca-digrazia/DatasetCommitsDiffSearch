/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.debug.DebuggerSession.ThreadSuspension;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

/**
 * This node sets thread-local enabled suspension flag. It uses {@link DebuggerSession}'s
 * {@link ThreadLocal} field, which is cached in 10 threads for fast access.
 */
abstract class SetThreadSuspensionEnabledNode extends Node {

    static final int CACHE_LIMIT = 10;

    public final void execute(boolean suspensionEnabled, DebuggerSession[] sessions) {
        execute(suspensionEnabled, sessions, Thread.currentThread().getId());
    }

    protected abstract void execute(boolean suspensionEnabled, DebuggerSession[] sessions, long threadId);

    @Specialization(guards = {"sessions.length == 1", "threadId == currentThreadId"}, limit = "CACHE_LIMIT")
    protected void executeCached(boolean suspensionEnabled,
                    @SuppressWarnings("unused") DebuggerSession[] sessions,
                    @SuppressWarnings("unused") long threadId,
                    @SuppressWarnings("unused") @Cached("currentThreadId()") long currentThreadId,
                    @Cached("getThreadSuspension(sessions)") ThreadSuspension threadSuspension) {
        threadSuspension.enabled = suspensionEnabled;
    }

    @ExplodeLoop
    @Specialization(replaces = "executeCached")
    protected void executeGeneric(boolean suspensionEnabled,
                    DebuggerSession[] sessions,
                    @SuppressWarnings("unused") long threadId) {
        for (DebuggerSession session : sessions) {
            session.setThreadSuspendEnabled(suspensionEnabled);
        }
    }

    static long currentThreadId() {
        return Thread.currentThread().getId();
    }

    @TruffleBoundary
    protected ThreadSuspension getThreadSuspension(DebuggerSession[] sessions) {
        assert sessions.length == 1;
        ThreadSuspension threadSuspension = new ThreadSuspension(true);
        sessions[0].threadSuspensions.set(threadSuspension);
        return threadSuspension;
    }

}
