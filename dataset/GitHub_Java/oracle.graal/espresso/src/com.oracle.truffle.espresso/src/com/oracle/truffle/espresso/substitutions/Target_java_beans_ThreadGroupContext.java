/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.plugins.jdkcaches.JDKCacheRedefinitionPlugin;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public class Target_java_beans_ThreadGroupContext {

    @Substitution(hasReceiver = true, methodName = "<init>")
    public static void init(
            @Host(Class.class) StaticObject context,
            // Checkstyle: stop
            @GuestCall(target = "java_beans_ThreadGroupContext_init", original = true) DirectCallNode original,
            // Checkstyle: resume
            @InjectMeta Meta meta) {

        // for class redefinition we need to collect details about beans
        JDKCacheRedefinitionPlugin plugin = meta.getContext().lookup(JDKCacheRedefinitionPlugin.class);
        if (plugin != null) {
            plugin.registerThreadGroupContext(context);
        }
        // call original method
        original.call(context);
    }
}
