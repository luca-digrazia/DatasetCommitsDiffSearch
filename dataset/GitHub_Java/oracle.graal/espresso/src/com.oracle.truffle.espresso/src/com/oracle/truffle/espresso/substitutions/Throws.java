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

package com.oracle.truffle.espresso.substitutions;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * List of (checked) guest exceptions a method may throw, equivalent to Java `throws`. Checked
 * exceptions are a Java construct, not enforced by the VM. This annotation is optional, it
 * preserves the Java meta-data.
 *
 * <pre>
 * {@code @Throws(CloneNotSupportedException.class)}
 * {@code @Throws(hostClasses = @Host(typeName = "Lmy/package/MyCheckedException;"))}
 * {@literal @}Throws({IllegalAccessException.class, IllegalArgumentException.class, InvocationTargetException.class})
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {METHOD})
public @interface Throws {
    /**
     * List of (checked?) exceptions the methods throws.
     */
    Class<? extends Throwable>[] value() default {};

    /**
     * List of exceptions classes that are not accessible.
     */
    Host[] hostClasses() default {};
}
