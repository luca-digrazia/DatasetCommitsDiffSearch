/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.impl;

import java.util.ArrayList;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class ModuleTable extends EntryTable<ModuleTable.ModuleEntry, ClassRegistry> {
    public static final Object moduleLock = new Object();

    @Override
    public Object getLock() {
        return moduleLock;
    }

    @Override
    public ModuleEntry createEntry(Symbol<Name> name, ClassRegistry registry) {
        return new ModuleEntry(name, registry);
    }

    public static class ModuleEntry implements EntryTable.NamedEntry {
        private Symbol<Name> name;

        ModuleEntry(Symbol<Name> name, ClassRegistry data) {
            this.name = name;
            this.registry = data;
        }

        public static ModuleEntry createUnnamedModuleEntry(StaticObject module, ClassRegistry registry) {
            ModuleEntry result = new ModuleEntry(null, registry);
            result.setCanReadAllUnnamed();
            if (!StaticObject.isNull(module)) {
                result.setModule(module);
            }
            result.isOpen = true;
            return result;
        }

        @Override
        public Symbol<Name> getName() {
            return name;
        }

        private final ClassRegistry registry;
        private StaticObject module;
        private boolean canReadAllUnnamed = false;
        private boolean isOpen = false;

        private ArrayList<ModuleEntry> reads;

        public void addReads(ModuleEntry module) {
            if (!isNamed()) {
                return;
            }
            synchronized (moduleLock) {
                if (module == null) {
                    setCanReadAllUnnamed();
                    return;
                }
                if (reads == null) {
                    reads = new ArrayList<>();
                }
                if (!reads.contains(module)) {
                    reads.add(module);
                }
            }
        }

        public boolean canRead(ModuleEntry module) {
            if (!module.isNamed() || module.isJavaBase()) {
                return true;
            }
            synchronized (moduleLock) {
                if (!hasReads()) {
                    return false;
                } else {
                    return reads.contains(module);
                }
            }
        }

        public void setModule(StaticObject module) {
            this.module = module;
        }

        public void setCanReadAllUnnamed() {
            canReadAllUnnamed = true;
        }

        public boolean isOpen() {
            return isOpen;
        }

        public boolean isNamed() {
            return getName() != null;
        }

        public boolean isJavaBase() {
            return false;
        }

        public boolean hasReads() {
            return reads != null && !reads.isEmpty();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ModuleEntry)) {
                return false;
            }
            ModuleEntry module = (ModuleEntry) obj;
            return this.name == module.name;
        }
    }
}
