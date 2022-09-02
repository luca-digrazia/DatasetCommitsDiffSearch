/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.api;

import com.oracle.truffle.api.source.Source;

/**
 * A representation of a method.
 */
public interface MethodRef {

    /**
     * Returnes the first code index for a given line within the method
     * @param line the line number in the source code of the method
     * @return the first bci for the line
     */
    long getBCIFromLine(int line);

    /**
     * Returns the source for the method
     * @return the source
     */
    Source getSource();

    /**
     * Determines if the input line number is a valid source line for the method.
     * @param lineNumber
     * @return true if line number is present in method, false otherwise
     */
    boolean hasLine(int lineNumber);

    /**
     * Returns the name of the source file that declares the method.
     * @return the source file name
     */
    String getSourceFile();

    /**
     * Returns a String representation of the name of the method.
     * @return the method name
     */
    String getNameAsString();

    /**
     * Returns the String representation of the signature of the method.
     * @return the method signature name
     */
    String getSignatureAsString();

    /**
     * Returns the method modifiers.
     * @return the bitmask for the modifiers
     */
    int getModifiers();

    /**
     * Returns the source line number for the given input code index.
     * @param bci the code index.
     * @return line number at the code index
     */
    int BCItoLineNumber(int bci);

    /**
     * @return true if method is native, false otherwise
     */
    boolean isMethodNative();

    /**
     * Returns the bytecode for the method in the Class file format.
     * @return the byte array for the bytecode of the method
     */
    byte[] getCode();

    /**
     * Returns all declared parameter types of the method.
     * @return an array of parameter types
     */
    KlassRef[] getParameters();

    /**
     * @return the local variable table of the method
     */
    LocalVariableTableRef getLocalVariableTable();

    /**
     * @return the line number table of the method
     */
    LineNumberTableRef getLineNumberTable();

    /**
     * Invokes the method on the input callee object with input arguments
     * @param callee guest-language object on which to execute the method
     * @param args guest-language arguments used when calling the method
     * @return the guest-language return value
     */
    Object invokeMethod(Object callee, Object[] args);

    /**
     * Determines if the declaring class has a source file attribute.
     * @return true if a source file attribute is present, false otherwise
     */
    boolean hasSourceFileAttribute();

    /**
     * Determines if the code index is located in the source file on the last
     * line of this method.
     * @param codeIndex
     * @return true if last line, false otherwise
     */
    boolean isLastLine(long codeIndex);

    /**
     * Returns the klass that declares this method.
     * @return the declaring klass
     */
    KlassRef getDeclaringKlass();
}
