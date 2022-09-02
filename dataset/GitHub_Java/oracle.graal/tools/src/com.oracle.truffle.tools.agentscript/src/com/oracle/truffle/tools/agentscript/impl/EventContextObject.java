/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unused")
@ExportLibrary(InteropLibrary.class)
final class EventContextObject extends AbstractContextObject {
    private final EventContext context;

    EventContextObject(EventContext context) {
        this.context = context;
    }

    @CompilerDirectives.TruffleBoundary
    RuntimeException wrap(Object target, int arity, InteropException ex) {
        IllegalStateException ill = new IllegalStateException("Cannot invoke " + target + " with " + arity + " arguments: " + ex.getMessage());
        ill.initCause(ex);
        return context.createError(ill);
    }

    RuntimeException rethrow(RuntimeException ex, InteropLibrary interopLib) {
        if (interopLib.isException(ex)) {
            throw context.createError(ex);
        }
        throw ex;
    }

    @ExportMessage
    static boolean hasMembers(EventContextObject obj) {
        return true;
    }

    @ExportMessage
    static Object getMembers(EventContextObject obj, boolean includeInternal) {
        return MEMBERS;
    }

    @ExportMessage
    Object readMember(String member) throws UnknownIdentifierException {
        return super.readMember(member);
    }

    @ExportMessage
    static boolean isMemberReadable(EventContextObject obj, String member) {
        return MEMBERS.contains(member);
    }

    @ExportMessage
    static Object invokeMember(EventContextObject obj, String member, Object[] args) throws ArityException, UnknownIdentifierException {
        if ("returnNow".equals(member)) {
            throw AgentExecutionNode.returnNow(obj.context, args);
        }
        if ("returnValue".equals(member)) {
            if (args.length == 0 || !(args[0] instanceof VariablesObject)) {
                return NullObject.nullCheck(null);
            }
            VariablesObject vars = (VariablesObject) args[0];
            return vars.getReturnValue();
        }
        if ("iterateFrames".equals(member)) {
            return iterateFrames(args, obj);
        }
        throw UnknownIdentifierException.create(member);
    }

    @CompilerDirectives.TruffleBoundary
    private static Object iterateFrames(Object[] args, EventContextObject obj) {
        if (args.length == 0 || !(args[0] instanceof VariablesObject)) {
            return NullObject.nullCheck(null);
        }
        VariablesObject vars = (VariablesObject) args[0];
        Truffle.getRuntime().iterateFrames((frameInstance) -> {
            if (frameInstance.getCallNode() == null) {
                // skip top most record about the instrument
                return null;
            }
            final Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
            NodeLibrary lib = NodeLibrary.getUncached();
            InteropLibrary iop = InteropLibrary.getUncached();
            final Node n = frameInstance.getCallNode() == null ? obj.getInstrumentedNode() : frameInstance.getCallNode();
            boolean scope = lib.hasScope(n, frame);
            if (scope) {
                try {
                    Object frameVars = lib.getScope(n, frame, false);
                    LocationObject location = new LocationObject(n);
                    iop.execute(args[1], location, frameVars);
                } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException ex) {
                    Logger.getLogger(EventContextObject.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return null;
        });
        return NullObject.nullCheck(null);
    }

    @ExportMessage
    static boolean isMemberInvocable(EventContextObject obj, String member) {
        return "returnNow".equals(member) || "returnValue".equals(member) || "iterateFrames".equals(member);
    }

    Node getInstrumentedNode() {
        return context.getInstrumentedNode();
    }

    @Override
    SourceSection getInstrumentedSourceSection() {
        return context.getInstrumentedSourceSection();
    }
}
