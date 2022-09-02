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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.espresso.jdwp.impl.JDWPDebuggerController;
import com.oracle.truffle.espresso.jdwp.impl.JDWPInstrument;

import java.util.HashMap;

/**
 * Main entry point for setting up JDWP. The class looks up the JDWP instrument
 * and initializes communication with the debugger.
 */
public class JDWPSetup {

    private static final HashMap<JDWPContext, JDWPDebuggerController> contextMap = new HashMap<>();

    public static void setup(TruffleLanguage.Env env, JDWPOptions options, JDWPContext context) {
        JDWPDebuggerController controller = env.lookup(env.getInstruments().get(JDWPInstrument.ID), JDWPDebuggerController.class);
        controller.initialize(env, options, context, false);
    }

    public static void finalize(JDWPContext context) {
        JDWPDebuggerController controller = contextMap.get(context);
        if (controller != null) {
            VMEventListeners.getDefault().vmDied();
            controller.disposeDebugger(false);
        }
    }
}
