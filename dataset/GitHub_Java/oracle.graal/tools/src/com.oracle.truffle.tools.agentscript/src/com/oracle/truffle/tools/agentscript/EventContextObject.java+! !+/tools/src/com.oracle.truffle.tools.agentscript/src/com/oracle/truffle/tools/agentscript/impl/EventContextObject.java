/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
final class EventContextObject implements TruffleObject {
    private final EventContext context;
    @CompilerDirectives.CompilationFinal private String name;

    EventContextObject(EventContext context) {
        this.context = context;
    }

    @ExportMessage
    static boolean hasMembers(EventContextObject obj) {
        return true;
    }

    @ExportMessage
    static Object getMembers(EventContextObject obj, boolean includeInternal) {
        return new Object[0];
    }

    @ExportMessage
    static Object readMember(EventContextObject obj, String member) throws UnknownIdentifierException {
        switch (member) {
            case "name":
                if (obj.name == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    obj.name = obj.context.getInstrumentedNode().getRootNode().getName();
                }
                return obj.name;
            default:
                throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    static boolean isMemberReadable(EventContextObject obj, String member) {
        return true;
    }

}
