/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.util;

/**
 * Strategy for comparing two objects. Default predefined strategies are {@link #EQUALS},
 * {@link #IDENTITY}, and {@link #IDENTITY_WITH_SYSTEM_HASHCODE}.
 */
public abstract class CompareStrategy {

    public static final CompareStrategy EQUALS = new CompareStrategy() {

        @Override
        public boolean equals(Object a, Object b) {
            return a.equals(b);
        }

        @Override
        public int hashCode(Object o) {
            return o.hashCode();
        }
    };

    public static final CompareStrategy IDENTITY = new CompareStrategy() {

        @Override
        public boolean equals(Object a, Object b) {
            return a == b;
        }

        @Override
        public int hashCode(Object o) {
            return o.hashCode();
        }
    };

    public static final CompareStrategy IDENTITY_WITH_SYSTEM_HASHCODE = new CompareStrategy() {

        @Override
        public boolean equals(Object a, Object b) {
            return a == b;
        }

        @Override
        public int hashCode(Object o) {
            return System.identityHashCode(o);
        }
    };

    public abstract boolean equals(Object a, Object b);

    public abstract int hashCode(Object o);
}
