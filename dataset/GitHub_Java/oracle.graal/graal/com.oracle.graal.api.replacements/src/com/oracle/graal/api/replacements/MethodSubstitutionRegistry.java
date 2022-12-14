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
package com.oracle.graal.api.replacements;

import java.lang.reflect.Type;

/**
 * A registry for {@link MethodSubstitution}s.
 */
public interface MethodSubstitutionRegistry {

    /**
     * Registers a substitution method.
     *
     * @param substituteDeclaringClass the class declaring the substitute method
     * @param name the name of both the original and substitute method
     * @param argumentTypes the argument types of the method
     */
    default void registerMethodSubstitution(Class<?> substituteDeclaringClass, String name, Type... argumentTypes) {
        registerMethodSubstitution(substituteDeclaringClass, name, name, argumentTypes);
    }

    /**
     * Registers a substitution method.
     *
     * @param substituteDeclaringClass the class declaring the substitute method
     * @param name the name of both the original method
     * @param substituteName the name of the substitute method
     * @param argumentTypes the argument types of the method
     */
    void registerMethodSubstitution(Class<?> substituteDeclaringClass, String name, String substituteName, Type... argumentTypes);
}
