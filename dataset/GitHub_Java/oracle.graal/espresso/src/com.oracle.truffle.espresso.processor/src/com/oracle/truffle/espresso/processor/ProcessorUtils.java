/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.processor;

public class ProcessorUtils {
    public static String stringify(String str) {
        return '\"' + str + '\"';
    }

    public static String methodDeclaration(String modifiers, String returnType, String methodName, String[] arguments) {
        StringBuilder str = new StringBuilder();
        if (modifiers != null) {
            str.append(modifiers).append(" ");
        }
        if (returnType != null) {
            str.append(returnType).append(" ");
        }
        str.append(methodName).append("(");
        str.append(listToString(arguments, ", "));
        str.append(")");
        return str.toString();
    }

    public static String fieldDeclaration(String modifiers, String type, String fieldName, String defaultValue) {
        return modifiers + " " + type + " " + fieldName + ((defaultValue == null) ? "" : (" = " + defaultValue)) + ";";
    }

    public static String argument(String className, String argName) {
        return className + " " + argName;
    }

    public static String assignment(String varName, String value) {
        return varName + " = " + value + ";";
    }

    public static String call(String receiver, String methodName, String[] args) {
        StringBuilder str = new StringBuilder();
        if (receiver != null) {
            str.append(receiver).append(".");
        }
        str.append(methodName);
        str.append("(");
        str.append(listToString(args, ", "));
        str.append(")");
        return str.toString();
    }

    public static String listToString(String[] strs, String separator) {
        StringBuilder str = new StringBuilder();
        boolean first = true;
        for (String arg : strs) {
            if (!first) {
                str.append(separator);
            }
            str.append(arg);
            first = false;
        }
        return str.toString();
    }
}
