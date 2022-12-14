/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.classfile;

import static com.oracle.truffle.espresso.classfile.ClassfileParser.JAVA_6_VERSION;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.LocalVariableTable;
import com.oracle.truffle.espresso.runtime.Attribute;

import java.util.Arrays;

public final class CodeAttribute extends Attribute {

    public static final Symbol<Name> NAME = Name.Code;

    private final int majorVersion;

    private final int maxStack;
    private final int maxLocals;

    @CompilationFinal(dimensions = 1) //
    private final byte[] code;

    @CompilationFinal(dimensions = 1) //
    private final byte[] originalCode; // no bytecode patching

    @CompilationFinal(dimensions = 1) //
    private final ExceptionHandler[] exceptionHandlerEntries;

    @CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    public CodeAttribute(Symbol<Name> name, int maxStack, int maxLocals, byte[] code, ExceptionHandler[] exceptionHandlerEntries, Attribute[] attributes, int majorVersion) {
        super(name, null);
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.code = code;
        this.originalCode = Arrays.copyOf(code, code.length);
        this.exceptionHandlerEntries = exceptionHandlerEntries;
        this.attributes = attributes;
        this.majorVersion = majorVersion;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public int getMaxLocals() {
        return maxLocals;
    }

    public Attribute[] getAttributes() {
        return attributes;
    }

    public byte[] getCode() {
        return code;
    }

    public byte[] getOriginalCode() {
        return originalCode;
    }

    public ExceptionHandler[] getExceptionHandlers() {
        return exceptionHandlerEntries;
    }

    public StackMapTableAttribute getStackMapFrame() {
        for (Attribute attr : attributes) {
            if (attr.getName() == Name.StackMapTable) {
                return (StackMapTableAttribute) attr;
            }
        }
        return null;
    }

    public LineNumberTable getLineNumberTableAttribute() {
        for (Attribute attr : attributes) {
            if (attr.getName() == Name.LineNumberTable) {
                return (LineNumberTable) attr;
            }
        }
        return LineNumberTable.EMPTY;
    }

    public LocalVariableTable getLocalvariableTable() {
        for (Attribute attr : attributes) {
            if (attr.getName() == Name.LocalVariableTable) {
                return (LocalVariableTable) attr;
            }
        }
        return LocalVariableTable.EMPTY;
    }

    public LocalVariableTable getLocalvariableTypeTable() {
        for (Attribute attr : attributes) {
            if (attr.getName() == Name.LocalVariableTypeTable) {
                return (LocalVariableTable) attr;
            }
        }
        return LocalVariableTable.EMPTY;
    }

    public int bciToLineNumber(int bci) {
        LineNumberTable lnt = getLineNumberTableAttribute();
        if (lnt == LineNumberTable.EMPTY) {
            return -1;
        }
        return lnt.getLineNumber(bci);
    }

    public boolean useStackMaps() {
        return majorVersion >= JAVA_6_VERSION;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public CodeAttribute forceSplit() {
        return new CodeAttribute(getName(), maxStack, maxLocals, code.clone(), exceptionHandlerEntries, attributes, majorVersion);
    }

    public void print(Klass klass) {
        try {
            new BytecodeStream(code).printBytecode(klass);
            System.err.println("\n");
            if (getStackMapFrame() != null) {
                getStackMapFrame().print(klass);
            }
        } catch (Throwable e) {
            System.err.println("Throw during printing. Aborting...");
        }
    }

}
