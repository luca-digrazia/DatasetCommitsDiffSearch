/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.*;

import com.oracle.max.graal.compiler.debug.*;


/**
 * This class contains a number of fields that collect metrics about compilation, particularly
 * the number of times certain optimizations are performed.
 */
public final class GraalMetrics {
    public static int CompiledMethods;
    public static int TargetMethods;
    public static int LocalValueNumberHits;
    public static int ValueMapResizes;
    public static int InlinedFinalizerChecks;
    public static int InlineForcedMethods;
    public static int InlineForbiddenMethods;
    public static int InlinedJsrs;
    public static int BlocksDeleted;
    public static int BytecodesCompiled;
    public static int CodeBytesEmitted;
    public static int SafepointsEmitted;
    public static int ExceptionHandlersEmitted;
    public static int DataPatches;
    public static int DirectCallSitesEmitted;
    public static int IndirectCallSitesEmitted;
    public static int HIRInstructions;
    public static int LiveHIRInstructions;
    public static int LIRInstructions;
    public static int LIRVariables;
    public static int LIRXIRInstructions;
    public static int LIRMoveInstructions;
    public static int LSRAIntervalsCreated;
    public static int LSRASpills;
    public static int LoadConstantIterations;
    public static int CodeBufferCopies;
    public static int UniqueValueIdsAssigned;
    public static int FrameStatesCreated;
    public static int FrameStateValuesCreated;
    public static int NodesCanonicalized;

    public static void print() {
        printClassFields(GraalMetrics.class);

        for (Entry<String, GraalMetrics> m : map.entrySet()) {
            printField(m.getKey(), m.getValue().value);
        }
    }

    private static LinkedHashMap<String, GraalMetrics> map = new LinkedHashMap<String, GraalMetrics>();

    public static GraalMetrics get(String name) {
        if (!map.containsKey(name)) {
            map.put(name, new GraalMetrics(name));
        }
        return map.get(name);
    }

    private GraalMetrics(String name) {
        this.name = name;
    }

    private int value;
    private String name;

    public void increment() {
        increment(1);
    }

    public void increment(int val) {
        value += val;
    }

    public static void printClassFields(Class<?> javaClass) {
        final String className = javaClass.getSimpleName();
        TTY.println(className + " {");
        for (final Field field : javaClass.getFields()) {
            printField(field, false);
        }
        TTY.println("}");
    }

    public static void printField(final Field field, boolean tabbed) {
        final String fieldName = String.format("%35s", field.getName());
        try {
            String prefix = tabbed ? "" : "    " + fieldName + " = ";
            String postfix = tabbed ? "\t" : "\n";
            if (field.getType() == int.class) {
                TTY.print(prefix + field.getInt(null) + postfix);
            } else if (field.getType() == boolean.class) {
                TTY.print(prefix + field.getBoolean(null) + postfix);
            } else if (field.getType() == float.class) {
                TTY.print(prefix + field.getFloat(null) + postfix);
            } else if (field.getType() == String.class) {
                TTY.print(prefix + field.get(null) + postfix);
            } else if (field.getType() == Map.class) {
                Map<?, ?> m = (Map<?, ?>) field.get(null);
                TTY.print(prefix + printMap(m) + postfix);
            } else {
                TTY.print(prefix + field.get(null) + postfix);
            }
        } catch (IllegalAccessException e) {
            // do nothing.
        }
    }

    private static String printMap(Map<?, ?> m) {
        StringBuilder sb = new StringBuilder();

        List<String> keys = new ArrayList<String>();
        for (Object key : m.keySet()) {
            keys.add((String) key);
        }
        Collections.sort(keys);

        for (String key : keys) {
            sb.append(key);
            sb.append("\t");
            sb.append(m.get(key));
            sb.append("\n");
        }

        return sb.toString();
    }

    private static void printField(String fieldName, long value) {
        TTY.print("    " + fieldName + " = " + value + "\n");
    }

    private static void printField(String fieldName, double value) {
        TTY.print("    " + fieldName + " = " + value + "\n");
    }
}

