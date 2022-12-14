/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.constraints;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

public class UnsupportedFeatures {

    static class Data implements Comparable<Data> {
        protected final String key;
        protected final AnalysisMethod method;
        protected final String message;
        protected final String trace;
        protected final Throwable originalException;

        Data(String key, AnalysisMethod method, String message, String trace, Throwable originalException) {
            this.key = key;
            this.method = method;
            this.message = message;
            this.trace = trace;
            this.originalException = originalException;
        }

        @Override
        public int compareTo(Data other) {
            int result = message.compareTo(other.message);
            if (result == 0) {
                result = key.compareTo(other.key);
            }
            return result;
        }
    }

    private final ConcurrentHashMap<String, Data> messages;

    public UnsupportedFeatures() {
        messages = new ConcurrentHashMap<>();
    }

    public void addMessage(String key, AnalysisMethod method, String message) {
        addMessage(key, method, message, null, null);
    }

    public void addMessage(String key, AnalysisMethod method, String message, String trace) {
        addMessage(key, method, message, trace, null);
    }

    /**
     * @param originalException The exception that originally caused this unsupported feature.
     */
    public void addMessage(String key, AnalysisMethod method, String message, String trace, Throwable originalException) {
        messages.putIfAbsent(key, new Data(key, method, message, trace, originalException));
    }

    /**
     * Report the unsupported features. Throws {@code UnsupportedFeatureException} if unsupported
     * features are found.
     *
     * @param bb the bigbang object
     * @throws UnsupportedFeatureException if unsupported features are found
     */
    public void report(BigBang bb) {
        if (!messages.isEmpty()) {
            List<Data> entries = new ArrayList<>(messages.values());
            Collections.sort(entries);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(outputStream);

            for (Data entry : entries) {
                printStream.println("Error: " + entry.message);
                if (entry.trace != null) {
                    printStream.println("Trace: " + entry.trace);
                }
                if (entry.method != null) {
                    printStream.println("Call path from entry point to " + entry.method.format("%H.%n(%p)") + ": ");
                    ShortestInvokeChainPrinter.print(bb, entry.method, printStream);
                    printStream.println();
                }
                if (entry.originalException != null) {
                    printStream.print("Original exception that caused the problem: ");
                    entry.originalException.printStackTrace(printStream);
                }
            }
            printStream.close();

            String unsupportedFeaturesMessage;
            if (entries.size() == 1) {
                unsupportedFeaturesMessage = entries.get(0).message + "\nDetailed message:\n" + outputStream.toString();
                throw new UnsupportedFeatureException(unsupportedFeaturesMessage, entries.get(0).originalException);
            } else {
                unsupportedFeaturesMessage = "unsupported features in " + entries.size() + " methods" + "\nDetailed message:\n" + outputStream.toString();
                throw new UnsupportedFeatureException(unsupportedFeaturesMessage);
            }

        }
    }

    public void checkMethod(AnalysisMethod method, StructuredGraph graph) {
        if (method.isEntryPoint() && !Modifier.isStatic(graph.method().getModifiers())) {
            ValueNode receiver = graph.start().stateAfter().localAt(0);
            if (receiver != null && receiver.usages().count() > 0) {
                /*
                 * Entry point methods should be static. However, for unit testing we also use JUnit
                 * test methods as entry points, and they are by convention non-static. If the
                 * receiver was used, the execution would crash because the receiver is null (or
                 * undefined).
                 */
                throw new UnsupportedFeatureException("Entry point is non-static and uses its receiver: " + method.format("%r %H.%n(%p)"));
            }
        }
    }
}
