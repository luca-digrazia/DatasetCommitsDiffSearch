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

package com.oracle.truffle.espresso.vm;

import static com.oracle.truffle.espresso.meta.EspressoError.cat;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.PackageTable;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.jni.Pointer;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;

/**
 * Helper to reduce cluttering of the {@link VM} class.
 */
public final class ModulesHelperVM {
    private ModulesHelperVM() {
    }

    private static ModuleTable.ModuleEntry getModuleEntry(@Host(typeName = "Ljava/lang/Module") StaticObject module, Meta meta) {
        return (ModuleTable.ModuleEntry) module.getHiddenObjectField(meta.HIDDEN_MODULE_ENTRY);
    }

    private static PackageTable.PackageEntry getPackageEntry(ModuleTable.ModuleEntry fromModuleEntry, Symbol<Symbol.Name> nameSymbol) {
        return fromModuleEntry.registry().packages().lookup(nameSymbol);
    }

    static ModuleTable.ModuleEntry extractToModuleEntry(@Host(typeName = "Ljava/lang/Module") StaticObject toModule, Meta meta,
                    SubstitutionProfiler profiler) {
        ModuleTable.ModuleEntry toModuleEntry = null;
        if (!StaticObject.isNull(toModule)) {
            toModuleEntry = getModuleEntry(toModule, meta);
            if (toModuleEntry == null) {
                profiler.profile(8);
                throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "to_module is invalid");
            }
        }
        return toModuleEntry;
    }

    static ModuleTable.ModuleEntry extractFromModuleEntry(@Host(typeName = "Ljava/lang/Module") StaticObject fromModule, Meta meta,
                    SubstitutionProfiler profiler) {
        if (StaticObject.isNull(fromModule)) {
            profiler.profile(9);
            throw meta.throwNullPointerException();
        }
        ModuleTable.ModuleEntry fromModuleEntry = getModuleEntry(fromModule, meta);
        if (fromModuleEntry == null) {
            profiler.profile(10);
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "from_module cannot be found");
        }
        return fromModuleEntry;
    }

    static PackageTable.PackageEntry extractPackageEntry(@Pointer TruffleObject pkgName, ModuleTable.ModuleEntry fromModuleEntry, Meta meta, SubstitutionProfiler profiler) {
        String pkg = NativeEnv.interopPointerToString(pkgName);
        PackageTable.PackageEntry packageEntry = null;
        Symbol<Symbol.Name> nameSymbol = meta.getContext().getNames().lookup(pkg);
        if (nameSymbol != null) {
            packageEntry = getPackageEntry(fromModuleEntry, nameSymbol);
        }
        if (packageEntry == null) {
            profiler.profile(11);
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
                            cat("package ", pkg, " cannot be found in ", fromModuleEntry.getNameAsString()));
        }
        if (packageEntry.module() != fromModuleEntry) {
            profiler.profile(12);
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
                            cat("package ", pkg, " found in ", packageEntry.module().getNameAsString(), ", not in ", fromModuleEntry.getNameAsString()));
        }
        return packageEntry;
    }

    static void addModuleExports(@Host(typeName = "Ljava/lang/Module") StaticObject fromModule,
                    @Pointer TruffleObject pkgName,
                    @Host(typeName = "Ljava/lang/Module") StaticObject toModule,
                    Meta meta,
                    InteropLibrary unchached,
                    SubstitutionProfiler profiler) {
        if (unchached.isNull(pkgName)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        ModuleTable.ModuleEntry fromModuleEntry = extractFromModuleEntry(fromModule, meta, profiler);
        if (!fromModuleEntry.isNamed() || fromModuleEntry.isOpen()) {
            // All packages in unnamed and open modules are exported by default.
            return;
        }
        ModuleTable.ModuleEntry toModuleEntry = extractToModuleEntry(toModule, meta, profiler);
        PackageTable.PackageEntry packageEntry = extractPackageEntry(pkgName, fromModuleEntry, meta, profiler);
        if (fromModuleEntry != toModuleEntry) {
            packageEntry.addExports(toModuleEntry);
        }
    }

}
