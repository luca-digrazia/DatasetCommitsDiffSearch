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
package com.oracle.graal.asm;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Architecture.*;

/**
 * The platform-independent base class for the assembler.
 */
public abstract class AbstractAssembler {
    public final TargetDescription target;
    public final Buffer codeBuffer;

    public AbstractAssembler(TargetDescription target) {
        this.target = target;

        if (target.arch.getByteOrder() == ByteOrder.BigEndian) {
            this.codeBuffer = new Buffer.BigEndian();
        } else {
            this.codeBuffer = new Buffer.LittleEndian();
        }
    }

    public final void bind(Label l) {
        assert !l.isBound() : "can bind label only once";
        l.bind(codeBuffer.position());
        l.patchInstructions(this);
    }

    public abstract void align(int modulus);

    public abstract void softAlign(int modulus);

    public abstract void jmp(Label l);

    protected abstract void patchJumpTarget(int branch, int jumpTarget);

    /**
     * Emits instruction(s) that access an address specified by a given displacement from the stack pointer
     * in the direction that the stack grows (which is down on most architectures).
     *
     * @param disp the displacement from the stack pointer at which the stack should be accessed
     */
    public abstract void bangStack(int disp);

    protected final void emitByte(int x) {
        codeBuffer.emitByte(x);
    }

    protected final void emitShort(int x) {
        codeBuffer.emitShort(x);
    }

    protected final void emitInt(int x) {
        codeBuffer.emitInt(x);
    }

    protected final void emitLong(long x) {
        codeBuffer.emitLong(x);
    }
}
