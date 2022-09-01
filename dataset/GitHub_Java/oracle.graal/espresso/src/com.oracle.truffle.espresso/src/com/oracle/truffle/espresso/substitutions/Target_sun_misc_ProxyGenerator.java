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
import com.oracle.truffle.espresso.redefinition.plugins.jdkproxy.JDKProxyRedefinitionPlugin;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public final class Target_sun_misc_ProxyGenerator {

    @Substitution(versionFilter = VersionFilter.Java8OrEarlier.class)
    public static @Host(byte[].class) StaticObject generateProxyClass(
                    @Host(String.class) StaticObject proxyName,
                    @Host(Class[].class) StaticObject interfaces,
                    int classModifier,
                    // Checkstyle: stop
                    @GuestCall(target = "sun_misc_ProxyGenerator_generateProxyClass", original = true) DirectCallNode original,
                    // Checkstyle: resume
                    @InjectMeta Meta meta) {

        // for class redefinition we need to collect details about generated JDK Dynamic proxies
        JDKProxyRedefinitionPlugin plugin = meta.getContext().lookup(JDKProxyRedefinitionPlugin.class);
        if (plugin != null) {
            plugin.collectProxyArguments(proxyName, interfaces, classModifier, original);
        }
        // call original method
        return (StaticObject) original.call(proxyName, interfaces, classModifier);
    }
}
