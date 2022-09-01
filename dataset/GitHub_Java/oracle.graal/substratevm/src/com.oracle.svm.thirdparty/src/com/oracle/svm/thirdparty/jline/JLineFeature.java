/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.thirdparty.jline;

// Checkstyle: allow reflection

import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;

@AutomaticFeature
final class JLineFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Class<?> terminalFactoryClass = access.findClassByName("jline.TerminalFactory");
        if (terminalFactoryClass == null) {
            /* JLine is not on the classpath. Nothing to do for this feature. */
            return;
        }

        Object[] createMethods = Arrays.stream(terminalFactoryClass.getDeclaredMethods())
                        .filter(m -> Modifier.isStatic(m.getModifiers()) && m.getName().equals("create"))
                        .toArray();
        access.registerReachabilityHandler(JLineFeature::registerTerminalConstructor, createMethods);
    }

    private static void registerTerminalConstructor(DuringAnalysisAccess access) {
        /*
         * TerminalFactory.create methods instantiate the actual Terminal implementation class via
         * reflection. We cannot automatically constant fold which class is going to be used, so we
         * register the default for each platform. If the user manually overrides the default
         * implementation class, they also need to provide a reflection configuration for that
         * class.
         */
        Class<?> terminalClass = access.findClassByName(Platform.includedIn(Platform.WINDOWS.class) ? "jline.AnsiWindowsTerminal" : "jline.UnixTerminal");
        if (terminalClass != null) {
            RuntimeReflection.register(terminalClass);
            RuntimeReflection.register(terminalClass.getDeclaredConstructors());
        }
    }
}
