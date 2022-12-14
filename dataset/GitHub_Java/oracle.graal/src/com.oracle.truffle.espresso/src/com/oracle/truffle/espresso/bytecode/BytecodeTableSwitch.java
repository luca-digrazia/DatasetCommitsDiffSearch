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
package com.oracle.truffle.espresso.bytecode;

/**
 * A utility for processing {@link Bytecodes#TABLESWITCH} bytecodes.
 */
public class BytecodeTableSwitch extends BytecodeSwitch {

    private static final int OFFSET_TO_LOW_KEY = 4;
    private static final int OFFSET_TO_HIGH_KEY = 8;
    private static final int OFFSET_TO_FIRST_JUMP_OFFSET = 12;
    private static final int JUMP_OFFSET_SIZE = 4;

    /**
     * Constructor for a {@link BytecodeStream}.
     *
     * @param stream the {@code BytecodeStream} containing the switch instruction
     */
    public BytecodeTableSwitch(BytecodeStream stream) {
        super(stream);
    }

    /**
     * Gets the low key of the table switch.
     *
     * @return the low key
     */
    public int lowKey(int bci) {
        return stream.readInt(getAlignedBci(bci) + OFFSET_TO_LOW_KEY);
    }

    /**
     * Gets the high key of the table switch.
     *
     * @return the high key
     */
    public int highKey(int bci) {
        return stream.readInt(getAlignedBci(bci) + OFFSET_TO_HIGH_KEY);
    }

    @Override
    public int keyAt(int bci, int i) {
        return lowKey(bci) + i;
    }

    @Override
    public int offsetAt(int bci, int i) {
        return stream.readInt(getAlignedBci(bci) + OFFSET_TO_FIRST_JUMP_OFFSET + JUMP_OFFSET_SIZE * i);
    }

    @Override
    public int numberOfCases(int bci) {
        return highKey(bci) - lowKey(bci) + 1;
    }

    @Override
    public int size(int bci) {
        return getAlignedBci(bci) + OFFSET_TO_FIRST_JUMP_OFFSET + JUMP_OFFSET_SIZE * numberOfCases(bci) - bci;
    }
}
