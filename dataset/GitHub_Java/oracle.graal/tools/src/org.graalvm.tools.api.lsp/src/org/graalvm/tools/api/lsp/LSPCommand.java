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
package org.graalvm.tools.api.lsp;

import java.util.List;

import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;

public interface LSPCommand {

    String getName();

    Object execute(LSPServer server, Env env, List<Object> arguments);

    default int getTimeoutMillis() {
        return -1;
    }

    default Object onTimeout(List<Object> arguments) {
        String argumentString = String.join(", ", arguments.toArray(new String[0]));
        if (getTimeoutMillis() > 0) {
            throw new RuntimeException("onTimeout not overriden. Arguments: " + argumentString);
        } else {
            throw new AssertionError("onTimeout triggered on negative or zero timeout. Arguments: " + argumentString);
        }
    }
}
