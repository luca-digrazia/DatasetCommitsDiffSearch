/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.runtime.Classpath.JAVA_BASE;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.ModulesHelperVM;

@EspressoSubstitutions
public class Target_java_lang_Module {

    @Substitution
    public static void addExports0(@Host(typeName = "Ljava/lang/Module;") StaticObject from,
                    @Host(String.class) StaticObject pn,
                    @Host(typeName = "Ljava/lang/Module;") StaticObject to,
                    @InjectMeta Meta meta,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(pn)) {
            throw meta.throwNullPointerException();
        }
        ModulesHelperVM.addModuleExports(from, meta.toHostString(pn).replace('.', '/'), to, meta, profiler);
    }

    @Substitution
    public static void addExportsToAll0(@Host(typeName = "Ljava/lang/Module;") StaticObject from,
                    @Host(String.class) StaticObject pn,
                    @InjectMeta Meta meta,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(pn)) {
            throw meta.throwNullPointerException();
        }
        ModulesHelperVM.addModuleExports(from, meta.toHostString(pn).replace('.', '/'), StaticObject.NULL, meta, profiler);
    }

    @Substitution
    public static void addExportsToAllUnnamed0(@Host(typeName = "Ljava/lang/Module;") StaticObject from,
                    @Host(String.class) StaticObject pn,
                    @InjectMeta Meta meta,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(pn)) {
            throw meta.throwNullPointerException();
        }
        ModulesHelperVM.addModuleExportsToAllUnnamed(from, meta.toHostString(pn).replace('.', '/'), profiler, meta);
    }

    @Substitution
    @TruffleBoundary
    public static void defineModule0(@Host(typeName = "Ljava/lang/Module;") StaticObject module,
                    boolean isOpen,
                    @SuppressWarnings("unused") @Host(String.class) StaticObject version,
                    @SuppressWarnings("unused") @Host(String.class) StaticObject location,
                    @Host(Object[].class) StaticObject pns,
                    @InjectMeta Meta meta,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(module)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        if (!meta.java_lang_Module.isAssignableFrom(module.getKlass())) {
            profiler.profile(1);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "module is not an instance of java.lang.Module");
        }
        StaticObject guestName = meta.java_lang_Module_name.getObject(module);
        if (StaticObject.isNull(guestName)) {
            profiler.profile(2);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "module name cannot be null");
        }
        String hostName = meta.toHostString(guestName);
        String[] packages = toStringArray(pns, meta);
        if (hostName.equals(JAVA_BASE)) {
            profiler.profile(5);
            meta.getVM().defineJavaBaseModule(module, packages, profiler);
        } else {
            profiler.profile(6);
            meta.getVM().defineModule(module, hostName, isOpen, packages, profiler);
        }
    }

    private static String[] toStringArray(StaticObject packages, Meta meta) {
        String[] strs = new String[packages.length()];
        StaticObject[] unwrapped = packages.unwrap();
        for (int i = 0; i < unwrapped.length; i++) {
            StaticObject str = unwrapped[i];
            if (StaticObject.isNull(str)) {
                throw meta.throwNullPointerException();
            }
            if (meta.java_lang_String != str.getKlass()) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Package name array contains a non-string");
            }
            strs[i] = meta.toHostString(str).replace('.', '/');
        }
        return strs;
    }
}
