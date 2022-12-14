/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common;

import static com.oracle.graal.compiler.common.CollectionsFactory.Mode.*;

import java.util.*;

/**
 * Factory for creating collection objects used during compilation.
 */
public class CollectionsFactory {

    private static final ThreadLocal<Mode> tl = new ThreadLocal<>();

    public static class ModeScope implements AutoCloseable {
        private final Mode previousMode;

        public ModeScope(Mode previousMode) {
            this.previousMode = previousMode;
        }

        public void close() {
            tl.set(previousMode);
        }
    }

    /**
     * Constants denoting what type of collections are {@link CollectionsFactory#getMode()
     * currently} returned by the factory.
     */
    public enum Mode {
        /**
         * Denotes standard collections such as {@link HashSet} and {@link HashMap}.
         */
        STANDARD,

        /**
         * Denotes collections that have a deterministic iteration order over their keys/entries.
         */
        DETERMINISTIC_ITERATION_ORDER;
    }

    /**
     * Gets the current mode determining the type of collection objects created by this factory.
     */
    public static Mode getMode() {
        Mode mode = tl.get();
        return mode == null ? Mode.STANDARD : mode;
    }

    /**
     * Updates the mode for the current thread.
     *
     * @return an object which when {@linkplain ModeScope#close() closed} will revert the mode of
     *         the current thread to the stae before calling this method
     */
    public static ModeScope changeMode(Mode mode) {
        Mode previousMode = tl.get();
        tl.set(mode);
        return new ModeScope(previousMode);
    }

    public static <K, V> HashMap<K, V> newMap() {
        return getMode() == STANDARD ? new HashMap<>() : new LinkedHashMap<>();
    }

    public static <K, V> HashMap<K, V> newMap(Map<K, V> m) {
        return getMode() == STANDARD ? new HashMap<>(m) : new LinkedHashMap<>(m);
    }

    public static <K, V> HashMap<K, V> newMap(int initialCapacity) {
        return getMode() == STANDARD ? new HashMap<>(initialCapacity) : new LinkedHashMap<>(initialCapacity);
    }

    public static <K, V> Map<K, V> newIdentityMap() {
        return getMode() == STANDARD ? new IdentityHashMap<>() : new LinkedIdentityHashMap<>();
    }

    public static <K, V> Map<K, V> newIdentityMap(int expectedMaxSize) {
        return getMode() == STANDARD ? new IdentityHashMap<>(expectedMaxSize) : new LinkedIdentityHashMap<>();
    }

    public static <K, V> Map<K, V> newIdentityMap(Map<K, V> m) {
        return getMode() == STANDARD ? new IdentityHashMap<>(m) : new LinkedIdentityHashMap<>(m);
    }
}
