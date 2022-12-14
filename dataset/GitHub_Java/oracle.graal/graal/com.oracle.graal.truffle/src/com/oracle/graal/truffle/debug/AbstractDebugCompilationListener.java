/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.debug;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.*;

public class AbstractDebugCompilationListener implements GraalTruffleCompilationListener {

    protected static final PrintStream OUT = TTY.out().out();

    public void notifyCompilationQueued(OptimizedCallTarget target) {
    }

    public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
    }

    public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {
    }

    public void notifyCompilationStarted(OptimizedCallTarget target) {
    }

    public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
    }

    public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {
    }

    public void notifyCompilationSuccess(OptimizedCallTarget target, StructuredGraph graph, CompilationResult result) {
    }

    public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
    }

    public void notifyShutdown(TruffleRuntime runtime) {
    }

    public static void log(int indent, String msg, String details, Map<String, Object> properties) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[truffle] %-16s ", msg));
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
        sb.append(String.format("%-" + (60 - indent) + "s", details));
        if (properties != null) {
            for (String property : properties.keySet()) {
                Object value = properties.get(property);
                if (value == null) {
                    continue;
                }
                sb.append('|');
                sb.append(property);

                StringBuilder propertyBuilder = new StringBuilder();
                if (value instanceof Integer) {
                    propertyBuilder.append(String.format("%6d", value));
                } else if (value instanceof Double) {
                    propertyBuilder.append(String.format("%8.2f", value));
                } else {
                    propertyBuilder.append(value);
                }

                int length = Math.max(1, 20 - property.length());
                sb.append(String.format(" %" + length + "s ", propertyBuilder.toString()));
            }
        }
        OUT.println(sb.toString());
    }

    public static void addASTSizeProperty(OptimizedCallTarget target, Map<String, Object> properties) {
        int nodeCount = target.countNonTrivialNodes(false);
        int deepNodeCount = nodeCount;
        TruffleInlining inlining = target.getInlining();
        if (inlining != null) {
            deepNodeCount += inlining.getInlinedNodeCount();
        }
        properties.put("ASTSize", String.format("%5d/%5d", nodeCount, deepNodeCount));

    }

}
