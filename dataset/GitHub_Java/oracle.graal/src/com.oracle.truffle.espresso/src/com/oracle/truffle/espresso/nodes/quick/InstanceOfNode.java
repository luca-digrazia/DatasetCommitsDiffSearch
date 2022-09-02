/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class InstanceOfNode extends QuickNode {

    @CompilerDirectives.CompilationFinal Klass typeToCheck;
    final char cpIndex;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract boolean executeInstanceOf(Klass instanceKlass);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "instanceKlass == cachedKlass")
    boolean instanceOfCached(Klass instanceKlass,
                    @Cached("instanceKlass") Klass cachedKlass,
                    @Cached("instanceOf(typeToCheck, cachedKlass)") boolean cachedAnswer) {
        return cachedAnswer;
    }

    @Specialization(replaces = "instanceOfCached")
    boolean instanceOfSlow(Klass instanceKlass) {
        // Brute instanceof checks, walk the whole klass hierarchy.
        return instanceOf(typeToCheck, instanceKlass);
    }

    InstanceOfNode(char cpIndex, int top, int curBCI) {
        super(top, curBCI);
        assert !typeToCheck.isPrimitive();
        this.cpIndex = cpIndex;
    }

    @TruffleBoundary
    static boolean instanceOf(Klass typeToCheck, Klass instanceKlass) {
        // TODO(peterssen): Method lookup is uber-slow and non-spec-compliant.
        return typeToCheck.isAssignableFrom(instanceKlass);
    }

    private boolean doInstanceOf(StaticObject receiver) {
        if (typeToCheck == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            typeToCheck = getBytecodesNode().resolveType(Bytecodes.CHECKCAST, cpIndex);
        }
        return executeInstanceOf(receiver.getKlass());
    }

    @Override
    public final int execute(final VirtualFrame frame) {
        // TODO(peterssen): Maybe refrain from exposing the whole root node?.
        BytecodeNode root = getBytecodesNode();
        StaticObject receiver = root.peekObject(frame, top - 1);
        boolean result = StaticObject.notNull(receiver) && doInstanceOf(receiver);
        root.putKind(frame, top - 1, result, JavaKind.Boolean);
        return 0; // stack effect -> pop receiver, push boolean
    }
}
