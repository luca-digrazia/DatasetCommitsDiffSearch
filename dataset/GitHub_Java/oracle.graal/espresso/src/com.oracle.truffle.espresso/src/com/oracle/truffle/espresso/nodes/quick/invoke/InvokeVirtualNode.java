/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.ClassRedefinition;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class InvokeVirtualNode extends QuickNode {

    final Method resolutionSeed;
    final int resultAt;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract Object executeVirtual(StaticObject receiver, Object[] args);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "receiver.getKlass() == cachedKlass", assumptions = "resolvedMethod.getAssumption()")
    Object callVirtualDirect(StaticObject receiver, Object[] args,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("methodLookup(receiver, resolutionSeed)") MethodVersion resolvedMethod,
                    @Cached("create(resolvedMethod.getCallTargetNoInit())") DirectCallNode directCallNode) {
        // getCallTarget doesn't ensure declaring class is initialized
        // so we need the below check prior to executing the method
        if (!resolvedMethod.getMethod().getDeclaringKlass().isInitialized()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            resolvedMethod.getMethod().getDeclaringKlass().safeInitialize();
        }
        return directCallNode.call(args);
    }

    @Specialization(replaces = "callVirtualDirect")
    Object callVirtualIndirect(StaticObject receiver, Object[] arguments,
                    @Cached("create()") IndirectCallNode indirectCallNode) {
        // vtable lookup.
        MethodVersion target = methodLookup(receiver, resolutionSeed);
        if (!target.getMethod().hasCode()) {
            enterExceptionProfile();
            Meta meta = receiver.getKlass().getMeta();
            throw Meta.throwException(meta.java_lang_AbstractMethodError);
        }
        return indirectCallNode.call(target.getCallTarget(), arguments);
    }

    InvokeVirtualNode(Method resolutionSeed, int top, int curBCI) {
        super(top, curBCI);
        assert !resolutionSeed.isStatic();
        this.resolutionSeed = resolutionSeed;
        this.resultAt = top - Signatures.slotsForParameters(resolutionSeed.getParsedSignature()) - 1; // -receiver;
    }

    static MethodVersion methodLookup(StaticObject receiver, Method resolutionSeed) {
        // Suprisingly, invokeVirtuals can try to invoke interface methods, even non-default
        // ones.
        // Good thing is, miranda methods are taken care of at vtable creation !
        if (resolutionSeed.isRemovedByRedefition()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // do not run while a redefinition is in progress
            try {
                ClassRedefinition.lock();

                // first check to see if there's a compatible new method before
                // bailing out with a NoSuchMethodError
                Klass receiverKlass = receiver.getKlass();
                Method method = receiverKlass.lookupMethod(resolutionSeed.getName(), resolutionSeed.getRawSignature(), receiverKlass);
                Meta meta = resolutionSeed.getMeta();
                if (method == null) {
                    throw Meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError,
                                    meta.toGuestString(resolutionSeed.getDeclaringKlass().getNameAsString() + "." + resolutionSeed.getName() + resolutionSeed.getRawSignature()));
                } else if (method.isStatic()) {
                    throw Meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "expected non-static method: " + method.getName());
                } else {
                    return method.getMethodVersion();
                }
            } finally {
                ClassRedefinition.unlock();
            }
        }
        Klass receiverKlass = receiver.getKlass();
        int vtableIndex = resolutionSeed.getVTableIndex();
        if (receiverKlass.isArray()) {
            return receiverKlass.getSuperKlass().vtableLookup(vtableIndex).getMethodVersion();
        }
        return receiverKlass.vtableLookup(vtableIndex).getMethodVersion();
    }

    @Override
    public final int execute(VirtualFrame frame, long[] primitives, Object[] refs) {
        // Method signature does not change across methods.
        // Can safely use the constant signature from `resolutionSeed` instead of the non-constant
        // signature from the lookup.
        Object[] args = BytecodeNode.popArguments(primitives, refs, top, true, resolutionSeed.getParsedSignature());
        StaticObject receiver = nullCheck((StaticObject) args[0]);
        Object result = executeVirtual(receiver, args);
        return (getResultAt() - top) + BytecodeNode.putKind(primitives, refs, getResultAt(), result, resolutionSeed.getReturnKind());
    }

    @Override
    public boolean removedByRedefintion() {
        return resolutionSeed.isRemovedByRedefition();
    }

    @Override
    public final boolean producedForeignObject(Object[] refs) {
        return resolutionSeed.getReturnKind().isObject() && BytecodeNode.peekObject(refs, getResultAt()).isForeignObject();
    }

    private int getResultAt() {
        return resultAt;
    }
}
