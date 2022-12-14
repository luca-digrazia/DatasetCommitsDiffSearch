/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.tiers;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.phases.*;

public final class Suites {

    public final PhaseSuite<HighTierContext> highTier;
    public final PhaseSuite<MidTierContext> midTier;

    public static final Suites DEFAULT;

    private static final Map<String, CompilerConfiguration> configurations;

    static {
        configurations = new HashMap<>();
        for (CompilerConfiguration config : ServiceLoader.loadInstalled(CompilerConfiguration.class)) {
            String name = config.getClass().getSimpleName();
            if (name.endsWith("Configuration")) {
                name = name.substring(0, name.length() - "Configuration".length());
            }
            configurations.put(name.toLowerCase(), config);
        }

        DEFAULT = createDefaultSuites();
    }

    private Suites(CompilerConfiguration config) {
        highTier = config.createHighTier();
        midTier = config.createMidTier();
    }

    public static Suites createDefaultSuites() {
        return createSuites(GraalOptions.CompilerConfiguration);
    }

    public static Suites createSuites(String name) {
        CompilerConfiguration config = configurations.get(name);
        if (config == null) {
            throw new GraalInternalError("unknown compiler configuration: " + name);
        }
        return new Suites(config);
    }

}
